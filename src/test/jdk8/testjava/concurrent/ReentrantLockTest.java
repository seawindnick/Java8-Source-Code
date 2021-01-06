package testjava.concurrent;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <Description>
 *
 * @author hushiye
 * @since 2020-10-26 18:05
 */
public class ReentrantLockTest {
    public static void main(String[] args) throws IOException {
        ReentrantLock reentrantLock = new ReentrantLock();
        reentrantLock.lock();

        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Runnable runnable = new Runnable() {

            @Override
            public void run() {
                System.out.println("哈哈哈");
                reentrantLock.lock();
            }
        };

        new Thread(runnable).start();

        System.in.read();


//        reentrantLock.unlock();
    }
}
