
package kl.demo;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.locks.AbstractOwnableSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.LockSupport;

import sun.misc.Unsafe;

/**
 * @author Doug Lea
 * @since 1.5
 */
public abstract class AbstractQueuedSynchronizer extends AbstractOwnableSynchronizer implements java.io.Serializable {
    private static final long serialVersionUID = 7373984972572414691L;

    protected AbstractQueuedSynchronizer() {
    }

    /**
     * Node是每个线程的封装,是一个链表结构
     */
    static final class Node {
        // 标记为共享模式，一般用于控制多个线程的流程，如，CountDownLatch的实现
        static final Node SHARED = new Node();
        //标记为独占模式，一般应用于锁的实现
        static final Node EXCLUSIVE = null;

        //waitStatus的状态值，标识已经被取消了
        static final int CANCELLED = 1;
        //waitStatus的状态值，标识释放当前节点时，下个节点需要被唤醒
        static final int SIGNAL = -1;
        //waitStatus的状态值，标识当前线程在condition下阻塞
        static final int CONDITION = -2;
        //waitStatus的状态值，标识共享模式下的可运行的线程
        static final int PROPAGATE = -3;

        //线程等待状态
        volatile int waitStatus;

        //当前节点的前一个节点，只有在当前节点拿到资源后，才会置空
        volatile Node prev;
        //当前节点的后一个节点
        volatile Node next;

        //此节点承载的线程
        volatile Thread thread;

        //下一个等待节点，一般作用于条件队列
        Node nextWaiter;

        //返回是否是共享模式
        final boolean isShared() {
            return nextWaiter == SHARED;
        }

        //返回前置节点
        final Node predecessor() throws NullPointerException {
            Node p = prev;
            if (p == null) {
                throw new NullPointerException();
            } else {
                return p;
            }
        }

        Node() {    // Used to establish initial head or SHARED marker
        }

        Node(Thread thread, Node mode) {     // Used by addWaiter
            this.nextWaiter = mode;
            this.thread = thread;
        }

        Node(Thread thread, int waitStatus) { // Used by Condition
            this.waitStatus = waitStatus;
            this.thread = thread;
        }
    }

    /**
     * 等待队列的头，延迟初始化。除了初始化之外，它只通过setHead方法进行修改。
     * 注意:如果head存在，则保证它的等待状态不会被取消。
     */
    private transient volatile Node head;

    //等待队列的尾部，延迟初始化。仅通过方法enq修改以添加新的等待节点。
    private transient volatile Node tail;

    //同步状态，采用volatile修饰，保证多个线程间读取的值一致
    private volatile int state;

    protected final int getState() {
        return state;
    }

    protected final void setState(int newState) {
        state = newState;
    }

    //如果当前值等于期望值，则原子性的将当前值设置为新值
    protected final boolean compareAndSetState(int expect, int update) {
        return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
    }

    // Queuing utilities
    //自旋的纳秒数，死循环获取资源，超过这个时间后就阻塞等待
    static final long spinForTimeoutThreshold = 1000L;

    /**
     * 通过自旋的方式将节点插入队尾，并返回之前的尾节点
     *
     * @param node
     * @return
     */
    private Node enq(final Node node) {
        for (; ; ) {
            Node t = tail;
            //如果当前尾节点为null，则初始化头节点，并将头节点赋值给尾节点
            if (t == null) {
                if (compareAndSetHead(new Node())) {
                    tail = head;
                }
            } else {
                node.prev = t; //如果尾节点不为空，就将尾节点设置为当前节点的前辈节点
                /**
                 * 通过cas的方式重新设置尾节点为当前节点，成功后将当前节点设置为原尾节点的后继节点，
                 * 这里会引发一个问题，在遍历这个链表的时候，如果从前往后遍历，很有可能尾节点设置成功后，
                 * 还未执行t.next = node的情况下，遍历不到新加的尾节点，但是这个时候新的尾节点是设置成功的，
                 * 按道理应该遍历到才是。这也解释了为什么AQS中的节点遍历操作都是从尾节点往前遍历的问题。
                 */
                if (compareAndSetTail(t, node)) {
                    //将当前节点设置为原来尾节点的下一个节点
                    t.next = node;
                    return t;
                }
            }
        }
    }

    //为指定[独占、共享]模式的线程，添加到排队节点
    private Node addWaiter(Node mode) {
        Node node = new Node(Thread.currentThread(), mode);
        // 尝试快速路径的enq，如果成功则直接返回当前节点。整个逻辑和enq()方法类似
        Node pred = tail;
        if (pred != null) {
            //如果尾节点不为空，则设置尾节点为当前节点的前辈节点
            node.prev = pred;
            if (compareAndSetTail(pred, node)) {
                pred.next = node;
                return node;
            }
        }
        enq(node);
        return node;
    }

    //设置头节点，并将前节点置空方便GC
    private void setHead(Node node) {
        head = node;
        node.thread = null;
        node.prev = null;
    }

    /**
     * 唤醒node的后继节点
     *
     * @param node
     */
    private void unparkSuccessor(Node node) {

        int ws = node.waitStatus;
        //如果waitStatus为负值，代表有效值，既将值设置为0。
        // 一般运行到这里，ws的值一般为0
        if (ws < 0) {
            compareAndSetWaitStatus(node, ws, 0);
        }

        Node s = node.next;
        //如果node后继节点为空，或者已取消获取资源，则通过从尾节点倒叙遍历的方式，找到需要唤醒的线程节点
        if (s == null || s.waitStatus > 0) {
            s = null;
            for (Node t = tail; t != null && t != node; t = t.prev) {
                if (t.waitStatus <= 0) {
                    s = t;
                }
            }
        }
        //如果此时node后继节点还不为空，则直接唤醒
        if (s != null) {
            LockSupport.unpark(s.thread);
        }
    }

    /**
     * 共享模式下释放线程资源
     */
    private void doReleaseShared() {
        for (; ; ) {
            Node h = head;
            //如果存在后继节点
            if (h != null && h != tail) {
                int ws = h.waitStatus;
                //如果node的waitStatus= -1 ,则代表存在后继节点需要被唤醒
                if (ws == Node.SIGNAL) {
                    //原子操作将waitStatus重置为0，如果失败则自旋重来
                    if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0)) {
                        continue;
                    }
                    unparkSuccessor(h);
                    //如果node的waitStatus =0,则原子操作将status重置为-3，如果失败则自旋重来
                } else if (ws == 0 && !compareAndSetWaitStatus(h, 0, Node.PROPAGATE)) {
                    continue;
                }
            }
            //如果不存在后继节点直接跳出
            if (h == head) {
                break;
            }
        }
    }

    /**
     * 设置队列的头部，并检查后续队列是否可以继续传播释放信号
     * 如果设置了propagate > 0或propagate status，则将进行传播.
     */
    private void setHeadAndPropagate(Node node, int propagate) {
        //记录旧的头节点，以便检查
        Node h = head;
        setHead(node);
        if (propagate > 0 || h == null || h.waitStatus < 0 || (h = head) == null || h.waitStatus < 0) {
            Node s = node.next;
            //如果node没有后继节点，或者后继节点为共享模式的，则释放共享模式的资源
            if (s == null || s.isShared()) {
                doReleaseShared();
            }
        }
    }

    // Utilities for various versions of acquire

    /**
     * 取消正在获取资源的动作
     *
     * @param node the node
     */
    private void cancelAcquire(Node node) {
        // Ignore if node doesn't exist
        if (node == null)
            return;
        //将当前节点封装的线程置空
        node.thread = null;

        // 跳过前任取消，如果当前节点的上一个节点waitStatus=1的话，一直迭代到prev节点的waitStatus小于0的
        Node pred = node.prev;
        while (pred.waitStatus > 0) {
            //将当前节点的前节点设置为，不需要取消的为止
            node.prev = pred = pred.prev;
        }
        //
        Node predNext = pred.next;

        // 设置当前节点的waitStatus=1,取消状态
        node.waitStatus = Node.CANCELLED;

        //如果当前节点时尾节点，那么设置pred为尾节点，如果设置成功，则将pred的后节点置空
        if (node == tail && compareAndSetTail(node, pred)) {
            compareAndSetNext(pred, predNext, null);
        } else {
            //否则，如果当前节点的后继节点需要被唤醒，那么将当前节点的后继节点设置为pred的后继节点
            int ws;
            if (pred != head && ((ws = pred.waitStatus) == Node.SIGNAL || (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) && pred.thread != null) {
                Node next = node.next;
                //如果当前节点的next节点不为空，且waitstatus状态是未取消的，则将当前节点的后继节点设置为pred的后继节点。那么就彻底的将当前节点从链条中剔除了
                if (next != null && next.waitStatus <= 0) {
                    compareAndSetNext(pred, predNext, next);
                }
            } else {
                //否则如果，pred.thread ==null，那么可以立刻唤醒当前节点的后继节点
                unparkSuccessor(node);//唤醒后继节点
            }
            node.next = node; // help GC
        }
    }

    /**
     * 获取资源失败后，是否需要阻塞，基本上都是自旋一次后才会返回true阻塞
     *
     * @param pred 前置节点
     * @param node 当前节点
     * @return {@code true} 返回true则表示需要阻塞
     */
    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        int ws = pred.waitStatus;
        //前任节点waitStatus = -1,标识了node需要被唤醒，所以可以直接阻塞。
        // 第一次传入的时候pred.waitStatus一般都为0，自旋一次后设置为-1
        if (ws == Node.SIGNAL) {
            return true;
        }
        if (ws > 0) {
            /*
              前任被取消了。检测是否还有被取消的，直到找到未取消的
             */
            do {
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0);
            pred.next = node;
        } else {
            /*
               设置前任节点waitStatus = -1：如果ws状态为0，则设置为-1，不阻塞。返回false重新自旋获取资源
             */
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }
        return false;
    }

    /**
     * 标记当前线程已被中断了
     */
    static void selfInterrupt() {
        Thread.currentThread().interrupt();
    }

    /**
     * 阻塞当前线程，并检测返回中断状态，同时清除了中断状态数据
     *
     * @return {@code true} if interrupted
     */
    private final boolean parkAndCheckInterrupt() {
        LockSupport.park(this);//当线程中断时，会唤醒park动作，和调用LockSupport.unpark()类似
        return Thread.interrupted();
    }

    /*
     获取的不同风格，在独占/共享和控制模式中有所不同。每一种都大同小异，但却令人讨厌地不同。
     由于异常机制(包括确保我们在尝试获取抛出异常时取消)和其他控件之间的交互，
     只有少量的因式分解是可能的，至少不会对性能造成太大影响
     */

    /**
     * 在队列中等待获取资源
     *
     * @param node the node
     * @param arg  the acquire argument
     * @return {@code true} if interrupted while waiting
     */
    final boolean acquireQueued(final Node node, int arg) {
        boolean failed = true;
        try {
            boolean interrupted = false;
            //自旋的方式获取资源
            for (; ; ) {
                final Node p = node.predecessor();
                //如果前任节点是head节点，并且实现者tryAcquire返回true，则代表获取到资源
                //tryAcquire的实现是关键，可重入锁的关键逻辑就是允许相同线程多次操作AQS的资源标识state，每获取一次就加1
                if (p == head && tryAcquire(arg)) {
                    setHead(node); //设置当前节点为头节点
                    p.next = null; //将前任节点从链表中剔除 help GC
                    failed = false;
                    return interrupted;
                }
                //如果没有获取到资源，就判断是否需要自旋继续获取，否则就阻塞，等待唤醒
                if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt()) {
                    interrupted = true;
                }
            }
        } finally {
            if (failed) {
                //这段逻辑基本上跑不到
                cancelAcquire(node);
            }
        }
    }

    /**
     * 以独占、可中断模式获取资源。
     * 独占模式，相同时刻只有一个线程的node可以获取到资源
     *
     * @param arg the acquire argument
     */
    private void doAcquireInterruptibly(int arg) throws InterruptedException {
        //以独占模式新增一个新的排队节点
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (; ; ) {
                //获取当前节点的前任节点
                final Node p = node.predecessor();
                //如果前任是头节点，并且实现者tryAcquire返回true，则代表获取到资源
                //tryAcquire的实现是关键，可重入锁的关键逻辑就是允许相同线程多次操作AQS的资源标识state，每获取一次就加1
                if (p == head && tryAcquire(arg)) {
                    setHead(node); //设置当前节点为头节点
                    p.next = null;//将前任节点从链表中剔除 help GC
                    failed = false;
                    return;
                }
                //如果没有获取到资源，就判断是否需要自旋继续获取，否则就阻塞，等待唤醒
                if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt()) {
                    throw new InterruptedException();
                }
            }
        } finally {
            if (failed) {
                //如果阻塞等待唤醒时，线程被中断了，则取消这个节点的等待
                cancelAcquire(node);
            }
        }
    }

    /**
     * 独占模式 带超时限制获取资源
     *
     * @param arg          获取的资源
     * @param nanosTimeout 最大等待时间 单位纳秒
     * @return {@code true} 如果拿到资源返回true
     */
    private boolean doAcquireNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (nanosTimeout <= 0L) {
            return false;
        }
        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return true;
                }
                //剩下的等待时间
                nanosTimeout = deadline - System.nanoTime();
                if (nanosTimeout <= 0L) {
                    return false;
                }
                /**
                 * doAcquireNanos 和 doAcquire不一样的地方，精髓就体现在下面这里，如果最大等待时间nanosTimeout大于自旋设置的1000纳秒
                 * 就阻塞等待nanosTimeout时间，如果小于1000纳秒，就自旋竞争下资源
                 */
                if (shouldParkAfterFailedAcquire(p, node) && nanosTimeout > spinForTimeoutThreshold) {
                    LockSupport.parkNanos(this, nanosTimeout);
                }
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
            }
        } finally {
            if (failed) {
                cancelAcquire(node);
            }
        }
    }

    /**
     * 以共享不可中断模式获取
     *
     * @param arg 获取的资源
     */
    private void doAcquireShared(int arg) {
        //以共享模式新增一个新的排队节点，在设置头节点时会用到
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            boolean interrupted = false;
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head) {
                    //继承者实现tryAcquireShared逻辑
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        //如果拿到资源，设置头节点，并且判断是否可以继续传播释放信号
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        if (interrupted) {
                            selfInterrupt();
                        }
                        failed = false;
                        return;
                    }
                }
                if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt()) {
                    interrupted = true;
                }
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * 以共享中断模式获取。和doAcquireShared 的区别就是线程阻塞被中断时，抛异常
     *
     * @param arg 获取的资源
     */
    private void doAcquireSharedInterruptibly(int arg) throws InterruptedException {
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        failed = false;
                        return;
                    }
                }
                //自旋第二次的时候会进parkAndCheckInterrupt(),直接阻塞当前线程，
                // 当线程被中断时抛出InterruptedException异常
                if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt()) {
                    throw new InterruptedException();
                }
            }
        } finally {
            if (failed) {
                cancelAcquire(node);
            }
        }
    }

    /**
     * 以共享模式 带超时限制获取资源
     *
     * @param arg          the acquire argument
     * @param nanosTimeout max wait time
     * @return {@code true} if acquired
     */
    private boolean doAcquireSharedNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (nanosTimeout <= 0L) {
            return false;
        }
        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        failed = false;
                        return true;
                    }
                }
                nanosTimeout = deadline - System.nanoTime();
                if (nanosTimeout <= 0L) {
                    return false;
                }
                if (shouldParkAfterFailedAcquire(p, node) && nanosTimeout > spinForTimeoutThreshold) {
                    LockSupport.parkNanos(this, nanosTimeout);
                }
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
            }
        } finally {
            if (failed) {
                cancelAcquire(node);
            }
        }
    }

    //===================================主要对外暴露的方法

    /**
     * 试图以独占模式获取。这个方法应该查询对象的状态是否允许以独占模式获取它，如果允许，
     * 也应该查询是否允许以独占模式获取它。<p>这个方法总是被执行获取的线程调用。
     * 如果此方法报告失败，如果尚未排队，则获取方法可以对线程进行排队，直到从其他线程发出释放信号。
     * 这可以用来实现方法{@link Lock#tryLock()}。
     */
    protected boolean tryAcquire(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 试图将状态设置为以独占模式反映发布。执行release的线程总是调用此方法。
     */
    protected boolean tryRelease(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 尝试在共享模式下获取。这个方法应该查询对象的状态是否允许以共享模式获取它，如果允许，
     * 也应该查询是否允许以共享模式获取它。<p>这个方法总是被执行获取的线程调用。如果此方法报告失败，
     * 如果尚未排队，则获取方法可以对线程进行排队，直到从其他线程发出释放信号
     */
    protected int tryAcquireShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 尝试将状态设置为以共享模式反映发布。
     */
    protected boolean tryReleaseShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 如果同步仅针对当前(调用)线程执行，则返回{@code true}。此方法在每次调用非等待的{@link ConditionObject}
     * 方法时调用。(相反，等待方法调用{@link #release}。)
     */
    protected boolean isHeldExclusively() {
        throw new UnsupportedOperationException();
    }

    /**
     * 以独占模式获取，忽略中断。通过至少调用一次{@link #tryAcquire}来实现，成功后返回。否则，线程将排队，
     * 可能会反复阻塞和解除阻塞，调用{@link #tryAcquire}直到成功。此方法可用于实现方法{@link Lock# Lock}。
     */
    public final void acquire(int arg) {
        if (!tryAcquire(arg) && acquireQueued(addWaiter(Node.EXCLUSIVE), arg)) {
            selfInterrupt();
        }
    }

    /**
     * 以独占模式获取，如果中断将中止。首先检查中断状态，然后至少调用一次{@link #tryAcquire}，成功后返回。
     * 否则，线程将排队，可能反复阻塞和解除阻塞，调用{@link #tryAcquire}，直到成功或线程被中断。
     * 此方法可用于实现方法{@link Lock#lockInterruptibly}。
     */
    public final void acquireInterruptibly(int arg) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        if (!tryAcquire(arg)) {
            doAcquireInterruptibly(arg);
        }
    }

    /**
     * 尝试以独占模式获取，如果中断将中止，如果超时超时将失败。
     * 首先检查中断状态，然后至少调用一次{@link #tryAcquire}，成功后返回。
     * 否则，线程将排队，可能反复阻塞和解除阻塞，调用{@link #tryAcquire}，直到成功或线程被中断或超时结束。
     * 此方法可用于实现方法{@link Lock#tryLock(long, TimeUnit)}。
     */
    public final boolean tryAcquireNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return tryAcquire(arg) || doAcquireNanos(arg, nanosTimeout);
    }

    /**
     * 以独占模式发布。如果{@link #tryRelease}返回true，则通过解阻塞一个或多个线程来实现。
     * 此方法可用于实现方法{@link Lock#unlock}。
     */
    public final boolean release(int arg) {
        if (tryRelease(arg)) {
            Node h = head;
            if (h != null && h.waitStatus != 0) {
                unparkSuccessor(h);
            }
            return true;
        }
        return false;
    }

    /**
     * 以共享模式获取，忽略中断。首先调用至少一次{@link # tryacquiremred}，成功后返回。
     * 否则，线程将排队，可能反复阻塞和解除阻塞，调用{@link # tryacquiremred}直到成功。
     */
    public final void acquireShared(int arg) {
        if (tryAcquireShared(arg) < 0) {
            doAcquireShared(arg);
        }
    }

    /**
     * 以共享模式获取，如果中断将中止。首先检查中断状态，然后至少调用一次{@link # tryacquiremred}，成功后返回。
     * 否则，线程将排队，可能反复阻塞和解除阻塞，调用{@link # tryacquiremred}，直到成功或线程被中断。
     */
    public final void acquireSharedInterruptibly(int arg) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        if (tryAcquireShared(arg) < 0) {
            doAcquireSharedInterruptibly(arg);
        }
    }

    /**
     * 尝试以共享模式获取，如果中断将中止，如果超时超时将失败。
     * 首先检查中断状态，然后至少调用一次{@link # tryacquiremred}，成功后返回。
     * 否则，线程将排队，可能反复阻塞和解除阻塞，调用{@link # tryacquiremred}，直到成功或线程被中断或超时结束。
     */
    public final boolean tryAcquireSharedNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return tryAcquireShared(arg) >= 0 || doAcquireSharedNanos(arg, nanosTimeout);
    }

    /**
     * 以共享模式发布。如果{@link #tryReleaseShared}返回true，则通过解除阻塞一个或多个线程来实现。
     */
    public final boolean releaseShared(int arg) {
        if (tryReleaseShared(arg)) {
            doReleaseShared();
            return true;
        }
        return false;
    }

    // 队列相关判断方法

    /**
     * 查询是否有线程正在等待获取。
     */
    public final boolean hasQueuedThreads() {
        return head != tail;
    }

    /**
     * 查询是否有线程争用过此同步器;
     */
    public final boolean hasContended() {
        return head != null;
    }

    /**
     * 返回队列中的第一个(等待时间最长的)线程，如果当前没有线程排队，则返回{@code null}
     */
    public final Thread getFirstQueuedThread() {
        // handle only fast path, else relay
        return (head == tail) ? null : fullGetFirstQueuedThread();
    }

    /**
     * 当快速查询路径失败时调用getFirstQueuedThread的版本
     */
    private Thread fullGetFirstQueuedThread() {
        /*
        第一个节点通常是head.next。尝试获取它的线程字段，确保一致读取:如果线程字段为null或s。
        prev不再是head，然后一些其他线程在我们的一些读取之间并发地执行setHead。
        在遍历之前，我们尝试了两次。
        */
        Node h, s;
        Thread st;
        if (((h = head) != null && (s = h.next) != null &&
                s.prev == head && (st = s.thread) != null) ||
                ((h = head) != null && (s = h.next) != null &&
                        s.prev == head && (st = s.thread) != null))
            return st;
        /*
          Head的下一个字段可能还没有设置，或者在setHead之后已经取消设置。
          所以我们必须检查tail是否是第一个节点。
          如果没有，我们继续，从尾部到头部安全地找到第一个，保证终止。
         */
        Node t = tail;
        Thread firstThread = null;
        while (t != null && t != head) {
            Thread tt = t.thread;
            if (tt != null)
                firstThread = tt;
            t = t.prev;
        }
        return firstThread;
    }

    /**
     * 如果给定线程当前正在排队，则返回true。
     */
    public final boolean isQueued(Thread thread) {
        if (thread == null) {
            throw new NullPointerException();
        }
        //从尾节点向前遍历，保证在并发场景下也能遍历到所有节点。具体原因见enq()方法
        for (Node p = tail; p != null; p = p.prev) {
            if (p.thread == thread) {
                return true;
            }
        }
        return false;
    }

    /**
     * 如果第一个排队的线程(如果存在)正在以排它模式等待，则返回{@code true}。
     * 如果这个方法返回{@code true}，并且当前线程正在尝试以共享模式获取(也就是说，
     * 这个方法是从{@link # tryacquiremred}调用的)，那么可以保证当前线程不是第一个排队的线程。
     * 仅在ReentrantReadWriteLock中作为启发式使用。
     */
    final boolean apparentlyFirstQueuedIsExclusive() {
        Node h, s;
        return (h = head) != null &&
                (s = h.next) != null &&
                !s.isShared() &&
                s.thread != null;
    }

    /**
     * 查询是否有任何线程等待获取的时间超过当前线程
     */
    public final boolean hasQueuedPredecessors() {
        /**
         * 这种方法的正确性取决于头在tail之前和head之前被初始化。
         * 如果当前线程是队列中的第一个线程，则next是准确的。
         */
        Node t = tail; // 按反初始化顺序读取字段
        Node h = head;
        Node s;
        return h != t && ((s = h.next) == null || s.thread != Thread.currentThread());
    }


    // Instrumentation and monitoring methods

    /**
     * 返回等待获取的线程数的估计值。这个值只是一个估计值，
     * 因为当这个方法遍历内部数据结构时，线程的数量可能会动态变化。
     * 该方法用于*监控系统状态，不用于同步控制。
     */
    public final int getQueueLength() {
        int n = 0;
        for (Node p = tail; p != null; p = p.prev) {
            if (p.thread != null)
                ++n;
        }
        return n;
    }

    /**
     * 返回一个集合，其中包含可能正在等待获取的线程。由于实际的线程集在构造此结果时可能会动态更改，
     * 因此返回的集合只是一个最佳效果估计。返回集合的元素没有特定的顺序。
     * 此方法的设计目的是方便构造提供更广泛监视设施的子类。
     */
    public final Collection<Thread> getQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            Thread t = p.thread;
            if (t != null)
                list.add(t);
        }
        return list;
    }

    /**
     * 返回一个集合，其中包含可能在独占模式下等待获取的线程。
     * 它具有与{@link #getQueuedThreads}相同的属性，只是它只返回那些由于独占获取而等待的线程
     */
    public final Collection<Thread> getExclusiveQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (!p.isShared()) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    /**
     * 返回一个集合，其中包含可能在共享模式下等待获取的线程。
     * 它具有与{@link #getQueuedThreads}相同的属性，只是它只返回那些由于共享获取而等待的线程。
     */
    public final Collection<Thread> getSharedQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (p.isShared()) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    /**
     * @return a string identifying this synchronizer, as well as its state
     */
    public String toString() {
        int s = getState();
        String q = hasQueuedThreads() ? "non" : "";
        return super.toString() +
                "[State = " + s + ", " + q + "empty queue]";
    }


    // Internal support methods for Conditions

    /**
     * 如果一个节点(始终是最初放置在条件队列中的节点)现在正等待在同步队列上重新获取，则返回true。
     */
    final boolean isOnSyncQueue(Node node) {
        if (node.waitStatus == Node.CONDITION || node.prev == null)
            return false;
        if (node.next != null) // If has successor, it must be on queue
            return true;
        /**
         node.prev可以是非null，但尚未在队列中，因为CAS将其置于队列中可能会失败。
         所以我们必须这样做从尾巴穿过，以确保它实际上成功。它在调用这种方法时，
         它总是接近尾部，并且除非CAS失败（这是不可能的），它会在那里，所以我们几乎不会遍历很多。
         */
        return findNodeFromTail(node);
    }

    /**
     * 如果节点位于同步队列上，则通过从tail向后搜索返回true。仅在isOnSyncQueue需要时调用。
     *
     * @return true if present
     */
    private boolean findNodeFromTail(Node node) {
        Node t = tail;
        for (; ; ) {
            if (t == node)
                return true;
            if (t == null)
                return false;
            t = t.prev;
        }
    }

    /**
     * 将节点从条件队列传输到同步队列。如果成功返回true。
     */
    final boolean transferForSignal(Node node) {
        /*
         * 如果无法更改等待状态，则节点已被取消。
         */
        if (!compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
            return false;
        }

        /**
         将Splice连接到队列，并尝试设置前辈的等待状态，以指示线程(可能)正在等待。
         如果取消或尝试设置等待状态失败，则唤醒并重新同步(在这种情况下，等待状态可能是暂时错误的，
         并且不会造成任何危害)。
         */
        Node p = enq(node);
        int ws = p.waitStatus;
        if (ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL))
            LockSupport.unpark(node.thread);
        return true;
    }

    /**
     * 如果需要，在取消等待后传输节点来同步队列。如果线程在发出信号之前被取消，则返回true
     */
    final boolean transferAfterCancelledWait(Node node) {
        if (compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
            enq(node);
            return true;
        }
        /*
          如果我们输给了一个signal()，那么我们就不能继续，直到它完成它的enq()。
          在一个不完整的转移过程中取消是罕见的，也是短暂的，所以只要旋转。
         */
        while (!isOnSyncQueue(node)) {
            Thread.yield();
        }

        return false;
    }

    /**
     * Invokes release with current state value; returns saved state.
     * Cancels node and throws exception on failure.
     *
     * @param node the condition node for this wait
     * @return previous sync state
     */
    final int fullyRelease(Node node) {
        boolean failed = true;
        try {
            int savedState = getState();
            if (release(savedState)) {
                failed = false;
                return savedState;
            } else {
                throw new IllegalMonitorStateException();
            }
        } finally {
            if (failed)
                node.waitStatus = Node.CANCELLED;
        }
    }

    // Instrumentation methods for conditions

    /**
     * Queries whether the given ConditionObject
     * uses this synchronizer as its lock.
     *
     * @param condition the condition
     * @return {@code true} if owned
     * @throws NullPointerException if the condition is null
     */
    public final boolean owns(ConditionObject condition) {
        return condition.isOwnedBy(this);
    }

    /**
     * Queries whether any threads are waiting on the given condition
     * associated with this synchronizer. Note that because timeouts
     * and interrupts may occur at any time, a {@code true} return
     * does not guarantee that a future {@code signal} will awaken
     * any threads.  This method is designed primarily for use in
     * monitoring of the system state.
     *
     * @param condition the condition
     * @return {@code true} if there are any waiting threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *                                      is not held
     * @throws IllegalArgumentException     if the given condition is
     *                                      not associated with this synchronizer
     * @throws NullPointerException         if the condition is null
     */
    public final boolean hasWaiters(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.hasWaiters();
    }

    /**
     * Returns an estimate of the number of threads waiting on the
     * given condition associated with this synchronizer. Note that
     * because timeouts and interrupts may occur at any time, the
     * estimate serves only as an upper bound on the actual number of
     * waiters.  This method is designed for use in monitoring of the
     * system state, not for synchronization control.
     *
     * @param condition the condition
     * @return the estimated number of waiting threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *                                      is not held
     * @throws IllegalArgumentException     if the given condition is
     *                                      not associated with this synchronizer
     * @throws NullPointerException         if the condition is null
     */
    public final int getWaitQueueLength(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitQueueLength();
    }

    /**
     * Returns a collection containing those threads that may be
     * waiting on the given condition associated with this
     * synchronizer.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate. The elements of the
     * returned collection are in no particular order.
     *
     * @param condition the condition
     * @return the collection of threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *                                      is not held
     * @throws IllegalArgumentException     if the given condition is
     *                                      not associated with this synchronizer
     * @throws NullPointerException         if the condition is null
     */
    public final Collection<Thread> getWaitingThreads(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitingThreads();
    }

    /**
     * Condition implementation for a {@link
     * AbstractQueuedSynchronizer} serving as the basis of a {@link
     * Lock} implementation.
     *
     * <p>Method documentation for this class describes mechanics,
     * not behavioral specifications from the point of view of Lock
     * and Condition users. Exported versions of this class will in
     * general need to be accompanied by documentation describing
     * condition semantics that rely on those of the associated
     * {@code AbstractQueuedSynchronizer}.
     *
     * <p>This class is Serializable, but all fields are transient,
     * so deserialized conditions have no waiters.
     */
    public class ConditionObject implements Condition, java.io.Serializable {
        private static final long serialVersionUID = 1173984872572414699L;
        /**
         * First node of condition queue.
         */
        private transient Node firstWaiter;
        /**
         * Last node of condition queue.
         */
        private transient Node lastWaiter;

        /**
         * Creates a new {@code ConditionObject} instance.
         */
        public ConditionObject() {
        }

        // Internal methods

        /**
         * Adds a new waiter to wait queue.
         *
         * @return its new wait node
         */
        private Node addConditionWaiter() {
            Node t = lastWaiter;
            // If lastWaiter is cancelled, clean out.
            if (t != null && t.waitStatus != Node.CONDITION) {
                unlinkCancelledWaiters();
                t = lastWaiter;
            }
            Node node = new Node(Thread.currentThread(), Node.CONDITION);
            if (t == null)
                firstWaiter = node;
            else
                t.nextWaiter = node;
            lastWaiter = node;
            return node;
        }

        /**
         * Removes and transfers nodes until hit non-cancelled one or
         * null. Split out from signal in part to encourage compilers
         * to inline the case of no waiters.
         *
         * @param first (non-null) the first node on condition queue
         */
        private void doSignal(Node first) {
            do {
                if ((firstWaiter = first.nextWaiter) == null)
                    lastWaiter = null;
                first.nextWaiter = null;
            } while (!transferForSignal(first) &&
                    (first = firstWaiter) != null);
        }

        /**
         * Removes and transfers all nodes.
         *
         * @param first (non-null) the first node on condition queue
         */
        private void doSignalAll(Node first) {
            lastWaiter = firstWaiter = null;
            do {
                Node next = first.nextWaiter;
                first.nextWaiter = null;
                transferForSignal(first);
                first = next;
            } while (first != null);
        }

        /**
         * Unlinks cancelled waiter nodes from condition queue.
         * Called only while holding lock. This is called when
         * cancellation occurred during condition wait, and upon
         * insertion of a new waiter when lastWaiter is seen to have
         * been cancelled. This method is needed to avoid garbage
         * retention in the absence of signals. So even though it may
         * require a full traversal, it comes into play only when
         * timeouts or cancellations occur in the absence of
         * signals. It traverses all nodes rather than stopping at a
         * particular target to unlink all pointers to garbage nodes
         * without requiring many re-traversals during cancellation
         * storms.
         */
        private void unlinkCancelledWaiters() {
            Node t = firstWaiter;
            Node trail = null;
            while (t != null) {
                Node next = t.nextWaiter;
                if (t.waitStatus != Node.CONDITION) {
                    t.nextWaiter = null;
                    if (trail == null)
                        firstWaiter = next;
                    else
                        trail.nextWaiter = next;
                    if (next == null)
                        lastWaiter = trail;
                } else
                    trail = t;
                t = next;
            }
        }

        // public methods

        /**
         * Moves the longest-waiting thread, if one exists, from the
         * wait queue for this condition to the wait queue for the
         * owning lock.
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *                                      returns {@code false}
         */
        public final void signal() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            Node first = firstWaiter;
            if (first != null)
                doSignal(first);
        }

        /**
         * Moves all threads from the wait queue for this condition to
         * the wait queue for the owning lock.
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *                                      returns {@code false}
         */
        public final void signalAll() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            Node first = firstWaiter;
            if (first != null)
                doSignalAll(first);
        }

        /**
         * Implements uninterruptible condition wait.
         * <ol>
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         * throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled.
         * <li> Reacquire by invoking specialized version of
         * {@link #acquire} with saved state as argument.
         * </ol>
         */
        public final void awaitUninterruptibly() {
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            boolean interrupted = false;
            while (!isOnSyncQueue(node)) {
                LockSupport.park(this);
                if (Thread.interrupted())
                    interrupted = true;
            }
            if (acquireQueued(node, savedState) || interrupted)
                selfInterrupt();
        }

        /*
         * For interruptible waits, we need to track whether to throw
         * InterruptedException, if interrupted while blocked on
         * condition, versus reinterrupt current thread, if
         * interrupted while blocked waiting to re-acquire.
         */

        /**
         * Mode meaning to reinterrupt on exit from wait
         */
        private static final int REINTERRUPT = 1;
        /**
         * Mode meaning to throw InterruptedException on exit from wait
         */
        private static final int THROW_IE = -1;

        /**
         * Checks for interrupt, returning THROW_IE if interrupted
         * before signalled, REINTERRUPT if after signalled, or
         * 0 if not interrupted.
         */
        private int checkInterruptWhileWaiting(Node node) {
            return Thread.interrupted() ?
                    (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) :
                    0;
        }

        /**
         * Throws InterruptedException, reinterrupts current thread, or
         * does nothing, depending on mode.
         */
        private void reportInterruptAfterWait(int interruptMode)
                throws InterruptedException {
            if (interruptMode == THROW_IE)
                throw new InterruptedException();
            else if (interruptMode == REINTERRUPT)
                selfInterrupt();
        }

        /**
         * Implements interruptible condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         * throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled or interrupted.
         * <li> Reacquire by invoking specialized version of
         * {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * </ol>
         */
        public final void await() throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                LockSupport.park(this);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null) // clean up if cancelled
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
        }

        /**
         * Implements timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         * throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         * {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * </ol>
         */
        public final long awaitNanos(long nanosTimeout)
                throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            final long deadline = System.nanoTime() + nanosTimeout;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    transferAfterCancelledWait(node);
                    break;
                }
                if (nanosTimeout >= spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
                nanosTimeout = deadline - System.nanoTime();
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return deadline - System.nanoTime();
        }

        /**
         * Implements absolute timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         * throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         * {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * <li> If timed out while blocked in step 4, return false, else true.
         * </ol>
         */
        public final boolean awaitUntil(Date deadline)
                throws InterruptedException {
            long abstime = deadline.getTime();
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            boolean timedout = false;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (System.currentTimeMillis() > abstime) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }
                LockSupport.parkUntil(this, abstime);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return !timedout;
        }

        /**
         * Implements timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         * throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         * {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * <li> If timed out while blocked in step 4, return false, else true.
         * </ol>
         */
        public final boolean await(long time, TimeUnit unit)
                throws InterruptedException {
            long nanosTimeout = unit.toNanos(time);
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            final long deadline = System.nanoTime() + nanosTimeout;
            boolean timedout = false;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }
                if (nanosTimeout >= spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
                nanosTimeout = deadline - System.nanoTime();
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return !timedout;
        }

        //  support for instrumentation

        /**
         * Returns true if this condition was created by the given
         * synchronization object.
         *
         * @return {@code true} if owned
         */
        final boolean isOwnedBy(AbstractQueuedSynchronizer sync) {
            return sync == AbstractQueuedSynchronizer.this;
        }

        /**
         * Queries whether any threads are waiting on this condition.
         * Implements {@link AbstractQueuedSynchronizer#hasWaiters(ConditionObject)}.
         *
         * @return {@code true} if there are any waiting threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *                                      returns {@code false}
         */
        protected final boolean hasWaiters() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION)
                    return true;
            }
            return false;
        }

        /**
         * Returns an estimate of the number of threads waiting on
         * this condition.
         * Implements {@link AbstractQueuedSynchronizer#getWaitQueueLength(ConditionObject)}.
         *
         * @return the estimated number of waiting threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *                                      returns {@code false}
         */
        protected final int getWaitQueueLength() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            int n = 0;
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION)
                    ++n;
            }
            return n;
        }

        /**
         * Returns a collection containing those threads that may be
         * waiting on this Condition.
         * Implements {@link AbstractQueuedSynchronizer#getWaitingThreads(ConditionObject)}.
         *
         * @return the collection of threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *                                      returns {@code false}
         */
        protected final Collection<Thread> getWaitingThreads() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            ArrayList<Thread> list = new ArrayList<Thread>();
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION) {
                    Thread t = w.thread;
                    if (t != null)
                        list.add(t);
                }
            }
            return list;
        }
    }

    /**
     * Setup to support compareAndSet. We need to natively implement
     * this here: For the sake of permitting future enhancements, we
     * cannot explicitly subclass AtomicInteger, which would be
     * efficient and useful otherwise. So, as the lesser of evils, we
     * natively implement using hotspot intrinsics API. And while we
     * are at it, we do the same for other CASable fields (which could
     * otherwise be done with atomic field updaters).
     */
    private static final Unsafe unsafe; //= Unsafe.getUnsafe();
    private static final long stateOffset;
    private static final long headOffset;
    private static final long tailOffset;
    private static final long waitStatusOffset;
    private static final long nextOffset;

    static {
        try {
            final PrivilegedExceptionAction<Unsafe> action = new PrivilegedExceptionAction<Unsafe>() {
                public Unsafe run() throws Exception {
                    Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                    theUnsafe.setAccessible(true);
                    return (Unsafe) theUnsafe.get(null);
                }
            };
            unsafe = AccessController.doPrivileged(action);
        } catch (Exception e) {
            throw new RuntimeException("Unable to load unsafe", e);
        }

        try {
            stateOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.class.getDeclaredField("state"));
            headOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.class.getDeclaredField("head"));
            tailOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.class.getDeclaredField("tail"));
            waitStatusOffset = unsafe.objectFieldOffset
                    (Node.class.getDeclaredField("waitStatus"));
            nextOffset = unsafe.objectFieldOffset
                    (Node.class.getDeclaredField("next"));

        } catch (Exception ex) {
            throw new Error(ex);
        }
    }

    /**
     * CAS head field. Used only by enq.
     */
    private final boolean compareAndSetHead(Node update) {
        return unsafe.compareAndSwapObject(this, headOffset, null, update);
    }

    /**
     * CAS tail field. Used only by enq.
     */
    private final boolean compareAndSetTail(Node expect, Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }

    /**
     * CAS waitStatus field of a node.
     */
    private static final boolean compareAndSetWaitStatus(Node node,
                                                         int expect,
                                                         int update) {
        return unsafe.compareAndSwapInt(node, waitStatusOffset,
                expect, update);
    }

    /**
     * CAS next field of a node.
     */
    private static final boolean compareAndSetNext(Node node,
                                                   Node expect,
                                                   Node update) {
        return unsafe.compareAndSwapObject(node, nextOffset, expect, update);
    }
}
