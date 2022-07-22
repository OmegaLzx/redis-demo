package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisHashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.constant.RedisConstants.*;

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


}
