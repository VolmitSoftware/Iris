package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.hydrology.config.IrisBankStyle;
import art.arcane.iris.generation.hydrology.config.IrisFluidProfile;
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
import art.arcane.iris.generation.hydrology.water.ConfluenceGrader;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondKind;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachShaper;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import art.arcane.iris.generation.hydrology.water.ShapedNetwork;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.testsupport.DeltaCoast;
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
import java.util.function.DoubleBinaryOperator;
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
    public void flatLakesSkipExtraTerrainSamplingForRiffles() {
        AtomicInteger samples = new AtomicInteger();
        Scene scene = new Scene(settings(new IrisHydrology()), (x, z) -> {
            samples.incrementAndGet();
            return 70;
        }).lake(pond(8L, PondKind.LAKE, 80, circle(8, 8, 100, 1), 8, 8, 0));

        assertEquals(80, scene.raster(0, 0).waterHead(8, 8));
        assertEquals(320, samples.get());
    }

    @Test
    public void shallowStandingWaterRetainsItsFootprintWhenItsHeadLowersOneBlock() {
        int desiredLevel = 75;
        Pond water = pond(9L, PondKind.NATURAL, desiredLevel, circle(-16, -16, 12, 1), -16, -16, 0);
        HydrologySettings settings = settings(new IrisHydrology());
        for (Scene scene : List.of(new Scene(settings, (x, z) -> desiredLevel - 1).pond(water),
                new Scene(settings, (x, z) -> desiredLevel - 1).lake(water))) {
            int wet = 0;
            for (int x = -32; x <= 0; x++) {
                for (int z = -32; z <= 0; z++) {
                    if (!water.mask().contains(x, z)) {
                        continue;
                    }
                    wet++;
                    assertEquals("head at " + x + "," + z, desiredLevel - 1, scene.head(x, z));
                    assertTrue("bed at " + x + "," + z, scene.terrain(x, z) < scene.head(x, z));
                }
            }
            assertTrue("wet footprint " + wet, wet > 400);
            assertEquals(0, scene.leaks(-32, -32, 0, 0));
        }
    }

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
                assertEquals(86, scene.head(x, z));
                assertTrue("bed " + bed + " below the water head at " + x + "," + z, bed < 86);
                assertTrue("bed never above natural at " + x + "," + z, bed <= ground);
                if (ground < 84) {
                    pooled++;
                    assertEquals("a depression under the channel keeps its natural floor at " + x + "," + z, ground, bed);
                }
            }
            assertTrue("the centre line keeps a submerged bed at x=" + x, 86 - scene.terrain(x, 8) >= 2);
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
    public void channelBedsAndBanksVaryAlongConstantWidthReaches() {
        IrisHydrology config = new IrisHydrology();
        config.getDeformation().setBankNoise(0D);
        HydrologySettings settings = settings(config);
        HydrologyTier river = tier("river", 8f, 0.75f, 24, IrisBankStyle.SMOOTH);
        Reach reach = line(15L, river, 0L, -112f, 0f, 112f, 0f, (x, z) -> 100);
        Scene scene = new Scene(settings, (x, z) -> 120).reach(reach);
        Set<Integer> bedHeights = new HashSet<>();
        Set<Integer> bankHeights = new HashSet<>();
        int previousBed = scene.terrain(-80, 4);
        int previousBank = scene.terrain(-80, 20);
        for (int x = -80; x <= 80; x++) {
            int bed = scene.terrain(x, 4);
            int bank = scene.terrain(x, 20);
            bedHeights.add(bed);
            bankHeights.add(bank);
            assertEquals(100, scene.head(x, 4));
            assertEquals(Integer.MIN_VALUE, scene.head(x, 20));
            assertTrue("depth stays within the tier limit", bed >= 94 && bed < 100);
            assertTrue("bank stays between the water and terrain", bank >= 100 && bank <= 120);
            assertTrue("bed variation remains coherent across chunk boundaries", Math.abs(bed - previousBed) <= 1);
            assertTrue("bank variation remains coherent across chunk boundaries", Math.abs(bank - previousBank) <= 1);
            previousBed = bed;
            previousBank = bank;
        }
        assertTrue("constant-width bed has varying contours", bedHeights.size() >= 3);
        assertTrue("constant-distance bank has varying contours", bankHeights.size() >= 3);
    }

    @Test
    public void pondShelvesAndTapersVaryWithoutChangingWaterCoverage() {
        HydrologySettings settings = settings(new IrisHydrology());
        long[] bits = new long[192 * 64 / 64];
        Arrays.fill(bits, -1L);
        Pond water = pond(16L, PondKind.NATURAL, 100, new PondMask(-96, -32, 1, 192, 64, bits), 0, 0, 0);
        Scene scene = new Scene(settings, (x, z) -> 110).pond(water);
        Set<Integer> shelfHeights = new HashSet<>();
        Set<Integer> deepHeights = new HashSet<>();
        Set<Integer> taperHeights = new HashSet<>();
        for (int x = -64; x <= 64; x++) {
            shelfHeights.add(scene.terrain(x, -28));
            deepHeights.add(scene.terrain(x, 0));
            taperHeights.add(scene.terrain(x, -40));
            for (int z = -48; z <= 47; z++) {
                assertEquals("the shoreline remains fixed", water.mask().contains(x, z), scene.water(x, z));
                if (scene.water(x, z)) {
                    assertEquals(100, scene.head(x, z));
                    assertTrue("pond depth remains bounded", scene.terrain(x, z) >= 92 && scene.terrain(x, z) < 100);
                } else {
                    assertTrue("dry taper stays above water", scene.terrain(x, z) >= 100);
                }
            }
        }
        assertTrue("shelf contours vary along a straight shore", shelfHeights.size() >= 3);
        assertTrue("deep floor has relief", deepHeights.size() >= 2);
        assertTrue("dry taper varies along a straight shore", taperHeights.size() >= 2);
        Scene reverse = new Scene(settings, (x, z) -> 110).pond(water);
        for (int x = 64; x >= -64; x--) {
            assertEquals("generation order leaves the floor unchanged", scene.terrain(x, -28), reverse.terrain(x, -28));
        }
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
        ChunkWaterRaster backward = new ChunkRasterizer(scene.settings, ChannelFluids.TIER).rasterize(31, 34, scene.natural::applyAsInt, reversed, scene.regions(31, 34));
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                assertEquals(scene.raster(31, 34).waterHead(lx, lz), backward.waterHead(lx, lz));
            }
        }
    }

    @Test
    public void fallsIntoAPondLeaveItsFloor() {
        IntBinaryOperator natural = (x, z) -> 70 + ((x - 16) * (x - 16) + (z - 16) * (z - 16)) / 20;
        Pond pond = pond(21L, PondKind.NATURAL, 75, circle(16, 16, 14, 4), 16, 16, 0);
        HydrologyTier stream = tier("stream", 1.5f, 0.5f, 4, IrisBankStyle.SMOOTH);
        Scene level = new Scene(settings(new IrisHydrology()), natural).pond(pond).reach(line(23L, stream, 0L, 16f, -48f, 16f, 6f, (x, z) -> 75));
        Scene falling = new Scene(settings(new IrisHydrology()), natural).pond(pond).reach(line(23L, stream, 0L, 16f, -48f, 16f, 6f, (x, z) -> 86));
        int checked = 0;
        for (int x = -8; x < 40; x++) {
            for (int z = -8; z < 40; z++) {
                if (falling.role(x, z) != WaterRole.POND) {
                    continue;
                }
                checked++;
                assertEquals("pond floor under the fall at " + x + "," + z, level.terrain(x, z), falling.terrain(x, z));
            }
        }
        assertTrue("pond columns " + checked, checked > 400);
        assertEquals("the stream falls into the pond", 86, falling.head(16, -2));
        assertEquals(74, falling.head(16, 6));
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
                assertEquals("pond level at " + x + "," + z, 74, scene.head(x, z));
                assertTrue("pond floor below the level at " + x + "," + z, scene.terrain(x, z) < 74);
                assertEquals("water", scene.fluid(x, z));
            }
        }
        assertTrue("pond columns " + wet, wet > 400);
        assertEquals("the pond spans four chunks", 4, chunks.size());
        assertTrue("open water stays within the maximum depth", scene.terrain(16, 16) >= 74 - 8 && scene.terrain(16, 16) < 74);
        assertEquals(WaterRole.POND_SHORE, scene.role(16, 30));
    }

    @Test
    public void heightsContinuousAcrossChunkSeam() {
        float startX = -48f;
        float startZ = -20f;
        float endX = 80f;
        float endZ = 60f;
        IntBinaryOperator natural = (x, z) -> 79 + (int) Math.floor(0.35D * perpendicular(x, z, startX, startZ, endX, endZ));
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

        IntBinaryOperator cliff = (x, z) -> (x < 24 ? 99 : 79) - Math.max(0, z - 8);
        Scene waterfall = new Scene(settings, cliff).reach(line(45L, stream, 0L, -24f, 8f, 72f, 8f, (x, z) -> x < 24 ? 99 : 79));
        assertContained("waterfall", waterfall, -16, -8, 64, 24, true);
        assertEquals(WaterRole.WATERFALL, waterfall.role(24, 8));
        assertEquals(98, waterfall.head(22, 8));
        assertEquals(78, waterfall.head(24, 8));
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
        IndexedWindow own = new IndexedWindow(new WindowPlan(key, List.of(), List.of(), List.of(inlet)), ReachIndex.build(key, List.of(), List.of()));
        ChunkRasterizer rasterizer = new ChunkRasterizer(settings, ChannelFluids.TIER);
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
        IndexedWindow own = new IndexedWindow(new WindowPlan(key, List.of(far), List.of(), List.of()), ReachIndex.build(key, List.of(far), List.of()));
        AtomicInteger samples = new AtomicInteger();
        IntBinaryOperator natural = (x, z) -> {
            samples.incrementAndGet();
            return 70;
        };
        ChunkRasterizer rasterizer = new ChunkRasterizer(settings, ChannelFluids.TIER);
        ChunkWaterRaster raster = rasterizer.rasterize(0, 0, natural::applyAsInt, List.of(own), regions(0, 0, List.of()));
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
        assertThrows(IllegalArgumentException.class, () -> rasterizer.rasterize(40, 0, natural::applyAsInt, List.of(own), regions(40, 0, List.of())));
        assertFalse("a chunk on the reach is not empty",
                rasterizer.rasterize(27, 25, natural::applyAsInt, List.of(own), regions(27, 25, List.of())).empty());
    }

    @Test
    public void reachKeepsThePolicyFluidOfItsUpstreamEnd() {
        HydrologySettings settings = settings(new IrisHydrology());
        HydrologyTier stream = tier("stream", 1.5f, 0.5f, 0, IrisBankStyle.SMOOTH);
        Reach outflow = line(61L, stream, 0L, 400f, 403f, 480f, 403f, (x, z) -> 69);
        Reach inflow = line(62L, stream, 0L, 480f, 412f, 400f, 412f, (x, z) -> 69);
        WindowKey key = new WindowKey(0, 0);
        IndexedWindow own = new IndexedWindow(new WindowPlan(key, List.of(outflow, inflow), List.of(), List.of()),
                ReachIndex.build(key, List.of(outflow, inflow), List.of()));
        ChannelFluids volcanic = (x, z) -> x < 440 ? "lava" : null;
        ChunkWaterRaster raster = new ChunkRasterizer(settings, volcanic).rasterize(27, 25, (x, z) -> 70, List.of(own), regions(27, 25, List.of()));
        int lava = 0;
        int water = 0;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                if (!isChannel(raster.role(lx, lz))) {
                    continue;
                }
                boolean startsVolcanic = raster.featureId(lx, lz) == 61L;
                assertEquals("column " + lx + "," + lz, startsVolcanic ? "lava" : "water", raster.fluid(lx, lz));
                if (startsVolcanic) {
                    lava++;
                } else {
                    water++;
                }
            }
        }
        assertTrue("lava columns " + lava + ", water columns " + water, lava > 0 && water > 0);
    }

    @Test
    public void banksNeverDipAwayFromTheWater() {
        HydrologySettings settings = settings(new IrisHydrology());
        Reach river = line(81L, tier("river", 3f, 0.5f, 32, IrisBankStyle.SMOOTH), 0L, 96f, 256f, 416f, 256f, (x, z) -> 69);
        WindowKey key = new WindowKey(0, 0);
        IndexedWindow own = new IndexedWindow(new WindowPlan(key, List.of(river), List.of(), List.of()), ReachIndex.build(key, List.of(river), List.of()));
        NaturalHeights natural = (x, z) -> {
            double away = Math.abs(z - 256);
            if (x < 256) {
                return 70D + 8D * Math.min(1D, away / 8D) + 0.15D * Math.max(0D, away - 8D);
            }
            return 70D + 8D * (1D - Math.exp(-away / 6D)) + (x < 336 ? 0D : 0.15D * away);
        };
        ChunkRasterizer rasterizer = new ChunkRasterizer(settings, ChannelFluids.TIER);
        Map<Long, Integer> terrain = new HashMap<>();
        Map<Long, WaterRole> roles = new HashMap<>();
        for (int chunkZ = 13; chunkZ <= 18; chunkZ++) {
            for (int chunkX = 12; chunkX <= 25; chunkX++) {
                ChunkWaterRaster raster = rasterizer.rasterize(chunkX, chunkZ, natural, List.of(own), regions(chunkX, chunkZ, List.of()));
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        int x = chunkX * 16 + lx;
                        int z = chunkZ * 16 + lz;
                        terrain.put(column(x, z), raster.empty() ? (int) Math.round(natural.height(x, z)) : raster.terrainHeight(lx, lz));
                        roles.put(column(x, z), raster.empty() ? WaterRole.NONE : raster.role(lx, lz));
                    }
                }
            }
        }
        int cut = 0;
        List<String> pits = new ArrayList<>();
        for (int z = 13 * 16 + 1; z < 19 * 16 - 1; z++) {
            for (int x = 12 * 16 + 1; x < 26 * 16 - 1; x++) {
                if (roles.get(column(x, z)) != WaterRole.BANK) {
                    continue;
                }
                int height = terrain.get(column(x, z));
                if (height < (int) Math.round(natural.height(x, z))) {
                    cut++;
                }
                int inward = z > 256 ? z - 1 : z + 1;
                if (z == 256 || roles.get(column(x, inward)) != WaterRole.BANK) {
                    continue;
                }
                int nearer = terrain.get(column(x, inward));
                if (height < nearer) {
                    pits.add(x + "," + z + "@" + height + "<" + nearer);
                }
            }
        }
        assertTrue("cut bank columns " + cut, cut > 400);
        assertTrue("bank dips " + pits.size() + " " + pits.subList(0, Math.min(10, pits.size())), pits.isEmpty());
    }

    private static long column(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
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
    public void receivingRiverOwnsItsFootprintAtAConfluence() {
        HydrologyTier river = tier("river", 8f, 1f, 16, IrisBankStyle.SMOOTH);
        HydrologyTier stream = tier("stream", 2f, 0.5f, 4, IrisBankStyle.SMOOTH);
        Reach below = line(302L, river, 0L, 16f, 8f, 120f, 8f, (x, z) -> 66);
        Reach main = line(301L, river, below.id(), -96f, 8f, 16f, 8f, (x, z) -> 66);
        for (int tributaryLevel : new int[]{70, 66}) {
            Reach tributary = line(303L, stream, below.id(), 16f, -72f, 16f, 8f, (x, z) -> z >= 8f ? 66 : tributaryLevel);
            Scene scene = new Scene(settings(new IrisHydrology()), (x, z) -> 75).reach(main).reach(tributary).reach(below);
            for (int x = -16; x <= 48; x++) {
                for (int z = 0; z <= 16; z++) {
                    assertEquals("river head at " + x + "," + z, 66, scene.head(x, z));
                    if (tributaryLevel == 66 && Math.abs(z - 8) <= 2) {
                        assertTrue("river bed under the tributary mouth at " + x + "," + z, scene.terrain(x, z) <= 63);
                    }
                }
            }
            assertEquals("the tributary keeps its own water upstream", tributaryLevel, scene.head(16, -24));
        }
    }

    @Test
    public void stepsAtTheEdgeOfALowerChannelAreStyledAsFalls() {
        HydrologyTier river = tier("river", 8f, 1f, 16, IrisBankStyle.SMOOTH);
        HydrologyTier stream = tier("stream", 2f, 0.5f, 4, IrisBankStyle.SMOOTH);
        Reach lower = line(302L, river, 0L, -96f, 8f, 120f, 8f, (x, z) -> 66);
        Map<Integer, WaterRole> styles = Map.of(68, WaterRole.CASCADE, 70, WaterRole.CASCADE, 72, WaterRole.WATERFALL);
        for (int level : new int[]{68, 70, 72}) {
            Reach perched = line(303L, stream, 0L, 16f, -72f, 16f, 8f, (x, z) -> level);
            Scene scene = new Scene(settings(new IrisHydrology()), (x, z) -> 80).reach(lower).reach(perched);
            for (int x = 15; x <= 17; x++) {
                assertEquals(level, scene.head(x, -1));
                assertEquals(66, scene.head(x, 0));
                assertEquals("the " + (level - 66) + "-block step onto the lower channel's edge at x " + x, styles.get(level), scene.role(x, 0));
                assertEquals("the perched channel above the step at x " + x, WaterRole.CHANNEL, scene.role(x, -1));
            }
            assertEquals("the lower channel away from the step", WaterRole.CHANNEL, scene.role(40, 0));
            assertEquals("the lower channel inside the perched channel's footprint", WaterRole.CHANNEL, scene.role(16, 6));
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
        ChunkWaterRaster backward = new ChunkRasterizer(scene.settings, ChannelFluids.TIER).rasterize(31, 12, scene.natural::applyAsInt, reversed, scene.regions(31, 12));
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
    public void ledInOutletMeetsItsFeedersAcrossAWindowLine() {
        HydrologyTier river = tier("river", 2f, 0.5f, 4, IrisBankStyle.SMOOTH);
        Reach outlet = ledIn(line(191L, river, 0L, 516f, 200f, 600f, 200f, (x, z) -> Math.max(60, 68 - (int) ((x - 516f) / 10f))), 503f, 200f);
        Reach feeder = line(192L, river, outlet.id(), 516f, 120f, 516f, 200f, (x, z) -> z < 160f ? 70 : 66);
        Pond pond = new Pond(190L, PondKind.NATURAL, 68, 60, 504, 200, 69.5f, circle(488, 200, 16, 1), outlet.id(), 0, "water", 488, 200);
        assertTrue("the lead-in starts in the pond", pond.mask().contains(503, 200));
        assertEquals(new WindowKey(0, 0), WindowKey.containing(503, 200));
        assertEquals(new WindowKey(1, 0), WindowKey.containing(516, 200));
        int feederEnd = feeder.grade()[feeder.grade().length - 1];
        assertTrue("the outlet owner starts above its feeder's end", outlet.grade()[1] > feederEnd);
        Scene scene = new Scene(settings(new IrisHydrology()), (x, z) -> 75).reach(outlet).reach(feeder).pond(pond);
        for (int x = 516; x <= 600; x++) {
            int head = scene.head(x, 200);
            assertNotEquals("centre line is water at x=" + x, Integer.MIN_VALUE, head);
            assertTrue("outlet water at " + head + " above its feeder's end " + feederEnd + " at x=" + x, head <= feederEnd);
        }
    }

    @Test
    public void ledInOutletKeepsItsOwnWidthBelowATributaryAcrossAWindowLine() {
        HydrologyTier narrow = tier("river", 1f, 0.5f, 4, IrisBankStyle.SMOOTH);
        HydrologyTier medium = tier("river", 4f, 0.5f, 4, IrisBankStyle.SMOOTH);
        HydrologyTier wide = tier("river", 8f, 0.5f, 4, IrisBankStyle.SMOOTH);
        assertEquals(new WindowKey(0, 0), WindowKey.containing(508, 200));
        assertEquals(new WindowKey(1, 0), WindowKey.containing(600, 200));

        Scene spout = outletBelowATributary(medium, narrow);
        for (int x = 497; x <= 530; x++) {
            assertTrue("the outlet keeps its half-width 4 below a narrow tributary at x=" + x, spout.water(x, 203) && spout.water(x, 197));
            assertFalse("the outlet is no wider than its half-width 4 at x=" + x, spout.water(x, 205));
        }

        Scene flood = outletBelowATributary(narrow, wide);
        assertFalse("the outlet keeps its half-width 1 below a wide tributary on the lip", flood.water(500, 205));
        assertFalse("the outlet keeps its half-width 1 below a wide tributary 9 blocks below the junction", flood.water(517, 202));
        assertTrue("the outlet centre line stays water", flood.water(517, 200));
    }

    @Test
    public void lakeOutletKeepsItsOwnWidthBelowATributaryAcrossAWindowLine() {
        HydrologyTier narrow = tier("river", 1f, 0.5f, 4, IrisBankStyle.SMOOTH);
        HydrologyTier medium = tier("river", 4f, 0.5f, 4, IrisBankStyle.SMOOTH);
        Reach plain = line(231L, medium, 0L, 508f, 200f, 600f, 200f, (x, z) -> 66);
        ReachStyle[] style = plain.style().clone();
        for (int point = 0; point < style.length; point++) {
            style[point] = plain.xs()[point] - plain.xs()[0] <= 8f ? ReachStyle.POND_OUTLET : style[point];
        }
        Reach outlet = new Reach(plain.id(), plain.tier(), plain.downstreamId(), plain.xs(), plain.zs(), plain.grade(), plain.halfWidth(),
                plain.depth(), style, plain.catchment());
        Reach tributary = line(232L, narrow, outlet.id(), 508f, 120f, 508f, 200f, (x, z) -> 66);
        Pond lake = pond(230L, PondKind.LAKE, 66, circle(480, 200, 24, 4), 480, 200, 0).withOutletReachId(outlet.id());
        assertEquals(new WindowKey(0, 0), WindowKey.containing(508, 200));
        assertEquals(new WindowKey(1, 0), WindowKey.containing(600, 200));
        Scene scene = new Scene(settings(new IrisHydrology()), (x, z) -> 75).lake(lake).reach(outlet).reach(tributary);
        for (int x = 510; x <= 530; x++) {
            assertTrue("the lake outlet keeps its half-width 4 below a narrow tributary at x=" + x, scene.water(x, 203) && scene.water(x, 197));
            assertFalse("the lake outlet is no wider than its half-width 4 at x=" + x, scene.water(x, 205));
        }
    }

    @Test
    public void skippedLeadInKeepsTheOutletJunctionAtItsFirstVertex() {
        HydrologyTier river = tier("river", 2f, 0.5f, 4, IrisBankStyle.SMOOTH);
        Reach outlet = line(201L, river, 0L, 255f, 200f, 335f, 200f, (x, z) -> 68);
        Reach feeder = line(202L, river, outlet.id(), 255f, 140f, 255f, 200f, (x, z) -> 66);
        Reach upstream = line(203L, river, feeder.id(), 255f, 60f, 255f, 140f, (x, z) -> z < 100f ? 70 : 62);
        Pond pond = new Pond(200L, PondKind.NATURAL, 68, 60, 235, 200, 69.5f, circle(235, 200, 8, 1), outlet.id(), 0, "water", 235, 200);
        assertFalse("the outlet starts off the pond", pond.mask().contains(255, 200));
        assertNotEquals("the first two outlet vertices straddle a corner-block line",
                Math.floorDiv((int) Math.floor(outlet.xs()[0]) + WindowKey.SIZE / 2, WindowKey.SIZE),
                Math.floorDiv((int) Math.floor(outlet.xs()[1]) + WindowKey.SIZE / 2, WindowKey.SIZE));
        Scene scene = new Scene(settings(new IrisHydrology()), (x, z) -> 75).reach(outlet).reach(feeder).reach(upstream).pond(pond);
        for (int x = 260; x <= 335; x++) {
            int head = scene.head(x, 200);
            assertNotEquals("centre line is water at x=" + x, Integer.MIN_VALUE, head);
            assertTrue("outlet water at " + head + " above the resolved feeder end 62 at x=" + x, head <= 62);
        }
    }

    @Test
    public void crossWindowFeederSettlesIntoALowerDownstreamStart() {
        HydrologyTier narrow = tier("river", 2f, 0.5f, 4, IrisBankStyle.SMOOTH);
        HydrologyTier wide = tier("river", 3f, 0.5f, 4, IrisBankStyle.SMOOTH);
        Reach downstream = line(222L, wide, 0L, 504f, 200f, 600f, 200f, (x, z) -> Math.max(56, 62 - (int) ((x - 504f) / 8f)));
        Reach feeder = line(221L, narrow, downstream.id(), 440f, 200f, 504f, 200f, (x, z) -> x < 460f ? 68 : 66);
        int start = downstream.grade()[0];
        int last = feeder.xs().length - 1;
        assertEquals(new WindowKey(0, 0), WindowKey.containing(504, 200));
        assertEquals(new WindowKey(1, 0), WindowKey.containing(600, 200));
        assertTrue("the downstream owner starts below the feeder's end", start < feeder.grade()[last]);
        float pool = downstream.halfWidth()[0] + feeder.halfWidth()[last] + ConfluenceGrader.POOL_MARGIN;
        Scene scene = new Scene(settings(new IrisHydrology()), (x, z) -> 75).reach(feeder).reach(downstream);
        int previous = Integer.MAX_VALUE;
        for (int x = 440; x <= 600; x++) {
            int head = scene.head(x, 200);
            assertNotEquals("centre line is water at x=" + x, Integer.MIN_VALUE, head);
            assertTrue("water rises from " + previous + " to " + head + " at x=" + x, head <= previous);
            if (x >= 504f - pool && x <= 504) {
                assertTrue("feeder water at " + head + " above the downstream start " + start + " at x=" + x, head <= start);
            }
            previous = head;
        }
        assertEquals("the feeder keeps its own water upstream", 68, scene.head(450, 200));
        assertWindowOrderFree(scene, 27, 37, 12);
    }

    @Test
    public void clampedSeamDropsAreStyledAsFalls() {
        HydrologyTier narrow = tier("river", 2f, 0.5f, 4, IrisBankStyle.SMOOTH);
        HydrologyTier wide = tier("river", 3f, 0.5f, 4, IrisBankStyle.SMOOTH);
        assertEquals(6, narrow.waterfallMinimumDrop());
        Reach low = line(232L, wide, 0L, 504f, 200f, 600f, 200f, (x, z) -> 62);
        Reach hanging = line(231L, narrow, low.id(), 440f, 200f, 504f, 200f, (x, z) -> 70);
        Scene falls = new Scene(settings(new IrisHydrology()), (x, z) -> 90).reach(hanging).reach(low);
        int bank = (int) Math.ceil(low.xs()[0] - low.halfWidth()[0]);
        assertTrue("the feeder stands more than the margin lowering above the downstream start",
                70 - 62 > ConfluenceGrader.MARGIN_LOWERING);
        assertEquals("the feeder keeps its level beside the receiving bank", 70, falls.head(bank - 1, 200));
        assertEquals(62, falls.head(bank, 200));
        assertEquals("an 8-block drop is a waterfall at the receiving bank", WaterRole.WATERFALL, falls.role(bank, 200));
        Reach steep = line(233L, narrow, low.id(), 440f, 200f, 504f, 200f, (x, z) -> x >= 496f ? 66 : Math.min(80, 66 + (int) ((496f - x) / 2f)));
        Scene cascade = new Scene(settings(new IrisHydrology()), (x, z) -> 90).reach(steep).reach(low);
        assertEquals(66, cascade.head(496, 200));
        assertEquals(62, cascade.head(497, 200));
        assertEquals("a 4-block settled drop is a cascade", WaterRole.CASCADE, cascade.role(497, 200));
        Reach stepped = line(235L, wide, 0L, 504f, 200f, 600f, 200f, (x, z) -> x < 520f ? 70 : 63);
        Reach level = line(234L, narrow, stepped.id(), 440f, 200f, 504f, 200f, (x, z) -> 66);
        assertEquals(ReachStyle.WATERFALL, stepped.style()[8]);
        Scene clamped = new Scene(settings(new IrisHydrology()), (x, z) -> 90).reach(level).reach(stepped);
        assertEquals(66, clamped.head(518, 200));
        assertEquals(62, clamped.head(519, 200));
        assertEquals("a waterfall clamped to a 3-block drop is a cascade", WaterRole.CASCADE, clamped.role(519, 200));
    }

    @Test
    public void crossWindowFeederSettlesToItsDownstreamOutletsPond() {
        HydrologyTier river = tier("river", 2f, 0.5f, 4, IrisBankStyle.SMOOTH);
        Reach outlet = ledIn(line(281L, river, 0L, 508f, 200f, 600f, 200f, (x, z) -> 68), 503f, 200f);
        Reach feeder = line(282L, river, outlet.id(), 508f, 120f, 508f, 200f, (x, z) -> 68);
        Pond pond = new Pond(280L, PondKind.NATURAL, 64, 56, 504, 200, 65.5f, circle(488, 200, 16, 1), outlet.id(), 0, "water", 488, 200);
        assertTrue("the lead-in starts in the pond", pond.mask().contains(503, 200));
        assertEquals(new WindowKey(0, 0), WindowKey.containing(508, 200));
        assertEquals(new WindowKey(1, 0), WindowKey.containing(600, 200));
        assertTrue("the pond sits below the outlet's planned start", pond.level() < outlet.grade()[0]);
        Scene scene = new Scene(settings(new IrisHydrology()), (x, z) -> 75).reach(outlet).reach(feeder).pond(pond);
        int previous = Integer.MAX_VALUE;
        for (int z = 120; z <= 199; z++) {
            int head = scene.head(508, z);
            assertNotEquals("feeder centre line is water at z=" + z, Integer.MIN_VALUE, head);
            assertTrue("water rises from " + previous + " to " + head + " at z=" + z, head <= previous);
            if (z >= 194) {
                assertTrue("feeder water at " + head + " above the outlet's pond " + pond.level() + " at z=" + z, head <= pond.level());
            }
            previous = head;
        }
        for (int x = 508; x <= 600; x++) {
            assertTrue("outlet water above its pond at x=" + x, scene.head(x, 200) <= pond.level());
        }
    }

    @Test
    public void fullyPooledShortFeederKeepsItsStartForItsOwnFeeders() {
        HydrologyTier narrow = tier("river", 2f, 0.5f, 4, IrisBankStyle.SMOOTH);
        HydrologyTier wide = tier("river", 3f, 0.5f, 4, IrisBankStyle.SMOOTH);
        Reach low = line(292L, wide, 0L, 504f, 200f, 600f, 200f, (x, z) -> 62);
        Reach stub = line(291L, narrow, low.id(), 498f, 200f, 504f, 200f, (x, z) -> 66);
        Reach upstream = line(290L, narrow, stub.id(), 440f, 200f, 498f, 200f, (x, z) -> 66);
        assertEquals(4, stub.xs().length);
        Scene scene = new Scene(settings(new IrisHydrology()), (x, z) -> 90).reach(upstream).reach(stub).reach(low);
        assertEquals("the stub keeps its start level for the reach that feeds it", 66, scene.head(498, 200));
        assertEquals(62, scene.head(499, 200));
        assertEquals("the stub drops inside itself as a cascade", WaterRole.CASCADE, scene.role(499, 200));
        int previous = Integer.MAX_VALUE;
        for (int x = 440; x <= 600; x++) {
            int head = scene.head(x, 200);
            assertNotEquals("centre line is water at x=" + x, Integer.MIN_VALUE, head);
            assertTrue("water rises from " + previous + " to " + head + " at x=" + x, head <= previous);
            previous = head;
        }
    }

    @Test
    public void crossWindowFedStartOneBlockAboveTheJunctionJoinsIt() {
        HydrologyTier narrow = tier("river", 2f, 0.5f, 4, IrisBankStyle.SMOOTH);
        HydrologyTier wide = tier("river", 3f, 0.5f, 4, IrisBankStyle.SMOOTH);
        Reach low = line(342L, wide, 0L, 504f, 200f, 600f, 200f, (x, z) -> 61);
        Reach stub = line(341L, narrow, low.id(), 498f, 200f, 504f, 200f, (x, z) -> 62);
        Reach upstream = line(340L, narrow, stub.id(), 440f, 200f, 498f, 200f, (x, z) -> 62);
        assertEquals(new WindowKey(0, 0), WindowKey.containing(504, 200));
        assertEquals(new WindowKey(1, 0), WindowKey.containing(600, 200));
        Scene scene = new Scene(settings(new IrisHydrology()), (x, z) -> 90).reach(upstream).reach(stub).reach(low);
        assertEquals("the stub's start one block above the junction joins it", 60, scene.head(498, 200));
        int previous = Integer.MAX_VALUE;
        for (int x = 440; x <= 600; x++) {
            int head = scene.head(x, 200);
            assertNotEquals("centre line is water at x=" + x, Integer.MIN_VALUE, head);
            assertTrue("water rises from " + previous + " to " + head + " at x=" + x, head <= previous);
            if (head < previous && previous != Integer.MAX_VALUE) {
                assertEquals("the step at x=" + x + " is styled", WaterRole.CASCADE, scene.role(x, 200));
            }
            previous = head;
        }
        assertWindowOrderFree(scene, 27, 37, 12);
    }

    @Test
    public void crossWindowSettlingKeepsAStepAtTheEndOfAnOutletZone() {
        HydrologyTier narrow = tier("river", 2f, 0.5f, 4, IrisBankStyle.SMOOTH);
        HydrologyTier wide = tier("river", 3f, 0.5f, 4, IrisBankStyle.SMOOTH);
        Reach low = line(332L, wide, 0L, 504f, 200f, 600f, 200f, (x, z) -> 62);
        Reach drawn = line(331L, narrow, low.id(), 492f, 200f, 504f, 200f, (x, z) -> x < 500f ? 66 : 65);
        boolean[] zone = new boolean[drawn.xs().length];
        for (int point = 0; point < zone.length; point++) {
            zone[point] = drawn.xs()[point] - drawn.xs()[0] <= 8f;
        }
        ReachStyle[] style = ReachShaper.styles(drawn.grade(), new boolean[zone.length], zone, narrow.waterfallMinimumDrop(), SEA_LEVEL);
        assertEquals("the last outlet zone point steps down one block, shown as two", ReachStyle.CASCADE, style[4]);
        assertTrue("the step lies at the end of the outlet zone", zone[4] && !zone[5]);
        Reach outlet = new Reach(drawn.id(), narrow, drawn.downstreamId(), drawn.xs(), drawn.zs(), drawn.grade(), drawn.halfWidth(), drawn.depth(),
                style, drawn.catchment());
        Scene scene = new Scene(settings(new IrisHydrology()), (x, z) -> 75).reach(outlet).reach(low);
        for (int x = 492; x <= 498; x++) {
            assertEquals("outlet zone water at x=" + x, 66, scene.head(x, 200));
        }
        assertEquals("the outlet zone keeps the step at its last point", 64, scene.head(500, 200));
        assertEquals(62, scene.head(502, 200));
        assertWindowOrderFree(scene, 30, 32, 12);
    }

    @Test
    public void crossWindowSettlingKeepsAnOutletsHeldLipAtThePondLevel() {
        HydrologyTier narrow = tier("river", 2f, 0.5f, 4, IrisBankStyle.SMOOTH);
        HydrologyTier wide = tier("river", 3f, 0.5f, 4, IrisBankStyle.SMOOTH);
        Reach low = line(322L, wide, 0L, 504f, 200f, 600f, 200f, (x, z) -> 62);
        Reach shore = line(321L, narrow, low.id(), 486f, 200f, 504f, 200f, (x, z) -> 66);
        ReachStyle[] style = shore.style().clone();
        for (int point = 0; point < style.length; point++) {
            style[point] = shore.xs()[point] - shore.xs()[0] <= 8f ? ReachStyle.POND_OUTLET : style[point];
        }
        Reach outlet = new Reach(shore.id(), shore.tier(), shore.downstreamId(), shore.xs(), shore.zs(), shore.grade(), shore.halfWidth(),
                shore.depth(), style, shore.catchment());
        Pond pond = new Pond(320L, PondKind.NATURAL, 66, 58, 490, 202, 67.5f, circle(490, 210, 8, 1), outlet.id(), 0, "water", 490, 210);
        assertFalse("the outlet is not led in", pond.mask().contains(486, 200));
        assertEquals("the lip vertices past the outlet zone run", ReachStyle.RUN, outlet.style()[7]);
        Scene scene = new Scene(settings(new IrisHydrology()), (x, z) -> 75).reach(outlet).reach(low).pond(pond);
        for (int x = 487; x <= 500; x++) {
            assertEquals("outlet water along its pond's lip at x=" + x, pond.level(), scene.head(x, 200));
        }
        assertEquals("the outlet drops into the lower river past its lip", WaterRole.CASCADE, scene.role(501, 200));
    }

    @Test
    public void crossWindowSettlingHoldsAPondsLipButNotALakes() {
        HydrologyTier narrow = tier("river", 2f, 0.5f, 4, IrisBankStyle.SMOOTH);
        HydrologyTier wide = tier("river", 3f, 0.5f, 4, IrisBankStyle.SMOOTH);
        Reach low = line(342L, wide, 0L, 504f, 200f, 600f, 200f, (x, z) -> 62);
        Reach shore = line(341L, narrow, low.id(), 486f, 200f, 504f, 200f, (x, z) -> 66);
        ReachStyle[] style = shore.style().clone();
        for (int point = 0; point < style.length; point++) {
            style[point] = shore.xs()[point] - shore.xs()[0] <= 8f ? ReachStyle.POND_OUTLET : style[point];
        }
        Reach outlet = new Reach(shore.id(), shore.tier(), shore.downstreamId(), shore.xs(), shore.zs(), shore.grade(), shore.halfWidth(),
                shore.depth(), style, shore.catchment());
        Pond pond = new Pond(340L, PondKind.NATURAL, 66, 58, 490, 202, 67.5f, circle(490, 210, 8, 1), outlet.id(), 0, "water", 490, 210);
        Pond lake = new Pond(340L, PondKind.LAKE, 66, 58, 490, 202, 67.5f, circle(490, 210, 8, 1), outlet.id(), 0, "water", 490, 210);
        Scene held = new Scene(settings(new IrisHydrology()), (x, z) -> 75).reach(outlet).reach(low).pond(pond);
        Scene free = new Scene(settings(new IrisHydrology()), (x, z) -> 75).reach(outlet).reach(low).lake(lake);
        for (int x = 497; x <= 500; x++) {
            assertEquals("a pond's outlet stays at its level along the lip at x=" + x, pond.level(), held.head(x, 200));
            assertEquals("a lake's outlet settles to the lower river along the lip at x=" + x, low.grade()[0], free.head(x, 200));
        }
    }

    @Test
    public void sharedOutletWithoutALeadInClampsToTheLastPondInItsPlan() {
        HydrologyTier river = tier("river", 2f, 0.5f, 4, IrisBankStyle.SMOOTH);
        Reach outlet = line(311L, river, 0L, 300f, 200f, 380f, 200f, (x, z) -> 68);
        Pond low = new Pond(310L, PondKind.NATURAL, 64, 56, 290, 200, 65.5f, circle(280, 200, 10, 1), outlet.id(), 0, "water", 280, 200);
        Pond high = new Pond(312L, PondKind.NATURAL, 68, 60, 300, 224, 69.5f, circle(300, 230, 6, 1), outlet.id(), 0, "water", 300, 230);
        assertFalse(low.mask().contains(300, 200));
        assertFalse(high.mask().contains(300, 200));
        for (boolean highLast : new boolean[]{true, false}) {
            Scene scene = new Scene(settings(new IrisHydrology()), (x, z) -> 75).reach(outlet);
            if (highLast) {
                scene.pond(low).pond(high);
            } else {
                scene.pond(high).pond(low);
            }
            int expected = highLast ? high.level() : low.level();
            for (int x = 302; x <= 320; x++) {
                assertEquals("outlet water with the " + (highLast ? "higher" : "lower") + " pond last at x=" + x, expected, scene.head(x, 200));
            }
        }
    }

    @Test
    public void aDownstreamReachOwnedByTwoWindowsFailsClosed() {
        HydrologyTier river = tier("river", 2f, 0.5f, 4, IrisBankStyle.SMOOTH);
        Reach downstream = line(332L, river, 0L, 504f, 200f, 600f, 200f, (x, z) -> 62);
        Reach feeder = line(331L, river, downstream.id(), 440f, 200f, 504f, 200f, (x, z) -> 66);
        List<IndexedWindow> windows = new ArrayList<>();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                WindowKey key = new WindowKey(dx, dz);
                List<Reach> owned = key.equals(new WindowKey(0, 0)) ? List.of(feeder)
                        : key.equals(new WindowKey(1, 0)) || key.equals(new WindowKey(1, -1)) ? List.of(downstream) : List.of();
                windows.add(new IndexedWindow(new WindowPlan(key, owned, List.of(), List.of()), ReachIndex.build(key, owned, List.of())));
            }
        }
        ChunkRasterizer rasterizer = new ChunkRasterizer(settings(new IrisHydrology()), ChannelFluids.TIER);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> rasterizer.rasterize(31, 12, (x, z) -> 75, windows, regions(31, 12, List.of())));
        assertTrue(failure.getMessage(), failure.getMessage().contains("Reach " + downstream.id() + " is owned by window"));
    }

    @Test
    public void crossWindowJunctionDropsSpreadOnTiersWithoutCascades() {
        HydrologyTier gentle = new HydrologyTier("stream", 0f, 2f, 2f, 0f, 0.5f, 1, false, 6, 4, IrisBankStyle.SMOOTH,
                new HydrologyTier.Estuary(48, 2.5f), new HydrologyTier.Delta(0, 35f, 64), new IrisHydrologyMaterials(), "water", List.of());
        HydrologyTier wide = tier("river", 3f, 0.5f, 4, IrisBankStyle.SMOOTH);
        Reach low = line(262L, wide, 0L, 504f, 200f, 600f, 200f, (x, z) -> 62);
        Reach steep = line(261L, gentle, low.id(), 440f, 200f, 504f, 200f, (x, z) -> x >= 496f ? 66 : Math.min(80, 66 + (int) ((496f - x) / 2f)));
        Scene scene = new Scene(settings(new IrisHydrology()), (x, z) -> 90).reach(steep).reach(low);
        for (int x = 441; x <= 504; x++) {
            int drop = scene.head(x - 1, 200) - scene.head(x, 200);
            assertTrue("a " + drop + "-block step at x=" + x, drop >= 0 && drop <= 2);
            assertEquals("channel role at x=" + x, WaterRole.CHANNEL, scene.role(x, 200));
        }
    }

    @Test
    public void crossWindowWidthBlendKeepsAnAlreadyFlaredDownstream() {
        HydrologyTier narrow = tier("river", 2f, 0.5f, 4, IrisBankStyle.SMOOTH);
        HydrologyTier wide = tier("river", 8f, 0.5f, 4, IrisBankStyle.SMOOTH);
        Reach plain = line(241L, narrow, 0L, 504f, 200f, 600f, 200f, (x, z) -> 66);
        Reach feeder = line(242L, wide, plain.id(), 440f, 200f, 504f, 200f, (x, z) -> 66);
        Reach flared = flared(plain, feeder.halfWidth()[feeder.xs().length - 1]);
        Scene unflared = new Scene(settings(new IrisHydrology()), (x, z) -> 75).reach(feeder).reach(plain);
        Scene preflared = new Scene(settings(new IrisHydrology()), (x, z) -> 75).reach(feeder).reach(flared);
        assertTrue("half the feeder's width remains 8 blocks below the junction", unflared.water(512, 204));
        assertFalse("the flare is linear 8 blocks below the junction", unflared.water(512, 206));
        for (int x = 504; x <= 530; x++) {
            for (int z = 188; z <= 212; z++) {
                assertEquals("water head at " + x + "," + z, unflared.head(x, z), preflared.head(x, z));
                assertEquals("terrain at " + x + "," + z, unflared.terrain(x, z), preflared.terrain(x, z));
            }
        }
    }

    @Test
    public void sharedOutletClampsToThePondItLeadsOutOf() {
        HydrologyTier river = tier("river", 2f, 0.5f, 4, IrisBankStyle.SMOOTH);
        Reach outlet = ledIn(line(251L, river, 0L, 516f, 200f, 600f, 200f, (x, z) -> Math.max(60, 68 - (int) ((x - 516f) / 10f))), 503f, 200f);
        Pond source = new Pond(250L, PondKind.NATURAL, 68, 60, 504, 200, 69.5f, circle(488, 200, 16, 1), outlet.id(), 0, "water", 488, 200);
        Pond shared = new Pond(252L, PondKind.NATURAL, 64, 58, 540, 234, 65.5f, circle(540, 240, 6, 1), outlet.id(), 0, "water", 540, 240);
        assertTrue("the lead-in starts in the source pond", source.mask().contains(503, 200));
        assertFalse("the lead-in starts off the shared pond", shared.mask().contains(503, 200));
        Scene scene = new Scene(settings(new IrisHydrology()), (x, z) -> 75).reach(outlet).pond(source).pond(shared);
        for (int x = 517; x <= 524; x++) {
            assertEquals("outlet water at the level of the pond it leads out of at x=" + x, 68, scene.head(x, 200));
        }
    }

    @Test
    public void everyBankStyleRasterizes() {
        IrisHydrology config = new IrisHydrology();
        config.getDeformation().setBankSlope(0D).setBankSteepFactor(0D).setBankNoise(0D);
        HydrologySettings settings = settings(config);
        IntBinaryOperator natural = (x, z) -> z >= 8 ? 69 + (z - 8) : 67 - (8 - z) / 4;
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
                assertTrue(style + " bank between water and natural at z=" + z + ": " + height, height >= 68 && height <= natural.applyAsInt(8, z));
                assertTrue(style + " bank never falls toward the ground at z=" + z, outward == 1 || height >= cut[outward - 2]);
                cut[outward - 1] = height;
                int levee = scene.terrain(8, 6 - outward);
                int ground = natural.applyAsInt(8, 6 - outward);
                assertTrue(style + " levee within the fill limit at z=" + (6 - outward) + ": " + levee,
                        levee >= ground && levee <= ground + settings.deformation().maxFill());
            }
            assertEquals(style + " meets natural at the band edge", natural.applyAsInt(8, 26), cut[15]);
            assertTrue(style + " levee holds the water", scene.terrain(8, 5) >= 69);
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
            assertEquals("cliff bench at outward " + (index + 1), 68, cliff[index]);
        }
        assertEquals("cliff wall reaches natural", natural.applyAsInt(8, 22), cliff[11]);
        int[] terraced = cuts.get(IrisBankStyle.TERRACED);
        boolean level = false;
        for (int index = 0; index < 15; index++) {
            if (terraced[index] < natural.applyAsInt(8, 11 + index)) {
                assertEquals("terrace step height at outward " + (index + 1), 0, (terraced[index] - 68) % 3);
            }
            level |= terraced[index] > 68 && terraced[index] == terraced[index + 1];
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
        IntBinaryOperator slope = (x, z) -> 79 - Math.max(0, z - 8);
        Scene scene = new Scene(settings, slope).reach(perchedReach);
        for (int x = -24; x < 40; x++) {
            assertTrue("channel water at x=" + x, scene.water(x, 9));
            assertFalse("dry downhill column at x=" + x, scene.water(x, 10));
            assertEquals("natural sits below the water at x=" + x, 77, slope.applyAsInt(x, 10));
            assertEquals("berm one block above the water at x=" + x, 79, scene.terrain(x, 10));
            assertEquals(WaterRole.SHORE, scene.role(x, 10));
            assertEquals(perchedReach.id(), scene.feature(x, 10));
            assertEquals(stream, scene.tier(x, 10));
            assertEquals("only the edge column is raised at x=" + x, slope.applyAsInt(x, 11), scene.terrain(x, 11));
        }
        assertEquals(0, scene.leaks(-24, -8, 40, 24));

        Scene steep = new Scene(settings, (x, z) -> z > 9 ? 60 : 80).reach(perchedReach);
        assertEquals("past the fill limit the guard berms to the water itself", 78, steep.terrain(0, 10));
        assertTrue("the guard rises past the fill limit", steep.terrain(0, 10) > 60 + maxFill);
        assertEquals(0, steep.leaks(-24, -8, 40, 24));
        Scene below = new Scene(settings, (x, z) -> z > 9 ? 30 : 80).reach(perchedReach);
        assertEquals("a column under the sea surface is bermed when the water stands above it", 78, below.terrain(0, 10));
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
                assertEquals("dam crest one block above the level at " + x + "," + z, 95, scene.terrain(x, z));
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
        for (int bankBand : new int[]{0, 32}) {
            assertCoastalFall(bankBand);
        }
    }

    private static void assertCoastalFall(int bankBand) {
        String banks = "bankBand " + bankBand + ": ";
        IntBinaryOperator coast = (x, z) -> x < 40 ? 60 : SEA_LEVEL - 10;
        HydrologyTier stream = tier("stream", 1.5f, 0.5f, bankBand, IrisBankStyle.SMOOTH);
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
        assertEquals(banks + "the lip carries the fall", WaterRole.WATERFALL, scene.role(38, 8));
        assertEquals(banks + "lip head", 58, scene.head(38, 8));
        int minZ = -bankBand;
        int maxZ = 16 + bankBand;
        int mouths = 0;
        for (int x = -20; x <= 56; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (scene.role(x, z) == WaterRole.MOUTH) {
                    mouths++;
                    assertEquals(banks + "mouth at sea level at " + x + "," + z, SEA_LEVEL, scene.head(x, z));
                    if (x >= 40) {
                        assertEquals(banks + "the fall leaves the sea floor under the mouth at " + x + "," + z, coast.applyAsInt(x, z), scene.terrain(x, z));
                    }
                }
                if (scene.water(x, z)) {
                    continue;
                }
                int ground = scene.terrain(x, z);
                if (x < 40) {
                    assertTrue(banks + "dry shore at " + x + "," + z + " is cut to " + ground + ", below the sea surface", ground >= SEA_LEVEL);
                } else {
                    assertEquals(banks + "no berm is built in the sea at " + x + "," + z, coast.applyAsInt(x, z), ground);
                }
            }
        }
        assertTrue(banks + "mouth columns " + mouths, mouths > 20);
        assertEquals(banks + "leaks", 0, scene.leaks(-20, minZ, 56, maxZ));
    }

    @Test
    public void mouthColumnsUseTheSeaFluid() {
        IntBinaryOperator coast = (x, z) -> x < 40 ? 44 : SEA_LEVEL - 10;
        HydrologyTier stream = tier("stream", 1.5f, 0.5f, 8, IrisBankStyle.SMOOTH);
        Reach river = mouthed(line(126L, stream, 0L, -20f, 8f, 56f, 8f, (x, z) -> x < 40f ? 42 : SEA_LEVEL), 40f);
        Scene scene = new Scene(settings(new IrisHydrology()), coast).reach(river);
        int mouths = 0;
        int channels = 0;
        for (int x = -16; x <= 52; x++) {
            for (int z = 0; z <= 16; z++) {
                WaterRole role = scene.role(x, z);
                if (role == WaterRole.MOUTH) {
                    mouths++;
                    assertEquals("mouth at " + x + "," + z, HydrologyCaveCell.SEA_FLUID, scene.fluid(x, z));
                } else if (role == WaterRole.CHANNEL) {
                    channels++;
                    assertEquals("channel at " + x + "," + z, "water", scene.fluid(x, z));
                }
            }
        }
        assertTrue("mouth columns " + mouths + ", channel columns " + channels, mouths > 20 && channels > 20);
    }

    @Test
    public void deltaChannelsRenderAsSeaLevelMouthWaterWithoutAGapAtTheBranch() {
        assertDeltaRendersAtSeaLevel(DeltaCoast.settings(2), DeltaCoast::ground, false);
        assertDeltaRendersAtSeaLevel(DeltaCoast.settings(2), DeltaCoast::cliffBacked, true);
        assertDeltaRendersAtSeaLevel(DeltaCoast.ungroupedSettings(2), DeltaCoast::cliffBacked, true);
        assertEquals("grouping moves no sea-level head upstream", firstSeaLevelHead(DeltaCoast.ungroupedSettings(2)), firstSeaLevelHead(DeltaCoast.settings(2)), 0f);
    }

    private static float firstSeaLevelHead(HydrologySettings settings) {
        Reach river = DeltaCoast.river(DeltaCoast.shape(settings, 0, 0, List.of(), DeltaCoast.NO_NEIGHBOUR, DeltaCoast.SEED, DeltaCoast::cliffBacked), DeltaCoast.ROW);
        int point = 0;
        while (WaterHeads.snap(river.grade()[point], SEA_LEVEL) > SEA_LEVEL) {
            point++;
        }
        return river.xs()[point];
    }

    private static void assertDeltaRendersAtSeaLevel(HydrologySettings settings, DoubleBinaryOperator ground, boolean raisedAboveTheEstuary) {
        ShapedNetwork shaped = DeltaCoast.shape(settings, 0, 0, List.of(), DeltaCoast.NO_NEIGHBOUR, DeltaCoast.SEED, ground);
        Reach river = DeltaCoast.river(shaped, DeltaCoast.ROW);
        List<Reach> fan = DeltaCoast.deltaChannels(shaped);
        assertEquals("two delta channels", 2, fan.size());
        IntBinaryOperator natural = (x, z) -> (int) Math.round(ground.applyAsDouble(x, z));
        Scene plain = new Scene(settings, natural).reach(river);
        Scene delta = new Scene(settings, natural).reach(river);
        for (Reach channel : fan) {
            delta.reach(channel);
        }
        int estuary = river.style().length - 1;
        while (estuary > 0 && river.style()[estuary - 1] == ReachStyle.MOUTH) {
            estuary--;
        }
        int minX = (int) Math.floor(river.xs()[estuary]) - 24;
        int maxX = DeltaCoast.SEA_X + 8;
        int minZ = DeltaCoast.ROW - 40;
        int maxZ = DeltaCoast.ROW + 40;
        int added = 0;
        int raised = 0;
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                if (inDelta(fan, x, z)) {
                    assertEquals("delta water at " + x + "," + z, WaterRole.MOUTH, delta.role(x, z));
                    assertEquals("delta head at " + x + "," + z, SEA_LEVEL, delta.head(x, z));
                    assertEquals("delta fluid at " + x + "," + z, HydrologyCaveCell.SEA_FLUID, delta.fluid(x, z));
                    added += plain.water(x, z) ? 0 : 1;
                }
                if (plain.water(x, z)) {
                    assertTrue("the river stays wet at " + x + "," + z, delta.water(x, z));
                    if (plain.head(x, z) > SEA_LEVEL) {
                        assertEquals("the river above its estuary keeps its head at " + x + "," + z, plain.head(x, z), delta.head(x, z));
                        raised += Math.hypot(x - fan.get(0).xs()[0], z - fan.get(0).zs()[0]) <= 2D * fan.get(0).halfWidth()[0] + 8D ? 1 : 0;
                    }
                }
            }
        }
        assertTrue("delta water beyond the estuary " + added, added > 100);
        assertTrue("river water above the sea next to the branch " + raised, !raisedAboveTheEstuary || raised > 0);
        assertEquals("delta leaks", 0, delta.leaks(minX, minZ, maxX, maxZ));
        int above = estuary;
        while (above > 0 && river.xs()[estuary] - river.xs()[above] < 16f) {
            above--;
        }
        Set<Long> reached = wetFrom(delta, Math.round(river.xs()[above]), Math.round(river.zs()[above]), minX, minZ, maxX, maxZ);
        for (Reach channel : fan) {
            for (int point = 0; point < channel.xs().length; point++) {
                int x = Math.round(channel.xs()[point]);
                int z = Math.round(channel.zs()[point]);
                assertTrue(channel + " point " + point + " joins the river's water", reached.contains(pack(x, z)));
            }
        }
    }

    private static boolean inDelta(List<Reach> fan, int x, int z) {
        for (Reach channel : fan) {
            for (int segment = 0; segment + 1 < channel.xs().length; segment++) {
                double ax = channel.xs()[segment];
                double az = channel.zs()[segment];
                double dx = channel.xs()[segment + 1] - ax;
                double dz = channel.zs()[segment + 1] - az;
                double length = dx * dx + dz * dz;
                double t = length == 0D ? 0D : Math.clamp(((x - ax) * dx + (z - az) * dz) / length, 0D, 1D);
                if (Math.hypot(x - (ax + t * dx), z - (az + t * dz)) <= channel.halfWidth()[segment]) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<Long> wetFrom(Scene scene, int startX, int startZ, int minX, int minZ, int maxX, int maxZ) {
        Set<Long> reached = new HashSet<>();
        List<int[]> frontier = new ArrayList<>();
        assertTrue("the river is wet at " + startX + "," + startZ, scene.water(startX, startZ));
        reached.add(pack(startX, startZ));
        frontier.add(new int[]{startX, startZ});
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int index = 0; index < frontier.size(); index++) {
            int[] column = frontier.get(index);
            for (int[] side : sides) {
                int x = column[0] + side[0];
                int z = column[1] + side[1];
                if (x >= minX && x <= maxX && z >= minZ && z <= maxZ && scene.water(x, z) && reached.add(pack(x, z))) {
                    frontier.add(new int[]{x, z});
                }
            }
        }
        return reached;
    }

    @Test
    public void lavaMouthMeetsTheSeaThroughTheSeam() {
        HydrologySettings settings = settings(lavaAndCrust());
        IntBinaryOperator coast = (x, z) -> x < 40 ? 44 : SEA_LEVEL - 3;
        Reach river = mouthed(line(131L, lavaTier(), 0L, -20f, 8f, 56f, 8f, (x, z) -> x < 40f ? 42 : SEA_LEVEL), 40f);
        assertLavaSealedFromTheSea(new Scene(settings, coast).reach(river), settings, coast);
    }

    @Test
    public void lavaFallingIntoItsEstuaryFallsAsTheSeam() {
        HydrologySettings settings = settings(lavaAndCrust());
        IntBinaryOperator coast = (x, z) -> x < 40 ? 46 : SEA_LEVEL - 3;
        Reach river = mouthed(line(132L, lavaTier(), 0L, -20f, 8f, 56f, 8f, (x, z) -> x < 40f ? SEA_LEVEL + 4 : SEA_LEVEL), 40f);
        Scene scene = new Scene(settings, coast).reach(river);
        assertLavaSealedFromTheSea(scene, settings, coast);
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int ontoTheSea = 0;
        for (int x = -16; x <= 52; x++) {
            for (int z = -8; z <= 24; z++) {
                if (!scene.water(x, z)) {
                    continue;
                }
                int head = scene.head(x, z);
                int[] source = null;
                for (int[] side : sides) {
                    if (scene.water(x + side[0], z + side[1]) && scene.head(x + side[0], z + side[1]) > head) {
                        head = scene.head(x + side[0], z + side[1]);
                        source = side;
                    }
                }
                if (source == null) {
                    continue;
                }
                String landing = scene.fluid(x, z);
                String falling = FluidSeams.fallingFluid(settings.seams(), scene.fluid(x + source[0], z + source[1]), landing);
                if (HydrologyCaveCell.SEA_FLUID.equals(landing)) {
                    ontoTheSea++;
                    assertEquals("lava falls into the estuary at " + x + "," + z + " as its seam", "crust", falling);
                }
                assertTrue("raw lava falls onto " + landing + " at " + x + "," + z, !falling.equals("lava") || landing.equals("lava"));
            }
        }
        assertTrue("falls into the estuary " + ontoTheSea, ontoTheSea > 0);
    }

    private static IrisHydrology lavaAndCrust() {
        IrisHydrology config = new IrisHydrology();
        config.getFluids().add(new IrisFluidProfile().setName("lava").setSeam("crust")
                .setPalette(new IrisMaterialPalette().qclear().qadd("minecraft:lava")));
        config.getFluids().add(new IrisFluidProfile().setName("crust")
                .setPalette(new IrisMaterialPalette().qclear().qadd("minecraft:obsidian")));
        return config;
    }

    private static HydrologyTier lavaTier() {
        return new HydrologyTier("lava", 0f, 1.5f, 1.5f, 0f, 0.5f, 1, true, 6, 8, IrisBankStyle.SMOOTH,
                new HydrologyTier.Estuary(48, 2.5f), new HydrologyTier.Delta(0, 35f, 64), new IrisHydrologyMaterials(), "lava", List.of());
    }

    private static void assertLavaSealedFromTheSea(Scene scene, HydrologySettings settings, IntBinaryOperator coast) {
        FluidSeams.Columns columns = new FluidSeams.Columns() {
            @Override
            public String fluid(int x, int z) {
                return scene.role(x, z).wet() ? scene.fluid(x, z) : null;
            }

            @Override
            public boolean sea(int x, int z) {
                return scene.terrain(x, z) < SEA_LEVEL;
            }
        };
        int mouths = 0;
        int lavaColumns = 0;
        int sealed = 0;
        for (int chunkX = -2; chunkX <= 3; chunkX++) {
            for (int chunkZ = -1; chunkZ <= 1; chunkZ++) {
                String[] seams = FluidSeams.seal(settings.seams(), SEED, chunkX, chunkZ, columns);
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        int x = chunkX * 16 + lx;
                        int z = chunkZ * 16 + lz;
                        String seam = seams == null ? null : seams[lz << 4 | lx];
                        WaterRole role = scene.role(x, z);
                        if (role == WaterRole.MOUTH) {
                            mouths++;
                            assertEquals("the estuary holds the sea at " + x + "," + z, HydrologyCaveCell.SEA_FLUID, scene.fluid(x, z));
                            assertNull("the sea itself is never sealed at " + x + "," + z, seam);
                        } else if (role.wet()) {
                            lavaColumns++;
                            assertEquals("upstream of the estuary the river is lava at " + x + "," + z, "lava", scene.fluid(x, z));
                            if (touchesTheSea(columns, x, z)) {
                                sealed++;
                                assertEquals("lava touching the sea is sealed at " + x + "," + z, "crust", seam);
                            }
                        } else if (x >= 40) {
                            assertEquals("no shore rises out of the sea beside the lava at " + x + "," + z, coast.applyAsInt(x, z),
                                    scene.terrain(x, z));
                        }
                    }
                }
            }
        }
        assertTrue("mouth columns " + mouths + ", lava columns " + lavaColumns + ", sealed " + sealed,
                mouths > 20 && lavaColumns > 20 && sealed > 0);
    }

    private static boolean touchesTheSea(FluidSeams.Columns columns, int x, int z) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                String fluid = columns.fluid(x + dx, z + dz);
                if (HydrologyCaveCell.SEA_FLUID.equals(fluid) || fluid == null && columns.sea(x + dx, z + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Reach mouthed(Reach drawn, float coastX) {
        ReachStyle[] style = drawn.style().clone();
        for (int point = 0; point < style.length; point++) {
            style[point] = drawn.xs()[point] >= coastX ? ReachStyle.MOUTH : ReachStyle.RUN;
        }
        return new Reach(drawn.id(), drawn.tier(), drawn.downstreamId(), drawn.xs(), drawn.zs(), drawn.grade(), drawn.halfWidth(), drawn.depth(),
                style, drawn.catchment());
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
        return new HydrologyTier(name, 0f, halfWidth, halfWidth, 0f, depthRatio, 1, true, 6, bankBand, style,
                new HydrologyTier.Estuary(48, 2.5f), new HydrologyTier.Delta(0, 35f, 64), new IrisHydrologyMaterials(), "water", List.of());
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

    private static Reach ledIn(Reach reach, float x, float z) {
        int count = reach.xs().length + 1;
        float[] xs = new float[count];
        float[] zs = new float[count];
        int[] grade = new int[count];
        float[] halfWidth = new float[count];
        float[] depth = new float[count];
        ReachStyle[] style = new ReachStyle[count];
        System.arraycopy(reach.xs(), 0, xs, 1, count - 1);
        System.arraycopy(reach.zs(), 0, zs, 1, count - 1);
        System.arraycopy(reach.grade(), 0, grade, 1, count - 1);
        System.arraycopy(reach.halfWidth(), 0, halfWidth, 1, count - 1);
        System.arraycopy(reach.depth(), 0, depth, 1, count - 1);
        System.arraycopy(reach.style(), 0, style, 1, count - 1);
        xs[0] = x;
        zs[0] = z;
        grade[0] = grade[1];
        halfWidth[0] = halfWidth[1];
        depth[0] = depth[1];
        style[0] = style[1];
        return new Reach(reach.id(), reach.tier(), reach.downstreamId(), xs, zs, grade, halfWidth, depth, style, reach.catchment());
    }

    private static Scene outletBelowATributary(HydrologyTier outletTier, HydrologyTier tributaryTier) {
        Reach outlet = ledIn(line(211L, outletTier, 0L, 508f, 200f, 600f, 200f, (x, z) -> 66), 494f, 200f);
        Reach tributary = line(212L, tributaryTier, outlet.id(), 508f, 120f, 508f, 200f, (x, z) -> 66);
        Pond pond = new Pond(210L, PondKind.NATURAL, 66, 58, 496, 200, 67.5f, circle(478, 200, 17, 1), outlet.id(), 0, "water", 478, 200);
        assertTrue("the lead-in starts in the pond", pond.mask().contains(494, 200));
        assertFalse("the junction lies off the pond", pond.mask().contains(508, 200));
        return new Scene(settings(new IrisHydrology()), (x, z) -> 75).reach(outlet).reach(tributary).pond(pond);
    }

    private static Reach flared(Reach reach, float width) {
        int count = reach.xs().length;
        float[] halfWidth = new float[count];
        float[] depth = new float[count];
        float along = 0f;
        for (int point = 0; point < count; point++) {
            if (point > 0) {
                along += (float) Math.hypot(reach.xs()[point] - reach.xs()[point - 1], reach.zs()[point] - reach.zs()[point - 1]);
            }
            halfWidth[point] = LinkBlend.blend(width, reach.halfWidth()[point], along);
            depth[point] = reach.tier().depth(halfWidth[point]);
        }
        return new Reach(reach.id(), reach.tier(), reach.downstreamId(), reach.xs(), reach.zs(), reach.grade(), halfWidth, depth,
                reach.style(), reach.catchment());
    }

    private static void assertWindowOrderFree(Scene scene, int fromChunkX, int toChunkX, int chunkZ) {
        for (int chunkX = fromChunkX; chunkX <= toChunkX; chunkX++) {
            List<IndexedWindow> reversed = new ArrayList<>(scene.windows(chunkX, chunkZ));
            Collections.reverse(reversed);
            ChunkWaterRaster backward = new ChunkRasterizer(scene.settings, ChannelFluids.TIER).rasterize(chunkX, chunkZ,
                    scene.natural::applyAsInt, reversed, scene.regions(chunkX, chunkZ));
            ChunkWaterRaster forward = scene.raster(chunkX, chunkZ);
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    assertEquals("head in chunk " + chunkX + "," + chunkZ, forward.waterHead(lx, lz), backward.waterHead(lx, lz));
                    assertEquals("terrain in chunk " + chunkX + "," + chunkZ, forward.terrainHeight(lx, lz), backward.terrainHeight(lx, lz));
                    assertEquals("role in chunk " + chunkX + "," + chunkZ, forward.role(lx, lz), backward.role(lx, lz));
                }
            }
        }
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
            this.rasterizer = new ChunkRasterizer(settings, ChannelFluids.TIER);
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
                    key -> rasterizer.rasterize(chunkX, chunkZ, natural::applyAsInt, windows(chunkX, chunkZ), regions(chunkX, chunkZ)));
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
                return new IndexedWindow(new WindowPlan(key, owned, ownedPonds, List.of()), ReachIndex.build(key, owned, ownedPonds));
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
