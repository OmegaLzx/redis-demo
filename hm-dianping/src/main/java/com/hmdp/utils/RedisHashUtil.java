package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.BooleanUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisHashUtil {
    public static final String NULL_VALUE = "nil";
    public static final String EXPIRE_TIME_KEY = "expireTime";
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    private final StringRedisTemplate redisTemplate;

    /**
     * 尝试加锁
     */
    public boolean tryLock(String key) {
        return BooleanUtil.isTrue(redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS));
    }

    /**
     * 解锁
     */
    public void unlock(String key) {
        redisTemplate.delete(key);
    }

    public void save2Redis(String key, Map<String, String> map) {
        redisTemplate.opsForHash().putAll(key, map);
    }

    /**
     * 存储到redis key-object
     * 不设置过期时间
     */
    public <R> R save2Redis(String key, R r, boolean saveNull) {
        if (saveNull && r == null) {
            save2Redis(key, MapUtil.of(NULL_VALUE, NULL_VALUE));
            return null;
        }
        redisTemplate.opsForHash().putAll(key, BeanUtil.beanToMap(r, new HashMap<>(), CopyOptions.create()
                .setIgnoreNullValue(true)
                .setIgnoreError(false)
                .setFieldValueEditor((field, value) -> Optional.ofNullable(value).map(Object::toString).orElse(null))));
        return r;
    }

    /**
     * 函数式
     * 存储到redis key-object
     * 设置过期时间
     */
    public <T, R> R save2Redis(String prefix, T id, boolean saveNull, Function<T, R> function) {
        R r = function.apply(id);
        String key = prefix + id;
        save2Redis(key, r, saveNull);
        return r;
    }

    /**
     * 存储到redis key-key'-value
     * 不设置过期时间
     */
    public void save2Redis(String key, String key1, String value) {
        redisTemplate.opsForHash().put(key, key1, value);
    }

    /**
     * 以逻辑过期时间存储到redis
     */
    public <T, R> RedisData<R> save2RedisWithLogicalExpire(String prefix, T id, Long expireTime, TimeUnit unit, boolean saveNull, Function<T, R> function) {
        R r = save2Redis(prefix, id, saveNull, function);
        LocalDateTime localDateTime = LocalDateTime.now().plusSeconds(unit.toSeconds(expireTime));
        save2Redis(prefix + id, EXPIRE_TIME_KEY, localDateTime.toString());
        return new RedisData<>(r, localDateTime);
    }


    /**
     * 函数式 设置过期时间存储到redis
     */
    public <T, R> R save2Redis(String prefix, T id, Long expireTime, TimeUnit timeUnit, boolean saveNull, Function<T, R> function) {
        R r = save2Redis(prefix, id, saveNull, function);
        expire(prefix + id, expireTime, timeUnit);
        return r;
    }


    /**
     * 存储到redis key-object
     * 设置过期时间
     */
    public <R> R save2Redis(String key, R obj, Long expireTime, TimeUnit timeUnit, boolean saveNull) {
        R r = save2Redis(key, obj, saveNull);
        expire(key, expireTime, timeUnit);
        return r;
    }

    public void save2Redis(String key, Map<String, String> map, Long expireTime, TimeUnit timeUnit) {
        save2Redis(key, map);
        expire(key, expireTime, timeUnit);
    }


    public void expire(String key, Long expireTime, TimeUnit unit) {
        redisTemplate.expire(key, expireTime, unit);
    }

    public <T> RedisData<T> getRedisData(Class<T> clazz, String key) {
        Map<Object, Object> map = redisTemplate.opsForHash().entries(key);
        T data;
        try {
            data = BeanUtil.fillBeanWithMap(map, clazz.newInstance(), false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Object expireObj = redisTemplate.opsForHash().get(key, EXPIRE_TIME_KEY);
        if (expireObj == null) {
            throw new RuntimeException();
        }
        LocalDateTime expireTime = LocalDateTime.parse((String) expireObj);
        return new RedisData<>(data, expireTime);
    }

    public <T, R> R queryWithPassThrough(String prefix, T id, Class<R> clazz, Long expireTime, TimeUnit unit, Function<T, R> function) {
        String cacheKey = prefix + id;
        // 1. 从redis中查询缓存
        Map<Object, Object> map = redisTemplate.opsForHash().entries(cacheKey);
        // 2. 判断是否存在
        if (!map.isEmpty()) {
            if (map.get(NULL_VALUE) != null) {
                return null;
            }
            try {
                return BeanUtil.fillBeanWithMap(map, clazz.newInstance(), false);
            } catch (Exception e) {
                log.error("", e);
            }
        }
        return save2Redis(prefix, id, expireTime, unit, true, function);
    }

    public <T, R> R queryWithMutex(String prefix, T id, Class<R> clazz, String lockPrefix, Long expireTime, TimeUnit unit, Function<T, R> function) {
        String cacheKey = prefix + id;
        // 1. 从redis中查询缓存
        Map<Object, Object> map = redisTemplate.opsForHash().entries(cacheKey);
        // 2. 判断是否存在
        if (!map.isEmpty()) {
            if (map.get(NULL_VALUE) != null) {
                return null;
            }
            try {
                return BeanUtil.fillBeanWithMap(map, clazz.newInstance(), false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        // 获取互斥锁
        String lockKey = lockPrefix + id;
        try {
            // 判断是否获取成功
            if (!tryLock(lockKey)) {
                // 失败，则休眠并重试
                Thread.sleep(10);
                return queryWithMutex(prefix, id, clazz, lockPrefix, expireTime, unit, function);
            }
            // 3 实现缓存重建
            return save2Redis(prefix, id, expireTime, unit, true, function);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            // 释放互斥锁
            unlock(lockKey);
        }
    }

    public <T, R> R queryWithLogicalExpire(String prefix, T id, Class<R> clazz, String lockPrefix, Long expireTime,
                                           TimeUnit unit, Function<T, R> function) {
        String cacheKey = prefix + id;
        // 1. 从redis中查询缓存
        Map<Object, Object> map = redisTemplate.opsForHash().entries(cacheKey);
        // 2. 判断是否存在
        if (map.isEmpty()) {
            return null;
        }
        // 缓存命中，先反序列化为对象
        RedisData<R> redisData = getRedisData(clazz, cacheKey);
        R r = redisData.getData();
        LocalDateTime localDateTime = redisData.getExpireTime();
        // 判断是否过期
        if (localDateTime.isAfter(LocalDateTime.now())) {
            // 未过期，直接返回店铺信息
            return r;
        }
        // 已过期，需要缓存重建
        // 获取互斥锁
        // 判断是否获取锁成功
        if (tryLock(lockPrefix + id)) {
            // 成功执行缓存重建
            // 双重检查
            redisData = getRedisData(clazz, cacheKey);
            if (redisData.getExpireTime().isBefore(LocalDateTime.now())) {
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                                                  try {
                                                      save2RedisWithLogicalExpire(prefix, id, expireTime,
                                                                                  unit, true, function
                                                      );
                                                  } catch (Exception e) {
                                                      log.error("", e);
                                                  } finally {
                                                      unlock(lockPrefix + id);
                                                  }
                                              }
                );
            }
        }
        return r;
    }
}
