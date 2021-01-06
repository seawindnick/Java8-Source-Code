package testjava.basic;

import java.util.Objects;

/**
 * <Description>
 *
 * @author hushiye
 * @since 12/28/20 10:18
 */
public class ArrayListTest {

    public static void main(String[] args) {

        int num = 10000000;

        Person[] old = new Person[num];
        Person[] newPerson = new Person[num];

        for (int i = 0; i < num - 2; i++) {
            Person temp = new Person("张三" + "old" + i, 14, false);
            old[i] = temp;
            newPerson[i] = temp;
        }

        Person p = new Person("张三", 14, false);
        Person temp = new Person("张三" + "newtemp", 14, false);
        old[num - 2] = p;
        old[num - 1] = temp;

        newPerson[num - 1] = p;
        newPerson[num - 2] = temp;


        long startTime1 = System.currentTimeMillis();
        for (int i = 0; i < newPerson.length; i++) {
            if (Objects.equals(newPerson[i], p)) {
                break;
            }
        }


        long startTime2 = System.currentTimeMillis();
        System.out.println(startTime2 - startTime1);
        long startTime3 = System.currentTimeMillis();
        for (int i = 0; i < newPerson.length; i++) {
            if (old[i] != newPerson[i] && Objects.equals(newPerson[i], p)) {
                break;
            }
        }
        long startTime4 = System.currentTimeMillis();
        System.out.println(startTime4 - startTime3);

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
