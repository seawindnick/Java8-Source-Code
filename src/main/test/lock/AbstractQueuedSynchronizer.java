package lock;/*
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
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */


import sun.misc.Unsafe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.TimeUnit;


public abstract class AbstractQueuedSynchronizer
        extends AbstractOwnableSynchronizer
        implements java.io.Serializable {

    private static final long serialVersionUID = 7373984972572414691L;


    protected AbstractQueuedSynchronizer() {
    }

    /**
     * Wait queue node class.
     * 等待队列
     *
     * <p>The wait queue is a variant of a "CLH" (Craig, Landin, and
     * Hagersten) lock queue. CLH locks are normally used for
     * spinlocks.  We instead use them for blocking synchronizers, but
     * use the same basic tactic of holding some of the control
     * information about a thread in the predecessor of its node.  A
     * "status" field in each node keeps track of whether a thread
     * should block.  A node is signalled when its predecessor
     * releases.  Each node of the queue otherwise serves as a
     * specific-notification-style monitor holding a single waiting
     * thread. The status field does NOT control whether threads are
     * granted locks etc though.  A thread may try to acquire if it is
     * first in the queue. But being first does not guarantee success;
     * it only gives the right to contend.  So the currently released
     * contender thread may need to rewait.
     * <p>
     * 等待队列是 CLH 锁队列的变体
     * CLH 是一个平常被使用在自旋锁，我们使用这个替代同步阻塞，管控一些线程的前驱节点信息是使用这些的基础
     * 一个 status 字段在所有节点跟踪信息 无论一个线程是否需要被阻塞
     * 一个节点被唤醒当前驱节点释放，队列中的所有节点无论服务作为一个 指定唤醒风格 管控一个单独的等待线程
     * 这个状态字段不控制线程是否被授予锁，一个线程会尝试去获取锁如果它是队列的头节点
     * 但是作为头节点并不代表会被授权成功
     * 它仅被授予在对的竞争
     * 所以当前释放竞争的线程也许会需要再次等待
     *
     *
     *
     * <p>To enqueue into a CLH lock, you atomically splice it in as new
     * tail. To dequeue, you just set the head field.
     * <pre>
     *      +------+  prev +-----+       +-----+
     * head |      | <---- |     | <---- |     |  tail
     *      +------+       +-----+       +-----+
     * </pre>
     * <p>
     * CLH锁排队，自动拼接它作为一个新的尾部元素。自动出队列，需要重新设置头元素
     *
     * <p>Insertion into a CLH queue requires only a single atomic
     * operation on "tail", so there is a simple atomic point of
     * demarcation from unqueued to queued. Similarly, dequeuing
     * involves only updating the "head". However, it takes a bit
     * more work for nodes to determine who their successors are,
     * in part to deal with possible cancellation due to timeouts
     * and interrupts.
     * <p>
     * 插入到 CLH 队列仅仅在tail上进行一个原子性操作
     * 因此队列入队是一个简单的界定原子性的点
     * 相似的 出队 操作仅仅是修改head
     * 这些操作对于节点能够很好的工作去线程 他们成功执行，处理的一部分中可能取消根据超时或者中断
     *
     *
     * <p>The "prev" links (not used in original CLH locks), are mainly
     * needed to handle cancellation. If a node is cancelled, its
     * successor is (normally) relinked to a non-cancelled
     * predecessor. For explanation of similar mechanics in the case
     * of spin locks, see the papers by Scott and Scherer at
     * http://www.cs.rochester.edu/u/scott/synchronization/
     * <p>
     * 前驱链接(不被用在原始的CHL锁)，最主要的是用于处理取消
     * 如果一个节点取消，它的后继节点通常重新连接一个没有取消的前驱节点
     * 例如一个 自旋锁的案例中，查看。。。论文
     *
     * <p>We also use "next" links to implement blocking mechanics.
     * The thread id for each node is kept in its own node, so a
     * predecessor signals the next node to wake up by traversing
     * next link to determine which thread it is.  Determination of
     * successor must avoid races with newly queued nodes to set
     * the "next" fields of their predecessors.  This is solved
     * when necessary by checking backwards from the atomically
     * updated "tail" when a node's successor appears to be null.
     * (Or, said differently, the next-links are an optimization
     * so that we don't usually need a backward scan.)
     * <p>
     * 我们经常使用 next 实现阻塞。所有的节点都有一个线程id 被保存在他们自己的节点
     * 因此一个前驱唤醒后一个节点通过遍历 下一个节点的连接去 确定是哪一个线程
     * 确定后继节点必须避免
     *
     * <p>Cancellation introduces some conservatism to the basic
     * algorithms.  Since we must poll for cancellation of other
     * nodes, we can miss noticing whether a cancelled node is
     * ahead or behind us. This is dealt with by always unparking
     * successors upon cancellation, allowing them to stabilize on
     * a new predecessor, unless we can identify an uncancelled
     * predecessor who will carry this responsibility.
     * <p>
     * 取消操作为基础的算法引入了一定的保守性
     * 因为我们必须轮询取消节点，可能会忽略一个被取消的节点是在我们的前面还是后面
     * 这是一个处理经常使用 不阻塞后继节点在取消上面，允许他们在一个稳定的新建前驱节点
     * 除非我们能够识别一个没有取消的将携带返回信息的前驱节点，
     *
     * <p>CLH queues need a dummy header node to get started. But
     * we don't create them on construction, because it would be wasted
     * effort if there is never contention. Instead, the node
     * is constructed and head and tail pointers are set upon first
     * contention.
     * <p>
     * CLH队列需要一个无效的头节点在启动时，但是我们不能创造他们在构造器中
     * 因为它是浪费的如果从来没有争用。代替的，这个节点被创建并且头尾节点被设置在第一个争用上
     *
     * <p>Threads waiting on Conditions use the same nodes, but
     * use an additional link. Conditions only need to link nodes
     * in simple (non-concurrent) linked queues because they are
     * only accessed when exclusively held.  Upon await, a node is
     * inserted into a condition queue.  Upon signal, the node is
     * transferred to the main queue.  A special value of status
     * field is used to mark which queue a node is on.
     * <p>
     * 等待条件的线程使用相同的节点，但是使用额外的链表
     * 条件仅仅在 链表节点在简单非并发的队列中  时候被需要，因为他们只在独占持有时被访问
     * 在等待上，一个节点需要添加进条件队列，在通知时，这个节点被转移到主队列
     * 一个 stats 字段特殊值被用于标记节点所在的队列
     *
     * <p>Thanks go to Dave Dice, Mark Moir, Victor Luchangco, Bill
     * Scherer and Michael Scott, along with members of JSR-166
     * expert group, for helpful ideas, discussions, and critiques
     * on the design of this class.
     * <p>
     * 同步队列
     */
    static final class Node {
        /** Marker to indicate a node is waiting in shared mode */
        /**
         * 标记表明节点是一个等待的共享模式  有初始值
         */
        static final Node SHARED = new Node();
        /** Marker to indicate a node is waiting in exclusive mode */
        /**
         * 标记一个等待节点是独占模式
         */
        static final Node EXCLUSIVE = null;

        /** waitStatus value to indicate thread has cancelled */
        /**
         * 等待状态介绍一个线程已经被取消
         */
        static final int CANCELLED = 1;

        /**
         * waitStatus value to indicate successor's thread needs unparking
         * <p>
         * 节点状态介绍后继线程需要不阻塞
         *
         * 同步队列中的节点在自旋获取锁的时候，如果前一个节点的状态是singal,那么自己就可以阻塞休息
         * 否则会一直自旋尝试获取锁
         * 表示后面的节点处于线程等待状态
         */

        static final int SIGNAL = -1;


        /** waitStatus value to indicate thread is waiting on condition */
        /**
         * 节点状态，介绍线程等待条件
         *
         * 当前node节点正在条件队列中，当有节点从同步队列转移到条件队列时，状态就会被改成 CONDITION
         */
        static final int CONDITION = -2;
        /**
         * waitStatus value to indicate the next acquireShared should
         * unconditionally propagate
         * 等待状态 介绍下一个要求共享的需要无条件传播
         * 无条件传播，共享模式下，该状态的进程处于可运行的状态
         */
        static final int PROPAGATE = -3;

        /**
         * Status field, taking on only the values:
         * 线程状态字段
         * SIGNAL:     The successor of this node is (or will soon be)
         * blocked (via park), so the current node must
         * unpark its successor when it releases or
         * cancels. To avoid races, acquire methods must
         * first indicate they need a signal,
         * then retry the atomic acquire, and then,
         * on failure, block.
         *
         * 通知： 这个节点的后继节点是或者很快是阻塞的通过park,因此当前节点必须不阻塞后继节点当他释放或者取消
         * 为了避免循环，要求方法必须第一次说明他们需要被通知，然后重试原子性的申请，继续，失败或者阻塞
         *
         * CANCELLED:  This node is cancelled due to timeout or interrupt.
         * Nodes never leave this state. In particular,
         * a thread with cancelled node never again blocks.
         *
         * 已取消 ： 节点取消根据超时或者中断，节点永远不会离开这个状态
         * 特别是，一个被取消的线程不会再次被阻塞
         *
         * CONDITION:  This node is currently on a condition queue.
         * It will not be used as a sync queue node
         * until transferred, at which time the status
         * will be set to 0. (Use of this value here has
         * nothing to do with the other uses of the
         * field, but simplifies mechanics.)
         *
         * 条件 这个节点当前在一个条件队列上，它将被使用作为一个同步节点队列直到被转移
         * 在这些时间，这个状态将被设置为0，这里使用这个值没有做任何事情，伴随着其他使用这个字段，仅仅是简单机制
         *
         *
         *
         * PROPAGATE:  A releaseShared should be propagated to other
         * nodes. This is set (for head node only) in
         * doReleaseShared to ensure propagation
         * continues, even if other operations have
         * since intervened.
         * 0:          None of the above
         * <p>
         * The values are arranged numerically to simplify use.
         * Non-negative values mean that a node doesn't need to
         * signal. So, most code doesn't need to check for particular
         * values, just for sign.
         * <p>
         * 传播，一个发布共享的需要释放其他节点，这个设置（仅仅对应表头）在传播共享需要确保传播继续
         * 即使其他操作已经干预
         *
         * 这个被安排的数值用于简单的使用，非负数值表示这个节点不需要被唤醒
         * 因此，大部分的code不需要检查特定的值，仅仅需要检查符号
         *
         *
         * The field is initialized to 0 for normal sync nodes, and
         * CONDITION for condition nodes.  It is modified using CAS
         * (or when possible, unconditional volatile writes).
         * <p>
         *
         * 这个字段初始化是0作为平常同步的节点，并且是条件状态对于条件节点
         * 需要使用CAS进行修改，否则可能不是可见的写操作
         * 0 初始状态
         */
        volatile int waitStatus;

        /**
         * Link to predecessor node that current node/thread relies on
         * for checking waitStatus. Assigned during enqueuing, and nulled
         * out (for sake of GC) only upon dequeuing.  Also, upon
         * cancellation of a predecessor, we short-circuit while
         * finding a non-cancelled one, which will always exist
         * because the head node is never cancelled: A node becomes
         * head only as a result of successful acquire. A
         * cancelled thread never succeeds in acquiring, and a thread only
         * cancels itself, not any other node.
         * <p>
         *
         * 链接当前线程或者节点的前驱节点，依赖于检查等待状态
         * 在入队时进行分配，只有在出队时允许是空的，当然在取消节点的前驱节点中，我们使用短循环
         * 当发现一个没有被取消的节点，将经常存在，因为这个线程的节点从来没有被取消
         * 一个节点变成头节点仅仅作为一个成功申请的结果时
         * 一个取消的线程没有在申请中成功，并且这个线程仅仅取消它自己，不会影响其他节点
         *
         * 前驱节点
         */
        volatile Node prev;

        /**
         * Link to the successor node that the current node/thread
         * unparks upon release. Assigned during enqueuing, adjusted
         * when bypassing cancelled predecessors, and nulled out (for
         * sake of GC) when dequeued.  The enq operation does not
         * assign next field of a predecessor until after attachment,
         * so seeing a null next field does not necessarily mean that
         * node is at end of queue. However, if a next field appears
         * to be null, we can scan prev's from the tail to
         * double-check.  The next field of cancelled nodes is set to
         * point to the node itself instead of null, to make life
         * easier for isOnSyncQueue.
         * <p>
         * <p>
         * 后继节点
         * 链接当前线程或节点的后继节点在释放时，在入队时设置
         * 当 绕过取消的前驱节点时进行调增，并且当出队时设置为null
         * 查询操作不分配 前驱元素的 next 节点，直到。。。以后，所以看到一个null 的后继节点，并不意味着这个节点就是队列的最后一个元素
         * 当然，如果 next 字段出现了null,我们需要从后往前双向检查前驱节点
         * next 字段是一个取消的节点是设置这个点为它自己替代null,保证让 isOnSyncQueue 操作更容易
         *
         */
        volatile Node next;

        /**
         * The thread that enqueued this node.  Initialized on
         * construction and nulled out after use.
         *
         * 这个节点入队时的线程，初始化在构造器中，并且在使用时允许为null
         * <p>
         * 每个节点代表一个线程
         * <p>
         * 期望效果
         * 当一个线程获取同步状态失败之后，将该线程阻塞，并包装成Node节点插入同步队列中
         * 当获取同步状态成功的线程释放同步状态时，通知队列中下一个未获取到同步状态的节点，让该节点的线程再次去获取同步状态
         */
        volatile Thread thread;

        /**
         * Link to next node waiting on condition, or the special
         * value SHARED.  Because condition queues are accessed only
         * when holding in exclusive mode, we just need a simple
         * linked queue to hold nodes while they are waiting on
         * conditions. They are then transferred to the queue to
         * re-acquire. And because conditions can only be exclusive,
         * we save a field by using special value to indicate shared
         * mode.
         * <p>
         *
         * 链接条件等待的下一个节点 否则是一个特殊的值 SHARED
         *
         * 因为条件队列被允许通过只有当处理独占模式，我们仅仅需要一个简单的链表队列去存储节点当它们在条件上等待
         * 然后他们被转移到队列使用重新申请
         * 因为条件只能被独占，我们保存一个字段使用指定的值去介绍分享模式
         *
         *
         * TODO 等待者？
         *
         * 两个队列的共享属性，通过节点的状态，控制节点的行为
         * 普通同步节点 0
         * 条件节点 -2
         *
         * 条件队列中，表示下一个节点的元素，一个个标识符，表示当前节点是共享模式还是排他模式
         *
         */
        Node nextWaiter;

        /**
         * Returns true if node is waiting in shared mode.
         * TODO 判断节点是否是共享的 （读写锁？）
         * 返回true表示一个等待节点是共享模式
         */
        final boolean isShared() {
            return nextWaiter == SHARED;
        }

        /**
         * Returns previous node, or throws NullPointerException if null.
         * Use when predecessor cannot be null.  The null check could
         * be elided, but is present to help the VM.
         *
         * @return the predecessor of this node
         * 查看是否有前驱
         */
        final Node predecessor() throws NullPointerException {
            Node p = prev;
            if (p == null)
                throw new NullPointerException();
            else
                return p;
        }

        Node() {    // Used to establish initial head or SHARED marker
        }

        //创建下一个等待节点
        Node(Thread thread, Node mode) {     // Used by addWaiter
            this.nextWaiter = mode;
            this.thread = thread;
        }

        //创建时需要有线程信息，等待节点信息
        Node(Thread thread, int waitStatus) { // Used by Condition
            this.waitStatus = waitStatus;
            this.thread = thread;
        }
    }

    /**
     * Head of the wait queue, lazily initialized.  Except for
     * initialization, it is modified only via method setHead.  Note:
     * If head exists, its waitStatus is guaranteed not to be
     * CANCELLED.
     * <p>
     * 如果链表头节点信息存在，那么 waitStatus 不能为 取消状态
     */
    private transient volatile Node head;

    /**
     * Tail of the wait queue, lazily initialized.  Modified only via
     * method enq to add new wait node.
     * <p>
     * 尾节点
     */
    private transient volatile Node tail;

    /**
     * The synchronization state.
     * <p>
     * 同步状态
     * <p>
     * 通过修改 state 字段代表的同步状态 实现多线程的 独占模式和共享模式
     */
    private volatile int state;

    /**
     * Returns the current value of synchronization state.
     * This operation has memory semantics of a {@code volatile} read.
     *
     * @return current state value
     */
    protected final int getState() {
        return state;
    }

    /**
     * Sets the value of synchronization state.
     * This operation has memory semantics of a {@code volatile} write.
     *
     * @param newState the new state value
     */
    protected final void setState(int newState) {
        state = newState;
    }

    /**
     * Atomically sets synchronization state to the given updated
     * value if the current state value equals the expected value.
     * This operation has memory semantics of a {@code volatile} read
     * and write.
     *
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful. False return indicates that the actual
     * value was not equal to the expected value.
     * <p>
     * CAS 设置同步状态
     */
    protected final boolean compareAndSetState(int expect, int update) {
        // See below for intrinsics setup to support this
        return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
    }

    // Queuing utilities

    /**
     * The number of nanoseconds for which it is faster to spin
     * rather than to use timed park. A rough estimate suffices
     * to improve responsiveness with very short timeouts.
     */
    static final long spinForTimeoutThreshold = 1000L;

    /**
     * Inserts node into queue, initializing if necessary. See picture above.
     * TODO 队列的头元素都是无效节点？
     *
     * @param node the node to insert
     * @return node's predecessor
     * <p>
     * TODO 将节点插入到队列中
     *
     * 返回值是前一个node
     */
    private Node enq(final Node node) {
        for (; ; ) {
            Node t = tail;
            // 尾部元素为空，进行初始化
            if (t == null) { // Must initialize
                //获取锁信息 旧元素为空，新元素为 new Node() ，使用操作系统进行 CAS加锁
                if (compareAndSetHead(new Node()))
                    //
                    tail = head;
            } else {
                // 将新元素的前一个节点设置为 队列中的末尾元素
                node.prev = t;
                // 获取锁信息 设置最后一个元素的偏移量，预期元素是元素 旧队列的尾部元素，期望元素是新的元素
                if (compareAndSetTail(t, node)) {
                    //获取锁之后，再设置原队列中尾部元素的下一个元素位置，防止并发出现不一致的情况
                    t.next = node;
                    return t;
                }
            }
        }
    }

    /**
     * Creates and enqueues node for current thread and given mode.
     *
     * @param mode Node.EXCLUSIVE for exclusive, Node.SHARED for shared
     * @return the new node
     * <p>
     * 添加新的等待节点
     * 如果有尾部节点，在尾部节点之后追加
     * 无尾部节点，则重新对队列进行初始化
     * <p>
     * TODO 为什么不直接使用  enq(node) ？
     * 如果使用CAS失败，然后再使用循环插入
     * <p>
     * 先不使用自旋向尾部追加元素，如果追加不成功，再使用自旋追加元素
     */
    private Node addWaiter(Node mode) {
        //构造一个新的节点
        Node node = new Node(Thread.currentThread(), mode);
        // Try the fast path of enq; backup to full enq on failure
        Node pred = tail;
        //尾节点不空，直接插入到尾部节点
        // 先设置好当前节点的属性，然后进行CAS设置尾部节点
        if (pred != null) {
            node.prev = pred;
            //TODO 更新tail,并将新节点插入到最后
            if (compareAndSetTail(pred, node)) {
                pred.next = node;
                return node;
            }
        }
        // 没有尾部节点
        // 有尾部节点，但是CAS设置尾部节点失败
        enq(node);
        return node;
    }

    /**
     * Sets head of queue to be node, thus dequeuing. Called only by
     * acquire methods.  Also nulls out unused fields for sake of GC
     * and to suppress unnecessary signals and traversals.
     *
     * 设置队列头节点
     * 仅能通过 acquire methods 方法调用
     * 为了方便GC和不使用的被唤醒和遍历机制，将一些字段置为null
     *
     * @param node the node
     *             <p>
     *             设置头节点信息
     */
    private void setHead(Node node) {
        head = node;
        node.thread = null;
        node.prev = null;
    }

    /**
     * Wakes up node's successor, if one exists.
     *
     * @param node the node
     *             <p>
     *             unpark 唤醒下一个线程执行者
     *             <p>
     *             head指向0号节点，状态为-1，首先将其 WaitStatus 设置为0初始态
     *             对其有效的后继节点1调用 LockSupport.unpark 唤醒节点对应的线程，将节点1的thread设置为null, 并将其作为头节点


        当线程释放锁成功后，从node开始唤醒同步队列中的节点
        通过唤醒机制，保证线程不会一直在同步队列中阻塞等待
     */
    private void unparkSuccessor(Node node) {
        /*
         * If status is negative (i.e., possibly needing signal) try
         * to clear in anticipation of signalling.  It is OK if this
         * fails or if status is changed by waiting thread.
         */

        //node是当前要释放的节点，也是同步队列的头节点
        int ws = node.waitStatus;

        /**
         *   TODO 如果节点处于不可执行状态 ,通过CAS设置节点状态信息？
         *   但是 CAS 不是自旋，失败了怎么处理？
         *
         *   TODO 如果节点已经被取消了，节点状态设置为初始化？
         *   取消状态不是 > 0 的吗？
         *
         *   头节点总是无效的节点，ws < 0 ,说明此时节点状态还是有效的，将其进行初始化
         */

        if (ws < 0) {
            /**
             *  TODO 标记线程执行完成？
             *
             *
             */
            compareAndSetWaitStatus(node, ws, 0);
        }

        /*
         * Thread to unpark is held in successor, which is normally
         * just the next node.  But if cancelled or apparently null,
         * traverse backwards from tail to find the actual
         * non-cancelled successor.
         *
         * 线程唤醒的继承者通常是下一个节点，如果下一个节点被取消或者为空，那么就
         * 从尾部元素 逆序遍历，找到可以执行的没有被取消的节点
         *
         */
        /**
         * 获取节点的后一个元素，判断其状态
         */
        Node s = node.next;
        //如果后一个元素为空或者 处于正在执行状态/取消状态？
        //如果后一个元素为空或者处于取消状态，那么从其后面的节点找出未执行的线程，将其唤醒

        //s为空，表示 node 的后一个节点为空
        // s.waitStatus > 0 表示s节点已经被取消了
        if (s == null || s.waitStatus > 0) {
            s = null;
            //从尾部元素开始向前找，在该节点-----末尾节点 找出未在执行的节点的头一个节点
            // TODO 为什么不是从前往后找呢？找到之后还要向前继续追溯
            // 因为 s == null,说明链表已经断裂，从后往前找总能找到一个有用的
            /**
             * 节点被阻塞时，是在 acquireQueued 方法中被阻塞，被唤醒时也一定会是在 acquireQueued 方法中被唤醒
             * 唤醒之后的条件是，判断当前节点的前驱节点是否是头节点
             *
             * 使用逆序迭代是为了过滤掉无效的潜质节点，否则节点被唤醒时，发现其前置节点还是无效节点，
             * 就又回陷入阻塞
             *
             */
            for (Node t = tail; t != null && t != node; t = t.prev)
                // 说明线程没有被取消，等待唤醒
                if (t.waitStatus <= 0)
                    s = t;
        }
        /**
         * TODO 做什么的？
         */
        if (s != null)
            //将线程唤醒
            LockSupport.unpark(s.thread);
    }

    /**
     * Release action for shared mode -- signals successor and ensures
     * propagation. (Note: For exclusive mode, release just amounts
     * to calling unparkSuccessor of head if it needs signal.)
     * <p>
     * 释放共享模块
     */
    private void doReleaseShared() {
        /*
         * Ensure that a release propagates, even if there are other
         * in-progress acquires/releases.  This proceeds in the usual
         * way of trying to unparkSuccessor of head if it needs
         * signal. But if it does not, status is set to PROPAGATE to
         * ensure that upon release, propagation continues.
         * Additionally, we must loop in case a new node is added
         * while we are doing this. Also, unlike other uses of
         * unparkSuccessor, we need to know if CAS to reset status
         * fails, if so rechecking.
         *
         * 确保释放 propagates.即使另外的程序需要 acquires 或者释放
         * 经常用于尝试唤醒头节点线程
         * 如果没有被唤醒，status 设置为 PROPAGATE 确保释放
         * 需要循环做这些事情时新创建的节点，不像其他经常使用的唤醒线程，需要直到CAS的重置结果，
         * 如果失败了，需要重复进行检查
         *
         *
         */
        for (; ; ) {
            //唤醒节点从头开始，该头节点已经被设置为新的节点
            //唤醒共享锁戒丢安的后继节点
            Node h = head;
            if (h != null && h != tail) {
                int ws = h.waitStatus;
                //表示节点需要被唤醒
                if (ws == Node.SIGNAL) {
                    // 如果标记失败，重试
                    if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                        continue;            // loop to recheck cases
                    // 标记成功 唤醒节点
                    unparkSuccessor(h);
                } else if (ws == 0 &&
                        //如果后继节点不需要被唤醒 ，则把当前节点状态设置为 PROPAGATE 确保以后可以传递下去
                        !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                    // 如果waitStatus为0 将其标记为 PROPAGATE
                    continue;                // loop on failed CAS
            }
            if (h == head)                   // loop if head changed
                break;
        }
    }

    /**
     * Sets head of queue, and checks if successor may be waiting
     * in shared mode, if so propagating if either propagate > 0 or
     * PROPAGATE status was set.
     *
     * @param node      the node
     * @param propagate the return value from a tryAcquireShared
     */
    /**
     * @param node      当前锁节点
     * @param propagate tryAcquireShared方法的返回值，注意上面说的，它可能大于0也可能等于0
     */
    private void setHeadAndPropagate(Node node, int propagate) {
        //记录当前头节点
        Node h = head; // Record old head for check below
        //TODO  将头节点进行替换？
        /**
         * 设置新的头节点，把当前获取到锁的节点设置为头节点
         * 由于是获取到锁之后的操作，不需要进行并发控制
         */
        setHead(node);
        /*
         * Try to signal next queued node if:
         *   Propagation was indicated by caller,
         *     or was recorded (as h.waitStatus either before
         *     or after setHead) by a previous operation
         *     (note: this uses sign-check of waitStatus because
         *      PROPAGATE status may transition to SIGNAL.)
         * and
         *   The next node is waiting in shared mode,
         *     or we don't know, because it appears null
         *
         * The conservatism in both of these checks may cause
         * unnecessary wake-ups, but only when there are multiple
         * racing acquires/releases, so most need signals now or soon
         * anyway.
         */
        /**
         * 唤醒后继节点两种条件
         * 1。propagate > 0, 表示调用方法指明后继节点需要被唤醒，因为此方法是获取读锁过程调用，那么后面的节点很可能也是获取读锁
         * 2。头节点后面的节点需要被唤醒 waitStatus < 0,不论是老的头节点还是新的头节点
         */
        if (propagate > 0 || h == null || h.waitStatus < 0 ||
                (h = head) == null || h.waitStatus < 0) {
            Node s = node.next;
            /**
             * 如果当前节点的后继节点是共享类型，则进行唤醒
             * 即非明确指明不需要唤醒（后继节点为独占类型），否则都需要进行唤醒
             *
             * 后一个节点是共享节点，则唤醒，实现共享，独占锁有释放时，唤醒
             */
            if (s == null || s.isShared())
                //唤醒头节点的后继节点
                doReleaseShared();
        }
    }

    // Utilities for various versions of acquire

    /**
     * Cancels an ongoing attempt to acquire.
     * 取消正在执行的请求
     *
     * @param node the node
     *             <p>
     *             TODO 需要再研究
     */
    private void cancelAcquire(Node node) {
        // Ignore if node doesn't exist
        if (node == null)
            return;
        //将该节点所属线程置为空
        node.thread = null;

        // Skip cancelled predecessors
        Node pred = node.prev;
        //跳过已经取消的节点，获取前面未执行的节点，设置为该节点的前一个节点
        while (pred.waitStatus > 0)
            node.prev = pred = pred.prev;

        // predNext is the apparent node to unsplice. CASes below will
        // fail if not, in which case, we lost race vs another cancel
        // or signal, so no further action is necessary.
        //获取原 pred 的下一个节点
        Node predNext = pred.next;

        // Can use unconditional write instead of CAS here.
        // After this atomic step, other Nodes can skip past us.
        // Before, we are free of interference from other threads.
        // 节点状态设置为取消状态
        node.waitStatus = Node.CANCELLED;

        // If we are the tail, remove ourselves.
        if (node == tail && compareAndSetTail(node, pred)) {
            compareAndSetNext(pred, predNext, null);
        } else {
            // If successor needs signal, try to set pred's next-link
            // so it will get one. Otherwise wake it up to propagate.
            int ws;
            if (pred != head &&
                    ((ws = pred.waitStatus) == Node.SIGNAL ||
                            (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) &&
                    pred.thread != null) {
                Node next = node.next;
                if (next != null && next.waitStatus <= 0)
                    compareAndSetNext(pred, predNext, next);
            } else {
                unparkSuccessor(node);
            }

            node.next = node; // help GC
        }
    }

    /**
     * Checks and updates status for a node that failed to acquire.
     * Returns true if thread should block. This is the main signal
     * control in all acquire loops.  Requires that pred == node.prev.
     *
     * 检查和更新节点的状态当申请资源失败
     * 返回true 如果线程需要进行阻塞，这表示这唤醒控制在整个 acquire 循环中
     * 要求 pred = node.prev
     *
     * @param pred node's predecessor holding status
     * @param node the node
     * @return {@code true} if thread should block
     * <p>
     * 获取锁信息失败时候，需要进行阻塞操作？
     *
     * 主要目的将前一个节点的状态置为 SIGNAL.只要前一个节点状态是SIGNAL,那么当前节点就可以阻塞
     *
     */
    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {

//        CANCELLED = 1;
//        SIGNAL = -1;
//        CONDITION = -2;
//        PROPAGATE = -3;


        /**
         * 前一个节点的状态是 SIGNAL 状态，那么当前节点就可以被阻塞，否则就不能被阻塞
         */
        //获取前一个节点的状态
        int ws = pred.waitStatus;
        if (ws == Node.SIGNAL)// -1 代表当前节点可以被阻塞
            /*
             * This node has already set status asking a release
             * to signal it, so it can safely park.
             */
            return true;
        if (ws > 0) {//代表前一个节点的线程已经被取消操作了
            /*
             * Predecessor was cancelled. Skip over predecessors and
             * indicate retry.
             */
            // 如果前驱节点被取消，一直往前找，直到前驱节点不是取消节点为止
            // 清除所有已经被取消的节点
            do {
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0);
            pred.next = node;
        } else {//设置前一个节点的状态为-1  TODO 为什么是-1？ 为什么设置前驱节点的状态为 -1 ？

            // ws == 0
            /*
             * waitStatus must be 0 or PROPAGATE.  Indicate that we
             * need a signal, but don't park yet.  Caller will need to
             * retry to make sure it cannot acquire before parking.
             *
             * waitStatus 必定是初始态或者传播状态，说明需要进行唤醒操作，但是不执行阻塞
             * 唤醒在将需要重试去确保它在阻塞前不能申请到资源
             *
             * TODO 第一次调用时，会将0号节点的 waitStatus 设置为 Node.SIGNAL，立即返回false
             * 说明0号后边的节点都处于等待状态
             */
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }
        return false;
    }

    /**
     * Convenience method to interrupt current thread.
     */
    static void selfInterrupt() {
        Thread.currentThread().interrupt();
    }

    /**
     * Convenience method to park and then check if interrupted
     *
     * @return {@code true} if interrupted
     */
    private final boolean parkAndCheckInterrupt() {
        LockSupport.park(this);
        return Thread.interrupted();
    }

    /*
     * Various flavors of acquire, varying in exclusive/shared and
     * control modes.  Each is mostly the same, but annoyingly
     * different.  Only a little bit of factoring is possible due to
     * interactions of exception mechanics (including ensuring that we
     * cancel if tryAcquire throws exception) and other control, at
     * least not without hurting performance too much.
     *
     * 各种各样的申请，申请独占/共享和控制模式
     * 所有的主要内容都是相同的，但是细节是不一样的
     * 仅仅只有一部分是根据中断异常（包括入队，我们取消如果使用 tryAcquire 抛出异常）和其他控制
     * 至少不没有很坏的性能
     *
     *
     */

    /**
     * Acquires in exclusive uninterruptible mode for thread already in
     * queue. Used by condition wait methods as well as acquire.
     *
     * 申请包括不中断类型对于线程已经在队列中，使用条件等待方法就像申请
     *
     * @param node the node
     * @param arg  the acquire argument
     * @return {@code true} if interrupted while waiting
     * <p>
     * TODO 需要再次关注
     *
     * 1.通过不断自旋，尝试使前驱节点的状态变为Singal,然后阻塞自己
     * 2.获取锁的线程执行完成之后，释放锁时，会把阻塞的node节点唤醒，然后再次自旋，尝试获取到锁
     * false表示获取锁成功
     * true表示失败
     *
     *
     *
     */
    final boolean acquireQueued(final Node node, int arg) {
        boolean failed = true;
        try {
            //判断节点是否已经被中断
            boolean interrupted = false;
            for (; ; ) {
                //获取节点的前一个节点
                final Node p = node.predecessor();
                // 如果前一个节点是头节点再次尝试获取同步状态
                // 怕获取同步状态的线程很快把同步状态释放，在阻塞当前线程之前再次进行尝试，如果尝试成功，将头节点换成自己，同时把本节点的Thread设置为null,意味自己是头号元素
                // TODO 头号元素有什么好处？ 为什么thread要设置为null?

                // 如果前一个元素是头节点，那么这个元素就是第二个节点，如果此时能获取到同步状态，就将自己设置为头号元素
                // TODO 非公平模式下，如果头节点没有获取到资源怎么办？

                //已经获取到资源，自己肯定是能够运行的，因此不需要被唤醒，将一些不必要的信息置为null
                // 头节点是无效节点

                /**
                 * 1。node节点之前没有获取到锁，进入acquireQueued之后，发现前驱节点就是头节点，尝试获取锁
                 * 2。node节点之前一直沉睡，然后被唤醒，唤醒node节点是其前驱节点，尝试获取锁
                 *
                 * 如果获取锁成功，马上将自己设置为head,把上一个节点移除
                 * 获取锁失败，尝试进入同步队列
                 */
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return interrupted;
                }
                /**
                 * 首次执行 shouldParkAfterFailedAcquire 将 node 头节点的 waitStatus 从0变为-1, 返回false
                 * 第二次执行 shouldParkAfterFailedAcquire 返回true,然后执行 parkAndCheckInterrupt
                 */
                // 没有头节点
                // 当前节点的前一个节点不是头节点
                // 当前节点的前一个节点是头节点，但是没有申请到资源
                // 线程不能被阻塞时，一直进行循环
                /**
                 * shouldParkAfterFailedAcquire 把node的前一个节点状态置为Singal
                 * 只要前一个节点状态是Singal,自己就可以阻塞
                 *
                 */
                if (shouldParkAfterFailedAcquire(p, node) &&
                        //阻塞当前线程，醒来时仍然在无限for循环，再次尝试获取锁
                        parkAndCheckInterrupt()){
                    interrupted = true;
                }
            }
        } finally {
            //获取锁失败，从队列中移除
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * Acquires in exclusive interruptible mode.
     *
     * @param arg the acquire argument
     */
    private void doAcquireInterruptibly(int arg)
            throws InterruptedException {
        //将当前线程节点放入队列中
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (; ; ) {
                //获取当前节点的前继节点
                final Node p = node.predecessor();
                /**
                 * 前继节点为头节点
                 * 1。前继节点现在占用lock
                 * 2. 前继节点是空节点，已经释放lock,
                 */
                if (p == head && tryAcquire(arg)) {

                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return;
                }
                //如果被中断，抛出异常信息
                if (shouldParkAfterFailedAcquire(p, node) &&
                        parkAndCheckInterrupt())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * Acquires in exclusive timed mode.
     *
     * @param arg          the acquire argument
     * @param nanosTimeout max wait time
     * @return {@code true} if acquired
     */
    private boolean doAcquireNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (nanosTimeout <= 0L)
            return false;
        final long deadline = System.nanoTime() + nanosTimeout;
        //添加一个同步节点
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return true;
                }
                nanosTimeout = deadline - System.nanoTime();
                if (nanosTimeout <= 0L)
                    return false;
                if (shouldParkAfterFailedAcquire(p, node) &&
                        nanosTimeout > spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if (Thread.interrupted())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * Acquires in shared uninterruptible mode.
     *
     * @param arg the acquire argument
     *            <p>
     *            获取共享锁
     */
    private void doAcquireShared(int arg) {
        // 将当前节点加入队列中
        // 队尾添加一个共享类型节点
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            boolean interrupted = false;
            for (; ; ) {
                //获取节点的前一个节点
                final Node p = node.predecessor();
                /**
                 * 前继节点为头节点
                 * 1。前继节点现在占用lock
                 * 2. 前继节点是空节点，已经释放lock,
                 */
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        //获取lock成功，设置新的head,并唤醒后继获取 readLock节点
                        //设置头节点，唤醒后继节点
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        //在获取lock时被中断过，自我再中断一次
                        if (interrupted)
                            selfInterrupt();
                        failed = false;
                        return;
                    }
                }
                //判断是否需要进行中断
                if (shouldParkAfterFailedAcquire(p, node) &&
                        // lock仍被其他线程阻塞，则睡眠，并返回是否线程是否处于被中断状态
                        parkAndCheckInterrupt())
                    interrupted = true;
            }
        } finally {
            if (failed)
                //请求Node节点并删除
                cancelAcquire(node);
        }
    }

    /**
     * Acquires in shared interruptible mode.
     *
     * @param arg the acquire argument
     */
    private void doAcquireSharedInterruptibly(int arg)
            throws InterruptedException {
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        failed = false;
                        return;
                    }
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                        parkAndCheckInterrupt())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * Acquires in shared timed mode.
     *
     * @param arg          the acquire argument
     * @param nanosTimeout max wait time
     * @return {@code true} if acquired
     */
    private boolean doAcquireSharedNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (nanosTimeout <= 0L)
            return false;
        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        failed = false;
                        return true;
                    }
                }
                nanosTimeout = deadline - System.nanoTime();
                if (nanosTimeout <= 0L)
                    return false;
                if (shouldParkAfterFailedAcquire(p, node) &&
                        nanosTimeout > spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if (Thread.interrupted())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    // Main exported methods

    /**
     * Attempts to acquire in exclusive mode. This method should query
     * if the state of the object permits it to be acquired in the
     * exclusive mode, and if so to acquire it.
     *
     * <p>This method is always invoked by the thread performing
     * acquire.  If this method reports failure, the acquire method
     * may queue the thread, if it is not already queued, until it is
     * signalled by a release from some other thread. This can be used
     * to implement method {@link Lock#tryLock()}.
     *
     * 接受一个申请使用独占模式，这个方法需要查询 如果state是一个对象的许可证，这是申请独占模式，如果申请他
     * 这个方法经常通过线程执行申请，如果这个方法需要公平，这个申请的方法的线程进入队列
     * 如果不再队列中，直到它被唤醒通过其它线程的释放，这里可以使用实现子类的 tryLock 方法
     *
     * <p>The default
     * implementation throws {@link UnsupportedOperationException}.
     *
     * @param arg the acquire argument. This value is always the one
     *            passed to an acquire method, or is the value saved on entry
     *            to a condition wait.  The value is otherwise uninterpreted
     *            and can represent anything you like.
     * @return {@code true} if successful. Upon success, this object has
     * been acquired.
     * @throws IllegalMonitorStateException  if acquiring would place this
     *                                       synchronizer in an illegal state. This exception must be
     *                                       thrown in a consistent fashion for synchronization to work
     *                                       correctly.
     * @throws UnsupportedOperationException if exclusive mode is not supported
     *                                       <p>
     *                                       TODO 独占式获取同步状态，获取成功返回true,失败返回 false
     *                                       将state的初始值设置为0，线程进行某项独占操作前，判断state是否为0。不是0代表别的线程已经进入该操作，本线程需要阻塞等待
     *                                       如果是0，将state设置为1，如果设置成功，自己进入该操作
     *                                       通过CAS保证原子性，称为尝试获取同步状态
     *                                       <p>
     *                                       如果一个线程A获取同步状态成功，将state从0设置为1，另外线程B尝试获取时，发现state为1，进入等待状态，直到 线程 A 将其释放，将state设置为0
     *                                       并通知后续等待线程
     *                                       <p>
     *                                       TODO 不同的同步工具针对的具体并发场景不同，如何获取同步状态，如何释放同步状态需要在自定义的AQS子类中实现
     *                                       <p>
     *                                       独占模式下，需要重写 tryAcquire、tryRelease和isHeldExclusively方法
     *                                       共享模式下，重写 tryAcquireShared和tryReleaseShared
     */
    protected boolean tryAcquire(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Attempts to set the state to reflect a release in exclusive
     * mode.
     *
     * <p>This method is always invoked by the thread performing release.
     *
     * <p>The default implementation throws
     * {@link UnsupportedOperationException}.
     *
     * @param arg the release argument. This value is always the one
     *            passed to a release method, or the current state value upon
     *            entry to a condition wait.  The value is otherwise
     *            uninterpreted and can represent anything you like.
     * @return {@code true} if this object is now in a fully released
     * state, so that any waiting threads may attempt to acquire;
     * and {@code false} otherwise.
     * @throws IllegalMonitorStateException  if releasing would place this
     *                                       synchronizer in an illegal state. This exception must be
     *                                       thrown in a consistent fashion for synchronization to work
     *                                       correctly.
     * @throws UnsupportedOperationException if exclusive mode is not supported
     *                                       <p>
     *                                       独占式释放同步状态，成功返回true,失败返回false
     */
    protected boolean tryRelease(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Attempts to acquire in shared mode. This method should query if
     * the state of the object permits it to be acquired in the shared
     * mode, and if so to acquire it.
     *
     * <p>This method is always invoked by the thread performing
     * acquire.  If this method reports failure, the acquire method
     * may queue the thread, if it is not already queued, until it is
     * signalled by a release from some other thread.
     *
     * <p>The default implementation throws {@link
     * UnsupportedOperationException}.
     *
     * @param arg the acquire argument. This value is always the one
     *            passed to an acquire method, or is the value saved on entry
     *            to a condition wait.  The value is otherwise uninterpreted
     *            and can represent anything you like.
     * @return a negative value on failure; zero if acquisition in shared
     * mode succeeded but no subsequent shared-mode acquire can
     * succeed; and a positive value if acquisition in shared
     * mode succeeded and subsequent shared-mode acquires might
     * also succeed, in which case a subsequent waiting thread
     * must check availability. (Support for three different
     * return values enables this method to be used in contexts
     * where acquires only sometimes act exclusively.)  Upon
     * success, this object has been acquired.
     * @throws IllegalMonitorStateException  if acquiring would place this
     *                                       synchronizer in an illegal state. This exception must be
     *                                       thrown in a consistent fashion for synchronization to work
     *                                       correctly.
     * @throws UnsupportedOperationException if shared mode is not supported
     *                                       <p>
     *                                       <p>
     *                                       共享模式获取同步状态
     *                                       共享模式
     *                                       如果某项操作允许10个线程同时进行，超过该数量的线程需要阻塞等待。
     *                                       将state设置为10，一个线程尝试获取同步状态 需要判断 state是否大于0，如果不大于0，意味着当前有10个线程进行操作，该线程需要进行阻塞等待
     *                                       如果state大于0，那么可以把state的值减1后进入该擦操作，每当一个线程完成操作的时候需要释放同步状态，将state的值 加1，并通知后续等待的线程
     */
    protected int tryAcquireShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Attempts to set the state to reflect a release in shared mode.
     *
     * <p>This method is always invoked by the thread performing release.
     *
     * <p>The default implementation throws
     * {@link UnsupportedOperationException}.
     *
     * @param arg the release argument. This value is always the one
     *            passed to a release method, or the current state value upon
     *            entry to a condition wait.  The value is otherwise
     *            uninterpreted and can represent anything you like.
     * @return {@code true} if this release of shared mode may permit a
     * waiting acquire (shared or exclusive) to succeed; and
     * {@code false} otherwise
     * @throws IllegalMonitorStateException  if releasing would place this
     *                                       synchronizer in an illegal state. This exception must be
     *                                       thrown in a consistent fashion for synchronization to work
     *                                       correctly.
     * @throws UnsupportedOperationException if shared mode is not supported
     *                                       <p>
     *                                       共享模式释放同步状态
     */
    protected boolean tryReleaseShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns {@code true} if synchronization is held exclusively with
     * respect to the current (calling) thread.  This method is invoked
     * upon each call to a non-waiting {@link ConditionObject} method.
     * (Waiting methods instead invoke {@link #release}.)
     *
     * <p>The default implementation throws {@link
     * UnsupportedOperationException}. This method is invoked
     * internally only within {@link ConditionObject} methods, so need
     * not be defined if conditions are not used.
     *
     * @return {@code true} if synchronization is held exclusively;
     * {@code false} otherwise
     * @throws UnsupportedOperationException if conditions are not supported
     *                                       <p>
     *                                       独占模式下，如果当前线程已经获取到同步状态，返回true,否则返回false
     */
    protected boolean isHeldExclusively() {
        throw new UnsupportedOperationException();
    }

    /**
     * Acquires in exclusive mode, ignoring interrupts.  Implemented
     * by invoking at least once {@link #tryAcquire},
     * returning on success.  Otherwise the thread is queued, possibly
     * repeatedly blocking and unblocking, invoking {@link
     * #tryAcquire} until success.  This method can be used
     * to implement method {@link Lock#lock}.
     *
     * 申请独占模式，忽略中断
     * 实现通过执行至少一次 tryAcquire ，返回success
     * 除非线程在队列中，可能反复阻塞和不阻塞，执行 tryAcquire 直到成功
     * 这个方法能被用于实现方法lock
     *
     * @param arg the acquire argument.  This value is conveyed to
     *            {@link #tryAcquire} but is otherwise uninterpreted and
     *            can represent anything you like.
     *            <p>
     *            独占式获取同步状态，如果获取成功则返回，获取失败则将当前线程包装成Node节点插入同步队列中
     *            <p>
     *            TODO 参数 arg 代表什么？
     *
     * arg 表示申请所需要的值，用于和state进行计算，由子类实现
     */
    public final void acquire(int arg) {
        // 尝试获取同步状态，获取失败执行 acquireQueued 将其插入同步队列中
        if (!tryAcquire(arg) &&
                // 节点模式是独占模式
                acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
            selfInterrupt();
    }

    /**
     * Acquires in exclusive mode, aborting if interrupted.
     * Implemented by first checking interrupt status, then invoking
     * at least once {@link #tryAcquire}, returning on
     * success.  Otherwise the thread is queued, possibly repeatedly
     * blocking and unblocking, invoking {@link #tryAcquire}
     * until success or the thread is interrupted.  This method can be
     * used to implement method {@link Lock#lockInterruptibly}.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *            {@link #tryAcquire} but is otherwise uninterpreted and
     *            can represent anything you like.
     * @throws InterruptedException if the current thread is interrupted
     *                              <p>
     *                              一个线程再执行本方法过程中被中断，则抛出 InterruptedException 异常
     */
    public final void acquireInterruptibly(int arg)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        if (!tryAcquire(arg))
            doAcquireInterruptibly(arg);
    }

    /**
     * Attempts to acquire in exclusive mode, aborting if interrupted,
     * and failing if the given timeout elapses.  Implemented by first
     * checking interrupt status, then invoking at least once {@link
     * #tryAcquire}, returning on success.  Otherwise, the thread is
     * queued, possibly repeatedly blocking and unblocking, invoking
     * {@link #tryAcquire} until success or the thread is interrupted
     * or the timeout elapses.  This method can be used to implement
     * method {@link Lock#tryLock(long, TimeUnit)}.
     *
     * @param arg          the acquire argument.  This value is conveyed to
     *                     {@link #tryAcquire} but is otherwise uninterpreted and
     *                     can represent anything you like.
     * @param nanosTimeout the maximum number of nanoseconds to wait
     * @return {@code true} if acquired; {@code false} if timed out
     * @throws InterruptedException if the current thread is interrupted
     *                              <p>
     *                              独占获取同步状态，添加超时时间，如果给定时间内没有获取到同步状态，返回false
     */
    public final boolean tryAcquireNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        return tryAcquire(arg) ||
                doAcquireNanos(arg, nanosTimeout);
    }

    /**
     * Releases in exclusive mode.  Implemented by unblocking one or
     * more threads if {@link #tryRelease} returns true.
     * This method can be used to implement method {@link Lock#unlock}.
     *
     * @param arg the release argument.  This value is conveyed to
     *            {@link #tryRelease} but is otherwise uninterpreted and
     *            can represent anything you like.
     * @return the value returned from {@link #tryRelease}
     * <p>
     * 释放同步状态，同步把同步队列第一个非0号节点代表的线程唤醒
     * 从对头开始，找它的下一个节点，如果下一个节点是空的，从尾部开始，一直找到状态不是取消的节点
     */
    public final boolean release(int arg) {
        if (tryRelease(arg)) {
            Node h = head;
            //头节点不是空的，并且非初始化状态
            if (h != null && h.waitStatus != 0)
                unparkSuccessor(h);
            return true;
        }
        return false;
    }

    /**
     * Acquires in shared mode, ignoring interrupts.  Implemented by
     * first invoking at least once {@link #tryAcquireShared},
     * returning on success.  Otherwise the thread is queued, possibly
     * repeatedly blocking and unblocking, invoking {@link
     * #tryAcquireShared} until success.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *            {@link #tryAcquireShared} but is otherwise uninterpreted
     *            and can represent anything you like.
     *            <p>
     *            调用子方法 tryAcquireShared 获取同步状态，
     *            如果state的值为0，其他线程无法获取到同步状态，被包装成Node节点进入同步等待队列中
     */
    public final void acquireShared(int arg) {
        if (tryAcquireShared(arg) < 0)
            doAcquireShared(arg);
    }

    /**
     * Acquires in shared mode, aborting if interrupted.  Implemented
     * by first checking interrupt status, then invoking at least once
     * {@link #tryAcquireShared}, returning on success.  Otherwise the
     * thread is queued, possibly repeatedly blocking and unblocking,
     * invoking {@link #tryAcquireShared} until success or the thread
     * is interrupted.
     *
     * @param arg the acquire argument.
     *            This value is conveyed to {@link #tryAcquireShared} but is
     *            otherwise uninterpreted and can represent anything
     *            you like.
     * @throws InterruptedException if the current thread is interrupted
     */
    public final void acquireSharedInterruptibly(int arg)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        if (tryAcquireShared(arg) < 0)
            doAcquireSharedInterruptibly(arg);
    }

    /**
     * Attempts to acquire in shared mode, aborting if interrupted, and
     * failing if the given timeout elapses.  Implemented by first
     * checking interrupt status, then invoking at least once {@link
     * #tryAcquireShared}, returning on success.  Otherwise, the
     * thread is queued, possibly repeatedly blocking and unblocking,
     * invoking {@link #tryAcquireShared} until success or the thread
     * is interrupted or the timeout elapses.
     *
     * @param arg          the acquire argument.  This value is conveyed to
     *                     {@link #tryAcquireShared} but is otherwise uninterpreted
     *                     and can represent anything you like.
     * @param nanosTimeout the maximum number of nanoseconds to wait
     * @return {@code true} if acquired; {@code false} if timed out
     * @throws InterruptedException if the current thread is interrupted
     */
    public final boolean tryAcquireSharedNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        return tryAcquireShared(arg) >= 0 ||
                doAcquireSharedNanos(arg, nanosTimeout);
    }

    /**
     * Releases in shared mode.  Implemented by unblocking one or more
     * threads if {@link #tryReleaseShared} returns true.
     *
     * @param arg the release argument.  This value is conveyed to
     *            {@link #tryReleaseShared} but is otherwise uninterpreted
     *            and can represent anything you like.
     * @return the value returned from {@link #tryReleaseShared}
     */
    public final boolean releaseShared(int arg) {
        if (tryReleaseShared(arg)) {
            doReleaseShared();
            return true;
        }
        return false;
    }

    // Queue inspection methods

    /**
     * Queries whether any threads are waiting to acquire. Note that
     * because cancellations due to interrupts and timeouts may occur
     * at any time, a {@code true} return does not guarantee that any
     * other thread will ever acquire.
     *
     * <p>In this implementation, this operation returns in
     * constant time.
     *
     * @return {@code true} if there may be other threads waiting to acquire
     * <p>
     * 是否有正在等待获取同步状态的线程
     */
    public final boolean hasQueuedThreads() {
        return head != tail;
    }

    /**
     * Queries whether any threads have ever contended to acquire this
     * synchronizer; that is if an acquire method has ever blocked.
     *
     * <p>In this implementation, this operation returns in
     * constant time.
     *
     * @return {@code true} if there has ever been contention
     * <p>
     * 是否有某个线程曾经因为获取不到同步状态而阻塞
     */
    public final boolean hasContended() {
        return head != null;
    }

    /**
     * Returns the first (longest-waiting) thread in the queue, or
     * {@code null} if no threads are currently queued.
     *
     * <p>In this implementation, this operation normally returns in
     * constant time, but may iterate upon contention if other threads are
     * concurrently modifying the queue.
     *
     * @return the first (longest-waiting) thread in the queue, or
     * {@code null} if no threads are currently queued
     * <p>
     * 返回队列中第一个（等待时间最长的线程），如果目前没有等待线程，返回null
     */
    public final Thread getFirstQueuedThread() {
        // handle only fast path, else relay
        return (head == tail) ? null : fullGetFirstQueuedThread();
    }

    /**
     * Version of getFirstQueuedThread called when fastpath fails
     */
    private Thread fullGetFirstQueuedThread() {
        /*
         * The first node is normally head.next. Try to get its
         * thread field, ensuring consistent reads: If thread
         * field is nulled out or s.prev is no longer head, then
         * some other thread(s) concurrently performed setHead in
         * between some of our reads. We try this twice before
         * resorting to traversal.
         */
        Node h, s;
        Thread st;
        if (((h = head) != null && (s = h.next) != null &&
                s.prev == head && (st = s.thread) != null) ||
                ((h = head) != null && (s = h.next) != null &&
                        s.prev == head && (st = s.thread) != null))
            return st;

        /*
         * Head's next field might not have been set yet, or may have
         * been unset after setHead. So we must check to see if tail
         * is actually first node. If not, we continue on, safely
         * traversing from tail back to head to find first,
         * guaranteeing termination.
         */

        Node t = tail;
        Thread firstThread = null;
        while (t != null && t != head) {
            Thread tt = t.thread;
            if (tt != null)
                firstThread = tt;
            t = t.prev;
        }
        return firstThread;
    }

    /**
     * Returns true if the given thread is currently queued.
     *
     * <p>This implementation traverses the queue to determine
     * presence of the given thread.
     *
     * @param thread the thread
     * @return {@code true} if the given thread is on the queue
     * @throws NullPointerException if the thread is null
     *                              <p>
     *                              判断某个线程是否在等待队列中
     */
    public final boolean isQueued(Thread thread) {
        if (thread == null)
            throw new NullPointerException();
        for (Node p = tail; p != null; p = p.prev)
            if (p.thread == thread)
                return true;
        return false;
    }

    /**
     * Returns {@code true} if the apparent first queued thread, if one
     * exists, is waiting in exclusive mode.  If this method returns
     * {@code true}, and the current thread is attempting to acquire in
     * shared mode (that is, this method is invoked from {@link
     * #tryAcquireShared}) then it is guaranteed that the current thread
     * is not the first queued thread.  Used only as a heuristic in
     * ReentrantReadWriteLock.
     */
    final boolean apparentlyFirstQueuedIsExclusive() {
        Node h, s;
        return (h = head) != null &&
                (s = h.next) != null &&
                !s.isShared() &&
                s.thread != null;
    }

    /**
     * Queries whether any threads have been waiting to acquire longer
     * than the current thread.
     *
     * <p>An invocation of this method is equivalent to (but may be
     * more efficient than):
     * <pre> {@code
     * getFirstQueuedThread() != Thread.currentThread() &&
     * hasQueuedThreads()}</pre>
     *
     * <p>Note that because cancellations due to interrupts and
     * timeouts may occur at any time, a {@code true} return does not
     * guarantee that some other thread will acquire before the current
     * thread.  Likewise, it is possible for another thread to win a
     * race to enqueue after this method has returned {@code false},
     * due to the queue being empty.
     *
     * <p>This method is designed to be used by a fair synchronizer to
     * avoid <a href="AbstractQueuedSynchronizer#barging">barging</a>.
     * Such a synchronizer's {@link #tryAcquire} method should return
     * {@code false}, and its {@link #tryAcquireShared} method should
     * return a negative value, if this method returns {@code true}
     * (unless this is a reentrant acquire).  For example, the {@code
     * tryAcquire} method for a fair, reentrant, exclusive mode
     * synchronizer might look like this:
     *
     * <pre> {@code
     * protected boolean tryAcquire(int arg) {
     *   if (isHeldExclusively()) {
     *     // A reentrant acquire; increment hold count
     *     return true;
     *   } else if (hasQueuedPredecessors()) {
     *     return false;
     *   } else {
     *     // try to acquire normally
     *   }
     * }}</pre>
     *
     * @return {@code true} if there is a queued thread preceding the
     * current thread, and {@code false} if the current thread
     * is at the head of the queue or the queue is empty
     * @since 1.7
     * <p>
     * 前面是否有等待线程
     */
    public final boolean hasQueuedPredecessors() {
        // The correctness of this depends on head being initialized
        // before tail and on head.next being accurate if the current
        // thread is first in queue.
        Node t = tail; // Read fields in reverse initialization order
        Node h = head;
        Node s;

        return h != t && //队列中有等待节点 如果没有等待节点，当然是没有前驱节点了
                ((s = h.next) == null // 队列首节点后的节点不存在 lock.AbstractQueuedSynchronizer.acquireQueued 说明首节点已经被移出队列了
                        || s.thread != Thread.currentThread()); //首节点后的节点线程不属于当前线程，说明该节点的没有获取到资源，继续等待
    }


    // Instrumentation and monitoring methods

    /**
     * Returns an estimate of the number of threads waiting to
     * acquire.  The value is only an estimate because the number of
     * threads may change dynamically while this method traverses
     * internal data structures.  This method is designed for use in
     * monitoring system state, not for synchronization
     * control.
     *
     * @return the estimated number of threads waiting to acquire
     * <p>
     * 返回等待获取同步状态的线程估计值，多线程环境下实际线程集合可能发生大的变化
     */
    public final int getQueueLength() {
        int n = 0;
        for (Node p = tail; p != null; p = p.prev) {
            if (p.thread != null)
                ++n;
        }
        return n;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive monitoring facilities.
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            Thread t = p.thread;
            if (t != null)
                list.add(t);
        }
        return list;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire in exclusive mode. This has the same properties
     * as {@link #getQueuedThreads} except that it only returns
     * those threads waiting due to an exclusive acquire.
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getExclusiveQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (!p.isShared()) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire in shared mode. This has the same properties
     * as {@link #getQueuedThreads} except that it only returns
     * those threads waiting due to a shared acquire.
     *
     * @return the collection of threads
     * <p>
     * 返回包含可能正在获取的线程集合，多环境下实际集合可能发生改变
     */
    public final Collection<Thread> getSharedQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (p.isShared()) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    /**
     * Returns a string identifying this synchronizer, as well as its state.
     * The state, in brackets, includes the String {@code "State ="}
     * followed by the current value of {@link #getState}, and either
     * {@code "nonempty"} or {@code "empty"} depending on whether the
     * queue is empty.
     *
     * @return a string identifying this synchronizer, as well as its state
     */
    public String toString() {
        int s = getState();
        String q = hasQueuedThreads() ? "non" : "";
        return super.toString() +
                "[State = " + s + ", " + q + "empty queue]";
    }


    // Internal support methods for Conditions

    /**
     * Returns true if a node, always one that was initially placed on
     * a condition queue, is now waiting to reacquire on sync queue.
     *
     * @param node the node
     * @return true if is reacquiring
     */
    final boolean isOnSyncQueue(Node node) {
        if (node.waitStatus == Node.CONDITION || node.prev == null)
            return false;
        if (node.next != null) // If has successor, it must be on queue
            return true;
        /*
         * node.prev can be non-null, but not yet on queue because
         * the CAS to place it on queue can fail. So we have to
         * traverse from tail to make sure it actually made it.  It
         * will always be near the tail in calls to this method, and
         * unless the CAS failed (which is unlikely), it will be
         * there, so we hardly ever traverse much.
         */
        return findNodeFromTail(node);
    }

    /**
     * Returns true if node is on sync queue by searching backwards from tail.
     * Called only when needed by isOnSyncQueue.
     *
     * @return true if present
     */
    private boolean findNodeFromTail(Node node) {
        Node t = tail;
        for (; ; ) {
            if (t == node)
                return true;
            if (t == null)
                return false;
            t = t.prev;
        }
    }

    /**
     * Transfers a node from a condition queue onto sync queue.
     * Returns true if successful.
     *
     * @param node the node
     * @return true if successfully transferred (else the node was
     * cancelled before signal)
     * 节点转移到同步队列中
     */
    final boolean transferForSignal(Node node) {
        /*
         * If cannot change waitStatus, the node has been cancelled.
         */
        if (!compareAndSetWaitStatus(node, Node.CONDITION, 0))
            return false;

        /*
         * Splice onto queue and try to set waitStatus of predecessor to
         * indicate that thread is (probably) waiting. If cancelled or
         * attempt to set waitStatus fails, wake up to resync (in which
         * case the waitStatus can be transiently and harmlessly wrong).
         */
        Node p = enq(node);
        int ws = p.waitStatus;
        if (ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL))
            LockSupport.unpark(node.thread);
        return true;
    }

    /**
     * Transfers node, if necessary, to sync queue after a cancelled wait.
     * Returns true if thread was cancelled before being signalled.
     *
     * @param node the node
     * @return true if cancelled before the node was signalled
     */
    final boolean transferAfterCancelledWait(Node node) {
        if (compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
            enq(node);
            return true;
        }
        /*
         * If we lost out to a signal(), then we can't proceed
         * until it finishes its enq().  Cancelling during an
         * incomplete transfer is both rare and transient, so just
         * spin.
         */
        while (!isOnSyncQueue(node))
            Thread.yield();
        return false;
    }

    /**
     * Invokes release with current state value; returns saved state.
     * Cancels node and throws exception on failure.
     *
     * @param node the condition node for this wait
     * @return previous sync state
     */
    final int fullyRelease(Node node) {
        boolean failed = true;
        try {
            int savedState = getState();
            if (release(savedState)) {
                failed = false;
                return savedState;
            } else {
                throw new IllegalMonitorStateException();
            }
        } finally {
            if (failed)
                node.waitStatus = Node.CANCELLED;
        }
    }

    // Instrumentation methods for conditions

    /**
     * Queries whether the given ConditionObject
     * uses this synchronizer as its lock.
     *
     * @param condition the condition
     * @return {@code true} if owned
     * @throws NullPointerException if the condition is null
     */
    public final boolean owns(ConditionObject condition) {
        return condition.isOwnedBy(this);
    }

    /**
     * Queries whether any threads are waiting on the given condition
     * associated with this synchronizer. Note that because timeouts
     * and interrupts may occur at any time, a {@code true} return
     * does not guarantee that a future {@code signal} will awaken
     * any threads.  This method is designed primarily for use in
     * monitoring of the system state.
     *
     * @param condition the condition
     * @return {@code true} if there are any waiting threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *                                      is not held
     * @throws IllegalArgumentException     if the given condition is
     *                                      not associated with this synchronizer
     * @throws NullPointerException         if the condition is null
     */
    public final boolean hasWaiters(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.hasWaiters();
    }

    /**
     * Returns an estimate of the number of threads waiting on the
     * given condition associated with this synchronizer. Note that
     * because timeouts and interrupts may occur at any time, the
     * estimate serves only as an upper bound on the actual number of
     * waiters.  This method is designed for use in monitoring of the
     * system state, not for synchronization control.
     *
     * @param condition the condition
     * @return the estimated number of waiting threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *                                      is not held
     * @throws IllegalArgumentException     if the given condition is
     *                                      not associated with this synchronizer
     * @throws NullPointerException         if the condition is null
     */
    public final int getWaitQueueLength(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitQueueLength();
    }

    /**
     * Returns a collection containing those threads that may be
     * waiting on the given condition associated with this
     * synchronizer.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate. The elements of the
     * returned collection are in no particular order.
     *
     * @param condition the condition
     * @return the collection of threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *                                      is not held
     * @throws IllegalArgumentException     if the given condition is
     *                                      not associated with this synchronizer
     * @throws NullPointerException         if the condition is null
     */
    public final Collection<Thread> getWaitingThreads(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitingThreads();
    }

    /**
     * Condition implementation for a {@link
     * AbstractQueuedSynchronizer} serving as the basis of a {@link
     * Lock} implementation.
     *
     * <p>Method documentation for this class describes mechanics,
     * not behavioral specifications from the point of view of Lock
     * and Condition users. Exported versions of this class will in
     * general need to be accompanied by documentation describing
     * condition semantics that rely on those of the associated
     * {@code AbstractQueuedSynchronizer}.
     *
     * <p>This class is Serializable, but all fields are transient,
     * so deserialized conditions have no waiters.
     * <p>
     * <p>
     * 获取锁的线程因为某个条件不满足时，应该进入哪个队列等待，什么时候释放锁，如果某个线程完成了该等待条件，
     * 那么在持有相同锁的情况下，怎么从响应的等待队列中将等待的线程从队列中移出
     * <p>
     * <p>
     * 用于notify/notifyAll
     * <p>
     * 使用wait/signal/signalAll 方法需要在获取显示锁的情况下进行使用
     * <p>
     * 通过显示锁的newCondition方法产生Condition对象，线程在持有该显示锁的情况下可以调用生成的Condition对象的await/singal方法
     * <p>
     * 每个显示锁对象可以产生若干个 Condition 对象，每个Condition对象都会对应一个等待队列
     * 起到一个显示锁对应多个等待队列的效果
     */
    public class ConditionObject implements Condition, java.io.Serializable {
        private static final long serialVersionUID = 1173984872572414699L;
        /**
         * First node of condition queue.
         * 队列第一个等待者
         */
        private transient Node firstWaiter;
        /**
         * Last node of condition queue.
         * 队列最后一个等待者
         * <p>
         * 在等待队列中的线程被唤醒的时候需要重新获取锁，即重新获取同步状态，因此等待队列必须知道线程是在持有哪个锁的时候开始进行等待
         */
        private transient Node lastWaiter;

        /**
         * Creates a new {@code ConditionObject} instance.
         */
        public ConditionObject() {
        }

        // Internal methods

        /**
         * Adds a new waiter to wait queue.
         *
         * @return its new wait node
         */
        private Node addConditionWaiter() {
            Node t = lastWaiter;
            // If lastWaiter is cancelled, clean out.
            if (t != null && t.waitStatus != Node.CONDITION) {
                unlinkCancelledWaiters();
                t = lastWaiter;
            }
            Node node = new Node(Thread.currentThread(), Node.CONDITION);
            if (t == null)
                firstWaiter = node;
            else
                t.nextWaiter = node;
            lastWaiter = node;
            return node;
        }

        /**
         * Removes and transfers nodes until hit non-cancelled one or
         * null. Split out from signal in part to encourage compilers
         * to inline the case of no waiters.
         *
         * @param first (non-null) the first node on condition queue
         */
        private void doSignal(Node first) {
            do {
                // nextWaiter 是空的，说明已经到了队尾了，直接把表头的.next置为null.将头节点移除队列
                if ((firstWaiter = first.nextWaiter) == null){
                    lastWaiter = null;
                }
                first.nextWaiter = null;
            } while (!transferForSignal(first) &&
                    (first = firstWaiter) != null);
        }

        /**
         * Removes and transfers all nodes.
         *
         * @param first (non-null) the first node on condition queue
         */
        private void doSignalAll(Node first) {
            lastWaiter = firstWaiter = null;
            do {
                Node next = first.nextWaiter;
                first.nextWaiter = null;
                transferForSignal(first);
                first = next;
            } while (first != null);
        }

        /**
         * Unlinks cancelled waiter nodes from condition queue.
         * Called only while holding lock. This is called when
         * cancellation occurred during condition wait, and upon
         * insertion of a new waiter when lastWaiter is seen to have
         * been cancelled. This method is needed to avoid garbage
         * retention in the absence of signals. So even though it may
         * require a full traversal, it comes into play only when
         * timeouts or cancellations occur in the absence of
         * signals. It traverses all nodes rather than stopping at a
         * particular target to unlink all pointers to garbage nodes
         * without requiring many re-traversals during cancellation
         * storms.
         */
        private void unlinkCancelledWaiters() {
            Node t = firstWaiter;
            Node trail = null;
            while (t != null) {
                Node next = t.nextWaiter;
                if (t.waitStatus != Node.CONDITION) {
                    t.nextWaiter = null;
                    if (trail == null)
                        firstWaiter = next;
                    else
                        trail.nextWaiter = next;
                    if (next == null)
                        lastWaiter = trail;
                } else
                    trail = t;
                t = next;
            }
        }

        // public methods

        /**
         * Moves the longest-waiting thread, if one exists, from the
         * wait queue for this condition to the wait queue for the
         * owning lock.
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *                                      returns {@code false}
         *                                      <p>
         *                                      唤醒一个线程
         */
        public final void signal() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            Node first = firstWaiter;
            if (first != null)
                doSignal(first);
        }

        /**
         * Moves all threads from the wait queue for this condition to
         * the wait queue for the owning lock.
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *                                      returns {@code false}
         *                                      唤醒所有线程
         */
        public final void signalAll() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            Node first = firstWaiter;
            if (first != null)
                doSignalAll(first);
        }

        /**
         * Implements uninterruptible condition wait.
         * <ol>
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         * throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled.
         * <li> Reacquire by invoking specialized version of
         * {@link #acquire} with saved state as argument.
         * </ol>
         * <p>
         * 当前线程进入等待状态，直到在等待状态中被通知
         */
        public final void awaitUninterruptibly() {
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            boolean interrupted = false;
            while (!isOnSyncQueue(node)) {
                LockSupport.park(this);
                if (Thread.interrupted())
                    interrupted = true;
            }
            if (acquireQueued(node, savedState) || interrupted)
                selfInterrupt();
        }

        /*
         * For interruptible waits, we need to track whether to throw
         * InterruptedException, if interrupted while blocked on
         * condition, versus reinterrupt current thread, if
         * interrupted while blocked waiting to re-acquire.
         */

        /**
         * Mode meaning to reinterrupt on exit from wait
         */
        private static final int REINTERRUPT = 1;
        /**
         * Mode meaning to throw InterruptedException on exit from wait
         */
        private static final int THROW_IE = -1;

        /**
         * Checks for interrupt, returning THROW_IE if interrupted
         * before signalled, REINTERRUPT if after signalled, or
         * 0 if not interrupted.
         */
        private int checkInterruptWhileWaiting(Node node) {
            return Thread.interrupted() ?
                    (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) :
                    0;
        }

        /**
         * Throws InterruptedException, reinterrupts current thread, or
         * does nothing, depending on mode.
         */
        private void reportInterruptAfterWait(int interruptMode)
                throws InterruptedException {
            if (interruptMode == THROW_IE)
                throw new InterruptedException();
            else if (interruptMode == REINTERRUPT)
                selfInterrupt();
        }

        /**
         * Implements interruptible condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         * throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled or interrupted.
         * <li> Reacquire by invoking specialized version of
         * {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * </ol>
         * <p>
         * 当前线程进入等待，直到被通知或中断
         */
        public final void await() throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            //加入到条件队列的队尾
            Node node = addConditionWaiter();

            /**
             * 释放lock时申请的资源，唤醒同步队列头节点，因为当前线程陷入阻塞，必须释放之前lock资源
             * 否则自己不被唤醒，别的线程永远不能获取到该共享资源
             */
            int savedState = fullyRelease(node);
            int interruptMode = 0;

            /**
             * 确认node不在同步队列上，再阻塞，如果 node 在同步队列上，不能进行上锁
             *
             *
             *
             */
            while (!isOnSyncQueue(node)) {
                LockSupport.park(this);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null) // clean up if cancelled
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
        }

        /**
         * Implements timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         * throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         * {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * </ol>
         * 等待时间为纳秒
         */
        public final long awaitNanos(long nanosTimeout)
                throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            final long deadline = System.nanoTime() + nanosTimeout;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    transferAfterCancelledWait(node);
                    break;
                }
                if (nanosTimeout >= spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
                nanosTimeout = deadline - System.nanoTime();
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return deadline - System.nanoTime();
        }

        /**
         * Implements absolute timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         * throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         * {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * <li> If timed out while blocked in step 4, return false, else true.
         * </ol>
         * <p>
         * 当前线程进入等待状态，如果达到最后期限或在等待状态中被通知或中断则返回
         */
        public final boolean awaitUntil(Date deadline)
                throws InterruptedException {
            long abstime = deadline.getTime();
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            boolean timedout = false;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (System.currentTimeMillis() > abstime) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }
                LockSupport.parkUntil(this, abstime);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return !timedout;
        }

        /**
         * Implements timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         * throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         * {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * <li> If timed out while blocked in step 4, return false, else true.
         * </ol>
         * <p>
         * 当前线程在指定时间进入等待状态，如果超出指定时间或者在等待状态中被通知或中断则返回
         */
        public final boolean await(long time, TimeUnit unit)
                throws InterruptedException {
            long nanosTimeout = unit.toNanos(time);
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            final long deadline = System.nanoTime() + nanosTimeout;
            boolean timedout = false;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }
                if (nanosTimeout >= spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
                nanosTimeout = deadline - System.nanoTime();
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return !timedout;
        }

        //  support for instrumentation

        /**
         * Returns true if this condition was created by the given
         * synchronization object.
         *
         * @return {@code true} if owned
         */
        final boolean isOwnedBy(AbstractQueuedSynchronizer sync) {
            return sync == AbstractQueuedSynchronizer.this;
        }

        /**
         * Queries whether any threads are waiting on this condition.
         * Implements {@link AbstractQueuedSynchronizer#hasWaiters(ConditionObject)}.
         *
         * @return {@code true} if there are any waiting threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *                                      returns {@code false}
         */
        protected final boolean hasWaiters() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION)
                    return true;
            }
            return false;
        }

        /**
         * Returns an estimate of the number of threads waiting on
         * this condition.
         * Implements {@link AbstractQueuedSynchronizer#getWaitQueueLength(ConditionObject)}.
         *
         * @return the estimated number of waiting threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *                                      returns {@code false}
         */
        protected final int getWaitQueueLength() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            int n = 0;
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION)
                    ++n;
            }
            return n;
        }

        /**
         * Returns a collection containing those threads that may be
         * waiting on this Condition.
         * Implements {@link AbstractQueuedSynchronizer#getWaitingThreads(ConditionObject)}.
         *
         * @return the collection of threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *                                      returns {@code false}
         */
        protected final Collection<Thread> getWaitingThreads() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            ArrayList<Thread> list = new ArrayList<Thread>();
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION) {
                    Thread t = w.thread;
                    if (t != null)
                        list.add(t);
                }
            }
            return list;
        }
    }

    /**
     * Setup to support compareAndSet. We need to natively implement
     * this here: For the sake of permitting future enhancements, we
     * cannot explicitly subclass AtomicInteger, which would be
     * efficient and useful otherwise. So, as the lesser of evils, we
     * natively implement using hotspot intrinsics API. And while we
     * are at it, we do the same for other CASable fields (which could
     * otherwise be done with atomic field updaters).
     */
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static final long stateOffset;
    private static final long headOffset;
    private static final long tailOffset;
    private static final long waitStatusOffset;
    private static final long nextOffset;

    static {
        try {
            stateOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.class.getDeclaredField("state"));
            headOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.class.getDeclaredField("head"));
            tailOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.class.getDeclaredField("tail"));
            waitStatusOffset = unsafe.objectFieldOffset
                    (Node.class.getDeclaredField("waitStatus"));
            nextOffset = unsafe.objectFieldOffset
                    (Node.class.getDeclaredField("next"));

        } catch (Exception ex) {
            throw new Error(ex);
        }
    }

    /**
     * CAS head field. Used only by enq.
     * TODO 什么地方 SetHead ?
     */
    private final boolean compareAndSetHead(Node update) {
        return unsafe.compareAndSwapObject(this, headOffset, null, update);
    }

    /**
     * CAS tail field. Used only by enq.
     */
    private final boolean compareAndSetTail(Node expect, Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }

    /**
     * CAS waitStatus field of a node.
     */
    private static final boolean compareAndSetWaitStatus(Node node,
                                                         int expect,
                                                         int update) {
        return unsafe.compareAndSwapInt(node, waitStatusOffset,
                expect, update);
    }

    /**
     * CAS next field of a node.
     */
    private static final boolean compareAndSetNext(Node node,
                                                   Node expect,
                                                   Node update) {
        return unsafe.compareAndSwapObject(node, nextOffset, expect, update);
    }
}
