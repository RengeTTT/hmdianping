package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import io.lettuce.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    /**
     * 采用缓存null值的方法解决缓存穿透的问题
     */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {
        if (id == null) {
            return Result.fail("无效id");
        }
        // Shop shop = queryWithPassThrough(id);
        Shop shop = cacheClient.queryWithMutex(RedisConstants.CACHE_SHOP_KEY, Shop.class, id, this :: getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //Shop shop = cacheClient.queryWithLogicalExpire();
        return Result.ok(shop);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result updateShop(Shop shop) {
        if (shop == null) {
            return Result.fail("商铺为空");
        }
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("id为空");
        }
        this.updateById(shop);
        String cacheKey = RedisConstants.CACHE_SHOP_KEY + id;
        stringRedisTemplate.delete(cacheKey);
        return Result.ok();
    }




}
