package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    private final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // 向缓存中存储数据
    public void set(String key, Object value, Long time, TimeUnit timeUnit){
        String jsonStr = JSONUtil.toJsonStr(value);
        stringRedisTemplate.opsForValue().set(key,jsonStr,time,timeUnit);
    }

    // 向缓存中存储数据，并设置逻辑过期时间
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        String jsonStr = JSONUtil.toJsonStr(redisData);
        // 写入缓存
        stringRedisTemplate.opsForValue().set(key,jsonStr);
    }

    //封装缓存击穿的查询代码
    public  <R,ID>R queryWithMutex(String keyPrefix, ID id, Class<R> type,Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        // 从redis缓存中查询
        String objJSON = stringRedisTemplate.opsForValue().get(key);
        // 如果存在，直接返回
        if (StringUtils.isNotBlank(objJSON)) {
            R obj = JSONUtil.toBean(objJSON, type);
            return obj;
        }
        if(objJSON != null){
            return null;
        }
        //缓存中不存在，查询数据库
        //获取锁
        String lockKey = LOCK_SHOP_KEY + id;
        R r = null;
        try {
            if (!tryLock(lockKey)) {
                // 获取锁失败，休眠后重试
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, timeUnit);
            }
            // 获取锁成功，根据id查询数据库
            r = dbFallback.apply(id);
            // 将数据保存到redis缓存 并设置保存时间
            if (r == null) {
                // 不存在，返回错误
                // 把空值缓存到redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, timeUnit);
                return null;
            }
            set(key, r, time, timeUnit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            unLock(lockKey);
        }
        return r;
    }


    // 从缓存中获取数据，并基于逻辑过期实现缓存击穿
    public  <R,ID>R queryWithLogicExpire(String keyPrefix,ID id,Class<R> type,Function<ID,R> dbFallback,Long time,TimeUnit timeUnit) {
        String key = keyPrefix + id;
        // 1.从redis中查询商铺缓存
        String objJson = stringRedisTemplate.opsForValue().get(key);
        // 2.未命中,返回错误信息
        if(StringUtils.isBlank(objJson)){
            return null;
        }
        // 3.命中，判断是否过期
        RedisData redisData = JSONUtil.toBean(objJson, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        R obj = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        // 3.1 判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //未过期 直接返回
            return obj;
        }
        // 3.2 过期，需要缓存重建
        // 4.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if(isLock){
            // 4.2 获得锁成功，开启新线程缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    // 重建缓存
                    R r = dbFallback.apply(id);
                    setWithLogicalExpire(key,r,time,timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(lockKey);
                }
            });
        }
        //获取失败返回过期数据
        return obj;
    }


    //尝试获得锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    // 从缓存中获取数据,并实现缓存穿透
    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit timeUnit){
        String key = keyPrefix + id;
        // 1.从redis中查询商铺缓存
        String objJson = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if(StringUtils.isNotBlank(objJson)){
            // 2.1存在，返回数据
            return JSONUtil.toBean(objJson, type);
        }
        //objJson == 为""
        if(objJson != null){
            return null;
        }
        // 2.2不存在，查询数据库
        // 3.重建缓存
        R obj = dbFallback.apply(id);
        if(obj == null){
            // 3.1数据库不存在，返回错误 缓存中添加一个空值，防止缓存穿透
            set(key,"",CACHE_NULL_TTL,timeUnit);
            return null;
        }
        // 3.2数据库存在，返回数据
        set(key,obj,time,timeUnit);
        return obj;
    }
}
