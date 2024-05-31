package com.atguigu.gulimall.order.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.constant.OrderConstant;
import com.atguigu.common.exception.NoStockException;
import com.atguigu.common.to.mq.OrderTo;
import com.atguigu.common.to.mq.SeckillOrderTo;
import com.atguigu.common.utils.R;
import com.atguigu.common.vo.MemberRespVo;
import com.atguigu.gulimall.order.entity.OrderItemEntity;
import com.atguigu.gulimall.order.entity.PaymentInfoEntity;
import com.atguigu.gulimall.order.enume.OrderStatusEnum;
import com.atguigu.gulimall.order.feign.CartFeignService;
import com.atguigu.gulimall.order.feign.MemberFeignService;
import com.atguigu.gulimall.order.feign.ProductFeignService;
import com.atguigu.gulimall.order.feign.WareFeignService;
import com.atguigu.gulimall.order.interceptor.LoginUserInterceptor;
import com.atguigu.gulimall.order.service.OrderItemService;
import com.atguigu.gulimall.order.service.PaymentInfoService;
import com.atguigu.gulimall.order.to.OrderCreateTo;
import com.atguigu.gulimall.order.vo.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
//import io.seata.spring.annotation.GlobalTransactional;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.order.dao.OrderDao;
import com.atguigu.gulimall.order.entity.OrderEntity;
import com.atguigu.gulimall.order.service.OrderService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;


@Service("orderService")
public class OrderServiceImpl extends ServiceImpl<OrderDao, OrderEntity> implements OrderService {
    @Autowired
    MemberFeignService memberFeignService;

    @Autowired
    CartFeignService cartFeignService;

    @Autowired
    ThreadPoolExecutor executor;

    @Autowired
    WareFeignService wareFeignService;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    ProductFeignService productFeignService;

    @Autowired
    OrderItemService orderItemService;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    PaymentInfoService paymentInfoService;

    private ThreadLocal<OrderSubmitVo> submitVoThreadLocal = new ThreadLocal<>();

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<OrderEntity> page = this.page(
                new Query<OrderEntity>().getPage(params),
                new QueryWrapper<OrderEntity>()
        );

        return new PageUtils(page);
    }

    /**
     * 封装订单确认页面的数据
     * @return
     */
    @Override
    public OrderConfirmVo confirmOrder() throws ExecutionException, InterruptedException {
        //当前登录的用户
        MemberRespVo memberRespVo = LoginUserInterceptor.loginUser.get();
        //订单确认页要返回的对象
        OrderConfirmVo orderConfirmVo = new OrderConfirmVo();

        //当前主线程请求request中的数据
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

        //1.会员的收货地址列表
        CompletableFuture<Void> getAddressListTask = CompletableFuture.runAsync(() -> {
            //将主线程中的请求数据设置到当前分支线程的请求数据中
            RequestContextHolder.setRequestAttributes(requestAttributes);

            R r = memberFeignService.getAddress(memberRespVo.getId());
            if (r.getCode() == 0) {
                List<MemberAddressVo> memberAddress = r.getData("memberAddress", new TypeReference<List<MemberAddressVo>>() {
                });
                orderConfirmVo.setAddress(memberAddress);
            }
        }, executor);


        //2.远程查询购物车选中的购物项
        CompletableFuture<Void> getCartItemsTask = CompletableFuture.runAsync(() -> {
            //将主线程中的请求数据设置到当前分支线程的请求数据中
            RequestContextHolder.setRequestAttributes(requestAttributes);

            List<OrderItemVo> checkedItems = cartFeignService.getCurrentUserCartItems();
            orderConfirmVo.setItems(checkedItems);
        }, executor).thenRunAsync(()->{
            //查询这些被选中的商品是否还有库存
            List<OrderItemVo> items = orderConfirmVo.getItems();
            if(items != null){
                List<Long> skuIds = items.stream().map(OrderItemVo::getSkuId).collect(Collectors.toList());
                R r = wareFeignService.getSkusHasStock(skuIds);
                List<SkuStockVo> skuStockVos = r.getData("data", new TypeReference<List<SkuStockVo>>() {
                });
                //将库存的信息转化为Map<Long,Boolean>
                Map<Long, Boolean> stockMap = skuStockVos.stream().collect(Collectors.toMap(SkuStockVo::getSkuId, SkuStockVo::getHasStock));
                orderConfirmVo.setStocks(stockMap);
            }
        },executor);


        //3.查询用户的积分
        orderConfirmVo.setIntegration(memberRespVo.getIntegration());

        //4.其他总价格等属性自动计算

        //5.防订单重复提交令牌
        String token = UUID.randomUUID().toString().replace("-", "");
        orderConfirmVo.setOrderToken(token);//返回给前端页面
        //将令牌也保存到redis中，后续提交订单根据前端页面的token与redis中的令牌进行对比，相同就创建订单，删除令牌，防止订单的重复提交
        stringRedisTemplate.opsForValue().set(OrderConstant.USER_ORDER_TOKEN_PREFIX+memberRespVo.getId(),token,30, TimeUnit.MINUTES);

        CompletableFuture.allOf(getAddressListTask,getCartItemsTask).get();

        return orderConfirmVo;
    }

    /**
     * 下单操作：
     * 1.验证令牌
     * 2.生成订单
     * 3.验证价格
     * 4.保存订单
     * 5.锁定库存
     * 锁定库存属于远程调用，本地的事务无法控制远程的事务，需要引入分布式事务seata
     *
     * seata的AT模式只适用于一些并发量小的后台管理系统，对于商城的订单服务这种高并发场景并不适用
     * 所以在这里我们使用消息队列的方式来解决，在远程调用扣减积分的服务中，一旦发生错误，就发生消息给消息队列
     * 使库存服务收到消息，自己回滚，解锁被锁定的库存
     * @param vo
     * @return
     */
    //@GlobalTransactional //全局事务，控制远程调用的方法
    @Transactional //本地事务，控制当前方法
    @Override
    public SubmitOrderResponseVo submitOrder(OrderSubmitVo vo) {
        //用户登录信息
        MemberRespVo memberRespVo = LoginUserInterceptor.loginUser.get();

        //返回数据对象
        SubmitOrderResponseVo responseVo = new SubmitOrderResponseVo();
        responseVo.setCode(0);//默认成功，后续发生任何异常都代表订单提价失败，改变状态码

        //将订单提交时携带的数据对象存储到threadLocal中
        submitVoThreadLocal.set(vo);

        //1.验证令牌的正确性以及是否过期(令牌的对比和删除必须保证原子性，否则两个提交订单的请求间隔很短会导致在令牌删除之前，两个请求同时进入业务处理阶段，生成两个一样的订单)
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";//0令牌对比失败 1令牌对比成功且删除
        String orderToken = vo.getOrderToken();//前端页面的令牌
        //原子验证和删除令牌的结果
        Long result = stringRedisTemplate.execute(new DefaultRedisScript<Long>(script, Long.class), Arrays.asList(OrderConstant.USER_ORDER_TOKEN_PREFIX + memberRespVo.getId()), orderToken);
        if(result == 0L){
            //验证并删除token失败
            responseVo.setCode(1);
            return responseVo;
        }else{
            //验证并删除token成功
            //2.创建订单基本信息，订单项信息，订单价格
            OrderCreateTo order = createOrder();
            //3.验证价格（将页面显示的应付金额和后端根据数据库实时价格计算的应付金额进行比较）
            BigDecimal payAmount = order.getOrder().getPayAmount();//后端重新计算的金额
            BigDecimal payPrice = vo.getPayPrice();//前端页面显示的金额
            if (Math.abs(payAmount.subtract(payPrice).doubleValue())<0.01) {
                //后端价格与页面显示的价格偏差在指定范围内
                //4.保存订单到数据库
                saveOrder(order);

                //5.锁定库存，只要库存不足就抛异常，让所有事务回滚
                //创建需要锁定的库存对象
                WareSkuLockVo wareSkuLockVo = new WareSkuLockVo();
                wareSkuLockVo.setOrderSn(order.getOrder().getOrderSn());
                if(order.getOrderItems() != null && order.getOrderItems().size() > 0){
                    List<OrderItemVo> orderItemVos = order.getOrderItems().stream().map(item -> {
                        OrderItemVo orderItemVo = new OrderItemVo();
                        orderItemVo.setSkuId(item.getSkuId());
                        orderItemVo.setCount(item.getSkuQuantity());
                        orderItemVo.setTitle(item.getSkuName());
                        return orderItemVo;
                    }).collect(Collectors.toList());
                    wareSkuLockVo.setLocks(orderItemVos);
                }
                //远程调用锁定库存
                R r = wareFeignService.orderLockStock(wareSkuLockVo);
                if(r.getCode() == 0){
                    //锁定成功
                    responseVo.setOrder(order.getOrder());

                    //6.远程调用扣减积分
                    //int i=10/0;//模拟异常，测试分布式事务@GlobalTransaction注解

                    //7.订单创建成功，发送消息给MQ的延迟队列30分钟后判断是否支付
                    rabbitTemplate.convertAndSend("order-event-exchange","order.create.order",order.getOrder());

                    return responseVo;
                }else{
                    //锁定失败，库存不足
                    String msg = (String) r.get("msg");
                    throw new NoStockException(msg);
                    //responseVo.setCode(3);
                    //return responseVo;
                }

            }else{
                //后端价格与页面显示的价格存在偏差
                responseVo.setCode(2);
                return responseVo;
            }
        }
    }

    /**
     * 保存订单到数据库
     * @param order
     */
    private void saveOrder(OrderCreateTo order) {
        //订单基本信息
        OrderEntity orderEntity = order.getOrder();
        orderEntity.setModifyTime(new Date());
        orderEntity.setCreateTime(new Date());
        this.save(orderEntity);

        //订单中的商品项
        List<OrderItemEntity> orderItems = order.getOrderItems();
        /*for (OrderItemEntity orderItem : orderItems) {
            orderItemService.save(orderItem);
        }*/
        orderItemService.saveBatch(orderItems);
    }

    /**
     * 创建订单
     * @return
     */
    private OrderCreateTo createOrder(){
        //返回的订单对象
        OrderCreateTo orderCreateTo = new OrderCreateTo();

        //订单号
        String orderSn = IdWorker.getTimeId();

        //1.生成订单基本信息
        OrderEntity orderEntity = buildOrder(orderSn);

        //2.生成订单支付中的每一个购物项信息(这里的购物项信息实际就是购物车选中的那些购物项)
        List<OrderItemEntity> orderItemEntities = buildOrderItems(orderSn);
        //选中的购物项信息
        orderCreateTo.setOrderItems(orderItemEntities);

        //3.根据所有选中的订单项价格进行计算，计算最终应该支付的价格
        orderEntity = computePrice(orderEntity,orderItemEntities);
        //订单基本信息
        orderCreateTo.setOrder(orderEntity);

        return orderCreateTo;
    }

    /**
     * 进行计价，计算订单最终应该支付的价格
     *
     * @param orderEntity
     * @param orderItemEntities
     * @return
     */
    private OrderEntity computePrice(OrderEntity orderEntity, List<OrderItemEntity> orderItemEntities) {
        //订单中所有商品项的总价格
        BigDecimal total = new BigDecimal("0.0");
        //订单中所有商品项的总优惠
        BigDecimal coupon = new BigDecimal("0.0");
        BigDecimal integration = new BigDecimal("0.0");
        BigDecimal promotion = new BigDecimal("0.0");
        //订单支付后可以获得的总积分和总成长值
        BigDecimal gift = new BigDecimal("0.0");
        BigDecimal growth = new BigDecimal("0.0");

        //遍历订单中的每一个商品项，对每一个商品项的价格进行相加得到总价格
        for (OrderItemEntity orderItem : orderItemEntities) {
            //每个商品项的单价*商品项的数量-优惠价格 得到每个商品项总的价格
            total = total.add(orderItem.getRealAmount());
            //得到每个商品项的优惠价格
            coupon = coupon.add(orderItem.getCouponAmount());
            integration = integration.add(orderItem.getIntegrationAmount());
            promotion = promotion.add(orderItem.getPromotionAmount());
            //得到每个商品项可以获得的积分和成长值
            gift = gift.add(new BigDecimal(orderItem.getGiftIntegration().toString()));
            growth = growth.add(new BigDecimal(orderItem.getGiftGrowth().toString()));
        }

        //继续完善订单的相关信息
        //设置订单总的优惠信息，即每个商品项经过优惠后，一共优惠了多少
        orderEntity.setCouponAmount(coupon);
        orderEntity.setIntegrationAmount(integration);
        orderEntity.setPromotionAmount(promotion);
        //设置订单中所有商品项的总价格（前面遍历已经去除每个商品项优惠的价格）
        orderEntity.setTotalAmount(total);
        //设置商品项加上运费后实际应该付费的总价格
        orderEntity.setPayAmount(total.add(orderEntity.getFreightAmount()));
        //设置订单支付后总共可以获得的积分和成长值
        orderEntity.setIntegration(gift.intValue());
        orderEntity.setGrowth(growth.intValue());

        return orderEntity;
    }

    /**
     * 生成订单的基本信息
     * @return
     */
    private OrderEntity buildOrder(String orderSn) {
        //登录的用户
        MemberRespVo memberRespVo = LoginUserInterceptor.loginUser.get();
        //创建的订单实体
        OrderEntity orderEntity = new OrderEntity();

        //设置订单的所属用户id
        orderEntity.setMemberId(memberRespVo.getId());
        //设置订单号
        orderEntity.setOrderSn(orderSn);
        //远程查询收货人，收货地址和运费信息
        OrderSubmitVo orderSubmitVo = submitVoThreadLocal.get();
        R r = wareFeignService.getFare(orderSubmitVo.getAddrId());
        FareVo fare = r.getData("data", new TypeReference<FareVo>() {
        });
        //设置收货地址信息
        orderEntity.setReceiverProvince(fare.getAddress().getProvince());
        orderEntity.setReceiverCity(fare.getAddress().getCity());
        orderEntity.setReceiverRegion(fare.getAddress().getRegion());
        orderEntity.setReceiverDetailAddress(fare.getAddress().getDetailAddress());
        //收货人信息
        orderEntity.setReceiverName(fare.getAddress().getName());
        orderEntity.setReceiverPhone(fare.getAddress().getPhone());
        orderEntity.setReceiverPostCode(fare.getAddress().getPostCode());
        //设置运费信息
        orderEntity.setFreightAmount(fare.getFare());
        //设置初始生成的订单的状态
        orderEntity.setStatus(OrderStatusEnum.CREATE_NEW.getCode());
        //设置自动确认收货的时间
        orderEntity.setAutoConfirmDay(7);
        //设置订单的删除状态 0未删除 1已删除
        orderEntity.setDeleteStatus(0);
        return orderEntity;
    }

    /**
     * 根据购物车选中的OrderItemVo商品项目，创建出订单支付页面的OrderItemEntity商品项目
     * 生成所有的订单项数据信息
     * @return
     */
    private List<OrderItemEntity> buildOrderItems(String orderSn) {
        List<OrderItemVo> checkedItems = cartFeignService.getCurrentUserCartItems();
        if(checkedItems != null && checkedItems.size() > 0){
            List<OrderItemEntity> itemEntities = checkedItems.stream().map(item -> {
                //构建订单项信息
                OrderItemEntity orderItemEntity = buildOrderItem(item);
                orderItemEntity.setOrderSn(orderSn);
                return orderItemEntity;
            }).collect(Collectors.toList());

            return itemEntities;
        }
        return null;
    }

    /**
     * 构建单个订单项的数据信息
     * @param item 购物车中选中(check=true)的单个商品项，从redis缓存中获取到的数据
     * @return 返回的是结算页中，每一个选中的订单项需要展示的数据
     */
    private OrderItemEntity buildOrderItem(OrderItemVo item) {
        OrderItemEntity orderItemEntity = new OrderItemEntity();
        //1.订单号（在返回后设置）
        //2.商品的spu信息（需要远程查询）
        R spuResp = productFeignService.getSpuInfoBySkuId(item.getSkuId());
        SpuInfoVo spuInfo = spuResp.getData("spuInfo", new TypeReference<SpuInfoVo>() {
        });
        orderItemEntity.setSpuId(spuInfo.getId());
        orderItemEntity.setSpuName(spuInfo.getSpuName());
        orderItemEntity.setSpuPic(spuInfo.getSpuDescription());
        orderItemEntity.setCategoryId(spuInfo.getCatalogId());
        //其中spu的品牌名需要额外再查
        R brandResp = productFeignService.getBrandInfoByBrandId(spuInfo.getBrandId());
        BrandInfoVo brand = brandResp.getData("brand", new TypeReference<BrandInfoVo>() {
        });
        orderItemEntity.setSpuBrand(brand.getName());
        //3.商品的sku信息（购物车的商品项中存储着sku信息）
        orderItemEntity.setSkuId(item.getSkuId());
        orderItemEntity.setSkuName(item.getTitle());
        orderItemEntity.setSkuPic(item.getImage());
        orderItemEntity.setSkuPrice(item.getPrice());
        orderItemEntity.setSkuAttrsVals(StringUtils.collectionToDelimitedString(item.getSkuAttr(),";"));
        orderItemEntity.setSkuQuantity(item.getCount());
        //4.优惠信息（不做）
        //5.积分信息（根据价格计算即可）
        orderItemEntity.setGiftGrowth(item.getPrice().multiply(new BigDecimal(item.getCount().toString())).intValue());
        orderItemEntity.setGiftIntegration(item.getPrice().multiply(new BigDecimal(item.getCount().toString())).intValue());
        //6.每个商品项的价格优惠信息
        orderItemEntity.setPromotionAmount(new BigDecimal("0"));
        orderItemEntity.setCouponAmount(new BigDecimal("0"));
        orderItemEntity.setIntegrationAmount(new BigDecimal("0"));
        //每个商品项优惠后的实际价格 商品单价*商品数量-商品项的优惠价格
        BigDecimal origin = item.getPrice().multiply(new BigDecimal(item.getCount().toString()));
        BigDecimal real = origin.subtract(orderItemEntity.getPromotionAmount())
                                .subtract(orderItemEntity.getCouponAmount())
                                .subtract(orderItemEntity.getIntegrationAmount());
        orderItemEntity.setRealAmount(real);


        return orderItemEntity;
    }

    /**
     * 根据订单号获取订单信息
     * @param orderSn
     * @return
     */
    @Override
    public OrderEntity getOrderByOrderSn(String orderSn) {
        return this.getOne(
                new QueryWrapper<OrderEntity>()
                        .eq("order_sn",orderSn)
        );
    }

    /**
     * 订单30分钟未支付，自动关单
     * @param entity
     */
    @Override
    public void closeOrder(OrderEntity entity) {
        //查询当前订单的最新状态
        OrderEntity orderEntity = this.getById(entity.getId());

        if(orderEntity.getStatus() == OrderStatusEnum.CREATE_NEW.getCode()){
            //30分钟后查询订单的最新状态，如果是待付款状态，就需要关闭当前订单(已取消状态)
            orderEntity.setStatus(OrderStatusEnum.CANCLED.getCode());
            this.updateById(orderEntity);
            /**
             * 为了防止网络抖动，在解锁库存操作之后，关单操作的消息才被接收，订单状态才变成4，导致前面解锁库存失败
             * 解决方法：
             * 在关单之后给库存释放队列再次发送一个关闭订单的消息，在库存接收到该消息时需要再次执行解锁库存的操作
             */
            OrderTo orderTo = new OrderTo();
            BeanUtils.copyProperties(orderEntity,orderTo);
            try {
                //保证消息一定会发生出去，每个消息都做好日志记录（在数据库中保存每一个消息的详细信息）
                //定期扫描数据库，将失败的消息重新发送
                rabbitTemplate.convertAndSend("order-event-exchange","order.release.other",orderTo);
            }catch (Exception e){
                //将发送失败的消息进行重试发送
            }
        }

    }

    /**
     * 根据订单号查询订单信息，封装PayVo对象
     * @param orderSn
     * @return
     */
    @Override
    public PayVo getOrderPay(String orderSn) {
        //根据订单号查询订单
        OrderEntity orderEntity = this.getOrderByOrderSn(orderSn);
        //支付信息的实体对象
        PayVo payVo = new PayVo();

        payVo.setOut_trade_no(orderSn);//订单号

        //订单中的所有商品项
        List<OrderItemEntity> orderItemEntities = orderItemService.list(new QueryWrapper<OrderItemEntity>().eq("order_sn", orderSn));
        payVo.setSubject(orderItemEntities.get(0).getSpuName()+"...");//订单名称
        payVo.setBody(orderItemEntities.get(0).getSkuName()+"...");//商品描述

        BigDecimal payAmount = orderEntity.getPayAmount().setScale(2, BigDecimal.ROUND_UP);
        payVo.setTotal_amount(payAmount.toString());//付款金额

        return payVo;
    }

    /**
     * 查看我的订单页面
     * @param params
     * @return
     */
    @Override
    public PageUtils queryPageWithItem(Map<String, Object> params) {
        //用户登录信息
        MemberRespVo memberRespVo = LoginUserInterceptor.loginUser.get();

        IPage<OrderEntity> page = this.page(
                new Query<OrderEntity>().getPage(params),
                new QueryWrapper<OrderEntity>().eq("member_id",memberRespVo.getId()).orderByDesc("id")
        );

        Integer orderCount = this.baseMapper.selectCount(new QueryWrapper<OrderEntity>().eq("member_id", memberRespVo.getId()));

        //遍历处理该用户下的所有订单，找到每个订单下的所有商品项设置回订单中
        List<OrderEntity> orderEntities = page.getRecords().stream().map(order -> {
            List<OrderItemEntity> itemEntities = orderItemService.list(new QueryWrapper<OrderItemEntity>().eq("order_sn", order.getOrderSn()));
            order.setItemEntities(itemEntities);
            return order;
        }).collect(Collectors.toList());

        page.setRecords(orderEntities);
        //总记录
        page.setTotal(Long.parseLong(orderCount.toString()));
        //总页码
        Long totalPageNum = 0L;
        if(orderCount%page.getSize()==0){
            totalPageNum = orderCount/page.getSize();
        }else{
            totalPageNum = orderCount/page.getSize() + 1;
        }
        page.setPages(totalPageNum);

        return new PageUtils(page);
    }

    /**
     * 处理支付宝的回调，根据支付宝的支付结果修改订单的状态
     * @param payAsyncVo
     * @return
     */
    @Transactional
    @Override
    public String handlePayResult(PayAsyncVo payAsyncVo) {
        //1.保存交易流水
        PaymentInfoEntity paymentInfoEntity = new PaymentInfoEntity();
        paymentInfoEntity.setAlipayTradeNo(payAsyncVo.getTrade_no());
        paymentInfoEntity.setOrderSn(payAsyncVo.getOut_trade_no());
        paymentInfoEntity.setPaymentStatus(payAsyncVo.getTrade_status());
        paymentInfoEntity.setCallbackTime(payAsyncVo.getNotify_time());
        paymentInfoService.save(paymentInfoEntity);

        //2.修改订单的状态信息
        if (payAsyncVo.getTrade_status().equals("TRADE_SUCCESS") || payAsyncVo.getTrade_status().equals("TRADE_FINISHED")) {
            //支付成功状态，根据订单号修改订单状态
            String orderSn = payAsyncVo.getOut_trade_no();
            this.baseMapper.updateOrderStatus(orderSn,OrderStatusEnum.PAYED.getCode());
        }
        return "success";
    }

    /**
     * 处理监听到的秒杀订单消息，创建秒杀单
     * @param orderTo
     */
    @Override
    public void createSeckillOrder(SeckillOrderTo orderTo) {
        //TODO 1.保存订单基本信息
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setOrderSn(orderTo.getOrderSn());
        orderEntity.setMemberId(orderTo.getMemberId());
        orderEntity.setStatus(OrderStatusEnum.CREATE_NEW.getCode());
        BigDecimal payAmount = orderTo.getSeckillPrice().multiply(new BigDecimal(orderTo.getNum().toString()));
        orderEntity.setPayAmount(payAmount);
        this.save(orderEntity);
        //TODO 2.保存订单项信息
        OrderItemEntity orderItemEntity = new OrderItemEntity();
        orderItemEntity.setOrderSn(orderTo.getOrderSn());
        orderItemEntity.setRealAmount(payAmount);
        orderItemEntity.setSkuQuantity(orderTo.getNum());
        orderItemService.save(orderItemEntity);
    }
}