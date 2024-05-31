package com.atguigu.gulimall.product;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * 整合SpringCache简化Redis缓存的开发：
 * 优点：只需要使用注解就能完成缓存的操作，而不再需要那些重复的业务代码(查缓存->缓存没有->查数据库->写入缓存)
 * SpringCache对缓存的管理进行了分区操作
 * 1.引入依赖spring-boot-starter-cache和spring-boot-starter-data-redis
 * 2.写配置：
 * 2.1自动导入了RedisCacheConfiguration，自动配好了缓存管理器，对缓存进行分区操作
 * 把不同业务类型的缓存分到不同的区域中
 * 2.2配置使用redis作为缓存
 * 3.测试使用缓存：
 * @Cacheable :将数据保存到缓存的操作
 * @CacheEvict :将数据从缓存删除的操作
 * @CachePut :不影响方法执行更新缓存
 * @Caching :组合以上多个操作
 * @CacheConfig :在类级别共享缓存的相同配置
 * 开启缓存功能@EnableCaching
 * 只需要使用注解就可以完成缓存操作
 *
 */
@EnableRedisHttpSession
@EnableCaching
@EnableFeignClients("com.atguigu.gulimall.product.feign")
@SpringBootApplication
@EnableDiscoveryClient
public class GulimallProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(GulimallProductApplication.class, args);
    }

}
