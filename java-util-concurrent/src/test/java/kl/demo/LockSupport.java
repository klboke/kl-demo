
package kl.demo;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;

/**
   用于创建锁和其他同步类的基本线程阻塞基础类，提供基础的线程控制功能。
 */
public class LockSupport {
    private LockSupport() {}
    /**
      解除park的阻塞，如果还没阻塞，它对{@code park}的下一次调用将保证不会阻塞
     */
    public static void unpark(Thread thread) {
        if (thread != null) {
            UNSAFE.unpark(thread);
        }
    }
    /**
      阻塞当前线程，除非先调用了unpark()方法。
     */
    public static void park() {
        UNSAFE.park(false, 0L);
    }

    //Hotspot implementation via intrinsics API
    private static final Unsafe UNSAFE;
    static {
        try {
            try {
                final PrivilegedExceptionAction<Unsafe> action = new PrivilegedExceptionAction<Unsafe>() {
                    @Override
                    public Unsafe run() throws Exception {
                        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                        theUnsafe.setAccessible(true);
                        return (Unsafe) theUnsafe.get(null);
                    }
                };
                UNSAFE = AccessController.doPrivileged(action);
            } catch (Exception e) {
                throw new RuntimeException("Unable to load unsafe", e);
            }
        } catch (Exception ex) { throw new Error(ex); }
    }
}
