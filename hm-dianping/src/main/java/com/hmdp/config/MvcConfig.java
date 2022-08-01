package com.hmdp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    public RedisConstantConfig constantConfig;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/user/login",
                        "/user/code",
                        "/user/uv/*",
                        "/user/pv/*"
                )
                .order(2);

        registry.addInterceptor(new RefreshTokenInterceptor(constantConfig, stringRedisTemplate))
                .addPathPatterns("/**").order(0);

        registry.addInterceptor(new UvAndPvInterceptor(stringRedisTemplate))
                .addPathPatterns("/**").order(1);

        WebMvcConfigurer.super.addInterceptors(registry);
    }
}
