package art.arcane.iris.generation.hydrology.underground;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.hydrology.config.IrisCavePools;
import art.arcane.iris.generation.hydrology.config.IrisFluidProfile;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.mantle.MantleWriter;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class CavePoolFillerTest {
    private static final long SEED = 1337L;
    private static final int FLOOR = 50;

    @Test
    public void globalDensityThinsCandidateColumnsBeforeSamplingCaves() {
        IrisHydrology config = new IrisHydrology().setDensity(0.5D);
        config.getUnderground().getCavePools().setDensity(8D);
        CavePoolFiller filler = new CavePoolFiller(HydrologySettings.compile(config, SEED, 63, 0, 384));
        CarvedTerrain carved = mock(CarvedTerrain.class);
        MantleWriter writer = mock(MantleWriter.class);
        Set<Long> columns = new HashSet<>();
        when(carved.surface(anyInt(), anyInt())).thenAnswer(invocation -> {
            int x = invocation.getArgument(0);
            int z = invocation.getArgument(1);
            columns.add(key(x, 0, z));
            return 0;
        });
        for (int chunkX = 0; chunkX < 1024; chunkX++) {
            filler.fill(chunkX, 0, carved, writer, SEED);
        }
        assertTrue("half density samples approximately half the candidate columns", columns.size() > 3600 && columns.size() < 4500);
        Set<Long> repeated = new HashSet<>(columns);
        columns.clear();
        for (int chunkX = 1023; chunkX >= 0; chunkX--) {
            filler.fill(chunkX, 0, carved, writer, SEED);
        }
        assertEquals(repeated, columns);
        verifyNoInteractions(writer);
    }

    @Test
    public void zeroGlobalDensityDoesNotReadCaves() {
        IrisHydrology config = new IrisHydrology().setDensity(0D);
        config.getUnderground().getCavePools().setDensity(8D);
        CavePoolFiller filler = new CavePoolFiller(HydrologySettings.compile(config, SEED, 63, 0, 384));
        CarvedTerrain carved = mock(CarvedTerrain.class);
        MantleWriter writer = mock(MantleWriter.class);
        filler.fill(0, 0, carved, writer, SEED);
        verifyNoInteractions(carved, writer);
    }

    @Test
    public void poolLevelIsHighestContainedLevelMinusFreeboard() {
        VoxelWorld world = roomWithSill();

        Map<Long, HydrologyCaveCell> brim = fill(settings(pools -> pools.setFreeboard(new KList<>(0, 0))), world, 0, 0);
        assertEquals(roomCells(FLOOR, 52), wetCells(brim, "water"));
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                assertGuard(brim, x, FLOOR - 1, z);
            }
        }
        for (int y = FLOOR; y <= 52; y++) {
            for (int offset = 0; offset < 16; offset++) {
                assertGuard(brim, -1, y, offset);
                assertGuard(brim, 16, y, offset);
                assertGuard(brim, offset, y, -1);
                assertGuard(brim, offset, y, 16);
            }
        }
        assertFalse(brim.containsKey(key(16, 53, 8)));

        Map<Long, HydrologyCaveCell> lowered = fill(settings(pools -> pools.setFreeboard(new KList<>(1, 1))), world, 0, 0);
        assertEquals(roomCells(FLOOR, 51), wetCells(lowered, "water"));
    }

    @Test
    public void leakingDepressionGetsNoPool() {
        VoxelWorld spill = room(0, 15, 0, 15, FLOOR, 57);
        spill.carve(16, 40, 8, 8, FLOOR, 57);
        assertTrue(fill(settings(pools -> {
        }), spill, 0, 0).isEmpty());

        VoxelWorld drain = room(0, 15, 0, 15, FLOOR, 57);
        drain.carve(8, 8, 8, 8, 40, FLOOR - 1);
        drain.carve(4, 40, 4, 12, 40, 44);
        assertTrue(fill(settings(pools -> {
        }), drain, 0, 0).isEmpty());
    }

    @Test
    public void poolNeverOpensToSky() {
        VoxelWorld shaft = roomWithSill();
        shaft.carve(8, 8, 8, 8, 58, VoxelWorld.SURFACE);
        assertTrue(fill(settings(pools -> {
        }), shaft, 0, 0).isEmpty());

        VoxelWorld shallow = room(0, 15, 0, 15, FLOOR, 53);
        shallow.surface(55);
        Map<Long, HydrologyCaveCell> written = fill(settings(pools -> pools.setFreeboard(new KList<>(0, 0))), shallow, 0, 0);
        assertEquals(roomCells(FLOOR, 52), wetCells(written, "water"));
        for (long cell : wetCells(written, "water")) {
            assertTrue(y(cell) <= 55 - CavePoolFiller.SURFACE_DEPTH);
        }
    }

    @Test
    public void chunkOrderIndependent() {
        VoxelWorld world = room(8, 23, 0, 15, FLOOR, 57);
        world.carve(-20, 7, 8, 8, 52, 57);
        world.carve(-20, -5, 0, 15, 52, 57);
        world.carve(28, 40, 20, 30, FLOOR, 55);
        world.carve(24, 27, 25, 25, 53, 55);
        world.policyFluid(x -> x >= 28 ? "lava" : null);
        HydrologySettings settings = settings(pools -> pools.setDensity(8D).setFreeboard(new KList<>(0, 0)));

        Map<Long, HydrologyCaveCell> forward = new HashMap<>();
        Map<Long, HydrologyCaveCell> reverse = new HashMap<>();
        MantleWriter forwardWriter = writer(forward);
        MantleWriter reverseWriter = writer(reverse);
        for (int index = 0; index < 9; index++) {
            new CavePoolFiller(settings).fill(index % 3 - 1, index / 3 - 1, world, forwardWriter, SEED);
        }
        for (int index = 8; index >= 0; index--) {
            new CavePoolFiller(settings).fill(index % 3 - 1, index / 3 - 1, world, reverseWriter, SEED);
        }

        assertFalse(forward.isEmpty());
        assertEquals(forward, reverse);
        Set<Long> crossing = new HashSet<>();
        for (long cell : wetCells(forward, null)) {
            if (x(cell) >= 16 && x(cell) <= 23) {
                crossing.add(cell);
            }
        }
        assertFalse("a pool crosses the chunk edge at x=16", crossing.isEmpty());
        for (long cell : crossing) {
            assertEquals("water", forward.get(cell).fluidProfileKey());
        }
        assertFalse("the lava room writes into a chunk that was never filled", wetCells(forward, "lava").isEmpty());
        for (long cell : wetCells(forward, "lava")) {
            assertTrue(x(cell) >= 28);
        }
        assertNoMixedBodies(forward);
    }

    @Test
    public void depressionsSpanningTwoCaveFluidsGetNoPool() {
        VoxelWorld mixed = roomWithSill();
        mixed.policyFluid(x -> x >= 8 ? "lava" : null);
        assertTrue(fill(settings(pools -> pools.setDensity(8D)), mixed, 0, 0).isEmpty());
    }

    @Test
    public void candidatesSettleToTheLowestReachableFloorBeforeMeasuring() {
        VoxelWorld shelf = room(0, 15, 0, 15, 60, 63);
        shelf.carve(16, 18, 0, 15, FLOOR, 63);
        Map<Long, HydrologyCaveCell> written = fill(settings(pools -> pools.setDensity(8D).setFreeboard(new KList<>(0, 0))), shelf, 0, 0);

        Set<Long> expected = new HashSet<>();
        for (int x = 16; x <= 18; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = FLOOR; y <= FLOOR + 3; y++) {
                    expected.add(key(x, y, z));
                }
            }
        }
        assertEquals("shelf candidates drain into the pit and pool there", expected, wetCells(written, "water"));
    }

    @Test
    public void foreignCellsOverThePoolSurfaceLowerTheLevelAndRockOverItIsGuarded() {
        VoxelWorld covered = roomWithSill();
        covered.foreign(5, 53, 5);
        assertEquals(roomCells(FLOOR, 51), wetCells(fill(settings(pools -> pools.setFreeboard(new KList<>(0, 0))), covered, 0, 0), "water"));

        VoxelWorld low = room(0, 15, 0, 15, FLOOR, 52);
        Map<Long, HydrologyCaveCell> written = fill(settings(pools -> pools.setFreeboard(new KList<>(0, 0))), low, 0, 0);
        assertEquals(roomCells(FLOOR, 52), wetCells(written, "water"));
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                assertGuard(written, x, 53, z);
            }
        }
    }

    @Test
    public void lavaProfileHonoured() {
        VoxelWorld world = roomWithSill();
        HydrologySettings lava = settings(pools -> pools.setFluid("lava"));
        Map<Long, HydrologyCaveCell> written = fill(lava, world, 0, 0);
        assertFalse(wetCells(written, "lava").isEmpty());
        assertTrue(wetCells(written, "water").isEmpty());

        VoxelWorld policy = roomWithSill();
        policy.policyFluid(x -> "lava");
        Map<Long, HydrologyCaveCell> overridden = fill(settings(pools -> {
        }), policy, 0, 0);
        assertFalse(wetCells(overridden, "lava").isEmpty());
        assertTrue(wetCells(overridden, "water").isEmpty());

        VoxelWorld unknown = roomWithSill();
        unknown.policyFluid(x -> "magma");
        assertThrows(IllegalStateException.class, () -> fill(settings(pools -> {
        }), unknown, 0, 0));
    }

    @Test
    public void foreignCellsAndDisabledPoolsWriteNothing() {
        VoxelWorld feature = roomWithSill();
        feature.foreign(3, FLOOR, 3);
        assertTrue(fill(settings(pools -> {
        }), feature, 0, 0).isEmpty());

        assertTrue(fill(settings(pools -> pools.setEnabled(false)), roomWithSill(), 0, 0).isEmpty());
        assertTrue(fill(settings(pools -> pools.setMinArea(300)), roomWithSill(), 0, 0).isEmpty());
        assertNotEquals(0, fill(settings(pools -> pools.setMaxArea(256)), roomWithSill(), 0, 0).size());
        assertTrue(fill(settings(pools -> pools.setMaxArea(255)), roomWithSill(), 0, 0).isEmpty());
    }

    private static void assertNoMixedBodies(Map<Long, HydrologyCaveCell> written) {
        for (Map.Entry<Long, HydrologyCaveCell> entry : written.entrySet()) {
            if (entry.getValue().action() != HydrologyCaveAction.POOL_SOURCE) {
                continue;
            }
            long cell = entry.getKey();
            int[][] offsets = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            for (int[] offset : offsets) {
                HydrologyCaveCell neighbour = written.get(key(x(cell) + offset[0], y(cell) + offset[1], z(cell) + offset[2]));
                if (neighbour != null && neighbour.action() == HydrologyCaveAction.POOL_SOURCE) {
                    assertEquals("one body of water holds one fluid", entry.getValue().fluidProfileKey(), neighbour.fluidProfileKey());
                }
            }
        }
    }

    private static VoxelWorld roomWithSill() {
        VoxelWorld world = room(0, 15, 0, 15, FLOOR, 57);
        world.carve(16, 40, 8, 8, 53, 57);
        return world;
    }

    private static VoxelWorld room(int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {
        VoxelWorld world = new VoxelWorld();
        world.carve(minX, maxX, minZ, maxZ, minY, maxY);
        return world;
    }

    private static Set<Long> roomCells(int minY, int maxY) {
        Set<Long> cells = new HashSet<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y <= maxY; y++) {
                    cells.add(key(x, y, z));
                }
            }
        }
        return cells;
    }

    private static Map<Long, HydrologyCaveCell> fill(HydrologySettings settings, VoxelWorld world, int chunkX, int chunkZ) {
        Map<Long, HydrologyCaveCell> written = new HashMap<>();
        new CavePoolFiller(settings).fill(chunkX, chunkZ, world, writer(written), SEED);
        return written;
    }

    private static MantleWriter writer(Map<Long, HydrologyCaveCell> written) {
        MantleWriter writer = mock(MantleWriter.class);
        doAnswer(invocation -> {
            written.put(key(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)), invocation.getArgument(3));
            return null;
        }).when(writer).setData(anyInt(), anyInt(), anyInt(), any());
        when(writer.getDataIfPresent(anyInt(), anyInt(), anyInt(), eq(HydrologyCaveCell.class))).thenAnswer(invocation ->
                written.get(key(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2))));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return null;
        }).when(writer).withChunkFence(anyInt(), anyInt(), any(Runnable.class));
        return writer;
    }

    private static HydrologySettings settings(Consumer<IrisCavePools> pools) {
        IrisHydrology config = new IrisHydrology().setEnabled(true);
        config.getFluids().add(new IrisFluidProfile().setName("lava").setPalette(new IrisMaterialPalette().qclear().qadd("minecraft:lava")));
        pools.accept(config.getUnderground().getCavePools().setDensity(1D));
        return HydrologySettings.compile(config, SEED, 63, 0, 384);
    }

    private static Set<Long> wetCells(Map<Long, HydrologyCaveCell> written, String fluid) {
        Set<Long> cells = new HashSet<>();
        for (Map.Entry<Long, HydrologyCaveCell> entry : written.entrySet()) {
            HydrologyCaveCell cell = entry.getValue();
            if (cell.action() == HydrologyCaveAction.POOL_SOURCE && (fluid == null || fluid.equals(cell.fluidProfileKey()))) {
                cells.add(entry.getKey());
            }
        }
        return cells;
    }

    private static void assertGuard(Map<Long, HydrologyCaveCell> written, int x, int y, int z) {
        HydrologyCaveCell cell = written.get(key(x, y, z));
        assertTrue("guard at " + x + "," + y + "," + z, cell != null && cell.action() == HydrologyCaveAction.SEAL_GUARD);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 4096) << 40) | ((long) y << 20) | (z + 4096);
    }

    private static int x(long key) {
        return (int) (key >>> 40) - 4096;
    }

    private static int z(long key) {
        return (int) (key & 0xFFFFF) - 4096;
    }

    private static int y(long key) {
        return (int) ((key >>> 20) & 0xFFFFF);
    }

    private static final class VoxelWorld implements CarvedTerrain {
        static final int SURFACE = 120;

        private final Set<Long> air = new HashSet<>();
        private final Set<Long> foreign = new HashSet<>();
        private int surface = SURFACE;
        private IntFunction<String> policyFluid = x -> null;

        void carve(int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        air.add(key(x, y, z));
                    }
                }
            }
        }

        void foreign(int x, int y, int z) {
            foreign.add(key(x, y, z));
        }

        void surface(int surface) {
            this.surface = surface;
        }

        void policyFluid(IntFunction<String> fluid) {
            this.policyFluid = fluid;
        }

        @Override
        public CaveVoxel voxel(int x, int y, int z) {
            long key = key(x, y, z);
            if (y <= 0 || y > surface || foreign.contains(key)) {
                return CaveVoxel.FOREIGN;
            }
            return air.contains(key) ? CaveVoxel.AIR : CaveVoxel.SOLID;
        }

        @Override
        public int surface(int x, int z) {
            return surface;
        }

        @Override
        public String caveFluid(int x, int y, int z) {
            return policyFluid.apply(x);
        }
    }
}
