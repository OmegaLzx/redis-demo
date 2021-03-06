package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.config.RedisConstantConfig;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisHashUtil;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedisHashUtil redisHashUtil;
    @Resource
    private RedisConstantConfig redisConstant;

    @Override
    public Result sendCode(String phone) {
        // 验证手机号码格式
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号码格式不正确");
        }
        // 发送手机验证码
        String code = RandomUtil.randomNumbers(6);
        log.info("发送手机验证码：{}", code);
        // 将验证码保存在redis中
        stringRedisTemplate.opsForValue().set(
                redisConstant.getLoginCodePrefix() + phone, code, redisConstant.getLoginCodeTtl(), TimeUnit.MINUTES);
        return Result.ok();
    }

    @Override
    public Result validateCode(LoginFormDTO loginFormDTO) {
        //        if (!validCode(loginFormDTO)) {
        //            return Result.fail("验证码无效");
        //        }

        // 3. 验证码有效
        // 3.1 检查用户是否存在
        User user = this.getOne(new QueryWrapper<User>().eq("phone", loginFormDTO.getPhone()));
        // 3.2 用户不存在，创建用户
        if (user == null) {
            user = new User();
            user.setPhone(loginFormDTO.getPhone());
            this.save(user);
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 3.3 用户存在，查询用户信息，并存入redis
        String token = UUID.randomUUID().toString(true);
        String tokenKey = redisConstant.getUserInfoPrefix() + token;

        redisHashUtil.save2Redis(tokenKey, "token", token);
        redisHashUtil.save2Redis(tokenKey, userDTO, true);
        // 4. 返回token
        return Result.ok(token);
    }

    private boolean validCode(LoginFormDTO loginFormDTO) {
        // 1. 检查验证码有效性
        String code = stringRedisTemplate.opsForValue().get(
                redisConstant.getLoginCodePrefix() + loginFormDTO.getPhone());
        // 2. 验证码无效，返回错误信息
        return code != null && code.equals(loginFormDTO.getCode());
    }

    @Override
    public Result me() {
        UserDTO user = UserHolder.getUser();
        if (user != null) {
            return Result.ok(user);
        }
        return Result.fail("用户未登录");
    }

    @Override
    public Result logout() {
        stringRedisTemplate.delete(redisConstant.getUserInfoPrefix() + UserHolder.getToken());
        return Result.ok();
    }

}
