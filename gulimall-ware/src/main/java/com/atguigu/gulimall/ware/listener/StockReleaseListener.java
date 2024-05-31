package com.atguigu.gulimall.ware.listener;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.to.mq.OrderTo;
import com.atguigu.common.to.mq.StockDetailTo;
import com.atguigu.common.to.mq.StockLockedTo;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.ware.entity.WareOrderTaskDetailEntity;
import com.atguigu.gulimall.ware.entity.WareOrderTaskEntity;
import com.atguigu.gulimall.ware.feign.OrderFeignService;
import com.atguigu.gulimall.ware.service.WareOrderTaskDetailService;
import com.atguigu.gulimall.ware.service.WareOrderTaskService;
import com.atguigu.gulimall.ware.service.WareSkuService;
import com.atguigu.gulimall.ware.vo.OrderVo;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * 库存解锁监听器
 */
@Service
@RabbitListener(queues = "stock.release.stock.queue")
public class StockReleaseListener {
    @Autowired
    WareSkuService wareSkuService;

    @Autowired
    WareOrderTaskService orderTaskService;

    @Autowired
    WareOrderTaskDetailService orderTaskDetailService;

    @Autowired
    OrderFeignService orderFeignService;

    /**
     * 监听消息队列，自动解锁库存
     * 下订单成功，远程调用库存锁定成功，在订单业务继续调用远程服务发生异常，之前的库存锁定需要回滚
     */
    @RabbitHandler
    public void handleStockLockedRelease(StockLockedTo stockLockedTo, Message message, Channel channel) throws IOException {
        System.out.println("收到解锁库存的消息，即将准备解锁库存");
        try {
            wareSkuService.unlockStock(stockLockedTo);
            //方法顺利执行结束，没有抛出任何异常，说明消息处理成功，该解锁的库存都已经解锁，不该解锁的也没有解锁，确认消息
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        }catch (Exception e){
            //解锁库存中有异常，消息重新入队
            channel.basicReject(message.getMessageProperties().getDeliveryTag(),true);
        }
    }

    /**
     * 监听消息队列，自动解锁库存
     * 订单服务中的订单关闭，订单状态修改为4，需要将锁定的库存释放
     * 订单状态修改为4，可能是30分钟未支付变成4，也可能是用户取消了订单变成4
     */
    @RabbitHandler
    public void handleOrderCloseRelease(Message message, Channel channel, OrderTo orderTo) throws IOException {
        System.out.println("收到订单关闭的消息，即将准备解锁库存");
        try {
            wareSkuService.unlockStock(orderTo);
            //方法顺利执行结束，没有抛出任何异常，说明消息处理成功，该解锁的库存都已经解锁，不该解锁的也没有解锁，确认消息
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        }catch (Exception e){
            //解锁库存中有异常，消息重新入队
            channel.basicReject(message.getMessageProperties().getDeliveryTag(),true);
        }
    }
}
