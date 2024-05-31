package com.atguigu.gulimall.product.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.gulimall.product.service.CategoryBrandRelationService;
import com.atguigu.gulimall.product.vo.Catalog2Vo;
import com.netflix.ribbon.proxy.annotation.Var;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.product.dao.CategoryDao;
import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.CategoryService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;


@Service("categoryService")
public class CategoryServiceImpl extends ServiceImpl<CategoryDao, CategoryEntity> implements CategoryService {
    @Autowired
    CategoryBrandRelationService categoryBrandRelationService;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    RedissonClient redissonClient;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<CategoryEntity> page = this.page(
                new Query<CategoryEntity>().getPage(params),
                new QueryWrapper<CategoryEntity>()
        );

        return new PageUtils(page);
    }

    /**
     * 将所有菜单的分类以树形结构列出
     * @return
     */
    @Override
    public List<CategoryEntity> listWithTree() {
        //1.查出所有的分类
        List<CategoryEntity> entities = baseMapper.selectList(null);
        //2.组装成父子的树形结构
        //取出一级分类
        List<CategoryEntity> level1Menus = entities.stream().filter(categoryEntity ->
            categoryEntity.getParentCid() == 0
        ).map(menu -> {
            menu.setChildren(getChildren(menu,entities));
            return menu;
        }).sorted((menu1,menu2)->{
            return (menu1.getSort()==null?0:menu1.getSort()) - (menu2.getSort()==null?0:menu2.getSort());
        }).collect(Collectors.toList());

        return level1Menus;
    }

    /**
     * 递归获取某个菜单中的所有子菜单
     * @param root 当前操作的菜单看成root，查找它所有的子节点
     * @param all 所有的菜单，在所有的菜单中查找当前菜单的子节点
     * @return 返回所有的子节点
     */
    private List<CategoryEntity> getChildren(CategoryEntity root,List<CategoryEntity> all){
        List<CategoryEntity> children = all.stream().filter(categoryEntity -> {
            return categoryEntity.getParentCid().equals(root.getCatId()) ;
        }).map(categoryEntity -> {
            //递归寻找子菜单
            categoryEntity.setChildren(getChildren(categoryEntity,all));
            return categoryEntity;
        }).sorted((categoryEntity1,categoryEntity2)->{
            //将子菜单进行排序
            return (categoryEntity1.getSort()==null?0:categoryEntity1.getSort()) - (categoryEntity2.getSort()==null?0:categoryEntity2.getSort());
        }).collect(Collectors.toList());
        return children;
    }

    @Override
    public void removeMenuByIds(List<Long> asList) {
        //TODO:检查当前删除的菜单是否被其他地方引用
        //逻辑删除菜单
        baseMapper.deleteBatchIds(asList);
    }

    /**
     * 查找catelogId的完整路径 [父,子,孙] [2,25,225]
     * @param catelogId
     * @return
     */
    @Override
    public Long[] findCatelogPath(Long catelogId) {
        List<Long> paths = new ArrayList<>();

        paths = findParentPath(catelogId,paths);

        Collections.reverse(paths);

        return paths.toArray(new Long[paths.size()]);
    }

    /**
     * 最后收集到的paths [225,25,2]
     * @param catelogId
     * @param paths
     * @return
     */
    private List<Long> findParentPath(Long catelogId,List<Long> paths){
        // 先收集当前节点的id
        paths.add(catelogId);

        CategoryEntity categoryEntity = this.getById(catelogId);
        if(categoryEntity.getParentCid()!=0){
            findParentPath(categoryEntity.getParentCid(),paths);
        }

        return paths;
    }

    /**
     * 修改商品分类的信息，修改后应该将商品的一级分类缓存和二、三级分类缓存全部删除掉
     * levelOneCategorys 和 catalogJson
     * @CacheEvict 一次只能删除一个缓存
     * 想要一次性删除多个，需要使用 @Caching
     * @param category
     */
    //@CacheEvict(value = "category",allEntries = true)//删除该category分区下所有的缓存
    @Caching(evict = {
            @CacheEvict(value = "category",key = "'levelOneCategorys'"),
            @CacheEvict(value = "category",key = "'catalogJson'")
    })
    @Transactional
    @Override
    public void updateDetail(CategoryEntity category) {
        this.updateById(category);

        if(!StringUtils.isEmpty(category.getName())){
            categoryBrandRelationService.updateCategoryName(category.getCatId(),category.getName());
            //TODO : 更新其他表与商品名关联的字段
        }

    }

    /**
     * @Cacheable
     * 1.代表当前方法查询到的商品一级分类的结果需要缓存，如果缓存中有，方法无需调用。
     * 如果缓存中没有，会调用该方法，最后将方法的结果放入缓存。
     *
     * 2.默认行为：
     * 如果缓存中有数据，方法不调用
     * key默认自动生成为 分区名::SimpleKey []
     * value默认存储java序列化之后的结果
     * 默认过期时间永不过期
     *
     * 3.自定义行为：
     * 指定生成的缓存的Key值：使用Key属性
     * 指定缓存的过期时间：在配置文件中配置
     * 指定保存的缓存的Value值为Json格式的数据：
     * CacheAutoConfiguration->RedisCacheConfiguration->RedisCacheManager
     * ->初始化每个分区的缓存->每个缓存决定使用什么配置
     * ->RedisCacheConfiguration有配置就用已有的，没有就用默认配置
     * ->修改缓存中的配置，只需要在容器中放入RedisCacheConfiguration覆盖默认的即可
     * ->就会将配置应用到当前RedisCacheManager管理的所有缓存分区中
     * @return
     */
    @Cacheable(cacheNames = {"category"},key = "'levelOneCategorys'")//缓存的数据放到category分区中
    @Override
    public List<CategoryEntity> getLevelOneCategorys() {
        //System.out.println("获取了一级分类数据...");
        List<CategoryEntity> categoryEntities = baseMapper.selectList(
                new QueryWrapper<CategoryEntity>()
                        .eq("parent_cid", 0)
        );
        return categoryEntities;
    }

    /**
     * 使用SpringCache简化获取二、三级商品分类的数据：
     * 原先 查缓存->若缓存没有->查数据库->放缓存中 若缓存中有->缓存返回 多写了很多业务
     * 现在 只需要 查数据库 这一业务即可，其他的全部交给SpringCache
     * sync = true，防止缓存击穿问题，给以下方法的业务操作加锁，使得只有第一个抢占到锁的线程来查询数据库
     * @return
     */
    @Cacheable(cacheNames = {"category"},key = "'catalogJson'",sync = true)
    @Override
    public Map<String, List<Catalog2Vo>> getCatalogJson() {
        //System.out.println("查询了数据库...");
        //1.优化，在数据库中一次性查询出所有的三级分类
        List<CategoryEntity> selectAll = baseMapper.selectList(null);

        //1.先查出所有一级分类的id作为map的key值
        List<CategoryEntity> levelOneCategorys = getCategoryChildren(selectAll,0L);

        //2.封装二、三级分类作为map的value值
        Map<String, List<Catalog2Vo>> catalog2Map = levelOneCategorys.stream().collect(Collectors.toMap(
                        k -> k.getCatId().toString(), //设置key为一级分类的Id
                        v -> {
                            //查询遍历的每一个一级分类的所有二级分类
                            List<CategoryEntity> categoryTwos = getCategoryChildren(selectAll,v.getCatId());

                            //将所有查询到的二级分类从CategoryEntity封装为Catalog2Vo形式
                            List<Catalog2Vo> catalog2Vos = null;
                            if (categoryTwos != null) {
                                catalog2Vos = categoryTwos.stream().map(categoryTwo -> {
                                    //item为二级分类，查询三级分类
                                    //查询遍历的每一个二级分类的所有三级分类
                                    List<CategoryEntity> categoryThrees = getCategoryChildren(selectAll,categoryTwo.getCatId());
                                    //封装所有的三级分类为Catalog3Vos
                                    List<Catalog2Vo.Catalog3Vo> catalog3Vos = null;
                                    if(categoryThrees != null){
                                        catalog3Vos = categoryThrees.stream().map(categoryThree -> {
                                            return new Catalog2Vo.Catalog3Vo(categoryTwo.getCatId().toString(), categoryThree.getCatId().toString(), categoryThree.getName());
                                        }).collect(Collectors.toList());
                                    }

                                    return new Catalog2Vo(v.getCatId().toString(), catalog3Vos, categoryTwo.getCatId().toString(), categoryTwo.getName());
                                }).collect(Collectors.toList());
                            }

                            return catalog2Vos;//设置value为二级分类封装好的Catalog2Vos
                        }
                )
        );
        return catalog2Map;
    }

    /**
     * 在redis中查询缓存，如果存在就直接使用缓存中的二、三级分类数据
     * 如果不存在，就调用getCatalogJsonFromDB()查询，并将结果保存到redis中
     * 缓存穿透、缓存雪崩、缓存击穿等问题
     * @return
     */
    //@Override
    public Map<String, List<Catalog2Vo>> getCatalogJson2() {
        /**
         * 解决缓存穿透、缓存雪崩、缓存击穿等问题：
         * 1.空结果缓存：缓存穿透
         * 2.设置过期时间（加随机值）：缓存雪崩
         * 3.加锁：缓存击穿
         */
        //1.查询redis中是否有缓存
        String catalogJSON = stringRedisTemplate.opsForValue().get("catalogJSON");
        if(StringUtils.isEmpty(catalogJSON)){
            //2.redis中没有缓存
            Map<String, List<Catalog2Vo>> catalogJsonFromDB = getCatalogJsonFromDBWithRedisLock();
            return catalogJsonFromDB;
        }else{
            //3.redis中有缓存，取出缓存的数据
            //逆转为Map<String, List<Catalog2Vo>>对象返回
            return JSON.parseObject(catalogJSON,new TypeReference<Map<String, List<Catalog2Vo>>>(){});
        }
    }

    /**
     * 使用redisson来实现分布式锁
     * 缓存中的商品三级分类数据如何和数据库保持一致：
     * 1.双写模式，更新数据库的同时更新缓存
     * 问题：可能会导致脏读问题
     * 写数据库1-------------------->写缓存1
     *         写数据库2--->写缓存2
     * 这样就导致写缓存2没有成功写入，被卡顿的后续的写缓存1覆盖了
     * 2.失效模式，更新数据库的同时删除缓存
     *
     * 我们系统的一致性解决方案：
     * 1.缓存的所有数据都有过期时间，数据过期下一次查询触发主动更新
     * 2.读写数据的时候加上分布式的读写锁
     * @return
     */
    public Map<String, List<Catalog2Vo>> getCatalogJsonFromDBWithRedissonLock() {
        //获取一把redisson分布式锁，指定key的值
        RLock lock = redissonClient.getLock("catalogJson-lock");
        //加锁
        lock.lock();

        Map<String, List<Catalog2Vo>> dataFromDB;
        try{
            //加锁成功，抢占到分布式锁，作为第一个查询数据库的分布式线程
            dataFromDB = getDataFromDB();
        }finally {
            //解锁
           lock.unlock();
        }
        return dataFromDB;
    }

    /**
     * 根据所有的一级分类，从数据库中查询所有商品的二、三级分类
     *
     * 使用redis分布式锁，解决分布式问题
     *
     * 场景一：没有设置过期时间，中途断电或者发生异常，锁没有成功释放，造成死锁
     * 场景二：设置了过期时间，但是没有在设置锁的时候同时设置过期时间，若设置过期时间之前断电或者异常，过期时间没有成功设置，同样导致死锁
     * 解决：在抢占锁的同时设置上过期时间
     *
     * 场景三：业务超时，过期时间一到，自己线程的锁过期了，但是后续业务继续执行完毕，删除锁时，删的是别人的锁
     * 解决：在加锁的的时候，将值设置为uuid，在删除锁时，根据锁的value值进行删除，
     * 这一过程也要保证原子性，否则可能在对比uuid时，对比正确redis返回true，在传输true过程中，网络波动
     * 在程序这边接收到true时，刚好又已经超过了过期时间，原先的锁过期自动删除了，又把其他线程的锁删除了
     * @return
     */
    public Map<String, List<Catalog2Vo>> getCatalogJsonFromDBWithRedisLock() {
        //1.抢占分布式锁，使用redis占坑，返回抢占的结果
        //设置一个UUID作为分布式锁的value值，防止自己的业务超时分布式锁自动过期，在删除锁时删除了其他线程的锁
        String uuid = UUID.randomUUID().toString();
        //为锁设置一个过期时间(在加锁的时候就要设置)，防止在删锁之前发生异常或者宕机断电等问题，导致锁没有释放，造成死锁问题
        //锁的过期时间设置长一点就不需要关心续机问题了
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent("lock",uuid,300,TimeUnit.SECONDS);
        if(lock){
            //System.out.println("获取分布式锁成功...");
            Map<String, List<Catalog2Vo>> dataFromDB;
            try{
                //加锁成功，抢占到分布式锁，作为第一个查询数据库的分布式线程
                dataFromDB = getDataFromDB();
            }finally {
                //业务执行完毕，删除分布式锁
                //stringRedisTemplate.delete("lock"); //可能出现业务超时误删了其他线程的锁
                //采用lua脚本解锁，保证删除锁的原子性
                String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
                Long deleteFlag = stringRedisTemplate.execute(new DefaultRedisScript<Long>(script, Long.class),
                        Arrays.asList("lock"),
                        uuid);//Integer为设置的脚本的返回值类型，1删除成功，0删除失败
            }
            return dataFromDB;
        }else{
            //加锁失败，没有抢到分布式锁，不断进行重试
            //System.out.println("获取分布式锁失败...等待重试");
            try {
                Thread.sleep(200);
            } catch (Exception e) {

            }
            return getCatalogJsonFromDBWithRedisLock();//自旋的方式进行重试
        }
    }

    /**
     * 根据所有的一级分类，从数据库中查询所有商品的二、三级分类
     * 使用了synchronized本地锁，无法解决分布式问题
     * @return
     */
    public Map<String, List<Catalog2Vo>> getCatalogJsonFromDBWithLocalLock() {
        //TODO : 本地锁，只能锁住当前的进程，在分布式场景下还是会有多个请求同时查询数据库。所以需要分布式锁
        //this是CategoryService，在SpringBoot中是单例的，可以作为锁的对象，解决缓存击穿
        synchronized (this){
            //加锁以后，100万个人串行访问DB，每个人都需要先查一遍缓存，因为一般只有第一个人才需要查询数据库
            //加锁成功，抢占到本地锁，作为第一个查询数据库的本地线程
            return getDataFromDB();
        }
    }

    /**
     * 高并发访问服务时，如果redis中没有缓存，会有大量请求同时访问数据库
     * 以下方法为：在加锁的情况下，如何避免大批数据同时访问数据库
     * 1.先查一遍缓存，看看有没有人先抢占到锁查询了数据库，并将数据封装到redis中
     * 2.如果没有，说明是第一个拿到锁的线程，查询数据库
     * 3.将查询到的数据存储到redis中，下一次拿到锁的线程就无需再查数据库了
     * @return
     */
    private Map<String, List<Catalog2Vo>> getDataFromDB() {
        //加锁以后，100万个人串行访问DB，每个人都需要先查一遍缓存，因为一般只有第一个人才需要查询数据库
        String catalogJSON = stringRedisTemplate.opsForValue().get("catalogJSON");
        if(!StringUtils.isEmpty(catalogJSON)){
            //redis中有缓存，说明前面串行访问已经有人查过数据库了，将缓存结果转为对象返回
            return JSON.parseObject(catalogJSON, new TypeReference<Map<String, List<Catalog2Vo>>>() {
            });
        }

        //System.out.println("开始查询数据库...");
        //1.优化，在数据库中一次性查询出所有的三级分类
        List<CategoryEntity> selectAll = baseMapper.selectList(null);

        //1.先查出所有一级分类的id作为map的key值
        List<CategoryEntity> levelOneCategorys = getCategoryChildren(selectAll,0L);

        //2.封装二、三级分类作为map的value值
        Map<String, List<Catalog2Vo>> catalog2Map = levelOneCategorys.stream().collect(Collectors.toMap(
                        k -> k.getCatId().toString(), //设置key为一级分类的Id
                        v -> {
                            //查询遍历的每一个一级分类的所有二级分类
                            List<CategoryEntity> categoryTwos = getCategoryChildren(selectAll,v.getCatId());

                            //将所有查询到的二级分类从CategoryEntity封装为Catalog2Vo形式
                            List<Catalog2Vo> catalog2Vos = null;
                            if (categoryTwos != null) {
                                catalog2Vos = categoryTwos.stream().map(categoryTwo -> {
                                    //item为二级分类，查询三级分类
                                    //查询遍历的每一个二级分类的所有三级分类
                                    List<CategoryEntity> categoryThrees = getCategoryChildren(selectAll,categoryTwo.getCatId());
                                    //封装所有的三级分类为Catalog3Vos
                                    List<Catalog2Vo.Catalog3Vo> catalog3Vos = null;
                                    if(categoryThrees != null){
                                        catalog3Vos = categoryThrees.stream().map(categoryThree -> {
                                            return new Catalog2Vo.Catalog3Vo(categoryTwo.getCatId().toString(), categoryThree.getCatId().toString(), categoryThree.getName());
                                        }).collect(Collectors.toList());
                                    }

                                    return new Catalog2Vo(v.getCatId().toString(), catalog3Vos, categoryTwo.getCatId().toString(), categoryTwo.getName());
                                }).collect(Collectors.toList());
                            }

                            return catalog2Vos;//设置value为二级分类封装好的Catalog2Vos
                        }
                )
        );

        //将数据库查询得到的数据转为json串存入缓存
        String jsonString = JSON.toJSONString(catalog2Map);
        stringRedisTemplate.opsForValue().set("catalogJSON",jsonString,1, TimeUnit.DAYS);

        return catalog2Map;
    }

    /**
     * 传递所有的实体对象，在所有实体对象中找出父节点为parentCid的所有子实体
     * 即获取一级父分类下的所有实体，或者获取二级父分类下的所有实体
     * @param selectAll
     * @param parentCid 本次要获取的对象的父id
     * @return
     */
    private List<CategoryEntity> getCategoryChildren(List<CategoryEntity> selectAll,Long parentCid) {
        return selectAll.stream().filter(item->{
            if(item.getParentCid() == parentCid){
                return true;
            }
            return false;
        }).collect(Collectors.toList());
    }

}