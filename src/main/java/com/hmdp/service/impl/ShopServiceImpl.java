package com.hmdp.service.impl;


import cn.hutool.core.util.StrUtil;


import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;

import com.hmdp.utils.SystemConstants;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;

import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.*;
import java.util.List;

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

    /*
    *  查询附近商铺功能
    * */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if (typeId < 0 | current < 0) {
            return Result.ok(Collections.emptyList());
        }
        // 计算分页参数
        int from = (current - 1) * SystemConstants.MAX_PAGE_SIZE;
        int end = current * SystemConstants.MAX_PAGE_SIZE;
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        // 查询店铺对应的地理位置
        GeoResults<RedisGeoCommands.GeoLocation<String>> search = stringRedisTemplate.opsForGeo()
                .search(key, GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));
        if (search == null) {
            return Result.ok(Collections.emptyList());
        }
        // 收集店铺id和distance
        List<Long> ids = new ArrayList<>();
        Map<String, Distance> distanceMap = new HashMap<>();
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = search.getContent();
        if (content.size() <= from) {
            return Result.ok(Collections.emptyList());
        }
        content.stream().skip(from).forEach(geoResult -> {
            String idStr = geoResult.getContent().getName();
            ids.add(Long.parseLong(idStr));
            distanceMap.put(idStr, geoResult.getDistance());
        });
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("order by field(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);
    }


}
