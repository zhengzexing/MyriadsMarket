package com.atguigu.gulimall.search.vo;

import com.atguigu.common.to.es.SkuEsModel;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 检索之后响应的数据
 * 应该为es中存储的商品数据，es中存储什么就应该返回什么
 */
@Data
public class SearchResult {
    /**
     * 返回的商品列表信息
     * 用es数据模型进行封装
     */
    private List<SkuEsModel> products;
    /**
     * 当前页码
     */
    private Integer pageNum;
    /**
     * 总记录数
     */
    private Long total;
    /**
     * 总页码
     */
    private Integer totalPages;
    /**
     * 可遍历的导航页
     */
    private List<Integer> pageNavs;
    /**
     * 该商品分类（手机）下所有涉及的品牌（小米，华为，苹果，OPPO）
     */
    private List<BrandVo> brands;
    /**
     * 当前该商品分类所涉及的所有属性
     */
    private List<AttrVo> attrs;
    /**
     * 该商品分类所涉及的所有分类
     */
    private List<CatalogVo> catalogs;
    /**
     * 前端面包屑导航数据
     */
    private List<NavVo> navs = new ArrayList<>();

    /**
     * 页面中已经被筛选的属性id，应用于面包屑的属性显示与不显示功能需要得到选中的属性，选中的那些将不显示
     */
    private List<Long> attrIds = new ArrayList<>();

    @Data
    public static class NavVo{
        private String navName;
        private String navValue;
        private String link;
    }

    /**
     * 商品品牌的vo
     */
    @Data
    public static class BrandVo{
        private Long brandId;
        private String brandName;
        private String brandImg;
    }

    /**
     * 商品属性的vo
     */
    @Data
    public static class AttrVo{
        private Long attrId;
        private String attrName;
        private List<String> attrValue;
    }

    /**
     * 商品分类的vo
     */
    @Data
    public static class CatalogVo{
        private Long catalogId;
        private String catalogName;
    }
}
