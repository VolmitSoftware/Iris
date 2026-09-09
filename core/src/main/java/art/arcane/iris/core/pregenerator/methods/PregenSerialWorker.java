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

package art.arcane.iris.core.pregenerator.methods;

import art.arcane.iris.spi.IrisLogging;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Single-thread worker owned by one pregen run. Used for mantle cleanup (Paper-like chunk futures
 * complete on the server thread, so the covered-neighbour scan must leave the tick) and for native
 * region flushes (a Moonrise flush parks until the level's pending IO settles; parking the shared
 * Iris IO pool starved the mantle plate loads that generation was waiting on, wedging every worker).
 */
final class PregenSerialWorker {
    private final ExecutorService executor;
    private final AtomicInteger pending = new AtomicInteger();
    private final String role;
    private final String worldName;
    private final BiConsumer<String, Throwable> failureReporter;
    private final Consumer<String> warningReporter;

    PregenSerialWorker(String role, String worldName) {
        this(role, worldName, IrisLogging::reportError, IrisLogging::warn);
    }

    PregenSerialWorker(String role,
                       String worldName,
                       BiConsumer<String, Throwable> failureReporter,
                       Consumer<String> warningReporter) {
        this.role = Objects.requireNonNull(role, "role");
        this.worldName = Objects.requireNonNull(worldName, "world name");
        this.failureReporter = Objects.requireNonNull(failureReporter, "failure reporter");
        this.warningReporter = Objects.requireNonNull(warningReporter, "warning reporter");
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, role + " " + worldName);
            thread.setDaemon(true);
            return thread;
        });
    }

    /** The serial executor itself, for callers that need a completion future per task. */
    Executor executor() {
        return executor;
    }

    boolean submit(Runnable cleanup) {
        Objects.requireNonNull(cleanup, "cleanup");
        pending.incrementAndGet();
        try {
            executor.execute(() -> {
                try {
                    cleanup.run();
                } catch (Throwable failure) {
                    failureReporter.accept(role + " failed in world " + worldName + ".", failure);
                } finally {
                    pending.decrementAndGet();
                }
            });
            return true;
        } catch (RejectedExecutionException rejected) {
            pending.decrementAndGet();
            return false;
        }
    }

    int pending() {
        return pending.get();
    }

    boolean close(long timeout, TimeUnit unit) {
        executor.shutdown();
        boolean drained = false;
        boolean interrupted = false;
        try {
            drained = executor.awaitTermination(timeout, unit);
        } catch (InterruptedException interruption) {
            interrupted = true;
        }
        if (!drained) {
            int abandoned = executor.shutdownNow().size();
            warningReporter.accept(role + " for " + worldName + " did not drain in " + unit.toSeconds(timeout)
                    + "s; " + abandoned + " queued task(s) were abandoned.");
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        return drained;
    }
}
