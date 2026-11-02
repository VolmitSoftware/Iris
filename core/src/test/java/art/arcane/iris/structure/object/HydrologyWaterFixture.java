package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.stream.ProvisionalSampling;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.stream.ProceduralStream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class HydrologyWaterFixture {
    public static final int LAND_ABOVE_SEA = 17;
    public static final int WATER_RADIUS = 8;

    private final Map<Long, Column> columns = new HashMap<>();
    private final IrisData data;
    private final Engine engine;
    private final IrisComplex complex;
    private final int seaLevel;
    private Column water;
    private boolean provisional;

    public HydrologyWaterFixture() {
        IrisDimension dimension = new IrisDimension();
        seaLevel = dimension.getFluidHeight();
        @SuppressWarnings("unchecked")
        ProceduralStream<Double> heads = mock(ProceduralStream.class);
        when(heads.get(anyDouble(), anyDouble())).thenAnswer(invocation -> (double) headAt(block(invocation.getArgument(0)), block(invocation.getArgument(1))));
        when(heads.getDouble(anyDouble(), anyDouble())).thenAnswer(invocation -> (double) headAt(block(invocation.getArgument(0)), block(invocation.getArgument(1))));
        @SuppressWarnings("unchecked")
        ProceduralStream<WaterRole> roles = mock(ProceduralStream.class);
        when(roles.get(anyDouble(), anyDouble())).thenAnswer(invocation -> roleAt(block(invocation.getArgument(0)), block(invocation.getArgument(1))));
        complex = mock(IrisComplex.class);
        when(complex.getWaterHeadStream()).thenReturn(heads);
        when(complex.getWaterRoleStream()).thenReturn(roles);
        when(complex.hasWaterOrShore(anyInt(), anyInt())).thenAnswer(invocation -> waterOrShore(roleAt(invocation.getArgument(0), invocation.getArgument(1))));
        engine = mock(Engine.class);
        when(engine.getHeight()).thenReturn(256);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getDimension()).thenReturn(dimension);
        data = mock(IrisData.class);
        when(data.getEngine()).thenReturn(engine);
    }

    public IrisData data() {
        return data;
    }

    public IrisComplex complex() {
        return complex;
    }

    public int seaLevel() {
        return seaLevel;
    }

    public void water(WaterRole role, int floorAboveSea, int headAboveSea) {
        water = new Column(role, seaLevel + floorAboveSea, seaLevel + headAboveSea);
    }

    public void column(int x, int z, WaterRole role, int floorAboveSea, int headAboveSea) {
        columns.put(key(x, z), new Column(role, seaLevel + floorAboveSea, seaLevel + headAboveSea));
    }

    public void provisional() {
        provisional = true;
    }

    public int floorAt(int x, int z) {
        return columnAt(x, z).floor();
    }

    public int headAt(int x, int z) {
        Column column = columnAt(x, z);
        return column.role().wet() ? column.head() : seaLevel;
    }

    public Placer placer() {
        return new Placer();
    }

    public static IrisObject pillar() {
        IrisObject object = new IrisObject(3, 3, 3);
        object.setUnsigned(1, 0, 1, solid());
        object.setUnsigned(1, 1, 1, solid());
        return object;
    }

    public static IrisObject raft() {
        IrisObject object = new IrisObject(3, 3, 3);
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                object.setUnsigned(x, 0, z, solid());
            }
        }
        object.setUnsigned(1, 1, 1, solid());
        return object;
    }

    private static NativeBlockState solid() {
        NativeBlockState solid = mock(NativeBlockState.class);
        when(solid.isSolid()).thenReturn(true);
        when(solid.key()).thenReturn("minecraft:stone");
        when(solid.materialKey()).thenReturn("minecraft:stone");
        return solid;
    }

    private WaterRole roleAt(int x, int z) {
        return columnAt(x, z).role();
    }

    private Column columnAt(int x, int z) {
        if (provisional) {
            ProvisionalSampling.mark();
        }
        Column override = columns.get(key(x, z));
        if (override != null) {
            return override;
        }
        if (water != null && Math.abs(x) <= WATER_RADIUS && Math.abs(z) <= WATER_RADIUS) {
            return water;
        }
        return new Column(WaterRole.NONE, seaLevel + LAND_ABOVE_SEA, seaLevel);
    }

    private static boolean waterOrShore(WaterRole role) {
        return switch (role) {
            case CHANNEL, POND, MOUTH, CASCADE, WATERFALL, INLET, SHORE, POND_SHORE, DAM -> true;
            case NONE, BANK -> false;
        };
    }

    private static int block(Object coordinate) {
        return (int) Math.floor(((Number) coordinate).doubleValue());
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private record Column(WaterRole role, int floor, int head) {
    }

    public final class Placer implements IObjectPlacer {
        private final List<Integer> writes = new ArrayList<>();

        public List<Integer> writes() {
            return writes;
        }

        public int lowestWrite() {
            int lowest = Integer.MAX_VALUE;
            for (int y : writes) {
                lowest = Math.min(lowest, y);
            }
            return lowest;
        }

        public int highestWrite() {
            int highest = Integer.MIN_VALUE;
            for (int y : writes) {
                highest = Math.max(highest, y);
            }
            return highest;
        }

        @Override
        public int getHighest(int x, int z, IrisData data) {
            return getHighest(x, z, data, false);
        }

        @Override
        public int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
            int floor = floorAt(x, z);
            return ignoreFluid ? floor : Math.max(floor, getFluidHeight(x, z));
        }

        @Override
        public void set(int x, int y, int z, NativeBlockState state) {
            writes.add(y);
        }

        @Override
        public NativeBlockState get(int x, int y, int z) {
            return null;
        }

        @Override
        public boolean isPreventingDecay() {
            return false;
        }

        @Override
        public boolean isCarved(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean isSolid(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean isUnderwater(int x, int z) {
            return getHighest(x, z, data, true) < getFluidHeight(x, z);
        }

        @Override
        public int getFluidHeight() {
            return seaLevel;
        }

        @Override
        public boolean isDebugSmartBore() {
            return false;
        }

        @Override
        public void setTile(int x, int y, int z, TileData tile) {
        }

        @Override
        public <T> void setData(int x, int y, int z, T value) {
        }

        @Override
        public <T> T getData(int x, int y, int z, Class<T> type) {
            return null;
        }

        @Override
        public Engine getEngine() {
            return engine;
        }
    }
}
