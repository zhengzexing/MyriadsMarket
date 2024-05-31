package com.atguigu.gulimall.seckill.shceduled;

import com.atguigu.gulimall.seckill.service.SeckillService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 秒杀商品的定时上架功能
 */
@Slf4j
@Service
public class SeckillSkuScheduled {
    @Autowired
    SeckillService seckillService;

    @Autowired
    RedissonClient redissonClient;

    private final String upload_lock = "seckill:upload:lock";

    /**
     * 定时上架最近3天秒杀活动的商品
     * 需要解决分布式场景重复上架的问题：使用分布式锁redisson
     */
    @Scheduled(cron = "*/10 * * * * ?")
    public void uploadSeckillSkuLatest3Days(){
        log.info("上架最近3天秒杀的商品信息");
        //分布式场景存在重复上架问题：使用分布式锁，让一个机器来执行，10秒后解锁，其他机器检查只要上架完毕就无需再上架了
        RLock lock = redissonClient.getLock(upload_lock);
        lock.lock(10, TimeUnit.SECONDS);//锁住10秒
        try {
            seckillService.uploadSeckillSkuLatest3Days();
        }catch (Exception e){

        }finally {
            lock.unlock();//释放
        }
    }
}
