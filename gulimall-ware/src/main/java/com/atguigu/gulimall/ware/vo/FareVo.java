package com.atguigu.gulimall.ware.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 某个地址对应的运费信息
 */
@Data
public class FareVo {
    /**
     * 地址信息
     */
    private MemberAddressVo address;
    /**
     * 运费
     */
    private BigDecimal fare;
}
