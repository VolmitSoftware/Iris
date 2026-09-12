package art.arcane.iris.world;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.mantle.MantleSliceRetention;
import art.arcane.iris.spi.IrisLogging;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class WorldMaintenance {
    private static final Map<String, AtomicInteger> worldMaintenanceDepth = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> worldMaintenanceMantleBypassDepth = new ConcurrentHashMap<>();

    private WorldMaintenance() {
    }

    public static void beginWorldMaintenance(String worldName, String reason) {
        beginWorldMaintenance(worldName, reason, false);
    }

    public static void beginWorldMaintenance(String worldName, String reason, boolean bypassMantleStages) {
        if (worldName == null) {
            return;
        }

        int depth = incrementDepth(worldMaintenanceDepth, worldName);
        if (bypassMantleStages) {
            incrementDepth(worldMaintenanceMantleBypassDepth, worldName);
        }
        if (IrisSettings.get().getGeneral().isDebug()) {
            IrisLogging.info("World maintenance enter: " + worldName + " reason=" + reason + " depth=" + depth + " bypassMantle=" + bypassMantleStages);
        } else {
            IrisLogging.debug("World maintenance enter: " + worldName + " reason=" + reason + " depth=" + depth + " bypassMantle=" + bypassMantleStages);
        }
    }

    public static void endWorldMaintenance(String worldName, String reason) {
        endWorldMaintenance(worldName, reason, false);
    }

    public static void endWorldMaintenance(String worldName, String reason, boolean bypassMantleStages) {
        if (worldName == null) {
            return;
        }

        if (!worldMaintenanceDepth.containsKey(worldName)) {
            return;
        }

        int depth = decrementDepth(worldMaintenanceDepth, worldName);

        // Only a bypass-begin's paired end releases bypass credit: a plain end overlapping a
        // bypassing operation stole its credit and re-enabled mantle stages under it.
        int bypassDepth = 0;
        if (bypassMantleStages) {
            bypassDepth = decrementDepth(worldMaintenanceMantleBypassDepth, worldName);
        }

        if (IrisSettings.get().getGeneral().isDebug()) {
            IrisLogging.info("World maintenance exit: " + worldName + " reason=" + reason + " depth=" + depth + " bypassMantleDepth=" + bypassDepth);
        } else {
            IrisLogging.debug("World maintenance exit: " + worldName + " reason=" + reason + " depth=" + depth + " bypassMantleDepth=" + bypassDepth);
        }
    }

    // Depth mutations run inside compute() so an identity-based remove can never clear a
    // registration a concurrent begin just re-incremented.
    private static int incrementDepth(Map<String, AtomicInteger> depths, String worldName) {
        return depths.compute(worldName, (key, current) -> {
            AtomicInteger counter = current == null ? new AtomicInteger() : current;
            counter.incrementAndGet();
            return counter;
        }).get();
    }

    private static int decrementDepth(Map<String, AtomicInteger> depths, String worldName) {
        AtomicInteger remaining = depths.computeIfPresent(
                worldName, (key, current) -> current.decrementAndGet() <= 0 ? null : current);
        return remaining == null ? 0 : Math.max(0, remaining.get());
    }

    public static boolean isWorldMaintenanceActive(String worldName) {
        if (worldName == null) {
            return false;
        }

        AtomicInteger counter = worldMaintenanceDepth.get(worldName);
        return counter != null && counter.get() > 0;
    }

    public static boolean isWorldMaintenanceBypassingMantleStages(String worldName) {
        if (worldName == null) {
            return false;
        }

        AtomicInteger counter = worldMaintenanceMantleBypassDepth.get(worldName);
        return counter != null && counter.get() > 0;
    }

    public static void retainMantleDataForSlice(String className) {
        MantleSliceRetention.retain(className);
    }

    public static boolean isRetainingMantleDataForSlice(String className) {
        return MantleSliceRetention.isRetained(className);
    }
}
