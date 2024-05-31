package com.atguigu.gulimall.thirdparty.component;

import com.aliyun.dysmsapi20170525.models.QuerySendDetailsRequest;
import com.aliyun.dysmsapi20170525.models.QuerySendDetailsResponse;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.tea.TeaException;
import com.atguigu.common.constant.AuthConstant;
import com.atguigu.gulimall.thirdparty.config.ALiYunSmsConfig;
import com.atguigu.gulimall.thirdparty.util.Sample;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * 阿里云短信发送、短信验证
 */
@Component
public class SmsComponent {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ALiYunSmsConfig smsConfig;

    /**
     * 发送短信
     * @param phone
     * @return
     * @throws Exception
     */
    public boolean sendSms(String phone) throws Exception {
        boolean send = false;
        //设置短信内容
        String random = RandomStringUtils.randomNumeric(6);
        System.out.println("发送验证码的随机数 random="+random);

        // 获取当前日期
        String currentDate = new SimpleDateFormat("yyyyMMdd").format(new Date());

        // 请确保代码运行环境设置了环境变量 ALIBABA_CLOUD_ACCESS_KEY_ID 和 ALIBABA_CLOUD_ACCESS_KEY_SECRET。
        // 工程代码泄露可能会导致 AccessKey 泄露，并威胁账号下所有资源的安全性。以下代码示例使用环境变量获取 AccessKey 的方式进行调用，仅供参考，建议使用更安全的 STS 方式，更多鉴权访问方式请参见：https://help.aliyun.com/document_detail/378657.html
        com.aliyun.dysmsapi20170525.Client client = Sample.createClient(smsConfig.getAccessKeyID(), smsConfig.getAccessKeySecret());
        com.aliyun.dysmsapi20170525.models.SendSmsRequest sendSmsRequest = new com.aliyun.dysmsapi20170525.models.SendSmsRequest()
                .setSignName("谷粒商城")
                .setTemplateCode("SMS_464796376")
                .setPhoneNumbers(phone)
                .setTemplateParam("{\"code\":"+random+"}");
        com.aliyun.teautil.models.RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
        /*try {
            // 复制代码运行请自行打印 API 的返回值
            SendSmsResponse response = client.sendSmsWithOptions(sendSmsRequest, runtime);

            if(response.getStatusCode() == HttpStatus.SC_OK){
                String text = response.body.toString();

                //解析json
                if(StringUtils.isNotBlank(text)){
                    if(com.aliyun.teautil.Common.equalString(response.body.code, "OK")){//第三方接口调用成功
                        // 1. 读取回执ID
                        String bizId = response.body.bizId;

                        // 2. 等待 10 秒后查询结果
                        com.aliyun.teautil.Common.sleep(10000);

                        // 3. 查询结果
                        QuerySendDetailsRequest queryReq = new QuerySendDetailsRequest()
                                .setPhoneNumber(com.aliyun.teautil.Common.assertAsString(phone))
                                .setBizId(bizId)
                                .setSendDate(currentDate)
                                .setCurrentPage(1L)
                                .setPageSize(10L);
                        QuerySendDetailsResponse queryResp = client.querySendDetails(queryReq);

                        if(queryResp.body.smsSendDetailDTOs.smsSendDetailDTO.get(0).sendStatus == 3){
                            //短信发送成功
                            send = true;

                            //将验证码存在redis，并设置时效为3分钟
                            String key = AuthConstant.SMS_CODE_CACHE_PREFIX + phone;
                            stringRedisTemplate.boundValueOps(key).set(random,3, TimeUnit.MINUTES);

                        }
                    }
                }
            }
        } catch (TeaException error) {
            // 错误 message
            System.out.println(error.getMessage());
            // 诊断地址
            System.out.println(error.getData().get("Recommend"));
            com.aliyun.teautil.Common.assertAsString(error.message);
        } catch (Exception _error) {
            TeaException error = new TeaException(_error.getMessage(), _error);
            // 错误 message
            System.out.println(error.getMessage());
            // 诊断地址
            System.out.println(error.getData().get("Recommend"));
            com.aliyun.teautil.Common.assertAsString(error.message);
        }*/

        //短信接口过期，模拟短信发送成功
        send = true;

        //将验证码存在redis，并设置时效为3分钟
        String key = AuthConstant.SMS_CODE_CACHE_PREFIX + phone;
        String value = random + "_" + System.currentTimeMillis();
        stringRedisTemplate.boundValueOps(key).set(value, 3, TimeUnit.MINUTES);

        return send;
    }

    /**
     * 比对验证码
     * @param phone
     * @param code
     * @return
     */
    public boolean checkSmsCode(String phone, String code) {
        String key = AuthConstant.SMS_CODE_CACHE_PREFIX + phone;
        if (stringRedisTemplate.hasKey(key)) {
            //查询redis缓存中正确的验证码
            String querySmsCode = stringRedisTemplate.boundValueOps(key).get();
            //与用户输入的验证码进行比对
            if(code.equals(querySmsCode.split("_")[0])){
                return true;
            }
        }
        return false;
    }
}
