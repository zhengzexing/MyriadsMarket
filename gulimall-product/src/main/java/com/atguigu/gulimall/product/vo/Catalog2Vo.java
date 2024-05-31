package com.atguigu.gulimall.product.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 商品二级分类的vo
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class Catalog2Vo {
    private String catalog1Id; //一级父分类的id
    private List<Catalog3Vo> catalog3List; //三级子分类的列表
    private String id;
    private String name;

    /**
     * 商品三级分类的vo
     */
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Catalog3Vo{
        private String catalog2Id; //二级父分类的id
        private String id;
        private String name;
    }
}
