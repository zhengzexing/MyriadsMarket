package com.atguigu.gulimall.cart.interceptor;

import com.atguigu.common.constant.AuthConstant;
import com.atguigu.common.constant.CartConstant;
import com.atguigu.common.vo.MemberRespVo;
import com.atguigu.gulimall.cart.vo.UserInfoTo;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.UUID;

/**
 * 购物车拦截器，在执行方法之前，判断用户的登录状态，并封装传递给controller目标请求
 */
public class CartInterceptor implements HandlerInterceptor {
    public static ThreadLocal<UserInfoTo> threadLocal = new ThreadLocal<>();

    /**
     * 目标方法执行前进行拦截
     * userId有  userKey无  购物车数据放入userId中
     * userId无  userKey有  购物车数据放入userKey中
     * userId无  userKey无  添加临时用户userKey
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        UserInfoTo userInfoTo = new UserInfoTo();

        HttpSession session = request.getSession();
        MemberRespVo member = (MemberRespVo) session.getAttribute(AuthConstant.LOGIN_USER);

        if(member != null){
            //用户登录了，获取游客的userId
            userInfoTo.setUserId(member.getId());
        }

        //获取浏览器中游客user-key的cookie
        Cookie[] cookies = request.getCookies();
        if(cookies!=null && cookies.length>0){
            for (Cookie cookie : cookies) {
                String name = cookie.getName();
                if(name.equals(CartConstant.TEMP_USER_COOKIE_NAME)){
                    userInfoTo.setUserKey(cookie.getValue());
                    userInfoTo.setTempUser(true);
                }
            }
        }

        //如果没有登录，无userId，遍历cookies后，仍然无userKey，则需要手动创建一个userKey作为游客信息
        //userKey是必须的，无论何时都应该存在
        if(StringUtils.isEmpty(userInfoTo.getUserKey())){
            String uuid = UUID.randomUUID().toString();
            userInfoTo.setUserKey(uuid);
        }

        //目标方法执行之前，将用户信息userInfoTo放到threadLocal中，在执行目标方法时可以获取到用户信息
        threadLocal.set(userInfoTo);

        return true;
    }

    /**
     * 业务方法执行之后
     * 保存userKey的游客信息作为cookie，有效期限一个月
     * @param request
     * @param response
     * @param handler
     * @param modelAndView
     * @throws Exception
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        if(!threadLocal.get().isTempUser()){
            Cookie cookie = new Cookie(CartConstant.TEMP_USER_COOKIE_NAME, threadLocal.get().getUserKey());
            cookie.setDomain("gulimall.com");//设置作用域
            cookie.setMaxAge(CartConstant.TEMP_USER_COOKIE_TIMEOUT);//设置过期时间
            response.addCookie(cookie);
        }

    }
}
