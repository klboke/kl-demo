package kl.demo;

public class CountDown implements Sync {
    protected final int initialCount_;
    protected int count_;
    /**
     * Create a new CountDown with given count value
     **/
    public CountDown(int count) {
        count_ = initialCount_ = count;
    }
    @Override
    public void acquire() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        synchronized (this) {
            while (count_ > 0) {
                wait();
            }
        }
    }

    @Override
    public boolean attempt(long msecs) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        synchronized (this) {
            if (count_ <= 0) {
                return true;
            } else if (msecs <= 0) {
                return false;
            } else {
                long waitTime = msecs;
                long start = System.currentTimeMillis();
                for (; ; ) {
                    wait(waitTime);
                    if (count_ <= 0) {
                        return true;
                    } else {
                        waitTime = msecs - (System.currentTimeMillis() - start);
                        if (waitTime <= 0) {
                            return false;
                        }
                    }
                }
            }
        }
    }

    /**
     * Decrement the count.
     * After the initialCount'th release, all current and future
     * acquires will pass
     **/
    @Override
    public synchronized void release() {
        if (--count_ == 0) {
            notifyAll();
        }
    }

    /**
     * Return the initial count value
     **/
    public int initialCount() {
        return initialCount_;
    }

    public synchronized int currentCount() {
        return count_;
    }
}
