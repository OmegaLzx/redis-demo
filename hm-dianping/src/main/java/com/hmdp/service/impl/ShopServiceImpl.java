package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
    private final StringRedisTemplate redisTemplate;

    @Override
    public Result queryById(Long id) {
        Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    public Shop queryWithPassThrough(Long id) {
        String cacheKey = CACHE_SHOP_KEY + id;
        // 1. 从redis中查询缓存
        Map<Object, Object> shopMap = redisTemplate.opsForHash().entries(cacheKey);
        // 2. 判断是否存在
        if (!shopMap.isEmpty()) {
            if (shopMap.get("nil") != null) {
                return null;
            }
            return BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
        }
        // 3 不存在，则查询数据库
        Shop shop = getById(id);
        // 3.1 数据库中不存在，返回错误信息
        if (shop == null) {
            redisTemplate.opsForHash().putAll(cacheKey, MapUtil.of("nil", "nil"));
            redisTemplate.expire(cacheKey, CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 3.2 存在写回redis，返回数据

        redisTemplate.opsForHash().putAll(cacheKey, BeanUtil.beanToMap(shop, new HashMap<>(), CopyOptions.create()
                .setIgnoreNullValue(true)
                .setIgnoreError(false)
                .setFieldValueEditor((field, value) -> Optional.ofNullable(value).map(Object::toString).orElse(null))));
        redisTemplate.expire(cacheKey, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    public Shop queryWithMutex(Long id) {
        String cacheKey = CACHE_SHOP_KEY + id;
        // 1. 从redis中查询缓存
        Map<Object, Object> shopMap = redisTemplate.opsForHash().entries(cacheKey);
        // 2. 判断是否存在
        if (!shopMap.isEmpty()) {
            if (shopMap.get("nil") != null) {
                return null;
            }
            return BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
        }
        // 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop;
        try {
            // 判断是否获取成功
            if (!tryLock(lockKey)) {
                // 失败，则休眠并重试
                Thread.sleep(10);
                return queryWithMutex(id);
            }

            // 3 实现缓存重建
            shop = getById(id);
            // 3.1 数据库中不存在，返回错误信息
            if (shop == null) {
                redisTemplate.opsForHash().putAll(cacheKey, MapUtil.of("nil", "nil"));
                redisTemplate.expire(cacheKey, CACHE_SHOP_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 3.2 存在写回redis，返回数据
            redisTemplate.opsForHash().putAll(cacheKey, BeanUtil.beanToMap(shop, new HashMap<>(), CopyOptions.create()
                    .setIgnoreNullValue(true)
                    .setIgnoreError(false)
                    .setFieldValueEditor((field, value) -> Optional.ofNullable(value).map(Object::toString).orElse(null))));
            redisTemplate.expire(cacheKey, CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return shop;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            // 释放互斥锁
            unlock(lockKey);
        }
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

    private boolean tryLock(String key) {
        return BooleanUtil.isTrue(redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS));
    }

    private void unlock(String key) {
        redisTemplate.delete(key);
    }
}
