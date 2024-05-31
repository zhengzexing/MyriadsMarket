package com.atguigu.gulimall.cart.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.cart.feign.ProductFeignService;
import com.atguigu.gulimall.cart.interceptor.CartInterceptor;
import com.atguigu.gulimall.cart.service.CartService;
import com.atguigu.gulimall.cart.vo.CartItemVo;
import com.atguigu.gulimall.cart.vo.CartVo;
import com.atguigu.gulimall.cart.vo.SkuInfoVo;
import com.atguigu.gulimall.cart.vo.UserInfoTo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CartServiceImpl implements CartService {
    private final String CART_PREFIX = "gulimall:cart:";

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    ProductFeignService productFeignService;

    @Autowired
    ThreadPoolExecutor executor;

    /**
     * 添加数据到购物车
     *
     * @param skuId
     * @param num
     * @return
     */
    @Override
    public CartItemVo addToCart(Long skuId, Integer num) throws ExecutionException, InterruptedException {
        //1.获取商品需要添加到哪个购物车中
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();

        //2.判断当前购物车中是否有当前需要添加的商品，有就合并数量，没有就新增商品到购物车
        String item = (String) cartOps.get(skuId.toString());

        if (StringUtils.isEmpty(item)) {
            //1.创建购物车要添加的商品对象，原来购物车中没有该商品
            CartItemVo cartItemVo = new CartItemVo();
            cartItemVo.setCheck(true);
            cartItemVo.setSkuId(skuId);
            cartItemVo.setCount(num);

            //2.远程查询商品服务，查询要添加的商品的信息
            CompletableFuture<Void> getSkuInfoTask = CompletableFuture.runAsync(() -> {
                R r = productFeignService.getSkuInfo(skuId);
                SkuInfoVo skuInfo = r.getData("skuInfo", new TypeReference<SkuInfoVo>() {
                });

                cartItemVo.setImage(skuInfo.getSkuDefaultImg());
                cartItemVo.setTitle(skuInfo.getSkuTitle());
                cartItemVo.setPrice(skuInfo.getPrice());
            }, executor);

            //3.远程查询商品服务，查出skuId的商品属性组合信息如 罗兰紫+256GB
            CompletableFuture<Void> getSkuSaleAttrValuesTask = CompletableFuture.runAsync(() -> {
                R r = productFeignService.getSkuSaleAttrValues(skuId);
                List<String> saleValues = r.getData("saleValues", new TypeReference<List<String>>() {
                });

                cartItemVo.setSkuAttrValues(saleValues);
            }, executor);

            //4.等待异步编排完成
            CompletableFuture.allOf(getSkuInfoTask, getSkuSaleAttrValuesTask).get();

            //5.将商品对象放到购物车中，hashKey为skuId，hashValue为商品对象
            cartOps.put(skuId.toString(), JSON.toJSONString(cartItemVo));

            return cartItemVo;
        } else {
            //1.购物车中有当前要添加的商品，合并商品的数量
            CartItemVo cartItemVo = JSON.parseObject(item, CartItemVo.class);
            cartItemVo.setCount(cartItemVo.getCount() + num);

            //2.将合并数量后的商品对象重新放入购物车
            cartOps.put(skuId.toString(), JSON.toJSONString(cartItemVo));

            return cartItemVo;
        }
    }

    /**
     * 判断用户当前的登录状态，确定从redis中获取的购物车信息
     * 购物车中的数据以hash存储， skuId为key 商品信息为value
     *
     * @return 返回当前用户所对应的购物车缓存对象
     */
    private BoundHashOperations<String, Object, Object> getCartOps() {
        UserInfoTo userInfoTo = CartInterceptor.threadLocal.get();
        //1.决定购物车数据在redis中的key
        String cartKey = "";
        if (userInfoTo.getUserId() != null) {
            //用户登录了 redis中的key为gulimall:cart:userId
            cartKey = CART_PREFIX + userInfoTo.getUserId();
        } else {
            //用户没登录 redis中的key为gulimall:cart:userKey
            cartKey = CART_PREFIX + userInfoTo.getUserKey();
        }

        //2.判断现在添加的skuId商品在购物车中是否存在，若存在则数量相加，若不存在则直接加入购物车
        BoundHashOperations<String, Object, Object> operations = stringRedisTemplate.boundHashOps(cartKey);//绑定当前用户的购物车信息

        return operations;
    }

    /**
     * 获取购物车中商品编号为skuId的商品
     *
     * @param skuId
     * @return
     */
    @Override
    public CartItemVo getCartItem(Long skuId) {
        //绑定购物车
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        //在购物车中取出hashKey为skuId的商品项
        String cartItem = (String) cartOps.get(skuId.toString());

        return JSON.parseObject(cartItem, CartItemVo.class);
    }

    /**
     * 获取购物车中所有的商品
     *
     * @return
     */
    @Override
    public CartVo getCart() throws ExecutionException, InterruptedException {
        UserInfoTo userInfoTo = CartInterceptor.threadLocal.get();

        //创建购物车对象
        CartVo cartVo = new CartVo();

        if (userInfoTo.getUserId() != null) {
            //1.登录，展示当前用户的购物车，并将离线购物车的商品也合并到当前用户的购物车中

            //游客购物车
            String tempCartKey = CART_PREFIX + userInfoTo.getUserKey();//购物车的key，哪个游客的购物车
            List<CartItemVo> tempCartItems = getCartItems(tempCartKey);//获取临时购物车中的所有商品

            //将临时购物车中的所有商品遍历添加到用户购物车中
            if (tempCartItems != null && tempCartItems.size() > 0) {
                for (CartItemVo tempCartItem : tempCartItems) {
                    addToCart(tempCartItem.getSkuId(), tempCartItem.getCount());
                }

                //合并完毕，删除临时购物车
                clearCart(tempCartKey);
            }

            //用户购物车
            String cartKey = CART_PREFIX + userInfoTo.getUserId();//购物车的key，哪个用户的购物车
            List<CartItemVo> cartItems = getCartItems(cartKey);//获取合并后用户购物车中的所有商品

            cartVo.setItems(cartItems);

        } else {
            //2.未登录，直接展示游客的离线购物车即可
            String cartKey = CART_PREFIX + userInfoTo.getUserKey();//购物车的key，哪个游客的购物车
            List<CartItemVo> cartItems = getCartItems(cartKey);//获取当前临时购物车的所有商品

            cartVo.setItems(cartItems);
        }

        return cartVo;
    }

    /**
     * 获取指定的购物车中的所有商品，并将这些商品封装为list后返回
     *
     * @param cartKey
     * @return null购物车为空 cartItemVos购物车中的所有商品
     */
    private List<CartItemVo> getCartItems(String cartKey) {
        BoundHashOperations<String, Object, Object> cartOps = stringRedisTemplate.boundHashOps(cartKey);//绑定购物车
        List<Object> cartValues = cartOps.values();//购物车的value，该购物车所有的商品数据

        //遍历购物车所有的商品数据，将每一个商品数据的Json转化为CartItemVo对象，并重新收集为集合
        if (cartValues != null && cartValues.size() > 0) {
            List<CartItemVo> cartItemVos = cartValues.stream().map(cartValue -> {
                String cartStr = (String) cartValue;
                CartItemVo cartItemVo = JSON.parseObject(cartStr, CartItemVo.class);
                return cartItemVo;
            }).collect(Collectors.toList());

            return cartItemVos;
        }
        return null;
    }

    /**
     * 清空购物车中的所有商品
     *
     * @param cartKey
     */
    @Override
    public void clearCart(String cartKey) {
        stringRedisTemplate.delete(cartKey);
    }

    @Override
    public void checkItem(Long skuId, Integer check) {
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();

        //根据商品的skuId获取购物车中的该商品信息
        CartItemVo cartItemVo = getCartItem(skuId);
        //修改选中状态
        cartItemVo.setCheck(check == 1);
        //重新设置到缓存中
        cartOps.put(skuId.toString(), JSON.toJSONString(cartItemVo));
    }

    /**
     * 修改购物车商品数量
     *
     * @param skuId
     * @param num
     */
    @Override
    public void changeItemCount(Long skuId, Integer num) {
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();

        //根据商品的skuId获取购物车中的该商品信息
        CartItemVo cartItemVo = getCartItem(skuId);
        //修改数量
        cartItemVo.setCount(num);
        //重新设置到缓存中
        cartOps.put(skuId.toString(), JSON.toJSONString(cartItemVo));
    }

    @Override
    public void deleteItem(Long skuId) {
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        cartOps.delete(skuId.toString());
    }

    /**
     * 获取用户当前在购物车中勾选的购物项
     *
     * @return
     */
    @Override
    public List<CartItemVo> getUserCartItems() {
        UserInfoTo userInfoTo = CartInterceptor.threadLocal.get();
        if (userInfoTo.getUserId() == null) {
            return null;
        } else {
            //用户登录后的购物车key
            String cartKey = CART_PREFIX + userInfoTo.getUserId();
            //购物车中的所有商品
            List<CartItemVo> cartItems = getCartItems(cartKey);
            //过滤出check=true的商品
            if (cartItems != null && cartItems.size() > 0) {
                //购物车中有商品，过滤出选中的那些商品
                return cartItems.stream().map(item -> {
                    //更新要结算的商品的价格为当前的最新价格
                    R r = productFeignService.getPrice(item.getSkuId());
                    if(r.getCode() == 0){
                        item.setPrice(r.getData("price",new TypeReference<BigDecimal>(){}));
                    }
                    return item;
                }).filter(CartItemVo::getCheck).collect(Collectors.toList());
            } else {
                //购物车为空，一件商品都没有
                return null;
            }
        }
    }
}
