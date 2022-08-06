package com.hmdp.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.redisson.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.CountDownLatch;

@SpringBootTest
@RunWith(SpringRunner.class)
@Slf4j
public class RateLimitTest {
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    public void test() throws InterruptedException {
        String key = "rate:user:" + 1;
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);// 最大流速 = 每10秒钟产生1个令牌
        rateLimiter.setRate(RateType.OVERALL, 5, 1, RateIntervalUnit.SECONDS);

        execute(rateLimiter, "获取令牌1");
        execute(rateLimiter, "获取令牌2");

        Thread.sleep(Integer.MAX_VALUE);
    }

    private void execute(RRateLimiter rateLimiter, String s) {
        new Thread(() -> {
            while (true) {
                if (!rateLimiter.tryAcquire()) {
                    continue;
                }
                log.info(s);
            }
        }).start();

    }

    @Test
    public void topicTest() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        RTopic topic = redissonClient.getTopic("topic2");
        topic.addListener(String.class, (channel, msg) -> latch.countDown());

        topic.publish("msg");
        latch.await();

        redissonClient.shutdown();
    }
}
