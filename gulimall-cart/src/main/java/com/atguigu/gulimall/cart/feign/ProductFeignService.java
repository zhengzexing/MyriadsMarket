package com.atguigu.gulimall.cart.feign;

import com.atguigu.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;

@FeignClient("gulimall-product")
public interface ProductFeignService {
    /**
     * 根据skuId获取商品的基本信息
     * @param skuId
     * @return
     */
    @RequestMapping("/product/skuinfo/info/{skuId}")
    public R getSkuInfo(@PathVariable("skuId") Long skuId);

    /**
     * 根据skuId获取该商品的销售属性组合信息，如罗兰紫+256GB
     * @return
     */
    @GetMapping("/product/skusaleattrvalue/stringList/{skuId}")
    public R getSkuSaleAttrValues(@PathVariable("skuId") Long skuId);

    /**
     * 根据商品的Id获取商品的现在价格
     */
    @GetMapping("/product/skuinfo/{skuId}/price")
    public R getPrice(@PathVariable("skuId") Long skuId);
}
