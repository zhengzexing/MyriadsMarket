package com.atguigu.gulimall.seckill.interceptor;

import com.atguigu.common.constant.AuthConstant;
import com.atguigu.common.vo.MemberRespVo;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 整个订单服务页面都需要登录后才可以访问
 * 登录拦截器
 */
@Component
public class LoginUserInterceptor implements HandlerInterceptor {
    public static ThreadLocal<MemberRespVo> loginUser = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //只拦截秒杀活动立即抢购的请求，其他请求不拦截
        String uri = request.getRequestURI();
        AntPathMatcher antPathMatcher = new AntPathMatcher();
        boolean match = antPathMatcher.match("/kill", uri);

        if(match){
            MemberRespVo attribute = (MemberRespVo) request.getSession().getAttribute(AuthConstant.LOGIN_USER);
            if(attribute != null){
                //将登录的用户放到线程池，后续执行的方法可以直接获取登录的用户
                loginUser.set(attribute);
                //登录了，放行
                return true;
            }else{
                //没登录，拦截
                request.getSession().setAttribute("msg","请先进行登录");
                response.sendRedirect("http://auth.gulimall.com/login.html");
                return false;
            }
        }

        return true;
    }
}
