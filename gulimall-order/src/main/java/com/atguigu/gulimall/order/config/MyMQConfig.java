package com.atguigu.gulimall.order.config;

import com.atguigu.gulimall.order.entity.OrderEntity;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * MQ消息队列的配置
 * @Bean 注入容器，容器中的Binding Queue Exchange在MQ中都会自动创建
 */
@Configuration
public class MyMQConfig {
    /**
     * 延迟队列
     * 参数：队列名称 持久化 排他队列(只有一个对象可以使用该队列) 自动删除 自定义属性(ttl,死信队列等)
     * 自定义队列属性：
     * x-dead-letter-exchange: order-event-exchange 死信交换机
     * x-dead-letter-routing-key: order.release.order 死信交换机转发的路由
     * x-message-ttl: 60000 当前队列消息的ttl(毫秒)
     * @return
     */
    @Bean
    public Queue orderDelayQueue(){
        //自定义的属性
        Map<String,Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange","order-event-exchange");
        arguments.put("x-dead-letter-routing-key","order.release.order");
        arguments.put("x-message-ttl",60000L);

        return new Queue("order.delay.queue", true, false, false, arguments);
    }

    /**
     * 死信交换机将死信转发到该正常队列，消费者消费该队列中的消息，验证订单是否过期未支付
     * @return
     */
    @Bean
    public Queue orderReleaseOrderQueue(){
        return new Queue("order.release.order.queue", true, false, false);
    }

    /**
     * 订单服务唯一的交换机，一般会创建成主题交换机(可以根据routing-key进行模糊匹配)
     * 支持收集死信，可做为死信交换机
     * 参数：交换机名称 持久化 自动删除 自定义属性
     * @return
     */
    @Bean
    public Exchange orderEventExchange(){
        return new TopicExchange("order-event-exchange", true, false);
    }

    /**
     * orderEventExchange交换机与orderDelayQueue队列的绑定关系
     * 绑定关系的出发点一定是交换机，目的地可以是队列或者交换机
     * 参数：目的地 目的地类型(队列或者交换机) 出发点(交换机) 路由键
     * @return
     */
    @Bean
    public Binding orderCreateOrderBinding(){
        return new Binding("order.delay.queue", Binding.DestinationType.QUEUE, "order-event-exchange", "order.create.order", null);
    }

    /**
     * orderEventExchange交换机与orderReleaseOrderQueue队列的绑定关系
     * @return
     */
    @Bean
    public Binding orderReleaseOrderBinding(){
        return new Binding("order.release.order.queue", Binding.DestinationType.QUEUE, "order-event-exchange", "order.release.order", null);
    }

    /**
     * orderEventExchange交换机与stockReleaseStockQueue队列的绑定关系
     * 订单的交换机直接和库存的释放队列进行绑定
     * @return
     */
    @Bean
    public Binding orderReleaseOtherBinding(){
        return new Binding("stock.release.stock.queue", Binding.DestinationType.QUEUE, "order-event-exchange", "order.release.other.#", null);
    }

    /**
     * 接收秒杀订单的消息队列orderSeckillOrderQueue
     */
    @Bean
    public Queue orderSeckillOrderQueue(){
        return new Queue("order.seckill.order.queue",true,false,false);
    }

    /**
     * 秒杀订单的消息队列orderSeckillOrderQueue与交换机orderEventExchange的绑定关系
     */
    @Bean
    public Binding orderSeckillOrderBinding(){
        return new Binding("order.seckill.order.queue", Binding.DestinationType.QUEUE,"order-event-exchange","order.seckill.order",null);
    }

}
