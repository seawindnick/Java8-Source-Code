package testjava.concurrent;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * <Description>
 *
 * @author hushiye
 * @since 2020-08-26 14:38
 */
public class PlainLock {
    public static class Sync extends AbstractQueuedSynchronizer {

        @Override
        protected boolean tryAcquire(int arg) {
            return compareAndSetState(0,1);
        }

        @Override
        protected boolean tryRelease(int arg) {
            setState(0);
            return true;
        }

        @Override
        protected boolean isHeldExclusively() {
            return getState() == 1;
        }
    }

    private Sync sync = new Sync();
    public void lock(){
        //直接使用 tryAcquire 会导致出现没有获取到锁的线程没有加入到队列中，导致任务的丢失
        sync.acquire(1);
    }

    public void unlock(){
        sync.release(1);
    }




}
