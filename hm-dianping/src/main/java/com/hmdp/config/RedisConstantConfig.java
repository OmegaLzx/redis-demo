package com.hmdp.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "me.redis")
@Component
@Data
public class RedisConstantConfig {
    @Value("${me.redis.loginCodePrefix:login:code:}")
    private String loginCodePrefix;

    @Value("${me.redis.loginCodeTtl:5}")
    private Long loginCodeTtl;

    @Value("${me.redis.userInfoPrefix:user:token:}")
    private String userInfoPrefix;

    @Value("${me.redis.userInfoTtl:30}")
    private Long userInfoTtl;
    private String streamClient;
}
