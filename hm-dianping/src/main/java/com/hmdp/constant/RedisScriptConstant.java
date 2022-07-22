package com.hmdp.constant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static com.hmdp.constant.RedisConstants.SCRIPT_PATH;

@Slf4j
public class RedisScriptConstant {
    public static final String SECKILL_SCRIPT = "seckill.lua";
    public static final String UNLOCK_SCRIPT = "unlock.lua";
    private static final Map<String, DefaultRedisScript<Long>> REDIS_SCRIPT_MAP = new HashMap<>();

    static {
        try {
            Class<?> clazz = RedisScriptConstant.class;
            Field[] fields = clazz.getFields();
            StringBuilder sb = new StringBuilder();
            sb.append("load lua, ");
            for (Field field : fields) {
                String scriptName = field.getName();
                scriptName = (String) clazz.getField(scriptName).get(clazz);

                DefaultRedisScript<Long> script = (DefaultRedisScript<Long>) RedisScript.of(
                        new ClassPathResource(SCRIPT_PATH + scriptName), Long.class);
                REDIS_SCRIPT_MAP.put(scriptName, script);
                sb.append(String.format("[%s] ==> %s...%n", scriptName, script.getScriptAsString().substring(0, 64)));
            }
            log.info("{}", sb);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private RedisScriptConstant() {
    }

    public static DefaultRedisScript<Long> get(String scriptName) {
        return REDIS_SCRIPT_MAP.get(scriptName);
    }
}