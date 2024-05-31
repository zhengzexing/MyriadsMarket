package com.atguigu.gulimall.order.vo;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 订单确认页要展示的数据
 */
public class OrderConfirmVo implements Serializable {
    /**
     * 用户的收货地址列表
     */
    @Getter @Setter
    private List<MemberAddressVo> address;
    /**
     * 用户在购物车勾选的要结算的商品
     */
    @Getter @Setter
    private List<OrderItemVo> items;
    /**
     * 发票信息（不做了）
     */
    /**
     * 用户拥有的优惠券（远程查询coupon服务获取用户的优惠券，coupon服务没做，这里也不做了）
     */
    /**
     * 会员的积分
     */
    @Getter @Setter
    private Integer integration;
    /**
     * 当前要付款的订单总金额（优惠前）
     */
    //private BigDecimal total;

    public BigDecimal getTotal() {
        BigDecimal sum = new BigDecimal("0");
        if(items != null){
            for (OrderItemVo item : items) {
                sum = sum.add(item.getTotalPrice());
            }
        }
        return sum;
    }

    /**
     * 实际要付款的总金额（优惠后）
     */
    //private BigDecimal payPrice;

    public BigDecimal getPayPrice() {
        return getTotal();
    }

    /**
     * 订单的唯一令牌标识，防止重复下单
     */
    @Getter @Setter
    private String orderToken;

    public Integer getTotalCount(){
        Integer i = 0;
        if(items!=null){
            for (OrderItemVo item : items) {
                i+=item.getCount();
            }
        }
        return i;
    }

    /**
     * 商品的库存信息
     */
    @Getter @Setter
    Map<Long,Boolean> stocks;
}
