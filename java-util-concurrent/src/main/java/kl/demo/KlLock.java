package kl.demo;
/**
 * 简版的锁实现
 * @author: kl @kailing.pub
 * @date: 2019/9/2
 */
public class KlLock {
    class Sync extends AbstractQueuedSynchronizer {

        public boolean tryAcquire(int ignore) {
            return compareAndSetState(0, 1);
        }

        public boolean tryRelease(int ignore) {
            setState(0);
            return true;
        }
    }

    private final Sync sync = new Sync();

    public void lock() {
        sync.acquire(0);
    }

    public void unlock() {
        sync.release(0);
    }
}