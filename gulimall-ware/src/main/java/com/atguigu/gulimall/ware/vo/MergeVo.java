package com.atguigu.gulimall.ware.vo;

import lombok.Data;

import java.util.List;

@Data
public class MergeVo {
    /**
     * 采购单id
     */
    private Long purchaseId;

    /**
     * 要合并的采购需求单id
     */
    private List<Long> items;
}
