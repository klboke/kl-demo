package kl.demo;

import java.util.concurrent.locks.LockSupport;

/**
 * 简版的闭锁实现
 * @author: kl @kailing.pub
 * @date: 2019/9/2
 */
public class KlCountDownLatch {
    private Thread thread;
    private int count;

    public KlCountDownLatch(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count < 0");
        }
        this.count = count;
    }

    public synchronized void countDown() {
        if (--this.count == 0) {
            LockSupport.unpark(thread);
        }
    }

    public void await() {
        this.thread = Thread.currentThread();
        LockSupport.park(this);
    }
}
