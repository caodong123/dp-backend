package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static cn.hutool.core.lang.UUID.randomUUID;

public class SimpleRedisLock implements ILock{

    private String name;
    private StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PREFIX = "lock:";
    private static final String UUID= randomUUID().toString(true);

    private static final DefaultRedisScript redisScript;

    static {
        redisScript = new DefaultRedisScript();
        redisScript.setResultType(Long.class);
        redisScript.setLocation(new ClassPathResource("unlock.lua"));
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Boolean tryLock(Long expireSeconds) {
        //获取线程id
        long id = Thread.currentThread().getId();
        String value = UUID+"-"+id;

        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, value, expireSeconds, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(success);
    }

    @Override
    public void unLock() {
        //使用lua脚本
        stringRedisTemplate.execute(
                redisScript,
                Collections.singletonList(KEY_PREFIX + name),
                UUID+"-"+Thread.currentThread().getId()
        );
    }

    /*@Override
    public void unLock() {
        //释放锁
        //获取value
        String value = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        if((UUID+"-"+Thread.currentThread().getId()).equals(value)){
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }*/
}
