package testjava.concurrent;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * <Description>
 *
 * @author hushiye
 * @since 2020-08-26 15:40
 */
public class DoubleLock {

    public static class Sync extends AbstractQueuedSynchronizer {
        public Sync() {
            super();
            setState(2);
        }

        @Override
        protected int tryAcquireShared(int arg) {
            while (true) {
                int curState = getState();
                int next = curState - arg;
                if (compareAndSetState(curState, next)) {
                    return next;
                }
            }
        }

        @Override
        protected boolean tryReleaseShared(int arg) {
            while (true) {
                int curState = getState();
                int next = curState + arg;
                if (compareAndSetState(curState, next)) {
                    return true;
                }
            }
        }
    }


    private Sync sync = new Sync();

    public void lock() {
        sync.acquireShared(1);
    }

    public void unlock() {
        sync.acquireShared(1);
    }
}
