package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import com.hmdp.constant.RedisScriptConstant;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collections;
import java.util.concurrent.TimeUnit;


public class SimpleRedisLock implements ILock {
    private static final String KEY_PREFIX = "lock:";
    // 避免业务运行时间过长导致释放锁时误删其他进程加的锁
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    //private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    //static {
    //    UNLOCK_SCRIPT = new DefaultRedisScript<>();
    //    UNLOCK_SCRIPT.setLocation(new ClassPathResource(SCRIPT_PATH + "unlock.lua"));
    //    UNLOCK_SCRIPT.setResultType(Long.class);
    //}

    private final String name;
    private final StringRedisTemplate redisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate redisTemplate) {
        this.name = name;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        Boolean success = redisTemplate.opsForValue().setIfAbsent(
                KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        redisTemplate.execute(RedisScriptConstant.get(RedisScriptConstant.UNLOCK_SCRIPT),
                              Collections.singletonList(KEY_PREFIX + name), ID_PREFIX + Thread.currentThread().getId()
        );
    }
    //    @Override
    //    public void unlock() {
    //        // (1)获取线程标识
    //        String threadId = ID_PREFIX + Thread.currentThread().getId();
    //        String id = redisTemplate.opsForValue().get(KEY_PREFIX + name);
    //        if (threadId.equals(id)) {
    //            // todo 当在此处出现过长STW时，因为语句(1)(2)不具有原子性，仍然可能产生误删不属于自己的锁
    //            // (2)释放锁
    //            redisTemplate.delete(KEY_PREFIX + name);
    //        }
    //    }
}
