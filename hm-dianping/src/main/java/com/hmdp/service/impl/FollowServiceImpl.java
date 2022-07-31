package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.constant.RedisConstants.FOLLOW_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final IUserService userService;

    @Override
    public Result follow(Long followUserId) {
        Long userId = UserHolder.getUserId();
        String key = FOLLOW_KEY + userId;
        String lockKey = "lock:follow:" + userId;
        RLock lock = redissonClient.getLock(lockKey);
        if (!lock.tryLock()) {
            return Result.fail("操作速度过快，请稍后再试");
        }
        String msg = null;
        try {
            if (!Boolean.TRUE.equals(isFollow(followUserId).getData())) {
                Follow follow = new Follow();
                follow.setUserId(userId);
                follow.setFollowUserId(followUserId);
                if (save(follow)) {
                    redisTemplate.opsForSet().add(key, followUserId.toString());
                }
                msg = "关注成功";
            } else {
                if (remove(new QueryWrapper<Follow>().eq("user_id", userId)
                                   .eq("follow_user_id", followUserId))) {
                    redisTemplate.opsForSet().remove(key, followUserId.toString());
                }
                msg = "取关成功";
            }
        } catch (Exception e) {
            log.error("", e);
        } finally {
            lock.unlock();
        }
        return Result.ok(msg);
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUserId();
        String key = FOLLOW_KEY + userId;
        Boolean isMember = redisTemplate.opsForSet().isMember(key, followUserId.toString());
        return Result.ok(Boolean.TRUE.equals(isMember));
    }

    @Override
    public Result followCommons(String id) {
        Long userId = UserHolder.getUserId();
        String key1 = FOLLOW_KEY + userId;
        String key2 = FOLLOW_KEY + id;
        Set<String> intersect = redisTemplate.opsForSet().intersect(key1, key2);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> userIds = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(userIds).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
