package com.atguigu.gulimall.product.exception;

import com.atguigu.common.exception.BizCodeEnum;
import com.atguigu.common.utils.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 集中处理所有的数据异常BindingResult result
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.atguigu.gulimall.product.controller") // 收集该包下所有的异常
public class GulimallExceptionControllerAdvice {
    /**
     * 处理参数不合法的异常
     * @param e
     * @return
     */
    @ExceptionHandler(value = MethodArgumentNotValidException.class) // 处理的异常的类型
    public R handleValidException(MethodArgumentNotValidException e){
        log.error("数据校验出现问题{}，异常类型{}",e.getMessage(),e.getClass());
        // 获取数据绑定的错误信息
        BindingResult result = e.getBindingResult();

        Map<String,String> map = new HashMap<>();
        // 获取所有字段中错误的校验结果
        result.getFieldErrors().forEach((item)->{
            // 遍历取出所有错误字段中的错误信息
            String message = item.getDefaultMessage();
            // 获取错误的属性名字
            String field = item.getField();
            map.put(field,message);
        });
        return R.error(BizCodeEnum.VALID_EXCEPTION.getCode(),BizCodeEnum.VALID_EXCEPTION.getMsg()).put("data",map);
    }

    /**
     * 统一处理其他抛出的异常
     * @param throwable
     * @return
     */
    @ExceptionHandler(value = Throwable.class)
    public R handleException(Throwable throwable){
        log.error("出现的异常：{}",throwable);
        return R.error(BizCodeEnum.UNKNOW_EXCEPTION.getCode(), BizCodeEnum.UNKNOW_EXCEPTION.getMsg());
    }
}
