package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.Test;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private ShopServiceImpl shopService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void loadShopData() {
       // 查询店铺信息
        List<Shop> shops = shopService.list();
        // 按照商店类型分组，根据typeId进行分组
        Map<Long, List<Shop>> typeShopMap = shops.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 分批写入redis

        for (Map.Entry<Long, List<Shop>> entry : typeShopMap.entrySet()) {
            Long typeId = entry.getKey();
            String key = RedisConstants.SHOP_GEO_KEY + typeId;
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>();
            for (Shop shop : entry.getValue()) {
                locations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(), new Point(shop.getX(), shop.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }
    @Test
    void testHyperLogLog() {
        for (int i = 0; i < 100_000; i++) {
            String user = "user:" + i;
            stringRedisTemplate.opsForHyperLogLog().add("hy1", user);
        }
        Long count = stringRedisTemplate.opsForHyperLogLog().size("hy1");
        System.out.println(count);
    }
}
