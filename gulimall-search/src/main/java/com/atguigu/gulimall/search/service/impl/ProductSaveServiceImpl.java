package com.atguigu.gulimall.search.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.common.to.es.SkuEsModel;
import com.atguigu.gulimall.search.config.GulimallElasticSearchConfig;
import com.atguigu.gulimall.search.constant.EsConstant;
import com.atguigu.gulimall.search.service.ProductSaveService;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProductSaveServiceImpl implements ProductSaveService {
    @Autowired
    RestHighLevelClient restHighLevelClient; //es操作的客户端

    @Override
    public boolean productStatusUp(List<SkuEsModel> skuEsModels) throws IOException {
        //保存对象列表到es
        //1.给es建立索引product,建立好映射关系

        //2.给es中批量保存数据
        //BulkRequest bulkRequest RequestOptions options
        BulkRequest bulkRequest = new BulkRequest();

        for (SkuEsModel model : skuEsModels) {
            //在保存请求中，指定保存的索引
            IndexRequest indexRequest = new IndexRequest(EsConstant.PRODUCT_INDEX);
            //指定保存在es的id为skuId
            indexRequest.id(model.getSkuId().toString());
            //指定保存在es的数据
            String jsonString = JSON.toJSONString(model);
            indexRequest.source(jsonString, XContentType.JSON);

            bulkRequest.add(indexRequest);
        }
        //3.开始批量保存
        BulkResponse responses = restHighLevelClient.bulk(bulkRequest, GulimallElasticSearchConfig.COMMON_OPTIONS);

        //4.处理响应结果
        boolean b = responses.hasFailures();

        //responses.getItems()获取批量操作的所有对象
        List<String> collect = Arrays.stream(responses.getItems()).map(item -> {
            return item.getId();
        }).collect(Collectors.toList());

        if(b){
            log.error("商品上架存在错误:{}",collect);
            return false;
        }else{
            return true;
        }
    }
}
