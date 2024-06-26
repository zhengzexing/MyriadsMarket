package com.atguigu.common.exception;

public enum BizCodeEnum {
    UNKNOW_EXCEPTION(10000,"系统未知异常"),
    VALID_EXCEPTION(10001,"提交的数据不合法"),
    SMS_CODE_EXCEPTION(10002,"验证码获取频率太高，请稍后再试"),
    TOO_MANY_REQUEST(10003,"请求流量过大，请稍后重试"),
    REQUEST_TIMEOUT_EXCEPTION(10004,"远程请求超时，请稍后重试"),
    PRODUCT_UP_EXCEPTION(11000,"商品上架异常"),
    USERNAME_EXIST_EXCEPTION(15001,"用户名已经存在"),
    PHONE_EXIST_EXCEPTION(15002,"手机号已经存在"),
    LOGINACCT_PASSWORD_INVALID_EXCEPTION(15003,"账号或密码错误"),
    NO_STOCK_EXCEPTION(21000,"库存不足");

    private int code;
    private String msg;

    BizCodeEnum(int code,String msg){
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
