package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
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

import java.util.Map;
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

    @Override
    public Result queryById(Long id) {
        // 解决缓存穿透
        // Shop shop = queryWithPassThrough(id);

        // 互斥锁解决缓存击穿
        Shop shop = queryWithMutex(id);

        // 逻辑过期解决缓存击穿
//        Shop shop = queryWithLogicalExpire(id);
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


    public Shop queryWithLogicalExpire(Long id) {
        String cacheKey = CACHE_SHOP_KEY + id;
        // 1. 从redis中查询缓存
        Map<Object, Object> shopMap = redisTemplate.opsForHash().entries(cacheKey);
        // 2. 判断是否存在
        if (shopMap.isEmpty()) {
            return null;
        }

        Shop shop = getById(id);
        // 3.2 存在写回redis，返回数据

        redisHashUtil.save2Redis(cacheKey, shop, CACHE_SHOP_TTL, TimeUnit.MINUTES, true);
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
