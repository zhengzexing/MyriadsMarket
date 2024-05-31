package com.atguigu.gulimall.auth.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.constant.AuthConstant;
import com.atguigu.common.utils.HttpUtils;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.auth.feign.MemberFeignService;
import com.atguigu.common.vo.MemberRespVo;
import com.atguigu.gulimall.auth.vo.SocialUser;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
public class OAuth2Controller {
    @Autowired
    MemberFeignService memberFeignService;

    @GetMapping("/oauth2.0/weibo/success")
    public String weibo(@RequestParam("code") String code,
                        HttpSession session) throws Exception {
        Map<String,String> map = new HashMap<>();
        map.put("client_id","2476969271");
        map.put("client_secret","40796eb1afb970236e1749816e009910");
        map.put("grant_type","authorization_code");
        map.put("code",code);
        map.put("redirect_uri","http://auth.gulimall.com/oauth2.0/weibo/success");
        //1.根据code换取access_token
        HttpResponse response = HttpUtils.doPost("https://api.weibo.com", "/oauth2/access_token", "post",new HashMap<>(),new HashMap<>(),map);

        //2.处理响应请求，根据access_token获取用户信息
        if (response.getStatusLine().getStatusCode()==200) {
            //状态码200，请求成功，得到access_token
            String json = EntityUtils.toString(response.getEntity());
            SocialUser socialUser = JSON.parseObject(json, SocialUser.class);//社交对象

            //根据access_token获取用户基本信息，为用户注册/登录账号
            R r = memberFeignService.oauthLogin(socialUser);
            if(r.getCode() == 0){
                //注册/登录成功，保存信息
                MemberRespVo memberRespVo = r.getData("data", new TypeReference<MemberRespVo>() {
                });
                log.info("登录成功，用户信息为：{}",memberRespVo.toString());

                //将用户信息保存到session中，spring session会自动放到redis，解决分布式session共享问题
                session.setAttribute(AuthConstant.LOGIN_USER,memberRespVo);

                //默认发的令牌在当前的作用域：auth.gulimall.com，需要让父域gulimall.com也能使用，解决子域session共享问题
                //并且给auth.gulimall.com的父域名gulimall.com设置访问该session的cookie(GulimallSessionConfig配置类)

                //跳回首页
                return "redirect:http://gulimall.com";
            }
        }

        //获取access_token失败或者远程调用member服务注册/登录失败
        return "redirect:http://auth.gulimall.com/login.html";
    }
}
