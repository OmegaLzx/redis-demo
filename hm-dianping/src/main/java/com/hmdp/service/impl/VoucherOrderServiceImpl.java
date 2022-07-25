package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constant.RedisScriptConstant;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

import static com.hmdp.constant.RedisScriptConstant.SECKILL_SCRIPT;


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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    private final ISeckillVoucherService seckillVoucherService;
    private final RedisIdWorker redisIdWorker;
    private final StringRedisTemplate redisTemplate;


    @Override
    public Result seckillVoucher(Long voucherId) {
        long orderId = redisIdWorker.nextId("order");
        Long userId = UserHolder.getUserId();
        // 执行lua秒杀脚本
        Long result = redisTemplate.execute(
                RedisScriptConstant.get(SECKILL_SCRIPT),
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)),
                String.valueOf(orderId)
        );

        assert result != null;
        int r = result.intValue();
        if (r == 1) {
            return Result.fail("库存不足");
        } else if (r == 2) {
            return Result.fail("不能重复下单");
        } else if (r == 3) {
            return Result.fail("活动尚未开始");
        }
        return Result.ok(orderId);
    }


    @Transactional
    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        int count = query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        if (count > 0) {
            // 用户已经购买该优惠券
            log.error("用户已经购买过一次！");
            return;
        }

        // 5. 扣减库存
        if (!seckillVoucherService.update()
                .setSql("stock = stock - 1").gt("stock", 0)
                .eq("voucher_id", voucherId).update()) {
            log.error("库存不足");
            return;
        }
        // 6. 创建订单
        save(voucherOrder);
    }

    //@Override
    //public Result seckillVoucher(Long voucherId) {
    //    // 1. 查询优惠券
    //    SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
    //    if (voucher == null) {
    //        return Result.fail("优惠券不存在！");
    //    }
    //    // 2. 判断秒杀是否开始
    //    if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
    //        return Result.fail("秒杀尚未开始！");
    //    }
    //    // 3. 判断秒杀是否已经结束
    //    if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
    //        return Result.fail("秒杀已经结束！");
    //    }
    //    Long userId = UserHolder.getUser().getId();
    //     SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, redisTemplate);
    //    //RLock lock = redissonClient.getLock("order:" + userId);
    //    if (!lock.tryLock(1200)) {
    //        return Result.fail("不允许重复下单");
    //    }
    //    // 获取代理对象
    //    try {
    //        IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
    //        return proxy.createVoucherOrder(voucherId);
    //    } finally {
    //        lock.unlock();
    //    }
    //}
}
