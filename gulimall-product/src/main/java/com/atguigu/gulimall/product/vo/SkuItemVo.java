package com.atguigu.gulimall.product.vo;

import com.atguigu.gulimall.product.entity.SkuImagesEntity;
import com.atguigu.gulimall.product.entity.SkuInfoEntity;
import com.atguigu.gulimall.product.entity.SpuInfoDescEntity;
import lombok.Data;

import java.util.List;

@Data
public class SkuItemVo {
    //1.sku基本信息获取 pms_sku_info
    private SkuInfoEntity info;
    //2.sku的图片信息 pms_sku_images
    private List<SkuImagesEntity> images;
    //3.获取spu的销售属性组合
    List<SkuItemSaleAttrVo> saleAttr;
    //4.获取spu的介绍
    private SpuInfoDescEntity desp;
    //5.获取spu的规格参数信息
    private List<SpuItemAttrGroupVo> groupAttrs;
    //6.库存
    private boolean hasStock = true;
    //7.商品秒杀活动信息
    SeckillInfoVo seckillInfo;
}
