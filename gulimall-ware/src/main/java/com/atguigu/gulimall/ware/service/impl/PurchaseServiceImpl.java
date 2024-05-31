package com.atguigu.gulimall.ware.service.impl;

import com.atguigu.common.constant.WareConstant;
import com.atguigu.common.exception.RRException;
import com.atguigu.gulimall.ware.entity.PurchaseDetailEntity;
import com.atguigu.gulimall.ware.service.PurchaseDetailService;
import com.atguigu.gulimall.ware.service.WareSkuService;
import com.atguigu.gulimall.ware.vo.MergeVo;
import com.atguigu.gulimall.ware.vo.PurchaseDoneVo;
import com.atguigu.gulimall.ware.vo.PurchaseItemDoneVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.ware.dao.PurchaseDao;
import com.atguigu.gulimall.ware.entity.PurchaseEntity;
import com.atguigu.gulimall.ware.service.PurchaseService;
import org.springframework.transaction.annotation.Transactional;


@Service("purchaseService")
public class PurchaseServiceImpl extends ServiceImpl<PurchaseDao, PurchaseEntity> implements PurchaseService {
    @Autowired
    PurchaseDetailService purchaseDetailService;

    @Autowired
    WareSkuService wareSkuService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<PurchaseEntity> page = this.page(
                new Query<PurchaseEntity>().getPage(params),
                new QueryWrapper<PurchaseEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public PageUtils queryPageUnReceivePurchase(Map<String, Object> params) {
        QueryWrapper<PurchaseEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("status",WareConstant.PurchaseStatusEnum.CREATED.getCode()).or()
                .eq("status",WareConstant.PurchaseStatusEnum.ASSIGNED.getCode()); //新建状态或者已分配未领取状态的采购单

        IPage<PurchaseEntity> page = this.page(
                new Query<PurchaseEntity>().getPage(params),
                wrapper
        );

        return new PageUtils(page);
    }

    /**
     * 采购需求的状态已分配意思是已经分配给采购单了
     * 采购单的状态已分配意思是已经分配给采购人员了
     * @param mergeVo
     */
    @Transactional
    @Override
    public void mergePurchase(MergeVo mergeVo) {
        Long purchaseId = mergeVo.getPurchaseId();
        //1.采购单id为空时，需要自己创建新的采购单存储采购需求
        if(purchaseId == null){
            PurchaseEntity purchaseEntity = new PurchaseEntity();
            purchaseEntity.setStatus(WareConstant.PurchaseStatusEnum.CREATED.getCode());
            purchaseEntity.setCreateTime(new Date());
            purchaseEntity.setUpdateTime(new Date());

            this.save(purchaseEntity);
            purchaseId = purchaseEntity.getId();
        }

        PurchaseEntity purchase = this.getById(purchaseId);
        //采购单在新建或者已分配时才可以合并
        if(purchase.getStatus() == WareConstant.PurchaseStatusEnum.CREATED.getCode() ||
        purchase.getStatus() == WareConstant.PurchaseStatusEnum.ASSIGNED.getCode()){
            //2.合并采购需求后记录在指定的采购单上
            List<Long> items = mergeVo.getItems();//要合并的采购需求ids
            Long finalPurchaseId = purchaseId;

            //合并的这些采购需求需要修改状态和分配到的采购单id，封装好要修改的数据
            List<PurchaseDetailEntity> detailEntities = items.stream().map(item->{
                //根据采购需求的detailId，找出实体
                PurchaseDetailEntity detailEntity = purchaseDetailService.getById(item);
                if(detailEntity.getStatus() != WareConstant.PurchaseDetailStatusEnum.CREATED.getCode() &&
                        detailEntity.getStatus() != WareConstant.PurchaseDetailStatusEnum.ASSIGNED.getCode()){
                    throw new RRException("合并整单的过程中不能包含正在采购的采购需求");
                }
                return detailEntity;
            }).map((item) -> {
                PurchaseDetailEntity detailEntity = new PurchaseDetailEntity();
                detailEntity.setId(item.getId());
                detailEntity.setPurchaseId(finalPurchaseId);
                detailEntity.setStatus(WareConstant.PurchaseDetailStatusEnum.ASSIGNED.getCode());

                return detailEntity;
            }).collect(Collectors.toList());

            purchaseDetailService.updateBatchById(detailEntities);

            //3.合并完成修改采购单的修改时间
            PurchaseEntity purchaseEntity = new PurchaseEntity();
            purchaseEntity.setId(purchaseId);
            purchaseEntity.setUpdateTime(new Date());
            this.updateById(purchaseEntity);
        }else{
            throw new RRException("采购单只有在新建或者已分配状态才能接受采购需求的合并");
        }


    }

    @Override
    public void receivedPurchase(List<Long> purchaseIds) {
        //1.确认purchaseIds采购单都是新建或者已分配未领取的，把领取、完成的过滤掉
        List<PurchaseEntity> purchaseEntities = purchaseIds.stream().map((purchaseId) -> {
            //根据purchaseIds在数据库中找到所有的实体
            PurchaseEntity purchaseEntity = this.getById(purchaseId);
            return purchaseEntity;
        }).filter(purchaseEntity -> {
            //过滤领取、完成状态的采购单
            if (purchaseEntity.getStatus() == WareConstant.PurchaseStatusEnum.CREATED.getCode() ||
                    purchaseEntity.getStatus() == WareConstant.PurchaseStatusEnum.ASSIGNED.getCode()) {
                return true;
            }
            return false;
        }).map(purchaseEntity -> {
            //修改采购单的状态为已领取
            purchaseEntity.setStatus(WareConstant.PurchaseStatusEnum.RECEIVED.getCode());
            purchaseEntity.setUpdateTime(new Date());
            return purchaseEntity;
        }).collect(Collectors.toList());

        //2.改变采购单状态
        this.updateBatchById(purchaseEntities);

        //3.改变与采购单关联的采购需求的状态
        purchaseEntities.forEach(purchaseEntity -> {
            //找出purchaseId采购单id下的所有采购需求
            List<PurchaseDetailEntity> detailEntities = purchaseDetailService.listDetailByPurchaseId(purchaseEntity.getId());

            //修改采购需求的状态
            List<PurchaseDetailEntity> purchaseDetailEntities = detailEntities.stream().map(item -> {
                PurchaseDetailEntity purchaseDetailEntity = new PurchaseDetailEntity();
                purchaseDetailEntity.setId(item.getId());
                purchaseDetailEntity.setStatus(WareConstant.PurchaseDetailStatusEnum.BUYING.getCode());
                return purchaseDetailEntity;
            }).collect(Collectors.toList());

            //修改后的所有采购需求批量修改
            purchaseDetailService.updateBatchById(purchaseDetailEntities);
        });

    }

    @Override
    public void finishPurchase(PurchaseDoneVo purchaseDoneVo) {
        //1.改变采购项的状态(采购成功还是采购失败)
        Boolean flag = true;
        List<PurchaseItemDoneVo> items = purchaseDoneVo.getItems();
        //待修改的采购列表
        List<PurchaseDetailEntity> updates = new ArrayList<>();

        for (PurchaseItemDoneVo item : items) {
            PurchaseDetailEntity detailEntity = new PurchaseDetailEntity();
            if(item.getStatus() == WareConstant.PurchaseDetailStatusEnum.HAS_ERROR.getCode()){
                flag = false;
                //采购失败
                detailEntity.setStatus(WareConstant.PurchaseDetailStatusEnum.HAS_ERROR.getCode());
            }else{
                //采购成功
                detailEntity.setStatus(WareConstant.PurchaseDetailStatusEnum.FINISHED.getCode());
                //3.将成功的采购项进行入库操作
                //当前采购项的详细信息
                PurchaseDetailEntity entity = purchaseDetailService.getById(item.getItemId());
                //商品skuId、仓库id、需要采购的数量
                wareSkuService.addStock(entity.getSkuId(),entity.getWareId(),entity.getSkuNum());
            }
            detailEntity.setId(item.getItemId());

            //加入待修改列表
            updates.add(detailEntity);
        }

        purchaseDetailService.updateBatchById(updates);

        //2.改变采购单的状态(已完成还是出现异常)
        PurchaseEntity purchaseEntity = new PurchaseEntity();
        purchaseEntity.setId(purchaseDoneVo.getId());
        if(flag){
            //采购成功
            purchaseEntity.setStatus(WareConstant.PurchaseStatusEnum.FINISHED.getCode());
        }else{
            //有采购项出现异常
            purchaseEntity.setStatus(WareConstant.PurchaseStatusEnum.HAS_ERROR.getCode());
        }
        purchaseEntity.setUpdateTime(new Date());

        this.updateById(purchaseEntity);
    }

}