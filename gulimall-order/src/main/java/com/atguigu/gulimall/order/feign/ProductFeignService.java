package com.atguigu.gulimall.order.feign;

import com.atguigu.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@FeignClient("gulimall-product")
public interface ProductFeignService {
    /**
     * 根据商品skuId查询所属的spu信息
     */
    @GetMapping("/product/spuinfo/info/{skuId}")
    public R getSpuInfoBySkuId(@PathVariable("skuId") Long skuId);

    /**
     * 信息
     */
    @RequestMapping("/product/brand/info/{brandId}")
    //@RequiresPermissions("product:brand:info")
    public R getBrandInfoByBrandId(@PathVariable("brandId") Long brandId);
}
