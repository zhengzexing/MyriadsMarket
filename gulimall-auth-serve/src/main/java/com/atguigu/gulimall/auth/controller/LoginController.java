package com.atguigu.gulimall.auth.controller;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.constant.AuthConstant;
import com.atguigu.common.utils.R;
import com.atguigu.common.vo.MemberRespVo;
import com.atguigu.gulimall.auth.feign.MemberFeignService;
import com.atguigu.gulimall.auth.feign.ThirdPartFeignService;
import com.atguigu.gulimall.auth.vo.UserLoginVo;
import com.atguigu.gulimall.auth.vo.UserRegisterVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Controller
public class LoginController {
    @Autowired
    ThirdPartFeignService thirdPartFeignService;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    MemberFeignService memberFeignService;

    /**
     * 访问登录页面
     */
    @GetMapping("/login.html")
    public String loginPage(HttpSession session){
        Object attribute = session.getAttribute(AuthConstant.LOGIN_USER);
        if(attribute == null){
            //session中没有"loginUser"，未登录状态
            return "login";
        }

        //session中有"loginUser"，已登录
        return "redirect:http://gulimall.com";
    }

    /**
     * 注册或者登录时远程调用第三方服务进行短信验证
     * @return
     */
    @ResponseBody
    @GetMapping("/sms/sendcode")
    public R sendCode(@RequestParam("phone") String phone){
        R r = thirdPartFeignService.sendCode(phone);
        return r;
    }

    @PostMapping("/register")
    public String register(@Valid UserRegisterVo userRegisterVo,
                           BindingResult result,
                           RedirectAttributes redirectAttributes){
        if (result.hasErrors()){
            Map<String, String> errors = result.getFieldErrors().stream().collect(
                    Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage)
            );
            //校验出现错误，回到注册页面
            //model.addAttribute("errors",errors);
            redirectAttributes.addFlashAttribute("errors",errors);
            return "redirect:http://auth.gulimall.com/reg.html";
        }

        //校验没问题，调用远程服务，开始注册
        String key = AuthConstant.SMS_CODE_CACHE_PREFIX + userRegisterVo.getPhone();
        if (stringRedisTemplate.hasKey(key)) {
            //查询redis缓存中正确的验证码
            String querySmsCode = stringRedisTemplate.boundValueOps(key).get();
            //与用户输入的验证码进行比对
            if(userRegisterVo.getCode().equals(querySmsCode.split("_")[0])){
                //验证码正确，开始注册，并删除验证码的缓存
                stringRedisTemplate.delete(key);
                R regist = memberFeignService.regist(userRegisterVo);
                if(regist.getCode() == 0){
                    //远程调用注册用户成功
                    //注册成功回到登录页面
                    return "redirect:http://auth.gulimall.com/login.html";
                }else{
                    //远程调用注册用户出现异常
                    Map<String,String> errors = new HashMap<>();
                    errors.put("msg",regist.getData("msg",new TypeReference<String>(){}));
                    redirectAttributes.addFlashAttribute("errors",errors);
                    return "redirect:http://auth.gulimall.com/reg.html";
                }
            }
        }

        //验证码过期或者验证码错误，返回注册页面
        Map<String,String> errors = new HashMap<>();
        errors.put("code","验证码过期或者验证码错误，请重试");
        redirectAttributes.addFlashAttribute("errors",errors);
        return "redirect:http://auth.gulimall.com/reg.html";
    }

    @PostMapping("/login")
    public String login(UserLoginVo userLoginVo,
                        RedirectAttributes redirectAttributes,
                        HttpSession session){
        //远程调用，匹配账号和密码是否正确
        R login = memberFeignService.login(userLoginVo);
        if(login.getCode() == 0){
            //登录成功，保存用户数据
            MemberRespVo memberRespVo = login.getData("data", new TypeReference<MemberRespVo>() {
            });
            log.info("登录成功，用户信息为：{}",memberRespVo.toString());

            session.setAttribute(AuthConstant.LOGIN_USER,memberRespVo);
            //跳转到首页
            return "redirect:http://gulimall.com";
        }else{
            //登录失败，回到登录页面
            Map<String,String> errors = new HashMap<>();
            errors.put("msg",login.getData("msg",new TypeReference<String>(){}));
            redirectAttributes.addFlashAttribute("errors",errors);
            return "redirect:http://auth.gulimall.com/login.html";
        }
    }
}
