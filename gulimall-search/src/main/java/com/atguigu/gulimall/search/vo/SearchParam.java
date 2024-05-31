package com.atguigu.gulimall.search.vo;

import lombok.Data;

import java.util.List;

/**
 * 封装前端页面所有可能传递过来的检索参数
 */
@Data
public class SearchParam {
    /**
     * 页面传递过来的全文匹配关键字
     */
    private String keyword;
    /**
     * 三级分类id
     */
    private Long catalog3Id;
    /**
     * 排序条件，按价格、销售数量、热度分排序？
     */
    private String sort;
    /**
     * 是否只显示有库存的商品
     */
    private Integer hasStock;
    /**
     * 筛选商品的价格区间
     */
    private String skuPrice;
    /**
     * 筛选商品的品牌
     */
    private List<Long> brandId;
    /**
     * 根据商品的属性筛选
     */
    private List<String> attrs;
    /**
     * 分页数据
     */
    private Integer pageNum = 1;
    /**
     * 页面访问，url的所有查询参数
     */
    private String _QueryString;

}
