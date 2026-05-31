package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    private final StringRedisTemplate stringRedisTemplate;

    public ShopTypeServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result queryTypeList() {
        // 查询商铺类型缓存， 使用String类型进行缓存
        String cacheKey = RedisConstants.CACHE_SHOP_TYPE_KEY;
        String cacheType = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isNotBlank(cacheType)) {
            List<ShopType> shopTypeList = JSONUtil.toList(cacheType, ShopType.class);
            return Result.ok(shopTypeList);
        }
        // 如果不存在查询数据库
        List<ShopType> shopTypeList = this.lambdaQuery().orderByAsc(ShopType :: getSort).list();
        if (shopTypeList == null || shopTypeList.isEmpty()) {
            return Result.fail("不存在商店类型");
        }
        stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(shopTypeList));
        return Result.ok(shopTypeList);
    }
}
