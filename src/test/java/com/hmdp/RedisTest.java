package com.hmdp;


import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

@SpringBootTest
public class RedisTest {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedissonClient redissonClient;

    @Test
    public void test1() {
        RLock lock = null;
        try {
            lock = redissonClient.getLock("lock");
            boolean success = lock.tryLock(20, 100, TimeUnit.SECONDS);
            if(success){
                test2();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    @Test
    public void test2() {
        RLock lock = null;
        try {
            lock = redissonClient.getLock("lock");
            boolean success = lock.tryLock(20, 100, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }
}
