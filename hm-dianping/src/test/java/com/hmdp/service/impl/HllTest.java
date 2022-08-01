package com.hmdp.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest
@RunWith(SpringRunner.class)
@Slf4j
public class HllTest {
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    public void add() {
        String[] values = new String[1000];
        int j = 0;
        for (int i = 0; i < 1e7; i++) {
            j = i % 1000;
            values[j] = "user_" + i;
            if (j == 999) {
                redisTemplate.opsForHyperLogLog().add("h1", values);
            }
        }
        System.out.println(redisTemplate.opsForHyperLogLog().size("h1"));
    }
}
