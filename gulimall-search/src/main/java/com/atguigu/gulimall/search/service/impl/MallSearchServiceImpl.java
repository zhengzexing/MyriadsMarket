package com.atguigu.gulimall.search.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.to.es.SkuEsModel;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.search.config.GulimallElasticSearchConfig;
import com.atguigu.gulimall.search.constant.EsConstant;
import com.atguigu.gulimall.search.feign.ProductFeignService;
import com.atguigu.gulimall.search.service.MallSearchService;
import com.atguigu.gulimall.search.vo.AttrResponseVo;
import com.atguigu.gulimall.search.vo.BrandVo;
import com.atguigu.gulimall.search.vo.SearchParam;
import com.atguigu.gulimall.search.vo.SearchResult;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MallSearchServiceImpl implements MallSearchService {
    @Autowired
    RestHighLevelClient restHighLevelClient;

    @Autowired
    ProductFeignService productFeignService;

    /**
     * 根据前端页面传递的参数到es中检索需要的商品
     * 使用es语法DSL进行检索
     * @param searchParam
     * @return
     */
    @Override
    public SearchResult search(SearchParam searchParam) {
        SearchResult searchResult = null;

        //1.动态构建出查询需要的DSL语句，准备检索的请求
        SearchRequest searchRequest = buildSearchRequest(searchParam);

        try {
            //2.使用客户端操作es进行查询，并返回查询结果
            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, GulimallElasticSearchConfig.COMMON_OPTIONS);

            //3.分析响应数据，并封装成指定的格式
            searchResult = buildSearchResult(searchResponse,searchParam);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return searchResult;
    }

    /**
     * 动态构建DSL语句，准备es检索请求
     * 功能：模糊匹配、过滤(按照属性、分类、品牌、价格区间、库存)、排序、分页、高亮、聚合分析
     * @return
     */
    private SearchRequest buildSearchRequest(SearchParam param) {
        //用于构建DSL语句
        SearchSourceBuilder source = new SearchSourceBuilder();

        /**
         * 查询：模糊匹配，过滤(按照属性、分类、品牌、价格区间、库存)
         */
        //1.构建bool query
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        //1.1 must-模糊匹配
        if(!StringUtils.isEmpty(param.getKeyword())){
            boolQuery.must(QueryBuilders.matchQuery("skuTitle",param.getKeyword()));
        }
        //1.2 filter-过滤
        if(param.getCatalog3Id() != null){
            //按照三级分类的id过滤
            boolQuery.filter(QueryBuilders.termQuery("catalogId",param.getCatalog3Id()));
        }
        if(param.getBrandId() != null && param.getBrandId().size() > 0){
            //按照品牌的id过滤
            boolQuery.filter(QueryBuilders.termsQuery("brandId",param.getBrandId()));
        }
        if(param.getAttrs() != null && param.getAttrs().size() > 0){
            //按照属性进行过滤，属性在elasticsearch中进行了扁平化处理，所以需要用嵌入式的查询条件
            //attrs=1_5寸:8寸&attrs=2_16G:8G  属性在数据库中的id_属性筛选的值，所以对多个attr进行遍历
            for (String attrStr : param.getAttrs()) {
                //要给嵌入式传递的查询条件：bool查询
                BoolQueryBuilder nestedBoolQuery = QueryBuilders.boolQuery();
                String[] s = attrStr.split("_");
                String attrId = s[0];//s[0]:1 属性attrId
                String[] attrValues = s[1].split(":");// s[1]:5寸:8寸 属性attrValues

                nestedBoolQuery.must(QueryBuilders.termQuery("attrs.attrId",attrId));
                nestedBoolQuery.must(QueryBuilders.termsQuery("attrs.attrValue",attrValues));

                NestedQueryBuilder nestedQuery = QueryBuilders.nestedQuery("attrs", nestedBoolQuery, ScoreMode.None);

                boolQuery.filter(nestedQuery);
            }
        }
        if(param.getHasStock() != null){
            //按照是否有库存过滤 1过滤出有库存的 0过滤出无库存的
            boolQuery.filter(QueryBuilders.termQuery("hasStock",param.getHasStock() == 1));
        }
        if(!StringUtils.isEmpty(param.getSkuPrice())){
            //按照价格区间过滤 1_500(1到500) _500(小于500) 500_(大于500)
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("skuPrice");
            String[] s = param.getSkuPrice().split("_");
            if(s.length == 2){
                //按_分割，长度为2，说明价格是一个区间
                rangeQuery.gte(s[0]);
                rangeQuery.lte(s[1]);
            }else if(s.length == 1){
                //按_分割，长度为1，说明价格是大于或者小于s[0]的
                if(param.getSkuPrice().startsWith("_")){
                    //_500 小于500
                    rangeQuery.lte(s[0]);
                }
                if(param.getSkuPrice().endsWith("_")){
                    //500_ 大于500
                    rangeQuery.gte(s[0]);
                }
            }
            boolQuery.filter(rangeQuery);
        }

        //封装所有的查询和过滤条件
        source.query(boolQuery);

        /**
         * 排序、分页、高亮
         */
        //2.1 构建排序sort
        if(!StringUtils.isEmpty(param.getSort())){
            String sort = param.getSort();
            String[] s = sort.split("_");//s[0]按哪个字段排序，s[1]升序还是降序
            SortOrder sortOrder = s[1].equalsIgnoreCase("asc") ? SortOrder.ASC:SortOrder.DESC;
            source.sort(s[0],sortOrder);
        }
        //2.2 分页
        //from从哪条记录开始 (当前页码-1)*每页显示条数
        source.from((param.getPageNum()-1)*EsConstant.PRODUCT_PAGESIZE);
        //size搜索出几条记录
        source.size(EsConstant.PRODUCT_PAGESIZE);
        //2.3 高亮
        if(!StringUtils.isEmpty(param.getKeyword())){
            //搜索条件进行高亮
            HighlightBuilder highlightBuilder = new HighlightBuilder();
            highlightBuilder.field("skuTitle");//高亮的字段
            highlightBuilder.preTags("<b style='color:red'>");//高亮字段的前置标签
            highlightBuilder.postTags("</b>");//高亮字段的后置标签

            source.highlighter(highlightBuilder);
        }

        /**
         * 聚合分析
         */
        //3.1 按照品牌聚合
        TermsAggregationBuilder brand_agg = AggregationBuilders.terms("brand_agg").field("brandId").size(50);//品牌id有多个值
        //3.1.1 品牌聚合的子聚合
        brand_agg.subAggregation(AggregationBuilders.terms("brand_name_agg").field("brandName").size(1));//每个品牌id下只有一个品牌名
        brand_agg.subAggregation(AggregationBuilders.terms("brand_img_agg").field("brandImg").size(1));//每个品牌id下只有一个logo
        source.aggregation(brand_agg);

        //3.2 按照商品分类聚合
        TermsAggregationBuilder catalog_agg = AggregationBuilders.terms("catalog_agg").field("catalogId").size(50);//商品分类id有多个值
        //3.2.1 商品分类聚合的子聚合
        catalog_agg.subAggregation(AggregationBuilders.terms("catalog_name_agg").field("catalogName").size(1));//每个分类id下只有一个分类名
        source.aggregation(catalog_agg);

        //3.3 按照属性聚合
        NestedAggregationBuilder attr_agg = AggregationBuilders.nested("attr_agg", "attrs");
        TermsAggregationBuilder attr_id_agg = AggregationBuilders.terms("attr_id_agg").field("attrs.attrId").size(50);//属性id聚合

        attr_id_agg.subAggregation(AggregationBuilders.terms("attr_name_agg").field("attrs.attrName").size(1));//属性名聚合
        attr_id_agg.subAggregation(AggregationBuilders.terms("attr_value_agg").field("attrs.attrValue").size(50));//属性值聚合

        attr_agg.subAggregation(attr_id_agg);
        source.aggregation(attr_agg);

        //System.out.println("构建的DSL语句:"+source.toString());

        //指定索引和DSL语句
        SearchRequest searchRequest = new SearchRequest(new String[]{EsConstant.PRODUCT_INDEX}, source);

        return searchRequest;//返回检索请求
    }

    /**
     * 将es查询响应的结果进行封装返回SearchResult对象
     * @param response
     * @return
     */
    private SearchResult buildSearchResult(SearchResponse response,SearchParam param) {
        SearchResult result = new SearchResult();
        SearchHits hits = response.getHits();

        //1.封装所有查询到的商品信息
        List<SkuEsModel> products = new ArrayList<>();
        if (hits.getHits() != null && hits.getHits().length > 0) {
            for (SearchHit hit : hits.getHits()) {
                String sourceAsString = hit.getSourceAsString();
                //这里response获取到_source里的skuTitle没有高亮渲染，因为_source里封装的是原生的值
                SkuEsModel sku = JSON.parseObject(sourceAsString, SkuEsModel.class);
                //如果传递的值有keyword检索条件，需要进行高亮渲染
                if(!StringUtils.isEmpty(param.getKeyword())){
                    //需要到response中的highlight里获取skuTitle
                    HighlightField skuTitleHighlight = hit.getHighlightFields().get("skuTitle");
                    //重新封装回去sku的skuTitle，更换成高亮的样式
                    sku.setSkuTitle(skuTitleHighlight.getFragments()[0].string());
                }

                products.add(sku);
            }
        }
        result.setProducts(products);

        //2.封装所有商品涉及到的所有属性信息
        ParsedNested attr_agg = response.getAggregations().get("attr_agg");
        ParsedLongTerms attr_id_agg = attr_agg.getAggregations().get("attr_id_agg");
        List<SearchResult.AttrVo> attrVos = new ArrayList<>();
        for (Terms.Bucket bucket : attr_id_agg.getBuckets()) {
            SearchResult.AttrVo attrVo = new SearchResult.AttrVo();
            attrVo.setAttrId(bucket.getKeyAsNumber().longValue());

            ParsedStringTerms attr_name_agg = bucket.getAggregations().get("attr_name_agg");
            attrVo.setAttrName(attr_name_agg.getBuckets().get(0).getKeyAsString());

            ParsedStringTerms attr_value_agg = bucket.getAggregations().get("attr_value_agg");
            List<String> attrValues = new ArrayList<>();
            for (Terms.Bucket attrValueAggBucket : attr_value_agg.getBuckets()) {
                attrValues.add(attrValueAggBucket.getKeyAsString());
            }
            attrVo.setAttrValue(attrValues);

            attrVos.add(attrVo);
        }
        result.setAttrs(attrVos);

        //3.封装所有商品涉及到的所有品牌信息
        ParsedLongTerms brand_agg = response.getAggregations().get("brand_agg");
        List<SearchResult.BrandVo> brandVos = new ArrayList<>();
        for (Terms.Bucket bucket : brand_agg.getBuckets()) {
            SearchResult.BrandVo brandVo = new SearchResult.BrandVo();
            brandVo.setBrandId(bucket.getKeyAsNumber().longValue());

            ParsedStringTerms brand_name_agg = bucket.getAggregations().get("brand_name_agg");
            brandVo.setBrandName(brand_name_agg.getBuckets().get(0).getKeyAsString());

            ParsedStringTerms brand_img_agg = bucket.getAggregations().get("brand_img_agg");
            brandVo.setBrandImg(brand_img_agg.getBuckets().get(0).getKeyAsString());

            brandVos.add(brandVo);
        }
        result.setBrands(brandVos);

        //4.封装所有商品涉及到的所有分类信息
        ParsedLongTerms catalog_agg = response.getAggregations().get("catalog_agg");
        List<SearchResult.CatalogVo> catalogVos = new ArrayList<>();
        for (Terms.Bucket bucket : catalog_agg.getBuckets()) {
            SearchResult.CatalogVo catalogVo = new SearchResult.CatalogVo();
            catalogVo.setCatalogId(bucket.getKeyAsNumber().longValue());

            ParsedStringTerms catalog_name_agg = bucket.getAggregations().get("catalog_name_agg");
            catalogVo.setCatalogName(catalog_name_agg.getBuckets().get(0).getKeyAsString());

            catalogVos.add(catalogVo);
        }
        result.setCatalogs(catalogVos);

        //5.分页信息-页码、总记录数、总页数、可遍历的页码
        result.setPageNum(param.getPageNum());

        long total = hits.getTotalHits().value;//总记录数
        result.setTotal(total);

        int totalPages = (int)total%EsConstant.PRODUCT_PAGESIZE==0 ? (int)total/EsConstant.PRODUCT_PAGESIZE:((int)total/EsConstant.PRODUCT_PAGESIZE+1);//总页数
        result.setTotalPages(totalPages);

        List<Integer> pageNavs = new ArrayList<>();
        for (int i = 1; i <= totalPages; i++) {
            pageNavs.add(i);
        }
        result.setPageNavs(pageNavs);

        //6.构建面包屑导航
        if(param.getAttrs() != null && param.getAttrs().size() > 0){
            List<SearchResult.NavVo> navVos = param.getAttrs().stream().map(attr -> {
                //1.分析请求url中每个传递过来的attr查询参数
                SearchResult.NavVo navVo = new SearchResult.NavVo();
                //attrs=2_5寸:6寸
                String[] s = attr.split("_");
                navVo.setNavValue(s[1]);
                //远程调用，根据attr的id查询attr的name
                R r = productFeignService.getAttrInfo(Long.parseLong(s[0]));

                //页面中被筛选属性的所有id也添加到结果集中，面包屑的属性显示与不显示功能需要得到选中的属性，选中的那些将不显示
                result.getAttrIds().add(Long.parseLong(s[0]));

                if(r.getCode()==0){
                    AttrResponseVo attrInfoData = r.getData("attr",new TypeReference<AttrResponseVo>() {
                    });
                    navVo.setNavName(attrInfoData.getAttrName());
                }else{
                    navVo.setNavName(s[0]);
                }
                //2.取消了这个面包屑后需要跳转的地方(link)，拿到所有查询条件，删除当前的查询条件
                String link = replaceQueryString(param, attr, "attrs");
                navVo.setLink("http://search.gulimall.com/list.html?"+link);

                return navVo;
            }).collect(Collectors.toList());

            result.setNavs(navVos);
        }

        //7.品牌和分类也添加到面包屑导航 &branId=9&branId=10
        if(param.getBrandId() != null && param.getBrandId().size() > 0){
            List<SearchResult.NavVo> attrNavs = result.getNavs();
            SearchResult.NavVo navVo = new SearchResult.NavVo();
            navVo.setNavName("品牌");
            //远程调用根据品牌Id查询品牌名
            R r = productFeignService.getBrandsInfo(param.getBrandId());
            if(r.getCode() == 0){
                List<BrandVo> brands = r.getData("brands", new TypeReference<List<BrandVo>>() {
                });
                StringBuffer buffer = new StringBuffer();
                String link = "";
                for (BrandVo brand : brands) {
                    buffer.append(brand.getName()+" ");
                    link = replaceQueryString(param,brand.getBrandId()+"","brandId");
                }
                navVo.setNavValue(buffer.toString());
                navVo.setLink("http://search.gulimall.com/list.html?"+link);
            }

            attrNavs.add(navVo);
        }




        return result;
    }

    private static String replaceQueryString(SearchParam param, String value, String key) {
        String attrEncode = null;
        try {
            attrEncode = URLEncoder.encode(value, "UTF-8");
            attrEncode = attrEncode.replace("+","%20");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        String link = param.get_QueryString().replace("&"+key+"=" + attrEncode, "");
        return link;
    }
}
