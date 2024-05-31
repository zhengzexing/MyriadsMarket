package com.atguigu.gulimall.seckill;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * 1、整合Sentinel
 *  1）、导入依赖 spring-cloud-starter-alibaba-sentinel
 *  2）、下载sentinel控制台
 *  3）、配置 sentinel 控制台地址信息
 *  4）、在控制台调整参数、【默认所有的流控规则保存在内存中，重启失效】
 *
 * 2、每一个微服务都导入 actuator ：并配合 management.endpoints.web.exposure.include=* 启动请求实时监控
 * 3、自定义 sentinel 流控返回的数据
 *
 * 4、使用Sentinel来保护feign远程调用，设置熔断方法；
 *  1）、调用方的熔断保护：feign.sentinel.enable=true
 *  2）、调用方手动指定远程服务的降级策略（sentinel控制台）。远程服务被降级处理。触发我们的熔断回调方法
 *  3）、超大浏览的时候，必须牺牲一些远程服务。在服务的提供方（远程服务）指定降级策略；
 *      提供方是在运行，但是不允许自己的业务逻辑，返回的是默认的降级数据（限流的数据）
 *  4）、关于降级：
 *      给远程调用的方法设置降级，触发降级后会调用远程方法的熔断方法。
 *      给本地的方法设置降级，触发降级后，由于本地方法没有设置熔断，会调用流控返回的数据。
 *
 * 5、自定义受保护的资源
 *  1）、代码方式，可以在任意的代码片段设置资源名，细化到代码片段，给代码片段设置流控等规则
 *          try (Entry entry = SphU.entry("seckillSkus")) {
 *              //业务逻辑
 *          } catch(BlockException e) {}
 *
 *  2）、注解方式，可以在任意的方法上设置资源名，细化到方法，给方法设置流控等规则
 *  无论是1还是2两种方式都需要使用@SentinelResource设置限流后的返回数据
 *
 *  3）、不设置自定义受保护的资源，那么只有在controller层的方法才能被识别出来，设置流控等规则
 *
 *  自定义受保护的资源，一定要单独使用@SentinelResource(fallback方法名 fallbackClass类名)设置限流后的返回数据，否则页面会出现500
 *  URL请求受保护的资源，无需使用@SentinelResource，可以在配置类配置setUrlBlockHandler统一设置限流后返回的数据
 */
@EnableDiscoveryClient
@EnableRedisHttpSession
@EnableFeignClients
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class GulimallSeckillApplication {

    public static void main(String[] args) {
        SpringApplication.run(GulimallSeckillApplication.class, args);
    }

}
