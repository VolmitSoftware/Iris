package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.studio.jigsaw.JigsawStudioService.ActiveStudio;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.Objects;

import static art.arcane.iris.studio.jigsaw.JigsawStudioAutosaveScheduler.AUTOSAVE_DEBOUNCE_TICKS;
import static art.arcane.iris.studio.jigsaw.JigsawStudioProtection.inventoryBlocks;

final class JigsawStudioDirtyTracker {
    private final JigsawStudioService service;

    JigsawStudioDirtyTracker(JigsawStudioService service) {
        this.service = Objects.requireNonNull(service, "Jigsaw Studio service");
    }

    boolean markDirty(World world, int worldX, int worldY, int worldZ) {
        if (world == null) {
            return false;
        }
        ActiveStudio studio = service.studios.get(world.getUID());
        if (studio == null) {
            return false;
        }
        if (service.saveLifecycle.reopenRequiredRequests.contains(studio.generator().getRequest().requestId())) {
            return false;
        }
        JigsawStudioSession session = studio.generator().getSession();
        JigsawStudioBay bay = session.layout().findAt(worldX, worldY, worldZ);
        if (bay == null) {
            return false;
        }
        JigsawStudioVariant activeVariant = session.activeVariant(bay.stableId()).orElse(null);
        if (activeVariant == null || !activeVariant.owned()) {
            return false;
        }
        JigsawStudioSession.DirtyMark dirtyMark = session.markWorkcellDirty(bay.stableId());
        if (dirtyMark.status() != JigsawStudioSession.DirtyStatus.MARKED) {
            return false;
        }
        service.autosaveScheduler.scheduleAutosave(
                studio,
                bay,
                dirtyMark.identity().orElseThrow(),
                AUTOSAVE_DEBOUNCE_TICKS);
        service.markEvaluationStale(studio);
        if (dirtyMark.newlyDirty()) {
            service.playerContext.refreshWorkcellContext(studio.worldId(), bay.stableId());
        }
        return true;
    }

    int markAllDirty(World world) {
        if (world == null) {
            return 0;
        }
        ActiveStudio studio = service.studios.get(world.getUID());
        return studio == null ? 0 : markAllDirty(studio);
    }

    void markDirty(Block block) {
        if (block != null) {
            markDirty(block.getWorld(), block.getX(), block.getY(), block.getZ());
        }
    }

    void markDirty(List<Block> blocks) {
        for (Block block : blocks) {
            markDirty(block);
        }
    }

    void markDirty(Inventory inventory) {
        for (Block block : inventoryBlocks(inventory)) {
            markDirty(block);
        }
    }

    void markAllDirty() {
        for (ActiveStudio studio : service.studios.values()) {
            markAllDirty(studio);
        }
    }

    int markAllDirty(ActiveStudio studio) {
        int changed = 0;
        JigsawStudioSession session = studio.generator().getSession();
        for (JigsawStudioBay bay : session.layout().bays()) {
            JigsawStudioVariant activeVariant = session.activeVariant(bay.stableId()).orElse(null);
            if (activeVariant == null || !activeVariant.owned()) {
                continue;
            }
            JigsawStudioSession.DirtyMark dirtyMark = session.markWorkcellDirty(bay.stableId());
            if (dirtyMark.status() == JigsawStudioSession.DirtyStatus.MARKED) {
                service.autosaveScheduler.scheduleAutosave(
                        studio,
                        bay,
                        dirtyMark.identity().orElseThrow(),
                        AUTOSAVE_DEBOUNCE_TICKS);
                changed++;
            }
        }
        if (changed > 0) {
            service.markEvaluationStale(studio);
            for (JigsawStudioBay bay : session.layout().bays()) {
                service.playerContext.refreshWorkcellContext(studio.worldId(), bay.stableId());
            }
        }
        return changed;
    }
}
