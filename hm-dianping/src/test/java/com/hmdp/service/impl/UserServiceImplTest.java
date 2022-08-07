package com.hmdp.service.impl;

import cn.hutool.core.lang.UUID;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest
@RunWith(SpringRunner.class)
public class UserServiceImplTest {
    @Autowired
    private IUserService userService;

    @Test
    public void addUser() {
        Long initPhone = 13688669889L;
        for (int i = 0; i < 4000; i++) {
            User user = new User();
            user.setPhone(String.valueOf(initPhone + i));
            user.setNickName("user_" + UUID.randomUUID().toString(true).substring(0, 10));
            userService.save(user);
        }

    }

    @Test
    public void addUser2() {
        User user = new User();
        user.setPhone(String.valueOf(2222222222L));
        user.setNickName("user_" + UUID.randomUUID().toString(true).substring(0, 10));
        userService.save(user);
    }

}