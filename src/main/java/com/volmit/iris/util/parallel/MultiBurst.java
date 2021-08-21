/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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
import com.volmit.iris.core.service.PreservationSVC;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.M;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class MultiBurst {
    public static final MultiBurst burst = new MultiBurst();
    private ExecutorService service;
    private final AtomicLong last;
    private final String name;
    private final int priority;

    public MultiBurst() {
        this("Iris", Thread.MIN_PRIORITY);
    }

    public MultiBurst(String name, int priority) {
        this.name = name;
        this.priority = priority;
        last = new AtomicLong(M.ms());
        Iris.service(PreservationSVC.class).register(this);
    }

    private synchronized ExecutorService getService() {
        last.set(M.ms());
        if (service == null || service.isShutdown()) {
            service = new ForkJoinPool(Runtime.getRuntime().availableProcessors(),
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

    public void burst(List<Runnable> r) {
        burst(r.size()).queue(r).complete();
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

    public <T> Future<T> lazySubmit(Callable<T> o) {
        return getService().submit(o);
    }

    public void lazy(Runnable o) {
        getService().execute(o);
    }

    public Future<?> future(Runnable o) {
        return getService().submit(o);
    }

    public CompletableFuture<?> complete(Runnable o) {
        return CompletableFuture.runAsync(o, getService());
    }

    public <T> CompletableFuture<T> completeValue(Supplier<T> o) {
        return CompletableFuture.supplyAsync(o, getService());
    }

    public void close() {
        if (service != null) {
            service.shutdown();
            try {
                while (!service.awaitTermination(10, TimeUnit.SECONDS)) {
                    Iris.info("Still waiting to shutdown burster...");
                }
            } catch (Throwable e) {
                e.printStackTrace();
                Iris.reportError(e);
            }
        }
    }
}
