/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.parallel;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;

public class MultiBurst implements ExecutorService {
    private static final long TIMEOUT = Long.getLong("iris.burst.timeout", 15000);
    public static final MultiBurst burst = new MultiBurst();
    public static final MultiBurst ioBurst = new MultiBurst("Iris IO", () -> IrisSettings.get().getConcurrency().getIoParallelism());
    private final AtomicLong last;
    private final String name;
    private final int priority;
    private final IntSupplier parallelism;
    private ExecutorService service;

    public MultiBurst() {
        this("Iris");
    }

    public MultiBurst(String name) {
        this(name, Thread.MIN_PRIORITY, () -> IrisSettings.get().getConcurrency().getParallelism());
    }

    public MultiBurst(String name, IntSupplier parallelism) {
        this(name, Thread.MIN_PRIORITY, parallelism);
    }

    public MultiBurst(String name, int priority, IntSupplier parallelism) {
        this.name = name;
        this.priority = priority;
        this.parallelism = parallelism;
        last = new AtomicLong(M.ms());
    }

    private synchronized ExecutorService getService() {
        last.set(M.ms());
        if (service == null || service.isShutdown()) {
            service = new ForkJoinPool(IrisSettings.getThreadCount(parallelism.getAsInt()),
                    new ForkJoinPool.ForkJoinWorkerThreadFactory() {
                        int m = 0;

                        @Override
                        public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
                            final ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                            worker.setPriority(priority);
                            worker.setName(name + " " + ++m);
                            return worker;
                        }
                    },
                    (t, e) -> e.printStackTrace(), true);
        }

        return service;
    }

    public void burst(Runnable... r) {
        burst(r.length).queue(r).complete();
    }

    public void burst(boolean multicore, Runnable... r) {
        if (multicore) {
            burst(r);
        } else {
            sync(r);
        }
    }

    public void burst(List<Runnable> r) {
        burst(r.size()).queue(r).complete();
    }

    public void burst(boolean multicore, List<Runnable> r) {
        if (multicore) {
            burst(r);
        } else {
            sync(r);
        }
    }

    private void sync(List<Runnable> r) {
        for (Runnable i : new KList<>(r)) {
            i.run();
        }
    }

    public void sync(Runnable... r) {
        for (Runnable i : r) {
            i.run();
        }
    }

    public void sync(KList<Runnable> r) {
        for (Runnable i : r) {
            i.run();
        }
    }

    public BurstExecutor burst(int estimate) {
        return new BurstExecutor(getService(), estimate);
    }

    public BurstExecutor burst() {
        return burst(16);
    }

    public BurstExecutor burst(boolean multicore) {
        BurstExecutor b = burst();
        b.setMulticore(multicore);
        return b;
    }

    public <T> Future<T> lazySubmit(Callable<T> o) {
        return getService().submit(o);
    }

    public void lazy(Runnable o) {
        getService().execute(o);
    }

    public Future<?> future(Runnable o) {
        return getService().submit(o);
    }

    public Future<?> complete(Runnable o) {
        return getService().submit(o);
    }

    public <T> Future<T> completeValue(Callable<T> o) {
        return getService().submit(o);
    }

    @Override
    public void shutdown() {
        close();
    }

    @NotNull
    @Override
    public List<Runnable> shutdownNow() {
        close();
        return List.of();
    }

    @Override
    public boolean isShutdown() {
        return service == null || service.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return service == null || service.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
        return service == null || service.awaitTermination(timeout, unit);
    }

    @Override
    public void execute(@NotNull Runnable command) {
        getService().execute(command);
    }

    @NotNull
    @Override
    public <T> Future<T> submit(@NotNull Callable<T> task) {
        return getService().submit(task);
    }

    @NotNull
    @Override
    public <T> Future<T> submit(@NotNull Runnable task, T result) {
        return getService().submit(task, result);
    }

    @NotNull
    @Override
    public Future<?> submit(@NotNull Runnable task) {
        return getService().submit(task);
    }

    @NotNull
    @Override
    public <T> List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return getService().invokeAll(tasks);
    }

    @NotNull
    @Override
    public <T> List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> tasks, long timeout, @NotNull TimeUnit unit) throws InterruptedException {
        return getService().invokeAll(tasks, timeout, unit);
    }

    @NotNull
    @Override
    public <T> T invokeAny(@NotNull Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return getService().invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(@NotNull Collection<? extends Callable<T>> tasks, long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return getService().invokeAny(tasks, timeout, unit);
    }

    public void close() {
        if (service != null) {
            close(service);
        }
    }

    public static void close(ExecutorService service) {
        service.shutdown();
        PrecisionStopwatch p = PrecisionStopwatch.start();
        try {
            while (!service.awaitTermination(1, TimeUnit.SECONDS)) {
                Iris.info("Still waiting to shutdown burster...");
                if (p.getMilliseconds() > TIMEOUT) {
                    Iris.warn("Forcing Shutdown...");

                    try {
                        service.shutdownNow();
                    } catch (Throwable e) {

                    }

                    break;
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
            Iris.reportError(e);
        }
    }
}
