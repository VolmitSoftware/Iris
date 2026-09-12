package art.arcane.iris.generation.runtime;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.studio.view.PregeneratorJob;
import art.arcane.iris.world.pregen.MantleHeapPressure;
import art.arcane.iris.generation.validation.GoldenHashEngine;
import art.arcane.iris.world.WorldMaintenance;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class EngineMaintenance {
    private EngineMaintenance() {
    }

    public static boolean isAvailable(Engine engine) {
        return engine != null
                && !engine.isClosing()
                && !engine.isClosed()
                && !engine.getMantle().getMantle().isClosed();
    }

    public static boolean pregeneratorTargets(Engine engine) {
        if (engine == null || engine.getWorld() == null) {
            return false;
        }

        PregeneratorJob pregeneratorJob = PregeneratorJob.getInstance();
        return pregeneratorJob != null && pregeneratorJob.targetsWorldIdentity(engine.getWorld().identity());
    }

    public static boolean shouldRun(Engine engine) {
        if (!shouldRunStudioMaintenance(
                engine.isStudio(),
                IrisSettings.get().getPerformance().isTrimMantleInStudio(),
                engine.isStudio() && MantleHeapPressure.overHighWater())) {
            return false;
        }
        if (GoldenHashEngine.isActive()) {
            return false;
        }

        return engine.getWorld() == null
                || !WorldMaintenance.isWorldMaintenanceActive(engine.getWorld().identity());
    }

    static boolean shouldRunStudioMaintenance(
            boolean studio,
            boolean trimMantleInStudio,
            boolean heapPressure
    ) {
        return !studio || trimMantleInStudio || heapPressure;
    }

    public static int workerParallelism() {
        return IrisSettings.get().getPerformance().getEngineSVC().getParallelism();
    }

    public static Outcome run(Engine engine) {
        IrisSettings.IrisSettingsPerformance settings = IrisSettings.get().getPerformance();
        double heapUsage = MantleHeapPressure.usedFraction();
        Plan plan = plan(settings.getMantleKeepAlive(), heapUsage, settings.getEngineSVC().isForceMulticoreWrite());

        engine.getMantle().trim(plan.idleDurationMillis());

        long unloadStart = System.nanoTime();
        int unloadedTectonicPlates = engine.getMantle().unloadTectonicPlate(
                plan.multicoreUnload() ? 0 : Integer.MAX_VALUE);
        if (plan.heapPressure()) {
            MantleHeapPressure.requestPanicReclaim();
        }

        return new Outcome(
                unloadedTectonicPlates,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - unloadStart));
    }

    public static boolean isMantleClosed(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("mantle is closed")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    static Plan plan(int mantleKeepAliveSeconds, double heapUsage, boolean forceMulticoreWrite) {
        long baseIdleDurationMillis = TimeUnit.SECONDS.toMillis(Math.max(0, mantleKeepAliveSeconds));
        double reclaimUrgency = MantleHeapPressure.reclaimUrgency(heapUsage);
        long idleDurationMillis = Math.round(baseIdleDurationMillis * (1D - reclaimUrgency));
        boolean heapPressure = reclaimUrgency >= 1D;
        return new Plan(idleDurationMillis, heapPressure || forceMulticoreWrite, heapPressure);
    }

    public record Outcome(int unloadedTectonicPlates, long unloadDurationMillis) {
    }

    record Plan(long idleDurationMillis, boolean multicoreUnload, boolean heapPressure) {
    }
}
