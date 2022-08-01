package com.hmdp.service.impl;

import com.hmdp.entity.Shop;
import com.hmdp.utils.RedisHashUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.constant.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
@RunWith(SpringRunner.class)
@Slf4j
public class ShopServiceImplTest {
    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private RedisHashUtil redisHashUtil;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    public void saveShop2Redis() {
        redisHashUtil.save2RedisWithLogicalExpire(CACHE_SHOP_KEY, 3L, 2L, TimeUnit.HOURS,
                                                  true, id -> shopService.getById(id)
        );
    }

    @Test
    public void loadShopData() {
        List<Shop> list = shopService.list();
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            String key = "shop:geo:" + typeId;
            List<RedisGeoCommands.GeoLocation<String>> geoLocations =
                    entry.getValue().stream().map(shop -> new RedisGeoCommands.GeoLocation<>(
                                    shop.getId().toString(),
                                    new Point(shop.getX(), shop.getY())
                            ))
                            .collect(Collectors.toList());
            //for (Shop shop : shops) {
            //    redisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
            //}
            log.info("{} {}", key, geoLocations);
            //redisTemplate.opsForGeo().add(key, geoLocations);
        }
    }
}