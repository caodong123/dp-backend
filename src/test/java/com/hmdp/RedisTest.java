package com.hmdp;


import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;


public class RedisTest {

    @Resource
    private ShopServiceImpl shopService;

    @Test
    public void test() {
        LocalDateTime now = LocalDateTime.now();


        long second = now.toEpochSecond(ZoneOffset.UTC);
        System.out.println(second);
        String format = now.format(DateTimeFormatter.ofPattern("yyyy--MM-dd"));
        System.out.println(format);



    }
}
