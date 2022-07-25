package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constant.SystemConstants;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.hmdp.constant.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.constant.RedisConstants.LOCK_LIKED_KEY;

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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    private final IUserService userService;
    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(this::queryBlogUser);
        return Result.ok(records);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());

        Long currentUserId = UserHolder.getUserId();
        String key = BLOG_LIKED_KEY + blog.getId();
        Boolean isMember = redisTemplate.opsForSet().isMember(key, currentUserId.toString());
        blog.setIsLike(Boolean.TRUE.equals(isMember));
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在！");
        }
        queryBlogUser(blog);
        return Result.ok(blog);
    }

    @Override
    @Transactional
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUserId();
        String key = BLOG_LIKED_KEY + id;
        String lockKey = LOCK_LIKED_KEY + userId;
        RLock lock = redissonClient.getLock(lockKey);

        if (!lock.tryLock()) {
            return Result.fail("点赞的速度过快，请稍后再试");
        }

        try {
            if (Boolean.FALSE.equals(redisTemplate.opsForSet().isMember(key, userId.toString()))) {
                if (update().setSql("liked = liked + 1").eq("id", id).update()) {
                    redisTemplate.opsForSet().add(key, userId.toString());
                }
            } else {
                if (update().setSql("liked = liked - 1").eq("id", id).update()) {
                    redisTemplate.opsForSet().remove(key, userId.toString());
                }
            }
        } finally {
            lock.unlock();
        }

        return Result.ok();
    }
}
