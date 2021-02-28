import java.util.concurrent.CountDownLatch;

/**
 * <Description>
 *
 * @author hushiye
 * @since 2/23/21 17:48
 */
public class Driver2 {

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch doneSignal = new CountDownLatch(10);

        for (int i = 0; i < 10; ++i) // create and start threads
            new Thread(new WorkerRunnable(doneSignal, i)).start();

        doneSignal.await();           // wait for all to finish
        Thread.sleep(10000);
        System.out.println("结束");
    }


    static class WorkerRunnable implements Runnable {
        private final CountDownLatch doneSignal;
        private final int i;
        WorkerRunnable(CountDownLatch doneSignal, int i) throws InterruptedException {
            this.doneSignal = doneSignal;
            this.i = i;
        }
        public void run() {
            try {
                Thread.sleep(500000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            doWork(i);
            doneSignal.countDown();
        }

        void doWork(int i) {
            System.out.println(i+"狗放");
        }
    }


}
