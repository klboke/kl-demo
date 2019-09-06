package kl.demo;

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