package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constant.RedisScriptConstant;
import com.hmdp.constant.SystemConstants;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.constant.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.constant.RedisScriptConstant.LIKE_SCRIPT;

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
    private final IFollowService followService;

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
        Double score = redisTemplate.opsForZSet().score(key, currentUserId.toString());
        blog.setIsLike(score != null);
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
        // 执行lua点赞脚本
        Long result = redisTemplate.execute(
                RedisScriptConstant.get(LIKE_SCRIPT),
                Collections.emptyList(),
                userId.toString(),
                id.toString(),
                String.valueOf(System.currentTimeMillis())
        );

        assert result != null;
        int r = result.intValue();
        if (r == 1) {
            update().setSql("liked = liked - 1").eq("id", id).update();
        } else {
            update().setSql("liked = liked + 1").eq("id", id).update();
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        Set<String> topFive = redisTemplate.opsForZSet().range(key, 0, 5);
        if (topFive == null || topFive.isEmpty()) {
            return Result.ok(Collections.emptyList());

        }
        List<Long> ids = topFive.stream().map(Long::valueOf).collect(Collectors.toList());

        String idStr = CharSequenceUtil.join(",", ids);
        List<UserDTO> userDtoList = userService.query()
                .in("id", ids)
                .last("ORDER BY FIELD(id, " + idStr + ")")
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDtoList);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        Long userId = UserHolder.getUserId();
        blog.setUserId(userId);
        // 保存探店博文
        if (!save(blog)) {
            return Result.fail("新增笔记失败");
        }
        // 查询笔记作者的所有粉丝
        List<Follow> followUserList = followService
                .list(new QueryWrapper<Follow>()
                              .eq("follow_user_id", userId));
        for (Follow follow : followUserList) {
            Long followUserId = follow.getUserId();
            String key = "feed:" + followUserId;
            redisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        Long userId = UserHolder.getUserId();
        String key = "feed:" + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 3);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }

        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            assert tuple.getValue() != null;
            assert tuple.getScore() != null;

            ids.add(Long.valueOf(tuple.getValue()));
            long time = tuple.getScore().longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }

        String idStr = CharSequenceUtil.join(",", ids);
        List<Blog> blogList = query().in("id", ids)
                .last("ORDER BY FIELD(id, " + idStr + ")").list();
        blogList.forEach(this::queryBlogUser);

        ScrollResult scrollResult = new ScrollResult(blogList, minTime, os);
        return Result.ok(scrollResult);
    }


    //@Override
    //@Transactional
    //public Result likeBlog(Long id) {
    //    Long userId = UserHolder.getUserId();
    //    String key = BLOG_LIKED_KEY + id;
    //    String lockKey = LOCK_LIKED_KEY + userId;
    //    RLock lock = redissonClient.getLock(lockKey);
    //
    //    if (!lock.tryLock()) {
    //        return Result.fail("点赞的速度过快，请稍后再试");
    //    }
    //
    //    try {
    //        if (Boolean.FALSE.equals(redisTemplate.opsForSet().isMember(key, userId.toString()))) {
    //            if (update().setSql("liked = liked + 1").eq("id", id).update()) {
    //                redisTemplate.opsForSet().add(key, userId.toString());
    //            }
    //        } else {
    //            if (update().setSql("liked = liked - 1").eq("id", id).update()) {
    //                redisTemplate.opsForSet().remove(key, userId.toString());
    //            }
    //        }
    //    } finally {
    //        lock.unlock();
    //    }
    //
    //    return Result.ok();
    //}
}
