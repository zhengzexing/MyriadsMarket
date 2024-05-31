package com.atguigu.gulimall.seckill.feign;

import com.atguigu.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@FeignClient("gulimall-product")
public interface ProductFeignService {
    /**
     * 根据skuId获取商品详细信息
     */
    @RequestMapping("/product/skuinfo/info/{skuId}")
    public R getSkuInfoBySkuId(@PathVariable("skuId") Long skuId);
}
