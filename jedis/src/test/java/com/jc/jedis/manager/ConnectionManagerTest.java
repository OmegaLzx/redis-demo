package com.jc.jedis.manager;

import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import redis.clients.jedis.Jedis;

@Slf4j
public class ConnectionManagerTest {
    private Jedis jedis = null;

    @Test
    @Before
    public void connect() {
        log.info("Connecting to Redis");
        jedis = new Jedis("node1", 6001);
    }

    @After
    public void close() {
        jedis.close();
    }

    @Test
    public void get() {
        String key = jedis.get("key");
        log.info("key: {}", key);
    }

    @Test
    public void set() {
        jedis.set("foo", "bar");
        log.info("key: {}", jedis.get("foo"));
    }


}
