package threadlocal;


import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <Description>
 *
 * @author hushiye
 * @since 3/12/21 17:55
 */
public class ThreadLocalCopyTest {

    public static void main(String[] args) {


//                *    0   1    2       3     4    5
//                *    0 | 2 | index |  0 |   0 | null

        List<ThreadLocalCopy> list = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            list.add(new ThreadLocalCopy<>());
        }
        ThreadLocalCopy.ThreadLocalMap threadLocalMap = new ThreadLocalCopy.ThreadLocalMap();


        Integer[] nullArray = new Integer[]{5};
        Integer[] nullRefArray = new Integer[]{0, 2, 3, 4};
        AtomicInteger atomicInteger = new AtomicInteger(0);

        Integer startIndex = 0;
        Collection<Integer> nullList = Arrays.asList(nullArray);
        Collection<Integer> nullRefList = Arrays.asList(nullRefArray);

        for (int i = 0; i < 6; i++) {
            Integer value = atomicInteger.getAndIncrement();
            if (nullList.contains(i)) {
                continue;
            }
            ThreadLocalCopy threadLocalCopy = list.get(startIndex++);
//            threadLocalMap.set(threadLocalCopy,value,i);
            threadLocalMap.set(threadLocalCopy, value);
        }

        int index = -1;
        for (ThreadLocalCopy.ThreadLocalMap.Entry entry : threadLocalMap.table) {
            index++;
            if (entry == null) {
                continue;
            }
            Object value = entry.value;
            if (nullRefList.contains(value)) {
                threadLocalMap.set(null, entry.value, index);
            }
        }

//        initNullReference(threadLocalMap);
        int targetValue = 3;
        threadLocalMap.set(list.get(targetValue),5);

        System.out.println("哈哈");


    }

    private static void initNullReference(ThreadLocalCopy.ThreadLocalMap threadLocalMap) {
        int index = 0;
        for (ThreadLocalCopy.ThreadLocalMap.Entry entry : threadLocalMap.table) {
          if (entry == null){
              threadLocalMap.set(null,index,index);
          }
          index ++;
        }

    }

    private static Integer getIndex(ThreadLocalCopy.ThreadLocalMap threadLocalMap, int targetValue) {
        int index = 0;
        for (ThreadLocalCopy.ThreadLocalMap.Entry entry : threadLocalMap.table) {
            if (Objects.equals(entry.value, targetValue)) {
                return index;
            }
            index++;
        }
        return null;
    }
}
