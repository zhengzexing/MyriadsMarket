package com.atguigu.gulimall.ware.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.to.mq.OrderTo;
import com.atguigu.common.to.mq.StockDetailTo;
import com.atguigu.common.to.mq.StockLockedTo;
import com.atguigu.common.utils.R;
import com.atguigu.common.exception.NoStockException;
import com.atguigu.gulimall.ware.entity.WareOrderTaskDetailEntity;
import com.atguigu.gulimall.ware.entity.WareOrderTaskEntity;
import com.atguigu.gulimall.ware.feign.OrderFeignService;
import com.atguigu.gulimall.ware.feign.ProductFeignService;
import com.atguigu.gulimall.ware.service.WareOrderTaskDetailService;
import com.atguigu.gulimall.ware.service.WareOrderTaskService;
import com.atguigu.gulimall.ware.vo.OrderItemVo;
import com.atguigu.gulimall.ware.vo.OrderVo;
import com.atguigu.gulimall.ware.vo.SkuHasStockVo;
import com.atguigu.gulimall.ware.vo.WareSkuLockVo;
import com.rabbitmq.client.Channel;
import lombok.Data;
import org.apache.commons.lang.StringUtils;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.ware.dao.WareSkuDao;
import com.atguigu.gulimall.ware.entity.WareSkuEntity;
import com.atguigu.gulimall.ware.service.WareSkuService;
import org.springframework.transaction.annotation.Transactional;
@Service("wareSkuService")
public class WareSkuServiceImpl extends ServiceImpl<WareSkuDao, WareSkuEntity> implements WareSkuService {
    @Autowired
    WareSkuDao wareSkuDao;

    @Autowired
    ProductFeignService productFeignService;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    WareOrderTaskService orderTaskService;

    @Autowired
    WareOrderTaskDetailService orderTaskDetailService;

    @Autowired
    OrderFeignService orderFeignService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        QueryWrapper<WareSkuEntity> wrapper = new QueryWrapper<>();

        String skuId = (String) params.get("skuId");
        if(!StringUtils.isEmpty(skuId)){
            wrapper.eq("sku_id",skuId);
        }

        String wareId = (String) params.get("wareId");
        if(!StringUtils.isEmpty(wareId)){
            wrapper.eq("ware_id",wareId);
        }

        IPage<WareSkuEntity> page = this.page(
                new Query<WareSkuEntity>().getPage(params),
                wrapper
        );

        return new PageUtils(page);
    }

    @Transactional
    @Override
    public void addStock(Long skuId, Long wareId, Integer skuNum) {
        //1.判断数据库中是否包含了skuId,wareId这一条库存信息
        List<WareSkuEntity> wareSkuEntities = wareSkuDao.selectList(
                new QueryWrapper<WareSkuEntity>()
                        .eq("sku_id", skuId)
                        .eq("ware_id", wareId)
        );

        if(wareSkuEntities == null || wareSkuEntities.size() == 0){
            //没有该库存信息，插入
            WareSkuEntity wareSkuEntity = new WareSkuEntity();
            wareSkuEntity.setSkuId(skuId);
            wareSkuEntity.setWareId(wareId);
            wareSkuEntity.setStock(skuNum);
            wareSkuEntity.setStockLocked(0);

            //远程查询skuInfo根据sku_id获取sku_name，失败也无需回滚
            try {
                R skuInfo = productFeignService.skuInfo(skuId);
                if(skuInfo.getCode() == 0){
                    //将SkuInfoEntity强转为Map<String, Object>
                    Map<String,Object> data = (Map<String, Object>) skuInfo.get("skuInfo");
                    wareSkuEntity.setSkuName((String) data.get("skuName"));
                }
            }catch (Exception e){

            }

            wareSkuDao.insert(wareSkuEntity);
        }else{
            //修改库存信息
            wareSkuDao.addStock(skuId,wareId,skuNum);
        }

    }

    @Override
    public List<SkuHasStockVo> getSkusHasStock(List<Long> skuIds) {

        List<SkuHasStockVo> skuHasStockVos = skuIds.stream().map(skuId -> {
            SkuHasStockVo skuHasStockVo = new SkuHasStockVo();
            skuHasStockVo.setSkuId(skuId);
            //查询当前skuId的库存总量
            Long stockCount = baseMapper.getSkuStock(skuId);
            skuHasStockVo.setHasStock(stockCount == null ? false : stockCount > 0);
            return skuHasStockVo;
        }).collect(Collectors.toList());

        return skuHasStockVos;
    }

    /**
     * 根据商品的订单项进行库存锁定
     *
     * 什么时候需要解锁库存？
     * 1.下订单成功，但是订单过期没有支付，订单被系统自动取消
     * 2.订单被用户手动取消
     * 3.下订单成功，远程调用库存锁定成功，在订单业务继续调用远程服务发生异常，之前的库存锁定需要回滚
     * @param wareSkuLockVo
     * @return
     */
    @Transactional
    @Override
    public Boolean orderLockStock(WareSkuLockVo wareSkuLockVo) {
        //保存库存工作单的基本信息和每一个商品锁定库存的详情
        WareOrderTaskEntity wareOrderTaskEntity = new WareOrderTaskEntity();
        wareOrderTaskEntity.setOrderSn(wareSkuLockVo.getOrderSn());
        orderTaskService.save(wareOrderTaskEntity);

        //正常逻辑：按照下单的地址，查询就近的仓库，在该仓库中进行库存的锁定
        //简化逻辑：找到每个商品项在哪个仓库有库存，哪个仓库锁定成功就算哪个仓库
        List<OrderItemVo> lockItems = wareSkuLockVo.getLocks();

        //1.查询出某个商品在哪些仓库下有库存，将所有商品及库存信息存为集合
        List<SkuWareHasStock> wareHasStocks = lockItems.stream().map(item -> {
            SkuWareHasStock skuWareHasStock = new SkuWareHasStock();
            skuWareHasStock.setSkuId(item.getSkuId());
            //查询当前商品在哪些仓库中有库存
            List<Long> wareIds = wareSkuDao.listWareIdHasSkuStock(item.getSkuId());
            skuWareHasStock.setWareIds(wareIds);
            skuWareHasStock.setNum(item.getCount());
            return skuWareHasStock;
        }).collect(Collectors.toList());

        //2.为每个商品锁定库存
        for (SkuWareHasStock ware : wareHasStocks) {
            Boolean skuLock = false;//当前该商品锁定库存是否成功
            //当前要锁定的商品
            Long skuId = ware.getSkuId();
            //当前商品的仓库信息
            List<Long> wareIds = ware.getWareIds();
            if(wareIds == null || wareIds.size() == 0){
                //所有的仓库都没有当前该商品库存，代表当前商品锁定失败，抛异常全部回滚
                throw new NoStockException(skuId);
            }
            //当前该商品有库存，遍历所有有库存的仓库，选择一个扣除
            for (Long wareId : wareIds) {
                //update修改库存的锁定数量，修改成功count返回1
                Long count = wareSkuDao.lockSkuStock(skuId,wareId,ware.getNum());
                if(count == 1){
                    //当前商品锁定库存成功了，后续仓库无需再锁定
                    skuLock = true;
                    //保存每一个商品锁定库存的详情信息
                    WareOrderTaskDetailEntity wareOrderTaskDetailEntity = new WareOrderTaskDetailEntity(null,skuId,skuId.toString(),ware.getNum(),wareOrderTaskEntity.getId(),wareId,1);
                    orderTaskDetailService.save(wareOrderTaskDetailEntity);

                    //当前商品锁定库存成功的消息发送到MQ中
                    //如果中途有人锁定失败，事务回滚了，已发送的消息应该怎么处理？
                    StockLockedTo stockLockedTo = new StockLockedTo();
                    stockLockedTo.setId(wareOrderTaskEntity.getId());
                    StockDetailTo stockDetailTo = new StockDetailTo();
                    BeanUtils.copyProperties(wareOrderTaskDetailEntity,stockDetailTo);
                    stockLockedTo.setDetail(stockDetailTo);
                    rabbitTemplate.convertAndSend("stock-event-exchange","stock.locked",stockLockedTo);

                    break;
                }else{
                    //当前wareId仓库锁定库存失败，继续尝试下一个仓库
                }
            }
            //当前商品的所有仓库遍历完成，如果有仓库锁定了库存，就代表成功，否则就表示每一个仓库的库存都不足以锁定当前商品
            if(!skuLock){
                //每一个仓库的库存都不足以锁定当前的商品
                throw new NoStockException(skuId);
            }
        }

        //所有商品都成功锁定了库存
        return true;
    }

    /**
     * 解锁库存的方法
     * @param stockLockedTo
     */
    @Override
    public void unlockStock(StockLockedTo stockLockedTo) {
        StockDetailTo detail = stockLockedTo.getDetail();//库存锁定商品详情信息
        Long detailId = detail.getId();//库存锁定商品详情信息表Id
        /**
         * 以下执行解锁业务
         * 另外需要考虑如果锁定库存中途发生错误，事务全部回滚，发生错误之前发送的信息该如何处理？
         * 若当前的信息属于以上这种情况，不应该给库存表执行解锁库存的业务，因为在这之前已经全部回滚了
         *
         * 解决：
         * 根据库存锁定商品详情任务信息表detailId查询是否有当前的记录
         * 1.若查询不到该详情任务，说明是锁定库存的中途发生了异常，锁定库存的任务全部进行回滚了，此消息无需处理
         * 2.若可以查询到该详情任务，并且订单表中的订单删除了，说明订单业务发生了回滚，库存这边也需要解锁，即此消息需要处理
         *      2.1若可以查询到该详情任务，但是订单表中的订单还存在，需要判断订单是否支付，如果订单支付了，无需解锁，订单未支付，需要解锁
         *
         * 如果解锁库存的操作失败，消息又被mq自动确认删除了，还是会导致库存没有成功解锁
         * 这时则需要我们手动确认接收消息basicAck
         */
        WareOrderTaskDetailEntity detailEntity = orderTaskDetailService.getById(detailId);
        if(detailEntity != null){
            //属于情况2，需要处理该消息，进行解锁
            Long taskId = stockLockedTo.getId();//库存锁定基本信息工作单Id
            WareOrderTaskEntity taskEntity = orderTaskService.getById(taskId);//库存锁定的基本信息对象
            String orderSn = taskEntity.getOrderSn();//订单号，根据订单号查询订单是否存在
            R r = orderFeignService.getOrderStatus(orderSn);
            if(r.getCode() == 0){
                OrderVo order = r.getData("order", new TypeReference<OrderVo>() {
                });
                if(order == null || order.getStatus() == 4){
                    //订单不存在，订单业务发生异常导致订单回滚了；订单30分钟未支付被取消，订单状态为4；需要解锁库存
                    if(detailEntity.getLockStatus() == 1){
                        //只有已锁定的库存详情才需要来解锁
                        unLockStock(detail.getSkuId(),detail.getWareId(),detail.getSkuNum(),detail.getId());
                        //解锁成功，手动确认消息接收成功
                    }else{
                        //库存详情的状态为已解锁或者已扣减则不需要任何操作
                        //无需操作，手动确认消息接收成功
                    }
                }else{
                    //订单存在，并且该订单的状态不需要解锁库存，例如已支付的订单就不需要解锁库存
                    //无需操作，手动确认消息接收成功
                }
            }else{
                //远程调用查询订单服务失败，应该继续尝试远程获取订单信息，以决定是否需要解锁库存，故需要将消息重新入队
                //手动抛异常，监听器调用该方法捕获到这个异常将消息重新入队
                throw new RuntimeException("远程查询订单服务失败");
            }
        }else{
            //属于情况1，无需处理该消息，手动确认消息接收成功
        }
    }

    /**
     * 在订单关闭的时候，即订单状态变为4，解锁库存
     * 为了防止网络抖动，在解锁库存操作消息到期之后，关单操作的消息才被接收，订单状态才变成4，导致前面解锁库存失败
     * 解决方法：
     * 在关单之后给库存释放队列再次发送一个关闭订单的消息，在库存接收到该关闭订单消息时需要再次执行解锁库存的操作
     * @param orderTo
     */
    @Transactional
    @Override
    public void unlockStock(OrderTo orderTo) {
        String orderSn = orderTo.getOrderSn();//订单号
        //查询最新库存详情的状态（1-未解锁才需要解锁），防止库存重复解锁
        WareOrderTaskEntity taskEntity = orderTaskService.getOrderTaskByOrderSn(orderSn);

        Long taskId = taskEntity.getId();//库存任务基本信息表id
        //根据基本信息表id查询详情任务表中所有没有解锁的库存（1-未解锁才需要解锁）
        List<WareOrderTaskDetailEntity> taskDetailEntities = orderTaskDetailService.list(
                new QueryWrapper<WareOrderTaskDetailEntity>()
                        .eq("task_id", taskId)
                        .eq("lock_status", 1)
        );

        //对所有未解锁状态的库存进行解锁
        for (WareOrderTaskDetailEntity taskDetailEntity : taskDetailEntities) {
            unLockStock(taskDetailEntity.getSkuId(),taskDetailEntity.getWareId(),taskDetailEntity.getSkuNum(),taskDetailEntity.getId());
        }
    }

    /**
     * 操作dao层对数据库表进行解锁
     * 同时更新详情工作单的锁定状态为已解锁
     */
    @Transactional
    public void unLockStock(Long skuId,Long wareId,Integer num,Long taskDetailId){
        //库存表解锁库存
        wareSkuDao.unlockStock(skuId,wareId,num);
        //更新详情工作单的锁定状态为已解锁
        WareOrderTaskDetailEntity entity = new WareOrderTaskDetailEntity();
        entity.setId(taskDetailId);
        entity.setLockStatus(2);//已解锁
        orderTaskDetailService.updateById(entity);
    }

    /**
     * 在哪些仓库中存储着当前的sku商品
     */
    @Data
    class SkuWareHasStock{
        /**
         * 商品id
         */
        private Long skuId;
        /**
         * 仓库id
         */
        private List<Long> wareIds;
        /**
         * 锁定的数量
         */
        private Integer num;
    }

}