package com.hmdp.utils;

import com.hmdp.dto.UserDTO;

public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user) {
        tl.set(user);
    }

    public static UserDTO getUser() {
        return tl.get();
    }

    public static Long getUserId() {
        return getUser().getId();
    }

    public static String getToken() {
        return tl.get().getToken();
    }

    public static void removeUser() {
        tl.remove();
    }
}
