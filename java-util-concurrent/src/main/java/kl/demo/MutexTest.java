package kl.demo;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

/**
 * @author: kl @kailing.pub
 * @date: 2019/9/6
 */
public class MutexTest {
    @Test
    public void testLock()throws Exception{
        Mutex mutex = new Mutex();

        new Thread(()->{
            try {
                mutex.acquire();
                System.out.println("线程一：" + Thread.currentThread());
                TimeUnit.SECONDS.sleep(6);
            }catch (Exception ex){
                ex.printStackTrace();
            }finally {
                mutex.release();
            }
        }).start();
        new Thread(()->{
            try {
                mutex.acquire();
                System.out.println("线程二：" + Thread.currentThread());
            }catch (Exception ex){
                ex.printStackTrace();
            }finally {
                mutex.release();
            }
        }).start();
        mutex.acquire();
        System.out.println("mian线程");
        TimeUnit.SECONDS.sleep(3);
        mutex.release();

        System.in.read();
    }
}
