package com.atguigu.gulimall.seckill.feign;

import com.atguigu.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient("gulimall-coupon")
public interface CouponFeignService {
    /**
     * 获取最近3天的所有秒杀场次
     */
    @GetMapping("/coupon/seckillsession/latest3DaySession")
    public R getLatest3DaySession();
}
