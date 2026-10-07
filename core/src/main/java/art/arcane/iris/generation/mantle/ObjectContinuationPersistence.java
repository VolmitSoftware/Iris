package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.integration.Identifier;
import art.arcane.iris.world.storage.matter.TileWrapper;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterSlice;

import java.util.List;
import java.util.Map;
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
