package com.atguigu.gulimall.seckill.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.to.mq.SeckillOrderTo;
import com.atguigu.common.utils.R;
import com.atguigu.common.vo.MemberRespVo;
import com.atguigu.gulimall.seckill.feign.CouponFeignService;
import com.atguigu.gulimall.seckill.feign.ProductFeignService;
import com.atguigu.gulimall.seckill.interceptor.LoginUserInterceptor;
import com.atguigu.gulimall.seckill.service.SeckillService;
import com.atguigu.gulimall.seckill.to.SeckillSkuRedisTo;
import com.atguigu.gulimall.seckill.vo.SeckillSessionsWithSkus;
import com.atguigu.gulimall.seckill.vo.SeckillSkuVo;
import com.atguigu.gulimall.seckill.vo.SkuInfoVo;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SeckillServiceImpl implements SeckillService {
    @Autowired
    CouponFeignService couponFeignService;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    ProductFeignService productFeignService;

    @Autowired
    RedissonClient redissonClient;

    @Autowired
    RabbitTemplate rabbitTemplate;

    private final String SESSIONS_CACHE_PREFIX = "seckill:sessions:";

    private final String SKUKILL_CACHE_PREFIX = "seckill:skus";
    //TODO private final String SKUKILL_CACHE_PREFIX = "seckill:skus:"; 把所有session.getId()+"_"+sku.getSkuId().toString() -> sku.getSkuId().toString()

    private final String SKU_STOCK_SEMAPHORE = "seckill:stock:";

    /**
     * 上架最近3天的秒杀商品
     */
    @Override
    public void uploadSeckillSkuLatest3Days() {
        //1.扫描最近三天的所有秒杀场次
        R r = couponFeignService.getLatest3DaySession();
        if(r.getCode() == 0){
            List<SeckillSessionsWithSkus> sessionList = r.getData("data", new TypeReference<List<SeckillSessionsWithSkus>>() {
            });
            if(sessionList!=null && sessionList.size()>0){
                /**
                 * 2.如果远程调用传递回来的 秒杀场次 信息不为空，那么就将每场秒杀的所有商品信息上架到redis缓存
                 *      2.1缓存活动的信息：
                 *      key：seckill:sessions:秒杀场次startTime_endTime
                 *      value：skuIdList{1,2,3,4}
                 *
                 *      2.2缓存活动的关联商品信息：
                 *      key：秒杀场次的id
                 *      value：Hash
                 *      skuId1  skuId1的商品信息
                 *      skuId2  skuId2的商品信息
                 *      skuId3  skuId3的商品信息
                 */
                saveSessionInfos(sessionList);
                saveSessionSkuInfos(sessionList);
            }
        }
    }

    /**
     *      2.1缓存最近3天所有秒杀活动的信息：
     *      @param sessions 最近3天所有的秒杀场次
     *
     *      key：seckill:sessions:秒杀场次startTime_endTime
     *      value：skuIdList{1,2,3,4}
     */
    private void saveSessionInfos(List<SeckillSessionsWithSkus> sessions){
        //遍历最近3天所有的秒杀场次，将每一场秒杀活动都缓存到redis中
        sessions.stream().forEach(session->{
            //获取开始时间和结束时间
            Long startTime = session.getStartTime().getTime();
            Long endTime = session.getEndTime().getTime();
            Long nowTime = new Date().getTime();
            //组装redis缓存的key
            String key = SESSIONS_CACHE_PREFIX + startTime + "_" + endTime;

            //判断当前秒杀场次及其商品id是否已经缓存，只有当前秒杀场次不存在时才上架，防止定时任务重复上架
            if (!stringRedisTemplate.hasKey(key)) {
                //组装redis缓存的value 获取当前秒杀场次的所有商品id，即skuIds
                List<String> skuIds = session.getRelationSkus().stream().map(
                        sku-> session.getId()+"_"+sku.getSkuId().toString()
                ).collect(Collectors.toList());

                stringRedisTemplate.opsForList().leftPushAll(key, skuIds);
                stringRedisTemplate.expire(key,endTime-nowTime,TimeUnit.MILLISECONDS);//TODO 过期时间
            }
        });
    }

    /**
     *      2.2缓存每一场秒杀活动关联的商品信息：
     *      @param sessions 最近3天所有的秒杀场次，其中包含每场秒杀活动涉及的商品
     *
     *      key：seckill:skus:秒杀场次id
     *      value：Hash
     *      skuId1  skuId1的商品基本信息 秒杀信息
     *      skuId2  skuId2的商品基本信息 秒杀信息
     *      skuId3  skuId3的商品基本信息 秒杀信息
     */
    private void saveSessionSkuInfos(List<SeckillSessionsWithSkus> sessions){
        //遍历所有的秒杀活动
        sessions.stream().forEach(session->{
            BoundHashOperations<String, Object, Object> hashOps = stringRedisTemplate.boundHashOps(SKUKILL_CACHE_PREFIX);
            //TODO  BoundHashOperations<String, Object, Object> hashOps = stringRedisTemplate.boundHashOps(SKUKILL_CACHE_PREFIX+session.getId());
            //遍历每一场秒杀活动的所有商品
            session.getRelationSkus().stream().forEach(sku->{
                if (!hashOps.hasKey(session.getId()+"_"+sku.getSkuId().toString())) {
                    SeckillSkuRedisTo seckillSkuRedisTo = new SeckillSkuRedisTo();
                    //1.sku商品的基本信息
                    R r = productFeignService.getSkuInfoBySkuId(sku.getSkuId());
                    if(r.getCode() == 0){
                        SkuInfoVo skuInfo = r.getData("skuInfo", new TypeReference<SkuInfoVo>() {
                        });
                        seckillSkuRedisTo.setSkuInfo(skuInfo);
                    }

                    //2.sku商品的秒杀信息
                    BeanUtils.copyProperties(sku,seckillSkuRedisTo);

                    //3.sku商品所属秒杀活动的开始和结束时间
                    seckillSkuRedisTo.setStartTime(session.getStartTime().getTime());
                    seckillSkuRedisTo.setEndTime(session.getEndTime().getTime());

                    /**
                     * 4.sku商品的随机码 作用：
                     * 存在的问题：seckill?skuId=1 秒杀活动开始，通过脚本频繁发送当前请求，将秒杀的商品抢空
                     * 解决：seckill?skuId=1&key=随机码 随机码每次都不同，无法通过以上手段抢空秒杀商品
                     */
                    String randomCode = UUID.randomUUID().toString().replace("-", "");
                    seckillSkuRedisTo.setRandomCode(randomCode);

                    //上架一个sku商品
                    hashOps.put(session.getId()+"_"+sku.getSkuId().toString(),JSON.toJSONString(seckillSkuRedisTo));

                    /**
                     * 5.将本次商品要秒杀的数量作为信号量存储在缓存中：使用库存作为分布式信号量
                     *      5.1如果本次请求可以获取到缓存中的一个信号量，就将信号量减1，并访问数据库扣减库存
                     *      5.2如果本次请求连缓存中的信号量都获取不到，那么就直接返回没抢到秒杀的商品，无需再访问数据库了，提升系统速度
                     *      5.3请求当前商品的信号量也需要携带随机码，防止seckill?skuId=1请求频繁发送，使信号量被抢空
                     *      5.4具体操作：秒杀活动开始，访问商品并获取到随机码，秒杀当前商品，如果抢到，携带当前商品的随机码去扣减信号量
                     *
                     * 综上：信号量的获取也需要商品的随机码，与上面存储的商品随机码是同一个。最大的作用是限制数据库流量的访问
                     * key：seckill:stock:商品随机码
                     */
                    RSemaphore semaphore = redissonClient.getSemaphore(SKU_STOCK_SEMAPHORE + randomCode);
                    semaphore.trySetPermits(sku.getSeckillCount());
                    semaphore.expire(session.getEndTime().getTime()-new Date().getTime(),TimeUnit.MILLISECONDS);//TODO 过期时间
                }
            });
        });
    }

    /**
     * 获取当前时间正在参与秒杀的商品
     * @return
     */
    @Override
    public List<SeckillSkuRedisTo> getCurrentSeckillSkus() {
        //1.确定当前时间属于哪个秒杀场次
        Long nowTime = new Date().getTime();//当前时间
        //获取缓存中所有的seckill:session:*信息，遍历
        Set<String> keys = stringRedisTemplate.keys(SESSIONS_CACHE_PREFIX + "*");

        for (String key : keys) {
            //seckill:session:startTime_endTime
            String areaTimeStr = key.replace(SESSIONS_CACHE_PREFIX, "");//startTime_endTime
            Long startTime = Long.parseLong(areaTimeStr.split("_")[0]);
            Long endTime = Long.parseLong(areaTimeStr.split("_")[1]);
            if(nowTime > startTime && nowTime < endTime){
                //2.当前时间属于该秒杀场次，获取缓存中当前key的所有value即为当前场次的所有skuIds
                List<String> skuIdStrList = stringRedisTemplate.opsForList().range(key, 0, -1);
                BoundHashOperations<String, String, String> hashOps = stringRedisTemplate.boundHashOps(SKUKILL_CACHE_PREFIX);
                //根据skuId获取所有的商品信息
                List<String> skuInfos = hashOps.multiGet(skuIdStrList);
                if (skuInfos != null){
                    List<SeckillSkuRedisTo> skus = skuInfos.stream().map(skuInfo -> {
                        SeckillSkuRedisTo skuRedisTo = JSON.parseObject(skuInfo, SeckillSkuRedisTo.class);
                        return skuRedisTo;
                    }).collect(Collectors.toList());

                    //商品信息不为空时返回商品信息
                    return skus;
                }
                break;
            }
        }

        //商品信息为空
        return null;
    }

    /**
     * 根据商品id查询当前商品是否参与秒杀活动，展示在商品详情页
     * @param skuId
     * @return
     */
    @Override
    public SeckillSkuRedisTo getSkuSeckillInfo(Long skuId) {
        //1.找到所有需要参与秒杀的商品的key
        BoundHashOperations<String, String, String> hashOps = stringRedisTemplate.boundHashOps(SKUKILL_CACHE_PREFIX);
        Set<String> keys = hashOps.keys();
        if(keys != null && keys.size() > 0){
            String regx = "\\d_"+skuId; //只要是场次_skuId，就说明当前的商品参与了某一场秒杀活动
            for (String key : keys) {
                //key 1_35 1_40 场次_skuId
                if(Pattern.matches(regx,key)){
                    String skuInfoJson = hashOps.get(key);
                    SeckillSkuRedisTo skuRedisTo = JSON.parseObject(skuInfoJson, SeckillSkuRedisTo.class);
                    //商品详情页面展示的秒杀信息需要处理随机码，因为当前时间该商品可能还没有开始秒杀
                    if(new Date().getTime() >= skuRedisTo.getStartTime() && new Date().getTime() <= skuRedisTo.getEndTime()){
                        //当前商品正在参与秒杀，随机码无需处理
                        return skuRedisTo;
                    }else if(new Date().getTime() < skuRedisTo.getStartTime()){
                        //当前商品的秒杀活动还未开始，随机码要隐藏
                        skuRedisTo.setRandomCode(null);
                        return skuRedisTo;
                    }
                    //程序执行到这里表示，当前商品的这场秒杀活动已经结束，继续遍历，查询当前商品是否还有下一轮秒杀活动
                }
            }
        }
        return null;
    }

    /**
     * 秒杀商品，创建订单的业务
     * @param killId 场次id_skuId 1_35 1_40等等
     * @param key 随机码
     * @param num 秒杀订单数量
     * @return
     */
    @Override
    public String kill(String killId, String key, Integer num) {
        MemberRespVo memberResp = LoginUserInterceptor.loginUser.get();

        //1.获取当前秒杀商品的详情
        BoundHashOperations<String, String, String> hashOps = stringRedisTemplate.boundHashOps(SKUKILL_CACHE_PREFIX);
        String skuInfoJson = hashOps.get(killId);
        if(!StringUtils.isEmpty(skuInfoJson)){
            SeckillSkuRedisTo skuRedisInfo = JSON.parseObject(skuInfoJson, SeckillSkuRedisTo.class);
            Long startTime = skuRedisInfo.getStartTime();
            Long endTime = skuRedisInfo.getEndTime();
            Long nowTime = new Date().getTime();
            //2.开始校验商品秒杀时间的合法性
            if(nowTime >= startTime && nowTime <= endTime){
                String randomCode = skuRedisInfo.getRandomCode();//随机码
                String skuInfoKey = skuRedisInfo.getPromotionSessionId() + "_" + skuRedisInfo.getSkuId();//场次id_skuId
                //3.开始校验随机码和商品id的合法性
                if(randomCode.equals(key) && skuInfoKey.equals(killId)){
                    //4.开始校验单次购买数量的合法性
                    if (num <= skuRedisInfo.getSeckillLimit()) {
                        /**
                         * 5.校验当前用户是否多次购买(限购1件的话，单个用户只能购买1次，且只能购买1个)
                         * 幂等性处理的规定：只要秒杀成功，就在redis缓存中占一个位(userId_sessionId_skuId)
                         * 标识当前的用户userId 在该场秒杀活动中sessionId 购买过skuId商品了
                         *
                         * SETNX key:userId_sessionId_skuId value:购买num
                         * 只有不存在时才能占位，占位成功返回true，以此判断用户是否属于多次购买
                         */
                        String userSecKillKey = memberResp.getId()+"_"+skuInfoKey;
                        //自动过期，秒杀活动结束即过期
                        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(userSecKillKey, num.toString(),endTime-nowTime, TimeUnit.MILLISECONDS);
                        if(b){
                            //6.占位成功，尝试获取分布式信号量
                            RSemaphore semaphore = redissonClient.getSemaphore(SKU_STOCK_SEMAPHORE + randomCode);
                            boolean tryAcquire = semaphore.tryAcquire(num);
                            //7.获取到信号量，秒杀成功，发送MQ消息通知订单服务下单，同时生成订单号返回
                            if(tryAcquire){
                                String orderSn = IdWorker.getTimeId();
                                SeckillOrderTo seckillOrder = new SeckillOrderTo();
                                seckillOrder.setOrderSn(orderSn);
                                seckillOrder.setMemberId(memberResp.getId());
                                seckillOrder.setNum(num);
                                seckillOrder.setSkuId(skuRedisInfo.getSkuId());
                                seckillOrder.setPromotionSessionId(skuRedisInfo.getPromotionSessionId());
                                seckillOrder.setSeckillPrice(skuRedisInfo.getSeckillPrice());

                                rabbitTemplate.convertAndSend("order-event-exchange","order.seckill.order",seckillOrder);
                                return orderSn;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
}