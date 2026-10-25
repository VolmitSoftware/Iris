package art.arcane.iris.probe;

import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.RegionKey;
import art.arcane.iris.generation.hydrology.plan.RegionPlan;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.plan.WindowPlan;
import art.arcane.iris.generation.hydrology.plan.WindowPlanner;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondKind;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HydrologyMetricsTest {
    private static final int SEA = 40;
    private static final int NATURAL = 70;
    private static final HydrologySettings SETTINGS = HydrologySettings.compile(new IrisHydrology().setEnabled(true), 1L, SEA, 0, 320);
    private static final HydrologyTier STREAM = SETTINGS.tiers().get(0);
    private static final HydrologyTier RIVER = SETTINGS.tiers().get(1);
    private static final WindowKey WEST = new WindowKey(0, 0);
    private static final WindowKey EAST = new WindowKey(1, 0);
    private static final HydrologyWindows.WindowRange BOTH = new HydrologyWindows.WindowRange(0, 1, 0, 0);
    private static final HydrologyWindows.WindowRange WEST_ONLY = new HydrologyWindows.WindowRange(0, 0, 0, 0);

    @Test
    public void planSeamsFireOnSharedOwnershipMissingLinksAndOverlappingPonds() {
        Reach shared = reach(1L, STREAM, 99L, 1000f, new int[]{60, 59}, 10f, 10f, 20f, 10f);
        Pond west = pond(10L, 55, square(100, 100, 3), 98L, 104, 104);
        Pond east = pond(11L, 55, square(100, 100, 3), 0L, 108, 108);
        List<String> examples = new ArrayList<>();
        HydrologyPlanMetrics metrics = new HydrologyPlanMetrics(SETTINGS, windows(plan(WEST, List.of(shared), List.of(west)),
                plan(EAST, List.of(shared), List.of(east))), List.of(), BOTH, examples::add);
        assertEquals(examples.toString(), 2L, metrics.seams(WEST));
        assertEquals(examples.toString(), 2L, metrics.seams(EAST));
        assertTrue(examples.toString(), examples.contains("seam reach 1 owned by " + WEST + " and " + EAST));
        assertTrue(examples.toString(), examples.contains("seam reach 1 links to missing reach 99"));
        assertTrue(examples.toString(), examples.contains("seam pond 10 drains to missing reach 98"));
        assertTrue(examples.toString(), examples.contains("seam ponds 10 and 11 from " + WEST + " and " + EAST + " overlap"));
        HydrologyPlanMetrics clean = new HydrologyPlanMetrics(SETTINGS, windows(plan(WEST, List.of(reach(1L, STREAM, 0L, 1000f, new int[]{60, 59},
                10f, 10f, 20f, 10f)), List.of()), plan(EAST, List.of(), List.of())), List.of(), BOTH, examples::add);
        assertEquals(0L, clean.seams(WEST) + clean.seams(EAST));
    }

    @Test
    public void aStemFollowsItsLargestFeederThroughAJunction() {
        Reach small = reach(1L, RIVER, 3L, 5000f, new int[]{90, 80}, 0f, 0f, 200f, 0f);
        Reach large = reach(2L, RIVER, 3L, 6000f, new int[]{90, 80}, 200f, 300f, 200f, 0f);
        Reach trunk = reach(3L, RIVER, 0L, 11000f, new int[]{80, 70}, 200f, 0f, 350f, 0f);
        HydrologyPlanMetrics metrics = new HydrologyPlanMetrics(SETTINGS, windows(plan(WEST, List.of(small, large, trunk), List.of()),
                plan(EAST, List.of(), List.of())), List.of(), BOTH, ignored -> {
        });
        assertEquals(1, metrics.rivers(WEST));
    }

    @Test
    public void aStemContinuesThroughThePondItFillsIntoThePondOutlet() {
        Pond pond = pond(10L, 75, square(240, -16, 8), 2L, 256, 0);
        Reach inflow = reach(1L, RIVER, 0L, 6000f, new int[]{90, 76}, 0f, 0f, 250f, 0f);
        Reach outlet = reach(2L, RIVER, 0L, 6000f, new int[]{75, 70}, 270f, 0f, 470f, 0f);
        HydrologyPlanMetrics metrics = new HydrologyPlanMetrics(SETTINGS, windows(plan(WEST, List.of(inflow, outlet), List.of(pond)),
                plan(EAST, List.of(), List.of())), List.of(), BOTH, ignored -> {
        });
        assertEquals(pond, metrics.endPond(inflow));
        assertEquals(1, metrics.rivers(WEST));
    }

    @Test
    public void aStemIsCountedOnlyByTheWindowThatOwnsItsLowestReach() {
        Reach upper = reach(1L, RIVER, 2L, 6000f, new int[]{90, 80}, 100f, 0f, 500f, 0f);
        Reach lower = reach(2L, RIVER, 0L, 6000f, new int[]{80, 70}, 500f, 0f, 700f, 0f);
        Map<WindowKey, WindowPlan> plans = windows(plan(WEST, List.of(upper), List.of()), plan(EAST, List.of(lower), List.of()));
        HydrologyPlanMetrics west = new HydrologyPlanMetrics(SETTINGS, plans, List.of(), WEST_ONLY, ignored -> {
        });
        HydrologyPlanMetrics both = new HydrologyPlanMetrics(SETTINGS, plans, List.of(), BOTH, ignored -> {
        });
        assertEquals(0, west.rivers(WEST));
        assertEquals(0, both.rivers(WEST));
        assertEquals(1, both.rivers(EAST));
    }

    @Test
    public void aPondIsPerchedAgainstReachesOtherThanItsOwnInflowsAndOutlet() {
        Pond high = pond(10L, 60, square(96, 96, 3), 2L, 100, 100);
        Reach outlet = reach(2L, STREAM, 0L, 1000f, new int[]{60, 59}, 100f, 100f, 100f, 140f);
        Reach below = reach(3L, STREAM, 0L, 1000f, new int[]{50, 49}, 200f, 100f, 200f, 140f);
        HydrologyPlanMetrics perched = new HydrologyPlanMetrics(SETTINGS, windows(plan(WEST, List.of(outlet, below), List.of(high)),
                plan(EAST, List.of(), List.of())), List.of(), BOTH, ignored -> {
        });
        assertEquals(1, perched.perched(WEST));
        Pond low = pond(10L, 55, square(96, 96, 3), 2L, 100, 100);
        HydrologyPlanMetrics level = new HydrologyPlanMetrics(SETTINGS, windows(plan(WEST, List.of(outlet, below), List.of(low)),
                plan(EAST, List.of(), List.of())), List.of(), BOTH, ignored -> {
        });
        assertEquals(0, level.perched(WEST));
    }

    @Test
    public void risingWaterFiresAlongReachesAcrossLinksOutOfPondsIntoPondsAndAtMouths() {
        Reach along = reach(1L, STREAM, 0L, 1000f, new int[]{61, 60, 59}, 10f, 10f, 14f, 10f, 18f, 10f);
        Map<Long, Column> columns = new HashMap<>();
        wet(columns, along, 60, 59, 61);
        assertRises(1L, "rise along reach 1 from 59 to 61", List.of(along), List.of(), columns);

        Reach upper = reach(1L, STREAM, 2L, 1000f, new int[]{60, 55}, 10f, 10f, 14f, 10f);
        Reach lower = reach(2L, STREAM, 0L, 1000f, new int[]{55, 54}, 14f, 20f, 14f, 24f);
        Map<Long, Column> linked = new HashMap<>();
        wet(linked, upper, 60, 55);
        wet(linked, lower, 57, 54);
        assertRises(1L, "rise from reach 1 at 55 into reach 2 at 57", List.of(upper, lower), List.of(), linked);

        Pond pond = pond(10L, 50, square(96, 96, 3), 3L, 100, 100);
        Reach outlet = reach(3L, STREAM, 0L, 1000f, new int[]{50, 49}, 120f, 100f, 130f, 100f);
        Map<Long, Column> outflow = new HashMap<>();
        wet(outflow, outlet, 52, 49);
        assertRises(1L, "rise from pond 10 at 50 into outlet 3 at 52", List.of(outlet), List.of(pond), outflow);

        Reach inflow = reach(4L, STREAM, 0L, 1000f, new int[]{60, 50, 50}, 60f, 100f, 80f, 100f, 100f, 100f);
        Map<Long, Column> backwater = new HashMap<>();
        wet(backwater, inflow, 60, 48, HydrologyVoxels.NO_WATER);
        assertRises(1L, "rise from reach 4 at 48 into pond 10 at 50", List.of(inflow), List.of(pond(10L, 50, square(96, 96, 3), 0L, 100, 100)),
                backwater);

        Reach mouth = reach(5L, STREAM, 0L, 1000f, new int[]{45, 40}, 10f, 10f, 14f, 10f);
        mouth.style()[1] = ReachStyle.MOUTH;
        Map<Long, Column> sea = new HashMap<>();
        wet(sea, mouth, 45, 39);
        assertRises(1L, "rise from the sea at 40 into the mouth of reach 5 at 39", List.of(mouth), List.of(), sea);

        Map<Long, Column> level = new HashMap<>();
        wet(level, mouth, 45, 40);
        assertRises(0L, null, List.of(mouth), List.of(), level);
    }

    @Test
    public void chunkSeamsFireOnDryColumnsLeftBelowTheirBermAndOnePondAtTwoLevels() {
        HydrologyPlanMetrics plans = new HydrologyPlanMetrics(SETTINGS, windows(plan(WEST, List.of(), List.of()), plan(EAST, List.of(), List.of())),
                List.of(), BOTH, ignored -> {
                });
        Map<Long, Column> columns = new HashMap<>();
        columns.put(HydrologyVoxels.columnKey(15, 4), new Column(WaterRole.CHANNEL, 72, NATURAL, 1L));
        columns.put(HydrologyVoxels.columnKey(16, 4), new Column(WaterRole.BANK, HydrologyVoxels.NO_WATER, NATURAL - 1, 0L));
        columns.put(HydrologyVoxels.columnKey(31, 8), new Column(WaterRole.POND, 60, 58, 7L));
        columns.put(HydrologyVoxels.columnKey(32, 8), new Column(WaterRole.POND, 61, 58, 7L));
        List<String> examples = new ArrayList<>();
        HydrologyRasterMetrics metrics = new HydrologyRasterMetrics(SETTINGS, plans, rasters(columns), (x, z) -> NATURAL, BOTH, examples::add);
        assertEquals(examples.toString(), 2L, metrics.chunkSeams(WEST));
        assertTrue(examples.toString(), examples.contains("seam dry column left below its berm across chunks at 15,4 / 16,4"));
        assertTrue(examples.toString(), examples.contains("seam one pond at two levels across chunks at 31,8 / 32,8"));
        columns.put(HydrologyVoxels.columnKey(16, 4), new Column(WaterRole.BANK, HydrologyVoxels.NO_WATER, 72, 0L));
        columns.put(HydrologyVoxels.columnKey(32, 8), new Column(WaterRole.POND, 60, 58, 7L));
        assertEquals(0L, new HydrologyRasterMetrics(SETTINGS, plans, rasters(columns), (x, z) -> NATURAL, BOTH, ignored -> {
        }).chunkSeams(WEST));
    }

    @Test
    public void aChunkSeamCountsAnyDryColumnBelowTheWaterBesideItExceptOverTheSea() {
        HydrologyPlanMetrics plans = new HydrologyPlanMetrics(SETTINGS, windows(plan(WEST, List.of(), List.of()), plan(EAST, List.of(), List.of())),
                List.of(), BOTH, ignored -> {
                });
        Map<Long, Column> columns = new HashMap<>();
        columns.put(HydrologyVoxels.columnKey(15, 4), new Column(WaterRole.CHANNEL, 90, 88, 1L));
        columns.put(HydrologyVoxels.columnKey(16, 4), new Column(WaterRole.BANK, HydrologyVoxels.NO_WATER, 85, 0L));
        columns.put(HydrologyVoxels.columnKey(15, 20), new Column(WaterRole.MOUTH, SEA + 2, SEA - 3, 2L));
        columns.put(HydrologyVoxels.columnKey(16, 20), new Column(WaterRole.SHORE, HydrologyVoxels.NO_WATER, SEA - 2, 0L));
        columns.put(HydrologyVoxels.columnKey(15, 36), new Column(WaterRole.MOUTH, SEA, SEA - 3, 3L));
        columns.put(HydrologyVoxels.columnKey(16, 36), new Column(WaterRole.SHORE, HydrologyVoxels.NO_WATER, SEA - 2, 0L));
        List<String> examples = new ArrayList<>();
        HydrologyRasterMetrics metrics = new HydrologyRasterMetrics(SETTINGS, plans, rasters(columns), (x, z) -> NATURAL - 30, BOTH, examples::add);
        assertEquals(examples.toString(), 2L, metrics.chunkSeams(WEST));
        assertTrue(examples.toString(), examples.contains("seam dry column left below its berm across chunks at 15,4 / 16,4"));
        assertTrue(examples.toString(), examples.contains("seam dry column left below its berm across chunks at 15,20 / 16,20"));
    }

    @Test
    public void aPlanMadeWhileAPlanThatNeedsItRunsIsDepthOneOnAnyThread() throws Exception {
        RegionKey needed = WindowPlanner.lakeRegions(WEST).get(0);
        RegionKey unrelated = new RegionKey(needed.rx() + 100, needed.rz() + 100);
        assertEquals(1, depth(regions -> regions.apply(needed)));
        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            assertEquals(1, depth(regions -> {
                Future<RegionPlan> elsewhere = other.submit(() -> regions.apply(needed));
                try {
                    return elsewhere.get();
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            }));
        } finally {
            other.shutdownNow();
        }
        assertEquals(0, depth(regions -> regions.apply(unrelated)));
        HydrologyColdRuns.PlanGauge sequential = new HydrologyColdRuns.PlanGauge(Set.of(), () -> {
        });
        sequential.region(key -> null).apply(needed);
        sequential.window((key, plans) -> null).apply(WEST, List.of());
        assertEquals(0, sequential.deepest());
    }

    @Test
    public void missedTargetsIgnoreColdLatencyAndNeedSixPondLevelsPerNineTiles() {
        HydrologyReport.Result spread = result(1000_000D, 2.25D, Set.of(50, 51));
        assertTrue(spread.missedTargets().toString(), !spread.missedTargets().contains("coldFirstChunkMs"));
        assertTrue(spread.missedTargets().toString(), !spread.missedTargets().contains("pondLevelsDistinct"));
        HydrologyReport.Result flat = result(10D, 9D, Set.of(50, 51, 52, 53, 54));
        assertTrue(flat.missedTargets().toString(), flat.missedTargets().contains("pondLevelsDistinct"));
    }

    private static HydrologyReport.Result result(double coldMs, double landTiles, Set<Integer> levels) {
        HydrologyReport.WindowMetrics window = new HydrologyReport.WindowMetrics(WEST, coldMs, 0L, 0L, 0D, 0D, 0, landTiles, 100, 0D, 100,
                levels, 0, 0L, 0L, 0L, 0L, 0L, 0L, 0, 0L, 0, 0L, 0L, "digest");
        return new HydrologyReport.Result(List.of(window), 0D, 0, 1, 0L, 0L, 0D, 0L, "digest", List.of(), List.of());
    }

    private static int depth(Function<Function<RegionKey, RegionPlan>, RegionPlan> body) {
        HydrologyColdRuns.PlanGauge gauge = new HydrologyColdRuns.PlanGauge(Set.of(), () -> {
        });
        Function<RegionKey, RegionPlan> regions = gauge.region(key -> null);
        BiFunction<WindowKey, List<RegionPlan>, WindowPlan> windows = gauge.window((key, plans) -> {
            body.apply(regions);
            return null;
        });
        windows.apply(WEST, List.of());
        return gauge.deepest();
    }

    private static void assertRises(long expected, String example, List<Reach> reaches, List<Pond> ponds, Map<Long, Column> columns) {
        WindowPlan plan = plan(WEST, reaches, ponds);
        HydrologyPlanMetrics plans = new HydrologyPlanMetrics(SETTINGS, windows(plan, plan(EAST, List.of(), List.of())), List.of(), BOTH, ignored -> {
        });
        List<String> examples = new ArrayList<>();
        HydrologyRasterMetrics metrics = new HydrologyRasterMetrics(SETTINGS, plans, rasters(columns), (x, z) -> NATURAL, BOTH, examples::add);
        assertEquals(examples.toString(), expected, metrics.rising(WEST, plan));
        if (example != null) {
            assertTrue(examples.toString(), examples.contains(example));
        }
    }

    private static void wet(Map<Long, Column> columns, Reach reach, int... heads) {
        for (int point = 0; point < heads.length; point++) {
            if (heads[point] == HydrologyVoxels.NO_WATER) {
                continue;
            }
            int x = (int) Math.floor(reach.xs()[point]);
            int z = (int) Math.floor(reach.zs()[point]);
            columns.put(HydrologyVoxels.columnKey(x, z), new Column(WaterRole.CHANNEL, heads[point], heads[point] - 2, reach.id()));
        }
    }

    private static HydrologyRasterMetrics.Rasters rasters(Map<Long, Column> columns) {
        Map<Long, ChunkWaterRaster> chunks = new HashMap<>();
        return (chunkX, chunkZ) -> chunks.computeIfAbsent(HydrologyVoxels.chunkKey(chunkX, chunkZ), ignored -> raster(columns, chunkX, chunkZ));
    }

    private static ChunkWaterRaster raster(Map<Long, Column> columns, int chunkX, int chunkZ) {
        ChunkWaterRaster raster = mock(ChunkWaterRaster.class);
        when(raster.empty()).thenReturn(false);
        when(raster.role(anyInt(), anyInt())).thenAnswer(call -> column(columns, chunkX, chunkZ, call.getArgument(0), call.getArgument(1)).role());
        when(raster.waterHead(anyInt(), anyInt())).thenAnswer(call -> column(columns, chunkX, chunkZ, call.getArgument(0), call.getArgument(1)).head());
        when(raster.terrainHeight(anyInt(), anyInt())).thenAnswer(call -> column(columns, chunkX, chunkZ, call.getArgument(0), call.getArgument(1))
                .terrain());
        when(raster.featureId(anyInt(), anyInt())).thenAnswer(call -> column(columns, chunkX, chunkZ, call.getArgument(0), call.getArgument(1))
                .featureId());
        return raster;
    }

    private static Column column(Map<Long, Column> columns, int chunkX, int chunkZ, int lx, int lz) {
        Column column = columns.get(HydrologyVoxels.columnKey((chunkX << 4) + lx, (chunkZ << 4) + lz));
        return column == null ? new Column(WaterRole.NONE, HydrologyVoxels.NO_WATER, NATURAL, 0L) : column;
    }

    private record Column(WaterRole role, int head, int terrain, long featureId) {
    }

    private static Map<WindowKey, WindowPlan> windows(WindowPlan... plans) {
        Map<WindowKey, WindowPlan> windows = new LinkedHashMap<>();
        for (WindowPlan plan : plans) {
            windows.put(plan.key(), plan);
        }
        return windows;
    }

    private static WindowPlan plan(WindowKey key, List<Reach> reaches, List<Pond> ponds) {
        return new WindowPlan(key, reaches, ponds, List.of());
    }

    private static Reach reach(long id, HydrologyTier tier, long downstream, float catchment, int[] grade, float... coordinates) {
        int points = coordinates.length / 2;
        float[] xs = new float[points];
        float[] zs = new float[points];
        float[] halfWidth = new float[points];
        float[] depth = new float[points];
        ReachStyle[] style = new ReachStyle[points];
        for (int point = 0; point < points; point++) {
            xs[point] = coordinates[2 * point];
            zs[point] = coordinates[2 * point + 1];
            halfWidth[point] = 1f;
            depth[point] = 1f;
        }
        Arrays.fill(style, ReachStyle.RUN);
        return new Reach(id, tier, downstream, xs, zs, grade, halfWidth, depth, style, catchment);
    }

    private static Pond pond(long id, int level, PondMask mask, long outlet, int floorX, int floorZ) {
        return new Pond(id, PondKind.NATURAL, level, level - 4, floorX, floorZ, level + 1f, mask, outlet, 0, "minecraft:water", floorX, floorZ);
    }

    private static PondMask square(int originX, int originZ, int nodes) {
        long[] bits = new long[(nodes * nodes + 63) >>> 6];
        for (int index = 0; index < nodes * nodes; index++) {
            bits[index >>> 6] |= 1L << index;
        }
        return new PondMask(originX, originZ, 8, nodes, nodes, bits);
    }
}
