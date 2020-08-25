package testjava.io;


import java.io.*;

/**
 * 反序列化导致破坏单例结构
 */
public class SeriableSingleton implements Serializable {

    private int age = 1;

    /**
     * 序列化是把内存中的状态通过转换成字节码的形式，从而转换成I/O流，写入其他地方（磁盘，网络I/O）
     * 内存中的状态会永久保存下来
     *
     * 反序列化就是将已经持久化的字节码内容转换成I/O流
     * 通过I/O 流的读取，进而将读取的内容转换成 java 对象，在转换过程中会重新创建对象
     */
    public final static SeriableSingleton INSTANCE = new SeriableSingleton();

    private SeriableSingleton(){};

    public static final SeriableSingleton getInstance(){
        return INSTANCE;
    }



    private Object readResolve(){
        return INSTANCE;
    }


    public static void main(String[] args) {
        System.out.println(Long.MAX_VALUE);

        SeriableSingleton s1 = null;
        SeriableSingleton s2 = SeriableSingleton.getInstance();

        FileOutputStream fos = null;

        try {
            fos = new FileOutputStream("SeriableSingleton.obj");
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fos);
            objectOutputStream.writeObject(s2);
            objectOutputStream.flush();
            objectOutputStream.close();

            FileInputStream fls = new FileInputStream("SeriableSingleton.obj");
            ObjectInputStream objectInputStream = new ObjectInputStream(fls);
            s1 = (SeriableSingleton) objectInputStream.readObject();
            objectInputStream.close();

            System.out.println(s1);
            System.out.println(s2);
            System.out.println(s1 == s2);


        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }


    }
}
