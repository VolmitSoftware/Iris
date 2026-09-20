/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedServerLevels;


import art.arcane.iris.spi.PlatformScheduler;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class ModdedScheduler implements PlatformScheduler {
    private static final int ASYNC_MAX_THREADS = Math.max(4, Runtime.getRuntime().availableProcessors());
    private static final long ASYNC_KEEP_ALIVE_SECONDS = 30L;
    private static final int ASYNC_BACKLOG_WARN = 8192;
    private static final long ASYNC_BACKLOG_WARN_INTERVAL_MILLIS = 30000L;
    private static final long DRAIN_BUDGET_NANOS = TimeUnit.MILLISECONDS.toNanos(5L);
    private static final int DRAIN_TASK_CAP = 512;

    private static volatile Thread mainThread;

    private volatile ThreadPoolExecutor asyncExecutor;
    private final ConcurrentLinkedQueue<Runnable> mainQueue = new ConcurrentLinkedQueue<>();
    private final PriorityBlockingQueue<DelayedTask> delayedQueue = new PriorityBlockingQueue<>();
    private final AtomicLong currentTick = new AtomicLong();
    private final AtomicLong delayedSequence = new AtomicLong();
    private final AtomicLong lastBacklogWarnAt = new AtomicLong();

    public ModdedScheduler() {
        this.asyncExecutor = createAsyncExecutor();
    }

    private static ThreadPoolExecutor createAsyncExecutor() {
        // Unbounded queue: async work must never be executed inline on a tick thread (CallerRunsPolicy
        // stalled the server tick under load). Core == max with core timeout keeps the pool elastic,
        // which a LinkedBlockingQueue would otherwise pin to the core size.
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            ASYNC_MAX_THREADS,
            ASYNC_MAX_THREADS,
            ASYNC_KEEP_ALIVE_SECONDS,
            TimeUnit.SECONDS,
            workQueue,
            new AsyncThreadFactory(),
            dropRejectedTask());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static RejectedExecutionHandler dropRejectedTask() {
        return (Runnable task, ThreadPoolExecutor executor) -> {
            if (task instanceof RejectionAwareTask rejectionAwareTask) {
                rejectionAwareTask.reject();
            }
            if (executor.isShutdown()) {
                ModdedIrisLog.debug("Iris async task dropped: scheduler is shut down");
                return;
            }
            ModdedIrisLog.error("Iris async task rejected by the executor (queued={} active={})",
                    executor.getQueue().size(), executor.getActiveCount());
        };
    }

    public static void tick(Thread running) {
        if (running == null) {
            return;
        }
        if (mainThread != running) {
            mainThread = running;
        }
        ModdedScheduler scheduler = ModdedEngineBootstrap.schedulerOrNull();
        if (scheduler == null) {
            return;
        }
        scheduler.drain();
    }

    public static boolean isMainThread() {
        Thread running = mainThread;
        return running != null && Thread.currentThread() == running;
    }

    @Override
    public void global(Runnable task) {
        if (task == null) {
            return;
        }
        if (isMainThread()) {
            runGuarded(task);
            return;
        }
        mainQueue.add(task);
    }

    @Override
    public void region(NativeWorld world, int chunkX, int chunkZ, Runnable task) {
        global(task);
    }

    @Override
    public void async(Runnable task) {
        if (task == null) {
            return;
        }
        ThreadPoolExecutor executor = asyncExecutor;
        warnOnBacklog(executor);
        executor.execute(() -> runGuarded(task));
    }

    public boolean asyncIfRunning(Runnable task, Runnable rejection) {
        if (task == null) {
            return false;
        }
        ThreadPoolExecutor executor = asyncExecutor;
        RejectionAwareTask submittedTask = new RejectionAwareTask(
                () -> runGuarded(task),
                Objects.requireNonNull(rejection, "rejection")
        );
        warnOnBacklog(executor);
        try {
            executor.execute(submittedTask);
        } catch (RejectedExecutionException exception) {
            submittedTask.reject();
        }
        return !submittedTask.isRejected();
    }

    @Override
    public void laterGlobal(Runnable task, int ticks) {
        if (task == null) {
            return;
        }
        if (ticks <= 0) {
            global(task);
            return;
        }
        delayedQueue.add(new DelayedTask(currentTick.get() + ticks, delayedSequence.getAndIncrement(), task));
    }

    @Override
    public void laterRegion(NativeWorld world, int chunkX, int chunkZ, Runnable task, int ticks) {
        laterGlobal(task, ticks);
    }

    public void reset() {
        if (asyncExecutor.isShutdown()) {
            asyncExecutor = createAsyncExecutor();
        } else {
            List<Runnable> abandonedTasks = new ArrayList<>();
            asyncExecutor.getQueue().drainTo(abandonedTasks);
            rejectAbandonedTasks(abandonedTasks);
        }
        mainQueue.clear();
        delayedQueue.clear();
        // reset() only runs from ModdedEngineBootstrap.start at SERVER_STARTING, which every loader
        // fires on the server thread: capture it here instead of waiting for the first tick, so
        // global() cannot mistake a boot-time server-thread call for an off-thread one and defer it.
        mainThread = Thread.currentThread();
    }

    public void shutdown() {
        List<Runnable> abandonedTasks = asyncExecutor.shutdownNow();
        rejectAbandonedTasks(abandonedTasks);
        mainQueue.clear();
        delayedQueue.clear();
        mainThread = null;
        // Shutdown stage that always runs (ModdedEngineBootstrap.stop): release the level snapshot with it.
        ModdedServerLevels.forget();
    }

    private static void rejectAbandonedTasks(List<Runnable> abandonedTasks) {
        for (Runnable abandonedTask : abandonedTasks) {
            if (abandonedTask instanceof RejectionAwareTask rejectionAwareTask) {
                rejectionAwareTask.reject();
            }
        }
    }

    private void warnOnBacklog(ThreadPoolExecutor executor) {
        int queued = executor.getQueue().size();
        if (queued < ASYNC_BACKLOG_WARN) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = lastBacklogWarnAt.get();
        if (now - last < ASYNC_BACKLOG_WARN_INTERVAL_MILLIS || !lastBacklogWarnAt.compareAndSet(last, now)) {
            return;
        }
        ModdedIrisLog.warn("Iris async backlog {} tasks (threads={}); async work is falling behind", queued, executor.getPoolSize());
    }

    private void drain() {
        long tick = currentTick.incrementAndGet();
        promoteDelayed(tick);
        long deadline = System.nanoTime() + DRAIN_BUDGET_NANOS;
        int executed = 0;
        Runnable task;
        while ((task = mainQueue.poll()) != null) {
            runGuarded(task);
            executed++;
            if (executed >= DRAIN_TASK_CAP || System.nanoTime() >= deadline) {
                // Budget spent; the remainder stays queued in order and runs next tick.
                break;
            }
        }
    }

    /**
     * Due-ordered promotion; only the tick thread polls, so the head is always the earliest due task. A task
     * added while this runs is due at least one tick after the tick being promoted, so it sorts behind
     * everything this pass drains and is picked up on a later tick instead of being skipped. No per-tick
     * rebuild of the pending set.
     */
    private void promoteDelayed(long tick) {
        DelayedTask head;
        while ((head = delayedQueue.peek()) != null && head.dueTick() <= tick) {
            DelayedTask delayed = delayedQueue.poll();
            if (delayed == null) {
                return;
            }
            mainQueue.add(delayed.task());
        }
    }

    private void runGuarded(Runnable task) {
        try {
            task.run();
        } catch (Throwable error) {
            ModdedIrisLog.error("Iris scheduled task failed", error);
        }
    }

    private record DelayedTask(long dueTick, long sequence, Runnable task) implements Comparable<DelayedTask> {
        @Override
        public int compareTo(DelayedTask other) {
            int byTick = Long.compare(dueTick, other.dueTick);
            return byTick != 0 ? byTick : Long.compare(sequence, other.sequence);
        }
    }

    private static final class AsyncThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "iris-modded-async-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }

    private static final class RejectionAwareTask implements Runnable {
        private final Runnable task;
        private final Runnable rejection;
        private final AtomicBoolean rejected;

        private RejectionAwareTask(Runnable task, Runnable rejection) {
            this.task = task;
            this.rejection = rejection;
            rejected = new AtomicBoolean();
        }

        @Override
        public void run() {
            if (!rejected.get()) {
                task.run();
            }
        }

        private void reject() {
            if (rejected.compareAndSet(false, true)) {
                rejection.run();
            }
        }

        private boolean isRejected() {
            return rejected.get();
        }
    }
}
