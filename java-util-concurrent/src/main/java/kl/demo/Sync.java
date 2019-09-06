package kl.demo;

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

