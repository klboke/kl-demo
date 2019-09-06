package kl.demo;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static java.lang.System.*;

/**
 * @author: kl @kailing.pub
 * @date: 2019/8/6
 */
public class Test {

    static ExecutorService executor = Executors.newCachedThreadPool();
    private ReentrantLock lock = new ReentrantLock(true);
    private LimitLatch limitLatch = new LimitLatch(1);
    private CyclicBarrier cyclicBarrier = new CyclicBarrier(3, () -> out.println("通过屏障！"));
    private KlCyclicBarrier klcyclicBarrier = new KlCyclicBarrier(3, () -> out.println("通过屏障！"));

    @org.junit.Test
    public void testKlCyclicBarrier() throws Exception{

        executor.submit(()->{
            out.println("当前线程：" + Thread.currentThread());
            klcyclicBarrier.await();
            out.println("当前线程结束：" + Thread.currentThread());
        });

        executor.submit(()->{
            out.println("当前线程：" + Thread.currentThread());
            klcyclicBarrier.await();
            out.println("当前线程结束：" + Thread.currentThread());
        });
        TimeUnit.SECONDS.sleep(10);
        klcyclicBarrier.await();
        out.println("结束了");
    }

    @org.junit.Test
    public void testCyclicBarrier() throws Exception {

        executor.submit(()->{
            out.println("当前线程：" + Thread.currentThread());
            try {
                cyclicBarrier.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                e.printStackTrace();
            }
            out.println("当前线程结束：" + Thread.currentThread());
        });

        executor.submit(()->{
            out.println("当前线程：" + Thread.currentThread());
            try {
                cyclicBarrier.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                e.printStackTrace();
            }
            out.println("当前线程结束：" + Thread.currentThread());
        });
        TimeUnit.SECONDS.sleep(10);
        cyclicBarrier.await();
        out.println("结束了");
    }

    @org.junit.Test
    public void testLimitLatch() throws Exception {
        IntStream.range(0, 10).forEach(i -> executor.submit(() -> {
            try {
                limitLatch.countUpOrAwait();
                err.println(i + "顺利通过：" + limitLatch.getQueuedThreads().size());
                TimeUnit.SECONDS.sleep(i);
                limitLatch.countDown();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }));

        in.read();
    }

    @org.junit.Test
    public void testReentrantLock() throws Exception {
        new Thread(() -> pring(), "线程一").start();
        Thread thread = new Thread(() -> pring(), "线程二");
        thread.start();
        thread.interrupt();
        in.read();
    }

    private void pring() {
        lock.lock();
        try {
            out.println(Thread.currentThread().getName() + "进入");
            TimeUnit.SECONDS.sleep(10);
            out.println(Thread.currentThread().getName() + "完成");
        } catch (Exception e) {
            out.println("可能被中断了");
        } finally {
            lock.unlock();
        }
    }

    @org.junit.Test
    public void semaphoreTest() throws Exception {
        Semaphore semaphore = new Semaphore(2);

        IntStream.range(0, 10).forEach(i -> executor.submit(() -> {
            try {
                semaphore.acquire();
                err.println(i + "顺利通过：" + semaphore.getQueueLength());
                TimeUnit.SECONDS.sleep(2);
                semaphore.release();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }));

        in.read();
    }

    @org.junit.Test
    public void testKlLatch() {
        KlCountDownLatch latch = new KlCountDownLatch(2);
        executor.submit(() -> {
            try {
                TimeUnit.SECONDS.sleep(2);
                latch.countDown();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        executor.submit(() -> {
            try {
                TimeUnit.SECONDS.sleep(2);
                latch.countDown();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        latch.await();
        out.println("任务完成！");
    }

    @org.junit.Test
    public void testCountDownLatch() throws Exception {
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(2);
        executor.submit(() -> {
            try {
                TimeUnit.SECONDS.sleep(2);
                latch.countDown();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        executor.submit(() -> {
            try {
                TimeUnit.SECONDS.sleep(2);
                latch.countDown();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        latch.await();
        out.println("任务完成！");
    }

}

