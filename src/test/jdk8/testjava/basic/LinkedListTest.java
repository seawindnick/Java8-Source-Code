package testjava.basic;

import java.util.LinkedList;
import java.util.ListIterator;

/**
 * <Description>
 *
 * @author hushiye
 * @since 12/29/20 23:29
 */
public class LinkedListTest {

    public static void main(String[] args) {

        LinkedList<Integer> list = new LinkedList<>();
        for (int i = 0; i < 10; i++) {
            list.add(i);
        }

        ListIterator<Integer> listIterator = list.listIterator();
        while (listIterator.hasNext()){
            Integer i = listIterator.next();
        }

        while (listIterator.hasPrevious()){
            Integer i = listIterator.previous();
            listIterator.remove();
        }



    }
}
