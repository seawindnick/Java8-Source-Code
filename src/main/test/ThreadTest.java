/**
 * <Description>
 *
 * @author hushiye
 * @since 1/29/21 23:02
 */
public class ThreadTest {
    public static void main(String[] args) throws InterruptedException {


//        Thread thread = new Thread();
//
//
//        Lock lock = new ReentrantLock();
//        Condition condition = lock.newCondition();
//        RunThread runThread = new RunThread();
//
//
//        InterThread interThread = new InterThread(runThread,condition);
//
//        WaitThread waitThread = new WaitThread(runThread,condition);
//        waitThread.start();
//        interThread.start();
//        runThread.join();
//        System.out.println("哈哈");

//        Thread.sleep(1000);
    }


    public static class ParentThread extends Thread{

        @Override
        public void run() {
//            super.run();
//            super.getTh

        }
    }



    public static class RunThread extends Thread{

        @Override
        public void run() {
            try {
                int i =0;
                Thread.sleep(15000);
//                do {
//                    System.out.println(111);
//                }while (i++ < 100);
//
//                this.interrupt();
            }catch (Exception ex){

            }
        }
    }

    public static class InterThread extends Thread{
        public Thread thread;
        public Object object;

        public InterThread(Thread thread, Object object) {
            this.thread = thread;
            this.object = object;
        }


        @Override
        public void run() {
            try {
                Thread.sleep(5000);
                object.notifyAll();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    }

    public static class WaitThread extends Thread{
        public Thread thread;
        public Object object;

        public WaitThread(Thread thread, Object object) {
            this.thread = thread;
            this.object = object;
        }


        @Override
        public void run() {
            try {
                Thread.sleep(1000);
                object.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    }
}
