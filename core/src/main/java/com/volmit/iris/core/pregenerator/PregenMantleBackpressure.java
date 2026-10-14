package com.volmit.iris.core.pregenerator;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.math.M;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class PregenMantleBackpressure {
    private static final long PROGRESS_LOG_INTERVAL_MS = 5_000L;

    private final Supplier<Mantle> mantleSupplier;
    private final BooleanSupplier cancelled;
    private final int waitMs;
    private final long timeoutMs;

    public PregenMantleBackpressure(Supplier<Mantle> mantleSupplier, BooleanSupplier cancelled) {
        IrisSettings.IrisSettingsPregen pregen = IrisSettings.get().getPregen();
        this.mantleSupplier = mantleSupplier;
        this.cancelled = cancelled;
        this.waitMs = pregen.getMantleBackpressureWaitMs();
        this.timeoutMs = pregen.getMantleBackpressureTimeoutMs();
    }

    public void apply() {
        Mantle mantle = resolveMantle();
        if (mantle == null || mantle.isClosed()) {
            return;
        }

        int plateBudget = IrisSettings.get().getPregen().getEffectiveResidentTectonicPlates(mantle.getWorldHeight());
        enforcePlateBudget(mantle, plateBudget);
        awaitHeapHeadroom(mantle, plateBudget);
    }

    private void enforcePlateBudget(Mantle mantle, int plateBudget) {
        int hardCap = plateBudget * 2;
        long waitStart = M.ms();
        long lastLog = waitStart;
        while (mantle.getLoadedRegionCount() > hardCap && !isCancelled()) {
            int freed = evictIdlePlates(mantle);
            int resident = mantle.getLoadedRegionCount();
            if (resident <= hardCap) {
                return;
            }

            long elapsed = M.ms() - waitStart;
            if (elapsed >= timeoutMs) {
                Iris.warn("Pregen mantle backpressure waited %dms with %d tectonic plates resident (hard cap %d); "
                        + "continuing to avoid a deadlock. Raise pregen.maxResidentTectonicPlates if this keeps happening.",
                        timeoutMs, resident, hardCap);
                return;
            }
            if (M.ms() - lastLog >= PROGRESS_LOG_INTERVAL_MS) {
                lastLog = M.ms();
                Iris.info("Pregen mantle backpressure: %d tectonic plates resident (hard cap %d), freed %d last pass, waited %dms.",
                        resident, hardCap, freed, elapsed);
            }
            if (!pause()) {
                return;
            }
        }
    }

    private void awaitHeapHeadroom(Mantle mantle, int plateBudget) {
        long waitStart = M.ms();
        long lastLog = waitStart;
        while (MantleHeapPressure.overHighWater() && !isCancelled()) {
            if (mantle.getLoadedRegionCount() > plateBudget) {
                evictIdlePlates(mantle);
            }
            MantleHeapPressure.requestPanicReclaim();

            long elapsed = M.ms() - waitStart;
            if (elapsed >= timeoutMs) {
                Iris.warn("Pregen heap pressure wait exceeded %dms at %d%% heap; continuing to avoid a deadlock. "
                        + "Give the server more memory if this keeps happening.", timeoutMs, heapPercent());
                return;
            }
            if (M.ms() - lastLog >= PROGRESS_LOG_INTERVAL_MS) {
                lastLog = M.ms();
                Iris.info("Pregen heap pressure: paused at %d%% heap with %d tectonic plates resident, waiting for headroom.",
                        heapPercent(), mantle.getLoadedRegionCount());
            }
            if (!pause()) {
                return;
            }
        }
    }

    private int evictIdlePlates(Mantle mantle) {
        try {
            mantle.trim(0L, 0);
            return mantle.unloadTectonicPlate(0);
        } catch (Throwable e) {
            Iris.reportError(e);
            return 0;
        }
    }

    private boolean pause() {
        try {
            Thread.sleep(waitMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static long heapPercent() {
        return Math.round(MantleHeapPressure.usedFraction() * 100.0D);
    }

    private boolean isCancelled() {
        return Thread.currentThread().isInterrupted() || cancelled.getAsBoolean();
    }

    private Mantle resolveMantle() {
        try {
            return mantleSupplier.get();
        } catch (Throwable e) {
            return null;
        }
    }
}
