package com.hmdp.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
public class VoUtil {
    private VoUtil() {
    }

    public static <T> T fill(Supplier<T> supplier, Consumer<T> consumer) {
        T t = supplier.get();
        consumer.accept(t);
        return t;
    }
}
