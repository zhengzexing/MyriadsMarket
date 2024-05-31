package com.atguigu.gulimall.order.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * 解决Feign远程调用，创建新的request请求，丢失原来的请求头及其中的cookie userId问题
 * 创建的新的request请求会调用拦截器
 * 添加一个request请求拦截器
 */
@Configuration
public class GulimallFeignConfig {
    @Bean("requestInterceptor")
    public RequestInterceptor requestInterceptor(){
        /**
         * request请求拦截器，在发送请求之前，将原来的请求头添加回去，防止请求头丢失
         */
        return new RequestInterceptor(){
            @Override
            public void apply(RequestTemplate requestTemplate) {
                //RequestContextHolder(在ThreadLocal中)获取旧的request请求中的请求头信息，将它们放到新的request请求中
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if(attributes!=null){
                    HttpServletRequest request = attributes.getRequest();//旧的请求，浏览器发送过来的携带用户信息的
                    //同步旧请求请求头的数据 cookie 到新的请求中
                    String cookie = request.getHeader("Cookie");
                    //新请求 添加请求头
                    requestTemplate.header("Cookie",cookie);
                }
            }
        };
    }
}
