package com.hmdp.utils;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.*;

@SpringBootTest
public class RedisIdWorkerTest {
    @Autowired
    private RedisIdWorker redisIdWorker;

    //线程常量池
    private final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            CACHE_REBUILD_EXECUTOR.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }

    @Test
    public void nextId() {
        System.out.println(redisIdWorker.nextId("test"));
    }
}
