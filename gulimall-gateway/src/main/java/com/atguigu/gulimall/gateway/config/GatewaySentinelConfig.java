package com.atguigu.gulimall.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.fastjson.JSON;
import com.atguigu.common.exception.BizCodeEnum;
import com.atguigu.common.utils.R;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 配置网关的自定义限流回调
 */
@Configuration
public class GatewaySentinelConfig {
    public GatewaySentinelConfig(){
        GatewayCallbackManager.setBlockHandler(new BlockRequestHandler() {
            /**
             * 网关层面进行限流，限流后会调用此回调函数
             * @param serverWebExchange
             * @param throwable
             * @return
             */
            @Override
            public Mono<ServerResponse> handleRequest(ServerWebExchange serverWebExchange, Throwable throwable) {
                R error = R.error(BizCodeEnum.TOO_MANY_REQUEST.getCode(), BizCodeEnum.TOO_MANY_REQUEST.getMsg());
                String errorJson = JSON.toJSONString(error);

                Mono<ServerResponse> mono = ServerResponse.ok().body(Mono.just(errorJson), String.class);
                return mono;
            }
        });
    }
}
