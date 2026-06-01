package com.hmdp.utils;



import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;


@Component
public class RedisIWorker {
    private static final long BEGIN_TIME_STAMP = 1780351869L;
    private static final int MOVE_BITS = 32;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public Long nextId(String keyPrefix) {
        // 生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long endTimeStamp = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = endTimeStamp - BEGIN_TIME_STAMP;
        // 生成序列号，使用redis进行自增
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("incr:" + keyPrefix +":" + date);
        // 时间戳向左移动32位并拼接count
        return timestamp << MOVE_BITS | count;
    }

}
