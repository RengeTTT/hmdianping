package com.hmdp.service;

public interface IRedisLock {
    public boolean tryLock(long timeSeconds);
    public void unlock();
}
