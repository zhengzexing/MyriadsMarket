package com.atguigu.gulimall.seckill.controller;

import com.atguigu.common.utils.R;
import com.atguigu.gulimall.seckill.service.SeckillService;
import com.atguigu.gulimall.seckill.to.SeckillSkuRedisTo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * 秒杀业务的后续库存处理：
 * 1.秒杀活动开始前，将所有要秒杀的商品数量进行库存锁定
 * 2.在秒杀活动结束以后30分钟，根据缓存中剩余的信号量对库存进行处理
 *      2.1如果信号量用完了，则商品锁定的库存不需要解锁
 *      2.2如果信号量有剩余，则将商品锁定的库存减去剩余的信号量
 */
@Slf4j
@Controller
public class SeckillController {
    @Autowired
    SeckillService seckillService;

    /**
     * 获取当前需要参与秒杀的商品
     */
    @ResponseBody
    @GetMapping("/currentSeckillSkus")
    public R getCurrentSeckillSkus(){
        List<SeckillSkuRedisTo> seckillSkus = seckillService.getCurrentSeckillSkus();
        return R.ok().put("data",seckillSkus);
    }

    /**
     * 根据商品id查询当前商品是否参与秒杀活动，展示在商品详情页
     * @return
     */
    @ResponseBody
    @GetMapping("/sku/seckill/{skuId}")
    public R getSkuSeckillInfo(@PathVariable("skuId") Long skuId){
        SeckillSkuRedisTo sku = seckillService.getSkuSeckillInfo(skuId);
        return R.ok().put("data",sku);
    }

    /**
     * 秒杀商品生成秒杀订单的业务
     */
    @GetMapping("/kill")
    public String seckill(@RequestParam("killId")String killId,
                          @RequestParam("key")String key,
                          @RequestParam("num")Integer num,
                          Model model){
        //1.判断是否登录(拦截器)
        //2.秒杀商品
        String orderSn = seckillService.kill(killId,key,num);
        model.addAttribute("orderSn",orderSn);
        return "success";
    }
}
