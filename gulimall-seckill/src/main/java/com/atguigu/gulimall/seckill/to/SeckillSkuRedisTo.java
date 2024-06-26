package com.atguigu.gulimall.seckill.to;

import com.atguigu.gulimall.seckill.vo.SkuInfoVo;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class SeckillSkuRedisTo {
    /**
     * 活动id
     */
    private Long promotionId;
    /**
     * 活动场次id
     */
    private Long promotionSessionId;
    /**
     * 商品id
     */
    private Long skuId;
    /**
     * 秒杀价格
     */
    private BigDecimal seckillPrice;
    /**
     * 秒杀总量
     */
    private Integer seckillCount;
    /**
     * 每人限购数量
     */
    private Integer seckillLimit;
    /**
     * 排序
     */
    private Integer seckillSort;
    /**
     * 当前商品所属的秒杀活动的开始时间
     */
    private Long startTime;
    /**
     * 当前商品所属的秒杀活动的结束时间
     */
    private Long endTime;
    /**
     * 秒杀商品的随机码
     */
    private String randomCode;
    /**
     * sku的详细信息
     */
    private SkuInfoVo skuInfo;
}
