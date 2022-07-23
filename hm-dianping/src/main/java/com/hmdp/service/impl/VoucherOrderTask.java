package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.config.RedisConstantConfig;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherOrderTask {
    //public static final BlockingQueue<VoucherOrder> ORDER_TASK = new ArrayBlockingQueue<>(1024 * 1024);
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    private final IVoucherOrderService voucherOrderService;
    private final RedissonClient redissonClient;
    private static final String QUEUE_NAME = "stream.orders";
    private final StringRedisTemplate redisTemplate;
    private final RedisConstantConfig redisConstantConfig;

    @PostConstruct
    public void init() {
        SECKILL_ORDER_EXECUTOR.submit((Runnable) () -> {
            while (true) {
                try {
                    // 获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 count 1 block 2000 streams stream.order >
                    List<MapRecord<String, Object, Object>> list = redisTemplate.opsForStream().read(
                            Consumer.from("g1", redisConstantConfig.getStreamClient()),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2L)),
                            StreamOffset.create(QUEUE_NAME, ReadOffset.lastConsumed())
                    );
                    if (list == null || list.isEmpty()) {
                        continue;
                    }
                    MapRecord<String, Object, Object> orderMap = list.get(0);
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(orderMap.getValue(), new VoucherOrder(), true);
                    handleVoucherOrder(voucherOrder);
                    redisTemplate.opsForStream().acknowledge(QUEUE_NAME, "g1", orderMap.getId());
                } catch (Exception e) {
                    log.error("订单处理异常", e);
                    handlePendingList();
                }
            }
        });
    }

    private void handlePendingList() {
        while (true) {
            try {
                // 获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 count 1 block 2000 streams stream.order 0
                List<MapRecord<String, Object, Object>> list = redisTemplate.opsForStream().read(
                        Consumer.from("g1", redisConstantConfig.getStreamClient()),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(QUEUE_NAME, ReadOffset.from("0"))
                );
                if (list == null || list.isEmpty()) {
                    break;
                }
                MapRecord<String, Object, Object> orderMap = list.get(0);
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(orderMap.getValue(), new VoucherOrder(), true);
                handleVoucherOrder(voucherOrder);
                redisTemplate.opsForStream().acknowledge(QUEUE_NAME, "g1", orderMap.getId());
            } catch (Exception e) {
                log.error("pending-list订单处理异常", e);
            }
        }
    }


    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        if (!lock.tryLock()) {
            log.error("不允许重复下单");
            return;
        }
        // 获取代理对象
        try {
            voucherOrderService.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    //@PostConstruct
    //public void init() {
    //    SECKILL_ORDER_EXECUTOR.submit((Runnable) () -> {
    //        while (true) {
    //            try {
    //                VoucherOrder voucherOrder = ORDER_TASK.take();
    //                log.info("{}", voucherOrder);
    //                handleVoucherOrder(voucherOrder);
    //            } catch (Exception e) {
    //                log.error("订单处理异常 {} ", e);
    //            }
    //        }
    //    });
    //}
}
