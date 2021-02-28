import java.util.concurrent.CountDownLatch;

/**
 * <Description>
 *
 * @author hushiye
 * @since 2/23/21 17:31
 */
public class DriverTest {

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(10);

        for (int i = 0; i < 10; ++i) // create and start threads
            new Thread(new Worker(startSignal, doneSignal)).start();

//     doSomethingElse();            // don't let run yet
        Thread.sleep(10000);
        startSignal.countDown();      // let all threads proceed
        System.out.println("开始放狗");
//     doSomethingElse();
        doneSignal.await();           // wait for all to finish
        System.out.println("结束");
        Thread.sleep(30000);
    }

    static class Worker implements Runnable {
        private final CountDownLatch startSignal;
        private final CountDownLatch doneSignal;
        Worker(CountDownLatch startSignal, CountDownLatch doneSignal) {
            this.startSignal = startSignal;
            this.doneSignal = doneSignal;
        }
        public void run() {
            try {
                System.out.println("线程等待");
                startSignal.await();
                doWork();
                doneSignal.countDown();
                System.out.println("doneSignal 释放");
            } catch (InterruptedException ex) {} // return;
        }

        private void doWork() {
            System.out.println("哈哈");
        }

    }


}

