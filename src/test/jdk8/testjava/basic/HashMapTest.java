package testjava.basic;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * <Description>
 *
 * @author hushiye
 * @since 1/1/21 22:43
 */
public class HashMapTest {

    public static void main(String[] args) {


        TreeMap treeMap = new TreeMap<>();
        treeMap.put(null,1);

        Map map = new HashMap(3);
        map.put(1,1);


//        Map map = new HashMap();
//
//        for (int i = 0; i < 64; i++) {
//            String name = "张三" + i;
//            Person person = new Person(name, 11,false);
//            map.put(person, person);
//        }
    }


    public static class Person {
        private String name;
        private int age;
        private boolean version;

        public Person(String name, int age, boolean version) {
            this.name = name;
            this.age = age;
            this.version = version;
        }


        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Person)) return false;

            Person person = (Person) o;

            if (age != person.age) return false;
            if (version != person.version) return false;
            return name != null ? name.equals(person.name) : person.name == null;

        }

        @Override
        public int hashCode() {
            int result = name != null ? name.hashCode() : 0;
            result = 31 * result + age;
            result = 31 * result + (version ? 1 : 0);
            return result;
        }
    }
}
