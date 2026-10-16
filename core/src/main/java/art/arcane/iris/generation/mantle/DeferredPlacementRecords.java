package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.integration.Identifier;
import art.arcane.iris.world.storage.matter.TileWrapper;
import art.arcane.volmlib.nativelib.terrain.NativeBlockChangeSet;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterSlice;

public final class DeferredPlacementRecords {
    private DeferredPlacementRecords() {
    }

    public static NativeBlockChangeSet track(Engine engine, NativeBlockChangeSet.Bounds bounds) {
        Mantle<Matter> mantle = engine.getMantle().getMantle();
        for (int chunkX = bounds.minChunkX(); chunkX <= bounds.maxChunkX(); chunkX++) {
            for (int chunkZ = bounds.minChunkZ(); chunkZ <= bounds.maxChunkZ(); chunkZ++) {
                if (hasRecords(mantle, chunkX, chunkZ)) {
                    return NativeBlockChangeSet.create(bounds);
                }
            }
        }
        return NativeBlockChangeSet.disabled();
    }

    public static void invalidate(Engine engine, NativeBlockChangeSet changes) {
        if (changes.isEmpty()) {
            return;
        }
        Mantle<Matter> mantle = engine.getMantle().getMantle();
        int minHeight = engine.getMinHeight();
        changes.forEachChunk((chunkX, chunkZ) -> invalidateChunk(mantle, changes, chunkX, chunkZ, minHeight));
    }

    private static boolean hasRecords(Mantle<Matter> mantle, int chunkX, int chunkZ) {
        MantleChunk<Matter> chunk = mantle.useChunk(chunkX, chunkZ);
        try {
            synchronized (chunk) {
                return hasRecords(chunk);
            }
        } finally {
            chunk.release();
        }
    }

    private static boolean hasRecords(MantleChunk<Matter> chunk) {
        for (int section = 0; section < chunk.sectionCount(); section++) {
            Matter matter = chunk.get(section);
            if (matter != null && (matter.hasSlice(Identifier.class) || matter.hasSlice(TileWrapper.class))) {
                return true;
            }
        }
        return false;
    }

    private static void invalidateChunk(Mantle<Matter> mantle, NativeBlockChangeSet changes,
                                        int chunkX, int chunkZ, int minHeight) {
        MantleChunk<Matter> chunk = mantle.useChunk(chunkX, chunkZ);
        try {
            synchronized (chunk) {
                if (!hasRecords(chunk)) {
                    return;
                }
                changes.forEachPosition(chunkX, chunkZ, (x, y, z) -> clearRecords(chunk, x & 15, y - minHeight, z & 15));
            }
        } finally {
            chunk.release();
        }
    }

    private static void clearRecords(MantleChunk<Matter> chunk, int x, int y, int z) {
        if (y < 0 || (y >> 4) >= chunk.sectionCount()) {
            return;
        }
        Matter section = chunk.get(y >> 4);
        if (section == null) {
            return;
        }
        clearRecord(section.getSlice(Identifier.class), x, y & 15, z);
        clearRecord(section.getSlice(TileWrapper.class), x, y & 15, z);
    }

    private static void clearRecord(MatterSlice<?> slice, int x, int y, int z) {
        if (slice != null) {
            slice.set(x, y, z, null);
        }
    }
}
