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

package art.arcane.iris.world.pregen;

import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.math.M;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Keeps the resident tectonic plate count near its cap during pregeneration. Over the cap, the least recently
 * used idle plates are saved and unloaded one at a time on the eviction executor while submissions continue;
 * a submission only waits once residency passes the cap by the eviction overshoot.
 */
public final class PregenMantleBackpressure {
    private static final long LOG_INTERVAL_MS = 5_000L;

    private final Supplier<Mantle> mantleSupplier;
    private final int maxResidentTectonicPlates;
    private final int waitMs;
    private final long timeoutMs;
    private final Runnable onBudgetTimeout;
    private final Supplier<String> diagnostics;
    private final BooleanSupplier cancelled;
    private final Executor evictionExecutor;
    private final AtomicBoolean evicting = new AtomicBoolean();
    private final AtomicBoolean evictionFailed = new AtomicBoolean();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition progressed = lock.newCondition();

    public PregenMantleBackpressure(Supplier<Mantle> mantleSupplier, int maxResidentTectonicPlates, int waitMs, long timeoutMs, Runnable onBudgetTimeout, Supplier<String> diagnostics) {
        this(mantleSupplier, maxResidentTectonicPlates, waitMs, timeoutMs, onBudgetTimeout, diagnostics, () -> false);
    }

    public PregenMantleBackpressure(Supplier<Mantle> mantleSupplier, int maxResidentTectonicPlates, int waitMs, long timeoutMs, Runnable onBudgetTimeout, Supplier<String> diagnostics, BooleanSupplier cancelled) {
        this(mantleSupplier, maxResidentTectonicPlates, waitMs, timeoutMs, onBudgetTimeout, diagnostics, cancelled,
                command -> MultiBurst.ioBurst.execute(command));
    }

    public PregenMantleBackpressure(Supplier<Mantle> mantleSupplier, int maxResidentTectonicPlates, int waitMs, long timeoutMs, Runnable onBudgetTimeout, Supplier<String> diagnostics, BooleanSupplier cancelled, Executor evictionExecutor) {
        this.evictionExecutor = evictionExecutor;
        this.mantleSupplier = mantleSupplier;
        this.maxResidentTectonicPlates = maxResidentTectonicPlates;
        this.waitMs = Math.max(1, waitMs);
        this.timeoutMs = timeoutMs;
        this.onBudgetTimeout = onBudgetTimeout;
        this.diagnostics = diagnostics;
        this.cancelled = cancelled;
    }

    public void apply() {
        enforceMantleBudget();
        awaitHeapHeadroom();
    }

    public void enforceMantleBudget() {
        int cap = maxResidentTectonicPlates;
        if (cap <= 0) {
            return;
        }

        Mantle mantle = resolveMantle();
        if (mantle == null) {
            return;
        }

        if (mantle.getLoadedRegionCount() <= cap) {
            return;
        }

        evictionFailed.set(false);
        requestEviction(mantle, cap);
        int ceiling = cap + evictionOvershoot(cap);
        if (mantle.getLoadedRegionCount() <= ceiling || evictionFailed.getAndSet(false)) {
            return;
        }

        long waitStart = M.ms();
        long lastLog = 0L;
        int resident = mantle.getLoadedRegionCount();
        while (resident > ceiling) {
            if (isCancelled()) {
                return;
            }

            int before = resident;
            if (!awaitProgress() || evictionFailed.getAndSet(false)) {
                return;
            }
            try {
                requestEviction(mantle, cap);
                resident = mantle.getLoadedRegionCount();
            } catch (Throwable e) {
                IrisLogging.reportError(e);
                return;
            }
            if (resident <= ceiling) {
                return;
            }

            long elapsed = M.ms() - waitStart;
            if (elapsed >= timeoutMs && resident >= before) {
                IrisLogging.warn("Pregen mantle backpressure exceeded " + timeoutMs + "ms with " + resident
                        + " tectonic plates resident (residency target " + cap + "); no idle plate could be evicted. "
                        + "Allowing the active dependency working set to exceed the residency target; "
                        + "heap pressure still blocks new chunk submissions. " + diagnostics.get());
                onBudgetTimeout.run();
                return;
            }

            long logNow = M.ms();
            if (logNow - lastLog >= LOG_INTERVAL_MS) {
                lastLog = logNow;
                // Pausing to stay under the plate cap is the design working, and it is reported every five
                // seconds for the whole duration of a large pregen. Only exceeding the budget is a warning.
                IrisLogging.info("Pregen mantle backpressure: " + resident + " tectonic plates resident (residency target " + cap
                        + "), waited " + elapsed + "ms.");
            }
        }
    }

    static int evictionOvershoot(int cap) {
        return Math.max(2, cap / 8);
    }

    private void requestEviction(Mantle mantle, int cap) {
        if (!evicting.compareAndSet(false, true)) {
            return;
        }

        try {
            evictionExecutor.execute(() -> evictToCap(mantle, cap));
        } catch (Throwable e) {
            evicting.set(false);
            IrisLogging.reportError(e);
        }
    }

    private void evictToCap(Mantle mantle, int cap) {
        try {
            while (!mantle.isClosed() && !isCancelled() && mantle.getLoadedRegionCount() > cap
                    && mantle.saveOldestIdleTectonicPlate()) {
                signalProgress();
            }
        } catch (Throwable e) {
            if (!mantle.isClosed()) {
                evictionFailed.set(true);
                IrisLogging.reportError(e);
            }
        } finally {
            evicting.set(false);
            signalProgress();
        }
    }

    public void awaitHeapHeadroom() {
        awaitHeapHeadroom(MantleHeapPressure::overHighWater, MantleHeapPressure::requestPanicReclaim);
    }

    void awaitHeapHeadroom(BooleanSupplier heapPressure, Runnable panicReclaim) {
        Mantle mantle = resolveMantle();
        long waitStart = M.ms();
        long lastLog = 0L;
        long nextWarningElapsed = Math.max(0L, timeoutMs);
        long nextEvictionErrorLog = 0L;
        long warningInterval = Math.max(LOG_INTERVAL_MS, timeoutMs);
        while (heapPressure.getAsBoolean()) {
            if (isCancelled()) {
                return;
            }

            try {
                if (mantle != null) {
                    mantle.saveOldestIdleTectonicPlate();
                }
            } catch (Throwable e) {
                long now = M.ms();
                if (now >= nextEvictionErrorLog) {
                    nextEvictionErrorLog = now + warningInterval;
                    IrisLogging.reportError(e);
                }
            }

            panicReclaim.run();
            if (isCancelled() || !heapPressure.getAsBoolean()) {
                return;
            }

            long elapsed = M.ms() - waitStart;
            long logNow = M.ms();
            if (elapsed >= nextWarningElapsed) {
                nextWarningElapsed = elapsed + warningInterval;
                lastLog = logNow;
                IrisLogging.warn("Pregen heap pressure is still blocking new chunk submissions after " + elapsed + "ms at "
                        + Math.round(MantleHeapPressure.usedFraction() * 100.0D) + "% heap; waiting for headroom. "
                        + diagnostics.get());
                onBudgetTimeout.run();
            }

            if (logNow - lastLog >= LOG_INTERVAL_MS) {
                lastLog = logNow;
                IrisLogging.info("Pregen heap pressure: pausing generation at "
                        + Math.round(MantleHeapPressure.usedFraction() * 100.0D) + "% heap; evicting tectonic plates and waiting for headroom"
                        + (mantle != null ? " (" + mantle.getLoadedRegionCount() + " plates resident)" : "") + ".");
            }

            if (!awaitProgress()) {
                return;
            }
        }
    }

    public void signalProgress() {
        lock.lock();
        try {
            progressed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private boolean awaitProgress() {
        lock.lock();
        try {
            progressed.await(waitMs, TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            lock.unlock();
        }
    }

    private boolean isCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            return true;
        }

        try {
            return cancelled.getAsBoolean();
        } catch (Throwable e) {
            PregenDiagnostics.probeFailed("pregen cancellation state", e);
            return false;
        }
    }

    private Mantle resolveMantle() {
        try {
            return mantleSupplier.get();
        } catch (Throwable e) {
            PregenDiagnostics.probeFailed("mantle handle for backpressure", e);
            return null;
        }
    }
}
