package testjava.concurrent;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * <Description>
 *
 * @author hushiye
 * @since 2020-08-28 11:42
 */
public class ReadWriteLockTest {
    public static void main(String[] args) {
        ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        ReentrantReadWriteLock.ReadLock readLock = readWriteLock.readLock();
        ReentrantReadWriteLock.WriteLock writeLock = readWriteLock.writeLock();

        readLock.lock();


    }
}
