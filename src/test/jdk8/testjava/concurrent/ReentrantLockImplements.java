package testjava.concurrent;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <Description>
 *
 * @author hushiye
 * @since 2020-08-26 15:57
 */
public class ReentrantLockImplements {

    public static void main(String[] args) {
        Lock lock = new ReentrantLock();
        System.out.println("哈哈");
        lock.lock();
        try {
        } finally {
            lock.unlock();
        }

    }
}
