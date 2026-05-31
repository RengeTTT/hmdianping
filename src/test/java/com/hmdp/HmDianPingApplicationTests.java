package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private ShopServiceImpl shopService;
    @Test
    void testLogicalExpire() {
        shopService.saveShop2RedisData(1L, LocalDateTime.now().plusSeconds(10));
    }
}
