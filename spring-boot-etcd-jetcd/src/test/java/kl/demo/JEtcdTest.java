package kl.demo;

import io.etcd.jetcd.*;
import io.etcd.jetcd.lease.LeaseKeepAliveResponse;
import io.etcd.jetcd.options.PutOption;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.watch.WatchResponse;
import io.grpc.stub.StreamObserver;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;

/**
 * @author: kl @kailing.pub
 * @date: 2019/7/22
 */
public class JEtcdTest {

    private Client client;
    private Lock lock;
    private Lease lease;
    private Watch watch;
    //单位：秒
    private long lockTTl = 1;
    private ByteSequence lockKey = ByteSequence.from("/root/lock", StandardCharsets.UTF_8);
    private ScheduledExecutorService scheduledThreadPool = Executors.newScheduledThreadPool(2);

    @Before
    public void setUp() {
         client = Client.builder().endpoints(
                "http://localhost:2379"
        ).build();
         lock = client.getLockClient();
         lease = client.getLeaseClient();
         watch = client.getWatchClient();
    }

    @Test
    public void watchTest()throws Exception{
        ByteSequence key = ByteSequence.from("/root/lock/kl", StandardCharsets.UTF_8);
        ByteSequence value = ByteSequence.from("{aaa}", StandardCharsets.UTF_8);

      long id =  client.getLeaseClient().grant(10).get().getID();

       client.getKVClient().put(key,value,PutOption.newBuilder().withLeaseId(id).build());
        watch.watch(key, WatchOption.newBuilder().withPrevKV(true).withNoDelete(true).build(),new Watch.Listener() {
            @Override
            public void onNext(WatchResponse response) {
                System.out.println("监听到数据变更了:"+response.toString());
            }

            @Override
            public void onError(Throwable throwable) {
                throwable.printStackTrace();
            }

            @Override
            public void onCompleted() {
                System.out.println("完成监听了");
            }
        });
        System.out.println("wo ");
         System.in.read();
    }

    @Test
    public void lockTest1toMaster() throws InterruptedException, ExecutionException {
        long leaseId = lease.grant(20).get().getID();

         lease.keepAlive(leaseId, new StreamObserver<LeaseKeepAliveResponse>() {
             @Override
             public void onNext(LeaseKeepAliveResponse value) {
                 System.err.println("LeaseKeepAliveResponse value:"+ value.getTTL());
             }

             @Override
             public void onError(Throwable t) {

                 scheduledThreadPool.shutdown();
                 scheduledThreadPool = null;
//                 try {
//                     scheduledThreadPool = Executors.newScheduledThreadPool(2);
//                     lockTest1toMaster();
//                 } catch (Exception e) {
//                       e.printStackTrace();
//                 }
                 t.printStackTrace();
             }

             @Override
             public void onCompleted() {

               scheduledThreadPool.shutdown();

                 scheduledThreadPool = null;
                 try {
                     scheduledThreadPool = Executors.newScheduledThreadPool(2);
                     lockTest1toMaster();
                 } catch (Exception e) {
                        e.printStackTrace();
                 }
             }
         });
        lock.lock(lockKey, leaseId).get().getKey();

        scheduledThreadPool.submit(() -> {
            while (true) {
                try {
                    System.err.println("我是主服务开始工作了");
                    TimeUnit.SECONDS.sleep(1);
                }catch (Exception e){
                    Thread.currentThread().interrupt();
                    return ;
                }

            }
        });
        TimeUnit.DAYS.sleep(1);
    }

    @Test
    public void lockTest2toStandby() throws InterruptedException, ExecutionException {
        long leaseId = lease.grant(lockTTl).get().getID();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setWaitForTasksToCompleteOnShutdown(true);
        lease.keepAlive(leaseId, new StreamObserver<LeaseKeepAliveResponse>() {
            @Override
            public void onNext(LeaseKeepAliveResponse value) {
                System.err.println("LeaseKeepAliveResponse value:"+ value.getTTL());
            }

            @Override
            public void onError(Throwable t) {
                try {
                    scheduledThreadPool = Executors.newSingleThreadScheduledExecutor();
                    lockTest2toStandby();
                } catch (Exception e) {

                }
                scheduledThreadPool.shutdownNow();
                scheduledThreadPool = null;
                t.printStackTrace();
            }

            @Override
            public void onCompleted() {
                try {
                    lockTest2toStandby();
                    scheduledThreadPool = Executors.newSingleThreadScheduledExecutor();
                } catch (Exception e) {

                }
                scheduledThreadPool.shutdownNow();
                scheduledThreadPool = null;
            }
        });
        lock.lock(lockKey, leaseId).get().getKey();

        scheduledThreadPool.submit(() -> {
            while (true) {
                try {
                    System.err.println("我是备用服务2，我开始工作了，估计主服务已经挂了");
                    TimeUnit.SECONDS.sleep(1);
                }catch (Exception e){
                    Thread.currentThread().interrupt();
                    return ;
                }

            }
        });
        TimeUnit.DAYS.sleep(1);
    }

    @Test
    public void lockTest3toStandby() throws InterruptedException, ExecutionException {
        long leaseId = lease.grant(lockTTl).get().getID();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setWaitForTasksToCompleteOnShutdown(true);
        lease.keepAlive(leaseId, new StreamObserver<LeaseKeepAliveResponse>() {
            @Override
            public void onNext(LeaseKeepAliveResponse value) {
                System.err.println("LeaseKeepAliveResponse value:"+ value.getTTL());
            }

            @Override
            public void onError(Throwable t) {
                try {
                    scheduledThreadPool = Executors.newSingleThreadScheduledExecutor();
                    lockTest3toStandby();
                } catch (Exception e) {

                }
                scheduledThreadPool.shutdownNow();
                scheduledThreadPool = null;
                t.printStackTrace();
            }

            @Override
            public void onCompleted() {
                try {
                    lockTest3toStandby();
                    scheduledThreadPool = Executors.newSingleThreadScheduledExecutor();
                } catch (Exception e) {

                }
                scheduledThreadPool.shutdownNow();
                scheduledThreadPool = null;
            }
        });
        lock.lock(lockKey, leaseId).get().getKey();

        scheduledThreadPool.submit(() -> {
            while (true) {
                try {
                    System.err.println("我是备用服务3，我开始工作了，估计主服务已经挂了");
                    TimeUnit.SECONDS.sleep(1);
                }catch (Exception e){
                    Thread.currentThread().interrupt();
                    return ;
                }

            }
        });
        TimeUnit.DAYS.sleep(1);
    }
}
