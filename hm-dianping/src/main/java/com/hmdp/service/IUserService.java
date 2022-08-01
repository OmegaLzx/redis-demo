package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {
    Result sendCode(String phone);

    Result validateCode(LoginFormDTO loginFormDTO);

    Result me();

    Result logout();

    Result sign();

    Result signCount(String date);

    Result uvCount(String end);

    Result pvCount(String end);
}
