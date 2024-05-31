package com.atguigu.gulimall.ware.vo;

import lombok.Data;

/**
 * 库存锁定的结果
 */
@Data
public class LockStockResult {
    /**
     * 锁定的商品skuId
     */
    private Long skuId;
    /**
     * 锁定的数量
     */
    private Integer num;
    /**
     * 锁定的结果
     */
    private Boolean locked;

}
