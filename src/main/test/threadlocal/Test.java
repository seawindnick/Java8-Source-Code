package threadlocal;

/**
 * <Description>
 *
 * @author hushiye
 * @since 3/15/21 17:21
 */
public class Test {

    public static void main(String[] args) {
        String a = "a";
        String b = "b";
        String c = a + b;

        String result = add(a,b);

        System.out.println(c);

    }

    private static String add(String a, String b) {
        System.out.println("哈哈");
        return a+b;
    }
}
