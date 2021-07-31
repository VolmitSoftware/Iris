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

package com.volmit.iris.engine.parallel;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.Looper;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class MultiBurst {
    public static final MultiBurst burst = new MultiBurst("Iris", IrisSettings.get().getConcurrency().getMiscThreadPriority(), IrisSettings.getThreadCount(IrisSettings.get().getConcurrency().getMiscThreadCount()));
    private ExecutorService service;
    private final Looper heartbeat;
    private final AtomicLong last;
    private int tid;
    private final String name;
    private final int tc;
    private final int priority;

    public MultiBurst(int tc) {
        this("Iris", 6, tc);
    }

    public MultiBurst(String name, int priority, int tc) {
        this.name = name;
        this.priority = priority;
        this.tc = tc;
        last = new AtomicLong(M.ms());
        heartbeat = new Looper() {
            @Override
            protected long loop() {
                if(M.ms() - last.get() > TimeUnit.MINUTES.toMillis(1) && service != null)
                {
                    service.shutdown();
                    service = null;
                    Iris.debug("Shutting down MultiBurst Pool " + getName() + " to conserve resource.");
                }

                return 60000;
            }
        };
        heartbeat.setName(name);
        heartbeat.start();
    }

    private synchronized ExecutorService getService()
    {
        last.set(M.ms());
        if(service == null || service.isShutdown())
        {
            service = Executors.newFixedThreadPool(Math.max(tc, 1), r -> {
                tid++;
                Thread t = new Thread(r);
                t.setName(name + " " + tid);
                t.setPriority(priority);
                t.setUncaughtExceptionHandler((et, e) ->
                {
                    Iris.info("Exception encountered in " + et.getName());
                    e.printStackTrace();
                });

                return t;
            });
            Iris.debug("Started MultiBurst Pool " + name + " with " + tc + " threads at " + priority + " priority.");
        }

        return service;
    }

    public void burst(Runnable... r) {
        burst(r.length).queue(r).complete();
    }

    public void burst(KList<Runnable> r) {
        burst(r.size()).queue(r).complete();
    }

    public void sync(Runnable... r) {
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

    public void shutdownNow() {
        Iris.debug("Shutting down MultiBurst Pool " + heartbeat.getName() + ".");
        heartbeat.interrupt();

        if(service != null)
        {
            service.shutdownNow().forEach(Runnable::run);
        }
    }

    public void shutdown() {
        Iris.debug("Shutting down MultiBurst Pool " + heartbeat.getName() + ".");
        heartbeat.interrupt();

        if(service != null)
        {
            service.shutdown();
        }
    }

    public void shutdownLater() {
       if(service != null)
       {
           service.submit(() -> {
               J.sleep(3000);
               Iris.debug("Shutting down MultiBurst Pool " + heartbeat.getName() + ".");

               if(service != null)
               {
                   service.shutdown();
               }
           });

           heartbeat.interrupt();
       }
    }

    public void shutdownAndAwait() {
        Iris.debug("Shutting down MultiBurst Pool " + heartbeat.getName() + ".");
        heartbeat.interrupt();
        if(service != null)
        {
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
