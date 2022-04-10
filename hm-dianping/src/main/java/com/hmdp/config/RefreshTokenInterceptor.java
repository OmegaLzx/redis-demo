package com.hmdp.config;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshTokenInterceptor implements HandlerInterceptor {
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisConstantConfig constantConfig;

    public RefreshTokenInterceptor(RedisConstantConfig redisConstantConfig, StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.constantConfig = redisConstantConfig;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取token
        String token = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (token != null) {
            String userKey = constantConfig.getUserInfoPrefix() + token;
            Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(userKey);
            // 3.1 将当前用户信息存入ThreadLocal中
            if (!userMap.isEmpty()) {
                UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
                UserHolder.saveUser(userDTO);
                // 3.2 刷新token
                stringRedisTemplate.expire(userKey, constantConfig.getUserInfoTtl(), TimeUnit.MINUTES);
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 4. 清空ThreadLocal中的用户信息
        UserHolder.removeUser();
    }
}
