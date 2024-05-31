package com.atguigu.gulimall.seckill.shceduled;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HelloSchedule {
    /**
     * 定时任务：
     * 常用的cron表达式：秒 分 时 日 月 周 年(可选) 日和周有一个位置出现(?)
     * 1.允许有7位
     * 2.在周的位置 1-7代表周日到周六
     *
     * 常用的cron表达式和springboot的cron表达式的区别：
     * springboot自带的@Scheduled定时任务
     * cron表达式：
     * 1.只允许有6位，不允许第7位 年
     * 2.在周的位置 1-7代表周一到周日
     * 3.springboot的定时任务默认是阻塞的，只有上一个任务执行完毕才会执行下一个任务：
     *  3.1使用异步编排，将定时任务中的业务异步编排交给我们自己的线程池
     *  3.2修改配置文件，增大springboot定时任务的线程数(默认是1) spring.task.scheduling.pool.size=5;  自动配置类TaskSchedulingAutoConfiguration
     *  3.3使用异步任务，开启@EnableAsync注解，在执行的方法上添加@Async  自动配置类TaskExecutionAutoConfiguration
     *
     * 使用定时任务+异步任务解决秒杀商品上架功能
     */
    /*@Async
    @Scheduled(cron = "* * * * * ?")
    public void hello(){
        log.info("hello world");
    }*/
}
