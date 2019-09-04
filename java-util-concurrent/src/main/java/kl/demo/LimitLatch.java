package kl.demo;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;


/**
 共享锁存器，它允许锁存器被获取有限的次数，
 之后所有后续的获取锁存器的请求将被放置在FIFO队列中，直到其中一个共享被返回。
 */
public class LimitLatch {


    private class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 1L;

        public Sync() {
        }

        @Override
        protected int tryAcquireShared(int ignored) {
            long newCount = count.incrementAndGet();
            if (!released && newCount > limit) {
                // Limit exceeded
                count.decrementAndGet();
                return -1;
            } else {
                return 1;
            }
        }

        @Override
        protected boolean tryReleaseShared(int arg) {
            count.decrementAndGet();
            return true;
        }
    }

    private final Sync sync;
    private final AtomicLong count;
    private volatile long limit;
    private volatile boolean released = false;

    /**
     * 实例化具有初始限制的LimitLatch对象。
     * @param limit - 此锁存器的最大并发获取次数
     */
    public LimitLatch(long limit) {
        this.limit = limit;
        this.count = new AtomicLong(0);
        this.sync = new Sync();
    }

    /**
     * 返回锁存器的当前计数
     * @return 锁存器的当前计数
     */
    public long getCount() {
        return count.get();
    }

    /**
     * 获取当前最大限制
     * @return the limit
     */
    public long getLimit() {
        return limit;
    }


    /**
     设定一个新的限制。如果限制降低，可能会有一段时间，锁存器的份额会超过限制。
     *
     * @param limit The new limit
     */
    public void setLimit(long limit) {
        this.limit = limit;
    }


    /**
     如果共享锁存器可用，则获取一个共享锁存器;如果当前没有可用的共享锁存器，则等待一个共享锁存器。
     * @throws InterruptedException 如果当前线程中断则抛出InterruptedException异常
     */
    public void countUpOrAwait() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }

    /**
     *释放共享锁存器，使其可供其他线程使用。
     * @return 计数器值
     */
    public long countDown() {
        sync.releaseShared(0);
        long result = getCount();
        return result;
    }

    /**
     * 释放所有等待线程并忽略最大并发限制limit
     * until {@link #reset()} is called.
     * @return <code>true</code> if release was done
     */
    public boolean releaseAll() {
        released = true;
        return sync.releaseShared(0);
    }

    /**
     * 重置锁存器并将共享获取计数器初始化为零。
     * @see #releaseAll()
     */
    public void reset() {
        this.count.set(0);
        released = false;
    }

    /**
     如果线程正在等待，返回true
     */
    public boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    /**
     * 提供对等待获取此限制的线程列表的访问
     * shared latch.
     * @return a collection of threads
     */
    public Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }
}
