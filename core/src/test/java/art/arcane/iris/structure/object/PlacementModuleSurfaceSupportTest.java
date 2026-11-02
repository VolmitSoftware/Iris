package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.stream.ProvisionalSampling;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PlacementModuleSurfaceSupportTest {
    @Rule
    public final PlatformBinding platform = PlatformBinding.mockPlatform();

    private static final int LAND_ABOVE_SEA = 17;
    private static final int WATER_RADIUS = 8;

    private IrisData data;
    private Engine engine;
    private IrisComplex complex;
    private int dimensionFluidHeight;
    private int waterFloor;
    private int waterHead;
    private boolean provisionalWater;

    @Before
    public void bindEngine() {
        IrisDimension dimension = new IrisDimension();
        dimensionFluidHeight = dimension.getFluidHeight();
        @SuppressWarnings("unchecked")
        ProceduralStream<Double> waterHeads = mock(ProceduralStream.class);
        when(waterHeads.get(anyDouble(), anyDouble())).thenAnswer(invocation -> {
            if (provisionalWater) {
                ProvisionalSampling.mark();
            }
            return (double) headAt((int) Math.floor((double) invocation.getArgument(0)), (int) Math.floor((double) invocation.getArgument(1)));
        });
        complex = mock(IrisComplex.class);
        when(complex.getWaterHeadStream()).thenReturn(waterHeads);
        when(complex.hasWaterOrShore(anyInt(), anyInt())).thenAnswer(invocation -> {
            if (provisionalWater) {
                ProvisionalSampling.mark();
            }
            return isWater(invocation.getArgument(0), invocation.getArgument(1));
        });
        engine = mock(Engine.class);
        when(engine.getHeight()).thenReturn(256);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getDimension()).thenReturn(dimension);
        data = mock(IrisData.class);
        when(data.getEngine()).thenReturn(engine);
    }

    @Test
    public void underwaterObjectsPlaceInPonds() {
        water(7, 13);
        WaterPlacer placer = new WaterPlacer();
        IrisObjectPlacement placement = placement();
        placement.setUnderwater(true);

        assertTrue(place(placer, placement) >= 0);
        assertFalse(placer.writes().isEmpty());
        assertEquals(waterFloor, placer.lowestWrite());
        assertTrue(placer.highestWrite() < waterHead);
    }

    @Test
    public void underwaterObjectsNeedWaterAboveTheirBase() {
        water(12, 13);
        WaterPlacer placer = new WaterPlacer();
        IrisObjectPlacement placement = placement();
        placement.setUnderwater(true);

        assertEquals(-1, place(placer, placement));
        assertTrue(placer.writes().isEmpty());
    }

    @Test
    public void onwaterObjectsFloatOnRivers() {
        water(9, 13);
        WaterPlacer placer = new WaterPlacer();
        IrisObjectPlacement placement = placement();
        placement.setOnwater(true);

        assertTrue(place(placer, placement) >= 0);
        assertEquals(waterHead, placer.lowestWrite());
    }

    @Test
    public void onwaterObjectsSkipDryColumns() {
        WaterPlacer placer = new WaterPlacer();
        IrisObjectPlacement placement = placement();
        placement.setOnwater(true);

        assertEquals(-1, place(placer, placement));
        assertTrue(placer.writes().isEmpty());
    }

    @Test
    public void forcePlaceObjectsPlaceInRiverWater() {
        water(9, 13);
        WaterPlacer placer = new WaterPlacer();
        IrisObjectPlacement placement = placement();
        placement.setForcePlace(true);

        assertTrue(place(placer, placement) >= 0);
        assertEquals(waterHead, placer.lowestWrite());
    }

    @Test
    public void landObjectsStayOutOfRiverWater() {
        water(9, 13);
        WaterPlacer placer = new WaterPlacer();

        assertEquals(-1, place(placer, placement()));
        assertTrue(placer.writes().isEmpty());
    }

    @Test
    public void waterObjectsNeverPlaceOnProvisionalHydrology() {
        water(7, 13);
        provisionalWater = true;
        IrisObjectPlacement underwater = placement();
        underwater.setUnderwater(true);
        IrisObjectPlacement onwater = placement();
        onwater.setOnwater(true);
        IrisObjectPlacement forced = placement();
        forced.setForcePlace(true);

        for (IrisObjectPlacement placement : List.of(underwater, onwater, forced)) {
            WaterPlacer placer = new WaterPlacer();

            IllegalStateException error = assertThrows(IllegalStateException.class, () -> place(placer, placement));

            assertTrue(error.getMessage().contains("provisional"));
            assertTrue(placer.writes().isEmpty());
        }
    }

    @Test
    public void landObjectsNeverPlaceOnProvisionalHydrology() {
        provisionalWater = true;
        WaterPlacer placer = new WaterPlacer();

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> place(placer, placement()));

        assertTrue(error.getMessage().contains("provisional"));
        assertTrue(placer.writes().isEmpty());
    }

    private void water(int floorAboveSea, int headAboveSea) {
        waterFloor = dimensionFluidHeight + floorAboveSea;
        waterHead = dimensionFluidHeight + headAboveSea;
    }

    private boolean isWater(int x, int z) {
        return waterHead > waterFloor && Math.abs(x) <= WATER_RADIUS && Math.abs(z) <= WATER_RADIUS;
    }

    private int headAt(int x, int z) {
        return isWater(x, z) ? waterHead : dimensionFluidHeight;
    }

    private int floorAt(int x, int z) {
        return isWater(x, z) ? waterFloor : dimensionFluidHeight + LAND_ABOVE_SEA;
    }

    private int place(WaterPlacer placer, IrisObjectPlacement placement) {
        return object().place(0, -1, 0, placer, placement, new RNG(1234L), data);
    }

    private static IrisObjectPlacement placement() {
        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setMode(ObjectPlaceMode.CENTER_HEIGHT);
        return placement;
    }

    private static IrisObject object() {
        NativeBlockState solid = mock(NativeBlockState.class);
        when(solid.isSolid()).thenReturn(true);
        when(solid.key()).thenReturn("minecraft:stone");
        when(solid.materialKey()).thenReturn("minecraft:stone");
        IrisObject object = new IrisObject(3, 3, 3);
        object.setUnsigned(1, 0, 1, solid);
        object.setUnsigned(1, 1, 1, solid);
        return object;
    }

    private final class WaterPlacer implements IObjectPlacer {
        private final List<Integer> writes = new ArrayList<>();

        private List<Integer> writes() {
            return writes;
        }

        private int lowestWrite() {
            int lowest = Integer.MAX_VALUE;
            for (int y : writes) {
                lowest = Math.min(lowest, y);
            }
            return lowest;
        }

        private int highestWrite() {
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
            return dimensionFluidHeight;
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
