package com.atguigu.gulimall.auth.feign;

import com.atguigu.common.utils.R;
import com.atguigu.gulimall.auth.vo.SocialUser;
import com.atguigu.gulimall.auth.vo.UserLoginVo;
import com.atguigu.gulimall.auth.vo.UserRegisterVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(value = "gulimall-member")
public interface MemberFeignService {
    /**
     * 用户注册
     * @param vo
     * @return
     */
    @PostMapping("/member/member/regist")
    public R regist(@RequestBody UserRegisterVo vo);

    /**
     * 用户账户密码登录
     * @param vo
     * @return
     */
    @PostMapping("/member/member/login")
    public R login(@RequestBody UserLoginVo vo);

    /**
     * 用户社交账号登录
     * @return
     */
    @PostMapping("/member/member/oauth2/login")
    public R oauthLogin(@RequestBody SocialUser socialUser) throws Exception;
}
