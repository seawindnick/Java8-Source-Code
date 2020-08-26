package testjava.concurrent;

/**
 * <Description>
 *
 * @author hushiye
 * @since 2020-08-26 14:41
 */
public class Increment {

    private int i;
    PlainLock plainLock = new PlainLock();

    public void increase() {
        plainLock.lock();
        i++;
        plainLock.unlock();
    }

    public int getI() {
        return i;
    }

    public static void test(int threadNum, int loopTimes) {
        Increment increment = new Increment();

        Thread[] threads = new Thread[threadNum];

        for (int i = 0; i < threads.length; i++) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < loopTimes; i++) {
                        increment.increase();
                    }
                }
            });
            threads[i] = t;
            t.start();
        }

        for (Thread t : threads) {  //main线程等待其他线程都执行完成
            try {
                t.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        System.out.println(threadNum + "个线程，循环" + loopTimes + "次结果：" + increment.getI());
    }

    public static void main(String[] args) {
        test(20, 1);
        test(20, 10);
        test(20, 100);
        test(20, 1000);
        test(20, 10000);
        test(20, 100000);
        test(20, 1000000);
    }

}
