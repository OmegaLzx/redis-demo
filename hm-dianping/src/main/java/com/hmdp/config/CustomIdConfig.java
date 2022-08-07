package com.hmdp.config;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.hmdp.utils.RedisIdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@RequiredArgsConstructor
@Configuration
public class CustomIdConfig {
    private final RedisIdWorker redisIdWorker;

    @Bean
    public IdentifierGenerator idGenerator() {
        return entity -> {
            String bizKey = entity.getClass().getSimpleName().toLowerCase();
            return redisIdWorker.nextId(bizKey);
        };
    }
}
