package lock;


import java.util.concurrent.atomic.AtomicInteger;

public class ThreadId {
    private static final AtomicInteger nextId = new AtomicInteger(0);

    // Thread local variable containing each thread's ID
    private static final ThreadLocal<Integer> threadId =
            ThreadLocal.withInitial(() -> nextId.getAndIncrement());

    public static int get() {
        return threadId.get();
    }


    public static void main(String[] args) throws InterruptedException {

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 10; i++) {
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
