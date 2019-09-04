package kl.demo;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * @author: kl @kailing.pub
 * @date: 2019/8/7
 */
public class LockSupportTest {

    public static void main(String[] args) throws Exception {

        Thread thread = new Thread(() -> {
            System.out.println("========线程一启动");
            LockSupport.park(Thread.currentThread());
            if(!Thread.currentThread().isInterrupted())
            System.out.println("========线程一结束");

        });
        thread.start();
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (Exception e) {
        }
        thread.interrupt();
        System.out.println("中断完成");
        try {
            TimeUnit.SECONDS.sleep(6);
        } catch (Exception e) {
        }
        LockSupport.unpark(thread);

        System.out.println("释放锁定");
        System.in.read();
    }
}
