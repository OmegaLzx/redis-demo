package com.hmdp.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisHashUtil;
import com.hmdp.utils.VoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.constant.RedisConstants.*;
import static com.hmdp.constant.SystemConstants.DEFAULT_PAGE_SIZE;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    private final StringRedisTemplate redisTemplate;
    private final RedisHashUtil redisHashUtil;

    public Shop queryWithPassThrough(Long id) {
        return redisHashUtil.queryWithPassThrough(
                CACHE_SHOP_KEY, id, Shop.class, CACHE_SHOP_TTL, TimeUnit.MINUTES, this::getById);
    }

    public Shop queryWithMutex(Long id) {
        return redisHashUtil.queryWithMutex(
                CACHE_SHOP_KEY, id, Shop.class, LOCK_SHOP_KEY, CACHE_SHOP_TTL, TimeUnit.MINUTES, this::getById);
    }

    @Override
    public Result queryById(Long id) {
        // 解决缓存穿透
        Shop shop = queryWithPassThrough(id);

        // 互斥锁解决缓存击穿
        // Shop shop = queryWithMutex(id);

        // 逻辑过期解决缓存击穿
        // Shop shop = queryWithLogicalExpire(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    public Shop queryWithLogicalExpire(Long id) {
        return redisHashUtil.queryWithLogicalExpire(
                CACHE_SHOP_KEY, id, Shop.class, LOCK_SHOP_KEY, CACHE_SHOP_TTL, TimeUnit.MINUTES, this::getById);
    }

    @Override
    @Transactional
    //todo 完整学习spring事务传播机制
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺不存在");
        }
        // 1. 更新数据库
        if (!updateById(shop)) {
            return Result.fail("店铺不存在");
        }
        // 2. 删除缓存
        String cacheKey = CACHE_SHOP_KEY + id;
        redisTemplate.delete(cacheKey);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if (x == null || y == null) {
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        int from = (current - 1) * DEFAULT_PAGE_SIZE;
        int end = current * DEFAULT_PAGE_SIZE;
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
        );
        if (results == null) {
            return Result.ok();
        }
        Map<String, Double> collect = results.getContent().stream().skip(from)
                .collect(Collectors.toMap(
                                 result -> result.getContent().getName(),
                                 result -> result.getDistance().getValue(),
                                 (k1, k2) -> k1
                         )
                );

        Set<String> ids = collect.keySet();
        String idStr = CharSequenceUtil.join(",", ids);

        List<Shop> shopList = VoUtil.fill(
                () -> query().in("id", ids)
                        .last("ORDER BY FIELD(id, " + idStr + ")")
                        .list(),
                shops -> shops.forEach(
                        shop -> shop.setDistance(collect.get(shop.getId().toString()))
                )
        );
        return Result.ok(shopList);
    }

}
