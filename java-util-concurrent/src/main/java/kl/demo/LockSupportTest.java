package kl.demo;

import java.util.concurrent.TimeUnit;

/**
 * @author: kl @kailing.pub
 * @date: 2019/8/7
 */
public class LockSupportTest {

    public static void main(String[] args) throws Exception {

        Thread thread = new Thread(() -> {
            System.out.println("========线程一启动");
            LockSupport.park(Thread.currentThread());
            System.out.println("========线程一结束");


        });
        thread.start();

        TimeUnit.SECONDS.sleep(6);

        LockSupport.unpark(thread);

        System.out.println("释放锁定");
        System.in.read();
    }
}
