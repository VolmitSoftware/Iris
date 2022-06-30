package com.volmit.iris.engine;

import art.arcane.amulet.concurrent.J;
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
        i("Started Pool with " + engine.getConfiguration().getThreads() + " priority " + engine.getConfiguration().getThreadPriority() + " threads.");
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
        i("Shutting down generator pool");
        forks.shutdownNow().forEach((i) -> {
            try
            {
                i.run();
            }

            catch(Throwable e)
            {
                e.printStackTrace();
            }
        });
        i("Generator pool shutdown");
    }
}
