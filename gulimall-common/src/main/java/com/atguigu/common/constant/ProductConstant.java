package com.atguigu.common.constant;

public class ProductConstant {
    public enum AttrEnum{
        ATTR_TYPE_BASE(1,"规格参数"),
        ATTR_TYPE_SALE(0,"销售属性");

        private int code;

        private String msg;

        AttrEnum(int code,String msg){
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

    public enum StatusEnum{
        NEW_SPU(0,"商品新建"),
        UP_SPU(1,"商品上架"),
        DOWN_SPU(2,"商品下架");

        private int code;

        private String msg;

        StatusEnum(int code,String msg){
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
}
