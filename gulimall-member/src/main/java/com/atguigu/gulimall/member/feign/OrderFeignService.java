package com.atguigu.gulimall.member.feign;

import com.atguigu.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient("gulimall-order")
public interface OrderFeignService {
    /**
     * 查询当前用户所有已支付的订单，展示在我的订单中
     */
    @PostMapping("/order/order/listWithItem")
    public R listWithItem(@RequestBody Map<String,Object> params);
}
