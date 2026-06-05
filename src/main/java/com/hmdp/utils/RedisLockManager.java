package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import com.hmdp.service.IRedisLock;

import lombok.Data;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Data
public class RedisLockManager implements IRedisLock {
    // Redis中锁的前缀和ID前缀
    private static final String KEY_PREFIX = "lock:";
    private static final String THREAD_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> Lua_Script;
    static {
        Lua_Script = new DefaultRedisScript<>();
        Lua_Script.setLocation(new ClassPathResource("lua/lock.lua"));
        Lua_Script.setResultType(Long.class);
    }
    // 业务名称
    private String name;
    private StringRedisTemplate stringRedisTemplate;
    public RedisLockManager(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }
    @Override
    public boolean tryLock(long timeSeconds){
        long threadId = Thread.currentThread().threadId();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, THREAD_PREFIX + threadId, timeSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    public void unlock(){
        stringRedisTemplate.execute(
                Lua_Script,
                List.of(KEY_PREFIX + name),
                THREAD_PREFIX + Thread.currentThread().threadId()
                );
    }
}
