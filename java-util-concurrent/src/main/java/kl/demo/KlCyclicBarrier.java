package kl.demo;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * @author: kl @kailing.pub
 * @date: 2019/9/4
 */
public class KlCyclicBarrier {

    private AtomicInteger count;
    private int parties;
    private AtomicBoolean generation = new AtomicBoolean(false);
    private ConcurrentHashMap<String, Thread> threads;
    private Runnable barrierPoint;

    public KlCyclicBarrier(int parties) {
        this.parties = parties;
        this.count = new AtomicInteger(parties);
        this.threads = new ConcurrentHashMap<>(parties);
    }

    public KlCyclicBarrier(int parties, Runnable barrierPoint) {
        this.count = new AtomicInteger(parties);
        this.threads = new ConcurrentHashMap<>(parties);
        this.barrierPoint = barrierPoint;
        this.parties = parties;
    }

    private KlLock lock = new KlLock();

    public void await() {
        lock.lock();
        try {
            if (count.decrementAndGet() == 0) {
                threads.forEach((k, v) -> LockSupport.unpark(v));
                threads.clear();
                generation.compareAndSet(false,true);
                count.set(parties);
                if (barrierPoint != null) {
                    new Thread(barrierPoint).start();
                }
            }
        } finally {
            lock.unlock();
        }
        if (!generation.compareAndSet(true,false)){
            Thread thread = Thread.currentThread();
            threads.put(thread.getName(), thread);
            LockSupport.park(thread);
        }

    }
}
