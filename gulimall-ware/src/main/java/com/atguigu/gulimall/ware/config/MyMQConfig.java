package com.atguigu.gulimall.ware.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 队列和交换机
 */
@Configuration
public class MyMQConfig {
    /**
     * 库存服务的交换机stockEventExchange
     * @return
     */
    @Bean
    public Exchange stockEventExchange(){
        return new TopicExchange("stock-event-exchange",true,false);
    }

    /**
     * 库存服务队列stockReleaseStockQueue
     * 解锁库存队列，消费者接收该队列中的延迟信息，开始解锁库存
     * @return
     */
    @Bean
    public Queue stockReleaseStockQueue(){
        return new Queue("stock.release.stock.queue",true,false,false);
    }

    /**
     * 库存服务队列stockDelayQueue
     * 库存服务的延迟队列，库存锁定成功后，消息在此队列中存储指定时间后再进行转发
     * 自定义属性：
     * x-dead-letter-exchange: order-event-exchange 死信交换机
     * x-dead-letter-routing-key: order.release.order 死信交换机转发的路由
     * x-message-ttl: 60000 当前队列消息的ttl(毫秒)
     * @return
     */
    @Bean
    public Queue stockDelayQueue(){
        Map<String,Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange","stock-event-exchange");
        arguments.put("x-dead-letter-routing-key","stock.release");
        arguments.put("x-message-ttl",120000L);

        return new Queue("stock.delay.queue",true,false,false,arguments);
    }

    /**
     * 交换机stockEventExchange和队列stockDelayQueue的绑定关系
     * @return
     */
    @Bean
    public Binding stockLockedBinding(){
        return new Binding("stock.delay.queue", Binding.DestinationType.QUEUE,"stock-event-exchange","stock.locked",null);
    }

    /**
     * 交换机stockEventExchange和队列stockReleaseStockQueue的绑定关系
     * @return
     */
    @Bean
    public Binding stockReleaseBinding(){
        return new Binding("stock.release.stock.queue", Binding.DestinationType.QUEUE,"stock-event-exchange","stock.release.#",null);
    }

}
