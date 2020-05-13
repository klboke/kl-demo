package kl.demo;

import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static java.lang.System.out;

/**
 * @author: kl @kailing.pub
 * @date: 2019/9/5
 */
public class LockSupportTest {

    @Test
    public void testParkUntil() {
        out.println("启动测试");
        long start = System.currentTimeMillis();
        LockSupport.parkUntil(TimeUnit.SECONDS.toMillis(6) + start);
        long end = System.currentTimeMillis() - start;
        out.println(TimeUnit.MILLISECONDS.toSeconds(end) + "秒后结束测试测试");
    }

    @Test
    public void testPark2() {

        Thread currentThread = Thread.currentThread();
        new Thread(() -> {
            out.println("启动测试");
            LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(6));
            LockSupport.unpark(currentThread);

        }).start();
        LockSupport.park();
        out.println("unpack了");
    }

    @Test
    public void testPark() {
        Thread thread = new Thread(() -> {
            out.println("启动测试");
            LockSupport.park();
            out.println("unpack了");
        });
        thread.start();
        LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(6));
        LockSupport.unpark(thread);
    }

    @Test
    public void testNextSecondarySeed() {
        IntStream.range(0, 10).forEach(i -> {
            out.println(LockSupport.nextSecondarySeed());
        });
    }
}
