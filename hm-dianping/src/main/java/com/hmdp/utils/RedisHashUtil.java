package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.BooleanUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisHashUtil {
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

    /**
     * 存储到redis key-object
     * 不设置过期时间
     */
    public void save2Redis(String key, Object obj) {
        redisTemplate.opsForHash().putAll(key, BeanUtil.beanToMap(obj, new HashMap<>(), CopyOptions.create()
                .setIgnoreNullValue(true)
                .setIgnoreError(false)
                .setFieldValueEditor((field, value) -> Optional.ofNullable(value).map(Object::toString).orElse(null))));
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
    public <T, R> void save2RedisWithLogicalExpire(String prefix, T id, Long expireTime, TimeUnit unit, Function<T, R> function) {
        R data = function.apply(id);
        save2Redis(prefix + id, data);
        save2Redis(prefix + id, "expireTime", LocalDateTime.now().plusSeconds(unit.toSeconds(expireTime)).toString());
    }


    /**
     * 函数式 设置过期时间存储到redis
     */
    public <T, R> void save2Redis(String prefix, T id, Long expireTime, TimeUnit timeUnit, Function<T, R> function) {
        save2Redis(prefix, id, function);
        redisTemplate.expire(prefix + id, expireTime, timeUnit);
    }

    /**
     * 函数式
     * 存储到redis key-object
     * 设置过期时间
     */
    public <T, R> void save2Redis(String prefix, T id, Function<T, R> function) {
        R data = function.apply(id);
        String key = prefix + id;
        save2Redis(key, data);
    }

    /**
     * 存储到redis key-object
     * 设置过期时间
     */
    public void save2Redis(String key, Object obj, Long expireTime, TimeUnit timeUnit) {
        save2Redis(key, obj);
        redisTemplate.expire(key, expireTime, timeUnit);
    }

}
