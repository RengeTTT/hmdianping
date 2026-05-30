package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    /**
     * 采用缓存null值的方法解决缓存穿透的问题
     */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        if (id == null) {
            return Result.fail("无效id");
        }
        // 获取Redis商铺信息缓存
        String cacheKey = RedisConstants.CACHE_SHOP_KEY + id;
        String cache = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isNotBlank(cache)) {
            // 如果存在缓存，那么直接返回
            Shop cacheShop = JSONUtil.toBean(cache, Shop.class);
            return Result.ok(cacheShop);
        }
        if (cache != null) {
            return Result.fail("店铺不存在");
        }
        // 不存在缓存,查询数据库
        Shop shop = this.getById(id);
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(cacheKey, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("数据库不存在该信息");
        }
        String key = RedisConstants.CACHE_SHOP_KEY + shop.getId();
        Long TTL = new Random().nextLong(RedisConstants.CACHE_SHOP_TTL - 5 + 1) + 5;
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), TTL, TimeUnit.MINUTES);
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
