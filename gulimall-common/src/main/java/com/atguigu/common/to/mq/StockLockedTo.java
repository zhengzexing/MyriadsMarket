package com.atguigu.common.to.mq;

import lombok.Data;

import java.util.List;

@Data
public class StockLockedTo {
    /**
     * 库存工作单的id
     * 库存工作单，记录订单锁定库存的基本信息
     */
    private Long id;
    /**
     * 库存工作详情单的id
     * 库存工作详情单，记录某一订单锁定库存的商品详细信息
     */
    private StockDetailTo detail;
}
