package threadlocal;


import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class ThreadId {
    private static final AtomicInteger nextId = new AtomicInteger(0);

    // Thread local variable containing each thread's ID
    private static final ThreadLocal<Integer> threadId = ThreadLocal.withInitial(new Supplier() {
        @Override
        public Object get() {
            return nextId.getAndIncrement();
        }
    });

    public static int get() {
        return threadId.get();
    }


    public static void main(String[] args) throws InterruptedException {

        Thread thisThread = Thread.currentThread();
        System.out.println(get());

        System.out.println(ThreadId.get());

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 10; i++) {
                    Thread thisThread = Thread.currentThread();
                    System.out.println(ThreadId.get());
                }
            }
        });
        thread.start();

        Thread.sleep(1000);

        Thread thread2 = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 10; i++) {
                    System.out.println(ThreadId.get());
                }
            }
        });
        thread2.start();

        Thread.sleep(1000);

    }

}
