package com.atguigu.gulimall.ware.vo;

import lombok.Data;

@Data
public class PurchaseItemDoneVo {
    /**
     * 采购需求id
     */
    private Long itemId;
    /**
     * 采购状态
     */
    private Integer status;
    /**
     * 如果采购失败，原因是什么
     */
    private String reason;
}
