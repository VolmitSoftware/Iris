package art.arcane.iris.engine.hydrology.cave;

import java.util.Arrays;

final class RecordingCaveVoxelView implements CaveVoxelView {
    static final byte IN_WORLD = 0;
    static final byte VOXEL = 1;
    static final byte OPEN_TO_SURFACE = 2;
    static final byte ABOVE_TERRAIN_SURFACE = 3;
    private final CaveVoxelView delegate;
    private final boolean observeColumnsOnly;
    private CavePosition[] positions;
    private byte[] operations;
    private byte[] values;
    private CavePositionIndex[] observed;
    private int size;

    RecordingCaveVoxelView(CaveVoxelView delegate, boolean observeColumnsOnly) {
        this.delegate = delegate;
        this.observeColumnsOnly = observeColumnsOnly;
        this.positions = new CavePosition[1024];
        this.operations = observeColumnsOnly ? new byte[0] : new byte[1024];
        this.values = observeColumnsOnly ? new byte[0] : new byte[1024];
        this.observed = observeColumnsOnly
                ? new CavePositionIndex[]{new CavePositionIndex()}
                : new CavePositionIndex[]{
                        new CavePositionIndex(),
                        new CavePositionIndex(),
                        new CavePositionIndex(),
                        new CavePositionIndex()
                };
    }

    @Override
    public boolean isInWorld(CavePosition position) {
        boolean result = delegate.isInWorld(position);
        add(position, IN_WORLD, result ? 1 : 0);
        return result;
    }

    @Override
    public CaveVoxel voxelAt(CavePosition position) {
        CaveVoxel result = delegate.voxelAt(position);
        add(position, VOXEL, result.ordinal());
        return result;
    }

    @Override
    public boolean isOpenToSurface(CavePosition position) {
        boolean result = delegate.isOpenToSurface(position);
        add(position, OPEN_TO_SURFACE, result ? 1 : 0);
        return result;
    }

    @Override
    public boolean isAboveTerrainSurface(CavePosition position) {
        boolean result = delegate.isAboveTerrainSurface(position);
        add(position, ABOVE_TERRAIN_SURFACE, result ? 1 : 0);
        return result;
    }

    private void add(CavePosition position, byte operation, int value) {
        int observedIndex = observeColumnsOnly ? 0 : operation;
        int observedY = observeColumnsOnly ? 0 : position.y();
        if (!observed[observedIndex].add(position.x(), observedY, position.z())) {
            return;
        }
        if (size == positions.length) {
            int expandedSize = Math.multiplyExact(size, 2);
            positions = Arrays.copyOf(positions, expandedSize);
            if (!observeColumnsOnly) {
                operations = Arrays.copyOf(operations, expandedSize);
                values = Arrays.copyOf(values, expandedSize);
            }
        }
        positions[size] = position;
        if (!observeColumnsOnly) {
            operations[size] = operation;
            values[size] = (byte) value;
        }
        size++;
    }

    CaveViewObservations snapshot() {
        observed = null;
        if (positions.length == 1024) {
            return new CaveViewObservations(
                    Arrays.copyOf(positions, size),
                    observeColumnsOnly ? operations : Arrays.copyOf(operations, size),
                    observeColumnsOnly ? values : Arrays.copyOf(values, size),
                    size
            );
        }
        return new CaveViewObservations(positions, operations, values, size);
    }
}
