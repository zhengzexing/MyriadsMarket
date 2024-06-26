package com.atguigu.gulimall.ware.config;

import com.baomidou.mybatisplus.extension.plugins.PaginationInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement // 开启事务
@MapperScan("com.atguigu.gulimall.ware.dao")
public class MybatisConfig {
    /**
     * 引入分页的插件
     */
    @Bean
    public PaginationInterceptor paginationInterceptor(){
        PaginationInterceptor paginationInterceptor = new PaginationInterceptor();
        //在最后一页点击“下一页”后返回到“第一页”
        paginationInterceptor.setOverflow(true);
        //设置每页最大受限1000条数据，-1为不受限制
        paginationInterceptor.setLimit(1000);

        return paginationInterceptor;
    }
}
