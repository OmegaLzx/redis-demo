package com.hmdp.config;

import cn.hutool.core.lang.UUID;
import cn.hutool.extra.servlet.ServletUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * UV统计
 */
@Slf4j
public class UvAndPvInterceptor implements HandlerInterceptor {
    private final StringRedisTemplate redisTemplate;

    public UvAndPvInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        LocalDate now = LocalDate.now();
        // 计算uv
        String uvKey = "uv:" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        UserDTO user = UserHolder.getUser();
        String userId;
        if (user == null) {
            userId = ServletUtil.getClientIP(request);
        } else {
            userId = user.getId().toString();
        }
        log.info("访问用户: {}", userId);
        redisTemplate.opsForHyperLogLog().add(uvKey, userId);

        //计算pv
        String pvKey = "pv:" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String flag = UUID.randomUUID().toString(true);
        redisTemplate.opsForHyperLogLog().add(pvKey, flag);
        return true;
    }
}
