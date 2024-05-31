package com.atguigu.gulimall.thirdparty.controller;

import com.atguigu.common.constant.AuthConstant;
import com.atguigu.common.exception.BizCodeEnum;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.thirdparty.component.SmsComponent;
import com.atguigu.gulimall.thirdparty.util.CommonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sms")
public class SmsSendController {
    @Autowired
    SmsComponent smsComponent;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    /**
     * 发送短信验证码，短信验证码有效时间为3分钟，但超过1分钟就可以重新发送
     * @param phone
     * @return
     */
    @GetMapping("/sendcode")
    public R sendCode(@RequestParam("phone") String phone){
        if(CommonUtil.checkPhone(phone)){
            //先判断redis中是否有该手机号的验证码，没有的话再发送新的验证码
            String key = AuthConstant.SMS_CODE_CACHE_PREFIX + phone;
            if(stringRedisTemplate.hasKey(key)){
                String value = stringRedisTemplate.opsForValue().get(key);
                long cacheTime = Long.parseLong(value.split("_")[1]);
                if (System.currentTimeMillis() - cacheTime < 60000) {
                    //获取验证码的频率太高，每分钟只能发送一次，每个验证码的有效时间为三分钟
                    return R.error(BizCodeEnum.SMS_CODE_EXCEPTION.getCode(),BizCodeEnum.SMS_CODE_EXCEPTION.getMsg());
                }
            }

            //缓存中没有验证码时直接发送短信
            //缓存中有验证码但是没有频繁获取，距离上一条已超过一分钟，也直接发送
            try {
                boolean isSuccess = smsComponent.sendSms(phone);
                if(isSuccess){
                    return R.ok().put("result","验证码已发送，3分钟内有效");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }else{
            return R.error(BizCodeEnum.VALID_EXCEPTION.getCode(),"手机号码格式不正确，请重新输入");
        }
        return R.error(BizCodeEnum.UNKNOW_EXCEPTION.getCode(),"验证码发送失败");
    }
}
