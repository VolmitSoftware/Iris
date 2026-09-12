package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.studio.jigsaw.JigsawStudioService.ActiveStudio;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.collection.KMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static art.arcane.iris.studio.jigsaw.JigsawStudioService.AUTOSAVE_RETRY_TICKS;

final class JigsawStudioTileWatcher {
    private static final int JIGSAW_TILE_WATCH_INTERVAL_TICKS = 5;

    private final JigsawStudioService service;
    final Map<JigsawTileWatchKey, JigsawTileWatch> jigsawTileWatches = new ConcurrentHashMap<>();

    JigsawStudioTileWatcher(JigsawStudioService service) {
        this.service = Objects.requireNonNull(service, "Jigsaw Studio service");
    }

    void startJigsawTileWatch(Player player, Block block) {
        if (player == null || block == null || block.getType() != Material.JIGSAW) {
            return;
        }
        World world = block.getWorld();
        int worldX = block.getX();
        int worldY = block.getY();
        int worldZ = block.getZ();
        UUID playerId = player.getUniqueId();
        if (J.isOwnedByCurrentRegion(world, worldX >> 4, worldZ >> 4)) {
            initializeJigsawTileWatch(playerId, world, worldX, worldY, worldZ);
            return;
        }
        boolean scheduled = J.runRegion(
                world,
                worldX >> 4,
                worldZ >> 4,
                () -> initializeJigsawTileWatch(playerId, world, worldX, worldY, worldZ));
        if (!scheduled) {
            IrisLogging.warn("Jigsaw Studio could not watch jigsaw marker NBT at %d,%d,%d in %s",
                    worldX, worldY, worldZ, world.getName());
        }
    }

    private void initializeJigsawTileWatch(
            UUID playerId,
            World world,
            int worldX,
            int worldY,
            int worldZ
    ) {
        ActiveStudio studio = service.studios.get(world.getUID());
        if (!service.enabled || studio == null) {
            return;
        }
        Block block = world.getBlockAt(worldX, worldY, worldZ);
        if (block.getType() != Material.JIGSAW) {
            return;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay bay = session.layout().findAt(worldX, worldY, worldZ);
        JigsawStudioVariant variant = bay == null
                ? null
                : session.activeVariant(bay.stableId()).orElse(null);
        if (bay == null || variant == null || !variant.owned()) {
            return;
        }
        KMap<String, Object> snapshot;
        try {
            snapshot = BukkitPlatform.serializeTile(block.getLocation());
        } catch (Throwable exception) {
            IrisLogging.reportError(exception);
            return;
        }
        if (snapshot == null) {
            IrisLogging.warn("Jigsaw Studio could not read jigsaw marker NBT at %d,%d,%d in %s",
                    worldX, worldY, worldZ, world.getName());
            return;
        }
        JigsawStudioActivation.Request request = studio.generator().getRequest();
        JigsawTileWatchKey key = new JigsawTileWatchKey(
                request.requestId(), world.getUID(), worldX, worldY, worldZ);
        JigsawTileWatch watch = new JigsawTileWatch(
                key,
                studio,
                playerId,
                bay.stableId(),
                snapshot,
                new AtomicBoolean(false),
                new AtomicBoolean(false));
        jigsawTileWatches.put(key, watch);
        scheduleJigsawTileWatch(watch);
    }

    private void scheduleJigsawTileWatch(JigsawTileWatch watch) {
        boolean scheduled = J.runRegion(
                watch.studio().world(),
                watch.key().worldX() >> 4,
                watch.key().worldZ() >> 4,
                () -> runJigsawTileWatch(watch, false),
                JIGSAW_TILE_WATCH_INTERVAL_TICKS);
        if (!scheduled && jigsawTileWatches.get(watch.key()) == watch) {
            warnJigsawTileWatchScheduleFailure(watch);
            scheduleJigsawTileWatchReconciliation(watch, false);
        }
    }

    private void runJigsawTileWatch(JigsawTileWatch watch, boolean release) {
        if (jigsawTileWatches.get(watch.key()) != watch) {
            return;
        }
        ActiveStudio studio = service.studios.get(watch.key().worldId());
        if (!service.enabled || studio != watch.studio()
                || !service.isCurrentRequest(studio, watch.key().requestId())) {
            jigsawTileWatches.remove(watch.key(), watch);
            return;
        }
        Block block = studio.world().getBlockAt(
                watch.key().worldX(), watch.key().worldY(), watch.key().worldZ());
        JigsawStudioBay bay = studio.generator().getSession().layout().findAt(
                watch.key().worldX(), watch.key().worldY(), watch.key().worldZ());
        if (block.getType() != Material.JIGSAW
                || bay == null
                || !bay.stableId().equals(watch.workcellId())) {
            jigsawTileWatches.remove(watch.key(), watch);
            return;
        }
        KMap<String, Object> snapshot;
        try {
            snapshot = BukkitPlatform.serializeTile(block.getLocation());
        } catch (Throwable exception) {
            jigsawTileWatches.remove(watch.key(), watch);
            IrisLogging.reportError(exception);
            return;
        }
        if (snapshot == null) {
            jigsawTileWatches.remove(watch.key(), watch);
            IrisLogging.warn("Jigsaw Studio stopped watching unreadable jigsaw marker NBT at %d,%d,%d in %s",
                    watch.key().worldX(),
                    watch.key().worldY(),
                    watch.key().worldZ(),
                    studio.world().getName());
            return;
        }
        JigsawTilePollDecision decision = jigsawTilePollDecision(
                watch.snapshot(), snapshot, release);
        if (decision.changed()) {
            service.dirtyTracker.markDirty(
                    studio.world(),
                    watch.key().worldX(),
                    watch.key().worldY(),
                    watch.key().worldZ());
        }
        if (!decision.continueWatching()) {
            jigsawTileWatches.remove(watch.key(), watch);
            return;
        }
        JigsawTileWatch next = new JigsawTileWatch(
                watch.key(),
                watch.studio(),
                watch.playerId(),
                watch.workcellId(),
                snapshot,
                watch.scheduleFailureLogged(),
                new AtomicBoolean(false));
        if (jigsawTileWatches.replace(watch.key(), watch, next)) {
            scheduleJigsawTileWatch(next);
        }
    }

    boolean hasJigsawTileWatch(UUID requestId) {
        if (requestId == null) {
            return false;
        }
        for (JigsawTileWatchKey key : jigsawTileWatches.keySet()) {
            if (requestId.equals(key.requestId())) {
                return true;
            }
        }
        return false;
    }

    void clearJigsawTileWatches(UUID requestId) {
        if (requestId != null) {
            jigsawTileWatches.keySet().removeIf(key -> requestId.equals(key.requestId()));
        }
    }

    void finalizeJigsawTileWatchesForPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        for (JigsawTileWatch watch : List.copyOf(jigsawTileWatches.values())) {
            if (playerId.equals(watch.playerId())) {
                finalizeJigsawTileWatch(watch);
            }
        }
    }

    void finalizeJigsawTileWatches(UUID requestId) {
        if (requestId == null) {
            return;
        }
        for (JigsawTileWatch watch : List.copyOf(jigsawTileWatches.values())) {
            if (requestId.equals(watch.key().requestId())) {
                finalizeJigsawTileWatch(watch);
            }
        }
    }

    void finalizeAllJigsawTileWatches() {
        for (JigsawTileWatch watch : List.copyOf(jigsawTileWatches.values())) {
            try {
                finalizeJigsawTileWatch(watch);
            } catch (Throwable exception) {
                IrisLogging.reportError("Failed to finalize a Jigsaw Studio tile watch during shutdown.", exception);
            }
        }
    }

    private void finalizeJigsawTileWatch(JigsawTileWatch watch) {
        if (jigsawTileWatches.get(watch.key()) != watch) {
            return;
        }
        World world = watch.studio().world();
        int chunkX = watch.key().worldX() >> 4;
        int chunkZ = watch.key().worldZ() >> 4;
        if (J.isOwnedByCurrentRegion(world, chunkX, chunkZ)) {
            runJigsawTileWatch(watch, true);
            return;
        }
        boolean scheduled = J.runRegion(
                world,
                chunkX,
                chunkZ,
                () -> runJigsawTileWatch(watch, true));
        if (!scheduled) {
            warnJigsawTileWatchScheduleFailure(watch);
            scheduleJigsawTileWatchReconciliation(watch, true);
        }
    }

    private void warnJigsawTileWatchScheduleFailure(JigsawTileWatch watch) {
        if (watch.scheduleFailureLogged().compareAndSet(false, true)) {
            IrisLogging.warn("Jigsaw Studio could not schedule jigsaw marker NBT at %d,%d,%d in %s; Iris will keep retrying",
                    watch.key().worldX(),
                    watch.key().worldY(),
                    watch.key().worldZ(),
                    watch.studio().world().getName());
        }
    }

    private void scheduleJigsawTileWatchReconciliation(JigsawTileWatch watch, boolean release) {
        if (!watch.reconciliationScheduled().compareAndSet(false, true)) {
            return;
        }
        try {
            J.s(() -> {
                watch.reconciliationScheduled().set(false);
                if (jigsawTileWatches.get(watch.key()) != watch) {
                    return;
                }
                if (release) {
                    finalizeJigsawTileWatch(watch);
                } else {
                    scheduleJigsawTileWatch(watch);
                }
            }, AUTOSAVE_RETRY_TICKS);
        } catch (Throwable exception) {
            watch.reconciliationScheduled().set(false);
            IrisLogging.reportError(exception);
        }
    }

    static boolean tileSnapshotChanged(
            KMap<String, Object> previous,
            KMap<String, Object> current
    ) {
        return !Objects.equals(previous, current);
    }

    static JigsawTilePollDecision jigsawTilePollDecision(
            KMap<String, Object> previous,
            KMap<String, Object> current,
            boolean release
    ) {
        return new JigsawTilePollDecision(tileSnapshotChanged(previous, current), !release);
    }

    private record JigsawTileWatchKey(
            UUID requestId,
            UUID worldId,
            int worldX,
            int worldY,
            int worldZ
    ) {
        private JigsawTileWatchKey {
            Objects.requireNonNull(requestId, "Jigsaw Studio tile watch request ID");
            Objects.requireNonNull(worldId, "Jigsaw Studio tile watch world ID");
        }
    }

    record JigsawTilePollDecision(boolean changed, boolean continueWatching) {
    }

    private record JigsawTileWatch(
            JigsawTileWatchKey key,
            ActiveStudio studio,
            UUID playerId,
            String workcellId,
            KMap<String, Object> snapshot,
            AtomicBoolean scheduleFailureLogged,
            AtomicBoolean reconciliationScheduled
    ) {
        private JigsawTileWatch {
            Objects.requireNonNull(key, "Jigsaw Studio tile watch key");
            Objects.requireNonNull(studio, "Jigsaw Studio tile watch studio");
            Objects.requireNonNull(playerId, "Jigsaw Studio tile watch player ID");
            Objects.requireNonNull(workcellId, "Jigsaw Studio tile watch workcell ID");
            Objects.requireNonNull(snapshot, "Jigsaw Studio tile watch snapshot");
            Objects.requireNonNull(scheduleFailureLogged, "Jigsaw Studio tile watch schedule warning state");
            Objects.requireNonNull(reconciliationScheduled, "Jigsaw Studio tile watch retry schedule state");
        }
    }
}
