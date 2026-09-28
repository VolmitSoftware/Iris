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

import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.math.M;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class PregenMantleBackpressure {
    private static final long LOG_INTERVAL_MS = 5_000L;

    private final Supplier<Mantle> mantleSupplier;
    private final int maxResidentTectonicPlates;
    private final int waitMs;
    private final long timeoutMs;
    private final Runnable onBudgetTimeout;
    private final Supplier<String> diagnostics;
    private final BooleanSupplier cancelled;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition progressed = lock.newCondition();

    public PregenMantleBackpressure(Supplier<Mantle> mantleSupplier, int maxResidentTectonicPlates, int waitMs, long timeoutMs, Runnable onBudgetTimeout, Supplier<String> diagnostics) {
        this(mantleSupplier, maxResidentTectonicPlates, waitMs, timeoutMs, onBudgetTimeout, diagnostics, () -> false);
    }

    public PregenMantleBackpressure(Supplier<Mantle> mantleSupplier, int maxResidentTectonicPlates, int waitMs, long timeoutMs, Runnable onBudgetTimeout, Supplier<String> diagnostics, BooleanSupplier cancelled) {
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

        long waitStart = M.ms();
        long lastLog = 0L;
        while (mantle.getLoadedRegionCount() > cap) {
            if (isCancelled()) {
                return;
            }

            int freed;
            int resident;
            try {
                mantle.trim(0L);
                freed = mantle.unloadTectonicPlate(0);
                resident = mantle.getLoadedRegionCount();
                if (resident > cap && mantle.saveOldestIdleTectonicPlate()) {
                    freed++;
                    resident = mantle.getLoadedRegionCount();
                }
            } catch (Throwable e) {
                IrisLogging.reportError(e);
                break;
            }
            if (resident <= cap) {
                break;
            }

            long elapsed = M.ms() - waitStart;
            if (elapsed >= timeoutMs && freed == 0) {
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
                        + "), freed " + freed + " last pass, waited " + elapsed + "ms.");
            }

            if (!awaitProgress()) {
                return;
            }
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
