package com.atguigu.gulimall.thirdparty.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "spring.alicloud.realname")
public class AliYunRealNameConfig {
    private String url;
    private String appCode;
    private String path;
}
