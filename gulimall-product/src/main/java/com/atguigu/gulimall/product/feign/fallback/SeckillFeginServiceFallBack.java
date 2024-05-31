package com.atguigu.gulimall.product.feign.fallback;

import com.atguigu.common.exception.BizCodeEnum;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.product.feign.SeckillFeignService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 对秒杀服务请求的调用失败后，调用熔断方法
 */
@Slf4j
@Component
public class SeckillFeginServiceFallBack implements SeckillFeignService {
    /**
     * 熔断后调用的方法
     * @param skuId
     * @return
     */
    @Override
    public R getSkuSeckillInfo(Long skuId) {
        log.warn("秒杀服务远程方法调用超时，请稍后重试...调用熔断方法getSkuSeckillInfo");
        return R.error(BizCodeEnum.REQUEST_TIMEOUT_EXCEPTION.getCode(), BizCodeEnum.REQUEST_TIMEOUT_EXCEPTION.getMsg());
    }
}
