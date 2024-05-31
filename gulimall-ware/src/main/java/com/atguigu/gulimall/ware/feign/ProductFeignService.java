package com.atguigu.gulimall.ware.feign;

import com.atguigu.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@FeignClient(value = "gulimall-product")
public interface ProductFeignService {
    /**
     * 远程调用获取sku商品信息
     */
    @RequestMapping("/product/skuinfo/info/{skuId}")
    public R skuInfo(@PathVariable("skuId") Long skuId);
}
