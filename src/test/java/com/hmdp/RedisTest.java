package com.hmdp;


import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;


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
