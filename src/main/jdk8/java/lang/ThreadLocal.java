/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package java.lang;

import java.lang.ref.*;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * This class provides thread-local variables.  These variables differ from
 * their normal counterparts in that each thread that accesses one (via its
 * {@code get} or {@code set} method) has its own, independently initialized
 * copy of the variable.  {@code ThreadLocal} instances are typically private
 * static fields in classes that wish to associate state with a thread (e.g.,
 * a user ID or Transaction ID).
 * <p>
 * 这个类提供了线程局部变量.这个变量不同于他们的所有线程都能访问的变量同行，通过get 或者 set 有他自己的变量
 * 独立于初始化拷贝这个变量 。ThreadLocal 实例区别于私有的静态字段在这个类中，希望去通过线程联系状态，例如用户ID或者事务ID
 *
 * <p>For example, the class below generates unique identifiers local to each
 * thread.
 * 这个类允许操作生成唯一标识符局部 在所有的线程中
 * A thread's id is assigned the first time it invokes {@code ThreadId.get()}
 * and remains unchanged on subsequent calls.
 * 这个线程的id被设计在第一时间执行  ThreadId.get() 并且保持不被改变在后续的操作中
 * <pre>
 * import java.util.concurrent.atomic.AtomicInteger;
 *
 * public class ThreadId {
 *     // Atomic integer containing the next thread ID to be assigned
 *     private static final AtomicInteger nextId = new AtomicInteger(0);
 *
 *     // Thread local variable containing each thread's ID
 *     private static final ThreadLocal&lt;Integer&gt; threadId =
 *         new ThreadLocal&lt;Integer&gt;() {
 *             &#64;Override protected Integer initialValue() {
 *                 return nextId.getAndIncrement();
 *         }
 *     };
 *
 *     // Returns the current thread's unique ID, assigning it if necessary
 *     public static int get() {
 *         return threadId.get();
 *     }
 * }
 * </pre>
 * <p>Each thread holds an implicit reference to its copy of a thread-local
 * variable as long as the thread is alive and the {@code ThreadLocal}
 * instance is accessible; after a thread goes away, all of its copies of
 * thread-local instances are subject to garbage collection (unless other
 * references to these copies exist).
 * <p>
 * 所有的线程持有者都隐式的引用他们局部线程变量的拷贝，在线程活跃和ThreadLocal实例可访问，在线程结束之后，所有的线程局部变量的拷贝实例作为垃圾回收
 * 除非其他引用这个拷贝
 *
 * @author Josh Bloch and Doug Lea
 * @since 1.2
 */


public class ThreadLocal<T> {
    /**
     * ThreadLocals rely on per-thread linear-probe hash maps attached
     * to each thread (Thread.threadLocals and
     * inheritableThreadLocals).  The ThreadLocal objects act as keys,
     * searched via threadLocalHashCode.  This is a custom hash code
     * (useful only within ThreadLocalMaps) that eliminates collisions
     * in the common case where consecutively constructed ThreadLocals
     * are used by the same threads, while remaining well-behaved in
     * less common cases.
     * <p>
     * <p>
     * ThreadLocals 依赖每一个线程hash散列
     * 这个ThreadLocal对象实例作为key,查询通过 threadLocalHashCode
     * 这是一个自定义的hashCode(通常仅用在ThreadLocalMaps) 消除了集合通常的案例 当连续构造ThreadLocals被用在想用的线程，而在不常见情况下仍然保留良好的行为
     */
    private final int threadLocalHashCode = nextHashCode();

    /**
     * The next hash code to be given out. Updated atomically. Starts at
     * zero.
     * 下一个给出去的HashCode,从0开始，自动修改
     */
    private static AtomicInteger nextHashCode =
            new AtomicInteger();

    /**
     * The difference between successively generated hash codes - turns
     * implicit sequential thread-local IDs into near-optimally spread
     * multiplicative hash values for power-of-two-sized tables.
     * 与成功生成的hashCode不同，顺序局部线程id金操作 变得隐式，传播批量hash值使用 2的整次幂
     */
    private static final int HASH_INCREMENT = 0x61c88647;

    /**
     * TODO 为什么要增加一定的偏移量？并且 nextHashCode 还是变动的 ，那么下一次寻找时会有问题吗？
     *
     *
     */
    private static int nextHashCode() {
        return nextHashCode.getAndAdd(HASH_INCREMENT);
    }

    /**
     * Returns the current thread's "initial value" for this
     * thread-local variable.  This method will be invoked the first
     * time a thread accesses the variable with the {@link #get}
     * method, unless the thread previously invoked the {@link #set}
     * method, in which case the {@code initialValue} method will not
     * be invoked for the thread.  Normally, this method is invoked at
     * most once per thread, but it may be invoked again in case of
     * subsequent invocations of {@link #remove} followed by {@link #get}.
     * <p>
     * 返回当前线程 初始化值 作为这个局部线程的变量
     * 这个方法将会被执行在第一次这个线程访问这个变量通过 get 方法
     * 除非这个线程提供方法 set,这种情况下 initialValue 方法将不会被执行在这个线程中
     * 通常，这个方法被执行在许多一次性线程，但是它也许会被再次执行 当顺序执行 remove 后面接着执行get
     *
     * <p>This implementation simply returns {@code null}; if the
     * programmer desires thread-local variables to have an initial
     * value other than {@code null}, {@code ThreadLocal} must be
     * subclassed, and this method overridden.  Typically, an
     * anonymous inner class will be used.
     * <p>
     * 这个实现简单返回null
     * 如果程序员期望 线程局部变量有一个初始值而不是null,ThreadLocal必须被子类实现并且覆盖这个方法
     * 通常，将使用一个匿名内部类
     *
     * @return the initial value for this thread-local
     */
    protected T initialValue() {
        return null;
    }

    /**
     * Creates a thread local variable. The initial value of the variable is
     * determined by invoking the {@code get} method on the {@code Supplier}.
     * <p>
     * 创建一个局部变量线程，这个初始化值被确定功过执行 get方法在 Supplier 中
     *
     * @param <S>      the type of the thread local's value
     * @param supplier the supplier to be used to determine the initial value
     * @return a new thread local variable
     * @throws NullPointerException if the specified supplier is null
     * @since 1.8
     */
    public static <S> ThreadLocal<S> withInitial(Supplier<? extends S> supplier) {
        return new SuppliedThreadLocal<>(supplier);
    }

    /**
     * Creates a thread local variable.
     *
     * @see #withInitial(java.util.function.Supplier)
     */
    public ThreadLocal() {
    }

    /**
     * Returns the value in the current thread's copy of this
     * thread-local variable.  If the variable has no value for the
     * current thread, it is first initialized to the value returned
     * by an invocation of the {@link #initialValue} method.
     * <p>
     * 返回当前线程拷贝的局部变量值，如果这个变量在当前线程没有值，它是第一次初始化这个值，将执行initialValue方法的结果返回
     *
     * @return the current thread's value of this thread-local
     */
    public T get() {
        // 获取当前线程信息
        Thread t = Thread.currentThread();
        /**
         * 获取线程本身存储的 ThreadLocalMap 信息
         */
        ThreadLocalMap map = getMap(t);
        if (map != null) {
            ThreadLocalMap.Entry e = map.getEntry(this);
            if (e != null) {
                @SuppressWarnings("unchecked")
                T result = (T) e.value;
                return result;
            }
        }
        // 如果map为null（表示没有赋值）
        return setInitialValue();
    }

    /**
     * Variant of set() to establish initialValue. Used instead
     * of set() in case user has overridden the set() method.
     * <p>
     * set 的变体 建立初始化值
     * 被用在代替set方法，用在用户覆盖了set方法
     *
     * @return the initial value
     */
    private T setInitialValue() {
        // 调用子类覆盖方法
        T value = initialValue();
        Thread t = Thread.currentThread();
        // 获取线程锁持有的 Map 集合
        ThreadLocalMap map = getMap(t);
        if (map != null)
            map.set(this, value);
        else
            createMap(t, value);
        return value;
    }

    /**
     * Sets the current thread's copy of this thread-local variable
     * to the specified value.  Most subclasses will have no need to
     * override this method, relying solely on the {@link #initialValue}
     * method to set the values of thread-locals.
     * <p>
     * 这只当前线程的局部变量拷贝值 通过执行的值
     * 大多数子类不需要去覆盖这个方法
     * 仅仅依赖 initialValue 方法设置局部线程变量的值
     *
     * @param value the value to be stored in the current thread's copy of
     *              this thread-local.
     */
    public void set(T value) {
        Thread t = Thread.currentThread();
        ThreadLocalMap map = getMap(t);
        if (map != null)
            map.set(this, value);
        else
            createMap(t, value);
    }

    /**
     * Removes the current thread's value for this thread-local
     * variable.  If this thread-local variable is subsequently
     * {@linkplain #get read} by the current thread, its value will be
     * reinitialized by invoking its {@link #initialValue} method,
     * unless its value is {@linkplain #set set} by the current thread
     * in the interim.  This may result in multiple invocations of the
     * {@code initialValue} method in the current thread.
     * <p>
     * 移除当前线程的局部变量值，如果当前线程顺序执行get，它的值将重新被初始化通过执行initialValue方法
     * 除非这个值是当前线程临时通过set方法设置
     * 这也许造成多个线程执行这个initialValue 方法
     *
     * @since 1.5
     */
    public void remove() {
        ThreadLocalMap m = getMap(Thread.currentThread());
        if (m != null)
            m.remove(this);
    }

    /**
     * Get the map associated with a ThreadLocal. Overridden in
     * InheritableThreadLocal.
     * <p>
     * 获取 ThreadLocal  的相关的map，覆盖InheritableThreadLocal
     *
     * @param t the current thread
     * @return the map
     */
    ThreadLocalMap getMap(Thread t) {
        return t.threadLocals;
    }

    /**
     * Create the map associated with a ThreadLocal. Overridden in
     * InheritableThreadLocal.
     * 创建一个相关的map,覆盖InheritableThreadLocal
     *
     * @param t          the current thread
     * @param firstValue value for the initial entry of the map
     */
    void createMap(Thread t, T firstValue) {
        // 该Map存储在所属线程中，并不是 ThreadLocal 记性管理
        // ThreadLocal 并没有持有 <线程，实例>的对象信息，该对象信息是在线程中自己持有
        t.threadLocals = new ThreadLocalMap(this, firstValue);
    }

    /**
     * Factory method to create map of inherited thread locals.
     * Designed to be called only from Thread constructor.
     * 工厂方法创建一个 继承了 局部线程的map.被设计为仅仅能通过 Thread 构造函数调用
     *
     * @param parentMap the map associated with parent thread
     * @return a map containing the parent's inheritable bindings
     */
    static ThreadLocalMap createInheritedMap(ThreadLocalMap parentMap) {
        return new ThreadLocalMap(parentMap);
    }

    /**
     * Method childValue is visibly defined in subclass
     * InheritableThreadLocal, but is internally defined here for the
     * sake of providing createInheritedMap factory method without
     * needing to subclass the map class in InheritableThreadLocal.
     * This technique is preferable to the alternative of embedding
     * instanceof tests in methods.
     */
    T childValue(T parentValue) {
        throw new UnsupportedOperationException();
    }

    /**
     * An extension of ThreadLocal that obtains its initial value from
     * the specified {@code Supplier}.
     */
    static final class SuppliedThreadLocal<T> extends ThreadLocal<T> {

        private final Supplier<? extends T> supplier;

        SuppliedThreadLocal(Supplier<? extends T> supplier) {
            this.supplier = Objects.requireNonNull(supplier);
        }

        @Override
        protected T initialValue() {
            return supplier.get();
        }
    }

    /**
     * ThreadLocalMap is a customized hash map suitable only for
     * maintaining thread local values. No operations are exported
     * outside of the ThreadLocal class. The class is package private to
     * allow declaration of fields in class Thread.  To help deal with
     * very large and long-lived usages, the hash table entries use
     * WeakReferences for keys. However, since reference queues are not
     * used, stale entries are guaranteed to be removed only when
     * the table starts running out of space.
     * <p>
     * <p>
     * ThreadLocalMap 是一个自定义的hashMap,只适用于维护局部线程只
     * 没有操作被暴露在外面的ThreadLocal类
     * 这个类的包私有允许在类线程中声明字段
     * 帮助处理非常大的和长时间存活的用法
     * 这个hashTable实例用 WeakReferences 作为key,
     * 所以，当引用的table没有空间时，将会移除旧的table
     * 然而，所以引用队列不被引用，stale实例被
     * 保证能够移除仅仅当这个 表运行超出空间
     */
    static class ThreadLocalMap {

        /**
         * The entries in this hash map extend WeakReference, using
         * its main ref field as the key (which is always a
         * ThreadLocal object).  Note that null keys (i.e. entry.get()
         * == null) mean that the key is no longer referenced, so the
         * entry can be expunged from table.  Such entries are referred to
         * as "stale entries" in the code that follows.
         * <p>
         * 是个实例在这个hashMao中继承 WeakReference
         * 使用它意味着引用字段作为key(经常作为ThreadLocal对象)
         * null 的key意味着这个key不能长时间引用，所以这个实体能够被从table中删除
         * 这样的实体被称为 stale entries ，在下列的代码中
         *
         * 将 ThreadLocal 对象的生命周期和线程生命周期解绑
         * 对持有 ThreadLocal 的弱引用，可以使得 ThreadLocal 在没有其他强引用的时候被回收掉
         * 这个避免因为线程得不到销毁导致ThreadLocal 对象无法被回收
         *
         */
        static class Entry extends WeakReference<ThreadLocal<?>> {
            /**
             * The value associated with this ThreadLocal.
             */
            Object value;

            Entry(ThreadLocal<?> k, Object v) {
                super(k);
                value = v;
            }
        }

        /**
         * The initial capacity -- MUST be a power of two.
         * 初始化容积，必须是2的整次幂
         */
        private static final int INITIAL_CAPACITY = 16;

        /**
         * The table, resized as necessary.
         * table.length MUST always be a power of two.
         * <p>
         * 必要时重新调整，长度是2的整次幂
         */
        private Entry[] table;

        /**
         * The number of entries in the table.
         * 实例在table中的位置
         */
        private int size = 0;

        /**
         * The next size value at which to resize.
         * 哪一个需要调整的长度
         */
        private int threshold; // Default to 0

        /**
         * Set the resize threshold to maintain at worst a 2/3 load factor.
         * <p>
         * 负荷因子 2/3 0.666666
         */
        private void setThreshold(int len) {
            threshold = len * 2 / 3;
        }

        /**
         * Increment i modulo len.
         * 自增下一个的下一个角标
         */
        private static int nextIndex(int i, int len) {
            return ((i + 1 < len) ? i + 1 : 0);
        }

        /**
         * Decrement i modulo len.
         * 上一个角标
         */
        private static int prevIndex(int i, int len) {
            return ((i - 1 >= 0) ? i - 1 : len - 1);
        }

        /**
         * Construct a new map initially containing (firstKey, firstValue).
         * ThreadLocalMaps are constructed lazily, so we only create
         * one when we have at least one entry to put in it.
         * <p>
         * 构造一个新的Map包含第一个key,第一个值
         * ThreadLocalMaps 懒加载被创建，所以我们仅仅创建一个当我们有至少一个实体添加进该集合中
         */
        ThreadLocalMap(ThreadLocal<?> firstKey, Object firstValue) {
            table = new Entry[INITIAL_CAPACITY];
            // 获取要插入的位置
            int i = firstKey.threadLocalHashCode & (INITIAL_CAPACITY - 1);
            // 创造节点信息进行插入操作
            table[i] = new Entry(firstKey, firstValue);
            size = 1;
            // 设置下一次需要扩容时达到的数量
            setThreshold(INITIAL_CAPACITY);
        }

        /**
         * Construct a new map including all Inheritable ThreadLocals
         * from given parent map. Called only by createInheritedMap.
         * 构造一个新的map 包含所有的 Inheritable ThreadLocals 从给定的map中
         * 仅仅在创建 createInheritedMap 时调用
         *
         * @param parentMap the map associated with parent thread.
         */
        private ThreadLocalMap(ThreadLocalMap parentMap) {
            Entry[] parentTable = parentMap.table;
            int len = parentTable.length;
            setThreshold(len);
            table = new Entry[len];

            for (int j = 0; j < len; j++) {
                Entry e = parentTable[j];
                if (e != null) {
                    @SuppressWarnings("unchecked")
                    ThreadLocal<Object> key = (ThreadLocal<Object>) e.get();
                    if (key != null) {
                        Object value = key.childValue(e.value);
                        Entry c = new Entry(key, value);
                        int h = key.threadLocalHashCode & (len - 1);
                        while (table[h] != null)
                            h = nextIndex(h, len);
                        table[h] = c;
                        size++;
                    }
                }
            }
        }

        /**
         * Get the entry associated with key.  This method
         * itself handles only the fast path: a direct hit of existing
         * key. It otherwise relays to getEntryAfterMiss.  This is
         * designed to maximize performance for direct hits, in part
         * by making this method readily inlinable.
         * <p>
         * 获取一个相关key的实例，这个方法自身持有快捷路径：直接命中存在的key
         * 否则依赖 getEntryAfterMiss
         * 这个设计为了最大性能去直接命中，部分原因是这种方法易于内联
         *
         * @param key the thread local object
         * @return the entry associated with key, or null if no such
         */
        private Entry getEntry(ThreadLocal<?> key) {
            int i = key.threadLocalHashCode & (table.length - 1);
            Entry e = table[i];
            if (e != null && e.get() == key)
                return e;
            else
                return getEntryAfterMiss(key, i, e);
        }

        /**
         * Version of getEntry method for use when key is not found in
         * its direct hash slot.
         * <p>
         * 获取实体方法的版本，用于当使用key直接hash点命中时不存在
         *
         * @param key the thread local object
         * @param i   the table index for key's hash code
         * @param e   the entry at table[i]
         * @return the entry associated with key, or null if no such
         */
        private Entry getEntryAfterMiss(ThreadLocal<?> key, int i, Entry e) {
            Entry[] tab = table;
            int len = tab.length;

            while (e != null) {
                ThreadLocal<?> k = e.get();
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

        /**
         * Set the value associated with key.
         * 设置一个key相关联的值
         *
         * @param key   the thread local object
         * @param value the value to be set
         */
        private void set(ThreadLocal<?> key, Object value) {

            // We don't use a fast path as with get() because it is at
            // least as common to use set() to create new entries as
            // it is to replace existing ones, in which case, a fast
            // path would fail more often than not.

            /**
             * 不使用快速的路径使用get(),因为它在最后通常使用set会创建一个新的实体作为它替换已存在的旧值
             * 在这种情况下，一个快速的路径是失败比不存在更长发生
             */

            Entry[] tab = table;
            int len = tab.length;
            // 获取对应的角标信息 （TODO hash发生了偏移，随时在改变）
            int i = key.threadLocalHashCode & (len - 1);

            for (Entry e = tab[i];
                // 位置上有节点，角标位置向后移动一个单位
                // TODO 但是将后一个单位的值赋值给e是做什么？ 要是后一个单位的e也不是空的怎么处理？
                 e != null;
                 e = tab[i = nextIndex(i, len)]) {

                // 获取对象的引用
                ThreadLocal<?> k = e.get();

                //如果当前对象的引用和下一次对象的应用是同一个，进行替换
                if (k == key) {
                    e.value = value;
                    return;
                }

                // k == null 说明下一个位置的没有元素，将信息进行存储
                if (k == null) {
                    replaceStaleEntry(key, value, i);
                    return;
                }

                // 此时k不为null,继续进行下一次的循环
            }

            tab[i] = new Entry(key, value);
            int sz = ++size;
            if (!cleanSomeSlots(i, sz) && sz >= threshold)
                rehash();
        }

        /**
         * Remove the entry for key.
         * 移除这个key的实体值
         */
        private void remove(ThreadLocal<?> key) {
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

        /**
         * Replace a stale entry encountered during a set operation
         * with an entry for the specified key.  The value passed in
         * the value parameter is stored in the entry, whether or not
         * an entry already exists for the specified key.
         * <p>
         * 替换旧的实体，根据一个指定key的值进行设置操作
         * 这个值通过一个值的参数存储进这个实体，无论这个实体是否已经存在于指定的key中
         * <p>
         * As a side effect, this method expunges all stale entries in the
         * "run" containing the stale entry.  (A run is a sequence of entries
         * between two null slots.)
         * <p>
         * 另外一个影响，这个方法清除素有的旧数据 在 包含旧的实体
         * 一个 run 是在实体中是顺序的在两个null槽
         *
         * @param key       the key
         * @param value     the value to be associated with key
         * @param staleSlot index of the first stale entry encountered while
         *                  searching for key.
         *                  <p>
         *                  一个线程可能拥有多个ThreadLocal的局部线程变量
         *                  由于维护 ThreadLocal 与 其对应值的信息是在线程持有的数据
         *                  因此线程 会将 ThreadLocal 实体作为key存储在自身的Map中
         */
        private void replaceStaleEntry(ThreadLocal<?> key, Object value,
                                       int staleSlot) {
            Entry[] tab = table;
            int len = tab.length;
            Entry e;

            // Back up to check for prior stale entry in current run.
            // We clean out whole runs at a time to avoid continual
            // incremental rehashing due to garbage collector freeing
            // up refs in bunches (i.e., whenever the collector runs).

            /**
             * 备份以检查当前运行中的旧条目。//由于垃圾收集器释放了堆中的引用(即当收集器运行时)，我们一次清理整个运行，以避免连续的增量重散列
             *
             * 回溯检查前一个旧的实例在当前运行中的信息
             * 回溯检查前一个旧值实例在当前运行
             * 清除 运行着在时间避免继续增加rehashing 根据垃圾收集，释放引用
             *
             * 无论垃圾收集器是否运行
             */
            //删除的槽位

            int slotToExpunge = staleSlot;
            //找到前一个槽位
            /****
             *
             *   2 | 2 | 2 | 0 | 0 | 0 | 0 | 3
             *
             *   假设 0 表示持有的引用被置为null
             *   现在 staleSlot 是3所在的位置
             *   那么它会向回进行回溯，一直找到第一个0出现的位置
             */
            for (int i = prevIndex(staleSlot, len); (e = tab[i]) != null; i = prevIndex(i, len)) {
                if (e.get() == null) {
                    slotToExpunge = i;
                }
            }


            // Find either the key or trailing null slot of run, whichever
            // occurs first
            /**
             * 查找这个key是否是 落后于 null槽点
             * 发生在第一次？
             */

            /**
             * 遍历后面的槽点，直到遇到第一个非空的槽点
             */
            for (int i = nextIndex(staleSlot, len);(e = tab[i]) != null; i = nextIndex(i, len)) {

                ThreadLocal<?> k = e.get();

                // If we find key, then we need to swap it
                // with the stale entry to maintain hash table order.
                // The newly stale slot, or any other stale slot
                // encountered above it, can then be sent to expungeStaleEntry
                // to remove or rehash all of the other entries in run.
                /**
                 * 如果发现这个key,我们需要替换它使用失效的值去维护hash的顺序
                 * 新实现的槽位，无论其他的失效槽位遇上它，能够随后发送 删除过的条目去移除或者 rehash 所有其他的实例
                 */
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

        /**
         * Expunge a stale entry by rehashing any possibly colliding entries
         * lying between staleSlot and the next null slot.  This also expunges
         * any other stale entries encountered before the trailing null.  See
         * Knuth, Section 6.4
         * <p>
         * 清除一个旧数据通过 rehashing 任何可能的实体碰撞，在旧实体槽和下一个null槽
         * 这也清除任何其他的旧实体在遇到遍历空之前
         *
         * @param staleSlot index of slot known to have null key
         * @return the index of the next null slot after staleSlot
         * (all between staleSlot and this slot will have been checked
         * for expunging).
         */
        private int expungeStaleEntry(int staleSlot) {
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
                ThreadLocal<?> k = e.get();
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

        /**
         * Heuristically scan some cells looking for stale entries.
         * This is invoked when either a new element is added, or
         * another stale one has been expunged. It performs a
         * logarithmic number of scans, as a balance between no
         * scanning (fast but retains garbage) and a number of scans
         * proportional to number of elements, that would find all
         * garbage but would cause some insertions to take O(n) time.
         * <p>
         * 一些扫描旧实体的单元
         * 这个执行当一个新的元素添加或者一个旧的实体被清除
         * 它执行扫描的次数为对数，作为一个不扫描（快但是包含垃圾）和 扫描元素数量成比例的次数之间的一种平衡
         * 可以发现所有的垃圾，但是会造成插入操作执行 O(n)的时间
         *
         * @param i a position known NOT to hold a stale entry. The
         *          scan starts at the element after i.
         *          一个位置已知 没有持有旧的值，这个扫描开始在这个元素后
         * @param n scan control: {@code log2(n)} cells are scanned,
         *          unless a stale entry is found, in which case
         *          {@code log2(table.length)-1} additional cells are scanned.
         *          When called from insertions, this parameter is the number
         *          of elements, but when from replaceStaleEntry, it is the
         *          table length. (Note: all this could be changed to be either
         *          more or less aggressive by weighting n instead of just
         *          using straight log n. But this version is simple, fast, and
         *          seems to work well.)
         *          <p>
         *          log2(n) 单元被扫描，除非这个旧值被发现，这种情况下 log2(table.length)-1 额外的单元被扫描
         *          当执行插入调用时，这个参数是元素位置的值，但是当replaceStaleEntry调用时，这个参数是table的长度
         *          所有的这些都可以被改变，而不是通过n或者使用加权log n
         *          但是这个版本比较简单，快速，并且看起来工作的很好
         * @return true if any stale entries have been removed.
         * 如果任何旧值被删除返回true
         */
        private boolean cleanSomeSlots(int i, int n) {
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

        /**
         * Re-pack and/or re-size the table. First scan the entire
         * table removing stale entries. If this doesn't sufficiently
         * shrink the size of the table, double the table size.
         */
        private void rehash() {
            expungeStaleEntries();

            // Use lower threshold for doubling to avoid hysteresis
            if (size >= threshold - threshold / 4)
                resize();
        }

        /**
         * Double the capacity of the table.
         */
        private void resize() {
            Entry[] oldTab = table;
            int oldLen = oldTab.length;
            int newLen = oldLen * 2;
            Entry[] newTab = new Entry[newLen];
            int count = 0;

            for (int j = 0; j < oldLen; ++j) {
                Entry e = oldTab[j];
                if (e != null) {
                    ThreadLocal<?> k = e.get();
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

        /**
         * Expunge all stale entries in the table.
         */
        private void expungeStaleEntries() {
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





