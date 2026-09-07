package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.mantle.MantleWriter;
import art.arcane.iris.engine.object.TileData;
import art.arcane.iris.engine.object.IObjectPlacer;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.project.matter.TileWrapper;
import art.arcane.volmlib.util.matter.MatterCavern;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ObjectDestinationTransaction implements ObjectPassPlacer {
    private static final Object CLEARED = new Object();

    private final MantleWriter writer;
    private final int destinationChunkX;
    private final int destinationChunkZ;
    private final int worldHeight;
    private final List<Mutation> mutations;
    private final Map<DataKey, Object> overlay;

    ObjectDestinationTransaction(MantleWriter writer, int destinationChunkX, int destinationChunkZ) {
        this.writer = writer;
        this.destinationChunkX = destinationChunkX;
        this.destinationChunkZ = destinationChunkZ;
        this.worldHeight = writer.getMantle().getWorldHeight();
        this.mutations = new ArrayList<>();
        this.overlay = new HashMap<>();
    }

    void commit() {
        writer.withChunkFence(destinationChunkX, destinationChunkZ, this::commitFenced);
    }

    int mutationCheckpoint() {
        return mutations.size();
    }

    ObjectSourcePlan sourcePlanSince(int checkpoint) {
        if (checkpoint < 0 || checkpoint > mutations.size()) {
            throw new IllegalArgumentException("Mutation checkpoint is outside the transaction");
        }
        return new ObjectSourcePlan(mutations.subList(checkpoint, mutations.size()));
    }

    void apply(ObjectSourcePlan plan) {
        for (Mutation mutation : plan.mutations()) {
            mutation.apply(this);
        }
    }

    private void commitFenced() {
        LinkedHashMap<DataKey, Object> originals = new LinkedHashMap<>();
        try {
            for (Mutation mutation : mutations) {
                if (!isDestination(mutation.x(), mutation.z())) {
                    continue;
                }
                captureOriginal(originals, mutation.key());
                if (mutation.key().type() == PlatformBlockState.class) {
                    captureOriginal(originals, new DataKey(
                            mutation.key().x(),
                            mutation.key().y(),
                            mutation.key().z(),
                            Identifier.class
                    ));
                }
                mutation.apply(writer);
            }
        } catch (Throwable failure) {
            rollback(originals, failure);
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Object destination publication failed", failure);
        }
    }

    @Override
    public int getHighest(int x, int z, IrisData data) {
        return writer.getHighest(x, z, data);
    }

    @Override
    public int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
        return writer.getHighest(x, z, data, ignoreFluid);
    }

    @Override
    public void set(int x, int y, int z, PlatformBlockState state) {
        if (state == null) {
            return;
        }
        String placementKey = state.deferredPlacementKey();
        PlatformBlockState baseState = state.placementBaseState();
        if (state.isCustom() && placementKey != null && baseState != null) {
            if (!canSetBlock(x, y, z)) {
                return;
            }
            DataKey blockKey = new DataKey(x, y, z, PlatformBlockState.class);
            Identifier identifier = Identifier.fromString(placementKey);
            overlay.put(blockKey, baseState);
            overlay.put(new DataKey(x, y, z, Identifier.class), identifier);
            mutations.add(new CustomBlockMutation(blockKey, state));
            return;
        }
        setData(x, y, z, state);
    }

    @Override
    public PlatformBlockState get(int x, int y, int z) {
        DataKey key = new DataKey(x, y, z, PlatformBlockState.class);
        Object value = overlay.get(key);
        if (value == CLEARED) {
            return EngineMantle.AIR.get();
        }
        return value instanceof PlatformBlockState state
                ? state
                : writer.getPrerequisiteBlock(x, y, z);
    }

    @Override
    public boolean isPreventingDecay() {
        return writer.isPreventingDecay();
    }

    @Override
    public boolean isCarved(int x, int y, int z) {
        Object block = overlay.get(new DataKey(x, y, z, PlatformBlockState.class));
        if (block instanceof PlatformBlockState state && !state.isAir() && !state.isFluid()) {
            return false;
        }
        Object hydrologyValue = overlay.get(new DataKey(x, y, z, HydrologyCaveCell.class));
        if (hydrologyValue instanceof HydrologyCaveCell hydrology) {
            return hydrology.carves();
        }
        Object cavern = overlay.get(new DataKey(x, y, z, MatterCavern.class));
        return cavern == null ? writer.isPrerequisiteCarved(x, y, z) : cavern != CLEARED;
    }

    @Override
    public boolean isSurfaceSolid(int x, int y, int z) {
        return writer.isSurfaceSolid(x, y, z);
    }

    @Override
    public boolean isSolid(int x, int y, int z) {
        return B.isSolid(get(x, y, z));
    }

    @Override
    public boolean isUnderwater(int x, int z) {
        return writer.isUnderwater(x, z);
    }

    @Override
    public int getFluidHeight() {
        return writer.getFluidHeight();
    }

    @Override
    public boolean isDebugSmartBore() {
        return writer.isDebugSmartBore();
    }

    @Override
    public void setTile(int x, int y, int z, TileData tile) {
        if (tile != null) {
            setData(x, y, z, new TileWrapper(tile));
        }
    }

    @Override
    public <T> void setData(int x, int y, int z, T data) {
        if (data == null || y < 0 || y >= worldHeight) {
            return;
        }
        if (data instanceof PlatformBlockState && !canSetBlock(x, y, z)) {
            return;
        }
        if (data instanceof MatterCavern && hasProtectedHydrology(x, y, z)) {
            return;
        }
        Class<?> type = data instanceof PlatformBlockState ? PlatformBlockState.class : data.getClass();
        if (data instanceof PlatformBlockState) {
            overlay.put(new DataKey(x, y, z, Identifier.class), CLEARED);
        }
        DataKey key = new DataKey(x, y, z, type);
        overlay.put(key, data);
        mutations.add(new SetMutation(key, data));
    }

    @Override
    public <T> @Nullable T getDataIfPresent(int x, int y, int z, Class<T> type) {
        DataKey key = new DataKey(x, y, z, type);
        Object value = overlay.get(key);
        if (value == CLEARED) {
            return null;
        }
        if (value != null) {
            return type.cast(value);
        }
        return writer.getPrerequisiteDataIfPresent(x, y, z, type);
    }

    @Override
    public byte[] getCarvedColumn(int x, int z, int height) {
        int cappedHeight = Math.min(Math.max(height, 0), worldHeight);
        byte[] carved = writer.getPrerequisiteCarvedColumn(x, z, cappedHeight);
        if (carved.length != cappedHeight) {
            byte[] resized = new byte[cappedHeight];
            System.arraycopy(carved, 0, resized, 0, Math.min(carved.length, cappedHeight));
            carved = resized;
        } else {
            carved = carved.clone();
        }
        for (int y = 0; y < cappedHeight; y++) {
            Object block = overlay.get(new DataKey(x, y, z, PlatformBlockState.class));
            if (block instanceof PlatformBlockState state && !state.isAir() && !state.isFluid()) {
                carved[y] = 0;
                continue;
            }
            DataKey hydrologyKey = new DataKey(x, y, z, HydrologyCaveCell.class);
            Object hydrologyValue = overlay.get(hydrologyKey);
            if (hydrologyValue instanceof HydrologyCaveCell hydrology) {
                carved[y] = hydrology.carves() ? (byte) 1 : 0;
                continue;
            }
            DataKey cavernKey = new DataKey(x, y, z, MatterCavern.class);
            Object cavernValue = overlay.get(cavernKey);
            if (cavernValue != null) {
                carved[y] = cavernValue == CLEARED ? (byte) 0 : (byte) 1;
            }
        }
        return carved;
    }

    @Override
    public Engine getEngine() {
        return writer.getEngine();
    }

    private Object prerequisiteOrCleared(int x, int y, int z, Class<?> type) {
        Object value = writer.getPrerequisiteDataIfPresent(x, y, z, type);
        return value == null ? CLEARED : value;
    }

    private void captureOriginal(LinkedHashMap<DataKey, Object> originals, DataKey key) {
        originals.computeIfAbsent(
                key,
                candidate -> prerequisiteOrCleared(
                        candidate.x(),
                        candidate.y(),
                        candidate.z(),
                        candidate.type()
                )
        );
    }

    private boolean isDestination(int x, int z) {
        return (x >> 4) == destinationChunkX && (z >> 4) == destinationChunkZ;
    }

    private boolean canSetBlock(int x, int y, int z) {
        if (y < 0 || y >= worldHeight) {
            return false;
        }
        if (y == 0 && writer.getEngine().getDimension().isBedrock()) {
            return false;
        }
        return !hasProtectedHydrology(x, y, z);
    }

    private boolean hasProtectedHydrology(int x, int y, int z) {
        HydrologyCaveCell hydrology = getDataIfPresent(x, y, z, HydrologyCaveCell.class);
        return hydrology != null && hydrology.protectsPlacement();
    }

    private void rollback(LinkedHashMap<DataKey, Object> originals, Throwable failure) {
        ArrayList<Map.Entry<DataKey, Object>> entries = new ArrayList<>(originals.entrySet());
        entries.sort((first, second) -> Boolean.compare(
                second.getKey().type() == PlatformBlockState.class,
                first.getKey().type() == PlatformBlockState.class
        ));
        for (Map.Entry<DataKey, Object> entry : entries) {
            DataKey key = entry.getKey();
            try {
                if (key.type() == PlatformBlockState.class
                        && writer.restorePrerequisiteCell(key.x(), key.y(), key.z())) {
                    continue;
                }
                if (writer.restorePrerequisiteData(key.x(), key.y(), key.z(), key.type())) {
                    continue;
                }
                writer.clearData(key.x(), key.y(), key.z(), key.type());
                Object original = entry.getValue();
                if (original != CLEARED) {
                    writer.setData(key.x(), key.y(), key.z(), original);
                }
            } catch (Throwable rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    record DataKey(int x, int y, int z, Class<?> type) {
    }

    interface Mutation {
        int x();

        int z();

        DataKey key();

        void apply(IObjectPlacer placer);

        default int weight() {
            return 1;
        }
    }

    record SetMutation(DataKey key, Object value) implements Mutation {
        @Override
        public int x() {
            return key.x();
        }

        @Override
        public int z() {
            return key.z();
        }

        @Override
        public void apply(IObjectPlacer placer) {
            placer.setData(key.x(), key.y(), key.z(), value);
        }
    }

    record CustomBlockMutation(DataKey key, PlatformBlockState state) implements Mutation {
        @Override
        public int x() {
            return key.x();
        }

        @Override
        public int z() {
            return key.z();
        }

        @Override
        public void apply(IObjectPlacer placer) {
            placer.set(key.x(), key.y(), key.z(), state);
        }

        @Override
        public int weight() {
            return 2;
        }
    }
}
