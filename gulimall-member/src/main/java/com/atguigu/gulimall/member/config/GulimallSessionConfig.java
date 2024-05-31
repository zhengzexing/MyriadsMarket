package com.atguigu.gulimall.member.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * 自定义配置spring session的作用域，解决子域session共享问题
 * spring session的默认作用域为当前服务auth.gulimall.com
 * 通过该配置类，配置成父域.gulimall.com
 */
@Configuration
public class GulimallSessionConfig {
    /**
     * 自定义cookie作用域，修改默认的作用域
     * @return
     */
    @Bean
    public CookieSerializer cookieSerializer(){
        DefaultCookieSerializer cookieSerializer = new DefaultCookieSerializer();
        cookieSerializer.setDomainName("gulimall.com");//修改默认的auth.gulimall.com作用域
        cookieSerializer.setCookieName("GULISESSION");
        return cookieSerializer;
    }

    /**
     * 修改redis默认的序列化方式，使用Json方式进行序列化
     */
    @Bean
    public RedisSerializer<Object> springSessionDefaultRedisSerializer(){
        return new GenericJackson2JsonRedisSerializer();
    }


}
