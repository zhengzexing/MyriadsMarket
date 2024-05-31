package com.atguigu.gulimall.ware.feign;

import com.atguigu.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@FeignClient("gulimall-member")
public interface MemberFeignService {
    /**
     * 根据收货地址的id获取收货地址详细信息
     */
    @RequestMapping("/member/memberreceiveaddress/info/{id}")
    public R getAddrInfo(@PathVariable("id") Long id);
}
