package com.atguigu.gulimall.order.listener;

import com.atguigu.gulimall.order.entity.OrderEntity;
import com.atguigu.gulimall.order.service.OrderService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * 订单30分钟未支付定时关单监听器
 */
@RabbitListener(queues = "order.release.order.queue")
@Service
public class OrderCloseListener {
    @Autowired
    OrderService orderService;

    /**
     * 监听队列orderReleaseOrderQueue
     * 参数：消息类型 信道 消息主体 顺序可以随意替换
     */
    @RabbitHandler
    public void listener(OrderEntity orderEntity, Channel channel, Message message) throws IOException {
        System.out.println("订单30分钟未支付，订单已过期，准备关闭当前订单"+orderEntity.getOrderSn());
        try {
            orderService.closeOrder(orderEntity);
            //TODO 订单接收到消息队列的消息，这边准备关单解锁库存了。支付宝支付那边应该手动调用收单，让用户停止支付

            //手动确认收到消息 消息标签 批量确认
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        }catch (Exception e){
            channel.basicReject(message.getMessageProperties().getDeliveryTag(),true);
        }
    }
}
