package com.atguigu.gulimall.order.feign;

import com.atguigu.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient("gulimall-member")
public interface MemberFeignService {
    /**
     * 获取会员用户的收货地址列表
     * @return
     */
    @GetMapping("/member/memberreceiveaddress/{memberId}/addresses")
    public R getAddress(@PathVariable("memberId") Long memberId);
}
