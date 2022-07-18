package com.hmdp.service.impl;

import com.hmdp.utils.RedisHashUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.constant.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
@RunWith(SpringRunner.class)
public class ShopServiceImplTest {
    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private RedisHashUtil redisHashUtil;

    @Test
    public void saveShop2Redis() {
        redisHashUtil.save2RedisWithLogicalExpire(CACHE_SHOP_KEY, 32L, 2L, TimeUnit.HOURS, true, id -> shopService.getById(id));
    }
}