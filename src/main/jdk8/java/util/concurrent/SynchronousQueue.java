/*
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

/*
 *
 *
 *
 *
 *
 * Written by Doug Lea, Bill Scherer, and Michael Scott with
 * assistance from members of JCP JSR-166 Expert Group and released to
 * the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.*;
import java.util.Spliterator;
import java.util.Spliterators;

/**
 * A {@linkplain BlockingQueue blocking queue} in which each insert
 * operation must wait for a corresponding remove operation by another
 * thread, and vice versa.  A synchronous queue does not have any
 * internal capacity, not even a capacity of one.  You cannot
 * {@code peek} at a synchronous queue because an element is only
 * present when you try to remove it; you cannot insert an element
 * (using any method) unless another thread is trying to remove it;
 * you cannot iterate as there is nothing to iterate.  The
 * <em>head</em> of the queue is the element that the first queued
 * inserting thread is trying to add to the queue; if there is no such
 * queued thread then no element is available for removal and
 * {@code poll()} will return {@code null}.  For purposes of other
 * {@code Collection} methods (for example {@code contains}), a
 * {@code SynchronousQueue} acts as an empty collection.  This queue
 * does not permit {@code null} elements.
 * <p>
 * 所有的插入操作都需要等待相应的移除操作通过另一个线程，反之亦然
 * 一个同步队列没有任何内部容量，甚至容量就是1
 * 不能 peek 操作在 同步队列，因为一个元素只能仅仅存在当你尝试移除它的时候
 * 不能插入一个元素除非另外一个线程尝试移除它
 * 不能使用迭代它使用迭代器
 * 队列的头是一个元素 第一个排队插入线程插入在队列中。如果没有其他线程，然后没有任何元素可以被使用reoval 和 poll,将会返回null
 * 为了另外集合方法的目的，一个同步队列实际是一个空集合，这个队列没有许可任何空元素
 *
 * <p>Synchronous queues are similar to rendezvous channels used in
 * CSP and Ada. They are well suited for handoff designs, in which an
 * object running in one thread must sync up with an object running
 * in another thread in order to hand it some information, event, or
 * task.
 * <p>
 * 同步队列是一个类似会和管道，被用在CSP和ADA，他们非常适合切换设计，当一个元素传递到另外一个线程必须同步使用对象传递到另外一个线程为了顺序去处理它一些信息，事件或者任务
 *
 * <p>This class supports an optional fairness policy for ordering
 * waiting producer and consumer threads.  By default, this ordering
 * is not guaranteed. However, a queue constructed with fairness set
 * to {@code true} grants threads access in FIFO order.
 * 支持公平策略操作为了顺序等待消费者和生产者线程，默认的，这个顺序不是可以i保证的。当然一个队列构造器使用公平设置true帮助线程先进先出的顺序
 *
 * <p>This class and its iterator implement all of the
 * <em>optional</em> methods of the {@link Collection} and {@link
 * Iterator} interfaces.
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @param <E> the type of elements held in this collection
 * @author Doug Lea and Bill Scherer and Michael Scott
 * @since 1.5
 */
public class SynchronousQueue<E> extends AbstractQueue<E>
        implements BlockingQueue<E>, java.io.Serializable {
    private static final long serialVersionUID = -3223113410248163686L;

    /*
     * This class implements extensions of the dual stack and dual
     * queue algorithms described in "Nonblocking Concurrent Objects
     * with Condition Synchronization", by W. N. Scherer III and
     * M. L. Scott.  18th Annual Conf. on Distributed Computing,
     * Oct. 2004 (see also
     * http://www.cs.rochester.edu/u/scott/synchronization/pseudocode/duals.html).
     * The (Lifo) stack is used for non-fair mode, and the (Fifo)
     * queue for fair mode. The performance of the two is generally
     * similar. Fifo usually supports higher throughput under
     * contention but Lifo maintains higher thread locality in common
     * applications.
     *
     * A dual queue (and similarly stack) is one that at any given
     * time either holds "data" -- items provided by put operations,
     * or "requests" -- slots representing take operations, or is
     * empty. A call to "fulfill" (i.e., a call requesting an item
     * from a queue holding data or vice versa) dequeues a
     * complementary node.  The most interesting feature of these
     * queues is that any operation can figure out which mode the
     * queue is in, and act accordingly without needing locks.
     *
     * Both the queue and stack extend abstract class Transferer
     * defining the single method transfer that does a put or a
     * take. These are unified into a single method because in dual
     * data structures, the put and take operations are symmetrical,
     * so nearly all code can be combined. The resulting transfer
     * methods are on the long side, but are easier to follow than
     * they would be if broken up into nearly-duplicated parts.
     *
     * The queue and stack data structures share many conceptual
     * similarities but very few concrete details. For simplicity,
     * they are kept distinct so that they can later evolve
     * separately.
     *
     * The algorithms here differ from the versions in the above paper
     * in extending them for use in synchronous queues, as well as
     * dealing with cancellation. The main differences include:
     *
     *  1. The original algorithms used bit-marked pointers, but
     *     the ones here use mode bits in nodes, leading to a number
     *     of further adaptations.
     *  2. SynchronousQueues must block threads waiting to become
     *     fulfilled.
     *  3. Support for cancellation via timeout and interrupts,
     *     including cleaning out cancelled nodes/threads
     *     from lists to avoid garbage retention and memory depletion.
     *
     * Blocking is mainly accomplished using LockSupport park/unpark,
     * except that nodes that appear to be the next ones to become
     * fulfilled first spin a bit (on multiprocessors only). On very
     * busy synchronous queues, spinning can dramatically improve
     * throughput. And on less busy ones, the amount of spinning is
     * small enough not to be noticeable.
     *
     * Cleaning is done in different ways in queues vs stacks.  For
     * queues, we can almost always remove a node immediately in O(1)
     * time (modulo retries for consistency checks) when it is
     * cancelled. But if it may be pinned as the current tail, it must
     * wait until some subsequent cancellation. For stacks, we need a
     * potentially O(n) traversal to be sure that we can remove the
     * node, but this can run concurrently with other threads
     * accessing the stack.
     *
     * While garbage collection takes care of most node reclamation
     * issues that otherwise complicate nonblocking algorithms, care
     * is taken to "forget" references to data, other nodes, and
     * threads that might be held on to long-term by blocked
     * threads. In cases where setting to null would otherwise
     * conflict with main algorithms, this is done by changing a
     * node's link to now point to the node itself. This doesn't arise
     * much for Stack nodes (because blocked threads do not hang on to
     * old head pointers), but references in Queue nodes must be
     * aggressively forgotten to avoid reachability of everything any
     * node has ever referred to since arrival.
     */

    /**
     * Shared internal API for dual stacks and queues.
     */
    abstract static class Transferer<E> {
        /**
         * Performs a put or take.
         *
         * @param e     if non-null, the item to be handed to a consumer;
         *              if null, requests that transfer return an item
         *              offered by producer.
         * @param timed if this operation should timeout
         * @param nanos the timeout, in nanoseconds
         * @return if non-null, the item provided or received; if null,
         * the operation failed due to timeout or interrupt --
         * the caller can distinguish which of these occurred
         * by checking Thread.interrupted.
         */
        abstract E transfer(E e, boolean timed, long nanos);
    }

    /**
     * The number of CPUs, for spin control
     */
    static final int NCPUS = Runtime.getRuntime().availableProcessors();

    /**
     * The number of times to spin before blocking in timed waits.
     * The value is empirically derived -- it works well across a
     * variety of processors and OSes. Empirically, the best value
     * seems not to vary with number of CPUs (beyond 2) so is just
     * a constant.
     * <p>
     * <p>
     * 自旋的次数在阻塞到时间的等待者
     * 这个值是经验的设计，它工作的很好通过各种处理器和操作系统
     * 经验，这个最好的值似乎 和CPU数量有些不同 因此尽是一个常量
     */
    static final int maxTimedSpins = (NCPUS < 2) ? 0 : 32;

    /**
     * The number of times to spin before blocking in untimed waits.
     * This is greater than timed value because untimed waits spin
     * faster since they don't need to check times on each spin.
     * 自旋的次数在阻塞之前在未到达时间的等待
     * 这个值比 超时的值大，因为未超时等待自旋快，因为他们不需要检查次数在自旋中
     */
    static final int maxUntimedSpins = maxTimedSpins * 16;

    /**
     * the number of nanoseconds for which it is faster to spin
     * rather than to use timed park. a rough estimate suffices.
     */
    static final long spinForTimeoutThreshold = 1000L;


    /**
     * The transferer. Set only in constructor, but cannot be declared
     * as final without further complicating serialization.  Since
     * this is accessed only at most once per public method, there
     * isn't a noticeable performance penalty for using volatile
     * instead of final here.
     */
    private transient volatile Transferer<E> transferer;

    /**
     * Creates a {@code SynchronousQueue} with nonfair access policy.
     */
    public SynchronousQueue() {
        this(false);
    }

    /**
     * Creates a {@code SynchronousQueue} with the specified fairness policy.
     *
     * @param fair if true, waiting threads contend in FIFO order for
     *             access; otherwise the order is unspecified.
     */
    public SynchronousQueue(boolean fair) {
        transferer = fair ? new TransferQueue<E>() : new TransferStack<E>();
    }


    /**
     * Dual stack
     */
    static final class TransferStack<E> extends Transferer<E> {
        /*
         * This extends Scherer-Scott dual stack algorithm, differing,
         * among other ways, by using "covering" nodes rather than
         * bit-marked pointers: Fulfilling operations push on marker
         * nodes (with FULFILLING bit set in mode) to reserve a spot
         * to match a waiting node.
         */

        /* Modes for SNodes, ORed together in node fields */
        /**
         * Node represents an unfulfilled consumer
         * 消费者模式
         */
        static final int REQUEST = 0;
        /**
         * Node represents an unfulfilled producer
         * 生产者模式
         */
        static final int DATA = 1;
        /**
         * Node is fulfilling another unfulfilled DATA or REQUEST
         * 互补模式
         */
        static final int FULFILLING = 2;


        /**
         * The head (top) of the stack
         */
        volatile SNode head;


        /**
         * Returns true if m has fulfilling bit set.
         * 判断节点是否是互补模式
         * TODO TMD为什么不使用 m == FULFILLING 来判断？
         *
         * m有四种状态
         * 00  消费者
         * 01  生产者
         * 10  底部生产者，顶部消费者
         * 11  底部消费者，顶部生产者
         *
         * 其中
         * 00 & 10 = 00
         * 01 & 10 = 00
         * 这两个说明其顶部只有一个元素或者有多个相同操作的元素
         *
         * 10 & 10 = 10
         * 11 & 10 = 10
         */
        static boolean isFulfilling(int m) {
            System.out.println("m=" + m + ",m & FULFILLING = " + (m & FULFILLING));
            return (m & FULFILLING) != 0;
        }

        /**
         * Node class for TransferStacks.
         */
        static final class SNode {
            //栈的下一个元素，被当前栈压在下面的元素
            volatile SNode next;        // next node in stack
            //节点匹配，用来判断阻塞栈元素能被唤醒的时机
            //
            volatile SNode match;       // the node matched to this
            //栈元素的阻塞是通过线程阻塞来实现的，waiter为阻塞线程
            //控制park,unpark线程
            volatile Thread waiter;     // to control park/unpark
            //未投递的消息或者未消费的消息
            Object item;                // data; or null for REQUESTs
            int mode;
            // Note: item and mode fields don't need to be volatile
            // since they are always written before, and read after,
            // other volatile/atomic operations.


            SNode(Object item) {
                this.item = item;
            }

            boolean casNext(SNode cmp, SNode val) {
                return cmp == next &&
                        UNSAFE.compareAndSwapObject(this, nextOffset, cmp, val);
            }

            /**
             * Tries to match node s to this node, if so, waking up thread.
             * Fulfillers call tryMatch to identify their waiters.
             * Waiters block until they have been matched.
             * 尝试匹配节点，如果存在匹配节点则判断是否是当前节点
             *
             * @param s the node to match
             * @return true if successfully matched to s
             */
            boolean tryMatch(SNode s) {
                //如果没有匹配节点，那么就通过CAS设置匹配节点，设置成功之后，将匹配节点对应的线程唤醒
                if (match == null &&
                        UNSAFE.compareAndSwapObject(this, matchOffset, null, s)) {
                    Thread w = waiter;
                    if (w != null) {    // waiters need at most one unpark
                        waiter = null;
                        LockSupport.unpark(w);
                    }
                    return true;
                }
                //存过存在匹配节点，判断其是否与传入的节点信息一致
                return match == s;
            }

            /**
             * Tries to cancel a wait by matching node to itself.
             * 取消一个等待匹配的节点，将当前节点的match节点设置为当前节点
             */
            void tryCancel() {
                UNSAFE.compareAndSwapObject(this, matchOffset, null, this);
            }

            /**
             * 判断match节点是不是等于当前节点
             *
             * @return
             */
            boolean isCancelled() {
                return match == this;
            }

            // Unsafe mechanics
            private static final sun.misc.Unsafe UNSAFE;
            private static final long matchOffset;
            private static final long nextOffset;


            static {
                try {
                    UNSAFE = sun.misc.Unsafe.getUnsafe();
//                    UNSAFE = Util.getUnsafeInstance();
                    Class<?> k = SNode.class;
                    matchOffset = UNSAFE.objectFieldOffset
                            (k.getDeclaredField("match"));
                    nextOffset = UNSAFE.objectFieldOffset
                            (k.getDeclaredField("next"));
                } catch (Exception e) {
                    throw new Error(e);
                }
            }
        }


        //替换当前头节点
        boolean casHead(SNode h, SNode nh) {
            return h == head &&
                    UNSAFE.compareAndSwapObject(this, headOffset, h, nh);
        }

        /**
         * Creates or resets fields of a node. Called only from transfer
         * where the node to push on stack is lazily created and
         * reused when possible to help reduce intervals between reads
         * and CASes of head and to avoid surges of garbage when CASes
         * to push nodes fail due to contention.
         * <p>
         * 创建或者重置一个节点的字段，被调用只有在转换节点添加进栈中是懒加载创建
         * 并且重用当可能帮助减少时间间隔在读和CAS设置头 并且去避免激增的垃圾当CAS设置 去添加节点失败由于争用
         */
        static SNode snode(SNode s, Object e, SNode next, int mode) {
            if (s == null) {
                s = new SNode(e);
            }
            s.mode = mode;
            s.next = next;
            return s;
        }

        /**
         * @param e     if non-null, the item to be handed to a consumer;
         *              if null, requests that transfer return an item
         *              offered by producer.
         * @param timed if this operation should timeout 是否需要超时控制，false不需要
         * @param nanos the timeout, in nanoseconds 超时时间，单位ns
         * @return
         */
        @SuppressWarnings("unchecked")
        E transfer(E e, boolean timed, long nanos) {
            /*
             * Basic algorithm is to loop trying one of three actions:
             *
             * 1. If apparently empty or already containing nodes of same
             *    mode, try to push node on stack and wait for a match,
             *    returning it, or null if cancelled.
             *
             * 如果显然是空的或者已经包含节点在mode中，尝试添加节点到栈并且等待匹配，返回它，或者null或者取消
             *
             * 2. If apparently containing node of complementary mode,
             *    try to push a fulfilling node on to stack, match
             *    with corresponding waiting node, pop both from
             *    stack, and return matched item. The matching or
             *    unlinking might not actually be necessary because of
             *    other threads performing action 3:
             *
             * 如果显然的包含元素是互补的mode,尝试添加一个充实的元素到栈，和相应的等待节点匹配
             * pop 也从栈中取出元素，并且返回匹配的节点。这个匹配或者unliking 也不不会变得必须操作
             * 因为其他线程程序会执行3
             *
             * 3. If top of stack already holds another fulfilling node,
             *    help it out by doing its match and/or pop
             *    operations, and then continue. The code for helping
             *    is essentially the same as for fulfilling, except
             *    that it doesn't return the item.
             * 如果栈的顶部元素已经拥有其他填充满了的节点，帮助它出去通过做一些匹配或者 pop操作
             * 然后继续，这个code帮助从本质上填充，期待它不反回他们
             */

            SNode s = null; // constructed/reused as needed
            // e为null 说明是take不是put

            // 0代表消费者，1 代表生产者
            int mode = (e == null) ? REQUEST : DATA;

            for (; ; ) {
                //获取头节点
                SNode h = head;
                //头节点为空，说明头节点没有数据
                //头节点不为空 && 是take类型，说明头节点线程正在等待拿数据
                //头节点不为空且是put类型，说明头节点正在等待放数据
                //栈头指针为空或者与表头模式相同,说明是两个相同操作
                if (h == null || h.mode == mode) {  // empty or same-mode
                    //设置了超时时间，并且e进栈或者出栈要超时，丢弃本次操作
                    if (timed && nanos <= 0) {      // can't wait
                        //栈头操作被取消
                        if (h != null && h.isCancelled())
                            //丢弃栈头，把栈头后一个元素记作栈头
                            casHead(h, h.next);     // pop cancelled node
                        else//丢弃本次操作
                            return null;
                        //直接把e作为新的栈头  没有设置超时 ||  timed && nanos > 0
                        // 重新设置头节点，加入栈顶，其下一个节点指向原来头节点
                    } else if (casHead(h, s = snode(s, e, h, mode))) {//初始化节点，并且修改栈顶指针
                        //等待e出栈，一种是空队列 take,一种是put
                        //进行等待操作
                        // 头节点是null 或者是两次相同的操作，如果是两次相同的操作，就需要进行等待,s是栈顶节点
                        //匹配的节点
                        SNode m = awaitFulfill(s, timed, nanos);
                        //返回的内容是本身，进行清理
                        // TODO 返回的匹配节点与当前节点一致，进行清除操作？
                        // 当节点取消的时候，返回的匹配节点就是其自身
                        if (m == s) {               // wait was cancelled
                            // 删除栈中已经取消的节点
                            clean(s);
                            return null;
                        }
                        //如果s不是栈头，把新数据作为栈头
                        //匹配节点与当前节点不一致 && 当前操作元素是现在栈中下一个节点
                        //当前元素与栈头信息匹配，将当前元素的下一个节点作为新的栈头
                        // TODO 如果当前节点在栈顶下的三四层会怎么处理？节点什么时候会删除？
                        // TODO 元素是向上匹配的？
                        if ((h = head) != null && h.next == s){
                            //将当前元素的下一个节点作为新的栈头
                            casHead(h, s.next);
                        }
                        // help s's fulfiller
                        // 如果请求类型是消费者，返回匹配元素的信息，如果当前线程是生产者，返回当亲操作元素的信息
                        return (E) ((mode == REQUEST) ? m.item : s.item);
                    }
                    //栈头正在等待其他线程put或者take
                    //如栈头正在堵塞，并且是put类型，此次操作是take类型
                    // 栈头不为空，且操作类型与栈头元素操作类型不一致
                    // 即栈头是消费者，当前操作是生产者
                    // 栈头是生产者，当前操作是消费者

                    // 栈头元素是生产者  00 & 10 = 00
                    // 栈头元素是消费者  01 & 10 = 00
                    // 说明栈头元素是take 或者 put 类型的，00，01 对其使用 10 & mode == 0
                    // 由于上述已经表明两个操作不一致，因此该操作和栈顶的操作是互补的
                } else if (!isFulfilling(h.mode)) { // try to fulfill
                    //栈头元素取消，把下一个元素作为栈头
                    if (h.isCancelled())            // already cancelled
                        casHead(h, h.next);         // pop and retry
                        //新建一个饿full节点，押入栈顶
                        //将当前元素设置进入栈中，并且设置 mode值为 FULFILLING | mode
                        // 如果当前操作为 PUT 类型，mode = 10 | 00 = 10 代表等待消费者
                        // 如果当前操作为 Task 类型 mode = 10 | 01 = 11 代表等待生产者
                    else if (casHead(h, s = snode(s, e, h, FULFILLING | mode))) {
                        for (; ; ) { // loop until matched or waiters disappear
                            //m是栈头
                            //s的下一个节点是匹配节点
                            // 当前操作节点与历史的表头节点操作类型不一致，说明两者是匹配的
                            SNode m = s.next;       // m is s's match
                            //代表没有等待内容了
                            if (m == null) {        // all waiters are gone
                                //弹出full节点
                                casHead(s, null);   // pop fulfill node
                                //设置null用于下次生成新节点
                                s = null;           // use new node next time
                                break;              // restart main loop
                            }
                            SNode mn = m.next;
                            //下一个节点和该节点匹配  TODO未必是栈顶节点
                            //节点操作已经匹配上，将匹配节点的下一个节点设置为栈头节点
                            if (m.tryMatch(s)) {
                                //弹出s节点和m节点
                                casHead(s, mn);     // pop both s and m
                                // 如果该操作是请求操作，返回匹配节点的数据，否则返回自身的数据
                                return (E) ((mode == REQUEST) ? m.item : s.item);
                            } else                  // lost match
                                //失去匹配，取消连接
                                //去下一个节点不是匹配节点，将其下下个节点进行匹配操作
                                s.casNext(m, mn);   // help unlink
                        }
                    }
                    // isFulfilling(h.mode) else含义为是互补的
                    // 多线程同时操作，有两个匹配操作同时进行，则头节点就是 mode FULFILLING | mode，其再 & FULFILLING 就 != 0
                } else {                            // help a fulfiller 帮助互补
                    //m是头节点的匹配节点
                    SNode m = h.next;               // m is h's match
                    //如果m不存在则直接将头节点赋值
                    if (m == null)                  // waiter is gone
                        casHead(h, null);           // pop fulfilling node
                    else {
                        SNode mn = m.next;
                        //h节点尝试匹配m即诶单
                        if (m.tryMatch(h))          // help match
                            //弹出m节点和h节点
                            casHead(h, mn);         // pop both h and m
                        else                        // lost match
                            h.casNext(m, mn);       // help unlink
                    }
                }
            }
        }

        /**
         * Spins/blocks until node s is matched by a fulfill operation.
         * 等待 fulfill操作
         *
         * @param s     the waiting node
         * @param timed true if timed wait
         * @param nanos timeout value
         * @return matched node, or s if cancelled
         */
        SNode awaitFulfill(SNode s, boolean timed, long nanos) {
            /*
             * When a node/thread is about to block, it sets its waiter
             * field and then rechecks state at least one more time
             * before actually parking, thus covering race vs
             * fulfiller noticing that waiter is non-null so should be
             * woken.
             *
             * 当节点或者线程处于阻塞状态，它设置等待者字段然后重新检查最后的状态 在 实际阻塞之前
             * 这样覆盖比赛旅行记录等待者是非空的，需要是工作的
             *
             * When invoked by nodes that appear at the point of call
             * to be at the head of the stack, calls to park are
             * preceded by spins to avoid blocking when producers and
             * consumers are arriving very close in time.  This can
             * happen enough to bother only on multiprocessors.
             *
             * *当被出现在栈头调用*点的节点调用时，对park的调用之前会有旋转，以避免当生产者和消费者在非常接近的时间到达时阻塞。这种情况只会发生在多处理器上。
             * 出现在栈顶部的节点调用时，对park的调用出现旋转，避免阻塞当生产者和消费者都在非常接近的时间，这种歌情况只会发生在多线程处理器上
             *
             *
             * The order of checks for returning out of main loop
             * reflects fact that interrupts have precedence over
             * normal returns, which have precedence over
             * timeouts. (So, on timeout, one last check for match is
             * done before giving up.) Except that calls from untimed
             * SynchronousQueue.{poll/offer} don't check interrupts
             * and don't wait at all, so are trapped in transfer
             * method rather than calling awaitFulfill.
             * 为了检查返回主循环影响事实，阻断有优先级的正常返回，当有优先级超时
             */
            final long deadline = timed ? System.nanoTime() + nanos : 0L;
            Thread w = Thread.currentThread();

            //判断是否应该自旋，最后一个进来的线程是需要进行自旋的，因为 s 与栈的头节点相同

            Boolean shouldSpinFlag = shouldSpin(s);
            int spins = shouldSpinFlag ? (timed ? maxTimedSpins : maxUntimedSpins) : 0;

//            int spins = (shouldSpin(s) ?
//                         (timed ? maxTimedSpins : maxUntimedSpins) : 0);
            for (; ; ) {
                //线程被取消
                if (w.isInterrupted())
                    //等待匹配节点设置为当前节点
                    s.tryCancel();
                // match是匹配的节点 如果取消，返回的是自身节点
                SNode m = s.match;
                if (m != null)
                    return m;
                if (timed) {
                    nanos = deadline - System.nanoTime();
                    //已经超时，取消操作
                    if (nanos <= 0L) {
                        s.tryCancel();
                        continue;
                    }
                }
                if (spins > 0)
                    spins = shouldSpin(s) ? (spins - 1) : 0;
                    //没有设置等待节点，其节点的等待线程设置为当前线程
                else if (s.waiter == null)
                    s.waiter = w; // establish waiter so can park next iter
                else if (!timed)
                    //阻塞
                    LockSupport.park(this);
                    // TODO 当 nanos <= spinForTimeoutThreshold 时，其不会进入阻塞状态，会一直进行自旋，一直等到匹配到元素或者超时取消
                    // 超时取消的 wait 会在 clean 方法中清空
                else if (nanos > spinForTimeoutThreshold)
                    //设置有超时时间的等待
                    LockSupport.parkNanos(this, nanos);
            }
        }

        /**
         * Returns true if node s is at head or there is an active
         * fulfiller.
         * 返回true  代表节点是头节点或者有一个激活的fulfiller
         * 如果当前节点就是空节点或者头节点是空节点或者头节点就是匹配模式，进行自旋
         * 如果 isFulfilling = false说明栈头元素在获取数据
         *
         * isFulfilling =  true 说明栈顶元素和底下的元素是匹配的
         *
         *  如果当前节点就是头节点进行自旋转 TODO 头节点是自身，为什么需要自旋转？
         *  如果头节点是空，进行自旋转 TODO 什么情况下头节点会是空的？
         *  如果头节点与下一个节点匹配，进行自旋转 头节点和下一个节点数据是匹配的，其本身不是头节点，
         *  说明在执行的过程中有后来的节点是头节点，且其与下一个节点是匹配的，自循环等待头节点处理完毕之后，再处理自己
         */
        boolean shouldSpin(SNode s) {
            SNode h = head;
            return (h == s || h == null || isFulfilling(h.mode));
        }

        /**
         * Unlinks s from the stack.
         */
        void clean(SNode s) {
            s.item = null;   // forget item
            s.waiter = null; // forget thread

            /*
             * At worst we may need to traverse entire stack to unlink
             * s. If there are multiple concurrent calls to clean, we
             * might not see s if another thread has already removed
             * it. But we can stop when we see any node known to
             * follow s. We use s.next unless it too is cancelled, in
             * which case we try the node one past. We don't check any
             * further because we don't want to doubly traverse just to
             * find sentinel.
             */

            SNode past = s.next;
            if (past != null && past.isCancelled())
                past = past.next;

            // Absorb cancelled nodes at head
            // 从栈顶删除已经取消的节点
            SNode p;
            while ((p = head) != null && p != past && p.isCancelled())
                casHead(p, p.next);

            // Unsplice embedded nodes
            // p 记录的是栈顶节点
            while (p != null && p != past) {
                SNode n = p.next;
                //删除已经取消的节点
                if (n != null && n.isCancelled())
                    p.casNext(n, n.next);
                else
                    p = n;
            }
        }

        // Unsafe mechanics
        private static final sun.misc.Unsafe UNSAFE;
        private static final long headOffset;

        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
//                UNSAFE = Util.getUnsafeInstance();
                Class<?> k = TransferStack.class;
                headOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("head"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    /**
     * Dual Queue
     */
    static final class TransferQueue<E> extends Transferer<E> {
        /*
         * This extends Scherer-Scott dual queue algorithm, differing,
         * among other ways, by using modes within nodes rather than
         * marked pointers. The algorithm is a little simpler than
         * that for stacks because fulfillers do not need explicit
         * nodes, and matching is done by CAS'ing QNode.item field
         * from non-null to null (for put) or vice versa (for take).
         */

        /**
         * Node class for TransferQueue.
         */
        static final class QNode {
            volatile QNode next;          // next node in queue
            volatile Object item;         // CAS'ed to or from null
            volatile Thread waiter;       // to control park/unpark
            final boolean isData;

            QNode(Object item, boolean isData) {
                this.item = item;
                this.isData = isData;
            }

            boolean casNext(QNode cmp, QNode val) {
                return next == cmp &&
                        UNSAFE.compareAndSwapObject(this, nextOffset, cmp, val);
            }

            boolean casItem(Object cmp, Object val) {
                return item == cmp &&
                        UNSAFE.compareAndSwapObject(this, itemOffset, cmp, val);
            }

            /**
             * Tries to cancel by CAS'ing ref to this as item.
             */
            void tryCancel(Object cmp) {
                UNSAFE.compareAndSwapObject(this, itemOffset, cmp, this);
            }

            boolean isCancelled() {
                return item == this;
            }

            /**
             * Returns true if this node is known to be off the queue
             * because its next pointer has been forgotten due to
             * an advanceHead operation.
             */
            boolean isOffList() {
                return next == this;
            }

            // Unsafe mechanics
            private static final sun.misc.Unsafe UNSAFE;
            private static final long itemOffset;
            private static final long nextOffset;

            static {
                try {
                    UNSAFE = sun.misc.Unsafe.getUnsafe();
//                    UNSAFE = Util.getUnsafeInstance();
                    Class<?> k = QNode.class;
                    itemOffset = UNSAFE.objectFieldOffset
                            (k.getDeclaredField("item"));
                    nextOffset = UNSAFE.objectFieldOffset
                            (k.getDeclaredField("next"));
                } catch (Exception e) {
                    throw new Error(e);
                }
            }
        }

        /**
         * Head of queue
         */
        //队列头
        transient volatile QNode head;
        /**
         * Tail of queue
         */
        //队列尾部
        transient volatile QNode tail;
        /**
         * Reference to a cancelled node that might not yet have been
         * unlinked from queue because it was the last inserted node
         * when it was cancelled.
         */
        transient volatile QNode cleanMe;

        TransferQueue() {
            QNode h = new QNode(null, false); // initialize to dummy node.
            head = h;
            tail = h;
        }

        /**
         * Tries to cas nh as new head; if successful, unlink
         * old head's next node to avoid garbage retention.
         * head 指针后移，原头元素.next指向自身
         */
        void advanceHead(QNode h, QNode nh) {
            // 队列头指针指向已经匹配过的无效数据
            if (h == head &&
                    UNSAFE.compareAndSwapObject(this, headOffset, h, nh))

                // 原头元素的.next 指向自身
                h.next = h; // forget old next
        }

        /**
         * Tries to cas nt as new tail.
         */
        void advanceTail(QNode t, QNode nt) {
            if (tail == t)
                UNSAFE.compareAndSwapObject(this, tailOffset, t, nt);
        }

        /**
         * Tries to CAS cleanMe slot.
         */
        boolean casCleanMe(QNode cmp, QNode val) {
            return cleanMe == cmp &&
                    UNSAFE.compareAndSwapObject(this, cleanMeOffset, cmp, val);
        }

        /**
         * Puts or takes an item.
         */
        @SuppressWarnings("unchecked")
        E transfer(E e, boolean timed, long nanos) {
            /* Basic algorithm is to loop trying to take either of
             * two actions:
             *
             * 基于算法循环尝试 take 在下面两种情况
             *
             * 1. If queue apparently empty or holding same-mode nodes,
             *    try to add node to queue of waiters, wait to be
             *    fulfilled (or cancelled) and return matching item.
             *
             * 如果队列显然空或者持有一些元素，尝试添加元素到队列的等待者，等待变得实现或者取消并且返回匹配的信息
             *
             * 2. If queue apparently contains waiting items, and this
             *    call is of complementary mode, try to fulfill by CAS'ing
             *    item field of waiting node and dequeuing it, and then
             *    returning matching item.
             *如果队列显然包含等待者，并且他们被互补模块呼叫，尝试去通过CAS实现他们的字段等待节点和退出节点，然后返回匹配的信息
             *
             *
             * In each case, along the way, check for and try to help
             * advance head and tail on behalf of other stalled/slow
             * threads.
             *
             * 在上面的案例中，沿着一条路，检查和尝试去帮助推进头节点和尾节点，当代表其他停滞不前和缓慢的线程
             *
             * The loop starts off with a null check guarding against
             * seeing uninitialized head or tail values. This never
             * happens in current SynchronousQueue, but could if
             * callers held non-volatile/final ref to the
             * transferer. The check is here anyway because it places
             * null checks at top of loop, which is usually faster
             * than having them implicitly interspersed.
             *
             * 循环开始在空的守卫检查 再一次看到未初始化的头节点和尾部值，
             * 这从来不会在当前SynchronousQueue发生，但是如果呼叫着持有不是volatile或者final引用在transferer中
             * 这个检检查在这里，因为他设置null检查在循环头部，经常是快的比拥有他们的隐式穿插
             *
             *
             *
             *
             */

            QNode s = null; // constructed/reused as needed
            //判断是添加操作还是查询操作
            // isData true 添加元素
            // false take元素
            boolean isData = (e != null);

            for (; ; ) {
                QNode t = tail;
                QNode h = head;
                // 由于 TransferQueue 初始化的时候，对 head,tail 节点已经进行初始化
                //如果不存在，说明 TransferQueue 没有构造完成，重入循环等待构造完成
                if (t == null || h == null)         // saw uninitialized value
                    continue;                       // spin

                //当队列中没有等待节点时 首尾节点相同
                //当前操作与末尾等待节点操作一致时，当前操作需要入队等待
                if (h == t || t.isData == isData) { // empty or same-mode
                    QNode tn = t.next;
                    // 说明有线程进行操作
                    if (t != tail)                  // inconsistent read
                        continue;
                    // 先前读取的末尾元素和此时的末尾元素一致，但是此时末尾元素有后继节点，且不为空，那么就将其后继节点作为末尾元素，重新进入循环。
                    // 当另外线程有新节点创建，已经添加为末尾元素的后继节点，但是还没有设置为末尾节点时出现此case。如果其后继节点还有后继节点，则在下一次循环中再次进行设置
                    if (tn != null) {               // lagging tail
                        // 重新设置末尾元素 TODO 为什么不重入循环？
                        advanceTail(t, tn);
                        continue;
                    }

                    if (timed && nanos <= 0)        // can't wait
                        return null;
                    // 要操作的元素节点为null时新增元素
                    // 当通过CAS设置当前操作节点为当前末尾元素的后继节点失败后，重入循环时,s不为空
                    if (s == null)
                        s = new QNode(e, isData);
                    // CAS设置当前操作的节点为先前读取的末尾元素后继节点
                    if (!t.casNext(null, s))        // failed to link in
                        continue;

                    //CAS设置当前操作节点为末尾节点，即使CAS设置失败了，也无影响，因为经过  advanceTail(t, tn); 保证元素一定在队列中
                    advanceTail(t, s);              // swing tail and wait
                    // 自旋等待匹配/线程中断结果，达到自旋次数没有匹配结果，当先线程会阻塞或者取消操作
                    Object x = awaitFulfill(s, e, timed, nanos);
                    // 当线程被中断或者已经到达超时时间，会取消操作，返回结果为其自身
                    if (x == s) {                   // wait was cancelled
                        //清除被取消的节点（惰性清除）
                        clean(t, s);
                        return null;
                    }

                    // 没有执行离队操作，离队时 next == 自身
                    if (!s.isOffList()) {           // not already unlinked
                        // TODO 为什么不是  advanceHead(h, s);
                        // 如果该节点是表头后的第一个后继节点，那么将该节点设置为表头（表头都是无效的节点）
                        advanceHead(t, s);          // unlink if head
                        if (x != null)              // and forget fields
                            s.item = s;
                        s.waiter = null;
                    }
                    return (x != null) ? (E) x : e;

                } else {                            // complementary-mode
                    // 由于队列头节点都是无效节点，因此从头节点的下一个节点进行匹配操作
                    // 已经操作离队的节点，其item = 自身
                    QNode m = h.next;               // node to fulfill
                    // t != tail ||  h != head 说明链表发生变更，重新进行匹配
                    // 如果  h != head，则表头第一个后继节点可能不是m,甚至m可能已经完成匹配操作，因此需要重入循环，重新进行查找头节点的第一个后继节点
                    // m == null 说明队列中已经没有元素了，没有匹配节点，重新进行操作
                    if (t != tail || m == null || h != head)
                        continue;                   // inconsistent read

                    // 获取头元素后继节点的属性信息
                    Object x = m.item;


                    // 当 isData 为true时,说明当前操作是put操作，其匹配操作为take，对应的item = null,如果此时 item不为null时，说明节点已经被取消或者已经完成匹配操作
                    // 同理当 isData 为false时,说明当前操作是take操作，其匹配操作为put，对应的item != null,如果此时 item == null时，说明节点已经被取消或者已经完成匹配操作
                    if (isData == (x != null) ||    // m already fulfilled
                            // 如果take操作时，其匹配的节点item不为空，但是为其自身时，说明其匹配节点已经被取消操作了
                            x == m ||                   // m cancelled
                            // 对匹配节点CAS设置对应的item值，如果设置失败，说明有节点已经对其进行了设置，即进行了匹配操作
                            !m.casItem(x, e)) {         // lost CAS
                        // 头指针后移，将匹配节点离队
                        advanceHead(h, m);          // dequeue and retry
                        continue;
                    }
                    // 已经匹配成功，将匹配节点离队
                    advanceHead(h, m);              // successfully fulfilled
                    // 将匹配节点的等待线程唤醒
                    LockSupport.unpark(m.waiter);
                    return (x != null) ? (E) x : e;
                }
            }
        }

        /**
         * Spins/blocks until node s is fulfilled.
         *
         * @param s     the waiting node
         * @param e     the comparison value for checking match
         * @param timed true if timed wait
         * @param nanos timeout value
         * @return matched item, or s if cancelled
         */
        Object awaitFulfill(QNode s, E e, boolean timed, long nanos) {
            /* Same idea as TransferStack.awaitFulfill */
            final long deadline = timed ? System.nanoTime() + nanos : 0L;
            Thread w = Thread.currentThread();
            //如果头节点的下一个元素不是新的元素，不经过自旋，判别之后进入等待状态
            int spins = ((head.next == s) ?
                    (timed ? maxTimedSpins : maxUntimedSpins) : 0);
            for (; ; ) {
                // 如果等待超时，会自动中断
                if (w.isInterrupted())
                    // 设置元素的item属性为其自身
                    s.tryCancel(e);
                // 如果谁取消节点，此时item是其自身，而不是节点创建时的元素，则 s.item != e 肯定是成立的
                // 如果该节点是put 节点，匹配成功之后，item属性为null
                // 如果该节点是take 节点，匹配成功之后，item属性为匹配的值
                Object x = s.item;
                // TODO 如果是取消的节点，如何跳出这个循环？
                if (x != e)
                    return x;
                if (timed) {
                    nanos = deadline - System.nanoTime();
                    if (nanos <= 0L) {
                        s.tryCancel(e);
                        continue;
                    }
                }
                if (spins > 0)
                    --spins;
                else if (s.waiter == null)
                    s.waiter = w;
                else if (!timed)
                    LockSupport.park(this);
                else if (nanos > spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanos);
            }
        }

        /**
         * Gets rid of cancelled node s with original predecessor pred.
         */
        void clean(QNode pred, QNode s) {
            s.waiter = null; // forget thread
            /*
             * At any given time, exactly one node on list cannot be
             * deleted -- the last inserted node. To accommodate this,
             * if we cannot delete s, we save its predecessor as
             * "cleanMe", deleting the previously saved version
             * first. At least one of node s or the node previously
             * saved can always be deleted, so this always terminates.
             *
             * 在任何给定的时刻，确保列表中最后一个被插入的节点不能被删除
             * 为了达到这个，如果不能删除 s,会保存他的程序作为 cleanMe,删除之前保存首先保存这个版本
             * 最后一个节点或者其前面保存的总是被删除的，因此这个是经常终止的
             *
             */
            while (pred.next == s) { // Return early if already unlinked
                QNode h = head;
                QNode hn = h.next;   // Absorb cancelled first node as head
                if (hn != null && hn.isCancelled()) {
                    // TODO 被无效的节点作为头节点的下一个元素 ？  TMD 本身就是头节点的下一个元素 迷之设计
                    // head 指针后移，原头元素.next指向自身
                    // 如果s是头元素的下一个元素 pred 是头元素，那么当其被取消后，那么 s 变为头元素，原头元素的next指向自身
                    advanceHead(h, hn);
                    continue;
                }
                QNode t = tail;      // Ensure consistent read for tail

                // 集合是空的，直接退出
                if (t == h)
                    return;
                QNode tn = t.next;
                // 确保t是末尾元素
                if (t != tail)
                    continue;
                // 如果末尾元素有next节点，且不是末尾元素，设置 t.next 为末尾元素
                if (tn != null) {
                    advanceTail(t, tn);
                    continue;
                }
                // s 不是末尾元素
                if (s != t) {        // If not tail, try to unsplice
                    QNode sn = s.next;
                    // TODO 什么时候 s.next == s ? 当节点从链表中移除时 s.next == s ,
                    //  pred.casNext(s, sn) 设置 s.next 作为 s 前一个元素的后继节点
                    if (sn == s || pred.casNext(s, sn))
                        return;
                }
                QNode dp = cleanMe;
                if (dp != null) {    // Try unlinking previous cancelled node
                    QNode d = dp.next;
                    QNode dn;
                    //被清除的节点为空
                    if (d == null ||               // d is gone or
                            // 被清除的节点已经离队
                            // 如果一个节点其下一个节点指向其本身，那么这个节点是已经被无效的节点
                            d == dp ||                 // d is off list or
                            // 被清除的节点是有效状态
                            !d.isCancelled() ||        // d not cancelled or
                            (d != t &&                 // 不是末尾元素
                                    // 被清除的节点有后继节点
                                    (dn = d.next) != null &&  //   has successor
                                    // 如果一个节点其下一个节点指向其本身，那么这个节点是已经被无效的节点
                                    dn != d &&                //   that is on list
                                    // 设置被清除前一个节点的后继节点是 清除元素的后继节点
                                    dp.casNext(d, dn)))       // d unspliced
                        // 清空cleanMe节点
                        casCleanMe(dp, null);
                    if (dp == pred)
                        return;      // s is already saved node
                } else if (casCleanMe(null, pred))
                    return;          // Postpone cleaning s
            }
        }

        private static final sun.misc.Unsafe UNSAFE;
        private static final long headOffset;
        private static final long tailOffset;
        private static final long cleanMeOffset;

        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
//                UNSAFE = Util.getUnsafeInstance();
                Class<?> k = TransferQueue.class;
                headOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("head"));
                tailOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("tail"));
                cleanMeOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("cleanMe"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    /**
     * Adds the specified element to this queue, waiting if necessary for
     * another thread to receive it.
     *
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public void put(E e) throws InterruptedException {
        if (e == null) throw new NullPointerException();
        if (transferer.transfer(e, false, 0) == null) {
            Thread.interrupted();
            throw new InterruptedException();
        }
    }

    /**
     * Inserts the specified element into this queue, waiting if necessary
     * up to the specified wait time for another thread to receive it.
     *
     * @return {@code true} if successful, or {@code false} if the
     * specified waiting time elapses before a consumer appears
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean offer(E e, long timeout, TimeUnit unit)
            throws InterruptedException {
        if (e == null) throw new NullPointerException();
        if (transferer.transfer(e, true, unit.toNanos(timeout)) != null)
            return true;
        if (!Thread.interrupted())
            return false;
        throw new InterruptedException();
    }

    /**
     * Inserts the specified element into this queue, if another thread is
     * waiting to receive it.
     *
     * @param e the element to add
     * @return {@code true} if the element was added to this queue, else
     * {@code false}
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e) {
        if (e == null) throw new NullPointerException();
        return transferer.transfer(e, true, 0) != null;
    }

    /**
     * Retrieves and removes the head of this queue, waiting if necessary
     * for another thread to insert it.
     *
     * @return the head of this queue
     * @throws InterruptedException {@inheritDoc}
     */
    public E take() throws InterruptedException {
        E e = transferer.transfer(null, false, 0);
        if (e != null)
            return e;
        Thread.interrupted();
        throw new InterruptedException();
    }

    /**
     * Retrieves and removes the head of this queue, waiting
     * if necessary up to the specified wait time, for another thread
     * to insert it.
     *
     * @return the head of this queue, or {@code null} if the
     * specified waiting time elapses before an element is present
     * @throws InterruptedException {@inheritDoc}
     */
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E e = transferer.transfer(null, true, unit.toNanos(timeout));
        if (e != null || !Thread.interrupted())
            return e;
        throw new InterruptedException();
    }

    /**
     * Retrieves and removes the head of this queue, if another thread
     * is currently making an element available.
     *
     * @return the head of this queue, or {@code null} if no
     * element is available
     */
    public E poll() {
        return transferer.transfer(null, true, 0);
    }

    /**
     * Always returns {@code true}.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @return {@code true}
     */
    public boolean isEmpty() {
        return true;
    }

    /**
     * Always returns zero.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @return zero
     */
    public int size() {
        return 0;
    }

    /**
     * Always returns zero.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @return zero
     */
    public int remainingCapacity() {
        return 0;
    }

    /**
     * Does nothing.
     * A {@code SynchronousQueue} has no internal capacity.
     */
    public void clear() {
    }

    /**
     * Always returns {@code false}.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @param o the element
     * @return {@code false}
     */
    public boolean contains(Object o) {
        return false;
    }

    /**
     * Always returns {@code false}.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @param o the element to remove
     * @return {@code false}
     */
    public boolean remove(Object o) {
        return false;
    }

    /**
     * Returns {@code false} unless the given collection is empty.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @param c the collection
     * @return {@code false} unless given collection is empty
     */
    public boolean containsAll(Collection<?> c) {
        return c.isEmpty();
    }

    /**
     * Always returns {@code false}.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @param c the collection
     * @return {@code false}
     */
    public boolean removeAll(Collection<?> c) {
        return false;
    }

    /**
     * Always returns {@code false}.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @param c the collection
     * @return {@code false}
     */
    public boolean retainAll(Collection<?> c) {
        return false;
    }

    /**
     * Always returns {@code null}.
     * A {@code SynchronousQueue} does not return elements
     * unless actively waited on.
     *
     * @return {@code null}
     */
    public E peek() {
        return null;
    }

    /**
     * Returns an empty iterator in which {@code hasNext} always returns
     * {@code false}.
     *
     * @return an empty iterator
     */
    public Iterator<E> iterator() {
        return Collections.emptyIterator();
    }

    /**
     * Returns an empty spliterator in which calls to
     * {@link Spliterator#trySplit()} always return {@code null}.
     *
     * @return an empty spliterator
     * @since 1.8
     */
    public Spliterator<E> spliterator() {
        return Spliterators.emptySpliterator();
    }

    /**
     * Returns a zero-length array.
     *
     * @return a zero-length array
     */
    public Object[] toArray() {
        return new Object[0];
    }

    /**
     * Sets the zeroeth element of the specified array to {@code null}
     * (if the array has non-zero length) and returns it.
     *
     * @param a the array
     * @return the specified array
     * @throws NullPointerException if the specified array is null
     */
    public <T> T[] toArray(T[] a) {
        if (a.length > 0)
            a[0] = null;
        return a;
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c) {
        if (c == null)
            throw new NullPointerException();
        if (c == this)
            throw new IllegalArgumentException();
        int n = 0;
        for (E e; (e = poll()) != null; ) {
            c.add(e);
            ++n;
        }
        return n;
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c, int maxElements) {
        if (c == null)
            throw new NullPointerException();
        if (c == this)
            throw new IllegalArgumentException();
        int n = 0;
        for (E e; n < maxElements && (e = poll()) != null; ) {
            c.add(e);
            ++n;
        }
        return n;
    }

    /*
     * To cope with serialization strategy in the 1.5 version of
     * SynchronousQueue, we declare some unused classes and fields
     * that exist solely to enable serializability across versions.
     * These fields are never used, so are initialized only if this
     * object is ever serialized or deserialized.
     */

    @SuppressWarnings("serial")
    static class WaitQueue implements java.io.Serializable {
    }

    static class LifoWaitQueue extends WaitQueue {
        private static final long serialVersionUID = -3633113410248163686L;
    }

    static class FifoWaitQueue extends WaitQueue {
        private static final long serialVersionUID = -3623113410248163686L;
    }

    private ReentrantLock qlock;
    private WaitQueue waitingProducers;
    private WaitQueue waitingConsumers;

    /**
     * Saves this queue to a stream (that is, serializes it).
     *
     * @param s the stream
     * @throws java.io.IOException if an I/O error occurs
     */
    private void writeObject(java.io.ObjectOutputStream s)
            throws java.io.IOException {
        boolean fair = transferer instanceof TransferQueue;
        if (fair) {
            qlock = new ReentrantLock(true);
            waitingProducers = new FifoWaitQueue();
            waitingConsumers = new FifoWaitQueue();
        } else {
            qlock = new ReentrantLock();
            waitingProducers = new LifoWaitQueue();
            waitingConsumers = new LifoWaitQueue();
        }
        s.defaultWriteObject();
    }

    /**
     * Reconstitutes this queue from a stream (that is, deserializes it).
     *
     * @param s the stream
     * @throws ClassNotFoundException if the class of a serialized object
     *                                could not be found
     * @throws java.io.IOException    if an I/O error occurs
     */
    private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        if (waitingProducers instanceof FifoWaitQueue)
            transferer = new TransferQueue<E>();
        else
            transferer = new TransferStack<E>();
    }

    // Unsafe mechanics
    static long objectFieldOffset(sun.misc.Unsafe UNSAFE,
                                  String field, Class<?> klazz) {
        try {
            return UNSAFE.objectFieldOffset(klazz.getDeclaredField(field));
        } catch (NoSuchFieldException e) {
            // Convert Exception to corresponding Error
            NoSuchFieldError error = new NoSuchFieldError(field);
            error.initCause(e);
            throw error;
        }
    }

}
