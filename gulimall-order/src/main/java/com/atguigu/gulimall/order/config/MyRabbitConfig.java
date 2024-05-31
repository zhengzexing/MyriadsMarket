package com.atguigu.gulimall.order.config;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * 防止MQ消息丢失：
 * 1、做好消息的确认机制（publisher的confirm  consumer的手动ack）
 * 2、将每一个发生的消息都在数据库做好记录，定期将失败的消息再次发送（失败的原因在confirm和returnedMessage中可以查看）
 */
@Configuration
public class MyRabbitConfig {
    @Autowired
    RabbitTemplate rabbitTemplate;

    /**
     * 指定消息转换器为Json
     * 发送的消息对象自动转化为Json
     * @return
     */
    @Bean
    public MessageConverter messageConverter(){
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 定制RabbitTemplate
     * 1、服务收到消息就会回调
     *      1、spring.rabbitmq.publisher-confirms: true
     *      2、设置确认回调
     * 2、消息正确抵达队列就会进行回调
     *      1、spring.rabbitmq.publisher-returns: true
     *         spring.rabbitmq.template.mandatory: true
     *      2、设置确认回调ReturnCallback
     *
     * 3、消费端确认(保证每个消息都被正确消费，此时才可以broker删除这个消息)
     *      spring.rabbitmq.listener.simple.acknowledge-mode: manual 开启手动确认
     *      1、开启手动确认收货模式，只要没有手动ack，消息就一直处于unacked状态
     *      2、签收货物：在监听消息的方法@RabbitListener末尾执行channel.basciAck()手动确认
     *      3、拒收货物：在监听消息的方法@RabbitListener末尾执行channel.basciNAck()手动拒绝 并选择是否重新入队
     */
    @PostConstruct //MyRabbitConfig对象创建完成以后，执行这个方法
    public void initRabbitTemplate(){
        //设置消息确认回调
        rabbitTemplate.setConfirmCallback(new RabbitTemplate.ConfirmCallback() {
            /**
             * 只要生产者的消息成功到达服务器（消息代理Broker），就会自动回调该方法，ack为true
             * @param correlationData 当前消息的唯一关联数据（发送的消息的唯一id）
             * @param ack 消息发送成功还是失败
             * @param cause 失败的原因
             */
            @Override
            public void confirm(CorrelationData correlationData, boolean ack, String cause) {
                //服务器Broker接收到消息
            }
        });

        //设置消息抵达队列的确认回调
        rabbitTemplate.setReturnCallback(new RabbitTemplate.ReturnCallback() {
            /**
             * 消息正确的抵达队列，不会回调方法。有消息没有送达给指定的队列才会回调
             * @param message 投递失败的消息
             * @param replyCode 回复的状态码
             * @param replyText 回复的文本内容
             * @param exchange 生产者将该消息发给哪个交换机
             * @param routingKey 生产者发给交换机，交换机转发到队列的路由key
             */
            @Override
            public void returnedMessage(Message message, int replyCode, String replyText, String exchange, String routingKey) {
                //消息从交换机发送到队列报错误了。修改数据库的消息状态->错误
            }
        });
    }

}
