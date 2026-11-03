package art.arcane.iris.generation.hydrology.underground;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.hydrology.config.IrisCaveSprings;
import art.arcane.iris.generation.hydrology.config.IrisFluidProfile;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.mantle.MantleWriter;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.matter.MatterUpdate;
import art.arcane.volmlib.util.matter.slices.UpdateMatter;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class CaveSpringPlacerTest {
    private static final long SEED = 1337L;
    private static final long[] SEEDS = {1337L, 42L, 7L, 99L, 2048L, -5L, 31337L, 88L};
    private static final int FLOOR = 50;
    private static final int CEILING = 70;
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    @Test
    public void placesOneSourceInARockWallAboveAnOpeningAndAsksForAnUpdate() {
        World world = pillars();
        Recorder recorder = new Recorder();
        for (long seed : SEEDS) {
            placer(springs -> {
            }).place(0, 0, () -> world, recorder.writer, seed);
        }

        assertFalse(recorder.cells.isEmpty());
        for (Map.Entry<Long, HydrologyCaveCell> entry : recorder.cells.entrySet()) {
            int x = x(entry.getKey());
            int y = y(entry.getKey());
            int z = z(entry.getKey());
            assertEquals(HydrologyCaveAction.SPRING_SOURCE, entry.getValue().action());
            assertEquals("lava", entry.getValue().fluidProfileKey());
            assertEquals(0, x >> 4);
            assertEquals(0, z >> 4);
            assertEquals(CaveVoxel.SOLID, world.voxel(x, y, z));
            assertEquals(CaveVoxel.SOLID, world.voxel(x, y + 1, z));
            assertEquals(CaveVoxel.SOLID, world.voxel(x, y - 1, z));
            boolean opening = false;
            for (int[] side : SIDES) {
                opening |= world.voxel(x + side[0], y, z + side[1]) == CaveVoxel.AIR
                        && world.voxel(x + side[0], y - 1, z + side[1]) == CaveVoxel.AIR;
            }
            assertTrue("seep at " + x + "," + y + "," + z + " opens over a drop", opening);
            assertSame(UpdateMatter.ON, recorder.updates.get(entry.getKey()));
        }
        assertEquals(recorder.cells.keySet(), recorder.updates.keySet());
    }

    @Test
    public void disabledSpringsAndZeroDensityNeverOpenTheCaves() {
        CarvedTerrain carved = mock(CarvedTerrain.class);
        MantleWriter writer = mock(MantleWriter.class);
        new CaveSpringPlacer(compile(new IrisHydrology())).place(0, 0, () -> carved, writer, SEED);
        placer(springs -> springs.setDensity(0D)).place(0, 0, () -> carved, writer, SEED);
        IrisHydrology thinned = config(springs -> springs.setDensity(2D)).setDensity(0D);
        new CaveSpringPlacer(compile(thinned)).place(0, 0, () -> carved, writer, SEED);
        verifyNoInteractions(carved, writer);
    }

    @Test
    public void densityIsSitesPerChunk() {
        World world = pillars();
        CaveSpringPlacer placer = placer(springs -> springs.setDensity(0.05D));
        int[] counter = new int[1];
        for (int chunkX = 0; chunkX < 2048; chunkX++) {
            placer.place(chunkX, 0, () -> {
                counter[0]++;
                return world;
            }, new Recorder().writer, SEED);
        }
        int opened = counter[0];
        assertTrue("0.05 sites a chunk open about 102 of 2048 chunks, opened " + opened, opened > 60 && opened < 150);
    }

    @Test
    public void policyFluidReplacesTheSpringFluidAndAnUnknownOneFails() {
        World policy = pillars();
        policy.policyFluid = x -> "water";
        Recorder recorder = new Recorder();
        placer(springs -> {
        }).place(0, 0, () -> policy, recorder.writer, SEED);
        assertFalse(recorder.cells.isEmpty());
        for (HydrologyCaveCell cell : recorder.cells.values()) {
            assertEquals("water", cell.fluidProfileKey());
        }

        World unknown = pillars();
        unknown.policyFluid = x -> "magma";
        assertThrows(IllegalStateException.class, () -> placer(springs -> {
        }).place(0, 0, () -> unknown, new Recorder().writer, SEED));
    }

    @Test
    public void chunkOrderDoesNotMatter() {
        World world = pillars();
        CaveSpringPlacer placer = placer(springs -> {
        });
        List<int[]> chunks = new ArrayList<>();
        for (int chunkX = -2; chunkX <= 2; chunkX++) {
            for (int chunkZ = -2; chunkZ <= 2; chunkZ++) {
                chunks.add(new int[]{chunkX, chunkZ});
            }
        }
        for (long seed : SEEDS) {
            Recorder forward = new Recorder();
            for (int[] chunk : chunks) {
                placer.place(chunk[0], chunk[1], () -> world, forward.writer, seed);
            }
            Collections.shuffle(chunks, new Random(seed));
            Recorder shuffled = new Recorder();
            for (int[] chunk : chunks) {
                placer.place(chunk[0], chunk[1], () -> world, shuffled.writer, seed);
            }
            assertFalse(forward.cells.isEmpty());
            assertEquals(forward.cells, shuffled.cells);
            assertEquals(forward.updates.keySet(), shuffled.updates.keySet());
        }
    }

    @Test
    public void staysMinDepthBelowTheSurfaceAlongTheSeepAndItsFlow() {
        World shallow = pillars();
        shallow.surface = CEILING + 3;
        assertTrue(place(shallow, springs -> springs.setMinDepthBelowSurface(CEILING + 3 - FLOOR)).isEmpty());

        World deep = pillars();
        Map<Long, HydrologyCaveCell> written = place(deep, springs -> springs.setMinDepthBelowSurface(deep.surface - 58));
        assertFalse(written.isEmpty());
        for (long cell : written.keySet()) {
            assertTrue(y(cell) <= 58);
        }

        World sloped = pillars();
        sloped.surfaceAt = (x, z) -> x >= 8 ? FLOOR + 8 : 200;
        assertTrue("the flow reaches ground too close to the surface",
                place(sloped, springs -> springs.setMinDepthBelowSurface(16)).isEmpty());
    }

    @Test
    public void theFlowKeepsMinDepthOnGroundThatStaysAboveIt() {
        World lowGround = pillars();
        lowGround.surfaceAt = (x, z) -> x >= 8 ? 60 : 200;
        assertFalse("the floor at 51 lies 9 under the low ground",
                place(lowGround, springs -> springs.setMinDepthBelowSurface(9)).isEmpty());
        assertTrue("the same floor is too shallow for 10",
                place(lowGround, springs -> springs.setMinDepthBelowSurface(10)).isEmpty());
    }

    @Test
    public void aForeignCellBesideTheSeepDropsItEvenWithAnotherSideOpen() {
        World walled = pillars();
        for (int x = -16; x < 32; x++) {
            for (int z = -16; z < 32; z++) {
                if (Math.floorMod(x + z, 2) == 1 && Math.floorMod(z, 2) == 1) {
                    for (int y = FLOOR + 2; y <= CEILING; y++) {
                        walled.foreign(x, y, z);
                    }
                }
            }
        }
        assertTrue(place(walled, springs -> {
        }).isEmpty());
    }

    @Test
    public void anOpeningWithNoDropHoldsNoSeep() {
        World slit = new World();
        World drop = new World();
        for (int x = -16; x < 32; x++) {
            for (int z = -16; z < 32; z++) {
                if (Math.floorMod(x + z, 2) == 1) {
                    slit.carve(x, x, z, z, 60, 60);
                    drop.carve(x, x, z, z, 59, 60);
                }
            }
        }
        assertTrue(place(slit, springs -> {
        }).isEmpty());
        assertFalse(place(drop, springs -> {
        }).isEmpty());
    }

    @Test
    public void aSeepNeedsRockDirectlyAboveAndBelow() {
        World layered = pillars();
        for (int x = -16; x < 32; x++) {
            for (int z = -16; z < 32; z++) {
                if (Math.floorMod(x + z, 2) == 0) {
                    for (int y = FLOOR + 4; y <= CEILING; y += 3) {
                        layered.carve(x, x, z, z, y, y);
                    }
                }
            }
        }
        assertTrue("every wall block has air directly above or below it", place(layered, springs -> {
        }).isEmpty());
    }

    @Test
    public void seepsCoverEveryColumnOfTheirChunk() {
        World world = pillars();
        CaveSpringPlacer placer = placer(springs -> {
        });
        Recorder recorder = new Recorder();
        for (long seed = 0L; seed < 128L; seed++) {
            placer.place(0, 0, () -> world, recorder.writer, seed);
        }
        Set<Integer> xs = new HashSet<>();
        Set<Integer> zs = new HashSet<>();
        for (long cell : recorder.cells.keySet()) {
            xs.add(x(cell));
            zs.add(z(cell));
        }
        Set<Integer> chunk = new HashSet<>();
        for (int column = 0; column < 16; column++) {
            chunk.add(column);
        }
        assertEquals(chunk, xs);
        assertEquals(chunk, zs);
    }

    @Test
    public void theFlowIsFollowedFifteenBlocksAndNoFurther() {
        Consumer<IrisCaveSprings> one = springs -> springs.setDensity(1D);
        int checked = 0;
        for (long seed : SEEDS) {
            Map<Long, HydrologyCaveCell> clean = placeAt(pillars(), seed, one);
            for (long cell : clean.keySet()) {
                for (int[] side : SIDES) {
                    World inside = pillars();
                    inside.foreign(x(cell) + side[0] * 15, FLOOR, z(cell) + side[1] * 15);
                    assertTrue(placeAt(inside, seed, one).isEmpty());
                    World outside = pillars();
                    outside.foreign(x(cell) + side[0] * 16, FLOOR, z(cell) + side[1] * 16);
                    assertEquals(clean, placeAt(outside, seed, one));
                    checked++;
                }
            }
        }
        assertTrue(checked > 0);
    }

    @Test
    public void aSeepThatWouldOpenOntoTheSurfaceIsDropped() {
        World open = pillars();
        open.carveOpenings(CEILING + 1, open.surface);
        assertTrue(place(open, springs -> {
        }).isEmpty());
    }

    @Test
    public void foreignCellsInTheFlowAndHistoricChunksDropTheSpring() {
        World bottom = pillars();
        for (int x = -16; x < 32; x++) {
            for (int z = -16; z < 32; z++) {
                bottom.foreign(x, FLOOR, z);
            }
        }
        assertTrue("cave lava, an aquifer or a room under the flow", place(bottom, springs -> {
        }).isEmpty());

        World historic = pillars();
        historic.historicMinX = 8;
        assertTrue("the flow reaches a chunk generated earlier", place(historic, springs -> {
        }).isEmpty());

        World own = pillars();
        own.historicMinX = -64;
        assertTrue("own chunk generated earlier", place(own, springs -> {
        }).isEmpty());
    }

    @Test
    public void structuresNearbyDropTheSpring() {
        World world = pillars();
        world.structures = (x, y, z) -> true;
        assertTrue(place(world, springs -> {
        }).isEmpty());
    }

    @Test
    public void theStructureCheckIsAskedAboutTheSeepItself() {
        Consumer<IrisCaveSprings> one = springs -> springs.setDensity(1D);
        int checked = 0;
        for (long seed : SEEDS) {
            for (long cell : placeAt(pillars(), seed, one).keySet()) {
                World world = pillars();
                world.structures = (x, y, z) -> key(x, y, z) == cell;
                assertTrue(placeAt(world, seed, one).isEmpty());
                checked++;
            }
        }
        assertTrue(checked > 0);
    }

    @Test
    public void poolWallsWinOverSpringsWhicheverChunkWritesFirst() {
        World world = new World();
        carvePillars(world, 0, 15);
        HydrologySettings settings = compile(config(springs -> {
        }));
        CaveSpringPlacer placer = new CaveSpringPlacer(settings);
        CavePoolFiller filler = new CavePoolFiller(settings, 0);

        Recorder springsFirst = new Recorder();
        placer.place(0, 0, () -> world, springsFirst.writer, SEED);
        Map<Long, HydrologyCaveCell> placed = new HashMap<>(springsFirst.cells);
        filler.fill(0, 0, () -> world, springsFirst.writer, SEED);

        Recorder poolsFirst = new Recorder();
        filler.fill(0, 0, () -> world, poolsFirst.writer, SEED);
        placer.place(0, 0, () -> world, poolsFirst.writer, SEED);

        assertEquals(springsFirst.cells, poolsFirst.cells);
        assertEquals("a replaced spring asks for no block update", liveUpdates(poolsFirst), liveUpdates(springsFirst));
        Set<Long> replaced = new HashSet<>();
        for (long cell : placed.keySet()) {
            if (springsFirst.cells.get(cell).action() == HydrologyCaveAction.SEAL_GUARD) {
                replaced.add(cell);
            }
        }
        assertFalse("the scene puts a spring in a pool wall", replaced.isEmpty());
    }

    private static CaveSpringPlacer placer(Consumer<IrisCaveSprings> springs) {
        return new CaveSpringPlacer(compile(config(springs)));
    }

    private static Map<Long, HydrologyCaveCell> place(World world, Consumer<IrisCaveSprings> springs) {
        Map<Long, HydrologyCaveCell> written = new HashMap<>();
        for (long seed : SEEDS) {
            written.putAll(placeAt(world, seed, springs));
        }
        return written;
    }

    private static Map<Long, HydrologyCaveCell> placeAt(World world, long seed, Consumer<IrisCaveSprings> springs) {
        Recorder recorder = new Recorder();
        placer(springs).place(0, 0, () -> world, recorder.writer, seed);
        return recorder.cells;
    }

    private static Set<Long> liveUpdates(Recorder recorder) {
        Set<Long> live = new HashSet<>();
        for (Map.Entry<Long, MatterUpdate> update : recorder.updates.entrySet()) {
            if (update.getValue().isUpdate()) {
                live.add(update.getKey());
            }
        }
        return live;
    }

    private static IrisHydrology config(Consumer<IrisCaveSprings> springs) {
        IrisHydrology config = new IrisHydrology().setEnabled(true);
        config.getFluids().add(new IrisFluidProfile().setName("lava").setPalette(new IrisMaterialPalette().qclear().qadd("minecraft:lava")));
        config.getUnderground().getCavePools().setDensity(8D).setMaxDepth(16).setMaxArea(961).setFreeboard(new KList<>(0, 0));
        springs.accept(config.getUnderground().getCaveSprings().setEnabled(true).setDensity(2D));
        return config;
    }

    private static HydrologySettings compile(IrisHydrology config) {
        return HydrologySettings.compile(config, SEED, 63, 0, 384);
    }

    private static World pillars() {
        World world = new World();
        carvePillars(world, -16, 31);
        return world;
    }

    private static void carvePillars(World world, int min, int max) {
        world.carve(min, max, min, max, FLOOR, FLOOR + 1);
        for (int x = min; x <= max; x++) {
            for (int z = min; z <= max; z++) {
                if (Math.floorMod(x + z, 2) == 1) {
                    world.carve(x, x, z, z, FLOOR, CEILING);
                }
            }
        }
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

    private interface SurfaceAt {
        int at(int x, int z);
    }

    private interface StructureAt {
        boolean at(int x, int y, int z);
    }

    private static final class Recorder {
        private final Map<Long, HydrologyCaveCell> cells = new HashMap<>();
        private final Map<Long, MatterUpdate> updates = new HashMap<>();
        private final MantleWriter writer = mock(MantleWriter.class);

        private Recorder() {
            doAnswer(invocation -> {
                long key = key(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2));
                Object value = invocation.getArgument(3);
                if (value instanceof HydrologyCaveCell cell) {
                    cells.put(key, cell);
                } else {
                    updates.put(key, (MatterUpdate) value);
                }
                return null;
            }).when(writer).setData(anyInt(), anyInt(), anyInt(), any());
            when(writer.getDataIfPresent(anyInt(), anyInt(), anyInt(), eq(HydrologyCaveCell.class))).thenAnswer(invocation ->
                    cells.get(key(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2))));
            doAnswer(invocation -> {
                invocation.<Runnable>getArgument(2).run();
                return null;
            }).when(writer).withChunkFence(anyInt(), anyInt(), any(Runnable.class));
        }
    }

    private static final class World implements CarvedTerrain {
        private final Set<Long> air = new HashSet<>();
        private final Set<Long> foreign = new HashSet<>();
        private int surface = 200;
        private SurfaceAt surfaceAt;
        private IntFunction<String> policyFluid = x -> null;
        private int historicMinX = Integer.MAX_VALUE;
        private StructureAt structures = (x, y, z) -> false;

        void carve(int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        air.add(key(x, y, z));
                    }
                }
            }
        }

        void carveOpenings(int minY, int maxY) {
            for (int x = -16; x < 32; x++) {
                for (int z = -16; z < 32; z++) {
                    if (Math.floorMod(x + z, 2) == 1) {
                        carve(x, x, z, z, minY, maxY);
                    }
                }
            }
        }

        void foreign(int x, int y, int z) {
            foreign.add(key(x, y, z));
        }

        @Override
        public CaveVoxel voxel(int x, int y, int z) {
            long key = key(x, y, z);
            if (y <= 0 || y > surface(x, z) || x >= historicMinX || foreign.contains(key)) {
                return CaveVoxel.FOREIGN;
            }
            return air.contains(key) ? CaveVoxel.AIR : CaveVoxel.SOLID;
        }

        @Override
        public int surface(int x, int z) {
            return surfaceAt == null ? surface : surfaceAt.at(x, z);
        }

        @Override
        public String caveFluid(int x, int y, int z) {
            return policyFluid.apply(x);
        }

        @Override
        public boolean structureNear(int x, int y, int z) {
            return structures.at(x, y, z);
        }
    }
}
