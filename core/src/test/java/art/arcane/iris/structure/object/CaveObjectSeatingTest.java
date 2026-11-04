package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.cave.CarvingMode;
import art.arcane.iris.generation.decoration.IrisStiltSettings;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.math.RNG;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CaveObjectSeatingTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private static final int FLOOR = 40;
    private static final int CEILING = 80;

    private IrisData data;
    private NativeBlockState stem;
    private NativeBlockState cap;
    private NativeBlockState ore;
    private NativeBlockState stone;

    @Before
    public void bindPlatform() {
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        Map<String, NativeBlockState> registryStates = new HashMap<>();
        when(registries.block(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return registryStates.computeIfAbsent(key, value -> state(value.toLowerCase(), !value.toLowerCase().contains("air")));
        });
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
        data = mock(IrisData.class);
        stem = state("minecraft:mushroom_stem", true);
        cap = state("minecraft:red_mushroom_block", true);
        ore = state("minecraft:coal_ore", true);
        stone = state("minecraft:stone", true);
    }

    @After
    public void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    public void floatingFloraDropsOntoTheFloorAtItsIntendedSize() {
        IrisObject mushroom = mushroom();
        CavePlacer placer = new CavePlacer();

        int result = mushroom.place(0, FLOOR + 12, 0, placer, organic(ObjectPlaceMode.ORGANIC_STILT), new RNG(2L), data);

        assertTrue(result >= 0);
        assertEquals(1, placer.writesOf(stem).size());
        assertEquals(1, placer.writesOf(cap).size());
        assertEquals(FLOOR + 1, placer.writesOf(stem).getFirst().y());
    }

    @Test
    public void seatedFloraNeverRepeatsOverALowerStep() {
        IrisObject wide = new IrisObject(3, 1, 1);
        wide.setUnsigned(0, 0, 0, cap);
        wide.setUnsigned(1, 0, 0, stem);
        wide.setUnsigned(2, 0, 0, cap);
        CavePlacer placer = new CavePlacer();
        placer.floor(-1, 0, FLOOR - 5);

        int result = wide.place(0, FLOOR + 1, 0, placer, organic(ObjectPlaceMode.ORGANIC_STILT), new RNG(2L), data);

        assertTrue(result >= 0);
        assertEquals(2, placer.writesOf(cap).size());
        assertEquals(1, placer.writesOf(stem).size());
    }

    @Test
    public void floraWithACapRimInTheWallStillDropsToTheFloor() {
        IrisObject wide = new IrisObject(3, 1, 1);
        wide.setUnsigned(0, 0, 0, cap);
        wide.setUnsigned(1, 0, 0, stem);
        wide.setUnsigned(2, 0, 0, cap);
        CavePlacer placer = new CavePlacer();
        placer.floor(1, 0, FLOOR + 20);

        int result = wide.place(0, FLOOR + 6, 0, placer, organic(ObjectPlaceMode.ORGANIC_STILT), new RNG(2L), data);

        assertTrue(result >= 0);
        assertEquals(FLOOR + 1, placer.writesOf(stem).getFirst().y());
    }

    @Test
    public void aCapRimOnALedgeDoesNotHoldTheStemUp() {
        IrisObject wide = new IrisObject(3, 1, 1);
        wide.setUnsigned(0, 0, 0, cap);
        wide.setUnsigned(1, 0, 0, stem);
        wide.setUnsigned(2, 0, 0, cap);
        CavePlacer placer = new CavePlacer();
        placer.floor(1, 0, FLOOR + 5);

        int result = wide.place(0, FLOOR + 6, 0, placer, organic(ObjectPlaceMode.ORGANIC_STILT), new RNG(2L), data);

        assertTrue(result >= 0);
        assertEquals(FLOOR + 1, placer.writesOf(stem).getFirst().y());
    }

    @Test
    public void floraBuriedInEveryColumnStaysWhereItIs() {
        IrisObject wide = new IrisObject(2, 1, 1);
        wide.setUnsigned(0, 0, 0, stem);
        wide.setUnsigned(1, 0, 0, cap);
        CavePlacer placer = new CavePlacer();

        IrisObjectPlacement placement = organic(ObjectPlaceMode.ORGANIC_STILT);
        placement.setForcePlace(true);

        int result = wide.place(0, FLOOR - 3, 0, placer, placement, new RNG(2L), data);

        assertTrue(result >= 0);
        assertEquals(FLOOR - 3, placer.writesOf(stem).getFirst().y());
    }

    @Test
    public void oreInARockBottomIsNeverRepeatedAndTheFillerIsTheRock() {
        IrisObject rock = new IrisObject(4, 1, 1);
        rock.setUnsigned(0, 0, 0, stone);
        rock.setUnsigned(1, 0, 0, stone);
        rock.setUnsigned(2, 0, 0, ore);
        rock.setUnsigned(3, 0, 0, stone);
        CavePlacer placer = new CavePlacer();
        placer.floor(0, 0, FLOOR - 3);

        int result = rock.place(0, FLOOR + 1, 0, placer, organic(ObjectPlaceMode.ORGANIC_STILT), new RNG(2L), data);

        assertTrue(result >= 0);
        assertEquals(1, placer.writesOf(ore).size());
        assertTrue(placer.writesOf(stone).size() > 3);
        assertSame(stone, placer.get(0, FLOOR - 2, 0));
    }

    @Test
    public void rockFillerClosesAReachableGapCompletelyEvenWithRaggedSettings() {
        IrisObject rock = new IrisObject(2, 1, 1);
        rock.setUnsigned(0, 0, 0, stone);
        rock.setUnsigned(1, 0, 0, stone);
        CavePlacer placer = new CavePlacer();
        placer.floor(0, 0, FLOOR - 6);
        IrisObjectPlacement placement = organic(ObjectPlaceMode.ORGANIC_STILT);
        placement.setStiltSettings(new IrisStiltSettings().setOrganicJitter(3).setOrganicScratch(0.9));

        rock.place(0, FLOOR + 1, 0, placer, placement, new RNG(2L), data);

        for (int y = FLOOR - 5; y <= FLOOR + 1; y++) {
            assertSame(stone, placer.get(0, y, 0));
        }
    }

    @Test
    public void aDeepDropGetsAShortRaggedRootWithNoDetachedCells() {
        IrisObject rock = new IrisObject(2, 1, 1);
        rock.setUnsigned(0, 0, 0, stone);
        rock.setUnsigned(1, 0, 0, stone);
        for (int x = -40; x < 40; x++) {
            CavePlacer placer = new CavePlacer();
            placer.floor(x - 1, 0, FLOOR);
            placer.floor(x, 0, FLOOR - 30);
            IrisObjectPlacement placement = organic(ObjectPlaceMode.ORGANIC_STILT);
            placement.setStiltSettings(new IrisStiltSettings().setOrganicJitter(3).setOrganicScratch(0.9));

            rock.place(x, FLOOR + 1, 0, placer, placement, new RNG(2L), data);

            int finalX = x;
            List<BlockWrite> column = placer.writesOf(stone).stream().filter(write -> write.x() == finalX).toList();
            int lowest = column.stream().mapToInt(BlockWrite::y).min().orElseThrow();
            assertTrue(lowest >= FLOOR + 1 - 8);
            assertEquals(FLOOR + 1 - lowest + 1, column.size());
        }
    }

    @Test
    public void rootsStopAtStandingWater() {
        IrisObject rock = new IrisObject(2, 1, 1);
        rock.setUnsigned(0, 0, 0, stone);
        rock.setUnsigned(1, 0, 0, stone);
        CavePlacer placer = new CavePlacer();
        placer.floor(0, 0, FLOOR - 30);
        placer.fluidBelow = FLOOR - 2;

        rock.place(0, FLOOR + 1, 0, placer, organic(ObjectPlaceMode.ORGANIC_STILT), new RNG(2L), data);

        assertTrue(placer.writesOf(stone).stream().allMatch(write -> write.y() > FLOOR - 2));
    }

    @Test
    public void anObjectWithNoFloorInReachIsRejectedInsteadOfFloating() {
        IrisObject mushroom = mushroom();
        CavePlacer placer = new CavePlacer();
        placer.floor(0, 0, 0);

        int result = mushroom.place(0, CEILING - 2, 0, placer, organic(ObjectPlaceMode.ORGANIC_STILT), new RNG(2L), data);

        assertEquals(-1, result);
    }

    @Test
    public void standingWaterIsNotAFloorForADryObject() {
        IrisObject mushroom = mushroom();
        CavePlacer placer = new CavePlacer();
        placer.fluidBelow = FLOOR + 5;

        int result = mushroom.place(0, FLOOR + 12, 0, placer, organic(ObjectPlaceMode.ORGANIC_STILT), new RNG(2L), data);

        assertEquals(-1, result);
    }

    @Test
    public void ceilingObjectsRiseToTheRoofAndNeverRepeat() {
        IrisObject hanging = new IrisObject(1, 2, 1);
        hanging.setUnsigned(0, 0, 0, stem);
        hanging.setUnsigned(0, 1, 0, cap);
        CavePlacer placer = new CavePlacer();

        int result = hanging.place(0, CEILING - 8, 0, placer, organic(ObjectPlaceMode.CEILING_HANG), new RNG(2L), data);

        assertTrue(result >= 0);
        assertEquals(1, placer.writesOf(stem).size());
        assertEquals(1, placer.writesOf(cap).size());
        int top = placer.writes.stream().mapToInt(BlockWrite::y).max().orElseThrow();
        assertEquals(CEILING - 1, top);
    }

    @Test
    public void anObjectAlreadyOnTheFloorStaysPut() {
        IrisObject mushroom = mushroom();
        CavePlacer placer = new CavePlacer();

        mushroom.place(0, FLOOR + 1 + mushroom.getCenter().getBlockY(), 0, placer, organic(ObjectPlaceMode.ORGANIC_STILT), new RNG(2L), data);

        assertEquals(FLOOR + 1, placer.writesOf(stem).getFirst().y());
    }

    @Test
    public void placersWithoutACaveAnswerKeepTheirStilt() {
        IObjectPlacer plain = mock(IObjectPlacer.class, CALLS_REAL_METHODS);
        assertNull(plain.caveCell(0, FLOOR, 0));
    }

    private IrisObject mushroom() {
        IrisObject mushroom = new IrisObject(1, 2, 1);
        mushroom.setUnsigned(0, 0, 0, stem);
        mushroom.setUnsigned(0, 1, 0, cap);
        return mushroom;
    }

    private static IrisObjectPlacement organic(ObjectPlaceMode mode) {
        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setMode(mode);
        placement.setRequireSurfaceSupport(false);
        placement.setCarvingSupport(CarvingMode.CARVING_ONLY);
        placement.setRotation(IrisObjectRotation.of(0, 0, 0));
        placement.setStiltSettings(new IrisStiltSettings().setOrganicJitter(0).setOrganicScratch(0));
        return placement;
    }

    private static NativeBlockState state(String key, boolean solid) {
        NativeBlockState state = mock(NativeBlockState.class);
        when(state.key()).thenReturn(key);
        when(state.materialKey()).thenReturn(key);
        when(state.isSolid()).thenReturn(solid);
        when(state.isOccluding()).thenReturn(solid);
        return state;
    }

    private record BlockWrite(int x, int y, int z, NativeBlockState state) {
    }

    private static final class CavePlacer implements IObjectPlacer {
        final List<BlockWrite> writes = new ArrayList<>();
        final Map<String, NativeBlockState> world = new HashMap<>();
        final Map<String, Integer> floors = new HashMap<>();
        int fluidBelow = Integer.MIN_VALUE;

        void floor(int x, int z, int y) {
            floors.put(x + ":" + z, y);
        }

        List<BlockWrite> writesOf(NativeBlockState state) {
            return writes.stream().filter(write -> write.state() == state).toList();
        }

        @Override
        public CaveCell caveCell(int x, int y, int z) {
            if (y <= floors.getOrDefault(x + ":" + z, FLOOR) || y >= CEILING) {
                return CaveCell.GROUND;
            }
            return y <= fluidBelow ? CaveCell.FLUID : CaveCell.OPEN;
        }

        @Override
        public int getHighest(int x, int z, IrisData data) {
            return 120;
        }

        @Override
        public int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
            return 120;
        }

        @Override
        public void set(int x, int y, int z, NativeBlockState state) {
            writes.add(new BlockWrite(x, y, z, state));
            world.put(x + ":" + y + ":" + z, state);
        }

        @Override
        public NativeBlockState get(int x, int y, int z) {
            return world.get(x + ":" + y + ":" + z);
        }

        @Override
        public boolean isPreventingDecay() {
            return false;
        }

        @Override
        public boolean isCarved(int x, int y, int z) {
            return caveCell(x, y, z) != CaveCell.GROUND;
        }

        @Override
        public boolean isSolid(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean isUnderwater(int x, int z) {
            return false;
        }

        @Override
        public int getFluidHeight() {
            return 0;
        }

        @Override
        public boolean isDebugSmartBore() {
            return false;
        }

        @Override
        public void setTile(int x, int y, int z, TileData tile) {
        }

        @Override
        public <T> void setData(int x, int y, int z, T data) {
        }

        @Override
        public <T> T getData(int x, int y, int z, Class<T> type) {
            return null;
        }

        @Override
        public Engine getEngine() {
            return null;
        }
    }
}
