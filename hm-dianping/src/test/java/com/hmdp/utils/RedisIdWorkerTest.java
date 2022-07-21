package com.hmdp.utils;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest
@RunWith(SpringRunner.class)
@Slf4j
public class RedisIdWorkerTest {
    @Autowired
    private RedisIdWorker redisIdWorker;

    @Test
    public void nextId() {
        for (int i = 0; i < 10000; i++) {
            long pay = redisIdWorker.nextId("pay");
            log.info("{}", pay);
        }
    }
}