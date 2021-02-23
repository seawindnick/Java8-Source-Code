import java.util.concurrent.*;

/**
 * <Description>
 *
 * @author hushiye
 * @since 1/30/21 14:58
 */
public class ExecutorsTest {

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(3, 3, 0, TimeUnit.SECONDS, new LinkedBlockingDeque<>());

        FutureTask futureTask = new FutureTask((Callable<String>) () -> {
            Thread.sleep(3000);
            return "haha" + Thread.currentThread().getName();
        });

//        threadPoolExecutor.execute(futureTask);

        String result = (String) futureTask.get();

        System.out.println(result);

        threadPoolExecutor.shutdown();

    }
}
