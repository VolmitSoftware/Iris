package com.volmit.iris.util.parallel;

import com.volmit.iris.util.math.M;
import com.volmit.iris.util.scheduling.SR;
import org.jetbrains.annotations.NotNull;

import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SyncExecutor implements Executor, AutoCloseable {
    private final CountDownLatch latch = new CountDownLatch(1);
    private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public SyncExecutor(int msPerTick) {
        new SR() {
            @Override
            public void run() {
                var time = M.ms() + msPerTick;
                while (time > M.ms()) {
                    Runnable r = queue.poll();
                    if (r == null) break;
                    r.run();
                }

                if (closed.get() && queue.isEmpty()) {
                    cancel();
                    latch.countDown();
                }
            }
        };
    }

    @Override
    public void execute(@NotNull Runnable command) {
        if (closed.get()) throw new IllegalStateException("Executor is closed!");
        queue.add(command);
    }

    @Override
    public void close() throws Exception {
        closed.set(true);
        latch.await();
    }
}
