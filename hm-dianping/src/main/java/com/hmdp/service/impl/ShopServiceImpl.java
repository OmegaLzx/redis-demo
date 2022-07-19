package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.RedisHashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.constant.RedisConstants.*;
import static com.hmdp.utils.RedisHashUtil.NULL_VALUE;

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
    private final RedisHashUtil redisHashUtil;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public Shop queryWithPassThrough(Long id) {
        String cacheKey = CACHE_SHOP_KEY + id;
        // 1. 从redis中查询缓存
        Map<Object, Object> shopMap = redisTemplate.opsForHash().entries(cacheKey);
        // 2. 判断是否存在
        if (!shopMap.isEmpty()) {
            if (shopMap.get(NULL_VALUE) != null) {
                return null;
            }
            return BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
        }
        return redisHashUtil.save2Redis(CACHE_SHOP_KEY, id, CACHE_SHOP_TTL, TimeUnit.MINUTES, true, this::getById);
    }

    public Shop queryWithMutex(Long id) {
        String cacheKey = CACHE_SHOP_KEY + id;
        // 1. 从redis中查询缓存
        Map<Object, Object> shopMap = redisTemplate.opsForHash().entries(cacheKey);
        // 2. 判断是否存在
        if (!shopMap.isEmpty()) {
            if (shopMap.get(NULL_VALUE) != null) {
                return null;
            }
            return BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
        }
        // 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop;
        try {
            // 判断是否获取成功
            if (!redisHashUtil.tryLock(lockKey)) {
                // 失败，则休眠并重试
                Thread.sleep(10);
                return queryWithMutex(id);
            }
            // 3 实现缓存重建
            return redisHashUtil.save2Redis(CACHE_SHOP_KEY, id, CACHE_SHOP_TTL, TimeUnit.MINUTES, true, this::getById);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            // 释放互斥锁
            redisHashUtil.unlock(lockKey);
        }
    }

    @Override
    public Result queryById(Long id) {
        // 解决缓存穿透
        // Shop shop = queryWithPassThrough(id);

        // 互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);

        // 逻辑过期解决缓存击穿
        Shop shop = queryWithLogicalExpire(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    public Shop queryWithLogicalExpire(Long id) {
        String cacheKey = CACHE_SHOP_KEY + id;
        // 1. 从redis中查询缓存
        Map<Object, Object> shopMap = redisTemplate.opsForHash().entries(cacheKey);
        // 2. 判断是否存在
        if (shopMap.isEmpty()) {
            return null;
        }
        // 缓存命中，先反序列化为对象
        RedisData redisData = redisHashUtil.getRedisData(Shop.class, cacheKey);
        Shop shop = (Shop) redisData.getData();
        LocalDateTime expireTime = redisData.getExpireTime();
        // 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期，直接返回店铺信息
            return shop;
        }
        // 已过期，需要缓存重建
        // 获取互斥锁
        // 判断是否获取锁成功
        if (redisHashUtil.tryLock(LOCK_SHOP_KEY + id)) {
            // 成功执行缓存重建
            // 双重检查
            redisData = redisHashUtil.getRedisData(Shop.class, cacheKey);
            if (redisData.getExpireTime().isBefore(LocalDateTime.now())) {
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                            try {
                                redisHashUtil.save2RedisWithLogicalExpire(CACHE_SHOP_KEY, id, CACHE_SHOP_TTL,
                                        TimeUnit.MINUTES, true, this::getById);
                            } catch (Exception e) {
                                log.error("", e);
                            } finally {
                                redisHashUtil.unlock(LOCK_SHOP_KEY + id);
                            }
                        }
                );
            }
        }
        return shop;
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
