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

package art.arcane.iris.util.common.scheduling;

import art.arcane.iris.Iris;
import art.arcane.iris.core.service.PreservationSVC;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.volmlib.util.function.NastyFunction;
import art.arcane.volmlib.util.function.NastyFuture;
import art.arcane.volmlib.util.function.NastyRunnable;
import art.arcane.volmlib.util.function.NastySupplier;
import art.arcane.volmlib.util.math.FinalInteger;
import art.arcane.volmlib.util.scheduling.AR;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.scheduling.JSupport;
import art.arcane.volmlib.util.scheduling.SR;
import art.arcane.volmlib.util.scheduling.SchedulerBridge;
import art.arcane.volmlib.util.scheduling.StartupQueueSupport;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@SuppressWarnings("ALL")
public class J {
    private static final long TICK_MS = 50L;
    private static final AtomicInteger TASK_IDS = new AtomicInteger(1);
    private static final Map<Integer, Runnable> REPEATING_CANCELLERS = new ConcurrentHashMap<>();
    private static final StartupQueueSupport STARTUP_QUEUE = new StartupQueueSupport();

    static {
        SchedulerBridge.setSyncScheduler(J::s);
        SchedulerBridge.setDelayedSyncScheduler(J::s);
        SchedulerBridge.setAsyncScheduler(J::a);
        SchedulerBridge.setDelayedAsyncScheduler(J::a);
        SchedulerBridge.setSyncRepeatingScheduler(J::sr);
        SchedulerBridge.setAsyncRepeatingScheduler(J::ar);
        SchedulerBridge.setCancelScheduler(J::car);
        SchedulerBridge.setErrorHandler(e -> {
            Iris.reportError(e);
            e.printStackTrace();
        });
        SchedulerBridge.setInfoLogger(Iris::debug);
        SchedulerBridge.setThreadRegistrar(thread -> {
            try {
                Iris.service(PreservationSVC.class).register(thread);
            } catch (Throwable e) {
                Iris.reportError(e);
            }
        });
    }

    public static void dofor(int a, Function<Integer, Boolean> c, int ch, Consumer<Integer> d) {
        JSupport.dofor(a, c, ch, d);
    }

    public static boolean doif(Supplier<Boolean> c, Runnable g) {
        return JSupport.doif(c, g, Iris::reportError);
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
        if (!isPluginEnabled()) {
            return;
        }

        if (!runAsyncImmediate(a)) {
            a(a, 0);
        }
    }

    public static <T> Future<T> a(Callable<T> a) {
        return MultiBurst.burst.lazySubmit(a);
    }

    public static void attemptAsync(NastyRunnable r) {
        JSupport.attemptAsync(r::run, J::a);
    }

    public static <R> R attemptResult(NastyFuture<R> r, R onError) {
        return JSupport.attemptResult(r::run, onError, Iris::reportError);
    }

    public static <T, R> R attemptFunction(NastyFunction<T, R> r, T param, R onError) {
        return JSupport.attemptFunction(r::run, param, onError, Iris::reportError);
    }

    public static boolean sleep(long ms) {
        return JSupport.sleep(ms);
    }

    public static boolean attempt(NastyRunnable r) {
        return JSupport.attempt(r::run);
    }

    public static <T> T attemptResult(NastySupplier<T> r) {
        return JSupport.attemptNullable(r::get);
    }

    public static Throwable attemptCatch(NastyRunnable r) {
        return JSupport.attemptCatch(r::run);
    }

    public static <T> T attempt(Supplier<T> t, T i) {
        return JSupport.attempt(t::get, i, Iris::reportError);
    }

    public static void executeAfterStartupQueue() {
        JSupport.executeAfterStartupQueue(STARTUP_QUEUE, J::s, J::a);
    }

    public static void ass(Runnable r) {
        JSupport.enqueueAfterStartupSync(STARTUP_QUEUE, r, J::s);
    }

    public static void asa(Runnable r) {
        JSupport.enqueueAfterStartupAsync(STARTUP_QUEUE, r, J::a);
    }

    public static boolean isFolia() {
        return FoliaScheduler.isFolia(Bukkit.getServer());
    }

    public static boolean isPrimaryThread() {
        return FoliaScheduler.isPrimaryThread();
    }

    public static boolean isOwnedByCurrentRegion(Entity entity) {
        if (entity == null) {
            return false;
        }

        if (!isFolia()) {
            return isPrimaryThread();
        }

        return FoliaScheduler.isOwnedByCurrentRegion(entity);
    }

    public static boolean isOwnedByCurrentRegion(World world, int chunkX, int chunkZ) {
        if (world == null) {
            return false;
        }

        if (!isFolia()) {
            return isPrimaryThread();
        }

        return FoliaScheduler.isOwnedByCurrentRegion(world, chunkX, chunkZ);
    }

    public static boolean runEntity(Entity entity, Runnable runnable) {
        if (entity == null || runnable == null) {
            return false;
        }

        if (isFolia()) {
            if (isOwnedByCurrentRegion(entity)) {
                runnable.run();
                return true;
            }

            return runEntityImmediate(entity, runnable);
        }

        if (isPrimaryThread()) {
            runnable.run();
            return true;
        }

        s(runnable);
        return true;
    }

    public static boolean runEntity(Entity entity, Runnable runnable, int delayTicks) {
        if (entity == null || runnable == null) {
            return false;
        }

        if (delayTicks <= 0) {
            return runEntity(entity, runnable);
        }

        if (isFolia() && runEntityDelayed(entity, runnable, delayTicks)) {
            return true;
        }

        s(() -> runEntity(entity, runnable), delayTicks);
        return true;
    }

    public static boolean runRegion(World world, int chunkX, int chunkZ, Runnable runnable) {
        if (world == null || runnable == null) {
            return false;
        }

        if (isFolia() && isOwnedByCurrentRegion(world, chunkX, chunkZ)) {
            runnable.run();
            return true;
        }

        if (runRegionImmediate(world, chunkX, chunkZ, runnable)) {
            return true;
        }

        if (isFolia()) {
            Iris.verbose("Failed to schedule immediate region task for " + world.getName() + "@" + chunkX + "," + chunkZ + " on Folia.");
            return false;
        }

        s(runnable);
        return true;
    }

    public static boolean runRegion(World world, int chunkX, int chunkZ, Runnable runnable, int delayTicks) {
        if (world == null || runnable == null) {
            return false;
        }

        if (delayTicks <= 0) {
            return runRegion(world, chunkX, chunkZ, runnable);
        }

        if (runRegionDelayed(world, chunkX, chunkZ, runnable, delayTicks)) {
            return true;
        }

        if (isFolia()) {
            Iris.verbose("Failed to schedule delayed region task for " + world.getName() + "@" + chunkX + "," + chunkZ
                    + " (" + delayTicks + "t) on Folia.");
            return false;
        }

        s(runnable, delayTicks);
        return true;
    }

    public static boolean runAt(Location location, Runnable runnable) {
        if (location == null || runnable == null || location.getWorld() == null) {
            return false;
        }

        return runRegion(location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4, runnable);
    }

    public static boolean runAt(Location location, Runnable runnable, int delayTicks) {
        if (location == null || runnable == null || location.getWorld() == null) {
            return false;
        }

        return runRegion(location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4, runnable, delayTicks);
    }

    public static void cancelPluginTasks() {
        if (Iris.instance == null) {
            return;
        }

        FoliaScheduler.cancelTasks(Iris.instance);

        try {
            Bukkit.getScheduler().cancelTasks(Iris.instance);
        } catch (UnsupportedOperationException ex) {
            // Folia blocks BukkitScheduler usage.
            Iris.verbose("Skipping BukkitScheduler#cancelTasks for Iris on this server.");
        }
    }

    public static void s(Runnable r) {
        if (!isPluginEnabled()) {
            return;
        }

        if (isFolia()) {
            if (runGlobalImmediate(r)) {
                return;
            }

            throw new IllegalStateException("Failed to schedule sync task on Folia runtime.");
        }

        try {
            Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, r);
        } catch (UnsupportedOperationException e) {
            FoliaScheduler.forceFoliaThreading(Bukkit.getServer());
            if (runGlobalImmediate(r)) {
                return;
            }

            throw new IllegalStateException("Failed to schedule sync task (Folia scheduler unavailable, BukkitScheduler unsupported).", e);
        }
    }

    public static CompletableFuture sfut(Runnable r) {
        CompletableFuture f = new CompletableFuture();

        if (!isPluginEnabled()) {
            return null;
        }

        s(() -> {
            r.run();
            f.complete(null);
        });

        return f;
    }

    public static <T> CompletableFuture<T> sfut(Supplier<T> r) {
        CompletableFuture<T> f = new CompletableFuture<>();

        if (!isPluginEnabled()) {
            return null;
        }

        s(() -> {
            try {
                f.complete(r.get());
            } catch (Throwable e) {
                f.completeExceptionally(e);
            }
        });

        return f;
    }

    public static CompletableFuture sfut(Runnable r, int delay) {
        CompletableFuture f = new CompletableFuture();

        if (!isPluginEnabled()) {
            return null;
        }

        s(() -> {
            r.run();
            f.complete(null);
        }, delay);

        return f;
    }

    public static CompletableFuture afut(Runnable r) {
        CompletableFuture f = new CompletableFuture();
        J.a(() -> {
            r.run();
            f.complete(null);
        });
        return f;
    }

    public static void s(Runnable r, int delay) {
        if (!isPluginEnabled()) {
            return;
        }

        if (delay <= 0) {
            s(r);
            return;
        }

        if (isFolia()) {
            if (runGlobalDelayed(r, delay)) {
                return;
            }

            a(() -> {
                if (sleep(ticksToMilliseconds(delay))) {
                    s(r);
                }
            });
            return;
        }

        try {
            Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, r, delay);
        } catch (UnsupportedOperationException e) {
            FoliaScheduler.forceFoliaThreading(Bukkit.getServer());
            if (runGlobalDelayed(r, delay)) {
                return;
            }

            throw new IllegalStateException("Failed to schedule delayed sync task (Folia scheduler unavailable, BukkitScheduler unsupported).", e);
        } catch (Throwable e) {
            Iris.reportError(e);
        }
    }

    public static void csr(int id) {
        cancelRepeatingTask(id);
    }

    public static int sr(Runnable r, int interval) {
        if (!isPluginEnabled()) {
            return -1;
        }

        int safeInterval = Math.max(1, interval);
        RepeatingState state = new RepeatingState();
        int taskId = trackRepeatingTask(() -> state.cancelled = true);

        Runnable[] loop = new Runnable[1];
        loop[0] = () -> {
            if (state.cancelled || !isPluginEnabled()) {
                REPEATING_CANCELLERS.remove(taskId);
                return;
            }

            r.run();
            if (state.cancelled || !isPluginEnabled()) {
                REPEATING_CANCELLERS.remove(taskId);
                return;
            }

            s(loop[0], safeInterval);
        };

        s(loop[0]);
        return taskId;
    }

    public static void sr(Runnable r, int interval, int intervals) {
        FinalInteger fi = new FinalInteger(0);

        new SR(interval) {
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

    public static void a(Runnable r, int delay) {
        if (!isPluginEnabled()) {
            return;
        }

        if (delay <= 0) {
            if (!runAsyncImmediate(r)) {
                a(r);
            }
            return;
        }

        if (!runAsyncDelayed(r, delay)) {
            a(() -> {
                if (sleep(ticksToMilliseconds(delay))) {
                    r.run();
                }
            });
        }
    }

    public static void car(int id) {
        cancelRepeatingTask(id);
    }

    public static int ar(Runnable r, int interval) {
        if (!isPluginEnabled()) {
            return -1;
        }

        int safeInterval = Math.max(1, interval);
        RepeatingState state = new RepeatingState();
        int taskId = trackRepeatingTask(() -> state.cancelled = true);

        Runnable[] loop = new Runnable[1];
        loop[0] = () -> {
            if (state.cancelled || !isPluginEnabled()) {
                REPEATING_CANCELLERS.remove(taskId);
                return;
            }

            r.run();
            if (state.cancelled || !isPluginEnabled()) {
                REPEATING_CANCELLERS.remove(taskId);
                return;
            }

            a(loop[0], safeInterval);
        };

        a(loop[0], 0);
        return taskId;
    }

    public static void ar(Runnable r, int interval, int intervals) {
        FinalInteger fi = new FinalInteger(0);

        new AR(interval) {
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

    private static int trackRepeatingTask(Runnable cancelAction) {
        int id = TASK_IDS.getAndIncrement();
        REPEATING_CANCELLERS.put(id, cancelAction);
        return id;
    }

    private static void cancelRepeatingTask(int id) {
        Runnable cancelAction = REPEATING_CANCELLERS.remove(id);
        if (cancelAction != null) {
            cancelAction.run();
        }
    }

    private static boolean isPluginEnabled() {
        return Iris.instance != null && Bukkit.getPluginManager().isPluginEnabled(Iris.instance);
    }

    private static long ticksToMilliseconds(int ticks) {
        return Math.max(0L, ticks) * TICK_MS;
    }

    private static boolean runGlobalImmediate(Runnable runnable) {
        if (!isFolia()) {
            return false;
        }

        if (isPrimaryThread()) {
            runnable.run();
            return true;
        }

        return FoliaScheduler.runGlobal(Iris.instance, runnable);
    }

    private static boolean runGlobalDelayed(Runnable runnable, int delayTicks) {
        if (!isFolia()) {
            return false;
        }

        if (delayTicks <= 0) {
            return runGlobalImmediate(runnable);
        }

        return FoliaScheduler.runGlobal(Iris.instance, runnable, Math.max(0, delayTicks));
    }

    private static boolean runRegionImmediate(World world, int chunkX, int chunkZ, Runnable runnable) {
        if (!isFolia()) {
            return false;
        }

        return FoliaScheduler.runRegion(Iris.instance, world, chunkX, chunkZ, runnable);
    }

    private static boolean runRegionDelayed(World world, int chunkX, int chunkZ, Runnable runnable, int delayTicks) {
        if (!isFolia()) {
            return false;
        }

        return FoliaScheduler.runRegion(Iris.instance, world, chunkX, chunkZ, runnable, Math.max(0, delayTicks));
    }

    private static boolean runAsyncImmediate(Runnable runnable) {
        if (!isFolia()) {
            return false;
        }

        return FoliaScheduler.runAsync(Iris.instance, runnable);
    }

    private static boolean runAsyncDelayed(Runnable runnable, int delayTicks) {
        if (!isFolia()) {
            return false;
        }

        return FoliaScheduler.runAsync(Iris.instance, runnable, Math.max(0, delayTicks));
    }

    private static boolean runEntityImmediate(Entity entity, Runnable runnable) {
        if (!isFolia()) {
            return false;
        }

        return FoliaScheduler.runEntity(Iris.instance, entity, runnable);
    }

    private static boolean runEntityDelayed(Entity entity, Runnable runnable, int delayTicks) {
        if (!isFolia()) {
            return false;
        }

        return FoliaScheduler.runEntity(Iris.instance, entity, runnable, Math.max(0, delayTicks));
    }

    private static final class RepeatingState {
        private volatile boolean cancelled;
    }
}
