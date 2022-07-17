package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.map.MapUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.hmdp.constant.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.constant.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@RequiredArgsConstructor
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    private final StringRedisTemplate redisTemplate;

    @Override
    public Result queryById(Long id) {
        String cacheKey = CACHE_SHOP_KEY + id;
        // 1. 从redis中查询缓存
        Map<Object, Object> shopMap = redisTemplate.opsForHash().entries(cacheKey);
        // 2. 判断是否存在
        if (!shopMap.isEmpty()) {
            if (shopMap.get("nil") != null) {
                return Result.fail("店铺信息不存在");
            }
            Shop shop = BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
            return Result.ok(shop);
        }
        // 3 不存在，则查询数据库
        Shop shop = getById(id);
        // 3.1 数据库中不存在，返回错误信息
        if (shop == null) {
            redisTemplate.opsForHash().putAll(cacheKey, MapUtil.of("nil", "nil"));
            redisTemplate.expire(cacheKey, CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return Result.fail("店铺信息不存在");
        }
        // 3.2 存在写回redis，返回数据

        redisTemplate.opsForHash().putAll(cacheKey, BeanUtil.beanToMap(shop, new HashMap<>(), CopyOptions.create()
                .setIgnoreNullValue(true)
                .setIgnoreError(false)
                .setFieldValueEditor((field, value) -> Optional.ofNullable(value).map(Object::toString).orElse(null))));
        redisTemplate.expire(cacheKey, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return Result.ok(shop);
    }

    @Override
    @Transactional
    //todo 完整学习spring事务传播机制
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺不存在");
        }
        // 1. 更新数据库
        if (!updateById(shop)) {
            return Result.fail("店铺不存在");
        }
        // 2. 删除缓存
        String cacheKey = CACHE_SHOP_KEY + id;
        redisTemplate.delete(cacheKey);
        return Result.ok();
    }

}
