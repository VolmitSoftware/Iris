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

package com.volmit.iris.util.scheduling;

import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.function.NastyFunction;
import com.volmit.iris.util.function.NastyFuture;
import com.volmit.iris.util.function.NastyRunnable;
import com.volmit.iris.util.function.NastySupplier;
import com.volmit.iris.util.math.FinalInteger;
import com.volmit.iris.util.parallel.MultiBurst;
import de.crazydev22.platformutils.scheduler.task.Task;
import org.bukkit.Bukkit;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@SuppressWarnings("ALL")
public class J {
    private static int tid = 0;
    private static KList<Runnable> afterStartup = new KList<>();
    private static KList<Runnable> afterStartupAsync = new KList<>();
    private static boolean started = false;

    public static void dofor(int a, Function<Integer, Boolean> c, int ch, Consumer<Integer> d) {
        for (int i = a; c.apply(i); i += ch) {
            c.apply(i);
        }
    }

    public static boolean doif(Supplier<Boolean> c, Runnable g) {
        try {
            if (c.get()) {
                g.run();
                return true;
            }
        } catch (NullPointerException e) {
            Iris.reportError(e);
            // TODO: Fix this because this is just a suppression for an NPE on g
            return false;
        }

        return false;
    }

    public static void arun(Runnable a) {
        MultiBurst.burst.lazy(() -> {
            try {
                a.run();
            } catch (Throwable e) {
                Iris.reportError(e);
                Iris.error("Failed to run async task");
                e.printStackTrace();
            }
        });
    }

    public static void a(Runnable a) {
        MultiBurst.burst.lazy(() -> {
            try {
                a.run();
            } catch (Throwable e) {
                Iris.reportError(e);
                Iris.error("Failed to run async task");
                e.printStackTrace();
            }
        });
    }

    public static void aBukkit(Runnable a) {
        if (!Bukkit.getPluginManager().isPluginEnabled(Iris.instance)) {
            return;
        }
        Iris.platform.getAsyncScheduler().run(a);
    }

    public static <T> Future<T> a(Callable<T> a) {
        return MultiBurst.burst.lazySubmit(a);
    }

    public static void attemptAsync(NastyRunnable r) {
        J.a(() -> J.attempt(r));
    }

    public static <R> R attemptResult(NastyFuture<R> r, R onError) {
        try {
            return r.run();
        } catch (Throwable e) {
            Iris.reportError(e);

        }

        return onError;
    }

    public static <T, R> R attemptFunction(NastyFunction<T, R> r, T param, R onError) {
        try {
            return r.run(param);
        } catch (Throwable e) {
            Iris.reportError(e);

        }

        return onError;
    }

    public static boolean sleep(long ms) {
        return J.attempt(() -> Thread.sleep(ms));
    }

    public static boolean attempt(NastyRunnable r) {
        return attemptCatch(r) == null;
    }

    public static <T> T attemptResult(NastySupplier<T> r) {
        try {
            return r.get();
        } catch (Throwable e) {
            return null;
        }
    }

    public static Throwable attemptCatch(NastyRunnable r) {
        try {
            r.run();
        } catch (Throwable e) {
            return e;
        }

        return null;
    }

    public static <T> T attempt(Supplier<T> t, T i) {
        try {
            return t.get();
        } catch (Throwable e) {
            Iris.reportError(e);
            return i;
        }
    }

    /**
     * Dont call this unless you know what you are doing!
     */
    public static void executeAfterStartupQueue() {
        if (started) {
            return;
        }

        started = true;

        for (Runnable r : afterStartup) {
            s(r);
        }

        for (Runnable r : afterStartupAsync) {
            a(r);
        }

        afterStartup = null;
        afterStartupAsync = null;
    }

    /**
     * Schedule a sync task to be run right after startup. If the server has already
     * started ticking, it will simply run it in a sync task.
     * <p>
     * If you dont know if you should queue this or not, do so, it's pretty
     * forgiving.
     *
     * @param r the runnable
     */
    public static void ass(Runnable r) {
        if (started) {
            s(r);
        } else {
            afterStartup.add(r);
        }
    }

    /**
     * Schedule an async task to be run right after startup. If the server has
     * already started ticking, it will simply run it in an async task.
     * <p>
     * If you dont know if you should queue this or not, do so, it's pretty
     * forgiving.
     *
     * @param r the runnable
     */
    public static void asa(Runnable r) {
        if (started) {
            a(r);
        } else {
            afterStartupAsync.add(r);
        }
    }

    /**
     * Queue a sync task
     *
     * @param r the runnable
     */
    public static void s(Runnable r) {
        if (!Bukkit.getPluginManager().isPluginEnabled(Iris.instance)) {
            return;
        }
        Iris.platform.getGlobalScheduler().run(r);
    }

    public static CompletableFuture sfut(Runnable r) {
        if (!Bukkit.getPluginManager().isPluginEnabled(Iris.instance)) {
            return null;
        }

        return Iris.platform.getGlobalScheduler().run(() -> {
            r.run();
            return null;
        }).getResult();
    }

    public static <T> CompletableFuture<T> sfut(Supplier<T> r) {
        if (!Bukkit.getPluginManager().isPluginEnabled(Iris.instance)) {
            return null;
        }
        return Iris.platform.getGlobalScheduler().run(r).getResult();
    }

    public static CompletableFuture sfut(Runnable r, int delay) {
        if (!Bukkit.getPluginManager().isPluginEnabled(Iris.instance)) {
            return null;
        }

        return Iris.platform.getGlobalScheduler().runDelayed(() -> {
            r.run();
            return null;
        }, delay).getResult();
    }

    public static CompletableFuture afut(Runnable r) {
        CompletableFuture f = new CompletableFuture();
        J.a(() -> {
            r.run();
            f.complete(null);
        });
        return f;
    }

    /**
     * Queue a sync task
     *
     * @param r     the runnable
     * @param delay the delay to wait in ticks before running
     */
    public static void s(Runnable r, int delay) {
        try {
            if (!Bukkit.getPluginManager().isPluginEnabled(Iris.instance)) {
                return;
            }
            Iris.platform.getGlobalScheduler().runDelayed(r, delay);
        } catch (Throwable e) {
            Iris.reportError(e);
        }
    }

    /**
     * Start a sync repeating task
     *
     * @param r        the runnable
     * @param interval the interval
     * @return the task id
     */
    public static Task sr(Runnable r, int interval) {
        if (!Bukkit.getPluginManager().isPluginEnabled(Iris.instance)) {
            return null;
        }
        return Iris.platform.getGlobalScheduler().runAtFixedRate(r, 1, Math.max(interval, 1));
    }

    /**
     * Call an async task dealyed
     *
     * @param r     the runnable
     * @param delay the delay to wait before running
     */
    public static void a(Runnable r, int delay) {
        if (Bukkit.getPluginManager().isPluginEnabled(Iris.instance)) {
            Iris.platform.getAsyncScheduler().runDelayed(r, Math.max(delay * 50, 0), TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Start an async repeat task
     *
     * @param r        the runnable
     * @param interval the interval in ticks
     * @return the task id
     */
    public static Task ar(Runnable r, int interval) {
        if (!Bukkit.getPluginManager().isPluginEnabled(Iris.instance)) {
            return null;
        }
        return Iris.platform.getAsyncScheduler().runAtFixedRate(r, 0, Math.max(interval, 1) * 50, TimeUnit.MILLISECONDS);
    }

    /**
     * Start an async repeating task for a limited time
     *
     * @param r         the runnable
     * @param interval  the interval
     * @param intervals the intervals to run
     */
    public static void ar(Runnable r, int interval, int intervals) {
        FinalInteger fi = new FinalInteger(0);

        new AR() {
            @Override
            public void run() {
                fi.add(1);
                r.run();

                if (fi.get() >= intervals) {
                    cancel();
                }
            }
        };
    }
}
