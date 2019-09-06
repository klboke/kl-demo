# 前言
J.U.C是java包java.util.concurrent的简写，中文简称并发包，是jdk1.5新增用来编写并发相关的基础api。java从事者一定不陌生，同时，流量时代的今天，并发包也成为了高级开发面试时必问的一块内容，本篇内容主要聊聊J.U.C背后的哪些事儿，然后结合LockSupport和Unsafe探秘下并发包更底层的哪些代码，有可能是系列博文的一个开篇

# 关于JCP和JSR
JCP是Java Community Process的简写，是一种开发和修订Java技术规范的流程，同时，我们提到JCP一般是指维护管理这套流程的组织，这个组织主要由Java开发者以及被授权的非盈利组织组成，他们掌管着Java的发展方向。JCP是Sun公司1998年12月8日提出的，旨在通过java社区的力量推进Java的发展。截止目前，已经从1.0版本发展到了最新的2019年7月21日颁布的2.11版本。JCP流程中有四个主要阶段，分别是启动、发布草案、最终版本、维护，一个Java的新功能从启动阶段提出，到顺利走完整套流程后，就会出现在下个版本的JDK中了。

JSR是Java Specification Requests的简写，是服务JCP启动阶段提出草案的规范，任何人注册成为JCP的会员后，都可以向JCP提交JSR。比如,你觉得JDK中String的操作方法没有guava中的实用，你提个JSR增强String中的方法，只要能够通过JCP的审核，就可以在下个版本的JDK中看到了。我们熟知的提案有，Java缓存api的JSR-107、Bean属性校验JSR-303等，当然还有本篇要讲的Java并发包JSR-166.

- JCP官网：https://jcp.org

#Doug Lea和他的JSR-166
Doug Lea，中文名为道格·利。是美国的一个大学教师，大神级的人物，J.U.C就是出自他之手。JDK1.5之前，我们控制程序并发访问同步代码只能使用synchronized，那个时候synchronized的性能还没优化好，性能并不好，控制线程也只能使用Object的wait和notify方法。这个时候Doug Lea给JCP提交了JSR-166的提案，在提交JSR-166之前，Doug Lea已经使用了类似J.U.C包功能的代码已经三年多了，这些代码就是J.U.C的原型，下面简单看下这些具有历史味道的代码，同时也能引发我们的一些思考，如果JDK中没有，那么就自己造呀！
- Doug Lea大爷在JDK1.5之前使用的并发包地址：http://gee.cs.oswego.edu/dl/classes/EDU/oswego/cs/dl/util/concurrent/intro.html
## Lock接口的原型
```
public class Mutex implements Sync  {
  /** The lock status **/
  protected boolean inuse_ = false;
  @Override
  public void acquire() throws InterruptedException {
    if (Thread.interrupted()) {
      throw new InterruptedException();
    }
    synchronized(this) {
      try {
        //如果inuse_为true就wait住线程
        while (inuse_) {
          wait();
        }
        inuse_ = true;
      }
      catch (InterruptedException ex) {
        notify();
        throw ex;
      }
    }
  }
  /**
   * 释放锁，通知线程继续执行
   */
  @Override
  public synchronized void release()  {
    inuse_ = false;
    notify(); 
  }
  @Override
  public boolean attempt(long msecs) throws InterruptedException {
    if (Thread.interrupted()) {
      throw new InterruptedException();
    }
    synchronized(this) {
      if (!inuse_) {
        inuse_ = true;
        return true;
      }
      else if (msecs <= 0) {
        return false;
      } else {
        long waitTime = msecs;
        long start = System.currentTimeMillis();
        try {
          for (;;) {
            wait(waitTime);
            if (!inuse_) {
              inuse_ = true;
              return true;
            }
            else {
              waitTime = msecs - (System.currentTimeMillis() - start);
              if (waitTime <= 0) 
                return false;
            }
          }
        }
        catch (InterruptedException ex) {
          notify();
          throw ex;
        }
      }
    }  
  }
}
```
## CountDownLatch的原型
```
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
```
## AbstractQueuedSynchronizer抽象类的原型
了解J.U.C的都知道，在这个包里面AbstractQueuedSynchronizer是精髓所在，这就是我们在聊并发包时俗称的AQS，这个框架设计为同步状态的原子性管理、线程的阻塞和解除阻塞以及排队提供一种通用的机制。并发包下ReentrantLock、CountDownLatch等都是基于AQS来实现的，在看下上面的原型实现都是实现的Sync接口，是不是似曾相识，下面的Sync就是AbstractQueuedSynchronizer的原型了

- AQS设计论文：http://gee.cs.oswego.edu/dl/papers/aqs.pdf
- 中文译文：https://www.cnblogs.com/dennyzhangdd/p/7218510.html
```
public interface Sync {

  public void acquire() throws InterruptedException;

  public boolean attempt(long msecs) throws InterruptedException;

  public void release();

  /**  One second, in milliseconds; convenient as a time-out value **/
  public static final long ONE_SECOND = 1000;
  /**  One minute, in milliseconds; convenient as a time-out value **/
  public static final long ONE_MINUTE = 60 * ONE_SECOND;
  /**  One hour, in milliseconds; convenient as a time-out value **/
  public static final long ONE_HOUR = 60 * ONE_MINUTE;
  /**  One day, in milliseconds; convenient as a time-out value **/
  public static final long ONE_DAY = 24 * ONE_HOUR;
  /**  One week, in milliseconds; convenient as a time-out value **/
  public static final long ONE_WEEK = 7 * ONE_DAY;
  /**  One year in milliseconds; convenient as a time-out value  **/
  public static final long ONE_YEAR = (long)(365.2425 * ONE_DAY);
  /**  One century in milliseconds; convenient as a time-out value **/
  public static final long ONE_CENTURY = 100 * ONE_YEAR;
}
```
## JSR-166的详细内容

### 1、请描述拟议的规范：
这个JSR的目标类似于JDK1.2 Collections包的目标：
- 1.标准化一个简单，可扩展的框架，该框架将常用的实用程序组织成一个足够小的包，以便用户可以轻松学习并由开发人员维护。
- 2.提供一些高质量的实现。

该包将包含接口和类，这些接口和类在各种编程样式和应用程序中都很有用。这些类包括：
- 原子变量。
- 专用锁，屏障，信号量和条件变量。
- 为多线程使用而设计的队列和相关集合。
- 线程池和自定义执行框架。

我们还将研究核心语言和库中的相关支持。
请注意，这些与J2EE中使用的事务并发控制框架完全不同。（但是，对于那些创建此类框架的人来说，它们会很有用。）

### 2、什么是目标Java平台？
J2SE

### 3、拟议规范将解决Java社区的哪些需求？
底层线程原语（例如synchronized块，Object.wait和Object.notify）不足以用于许多编程任务。因此，应用程序员经常被迫实现自己的更高级别的并发工具。这导致了巨大的重复工作。此外，众所周知，这些设施难以正确，甚至更难以优化。应用程序员编写的并发工具通常不正确或效率低下。提供一组标准的并发实用程序将简化编写各种多线程应用程序的任务，并通常可以提高使用它们的应用程序的质量。

### 4、为什么现有规范不满足这种需求？
目前，开发人员只能使用Java语言本身提供的并发控制结构。对于某些应用程序来说，这些级别太低，而对其他应用程序则不完整

### 5、请简要介绍基础技术或技术：
绝大多数软件包将在低级Java构造之上实现。但是，有一些关于原子性和监视器的关键JVM /语言增强功能是获得高效和正确语义所必需的。

### 6、API规范是否有建议的包名？（即javapi.something，org.something等）
java.util.concurrent中

### 7、建议的规范是否与您知道的特定操作系统，CPU或I/O设备有任何依赖关系？
只是间接地，因为在不同平台上运行的JVM可能能够以不同方式优化某些构造。

### 8、当前的安全模型是否存在无法解决的安全问题？
没有

### 9、是否存在国际化或本地化问题？
没有

### 10、是否有任何现有规范可能因此工作而过时，弃用或需要修订？
没有

### 11、请描述制定本规范的预期时间表。
目标是将此规范包含在J2SE 1.5（Tiger）的JSR中。

### 12、请描述致力于制定本规范的专家组的预期工作模式。
电子邮件，电话会议和不常见的会议。我们还将使用或创建一个开放的邮件列表，供专家组以外的其他感兴趣的人讨论。

#解密LockSupport和Unsafe
前面说到AQS是并发包下的精髓所在，那么LockSupport和Unsafe就是整个JSR-166并发包的所有功能实现的灵魂，纵观整个并发包下的代码，无处不见LockSupport和Unsafe的身影。LockSupport提供了两个关键方法，park和unpark，用来操作线程的阻塞和放行，功能可以类比Object的wait和notify，但是比这两个api更灵活。下面是博主简化了的实现（JDK中不是这样的）
```
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
```
有了park和unpark后，我们也可以这样来实现Lock的功能，代码如下，有了ConcurrentLinkedQueue加持后，就可以在基本的锁的功能上，实现公平锁的语义了。
```
/**
 * 简版先进先出的公平锁实现
 * @author: kl @kailing.pub
 * @date: 2019/9/2
 */
public class FIFOMutex {
    private final AtomicBoolean locked = new AtomicBoolean(false);
    private final Queue<Thread> waiters = new ConcurrentLinkedQueue<>();
    public void lock() {
        boolean wasInterrupted = false;
        Thread current = Thread.currentThread();
        waiters.add(current);
        // 在队列中不是第一个或无法获取锁时阻塞
        while (waiters.peek() != current || !locked.compareAndSet(false, true)) {
            LockSupport.park(this);
            if (Thread.interrupted()) {
                wasInterrupted = true;
            }
        }
        waiters.remove();
        if (wasInterrupted) {
            current.interrupt();
        }
    }
    public void unlock() {
        locked.set(false);
        LockSupport.unpark(waiters.peek());
    }
}
```
## 神秘的Unsafe，JSR166增加了哪些内容
心细的你可能发现了LockSupport最终还是基于Unsafe的park和unpark来实现的，Unsafe在JDK1.5之前就存在的，那JSR166后增加了哪些内容呢？先来看下Unsafe是什么来头。JDK源码中是这样描述的：一组用于执行低层、不安全操作的方法。尽管该类和所有方法都是公共的，但是该类的使用受到限制，因为只有受信任的代码才能获得该类的实例。如其名，不安全的，所以在JDK1.8后直接不提供源码了，JDK中其他的代码都可以在IDE中直接看到.java的文件，而Unsafe只有.class编译后的代码。因为Unsafe是真的有黑魔法，可以直接操作系统级的资源，比如系统内存、线程等。JDK不直接对外暴露Unsafe的api，如果直接在自己的应用程序中像JDK中那么获取Unsafe的实例，如：
```
private static final sun.misc.Unsafe UNSAFE = sun.misc.Unsafe.getUnsafe();
```
会直接抛异常SecurityException("Unsafe")，正确的获取方式如下：
```
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
```
其实，深入到Unsafe后，会发现深入不下去了，Unsafe中的方法是都是native标记的本地方法，没有实现，如：
```
    public native void unpark(Object var1);

    public native void park(boolean var1, long var2);
```
如果是在Windows下，最终调用的就是使用C++开发的最终编译成.dll的包，所以只要看到C++相关的代码就知道怎么回事了

- JDK源码下载地址：http://www.java.net/download/openjdk/jdk8/promoted/b132/openjdk-8-src-b132-03_mar_2014.zip
- JDK源码仓库：http://hg.openjdk.java.net/

首先定位到Unsafe.cpp,文件位置在：openjdk\hotspot\src\share\vm\prims\Unsafe.cpp,会发现和JSR166相关的都有注释，如：// These are the methods prior to the JSR 166 changes in 1.6.0。根据这些信息，得知JSR166在Unsafe中新增了五个方法，分别是compareAndSwapObject、compareAndSwapInt、compareAndSwapLong、park、unpark，这就是并发包中CAS原子操作和线程控制的核心所在了，并发包中的大部分功能都是基于他们来实现的。最后我们看下park和unpark的具体实现，在学校学的C语言丢的差不好多了，但是下面的代码还语义还是很清晰的
```
// JSR166
// -------------------------------------------------------

/*
 * The Windows implementation of Park is very straightforward: Basic
 * operations on Win32 Events turn out to have the right semantics to
 * use them directly. We opportunistically resuse the event inherited
 * from Monitor.
 *
void Parker::park(bool isAbsolute, jlong time) {
  guarantee (_ParkEvent != NULL, "invariant") ;
  // First, demultiplex/decode time arguments
  if (time < 0) { // don't wait
    return;
  }
  else if (time == 0 && !isAbsolute) {
    time = INFINITE;
  }
  else if  (isAbsolute) {
    time -= os::javaTimeMillis(); // convert to relative time
    if (time <= 0) // already elapsed
      return;
  }
  else { // relative
    time /= 1000000; // Must coarsen from nanos to millis
    if (time == 0)   // Wait for the minimal time unit if zero
      time = 1;
  }

  JavaThread* thread = (JavaThread*)(Thread::current());
  assert(thread->is_Java_thread(), "Must be JavaThread");
  JavaThread *jt = (JavaThread *)thread;

  // Don't wait if interrupted or already triggered
  if (Thread::is_interrupted(thread, false) ||
    WaitForSingleObject(_ParkEvent, 0) == WAIT_OBJECT_0) {
    ResetEvent(_ParkEvent);
    return;
  }
  else {
    ThreadBlockInVM tbivm(jt);
    OSThreadWaitState osts(thread->osthread(), false /* not Object.wait() */);
    jt->set_suspend_equivalent();

    WaitForSingleObject(_ParkEvent,  time);
    ResetEvent(_ParkEvent);

    // If externally suspended while waiting, re-suspend
    if (jt->handle_special_suspend_equivalent_condition()) {
      jt->java_suspend_self();
    }
  }
}

void Parker::unpark() {
  guarantee (_ParkEvent != NULL, "invariant") ;
  SetEvent(_ParkEvent);
}
```
# 结语
我们一直受益于J.U.C的代码，网上也不乏大量的解读分析J.U.C源码的文章，但是很少有讲J.U.C背后的关于J.U.C诞生的那些事儿，在深入了解并发包的代码同时，发现了很多值的分享的事情，整个J.U.C的技术脉络也无比的清晰，故记录下来了。从此，博主在技术界，又多了一位崇拜的偶像Doug Lea，希望，在读完本文后也能成为你的偶像。
