/**
 * <Description>
 *
 * @author hushiye
 * @since 1/30/21 10:26
 */
public class ThreadJoinTest {


    public static void main(String[] args) throws InterruptedException {

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        System.out.println("多线程"+thread.getState());
        System.out.println("主线程"+Thread.currentThread().getState());
        thread.start();
        thread.join();
        System.out.println("多线程"+thread.getState());
        System.out.println("主线程"+Thread.currentThread().getState());
    }


}
