package threadlocal;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <Description>
 *
 * @author hushiye
 * @since 3/12/21 17:45
 */
public class ThreadLocalCopy<T> {

    private final int threadLocalHashCode = nextHashCode();


    private static AtomicInteger nextHashCode =
            new AtomicInteger();


    private static final int HASH_INCREMENT = 0x61c88647;


    private static int nextHashCode() {
        return nextHashCode.getAndAdd(HASH_INCREMENT);
    }

    T childValue(T parentValue) {
        throw new UnsupportedOperationException();
    }


    static class ThreadLocalMap {


        static class Entry extends WeakReference<ThreadLocalCopy<?>> {
            /**
             * The value associated with this
             */
            Object value;

            Entry(ThreadLocalCopy<?> k, Object v) {
                super(k);
                value = v;
            }

            public void setRegerence() {

            }
        }

        private static final int INITIAL_CAPACITY = 16;

        public Entry[] table;


        private int size = 0;


        private int threshold; // Default to 0


        private void setThreshold(int len) {
            threshold = len * 2 / 3;
        }


        private static int nextIndex(int i, int len) {
            return ((i + 1 < len) ? i + 1 : 0);
        }


        private static int prevIndex(int i, int len) {
            return ((i - 1 >= 0) ? i - 1 : len - 1);
        }

        public ThreadLocalMap() {
            table = new Entry[INITIAL_CAPACITY];
            size = 1;
            // 设置下一次需要扩容时达到的数量
            setThreshold(INITIAL_CAPACITY);
        }

        ThreadLocalMap(ThreadLocalCopy<?> firstKey, Object firstValue) {
            table = new Entry[INITIAL_CAPACITY];
            // 获取要插入的位置
            int i = firstKey.threadLocalHashCode & (INITIAL_CAPACITY - 1);
            // 创造节点信息进行插入操作
            table[i] = new ThreadLocalMap.Entry(firstKey, firstValue);
            size = 1;
            // 设置下一次需要扩容时达到的数量
            setThreshold(INITIAL_CAPACITY);
        }

        private ThreadLocalMap(ThreadLocalMap parentMap) {
            ThreadLocalMap.Entry[] parentTable = parentMap.table;
            int len = parentTable.length;
            setThreshold(len);
            table = new ThreadLocalMap.Entry[len];

            for (int j = 0; j < len; j++) {
                ThreadLocalMap.Entry e = parentTable[j];
                if (e != null) {
                    @SuppressWarnings("unchecked")
                    ThreadLocalCopy<Object> key = (ThreadLocalCopy<Object>) e.get();
                    if (key != null) {
                        Object value = key.childValue(e.value);
                        ThreadLocalMap.Entry c = new ThreadLocalMap.Entry(key, value);
                        int h = key.threadLocalHashCode & (len - 1);
                        while (table[h] != null)
                            h = nextIndex(h, len);
                        table[h] = c;
                        size++;
                    }
                }
            }
        }


        private Entry getEntry(ThreadLocalCopy<?> key) {
            int i = key.threadLocalHashCode & (table.length - 1);
            ThreadLocalMap.Entry e = table[i];
            if (e != null && e.get() == key)
                return e;
            else
                return getEntryAfterMiss(key, i, e);
        }

        private Entry getEntryAfterMiss(ThreadLocalCopy<?> key, int i, Entry e) {
            ThreadLocalMap.Entry[] tab = table;
            int len = tab.length;

            while (e != null) {
                ThreadLocalCopy<?> k = e.get();
                if (k == key)
                    return e;
                if (k == null)
                    expungeStaleEntry(i);
                else
                    i = nextIndex(i, len);
                e = tab[i];
            }
            return null;
        }

        public void set(ThreadLocalCopy<?> key, Object value) {

            ThreadLocalMap.Entry[] tab = table;
            int len = tab.length;
            // 获取对应的角标信息 （TODO hash发生了偏移，随时在改变）
            int threadLocalHashCode = key.threadLocalHashCode;


            int i = threadLocalHashCode & (len - 1);
            System.out.println(value + "|-----|" + threadLocalHashCode + "|-----------|" + i);

            for (Entry e = tab[i]; e != null; e = tab[i = nextIndex(i, len)]) {

                // 获取对象的引用
                ThreadLocalCopy<?> k = e.get();

                //如果当前对象的引用和下一次对象的应用是同一个，进行替换
                if (k == key) {
                    e.value = value;
                    return;
                }
                if (k == null) {
                    replaceStaleEntry(key, value, i);
                    return;
                }
            }
            tab[i] = new ThreadLocalMap.Entry(key, value);
            int sz = ++size;
            if (!cleanSomeSlots(i, sz) && sz >= threshold)
                rehash();
        }


        public void set(ThreadLocalCopy<?> key, Object value, Integer index) {

            ThreadLocalMap.Entry[] tab = table;
            int len = tab.length;
//            // 获取对应的角标信息 （TODO hash发生了偏移，随时在改变）
//            int i = key.threadLocalHashCode & (len - 1);
//
//            for (Entry e = tab[i]; e != null; e = tab[i = nextIndex(i, len)]) {
//
//                // 获取对象的引用
//                ThreadLocalCopy<?> k = e.get();
//
//                //如果当前对象的引用和下一次对象的应用是同一个，进行替换
//                if (k == key) {
//                    e.value = value;
//                    return;
//                }
//                if (k == null) {
//                    replaceStaleEntry(key, value, i);
//                    return;
//                }
//            }
            tab[index] = new ThreadLocalMap.Entry(key, value);
//            int sz = ++size;
//            if (!cleanSomeSlots(index, sz) && sz >= threshold)
//                rehash();
        }

        public void remove(ThreadLocalCopy<?> key) {
            Entry[] tab = table;
            int len = tab.length;
            int i = key.threadLocalHashCode & (len - 1);
            for (Entry e = tab[i];
                 e != null;
                 e = tab[i = nextIndex(i, len)]) {
                if (e.get() == key) {
                    e.clear();
                    expungeStaleEntry(i);
                    return;
                }
            }
        }


        public void replaceStaleEntry(ThreadLocalCopy<?> key, Object value,
                                      int staleSlot) {
            Entry[] tab = table;
            int len = tab.length;
            Entry e;

            int slotToExpunge = staleSlot;

            for (int i = prevIndex(staleSlot, len); (e = tab[i]) != null; i = prevIndex(i, len)) {
                System.out.println(i);
                if (e.get() == null) {
                    slotToExpunge = i;
                }
            }

            for (int i = nextIndex(staleSlot, len); (e = tab[i]) != null; i = nextIndex(i, len)) {

                ThreadLocalCopy<?> k = e.get();

                if (k == key) {
                    e.value = value;

                    tab[i] = tab[staleSlot];
                    tab[staleSlot] = e;

                    // Start expunge at preceding stale entry if it exists
                    if (slotToExpunge == staleSlot)
                        slotToExpunge = i;
                    cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
                    return;
                }

                // If we didn't find stale entry on backward scan, the
                // first stale entry seen while scanning for key is the
                // first still present in the run.
                if (k == null && slotToExpunge == staleSlot)
                    slotToExpunge = i;
            }

            // If key not found, put new entry in stale slot
            tab[staleSlot].value = null;
            tab[staleSlot] = new Entry(key, value);

            // If there are any other stale entries in run, expunge them
            if (slotToExpunge != staleSlot)
                cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
        }

        public int expungeStaleEntry(int staleSlot) {
            Entry[] tab = table;
            int len = tab.length;

            // expunge entry at staleSlot
            tab[staleSlot].value = null;
            tab[staleSlot] = null;
            size--;

            // Rehash until we encounter null
            Entry e;
            int i;
            for (i = nextIndex(staleSlot, len);
                 (e = tab[i]) != null;
                 i = nextIndex(i, len)) {
                ThreadLocalCopy<?> k = e.get();
                if (k == null) {
                    e.value = null;
                    tab[i] = null;
                    size--;
                } else {
                    int h = k.threadLocalHashCode & (len - 1);
                    if (h != i) {
                        tab[i] = null;

                        // Unlike Knuth 6.4 Algorithm R, we must scan until
                        // null because multiple entries could have been stale.
                        while (tab[h] != null)
                            h = nextIndex(h, len);
                        tab[h] = e;
                    }
                }
            }
            return i;
        }

        public boolean cleanSomeSlots(int i, int n) {
            boolean removed = false;
            Entry[] tab = table;
            int len = tab.length;
            do {
                i = nextIndex(i, len);
                Entry e = tab[i];
                if (e != null && e.get() == null) {
                    n = len;
                    removed = true;
                    i = expungeStaleEntry(i);
                }
            } while ((n >>>= 1) != 0);
            return removed;
        }


        public void rehash() {
            expungeStaleEntries();

            // Use lower threshold for doubling to avoid hysteresis
            if (size >= threshold - threshold / 4)
                resize();
        }

        /**
         * Double the capacity of the table.
         */
        public void resize() {
            Entry[] oldTab = table;
            int oldLen = oldTab.length;
            int newLen = oldLen * 2;
            Entry[] newTab = new Entry[newLen];
            int count = 0;

            for (int j = 0; j < oldLen; ++j) {
                Entry e = oldTab[j];
                if (e != null) {
                    ThreadLocalCopy<?> k = e.get();
                    if (k == null) {
                        e.value = null; // Help the GC
                    } else {
                        int h = k.threadLocalHashCode & (newLen - 1);
                        while (newTab[h] != null)
                            h = nextIndex(h, newLen);
                        newTab[h] = e;
                        count++;
                    }
                }
            }

            setThreshold(newLen);
            size = count;
            table = newTab;
        }


        public void expungeStaleEntries() {
            Entry[] tab = table;
            int len = tab.length;
            for (int j = 0; j < len; j++) {
                Entry e = tab[j];
                if (e != null && e.get() == null)
                    expungeStaleEntry(j);
            }
        }
    }
}
