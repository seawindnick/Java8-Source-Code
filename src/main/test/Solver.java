import java.util.Random;
import java.util.concurrent.*;

/**
 * <Description>
 *
 * @author hushiye
 * @since 2/25/21 10:53
 */
public class Solver {


    public static void main(String[] args) {
        ExecutorService executorpool=Executors. newFixedThreadPool(3);
        CyclicBarrier cyclicBarrier= new CyclicBarrier(3, new Runnable() {
            @Override
            public void run() {
                System.out.println("哈哈");
            }
        });

        CycWork work1= new CycWork(cyclicBarrier, "张三" );
        CycWork work2= new CycWork(cyclicBarrier, "李四" );
        CycWork work3= new CycWork(cyclicBarrier, "王五" );

        executorpool.execute(work1);
        executorpool.execute(work2);
        executorpool.execute(work3);

        executorpool.shutdown();

    }

    public static class CycWork implements Runnable {


        private CyclicBarrier cyclicBarrier ;
        private String name ;

        public CycWork(CyclicBarrier cyclicBarrier,String name)
        {
            this .name =name;
            this .cyclicBarrier =cyclicBarrier;
        }

        @Override
        public void run() {
            // TODO Auto-generated method stub

            System. out .println(name +"正在打桩，毕竟不轻松。。。。。" );

            try {
                Thread. sleep(500);
                System. out .println(name +"不容易，终于把桩打完了。。。。" );
//                Thread. sleep(new Random().nextInt(2000));
                cyclicBarrier .await(1,TimeUnit.SECONDS);
                Thread. sleep(2000);
                System. out .println(name +"：其他逗b把桩都打完了，又得忙活了。。。" );
            } catch (Exception e) {
                // TODO: handle exception
                e.printStackTrace();
            }

        }

    }

}
