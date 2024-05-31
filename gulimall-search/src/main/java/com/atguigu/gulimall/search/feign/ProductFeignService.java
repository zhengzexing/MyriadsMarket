package com.atguigu.gulimall.search.feign;

import com.atguigu.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(value = "gulimall-product")
public interface ProductFeignService {
    /**
     * 获取attr的信息
     */
    @RequestMapping("/product/attr/info/{attrId}")
    public R getAttrInfo(@PathVariable("attrId") Long attrId);

    /**
     * 根据品牌的ids获取品牌数据
     * @param brandIds
     * @return
     */
    @GetMapping("/product/brand/infos")
    public R getBrandsInfo(@RequestParam("brandIds") List<Long> brandIds);
}
