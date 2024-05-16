package com.hmdp.utils;


public interface ILock {

    // 尝试获取锁
    public Boolean tryLock(Long expireSeconds);

    // 释放锁
    public void unLock();
}
