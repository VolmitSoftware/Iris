package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.decoration.tree.TreeBlockMaterial;
import art.arcane.iris.integration.Identifier;
import art.arcane.iris.world.storage.matter.TileWrapper;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterSlice;

import java.util.List;
import java.util.Map;
import java.util.BitSet;
import java.util.function.Supplier;

public final class ObjectContinuationPersistence {
    private ObjectContinuationPersistence() {
    }

    public static void persist(Mantle<Matter> mantle, ObjectContinuationBundle.PlacementKey key,
                               ObjectContinuationBundle.Bounds bounds,
                               Map<ObjectContinuationBundle.ChunkPosition, Supplier<Matter>> destinations) {
        if (destinations.size() < 2) {
            return;
        }
        List<ObjectContinuationBundle.ChunkPosition> touched = List.copyOf(destinations.keySet());
        for (Map.Entry<ObjectContinuationBundle.ChunkPosition, Supplier<Matter>> entry : destinations.entrySet()) {
            ObjectContinuationBundle.ChunkPosition destination = entry.getKey();
            MantleChunk<Matter> chunk = mantle.useChunk(destination.x(), destination.z());
            try {
                synchronized (chunk) {
                    if (chunk.isFlagged(MantleFlag.REAL)) {
                        continue;
                    }
                    Matter section = chunk.getOrCreate(0);
                    ObjectContinuationBundle bundle = section.hasSlice(ObjectContinuationBundle.class)
                            ? section.<ObjectContinuationBundle>getSlice(ObjectContinuationBundle.class).get(0, 0, 0) : null;
                    if (contains(bundle, key)) {
                        continue;
                    }
                    ObjectContinuationBundle.Fragment fragment = new ObjectContinuationBundle.Fragment(
                            key, bounds, touched, ObjectContinuationBundle.encode(entry.getValue().get()));
                    section.slice(ObjectContinuationBundle.class).set(0, 0, 0,
                            bundle == null ? new ObjectContinuationBundle(List.of(fragment)) : bundle.with(fragment));
                }
            } finally {
                chunk.release();
            }
        }
    }

    public static void put(Matter payload, int x, int y, int z, Object value) {
        if (value instanceof NativeBlockState state) {
            MatterSlice<TileWrapper> tiles = payload.getSlice(TileWrapper.class);
            if (tiles != null) {
                tiles.set(x, y, z, null);
            }
            String customKey = state.deferredPlacementKey();
            NativeBlockState base = state.placementBaseState();
            boolean custom = state.isCustom() && customKey != null && base != null;
            payload.slice(NativeBlockState.class).set(x, y, z, custom ? base : state);
            payload.slice(Identifier.class).set(x, y, z, custom ? Identifier.fromString(customKey) : null);
        } else if (value instanceof TileData tile) {
            payload.slice(TileWrapper.class).set(x, y, z, new TileWrapper(tile));
        } else {
            payload.slice(payload.getClass(value)).set(x, y, z, value);
        }
    }

    public static boolean beginBlock(Matter payload, int x, int y, int z, boolean canopy) {
        if (canopy) {
            MatterSlice<NativeBlockState> blocks = payload.getSlice(NativeBlockState.class);
            NativeBlockState existing = blocks == null ? null : blocks.get(x, y, z);
            if (existing != null && existing.isSolid()) {
                return false;
            }
            payload.slice(TreeCanopy.class).set(x, y, z, TreeCanopy.INSTANCE);
        } else {
            MatterSlice<TreeCanopy> conditions = payload.getSlice(TreeCanopy.class);
            if (conditions != null) {
                conditions.set(x, y, z, null);
            }
        }
        return true;
    }

    public static void restore(Mantle<Matter> mantle, int chunkX, int chunkZ, Matter continuation,
                               Hunk<NativeBlockState> blocks, boolean applyBlocks) {
        BitSet blocked = blockedCanopy(continuation, blocks, applyBlocks);
        MatterSlice<NativeBlockState> states = continuation.getSlice(NativeBlockState.class);
        MantleChunk<Matter> chunk = mantle.useChunk(chunkX, chunkZ);
        try {
            synchronized (chunk) {
                if (states != null) {
                    states.iterateSync((x, y, z, state) -> {
                        if (blocked.get(cellIndex(x, y, z))) {
                            return;
                        }
                        Matter section = chunk.get(y >> 4);
                        if (section != null) {
                            clearMetadata(section, x, y & 15, z);
                        }
                    });
                }
                for (Map.Entry<Class<?>, MatterSlice<?>> entry : continuation.getSliceMap().entrySet()) {
                    if (entry.getKey() == TreeCanopy.class) {
                        continue;
                    }
                    entry.getValue().iterateSync((x, y, z, value) -> {
                        if (!blocked.get(cellIndex(x, y, z))) {
                            chunk.getOrCreate(y >> 4).slice(entry.getKey()).set(x, y & 15, z, value);
                        }
                    });
                }
            }
        } finally {
            chunk.release();
        }
        if (applyBlocks && states != null) {
            states.iterateSync((x, y, z, state) -> {
                if (!blocked.get(cellIndex(x, y, z))) {
                    blocks.set(x, y, z, state);
                }
            });
        }
    }

    private static BitSet blockedCanopy(Matter continuation, Hunk<NativeBlockState> blocks, boolean applyBlocks) {
        BitSet blocked = new BitSet();
        MatterSlice<TreeCanopy> conditions = continuation.getSlice(TreeCanopy.class);
        if (conditions != null) {
            conditions.iterateSync((x, y, z, condition) -> {
                NativeBlockState existing = blocks.get(x, y, z);
                if (!applyBlocks || existing != null && existing.isSolid()) {
                    blocked.set(cellIndex(x, y, z));
                }
            });
        }
        return blocked;
    }

    private static int cellIndex(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }

    private static void clearMetadata(Matter section, int x, int y, int z) {
        clearMetadata(section.getSlice(Identifier.class), x, y, z);
        clearMetadata(section.getSlice(String.class), x, y, z);
        clearMetadata(section.getSlice(TreeBlockMaterial.class), x, y, z);
        clearMetadata(section.getSlice(TileWrapper.class), x, y, z);
    }

    private static void clearMetadata(MatterSlice<?> slice, int x, int y, int z) {
        if (slice != null) {
            slice.set(x, y, z, null);
        }
    }

    private static boolean contains(ObjectContinuationBundle bundle, ObjectContinuationBundle.PlacementKey key) {
        if (bundle == null) {
            return false;
        }
        for (ObjectContinuationBundle.Fragment fragment : bundle.fragments()) {
            if (fragment.key().equals(key)) {
                return true;
            }
        }
        return false;
    }
}
