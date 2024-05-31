package com.atguigu.gulimall.product.feign;

import com.atguigu.common.utils.R;
import com.atguigu.gulimall.product.feign.fallback.SeckillFeginServiceFallBack;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 指定远程调用失败后，方法默认回调的类
 */
@FeignClient(value = "gulimall-seckill",fallback = SeckillFeginServiceFallBack.class)
public interface SeckillFeignService {
    /**
     * 根据商品id查询当前商品是否参与秒杀活动，展示在商品详情页
     * @return
     */
    @GetMapping("/sku/seckill/{skuId}")
    public R getSkuSeckillInfo(@PathVariable("skuId") Long skuId);
}
