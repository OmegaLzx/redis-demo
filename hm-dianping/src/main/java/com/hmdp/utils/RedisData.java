package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisData<T> {
    private T data;
    private LocalDateTime expireTime;

    public RedisData(T data, LocalDateTime expireTime) {
        this.data = data;
        this.expireTime = expireTime;
    }
}
