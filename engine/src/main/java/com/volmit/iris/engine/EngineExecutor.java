package com.volmit.iris.engine;

import lombok.Data;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

@Data
public class EngineExecutor implements ForkJoinPool.ForkJoinWorkerThreadFactory, Thread.UncaughtExceptionHandler, Closeable {
    private final Engine engine;
    private final ForkJoinPool forks;

    public EngineExecutor(Engine engine)
    {
        this.engine = engine;
        forks = new ForkJoinPool(engine.getConfiguration().getThreads(), this, this, true);
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        e.printStackTrace();
    }

    @Override
    public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
        final ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
        worker.setName("Iris " + engine.getWorld().getName() + " Executor " + worker.getPoolIndex());
        return worker;
    }

    @Override
    public void close() throws IOException {
        forks.shutdownNow().forEach(Runnable::run);
    }
}
