package art.arcane.iris.world.lifecycle;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One warning per orphaned Iris world per boot.
 * <p>
 * The bootstrap storage audit, the bukkit.yml binding read and the worlds.json registry read all notice the
 * same orphan, and the registry read runs on every {@code getWorlds()}, so the warning is deduplicated by
 * configured world name instead of being emitted per code path.
 * <p>
 * Paper runs the plugin bootstrap before any plugin logger exists, so a warning raised there reaches the
 * console and the runtime log but never the instance's {@code logs/latest.log}, which is the only log most
 * operators read. Warnings raised while no platform is bound are held and re-emitted once by
 * {@link #replayToPlatformLog()}.
 * <p>
 * Nothing here prunes configuration. A world folder can be missing because a drive is unmounted or a backup
 * restore is in flight, and the surviving bukkit.yml and Multiverse entries are exactly what makes putting
 * the folder back a complete recovery.
 */
public final class MissingWorldStorageLog {
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static final List<String[]> PENDING = new ArrayList<>();
    private static final Object PENDING_LOCK = new Object();

    private MissingWorldStorageLog() {
    }

    public static void warnOnce(String configuredWorldName, Path expectedStorage) {
        String worldName = Objects.requireNonNull(configuredWorldName, "configuredWorldName");
        Path storage = Objects.requireNonNull(expectedStorage, "expectedStorage");
        if (!WARNED.add(worldName)) {
            return;
        }
        emit(new String[]{
                IrisLogging.format("Iris world %s is configured but has no world storage at %s; it will not load.",
                        worldName, storage),
                IrisLogging.format("Restore that folder, or drop the world with /iris remove world=%s delete=true.",
                        worldName)
        });
    }

    /**
     * Reports a world folder that survives with no pack snapshot and no world data - the skeleton the server
     * writes back when it saves a level whose folder was deleted underneath it. There is nothing in it to
     * lose, so it is reported instead of stopping startup, and the remedy is a delete rather than a restore.
     */
    public static void warnEmptyOnce(String configuredWorldName, Path storage) {
        String worldName = Objects.requireNonNull(configuredWorldName, "configuredWorldName");
        Path worldStorage = Objects.requireNonNull(storage, "storage");
        if (!WARNED.add(worldName)) {
            return;
        }
        emit(new String[]{
                IrisLogging.format("Iris world %s has an empty world folder at %s: no pack snapshot, no region"
                        + " data; it will generate as vanilla terrain.", worldName, worldStorage),
                IrisLogging.format("Delete that folder, or drop the world with /iris remove world=%s delete=true.",
                        worldName)
        });
    }

    public static boolean hasWarned(String configuredWorldName) {
        return WARNED.contains(Objects.requireNonNull(configuredWorldName, "configuredWorldName"));
    }

    /**
     * Re-emits every warning raised before a platform was bound, exactly once. Safe to call more than once
     * and safe to call when nothing was held.
     */
    public static void replayToPlatformLog() {
        List<String[]> held;
        synchronized (PENDING_LOCK) {
            if (PENDING.isEmpty()) {
                return;
            }
            held = List.copyOf(PENDING);
            PENDING.clear();
        }
        for (String[] lines : held) {
            write(lines);
        }
    }

    /**
     * Visible for tests: the deduplication set is per JVM, not per server boot.
     */
    public static void reset() {
        WARNED.clear();
        synchronized (PENDING_LOCK) {
            PENDING.clear();
        }
    }

    private static void emit(String[] lines) {
        write(lines);
        if (IrisPlatforms.isBound()) {
            return;
        }
        synchronized (PENDING_LOCK) {
            PENDING.add(lines);
        }
    }

    private static void write(String[] lines) {
        for (String line : lines) {
            // Pre-formatted: passing no arguments keeps a path containing '%' out of the format engine.
            IrisLogging.warn(line);
        }
    }
}
