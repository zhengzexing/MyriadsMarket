package com.atguigu.gulimall.coupon.service.impl;

import com.atguigu.gulimall.coupon.entity.SeckillSkuRelationEntity;
import com.atguigu.gulimall.coupon.service.SeckillSkuRelationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.coupon.dao.SeckillSessionDao;
import com.atguigu.gulimall.coupon.entity.SeckillSessionEntity;
import com.atguigu.gulimall.coupon.service.SeckillSessionService;


@Service("seckillSessionService")
public class SeckillSessionServiceImpl extends ServiceImpl<SeckillSessionDao, SeckillSessionEntity> implements SeckillSessionService {
    @Autowired
    SeckillSkuRelationService seckillSkuRelationService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<SeckillSessionEntity> page = this.page(
                new Query<SeckillSessionEntity>().getPage(params),
                new QueryWrapper<SeckillSessionEntity>()
        );

        return new PageUtils(page);
    }

    /**
     * 获取最近3天的所有秒杀场次
     * @return
     */
    @Override
    public List<SeckillSessionEntity> getLatest3DaySession() {
        //1.计算最近3天的时间 (2024.05.26 00:00:00 ~ 2024.05.28 23:59:59)
        LocalDate now = LocalDate.now();//当前的日期：2024.05.26 不含时分秒
        LocalDate latest3Days = now.plusDays(2L);//当前的日期加上2天：2024.05.28

        LocalTime min = LocalTime.MIN; //最小的时分秒 00:00:00
        LocalTime max = LocalTime.MAX; //最大的时分秒 23:59:59

        //组合时间
        LocalDateTime start = LocalDateTime.of(now, min);//2024.05.26 00:00:00
        String startFormat = start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        LocalDateTime end = LocalDateTime.of(latest3Days, max);//2024.05.28 23:59:59
        String endFormat = end.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        //2.获取最近3天的所有秒杀场次
        List<SeckillSessionEntity> sessionEntities = this.list(
                new QueryWrapper<SeckillSessionEntity>()
                        .between("start_time", startFormat, endFormat)
        );

        //3.获取当前秒杀场次关联的所有商品
        if(sessionEntities != null && sessionEntities.size() > 0){
            List<SeckillSessionEntity> sessionList = sessionEntities.stream().map(session -> {
                //当前秒杀场次的id
                Long sessionId = session.getId();
                //根据当前秒杀场次的id，列出当前秒杀场次关联的所有商品skus
                List<SeckillSkuRelationEntity> relationSkus = seckillSkuRelationService.list(new QueryWrapper<SeckillSkuRelationEntity>().eq("promotion_session_id", sessionId));
                session.setRelationSkus(relationSkus);
                return session;
            }).collect(Collectors.toList());

            return sessionList;
        }

        //走到这里说明最近三天都没有秒杀场次
        return null;
    }

}