package com.atguigu.gulimall.order.service.impl;

import com.atguigu.gulimall.order.entity.OrderEntity;
import com.atguigu.gulimall.order.entity.OrderReturnReasonEntity;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.order.dao.OrderItemDao;
import com.atguigu.gulimall.order.entity.OrderItemEntity;
import com.atguigu.gulimall.order.service.OrderItemService;

//@RabbitListener(queues = {"hello-java-queue"})
@Service("orderItemService")
public class OrderItemServiceImpl extends ServiceImpl<OrderItemDao, OrderItemEntity> implements OrderItemService {

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<OrderItemEntity> page = this.page(
                new Query<OrderItemEntity>().getPage(params),
                new QueryWrapper<OrderItemEntity>()
        );

        return new PageUtils(page);
    }

    /**
     * rabbitmq接收消息的方式
     * queues声明要监听的所有队列
     * 参数1.原生消息的详细信息。头+体
     * 参数2.T<发送的消息的类型> OrderReturnReasonEntity content 消息体的就会将内容自动转化为对应的类型
     * 参数3.Channel channel 当前数据传输的通道
     *
     * queue:可以很多人都来监听队列的信息。只要消息被接收，队列删除消息，而且只能有一个收到该信息
     *
     * @RabbitListener 可以在类和方法上使用 类：这个类中标注了@RabbitHandler的方法都会接收队列中的消息 方法：该方法接收队列中的消息
     * @RabbitHandler 只能在方法上使用 接收@RabbitListener中队列的消息 并且可以重载接收不同的消息
     * OrderReturnReasonEntity bodyContent  receiveMessage1()方法只接收该类型的消息体
     * OrderEntity bodyContent  receiveMessage2()方法只接收该类型的消息体
     */
    //@RabbitListener(queues = {"hello-java-queue"})
    //@RabbitHandler //OrderReturnReasonEntity类型的消息体只能被改方法接收到
    public void receiveMessage1(Message msg,
                               OrderReturnReasonEntity bodyContent,
                               Channel channel){
        byte[] body = msg.getBody();//消息体，存储的信息，转换为原始信息麻烦，可以使用方法的参数自动转换
        MessageProperties messageProperties = msg.getMessageProperties();//消息头属性信息

        System.out.println("接收到的消息："+msg+"====消息体内容："+bodyContent);

        //消息通道channel内自增的标签
        long deliveryTag = messageProperties.getDeliveryTag();

        try {
            //签收货物，手动ack消息 参数：ack信道中哪条消息 批量签收(false只签收自己的,true签收tag之前所有未签收的)
            channel.basicAck(deliveryTag,false);
            //拒收货物，手动unacked 参数：编号 批量拒收 拒收的消息是否重新入队
            //channel.basicNack(deliveryTag,false,true);
        } catch (IOException e) {

        }
    }

    //@RabbitHandler //OrderEntity类型的消息体只能被改方法接收
    public void receiveMessage2(Message msg,
                               OrderEntity bodyContent,
                               Channel channel){
        byte[] body = msg.getBody();//消息体，存储的信息，转换为原始信息麻烦，可以使用方法的参数自动转换
        MessageProperties messageProperties = msg.getMessageProperties();//消息头属性信息

        System.out.println("接收到的消息："+msg+"====消息体内容："+bodyContent);
    }
}