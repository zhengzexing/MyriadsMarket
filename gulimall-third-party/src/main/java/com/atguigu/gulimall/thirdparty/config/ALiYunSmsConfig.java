package com.atguigu.gulimall.thirdparty.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "spring.alicloud.sms")
public class ALiYunSmsConfig {
    private String accessKeyID;
    private String accessKeySecret;
}
