package com.atguigu.gulimall.coupon.dao;

import com.atguigu.gulimall.coupon.entity.CouponEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 优惠券信息
 * 
 * @author zhengzexing
 * @email 635249662@qq.com
 * @date 2024-04-21 19:15:54
 */
@Mapper
public interface CouponDao extends BaseMapper<CouponEntity> {
	
}
