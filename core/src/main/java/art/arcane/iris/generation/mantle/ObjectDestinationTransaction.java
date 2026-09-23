package art.arcane.iris.generation.mantle;

import art.arcane.iris.integration.Identifier;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.decoration.tree.TreeBlockMaterial;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.structure.object.IObjectPlacer;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.world.storage.matter.TileWrapper;
import art.arcane.volmlib.util.matter.MatterCavern;
import org.jetbrains.annotations.Nullable;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

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
    private final Long2ObjectOpenHashMap<Int2ObjectOpenHashMap<OverlayCell>> overlay;

    ObjectDestinationTransaction(MantleWriter writer, int destinationChunkX, int destinationChunkZ) {
        this.writer = writer;
        this.destinationChunkX = destinationChunkX;
        this.destinationChunkZ = destinationChunkZ;
        this.worldHeight = writer.getMantle().getWorldHeight();
        this.mutations = new ArrayList<>();
        this.overlay = new Long2ObjectOpenHashMap<>();
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
        for (Mutation mutation : plan.mutationsFor(destinationChunkX, destinationChunkZ)) {
            switch (mutation) {
                case SetMutation set -> setData(set.key().x(), set.key().y(), set.key().z(), set.value(), set);
                case CustomBlockMutation custom -> set(custom.key().x(), custom.key().y(), custom.key().z(), custom.state(), custom);
            }
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
                if (mutation.key().type() == NativeBlockState.class) {
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
    public void set(int x, int y, int z, NativeBlockState state) {
        set(x, y, z, state, null);
    }

    private void set(int x, int y, int z, NativeBlockState state, CustomBlockMutation replayed) {
        if (state == null) {
            return;
        }
        String placementKey = state.deferredPlacementKey();
        NativeBlockState baseState = state.placementBaseState();
        if (state.isCustom() && placementKey != null && baseState != null) {
            if (!canSetBlock(x, y, z)) {
                return;
            }
            Identifier identifier = Identifier.fromString(placementKey);
            OverlayCell cell = writableCell(x, y, z);
            cell.block = baseState;
            cell.identifier = identifier;
            mutations.add(replayed != null
                    ? replayed : new CustomBlockMutation(new DataKey(x, y, z, NativeBlockState.class), state));
            return;
        }
        setData(x, y, z, state);
    }

    @Override
    public NativeBlockState get(int x, int y, int z) {
        OverlayCell cell = overlayCell(x, y, z);
        Object value = cell == null ? null : cell.block;
        if (value == CLEARED) {
            return EngineMantle.AIR.get();
        }
        return value instanceof NativeBlockState state
                ? state
                : writer.getPrerequisiteBlock(x, y, z);
    }

    @Override
    public boolean isPreventingDecay() {
        return writer.isPreventingDecay();
    }

    @Override
    public boolean isCarved(int x, int y, int z) {
        OverlayCell cell = overlayCell(x, y, z);
        if (cell == null) {
            return writer.isPrerequisiteCarved(x, y, z);
        }
        Object block = cell.block;
        if (block instanceof NativeBlockState state && !state.isAir() && !state.isFluid()) {
            return false;
        }
        Object hydrologyValue = cell.hydrology;
        if (hydrologyValue instanceof HydrologyCaveCell hydrology) {
            return hydrology.carves();
        }
        Object cavern = cell.cavern;
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
        setData(x, y, z, data, null);
    }

    private void setData(int x, int y, int z, Object data, SetMutation replayed) {
        if (data == null || y < 0 || y >= worldHeight) {
            return;
        }
        if (data instanceof NativeBlockState && !canSetBlock(x, y, z)) {
            return;
        }
        if (data instanceof MatterCavern && hasProtectedHydrology(x, y, z)) {
            return;
        }
        Class<?> type = data instanceof NativeBlockState ? NativeBlockState.class : data.getClass();
        OverlayCell cell = writableCell(x, y, z);
        if (data instanceof NativeBlockState) {
            cell.block = data;
            cell.identifier = CLEARED;
        } else {
            cell.put(type, data);
        }
        mutations.add(replayed != null
                ? replayed : new SetMutation(new DataKey(x, y, z, type), data));
    }

    @Override
    public <T> @Nullable T getDataIfPresent(int x, int y, int z, Class<T> type) {
        OverlayCell cell = overlayCell(x, y, z);
        Object value = cell == null ? null : cell.get(type);
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
        Int2ObjectOpenHashMap<OverlayCell> column = overlay.get(columnKey(x, z));
        if (column == null) {
            return carved;
        }
        for (Int2ObjectMap.Entry<OverlayCell> entry : column.int2ObjectEntrySet()) {
            int y = entry.getIntKey();
            if (y >= cappedHeight) {
                continue;
            }
            OverlayCell cell = entry.getValue();
            if (cell.block instanceof NativeBlockState state && !state.isAir() && !state.isFluid()) {
                carved[y] = 0;
            } else if (cell.hydrology instanceof HydrologyCaveCell hydrology) {
                carved[y] = hydrology.carves() ? (byte) 1 : 0;
            } else if (cell.cavern != null) {
                carved[y] = cell.cavern == CLEARED ? (byte) 0 : (byte) 1;
            }
        }
        return carved;
    }

    @Override
    public Engine getEngine() {
        return writer.getEngine();
    }

    private static long columnKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private OverlayCell overlayCell(int x, int y, int z) {
        Int2ObjectOpenHashMap<OverlayCell> column = overlay.get(columnKey(x, z));
        return column == null ? null : column.get(y);
    }

    private OverlayCell writableCell(int x, int y, int z) {
        long coordinate = columnKey(x, z);
        Int2ObjectOpenHashMap<OverlayCell> column = overlay.get(coordinate);
        if (column == null) {
            column = new Int2ObjectOpenHashMap<>(4);
            overlay.put(coordinate, column);
        }
        OverlayCell cell = column.get(y);
        if (cell == null) {
            cell = new OverlayCell();
            column.put(y, cell);
        }
        return cell;
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
                second.getKey().type() == NativeBlockState.class,
                first.getKey().type() == NativeBlockState.class
        ));
        for (Map.Entry<DataKey, Object> entry : entries) {
            DataKey key = entry.getKey();
            try {
                if (key.type() == NativeBlockState.class
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

    private static final class OverlayCell {
        private Object block;
        private Object hydrology;
        private Object cavern;
        private Object identifier;
        private Object string;
        private Object treeMaterial;
        private Map<Class<?>, Object> other;

        private Object get(Class<?> type) {
            if (type == NativeBlockState.class) {
                return block;
            }
            if (type == HydrologyCaveCell.class) {
                return hydrology;
            }
            if (type == MatterCavern.class) {
                return cavern;
            }
            if (type == Identifier.class) {
                return identifier;
            }
            if (type == String.class) {
                return string;
            }
            if (type == TreeBlockMaterial.class) {
                return treeMaterial;
            }
            return other == null ? null : other.get(type);
        }

        private void put(Class<?> type, Object value) {
            if (type == HydrologyCaveCell.class) {
                hydrology = value;
            } else if (type == MatterCavern.class) {
                cavern = value;
            } else if (type == Identifier.class) {
                identifier = value;
            } else if (type == String.class) {
                string = value;
            } else if (type == TreeBlockMaterial.class) {
                treeMaterial = value;
            } else {
                if (other == null) {
                    other = new HashMap<>();
                }
                other.put(type, value);
            }
        }
    }

    record DataKey(int x, int y, int z, Class<?> type) {
    }

    sealed interface Mutation permits SetMutation, CustomBlockMutation {
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

    record CustomBlockMutation(DataKey key, NativeBlockState state) implements Mutation {
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
