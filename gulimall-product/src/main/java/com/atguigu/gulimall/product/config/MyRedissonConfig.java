package com.atguigu.gulimall.product.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * 配置Redisson
 * 之前是使用redis来操作分布式锁，实现锁的一些基本功能，现在使用更高级的redisson
 * 以后使用redisson作为所有分布式锁，分布式对象等功能框架
 * Redisson相比于手写的redis分布式锁的好处：
 * 1.Redisson分布式锁可以自动续期 如果业务超长 运行期间会自动给锁续上新的30s。不用担心业务时间超长，锁自动过期被删除。锁默认的过期时间是30s
 * 2.加锁的业务只要运行完成，就不会给当前锁续期，即使不手动解锁，锁默认在30s以后自动删除
 * 3.如果指定了自动解锁的时间(锁时间到了也不会自动续期)，指定的时间一定要大于业务执行的时间，否则自动解锁后，执行的unlock会抛异常(当前线程的锁已不存在)
 */
@Configuration
public class MyRedissonConfig {
    /**
     * 所有对redisson的使用都是通过RedissonClient对象来操作的
     * @return
     * @throws IOException
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redisson() throws IOException {
        //1.创建配置
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://192.168.88.131:6379");
        //2.根据Config创建出RedissonClient实例
        return Redisson.create(config);
    }

}
