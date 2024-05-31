package com.atguigu.gulimall.thirdparty.util;

import java.util.regex.Pattern;

public class CommonUtil {
    /*检查手机号格式*/
    public static boolean checkPhone(String phone){
        boolean flag = false;
        if(phone != null && phone.length() == 11){
            flag = Pattern.matches("^1[1-9]\\d{9}$",phone);
        }
        return flag;
    }
}
