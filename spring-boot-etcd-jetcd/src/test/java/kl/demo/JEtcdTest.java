package kl.demo;

import io.etcd.jetcd.*;
import io.etcd.jetcd.lease.LeaseKeepAliveResponse;
import io.grpc.stub.StreamObserver;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/**
 * @author: kl @kailing.pub
 * @date: 2019/7/22
 */
public class JEtcdTest {

    private Client client;
    private Lock lock;
    private Lease lease;
    //单位：秒
    private long lockTTl = 1;
    private ByteSequence lockKey = ByteSequence.from("/root/lock", StandardCharsets.UTF_8);
    private ScheduledExecutorService scheduledThreadPool = Executors.newScheduledThreadPool(2);

    @Before
    public void setUp() {
         client = Client.builder().endpoints(
                "http://127.0.0.1:2379"
        ).build();
         lock = client.getLockClient();
         lease = client.getLeaseClient();
    }

    @Test
    public void lockTest1toMaster() throws InterruptedException, ExecutionException {
        long leaseId = lease.grant(lockTTl).get().getID();

         lease.keepAlive(leaseId, new StreamObserver<LeaseKeepAliveResponse>() {
             @Override
             public void onNext(LeaseKeepAliveResponse value) {
                 System.err.println("LeaseKeepAliveResponse value:"+ value.getTTL());
             }

             @Override
             public void onError(Throwable t) {
                 scheduledThreadPool.shutdownNow();
                 t.printStackTrace();
             }

             @Override
             public void onCompleted() {
                 scheduledThreadPool.shutdownNow();
             }
         });
        lock.lock(lockKey, leaseId).get().getKey();

        scheduledThreadPool.submit(() -> {
            while (true) {
                System.err.println("我是主服务开始工作了");
                TimeUnit.SECONDS.sleep(1);
            }
        });
        TimeUnit.DAYS.sleep(1);
    }

    @Test
    public void lockTest2toStandby() throws InterruptedException, ExecutionException {
        long leaseId = lease.grant(lockTTl).get().getID();
        lease.keepAlive(leaseId, new StreamObserver<LeaseKeepAliveResponse>() {
            @Override
            public void onNext(LeaseKeepAliveResponse value) {
                System.err.println("LeaseKeepAliveResponse value:"+ value.getTTL());
            }

            @Override
            public void onError(Throwable t) {
                scheduledThreadPool.shutdownNow();

                t.printStackTrace();
            }

            @Override
            public void onCompleted() {
                scheduledThreadPool.shutdownNow();

            }
        });
        lock.lock(lockKey, leaseId).get().getKey();
        scheduledThreadPool.submit(() -> {
            while (true) {
                System.err.println("我是备用服务，我开始工作了，估计主服务已经挂了");
                TimeUnit.SECONDS.sleep(1);
            }
        });
        TimeUnit.DAYS.sleep(1);
    }

    @Test
    public void lockTest3toStandby() throws InterruptedException, ExecutionException {
        long leaseId = lease.grant(lockTTl).get().getID();
        lease.keepAlive(leaseId, new StreamObserver<LeaseKeepAliveResponse>() {
            @Override
            public void onNext(LeaseKeepAliveResponse value) {
                System.err.println("LeaseKeepAliveResponse value:"+ value.getTTL());
            }

            @Override
            public void onError(Throwable t) {
                scheduledThreadPool.shutdownNow();
                t.printStackTrace();
            }

            @Override
            public void onCompleted() {
                scheduledThreadPool.shutdownNow();
            }
        });
        lock.lock(lockKey, leaseId).get().getKey();

        scheduledThreadPool.submit(() -> {
            while (true) {
                System.err.println("我是备用服务，我开始工作了，估计主服务已经挂了");
                TimeUnit.SECONDS.sleep(1);
            }
        });
        TimeUnit.DAYS.sleep(1);
    }
}
