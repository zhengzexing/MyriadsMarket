package com.atguigu.gulimall.search.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springboot整合es，使用elasticsearch-rest-high-level-client操作es
 * 1.导入依赖
 * 2.编写配置，给容器中注入一个RestHighLevelClient
 * 3.参照官方API
 */
@Configuration
public class GulimallElasticSearchConfig {
    public static final RequestOptions COMMON_OPTIONS;

    /**
     * 签名，有签名才可以操作
     */
    static {
        RequestOptions.Builder builder = RequestOptions.DEFAULT.toBuilder();



        COMMON_OPTIONS = builder.build();
    }


    /**
     * 操作es的客户端
     * @return
     */
    @Bean
    public RestHighLevelClient client() {
        RestClientBuilder builder = RestClient.builder(
                new HttpHost("192.168.88.131", 9200, "http")
        );
        return new RestHighLevelClient(builder);
    }

}
