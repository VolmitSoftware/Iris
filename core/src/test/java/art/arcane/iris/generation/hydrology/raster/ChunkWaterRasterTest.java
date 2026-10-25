package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.hydrology.config.IrisBankStyle;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyMaterials;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.IndexedWindow;
import art.arcane.iris.generation.hydrology.plan.RegionKey;
import art.arcane.iris.generation.hydrology.plan.RegionPlan;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.plan.WindowPlan;
import art.arcane.iris.generation.hydrology.underground.SeaInlet;
import art.arcane.iris.generation.hydrology.underground.SeaInletRasterizer;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondKind;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntBinaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ChunkWaterRasterTest {
    private static final long SEED = 1337L;
    private static final int SEA_LEVEL = 40;
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;
    private static final float SPACING = 2f;
    private static final int GENTLE_STEP = 3;

    @Test
    public void channelBedBelowWaterHead() {
        IntBinaryOperator natural = (x, z) -> x >= 40 && x < 50 ? 82 : 90 + (int) Math.round(2D * Math.sin(x / 9D));
        HydrologyTier river = tier("river", 5f, 0.5f, 8, IrisBankStyle.SMOOTH);
        Scene scene = new Scene(settings(new IrisHydrology()), natural).reach(line(11L, river, 0L, -40f, 8f, 88f, 8f, (x, z) -> 87));
        int channel = 0;
        int pooled = 0;
        for (int x = -32; x < 80; x++) {
            for (int z = -8; z < 24; z++) {
                if (!isChannel(scene.role(x, z))) {
                    continue;
                }
                channel++;
                int bed = scene.terrain(x, z);
                int ground = natural.applyAsInt(x, z);
                assertEquals(87, scene.head(x, z));
                assertTrue("bed " + bed + " below the water head at " + x + "," + z, bed < 87);
                assertTrue("bed never above natural at " + x + "," + z, bed <= ground);
                if (ground < 84) {
                    pooled++;
                    assertEquals("a depression under the channel keeps its natural floor at " + x + "," + z, ground, bed);
                }
            }
            assertTrue("full depth on the centre line at x=" + x, 87 - scene.terrain(x, 8) >= 3);
        }
        assertTrue("channel columns " + channel, channel > 900);
        assertTrue("pooled columns " + pooled, pooled > 50);
        List<int[]> water = new ArrayList<>();
        for (int x = -80; x < 128; x++) {
            for (int z = -48; z < 64; z++) {
                if (scene.water(x, z)) {
                    water.add(new int[]{x, z});
                }
            }
        }
        int measured = 0;
        for (int x = -32; x < 80; x++) {
            for (int z = -40; z < 56; z++) {
                if (scene.water(x, z)) {
                    assertEquals(0, scene.shore(x, z));
                    continue;
                }
                int expected = (int) Math.min(ChunkWaterRaster.MAX_SHORE_DISTANCE, Math.ceil(nearest(water, x, z) - 1e-9D));
                int actual = scene.shore(x, z);
                assertTrue("river shore distance " + actual + " against " + expected + " at " + x + "," + z, Math.abs(actual - expected) <= 1);
                measured++;
            }
        }
        assertTrue(measured > 5000);
        assertEquals("far columns sit at the cap", ChunkWaterRaster.MAX_SHORE_DISTANCE, scene.shore(0, -40));
    }

    @Test
    public void lakeRimShoreDistanceMatchesNearestLakeWater() {
        IntBinaryOperator natural = (x, z) -> 60 + (int) (Math.hypot(x - 24, z - 24) / 2D);
        Pond lake = pond(131L, PondKind.LAKE, 68, circle(24, 24, 12, 16), 24, 24, 0);
        Scene scene = new Scene(settings(new IrisHydrology()), natural).lake(lake);
        List<int[]> water = new ArrayList<>();
        for (int x = -48; x < 96; x++) {
            for (int z = -48; z < 96; z++) {
                if (lake.mask().grownContains(x, z) && natural.applyAsInt(x, z) < lake.level()) {
                    water.add(new int[]{x, z});
                }
            }
        }
        int rim = 0;
        for (int x = -40; x < 88; x++) {
            for (int z = -40; z < 88; z++) {
                boolean wet = lake.mask().grownContains(x, z) && natural.applyAsInt(x, z) < lake.level();
                assertEquals("lake water by the per-column rule at " + x + "," + z, wet, scene.water(x, z));
                if (wet) {
                    assertEquals(0, scene.shore(x, z));
                    continue;
                }
                int expected = (int) Math.min(ChunkWaterRaster.MAX_SHORE_DISTANCE, Math.ceil(nearest(water, x, z) - 1e-9D));
                int actual = scene.shore(x, z);
                assertTrue("lake shore distance " + actual + " against " + expected + " at " + x + "," + z, Math.abs(actual - expected) <= 1);
                if (lake.mask().contains(x, z)) {
                    rim++;
                }
            }
        }
        assertTrue("dry rim columns inside the mask " + rim, rim > 20);
        assertTrue("a dry rim corner is not a shore", scene.shore(8, 8) >= 6);
        assertNotEquals(WaterRole.POND_SHORE, scene.role(8, 8));
    }

    @Test
    public void chainedCrossOwnerLinksNeverRaiseWater() {
        HydrologyTier river = tier("river", 2f, 0.5f, 4, IrisBankStyle.SMOOTH);
        Reach last = line(143L, river, 0L, 520f, 516f, 500f, 560f, (x, z) -> z < 528f ? 63 : z < 540f ? 61 : 59);
        Reach middle = line(142L, river, last.id(), 516f, 470f, 520f, 516f, (x, z) -> z < 494f ? 64 : 62);
        Reach first = line(141L, river, middle.id(), 440f, 470f, 516f, 470f, (x, z) -> x < 480f ? 62 : 60);
        assertEquals(new WindowKey(1, 0), WindowKey.containing(516, 470));
        assertEquals(new WindowKey(1, 1), WindowKey.containing(520, 516));
        assertEquals(new WindowKey(0, 1), WindowKey.containing(500, 560));
        assertTrue("each downstream owner starts above its feeder's end",
                middle.grade()[0] > first.grade()[first.grade().length - 1] && last.grade()[0] > middle.grade()[middle.grade().length - 1]);
        Scene scene = new Scene(settings(new IrisHydrology()), (x, z) -> 75).reach(first).reach(middle).reach(last);
        int previous = Integer.MAX_VALUE;
        int sampled = 0;
        for (Reach reach : List.of(first, middle, last)) {
            for (int point = 0; point < reach.xs().length; point++) {
                int x = Math.round(reach.xs()[point]);
                int z = Math.round(reach.zs()[point]);
                int head = scene.head(x, z);
                assertNotEquals("centre line is water at " + x + "," + z, Integer.MIN_VALUE, head);
                assertTrue("water rises from " + previous + " to " + head + " at " + x + "," + z, head <= previous);
                previous = head;
                sampled++;
            }
        }
        assertTrue(sampled > 60);
        assertTrue("the chain ends at or below the first end grade", previous <= 60);
        List<IndexedWindow> reversed = new ArrayList<>(scene.windows(31, 34));
        Collections.reverse(reversed);
        ChunkWaterRaster backward = new ChunkRasterizer(scene.settings).rasterize(31, 34, scene.natural, reversed, scene.regions(31, 34));
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                assertEquals(scene.raster(31, 34).waterHead(lx, lz), backward.waterHead(lx, lz));
            }
        }
    }

    @Test
    public void pondColumnsShareOneLevel() {
        IntBinaryOperator natural = (x, z) -> 70 + ((x - 16) * (x - 16) + (z - 16) * (z - 16)) / 20;
        Pond pond = pond(21L, PondKind.NATURAL, 75, circle(16, 16, 14, 4), 16, 16, 0);
        Scene scene = new Scene(settings(new IrisHydrology()), natural).pond(pond);
        Set<Long> chunks = new HashSet<>();
        int wet = 0;
        for (int x = -8; x < 40; x++) {
            for (int z = -8; z < 40; z++) {
                if (pond.mask().contains(x, z)) {
                    assertEquals("every mask column is pond water at " + x + "," + z, WaterRole.POND, scene.role(x, z));
                }
                if (scene.role(x, z) != WaterRole.POND) {
                    continue;
                }
                wet++;
                chunks.add(pack(Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
                assertEquals(pond.id(), scene.feature(x, z));
                assertEquals("pond level at " + x + "," + z, 75, scene.head(x, z));
                assertTrue("pond floor below the level at " + x + "," + z, scene.terrain(x, z) < 75);
                assertEquals("water", scene.fluid(x, z));
            }
        }
        assertTrue("pond columns " + wet, wet > 400);
        assertEquals("the pond spans four chunks", 4, chunks.size());
        assertEquals("open water reaches full depth", 75 - 8, scene.terrain(16, 16));
        assertEquals(WaterRole.POND_SHORE, scene.role(16, 30));
    }

    @Test
    public void heightsContinuousAcrossChunkSeam() {
        float startX = -48f;
        float startZ = -20f;
        float endX = 80f;
        float endZ = 60f;
        IntBinaryOperator natural = (x, z) -> 80 + (int) Math.floor(0.35D * perpendicular(x, z, startX, startZ, endX, endZ));
        HydrologyTier river = tier("river", 3f, 0.5f, 12, IrisBankStyle.SMOOTH);
        Scene scene = new Scene(settings(new IrisHydrology()), natural).reach(line(31L, river, 0L, startX, startZ, endX, endZ, (x, z) -> 79));
        int interior = 0;
        int seam = 0;
        int wetSeamPairs = 0;
        for (int x = -16; x < 48; x++) {
            for (int z = -16; z < 48; z++) {
                for (int axis = 0; axis < 2; axis++) {
                    int nx = x + (axis == 0 ? 1 : 0);
                    int nz = z + (axis == 0 ? 0 : 1);
                    if (nx >= 48 || nz >= 48) {
                        continue;
                    }
                    int step = Math.abs(scene.terrain(x, z) - scene.terrain(nx, nz));
                    boolean crosses = axis == 0 ? Math.floorMod(nx, 16) == 0 : Math.floorMod(nz, 16) == 0;
                    if (crosses) {
                        seam = Math.max(seam, step);
                        if (scene.water(x, z) && scene.water(nx, nz)) {
                            wetSeamPairs++;
                        }
                    } else {
                        interior = Math.max(interior, step);
                    }
                }
            }
        }
        assertTrue("channel water crosses seams: " + wetSeamPairs, wetSeamPairs >= 8);
        assertTrue("seam step " + seam + " exceeds the steepest interior step " + interior, seam <= interior);
        assertTrue("interior step " + interior + " exceeds the bank slope bound", interior <= GENTLE_STEP);
    }

    @Test
    public void noWaterColumnBesideLowerDryColumn() {
        HydrologySettings settings = settings(new IrisHydrology());
        Scene perched = new Scene(settings, (x, z) -> 100 - Math.floorDiv(z, 4))
                .pond(pond(41L, PondKind.SEEDED, 94, circle(8, 24, 10, 4), 8, 24, 0));
        assertContained("perched pond", perched, -16, 0, 32, 48, true);

        HydrologyTier stream = tier("stream", 1.5f, 0.5f, 0, IrisBankStyle.SMOOTH);
        IntBinaryOperator eastSlope = (x, z) -> 100 - Math.floorDiv(x, 10);
        Scene chained = new Scene(settings, eastSlope)
                .pond(pond(42L, PondKind.CHAINED, 99, circle(0, 8, 8, 4), 0, 8, 0))
                .pond(pond(43L, PondKind.CHAINED, 94, circle(64, 8, 8, 4), 64, 8, 0))
                .reach(line(44L, stream, 0L, 8f, 8f, 56f, 8f, (x, z) -> Math.clamp(eastSlope.applyAsInt((int) x, (int) z) - 1, 94, 99)));
        assertContained("chained ponds", chained, -16, -8, 80, 24, true);

        IntBinaryOperator cliff = (x, z) -> (x < 24 ? 100 : 80) - Math.max(0, z - 8);
        Scene waterfall = new Scene(settings, cliff).reach(line(45L, stream, 0L, -24f, 8f, 72f, 8f, (x, z) -> x < 24 ? 99 : 79));
        assertContained("waterfall", waterfall, -16, -8, 64, 24, true);
        assertEquals(WaterRole.WATERFALL, waterfall.role(24, 8));
        assertEquals(99, waterfall.head(22, 8));
        assertEquals(79, waterfall.head(24, 8));
    }

    @Test
    public void caveCeilingSetUnderWater() {
        for (boolean masked : new boolean[]{true, false}) {
            IrisHydrology config = new IrisHydrology();
            config.getDeformation().setCaveMask(masked);
            Scene scene = new Scene(settings(config), (x, z) -> 80)
                    .reach(line(51L, tier("river", 3f, 0.5f, 6, IrisBankStyle.SMOOTH), 0L, -30f, 8f, 60f, 8f, (x, z) -> 79))
                    .pond(pond(52L, PondKind.NATURAL, 79, circle(8, 40, 8, 4), 8, 40, 0));
            int wet = 0;
            for (int x = -16; x < 48; x++) {
                for (int z = -8; z < 56; z++) {
                    int ceiling = scene.cave(x, z);
                    if (!scene.water(x, z)) {
                        assertEquals("dry columns are unmasked at " + x + "," + z, Integer.MAX_VALUE, ceiling);
                        continue;
                    }
                    wet++;
                    int expected = masked ? scene.terrain(x, z) - 1 : Integer.MAX_VALUE;
                    assertEquals("cave ceiling at " + x + "," + z + " masked=" + masked, expected, ceiling);
                }
            }
            assertTrue("water columns " + wet, wet > 300);
        }
    }

    @Test
    public void openSeaInletSlotColumnsTakeTheInletRoleAtSeaLevel() {
        HydrologySettings settings = settings(new IrisHydrology());
        int stations = 10;
        float[] xs = new float[stations];
        float[] zs = new float[stations];
        float[] halfWidth = new float[stations];
        int[] floor = new int[stations];
        int[] ceiling = new int[stations];
        for (int station = 0; station < stations; station++) {
            xs[station] = -8f + station * 8f;
            zs[station] = 8f;
            halfWidth[station] = 4f;
            floor[station] = SEA_LEVEL - 3;
            ceiling[station] = station < 2 ? SeaInlet.OPEN : SEA_LEVEL + 5;
        }
        SeaInlet inlet = new SeaInlet(71L, xs, zs, halfWidth, SEA_LEVEL, floor, ceiling, 64, 8, 6);
        WindowKey key = new WindowKey(0, 0);
        IndexedWindow own = new IndexedWindow(new WindowPlan(key, List.of(), List.of(), List.of(inlet)), ReachIndex.build(List.of(), List.of()));
        ChunkRasterizer rasterizer = new ChunkRasterizer(settings);
        ChunkWaterRaster mouth = rasterizer.rasterize(0, 0, (x, z) -> 70, List.of(own), regions(0, 0, List.of()));
        int[] column = new int[2];
        int open = 0;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                boolean slot = SeaInletRasterizer.column(inlet, lx, lz, column) && column[1] == SeaInlet.OPEN;
                if (slot) {
                    open++;
                    assertEquals(WaterRole.INLET, mouth.role(lx, lz));
                    assertEquals(SEA_LEVEL, mouth.waterHead(lx, lz));
                    assertEquals(SEA_LEVEL - 3, mouth.terrainHeight(lx, lz));
                    assertEquals(SEA_LEVEL - 4, mouth.caveCeiling(lx, lz));
                    assertEquals(71L, mouth.featureId(lx, lz));
                    assertEquals(HydrologyCaveCell.SEA_FLUID, mouth.fluid(lx, lz));
                } else {
                    assertEquals(WaterRole.NONE, mouth.role(lx, lz));
                    assertEquals(70, mouth.terrainHeight(lx, lz));
                }
            }
        }
        assertTrue("open slot columns " + open, open > 20);
        assertTrue("a covered passage leaves the surface alone",
                rasterizer.rasterize(2, 0, (x, z) -> 70, List.of(own), regions(2, 0, List.of())).empty());
    }

    @Test
    public void riverlessChunkIsEmptyWithoutNeighbourPlans() {
        HydrologySettings settings = settings(new IrisHydrology());
        Reach far = line(61L, tier("stream", 1.5f, 0.5f, 0, IrisBankStyle.SMOOTH), 0L, 400f, 400f, 480f, 400f, (x, z) -> 69);
        WindowKey key = new WindowKey(0, 0);
        IndexedWindow own = new IndexedWindow(new WindowPlan(key, List.of(far), List.of(), List.of()), ReachIndex.build(List.of(far), List.of()));
        AtomicInteger samples = new AtomicInteger();
        IntBinaryOperator natural = (x, z) -> {
            samples.incrementAndGet();
            return 70;
        };
        ChunkRasterizer rasterizer = new ChunkRasterizer(settings);
        ChunkWaterRaster raster = rasterizer.rasterize(0, 0, natural, List.of(own), regions(0, 0, List.of()));
        assertTrue(raster.empty());
        assertFalse(raster.provisional());
        assertEquals("a riverless chunk never samples natural heights", 0, samples.get());
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                assertEquals(Integer.MIN_VALUE, raster.waterHead(lx, lz));
                assertEquals(WaterRole.NONE, raster.role(lx, lz));
                assertEquals(Integer.MAX_VALUE, raster.caveCeiling(lx, lz));
                assertEquals(ChunkWaterRaster.MAX_SHORE_DISTANCE, raster.shoreDistance(lx, lz));
                assertEquals(0L, raster.featureId(lx, lz));
                assertNull(raster.tier(lx, lz));
                assertNull(raster.fluid(lx, lz));
            }
        }
        assertThrows(IllegalStateException.class, () -> raster.terrainHeight(0, 0));
        assertTrue(ChunkWaterRaster.PROVISIONAL.provisional());
        assertTrue(ChunkWaterRaster.PROVISIONAL.empty());
        assertThrows(IllegalArgumentException.class, () -> rasterizer.rasterize(40, 0, natural, List.of(own), regions(40, 0, List.of())));
        assertFalse("a chunk on the reach is not empty",
                rasterizer.rasterize(27, 25, natural, List.of(own), regions(27, 25, List.of())).empty());
    }

    @Test
    public void lakeWaterWinsOverChannel() {
        IntBinaryOperator natural = (x, z) -> 60 + (int) (Math.hypot(x - 24, z - 24) / 2D);
        Pond lake = pond(71L, PondKind.LAKE, 68, circle(24, 24, 12, 16), 24, 24, 0);
        Reach channel = line(72L, tier("river", 2f, 0.5f, 4, IrisBankStyle.SMOOTH), 0L, -24f, 24f, 72f, 24f, (x, z) -> 70);
        Scene scene = new Scene(settings(new IrisHydrology()), natural).lake(lake).reach(channel);
        int lakeColumns = 0;
        int channelColumns = 0;
        for (int x = -16; x < 64; x++) {
            for (int z = 20; z <= 28; z++) {
                boolean underLake = natural.applyAsInt(x, z) < 68 && lake.mask().grownContains(x, z);
                if (underLake) {
                    lakeColumns++;
                    assertEquals("lake water at " + x + "," + z, WaterRole.POND, scene.role(x, z));
                    assertEquals(68, scene.head(x, z));
                    assertEquals(lake.id(), scene.feature(x, z));
                    assertNull(scene.tier(x, z));
                } else if (Math.abs(z - 24) <= 2) {
                    channelColumns++;
                    assertEquals("channel water at " + x + "," + z, WaterRole.CHANNEL, scene.role(x, z));
                    assertEquals(70, scene.head(x, z));
                    assertEquals(channel.id(), scene.feature(x, z));
                }
            }
        }
        assertTrue("lake columns " + lakeColumns, lakeColumns > 200);
        assertTrue("channel columns " + channelColumns, channelColumns > 200);
        Scene duplicated = new Scene(settings(new IrisHydrology()), natural).lake(lake).reach(channel).lakesInEveryRegion();
        for (int x = -16; x < 64; x++) {
            for (int z = 0; z < 48; z++) {
                assertEquals("lakes deduplicate by id at " + x + "," + z, scene.head(x, z), duplicated.head(x, z));
                assertEquals(scene.terrain(x, z), duplicated.terrain(x, z));
            }
        }
    }

    @Test
    public void crossWindowLinkNeverRaisesWaterDownstream() {
        HydrologyTier narrow = tier("river", 2f, 0.5f, 4, IrisBankStyle.SMOOTH);
        HydrologyTier wide = tier("river", 3f, 0.5f, 4, IrisBankStyle.SMOOTH);
        Reach downstream = line(82L, wide, 0L, 504f, 200f, 600f, 200f, (x, z) -> x <= 512f ? 69 : Math.max(60, 69 - (int) ((x - 512f) / 4f)));
        Reach upstream = line(81L, narrow, downstream.id(), 440f, 200f, 504f, 200f, (x, z) -> 72 - (int) ((x - 440f) / 10f));
        int upstreamEnd = upstream.grade()[upstream.grade().length - 1];
        assertEquals(66, upstreamEnd);
        assertTrue("the downstream owner starts above the upstream end", downstream.grade()[0] > upstreamEnd);
        Scene scene = new Scene(settings(new IrisHydrology()), (x, z) -> 75).reach(upstream).reach(downstream);
        int previous = Integer.MAX_VALUE;
        for (int x = 440; x <= 600; x++) {
            int head = scene.head(x, 200);
            assertNotEquals("centre line is water at x=" + x, Integer.MIN_VALUE, head);
            assertTrue("water rises from " + previous + " to " + head + " at x=" + x, head <= previous);
            if (x >= 504 && x <= 520) {
                assertTrue("water near the link stays at or below the upstream end at x=" + x, head <= upstreamEnd);
            }
            previous = head;
        }
        assertFalse("widths blend from the narrow upstream end", scene.water(506, 203));
        assertTrue("the full downstream width returns past the blend", scene.water(540, 203));
        ChunkWaterRaster forward = scene.raster(31, 12);
        List<IndexedWindow> reversed = new ArrayList<>(scene.windows(31, 12));
        Collections.reverse(reversed);
        ChunkWaterRaster backward = new ChunkRasterizer(scene.settings).rasterize(31, 12, scene.natural, reversed, scene.regions(31, 12));
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                assertEquals(forward.waterHead(lx, lz), backward.waterHead(lx, lz));
                assertEquals(forward.terrainHeight(lx, lz), backward.terrainHeight(lx, lz));
            }
        }
    }

    @Test
    public void aPondOutletNeverStartsAboveTheComposedPond() {
        HydrologyTier narrow = tier("river", 2f, 0.5f, 4, IrisBankStyle.SMOOTH);
        Reach outlet = line(91L, narrow, 0L, 236f, 200f, 320f, 200f, (x, z) -> 72 - (int) ((x - 236f) / 12f));
        Pond pond = new Pond(90L, PondKind.NATURAL, 68, 60, 228, 200, 69.5f, circle(212, 200, 20, 4), outlet.id(), 0, "water", 212, 200);
        assertTrue("the outlet owner starts above the pond", outlet.grade()[0] > pond.level());
        Scene scene = new Scene(settings(new IrisHydrology()), (x, z) -> 75).reach(outlet).pond(pond);
        for (int x = 238; x <= 320; x++) {
            int head = scene.head(x, 200);
            assertNotEquals("centre line is water at x=" + x, Integer.MIN_VALUE, head);
            assertTrue("outlet water at " + head + " above the pond at x=" + x, head <= pond.level());
        }
    }

    @Test
    public void everyBankStyleRasterizes() {
        IrisHydrology config = new IrisHydrology();
        config.getDeformation().setBankSlope(0D).setBankSteepFactor(0D).setBankNoise(0D);
        HydrologySettings settings = settings(config);
        IntBinaryOperator natural = (x, z) -> z >= 8 ? 70 + (z - 8) : 68 - (8 - z) / 4;
        Map<IrisBankStyle, int[]> cuts = new EnumMap<>(IrisBankStyle.class);
        for (IrisBankStyle style : IrisBankStyle.values()) {
            Scene scene = new Scene(settings, natural).reach(line(90L + style.ordinal(), tier("river", 2f, 0.5f, 16, style), 0L, -48f, 8f, 64f, 8f,
                    (x, z) -> 69));
            int[] cut = new int[16];
            for (int outward = 1; outward <= 16; outward++) {
                int z = 10 + outward;
                int height = scene.terrain(8, z);
                assertFalse(style + " bank is dry at z=" + z, scene.water(8, z));
                assertTrue(style + " bank role at z=" + z, scene.role(8, z) == WaterRole.BANK || scene.role(8, z) == WaterRole.SHORE);
                assertTrue(style + " bank between water and natural at z=" + z + ": " + height, height >= 69 && height <= natural.applyAsInt(8, z));
                assertTrue(style + " bank never falls toward the ground at z=" + z, outward == 1 || height >= cut[outward - 2]);
                cut[outward - 1] = height;
                int levee = scene.terrain(8, 6 - outward);
                int ground = natural.applyAsInt(8, 6 - outward);
                assertTrue(style + " levee within the fill limit at z=" + (6 - outward) + ": " + levee,
                        levee >= ground && levee <= ground + settings.deformation().maxFill());
            }
            assertEquals(style + " meets natural at the band edge", natural.applyAsInt(8, 26), cut[15]);
            assertTrue(style + " levee holds the water", scene.terrain(8, 5) >= 70);
            assertEquals(style + " levee meets natural at the band edge", natural.applyAsInt(8, -10), scene.terrain(8, -10));
            assertEquals(style + " leaks", 0, scene.leaks(-16, -16, 32, 32));
            cuts.put(style, cut);
        }
        int[] smooth = cuts.get(IrisBankStyle.SMOOTH);
        int[] linear = cuts.get(IrisBankStyle.LINEAR);
        int[] concave = cuts.get(IrisBankStyle.CONCAVE);
        boolean ordered = false;
        for (int index = 0; index < 16; index++) {
            assertTrue("concave climbs faster than linear at outward " + (index + 1), concave[index] >= linear[index]);
            if (index < 8) {
                assertTrue("smooth eases out of the water at outward " + (index + 1), linear[index] >= smooth[index]);
                ordered |= concave[index] > linear[index] && linear[index] > smooth[index];
            }
        }
        assertTrue(ordered);
        int[] cliff = cuts.get(IrisBankStyle.CLIFF);
        for (int index = 0; index < 8; index++) {
            assertEquals("cliff bench at outward " + (index + 1), 69, cliff[index]);
        }
        assertEquals("cliff wall reaches natural", natural.applyAsInt(8, 22), cliff[11]);
        int[] terraced = cuts.get(IrisBankStyle.TERRACED);
        boolean level = false;
        for (int index = 0; index < 15; index++) {
            if (terraced[index] < natural.applyAsInt(8, 11 + index)) {
                assertEquals("terrace step height at outward " + (index + 1), 0, (terraced[index] - 69) % 3);
            }
            level |= terraced[index] > 69 && terraced[index] == terraced[index + 1];
        }
        assertTrue("terraces hold level steps", level);
        List<IrisBankStyle> styles = List.of(IrisBankStyle.values());
        for (int first = 0; first < styles.size(); first++) {
            for (int second = first + 1; second < styles.size(); second++) {
                assertFalse(styles.get(first) + " and " + styles.get(second) + " cut the same bank",
                        Arrays.equals(cuts.get(styles.get(first)), cuts.get(styles.get(second))));
            }
        }
    }

    @Test
    public void bermContainsPerchedChannel() {
        HydrologySettings settings = settings(new IrisHydrology());
        int maxFill = settings.deformation().maxFill();
        HydrologyTier stream = tier("stream", 1.5f, 0.5f, 0, IrisBankStyle.SMOOTH);
        Reach perchedReach = line(101L, stream, 0L, -32f, 8f, 48f, 8f, (x, z) -> 79);
        IntBinaryOperator slope = (x, z) -> 80 - Math.max(0, z - 8);
        Scene scene = new Scene(settings, slope).reach(perchedReach);
        for (int x = -24; x < 40; x++) {
            assertTrue("channel water at x=" + x, scene.water(x, 9));
            assertFalse("dry downhill column at x=" + x, scene.water(x, 10));
            assertEquals("natural sits below the water at x=" + x, 78, slope.applyAsInt(x, 10));
            assertEquals("berm one block above the water at x=" + x, 80, scene.terrain(x, 10));
            assertEquals(WaterRole.SHORE, scene.role(x, 10));
            assertEquals(perchedReach.id(), scene.feature(x, 10));
            assertEquals(stream, scene.tier(x, 10));
            assertEquals("only the edge column is raised at x=" + x, slope.applyAsInt(x, 11), scene.terrain(x, 11));
        }
        assertEquals(0, scene.leaks(-24, -8, 40, 24));

        Scene steep = new Scene(settings, (x, z) -> z > 9 ? 60 : 80).reach(perchedReach);
        assertEquals("past the fill limit the guard berms to the water itself", 79, steep.terrain(0, 10));
        assertTrue("the guard rises past the fill limit", steep.terrain(0, 10) > 60 + maxFill);
        assertEquals(0, steep.leaks(-24, -8, 40, 24));
        Scene below = new Scene(settings, (x, z) -> z > 9 ? 30 : 80).reach(perchedReach);
        assertEquals("a column under the sea surface is bermed when the water stands above it", 79, below.terrain(0, 10));
    }

    @Test
    public void damRingRaisesOnlyDammedPonds() {
        HydrologySettings settings = settings(new IrisHydrology());
        int maxFill = settings.deformation().maxFill();
        IntBinaryOperator slope = (x, z) -> 100 - Math.floorDiv(z, 2);
        Pond dammed = pond(111L, PondKind.NATURAL, 95, circle(8, 24, 8, 4), 8, 24, 3);
        Scene scene = new Scene(settings, slope).pond(dammed);
        int dams = 0;
        int tall = 0;
        for (int x = -8; x <= 24; x++) {
            for (int z = 8; z <= 40; z++) {
                if (scene.role(x, z) != WaterRole.DAM) {
                    continue;
                }
                dams++;
                assertEquals(dammed.id(), scene.feature(x, z));
                assertEquals("dam crest one block above the level at " + x + "," + z, 96, scene.terrain(x, z));
                if (scene.terrain(x, z) - slope.applyAsInt(x, z) > maxFill) {
                    tall++;
                }
            }
        }
        assertTrue("dam ring columns " + dams, dams > 10);
        assertTrue("the dam rises past the berm limit", tall > 0);
        assertEquals(0, scene.leaks(-8, 8, 24, 40));

        Scene undammed = new Scene(settings, slope).pond(pond(112L, PondKind.NATURAL, 95, circle(8, 24, 8, 4), 8, 24, 0));
        int overCap = 0;
        for (int x = -8; x <= 24; x++) {
            for (int z = 8; z <= 40; z++) {
                assertNotEquals(WaterRole.DAM, undammed.role(x, z));
                overCap += !undammed.water(x, z) && undammed.terrain(x, z) > slope.applyAsInt(x, z) + maxFill ? 1 : 0;
            }
        }
        assertTrue("the guard berms an undammed perched pond past the fill limit", overCap > 0);
        assertEquals(0, undammed.leaks(-8, 8, 24, 40));
    }

    @Test
    public void coastalFallMarksLipAndMouthSitsAtSeaLevel() {
        IntBinaryOperator coast = (x, z) -> x < 40 ? 60 : SEA_LEVEL - 10;
        HydrologyTier stream = tier("stream", 1.5f, 0.5f, 0, IrisBankStyle.SMOOTH);
        Reach drawn = line(121L, stream, 0L, -20f, 8f, 50f, 8f, (x, z) -> x < 40f ? 59 : SEA_LEVEL);
        ReachStyle[] style = drawn.style().clone();
        for (int point = 0; point < style.length; point++) {
            boolean sea = drawn.xs()[point] >= 40f;
            boolean lip = !sea && point + 1 < style.length && drawn.xs()[point + 1] >= 40f;
            style[point] = sea ? ReachStyle.MOUTH : lip ? ReachStyle.WATERFALL : ReachStyle.RUN;
        }
        Reach coastal = new Reach(drawn.id(), stream, 0L, drawn.xs(), drawn.zs(), drawn.grade(), drawn.halfWidth(), drawn.depth(), style,
                drawn.catchment());
        Scene scene = new Scene(settings(new IrisHydrology()), coast).reach(coastal);
        assertEquals("the lip carries the fall", WaterRole.WATERFALL, scene.role(38, 8));
        assertEquals(59, scene.head(38, 8));
        int mouths = 0;
        for (int x = -20; x <= 56; x++) {
            for (int z = 0; z <= 16; z++) {
                if (scene.role(x, z) == WaterRole.MOUTH) {
                    mouths++;
                    assertEquals("mouth at sea level at " + x + "," + z, SEA_LEVEL, scene.head(x, z));
                }
                if (x >= 40 && !scene.water(x, z)) {
                    assertEquals("no berm is built in the sea at " + x + "," + z, coast.applyAsInt(x, z), scene.terrain(x, z));
                }
            }
        }
        assertTrue("mouth columns " + mouths, mouths > 20);
        assertEquals(0, scene.leaks(-20, 0, 56, 16));
    }

    private static void assertContained(String name, Scene scene, int minX, int minZ, int maxX, int maxZ, boolean bermed) {
        int water = 0;
        int raised = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (scene.water(x, z)) {
                    water++;
                } else if (scene.terrain(x, z) > scene.natural.applyAsInt(x, z)) {
                    raised++;
                }
            }
        }
        assertTrue(name + " has water: " + water, water > 50);
        assertEquals(name + " leaks", 0, scene.leaks(minX, minZ, maxX, maxZ));
        if (bermed) {
            assertTrue(name + " needed a berm", raised > 0);
        }
    }

    private static boolean isChannel(WaterRole role) {
        return role == WaterRole.CHANNEL || role == WaterRole.CASCADE || role == WaterRole.WATERFALL || role == WaterRole.MOUTH;
    }

    private static HydrologySettings settings(IrisHydrology config) {
        return HydrologySettings.compile(config, SEED, SEA_LEVEL, MIN_Y, MAX_Y);
    }

    private static HydrologyTier tier(String name, float halfWidth, float depthRatio, int bankBand, IrisBankStyle style) {
        return new HydrologyTier(name, 0f, halfWidth, halfWidth, 0f, depthRatio, 1, true, 6, bankBand, style, false,
                new IrisHydrologyMaterials(), "water", List.of());
    }

    private static Reach line(long id, HydrologyTier tier, long downstreamId, float startX, float startZ, float endX, float endZ, Grade grade) {
        double length = Math.hypot(endX - startX, endZ - startZ);
        int points = Math.max(2, (int) Math.round(length / SPACING) + 1);
        float[] xs = new float[points];
        float[] zs = new float[points];
        int[] grades = new int[points];
        float[] halfWidth = new float[points];
        float[] depth = new float[points];
        ReachStyle[] style = new ReachStyle[points];
        for (int point = 0; point < points; point++) {
            float t = point / (float) (points - 1);
            xs[point] = point == points - 1 ? endX : startX + (endX - startX) * t;
            zs[point] = point == points - 1 ? endZ : startZ + (endZ - startZ) * t;
            grades[point] = grade.at(xs[point], zs[point]);
            halfWidth[point] = tier.minHalfWidth();
            depth[point] = tier.depth(tier.minHalfWidth());
            int drop = point == 0 ? 0 : grades[point - 1] - grades[point];
            style[point] = drop >= tier.waterfallMinimumDrop() ? ReachStyle.WATERFALL
                    : drop >= 2 ? ReachStyle.CASCADE : drop == 1 ? ReachStyle.RIFFLE : ReachStyle.RUN;
        }
        return new Reach(id, tier, downstreamId, xs, zs, grades, halfWidth, depth, style, 1000f);
    }

    private static Pond pond(long id, PondKind kind, int level, PondMask mask, int floorX, int floorZ, int damRaise) {
        return new Pond(id, kind, level, level - 8, floorX, floorZ, level + 1.5f, mask, 0L, damRaise, "water", floorX, floorZ);
    }

    private static PondMask circle(int centreX, int centreZ, int radius, int step) {
        int originX = Math.floorDiv(centreX - radius, step) * step - step;
        int originZ = Math.floorDiv(centreZ - radius, step) * step - step;
        int width = (centreX + radius - originX) / step + 2;
        int depth = (centreZ + radius - originZ) / step + 2;
        long[] bits = new long[(width * depth + 63) >>> 6];
        for (int j = 0; j < depth; j++) {
            for (int i = 0; i < width; i++) {
                if (Math.hypot(originX + i * step - centreX, originZ + j * step - centreZ) <= radius) {
                    int index = j * width + i;
                    bits[index >>> 6] |= 1L << index;
                }
            }
        }
        return new PondMask(originX, originZ, step, width, depth, bits);
    }

    private static double perpendicular(int x, int z, float startX, float startZ, float endX, float endZ) {
        double dx = endX - startX;
        double dz = endZ - startZ;
        return Math.abs((x - startX) * dz - (z - startZ) * dx) / Math.hypot(dx, dz);
    }

    private static List<RegionPlan> regions(int chunkX, int chunkZ, List<Pond> lakes) {
        List<RegionPlan> plans = new ArrayList<>();
        for (RegionKey key : ChunkRasterizer.lakeRegions(chunkX, chunkZ)) {
            List<Pond> owned = new ArrayList<>();
            for (Pond lake : lakes) {
                if (key.equals(RegionKey.containing(lake.floorX(), lake.floorZ()))) {
                    owned.add(lake);
                }
            }
            plans.add(new RegionPlan(key, List.of(), owned));
        }
        return plans;
    }

    private static double nearest(List<int[]> columns, int x, int z) {
        double best = Double.POSITIVE_INFINITY;
        for (int[] column : columns) {
            best = Math.min(best, Math.hypot(column[0] - x, column[1] - z));
        }
        return best;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private interface Grade {
        int at(float x, float z);
    }

    private static final class Scene {
        private final HydrologySettings settings;
        private final IntBinaryOperator natural;
        private final ChunkRasterizer rasterizer;
        private final List<Reach> reaches = new ArrayList<>();
        private final List<Pond> ponds = new ArrayList<>();
        private final List<Pond> lakes = new ArrayList<>();
        private final Map<WindowKey, IndexedWindow> windows = new HashMap<>();
        private final Map<Long, ChunkWaterRaster> rasters = new HashMap<>();
        private boolean lakesInEveryRegion;

        private Scene(HydrologySettings settings, IntBinaryOperator natural) {
            this.settings = settings;
            this.natural = natural;
            this.rasterizer = new ChunkRasterizer(settings);
        }

        private Scene reach(Reach reach) {
            reaches.add(reach);
            return this;
        }

        private Scene pond(Pond pond) {
            ponds.add(pond);
            return this;
        }

        private Scene lake(Pond lake) {
            lakes.add(lake);
            return this;
        }

        private Scene lakesInEveryRegion() {
            lakesInEveryRegion = true;
            return this;
        }

        private ChunkWaterRaster raster(int chunkX, int chunkZ) {
            return rasters.computeIfAbsent(pack(chunkX, chunkZ),
                    key -> rasterizer.rasterize(chunkX, chunkZ, natural, windows(chunkX, chunkZ), regions(chunkX, chunkZ)));
        }

        private List<IndexedWindow> windows(int chunkX, int chunkZ) {
            WindowKey own = WindowKey.containing(chunkX * 16, chunkZ * 16);
            List<IndexedWindow> around = new ArrayList<>();
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    around.add(window(new WindowKey(own.wx() + dx, own.wz() + dz)));
                }
            }
            return around;
        }

        private IndexedWindow window(WindowKey key) {
            return windows.computeIfAbsent(key, ignored -> {
                List<Reach> owned = new ArrayList<>();
                for (Reach reach : reaches) {
                    int last = reach.xs().length - 1;
                    if (key.equals(WindowKey.containing((int) Math.floor(reach.xs()[last]), (int) Math.floor(reach.zs()[last])))) {
                        owned.add(reach);
                    }
                }
                List<Pond> ownedPonds = new ArrayList<>();
                for (Pond pond : ponds) {
                    if (key.equals(WindowKey.containing(pond.floorX(), pond.floorZ()))) {
                        ownedPonds.add(pond);
                    }
                }
                return new IndexedWindow(new WindowPlan(key, owned, ownedPonds, List.of()), ReachIndex.build(owned, ownedPonds));
            });
        }

        private List<RegionPlan> regions(int chunkX, int chunkZ) {
            if (!lakesInEveryRegion) {
                return ChunkWaterRasterTest.regions(chunkX, chunkZ, lakes);
            }
            List<RegionPlan> plans = new ArrayList<>();
            for (RegionKey key : ChunkRasterizer.lakeRegions(chunkX, chunkZ)) {
                plans.add(new RegionPlan(key, List.of(), lakes));
            }
            return plans;
        }

        private ChunkWaterRaster at(int x, int z) {
            return raster(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        }

        private int terrain(int x, int z) {
            ChunkWaterRaster raster = at(x, z);
            return raster.empty() ? natural.applyAsInt(x, z) : raster.terrainHeight(Math.floorMod(x, 16), Math.floorMod(z, 16));
        }

        private int head(int x, int z) {
            return at(x, z).waterHead(Math.floorMod(x, 16), Math.floorMod(z, 16));
        }

        private boolean water(int x, int z) {
            return head(x, z) != Integer.MIN_VALUE;
        }

        private WaterRole role(int x, int z) {
            return at(x, z).role(Math.floorMod(x, 16), Math.floorMod(z, 16));
        }

        private long feature(int x, int z) {
            return at(x, z).featureId(Math.floorMod(x, 16), Math.floorMod(z, 16));
        }

        private HydrologyTier tier(int x, int z) {
            return at(x, z).tier(Math.floorMod(x, 16), Math.floorMod(z, 16));
        }

        private String fluid(int x, int z) {
            return at(x, z).fluid(Math.floorMod(x, 16), Math.floorMod(z, 16));
        }

        private int shore(int x, int z) {
            return at(x, z).shoreDistance(Math.floorMod(x, 16), Math.floorMod(z, 16));
        }

        private int cave(int x, int z) {
            return at(x, z).caveCeiling(Math.floorMod(x, 16), Math.floorMod(z, 16));
        }

        private int leaks(int minX, int minZ, int maxX, int maxZ) {
            int[] dx = {1, -1, 0, 0};
            int[] dz = {0, 0, 1, -1};
            int leaks = 0;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!water(x, z)) {
                        continue;
                    }
                    int surface = head(x, z);
                    for (int side = 0; side < 4; side++) {
                        int nx = x + dx[side];
                        int nz = z + dz[side];
                        int ground = terrain(nx, nz);
                        if (!water(nx, nz) && ground >= SEA_LEVEL && ground < surface) {
                            leaks++;
                        }
                    }
                }
            }
            return leaks;
        }
    }
}
