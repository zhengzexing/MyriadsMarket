package com.atguigu.gulimall.cart.controller;

import com.atguigu.common.constant.AuthConstant;
import com.atguigu.gulimall.cart.interceptor.CartInterceptor;
import com.atguigu.gulimall.cart.service.CartService;
import com.atguigu.gulimall.cart.vo.CartItemVo;
import com.atguigu.gulimall.cart.vo.CartVo;
import com.atguigu.gulimall.cart.vo.UserInfoTo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Controller
public class CartController {
    @Autowired
    CartService cartService;

    /**
     * 请求购物车的列表页面，并判断是未登录的游客购物车还是登录后的用户购物车
     * 浏览器保存一个游客信息的cookie user-key，期限一个月
     *
     * 用户信息根据session中是否保存来判断登录情况
     *
     * 拦截器->目标方法的controller 在拦截器获取userInfoTo，通过threadLocal传递到目标方法中
     *
     * 页面的游客信息userKey是必须，用户未登录没有userId时就将购物车信息放在userKey对应的缓存中
     * 用户登录了有userId时就将购物车信息放在userId对应的缓存中，同时需要合并作为游客时userKey缓存中的信息
     * @return
     */
    @GetMapping("/cart.html")
    public String cartListPage(Model model) throws ExecutionException, InterruptedException {
        CartVo cartVo = cartService.getCart();
        model.addAttribute("cart",cartVo);
        return "cartList";
    }

    /**
     * 添加商品到购物车
     * @return
     */
    @GetMapping("/addToCart")
    public String addToCart(@RequestParam("skuId") Long skuId,
                            @RequestParam("num") Integer num,
                            RedirectAttributes redirectAttributes) throws ExecutionException, InterruptedException {
        cartService.addToCart(skuId,num);
        redirectAttributes.addAttribute("skuId",skuId);
        return "redirect:http://cart.gulimall.com/addToCartSuccess.html";
    }

    /**
     * 为防止刷新成功页面即可重复添加商品到购物车
     * 作出优化，在添加商品到购物车成功后，不直接返回成功页面（展示，不改变url），而是重定向到成功页面（改变url地址）
     * 在成功页面中再查询skuId商品
     * @param skuId
     * @param model
     * @return
     */
    @GetMapping("/addToCartSuccess.html")
    public String addToCartSuccessPage(@RequestParam("skuId") Long skuId,
                                       Model model){
        //重定向到成功页面后，查询购物车中skuId的商品信息
        CartItemVo cartItemVo = cartService.getCartItem(skuId);
        model.addAttribute("item",cartItemVo);
        return "success";
    }

    /**
     * 购物车商品的选中情况修改
     * @return
     */
    @GetMapping("/checkItem")
    public String checkItem(@RequestParam("skuId") Long skuId,
                            @RequestParam("check") Integer check){
        cartService.checkItem(skuId,check);

        return "redirect:http://cart.gulimall.com/cart.html";
    }

    /**
     * 购物车商品的数量增减
     */
    @GetMapping("/countItem")
    public String countItem(@RequestParam("skuId") Long skuId,
                            @RequestParam("num") Integer num){
        cartService.changeItemCount(skuId,num);
        return "redirect:http://cart.gulimall.com/cart.html";
    }

    /**
     * 删除购物车中的购物项
     */
    @GetMapping("/deleteItem")
    public String deleteItem(@RequestParam("skuId") Long skuId){
        cartService.deleteItem(skuId);

        return "redirect:http://cart.gulimall.com/cart.html";
    }

    /**
     * 获取购物车当前选中的购物项
     */
    @ResponseBody
    @GetMapping("/currentUserCartItems")
    public List<CartItemVo> getCurrentUserCartItems(){
        return cartService.getUserCartItems();
    }

}
