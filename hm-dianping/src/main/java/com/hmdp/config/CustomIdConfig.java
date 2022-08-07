package com.hmdp.config;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.hmdp.utils.RedisIdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

@RequiredArgsConstructor
@Configuration
public class CustomIdConfig {
    private final RedisIdWorker redisIdWorker;

    public IdentifierGenerator idGenerator() {
        return entity -> {
            String bizKey = entity.getClass().getName();
            return redisIdWorker.nextId(bizKey);
        };
    }
}
