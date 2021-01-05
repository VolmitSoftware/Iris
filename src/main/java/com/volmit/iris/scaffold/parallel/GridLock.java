package com.volmit.iris.scaffold.parallel;

import com.volmit.iris.scaffold.hunk.Hunk;
import com.volmit.iris.util.IORunnable;
import com.volmit.iris.util.NastyRunnable;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class GridLock
{
    private Hunk<ReentrantLock> locks;

    public GridLock(int x, int z)
    {
        this.locks = Hunk.newAtomicHunk(x, 1, z);
        locks.iterateSync((a,b,c) -> locks.set(a,b,c,new ReentrantLock()));
    }

    public void with(int x, int z, Runnable r)
    {
        lock(x, z);
        r.run();
        unlock(x, z);
    }

    public void withNasty(int x, int z, NastyRunnable r) throws Throwable {
        lock(x, z);
        r.run();
        unlock(x, z);
    }

    public void withIO(int x, int z, IORunnable r) throws IOException {
        lock(x, z);
        r.run();
        unlock(x, z);
    }

    public <T> T withResult(int x, int z, Supplier<T> r)
    {
        lock(x, z);
        T t = r.get();
        unlock(x, z);
        return t;
    }

    public void withAll(Runnable r)
    {
        locks.iterateSync((a,b,c,d) -> d.lock());
        r.run();
        locks.iterateSync((a,b,c,d) -> d.unlock());
    }

    public <T> T withAllResult(Supplier<T> r)
    {
        locks.iterateSync((a,b,c,d) -> d.lock());
        T t = r.get();
        locks.iterateSync((a,b,c,d) -> d.unlock());

        return t;
    }

    public boolean tryLock(int x, int z)
    {
        return locks.get(x,0,z).tryLock();
    }

    public boolean tryLock(int x, int z, long timeout)
    {
        try {
            return locks.get(x,0,z).tryLock(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
        }

        return false;
    }

    public void lock(int x, int z)
    {
        locks.get(x,0,z).lock();
    }

    public void unlock(int x, int z)
    {
        locks.get(x,0,z).unlock();
    }
}
