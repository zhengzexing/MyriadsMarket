package com.atguigu.gulimall.product.vo;

import lombok.Data;

@Data
public class AttrRespVo extends AttrVo{
    /**
     * 所属商品分类名称
     */
    private String catelogName;

    /**
     * 所属属性组名称
     */
    private String groupName;

    /**
     * 商品分类的路径，用于修改属性attr时，回显出商品的分类如：手机/手机通讯/手机[2,25,225]
     */
    private Long[] catelogPath;
}
