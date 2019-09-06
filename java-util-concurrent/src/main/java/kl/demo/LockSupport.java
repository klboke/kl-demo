
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

    private static void setBlocker(Thread t, Object arg) {
        UNSAFE.putObject(t, parkBlockerOffset, arg);
    }

    /**
      解除park的阻塞，如果还没阻塞，它对{@code park}的下一次调用将保证不会阻塞
     */
    public static void unpark(Thread thread) {
        if (thread != null) {
            UNSAFE.unpark(thread);
        }
    }

    /**
        阻塞当前线程并记录当前对象，除非先调用了unpark()方法。
     */
    public static void park(Object blocker) {
        Thread t = Thread.currentThread();
        setBlocker(t, blocker);
        UNSAFE.park(false, 0L);
        setBlocker(t, null);
    }

    /**
     阻塞当前线程nanos纳秒时间，并记录当前blocker对象，如果先调用了unpark则不阻塞
     */
    public static void parkNanos(Object blocker, long nanos) {
        if (nanos > 0) {
            Thread t = Thread.currentThread();
            setBlocker(t, blocker);
            UNSAFE.park(false, nanos);
            setBlocker(t, null);
        }
    }

    /**
     为线程调度目的禁用当前线程，直到指定的截止日期，除非先调用了unpark()方法。
     */
    public static void parkUntil(Object blocker, long deadline) {
        Thread t = Thread.currentThread();
        setBlocker(t, blocker);
        UNSAFE.park(true, deadline);
        setBlocker(t, null);
    }

    /**
     返回提供给最近一次调用尚未解阻塞的park方法的blocker对象，如果没有阻塞，则返回null。
     返回的值只是一个瞬时快照——线程可能已经在另一个blocker对象上解除了阻塞或阻塞。
     */
    public static Object getBlocker(Thread t) {
        if (t == null) {
            throw new NullPointerException();
        }
        return UNSAFE.getObjectVolatile(t, parkBlockerOffset);
    }

    /**
      阻塞当前线程，除非先调用了unpark()方法。
     */
    public static void park() {
        UNSAFE.park(false, 0L);
    }

    /**
     阻塞当前线程nanos纳秒时间，如果先调用了unpark则不阻塞
     */
    public static void parkNanos(long nanos) {
        if (nanos > 0) {
            UNSAFE.park(false, nanos);
        }
    }

    /**
     阻塞当前线程直到指定的截止时间，
     */
    public static void parkUntil(long deadline) {
        UNSAFE.park(true, deadline);
    }

    /**
     返回伪随机初始化或更新的辅助种子。由于包访问限制，从ThreadLocalRandom复制。
     */
    static final int nextSecondarySeed() {
        int r;
        Thread t = Thread.currentThread();
        if ((r = UNSAFE.getInt(t, SECONDARY)) != 0) {
            // xorshift
            r ^= r << 13;
            r ^= r >>> 17;
            r ^= r << 5;
        }
        else if ((r = java.util.concurrent.ThreadLocalRandom.current().nextInt()) == 0) {
            // avoid zero
            r = 1;
        }
        UNSAFE.putInt(t, SECONDARY, r);
        return r;
    }

    //Hotspot implementation via intrinsics API
    private static final sun.misc.Unsafe UNSAFE;
    private static final long parkBlockerOffset;
    private static final long SECONDARY;
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
            Class<?> tk = Thread.class;
            parkBlockerOffset = UNSAFE.objectFieldOffset(tk.getDeclaredField("parkBlocker"));
            SECONDARY = UNSAFE.objectFieldOffset(tk.getDeclaredField("threadLocalRandomSecondarySeed"));
        } catch (Exception ex) { throw new Error(ex); }
    }

}
