package com.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class CacheClient {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private static final ExecutorService executorService = Executors.newFixedThreadPool(10);



    public void set(String key, Object value, Long expire, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), expire, timeUnit);
    }
    public void setWithLogicalExpire(String key, Object value, Long expire, TimeUnit timeUnit) {
        RedisData redisData = RedisData.builder()
                .data(value)
                .expireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(expire)))
                .build();
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <T, ID> T queryWithPassThrough(String keyPrefix
            , Class<T> clazz, ID id, Function<ID, T> dbFallBack, Long expire, TimeUnit timeUnit) {
        if (id == null) {
            return null;
        }
        // 获取Redis商铺信息缓存
        String cacheKey = RedisConstants.CACHE_SHOP_KEY + id;
        String cache = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isNotBlank(cache)) {
            // 如果存在缓存，那么直接返回
            T cacheShop = JSONUtil.toBean(cache, clazz);
            return cacheShop;
        }
        if (cache != null) {
            return null;
        }
        // 不存在缓存,查询数据库
        T shop =dbFallBack.apply(id);
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(cacheKey, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        this.set(cacheKey, shop, expire, timeUnit);
        return shop;
    }

    // 利用互斥锁解决缓存穿透问题
    public <T, ID> T queryWithMutex(String keyPrefix
            , Class<T> clazz, ID id, Function<ID, T> dbFallBack, Long expire, TimeUnit timeUnit) {
        if (id == null) {
            return null;
        }
        // 获取Redis商铺信息缓存
        String cacheKey = RedisConstants.CACHE_SHOP_KEY + id;
        String cache = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isNotBlank(cache)) {
            // 如果存在缓存，那么直接返回
            T cacheShop = JSONUtil.toBean(cache, clazz);
            return cacheShop;
        }
        if (cache != null) {
            return null;
        }
        // 在查询数据库之前首先尝试获取互斥锁
        boolean isLock = tryLock(id);
        if (isLock) {
            try {
                cache = stringRedisTemplate.opsForValue().get(cacheKey);
                if (StrUtil.isNotBlank(cache)) {
                    // 如果存在缓存，那么直接返回
                    T cacheShop = JSONUtil.toBean(cache, clazz);
                    return cacheShop;
                }
                Thread.sleep(50);
                return queryWithMutex(cacheKey, clazz, id, dbFallBack, expire, timeUnit);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                unlock(id);
            }
        }
        // 不存在缓存,查询数据库
        T shop = dbFallBack.apply(id);
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(cacheKey, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        this.set(cacheKey, shop, expire, timeUnit);
        return shop;
    }

    // 利用逻辑过期解决缓存穿透问题
    public <T, ID> T queryWithLogicalExpire(String keyPrefix
            , Class<T> clazz, ID id, Function<ID, T> dbFallBack, Long expire, TimeUnit timeUnit) {
        if (id == null) {
            return null;
        }
        // 获取Redis商铺信息缓存
        String cacheKey = RedisConstants.CACHE_SHOP_KEY + id;
        String cache = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isBlank(cache)) {
            // 如果不存在缓存，那么直接返回null
            return null;
        }
        // 命中缓存，JSON反序列化为RedisData
        RedisData redisData = JSONUtil.toBean(cache, RedisData.class);
        T shop = JSONUtil.toBean((JSONObject) redisData.getData(), clazz);
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            return shop;
        }
        // 逻辑过期，需要进行缓存重建
        // 在查询数据库之前首先尝试获取互斥锁
        boolean isLock = tryLock(id);
        if (isLock) {
            // double check防止在多个线程同时争夺互斥锁时，一个线程完成缓存重建之后，余下的线程继续进行缓存重建
            if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
                return shop;
            }
            executorService.submit(() -> {
                try {
                    log.info("异步线程重建缓存");
                    this.setWithLogicalExpire(cacheKey, shop, expire, timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(id);
                }
            });

        }
        // 返回旧商铺信息
        return shop;
    }

    private <T> boolean tryLock(T id) {
        log.info("商店id:{}获取互斥锁", id);
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }

    private <T> void unlock(T id) {
        log.info("商店id:{}释放互斥锁", id);
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        stringRedisTemplate.delete(lockKey);
    }


}
