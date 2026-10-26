package art.arcane.iris.generation.hydrology.water;

import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.grid.Basin;
import art.arcane.iris.generation.hydrology.grid.BasinExtractor;
import art.arcane.iris.generation.hydrology.grid.D8Router;
import art.arcane.iris.generation.hydrology.grid.DemGrid;
import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;
import art.arcane.iris.generation.hydrology.grid.HydrologyTerrainSource;
import art.arcane.iris.generation.hydrology.grid.PriorityFlood;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.IndexedWindow;
import art.arcane.iris.generation.hydrology.plan.RegionKey;
import art.arcane.iris.generation.hydrology.plan.RegionPlan;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.plan.WindowPlan;
import art.arcane.iris.generation.hydrology.raster.ChunkRasterizer;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.raster.ReachIndex;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.DoubleBinaryOperator;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PondPlannerTest {
    private static final long SEED = 1337L;
    private static final int SEA_LEVEL = 40;
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;
    private static final byte ALLOW_ALL = HydrologySampleBuffer.ALLOW_STREAMS | HydrologySampleBuffer.ALLOW_PONDS;
    private static final float BOWL_SPILL = 112f;
    private static final WindowKey ORIGIN = new WindowKey(0, 0);

    @Test
    public void smallSeededPondFollowsItsBowlAtBlockResolution() {
        HydrologySettings settings = settings(config -> {
            seededOnly(config);
            config.getPonds().getSeeded().setDensity(1D).setRadius(new KList<>(8, 8)).setDepth(new KList<>(6, 6))
                    .setAlongChannels(false);
            config.getPonds().setFreeboard(new KList<>(1, 1));
            config.getPonds().getShore().setRoundness(0D);
        }, MIN_Y, MAX_Y);
        List<Pond> ponds = plan(settings, new Terrain((x, z) -> 100D, 1f, ALLOW_ALL, null, MIN_Y, MAX_Y),
                0, 0, 64, 64, new BitSet(), List.of());
        Pond pond = single(ponds);
        PondMask mask = pond.mask();
        assertEquals(1, mask.step());
        int wet = 0;
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int j = 0; j < mask.depth(); j++) {
            for (int i = 0; i < mask.width(); i++) {
                if (mask.node(i, j)) {
                    wet++;
                    minX = Math.min(minX, i);
                    minZ = Math.min(minZ, j);
                    maxX = Math.max(maxX, i);
                    maxZ = Math.max(maxZ, j);
                }
            }
        }
        int rectangle = (maxX - minX + 1) * (maxZ - minZ + 1);
        assertTrue("rounded bowl occupies " + wet + " of " + rectangle + " bounding blocks", wet < rectangle * 0.9D);
        assertFalse(mask.node(minX, minZ));
        assertFalse(mask.node(maxX, maxZ));
        assertTrue(mask.contains(pond.floorX(), pond.floorZ()));
    }

    @Test
    public void globalDensityHalvesSaturatedSeededSiteAcceptance() {
        HydrologySettings full = settings(config -> {
            seededOnly(config);
            config.getPonds().getSeeded().setDensity(16D).setAlongChannels(false);
        }, MIN_Y, MAX_Y);
        HydrologySettings half = settings(config -> {
            seededOnly(config);
            config.setDensity(0.5D);
            config.getPonds().getSeeded().setDensity(16D).setAlongChannels(false);
        }, MIN_Y, MAX_Y);
        Terrain terrain = new Terrain((x, z) -> 100D, 1f, ALLOW_ALL, null, MIN_Y, MAX_Y);
        Set<Long> all = seededSites(full, terrain);
        Set<Long> reduced = seededSites(half, terrain);
        assertTrue("full candidates " + all.size(), all.size() >= 450);
        assertTrue(all.containsAll(reduced));
        double fraction = reduced.size() / (double) all.size();
        assertTrue("retained fraction " + fraction, fraction > 0.44D && fraction < 0.56D);
    }

    private static Set<Long> seededSites(HydrologySettings settings, Terrain terrain) {
        Set<Long> sites = new HashSet<>();
        for (int window = 0; window < 32; window++) {
            int x = window * WindowKey.SIZE;
            Sampled sampled = sample(terrain, 8, x, 0, 64, 64);
            Arrays.fill(sampled.samples().pondDensity(), 100f);
            CupSet cups = new PondPlanner(settings).subtractCups(sampled.grid(), new WindowKey(window, 0), sampled.samples(), new BitSet());
            for (CupSet.Cup cup : cups.within(x, 0, x + WindowKey.SIZE, WindowKey.SIZE)) {
                sites.add(((long) cup.x() << 32) | (cup.z() & 0xFFFFFFFFL));
            }
        }
        return sites;
    }

    @Test
    public void naturalBasinPondSitsBelowSpill() {
        HydrologySettings settings = settings(PondPlannerTest::naturalOnly, MIN_Y, MAX_Y);
        DoubleBinaryOperator terrain = bowl(256, 256, 60, 100, 12, 0.1);
        List<Pond> ponds = plan(settings, new Terrain(terrain, 1f, ALLOW_ALL, null, MIN_Y, MAX_Y), 0, 0, 64, 64, null, List.of());
        assertEquals(1, ponds.size());
        Pond pond = ponds.get(0);
        assertEquals(PondKind.NATURAL, pond.kind());
        assertTrue("spill " + pond.spillHeight(), pond.spillHeight() <= BOWL_SPILL + 1e-3f);
        assertTrue("level " + pond.level() + " spill " + pond.spillHeight(), pond.level() <= (int) Math.floor(pond.spillHeight()) - 1);
        assertTrue(pond.level() > pond.floor());
        assertTrue(pond.floor() >= 99);
        assertTrue(pond.mask().contains(256, 256));
        assertEquals("water", pond.fluid());
        assertEquals(0L, pond.outletReachId());
        assertEquals(0, pond.damRaise());
        assertEquals(256, pond.floorX());
        assertEquals(256, pond.floorZ());
        assertContained(pond, terrain);
        for (int[] node : wetNodes(pond.mask())) {
            assertTrue(terrain.applyAsDouble(node[0], node[1]) < pond.level() + settings.ponds().shore().roundness());
        }
        HydrologySettings deepFreeboard = settings(config -> {
            naturalOnly(config);
            config.getPonds().setFreeboard(new KList<>(3, 3));
            config.getPonds().getShore().setRoundness(2D);
        }, MIN_Y, MAX_Y);
        DoubleBinaryOperator shallow = bowl(256, 256, 60, 100, 3.5, 0.1);
        assertTrue(plan(deepFreeboard, new Terrain(shallow, 1f, ALLOW_ALL, null, MIN_Y, MAX_Y), 0, 0, 64, 64, null, List.of()).isEmpty());
    }

    @Test
    public void refloodingAtALowerLevelOnlyShrinksThePond() {
        HydrologySettings settings = settings(PondPlannerTest::naturalOnly, MIN_Y, MAX_Y);
        DoubleBinaryOperator terrain = bowl(256, 256, 60, 100, 12, 0.1);
        Terrain source = new Terrain(terrain, 1f, ALLOW_ALL, null, MIN_Y, MAX_Y);
        PondPlanner planner = new PondPlanner(settings);
        Sampled sampled = sample(source, 8, 0, 0, 64, 64);
        CupSet cups = planner.subtractCups(sampled.grid(), ORIGIN, sampled.samples(), new BitSet());
        HeightLattice fine = new HeightLattice(source, 4, false, 1 << 20);
        Pond pond = single(flood(settings, planner, sampled, fine, List.of(), cups).ponds());
        Pond lowered = new Pond(pond.id(), pond.kind(), pond.level() - 3, pond.floor(), pond.spillX(), pond.spillZ(), pond.spillHeight(),
                pond.mask(), 7L, pond.damRaise(), pond.fluid(), pond.floorX(), pond.floorZ());
        Pond reflooded = planner.reflood(sampled.grid(), fine, cups, lowered);
        assertEquals(pond.id(), reflooded.id());
        assertEquals(pond.level() - 3, reflooded.level());
        assertEquals(7L, reflooded.outletReachId());
        List<int[]> before = wetNodes(pond.mask());
        List<int[]> after = wetNodes(reflooded.mask());
        assertTrue("nodes " + after.size() + " of " + before.size(), !after.isEmpty() && after.size() < before.size());
        for (int[] node : after) {
            assertTrue("node " + node[0] + "," + node[1] + " outside the old mask", pond.mask().contains(node[0], node[1]));
        }
        assertContained(reflooded, terrain);
        Pond drained = new Pond(pond.id(), pond.kind(), pond.floor(), pond.floor(), pond.spillX(), pond.spillZ(), pond.spillHeight(),
                pond.mask(), 0L, pond.damRaise(), pond.fluid(), pond.floorX(), pond.floorZ());
        assertNull(planner.reflood(sampled.grid(), fine, cups, drained));
    }

    @Test
    public void seededCupOnSlopeIsPerchedAndContained() {
        HydrologySettings settings = settings(config -> {
            seededOnly(config);
            config.getPonds().getSeeded().setDensity(16D);
            config.getPonds().setFreeboard(new KList<>(1, 2));
        }, MIN_Y, MAX_Y);
        DoubleBinaryOperator terrain = (x, z) -> 250D - 0.4D * x;
        List<Pond> ponds = plan(settings, new Terrain(terrain, 1f, ALLOW_ALL, null, MIN_Y, MAX_Y), 0, 0, 64, 64, new BitSet(), List.of());
        assertTrue("seeded ponds " + ponds.size(), ponds.size() >= 3);
        for (Pond pond : ponds) {
            assertEquals(PondKind.SEEDED, pond.kind());
            int downhillEdge = Integer.MIN_VALUE;
            for (int[] node : wetNodes(pond.mask())) {
                downhillEdge = Math.max(downhillEdge, node[0] + pond.mask().step() / 2);
            }
            double below = terrain.applyAsDouble(downhillEdge + 16, 0);
            assertTrue("level " + pond.level() + " natural below " + below, pond.level() > below);
            assertContained(pond, terrain);
        }
    }

    @Test
    public void chainedCupsHaveStrictlyFallingLevels() {
        HydrologySettings settings = settings(config -> {
            seededOnly(config);
            config.getPonds().getSeeded().setDensity(0D).setChannelSpacing(new KList<>(96, 128));
        }, MIN_Y, MAX_Y);
        DoubleBinaryOperator terrain = (x, z) -> 220D - 0.02D * x + 0.3D * Math.abs(z - 256D);
        int width = 160;
        BitSet channel = new BitSet(width * 64);
        for (int i = 0; i < width; i++) {
            channel.set(32 * width + i);
        }
        Planned planned = planned(settings, new Terrain(terrain, 1f, ALLOW_ALL, null, MIN_Y, MAX_Y), 0, 0, width, 64, channel, List.of());
        List<Pond> ordered = drainageOrder(planned, planned.grid().index(1, 32));
        assertEquals(planned.ponds().size(), ordered.size());
        assertTrue("chained ponds " + ordered.size(), ordered.size() >= 3);
        for (int index = 0; index < ordered.size(); index++) {
            Pond pond = ordered.get(index);
            assertEquals(PondKind.CHAINED, pond.kind());
            assertTrue(pond.level() < pond.spillHeight());
            assertContained(pond, terrain);
            if (index > 0) {
                assertTrue("levels " + ordered.get(index - 1).level() + " then " + pond.level(), pond.level() < ordered.get(index - 1).level());
            }
        }
    }

    @Test
    public void cupCutNeverExceedsTwiceDepth() {
        HydrologySettings settings = settings(config -> {
            seededOnly(config);
            config.getPonds().getSeeded().setDensity(64D).setDepth(new KList<>(6, 6)).setAlongChannels(false);
        }, MIN_Y, MAX_Y);
        for (double slope : new double[]{0.1D, 0.25D, 0.4D, 0.6D}) {
            DoubleBinaryOperator terrain = (x, z) -> 200D + slope * (0.8D * x + 0.6D * z);
            Sampled sampled = sample(new Terrain(terrain, 1f, ALLOW_ALL, null, MIN_Y, MAX_Y), 8, 0, 0, 64, 64);
            CupSet cups = new PondPlanner(settings).subtractCups(sampled.grid(), ORIGIN, sampled.samples(), new BitSet());
            float deepest = 0f;
            for (int cell = 0; cell < sampled.grid().values().length; cell++) {
                deepest = Math.max(deepest, sampled.samples().height()[cell] - sampled.grid().values()[cell]);
            }
            assertTrue("slope " + slope + " deepest cut " + deepest, deepest <= 12f + 1e-3f);
            if (slope < 0.5D) {
                assertTrue("slope " + slope, cups.size() > 0 && deepest > 0f);
            } else {
                assertEquals(0, cups.size());
            }
        }
    }

    @Test
    public void fineJoinedBasinsYieldOnePond() {
        HydrologySettings settings = settings(PondPlannerTest::naturalOnly, MIN_Y, MAX_Y);
        DoubleBinaryOperator terrain = (x, z) -> {
            double distance = Math.hypot((x - 256D) / 110D, (z - 256D) / 50D);
            double bowl = distance < 1D ? 100D + 12D * distance * distance : 112D - 5D * (distance - 1D);
            if (distance < 1D && Math.abs(x - 256D) < 2D) {
                double wave = Math.cos(Math.PI * z / 8D);
                return Math.max(bowl, 104D + 7.8D * wave * wave);
            }
            return bowl;
        };
        Planned planned = planned(settings, new Terrain(terrain, 1f, ALLOW_ALL, null, MIN_Y, MAX_Y), 0, 0, 64, 64, null, List.of());
        assertTrue("raw basins " + planned.basins().size(), planned.basins().size() >= 2);
        Pond pond = single(planned.ponds());
        assertTrue(pond.mask().contains(200, 256));
        assertTrue(pond.mask().contains(312, 256));
        assertContained(pond, terrain);
        assertRasterRimContained(settings, pond, terrain);
    }

    @Test
    public void aLoweredDamPondRefloodsAgainstItsDamCrest() {
        DoubleBinaryOperator terrain = bowl(256, 256, 60, 100, 12, 0.1);
        Terrain source = new Terrain(terrain, 1f, ALLOW_ALL, null, MIN_Y, MAX_Y);
        HydrologySettings settings = settings(config -> {
            naturalOnly(config);
            config.getPonds().getDams().setEnabled(true).setMaxRaise(8);
        }, MIN_Y, MAX_Y);
        Pond undammed = single(plan(settings(PondPlannerTest::naturalOnly, MIN_Y, MAX_Y), source, 0, 0, 64, 64, null, List.of()));
        PondPlanner planner = new PondPlanner(settings);
        Sampled sampled = sample(source, 8, 0, 0, 64, 64);
        CupSet cups = planner.subtractCups(sampled.grid(), ORIGIN, sampled.samples(), new BitSet());
        HeightLattice fine = new HeightLattice(source, 4, false, 1 << 20);
        Pond dammed = single(flood(settings, planner, sampled, fine, List.of(), cups).ponds());
        int aboveNaturalSpill = (int) Math.floor(undammed.spillHeight()) + 1;
        assertTrue("dammed " + dammed.level() + " natural spill " + undammed.spillHeight(), aboveNaturalSpill < dammed.level());
        Pond lowered = new Pond(dammed.id(), dammed.kind(), aboveNaturalSpill, dammed.floor(), dammed.spillX(), dammed.spillZ(),
                dammed.spillHeight(), dammed.mask(), 0L, dammed.damRaise(), dammed.fluid(), dammed.floorX(), dammed.floorZ());
        Pond reflooded = planner.reflood(sampled.grid(), fine, cups, lowered);
        assertEquals(aboveNaturalSpill, reflooded.level());
        assertEquals(dammed.damRaise(), reflooded.damRaise());
    }

    @Test
    public void damRaisesLevelAtMostMaxRaise() {
        DoubleBinaryOperator terrain = bowl(256, 256, 60, 100, 12, 0.1);
        Terrain source = new Terrain(terrain, 1f, ALLOW_ALL, null, MIN_Y, MAX_Y);
        Pond undammed = single(plan(settings(PondPlannerTest::naturalOnly, MIN_Y, MAX_Y), source, 0, 0, 64, 64, null, List.of()));
        HydrologySettings dammedSettings = settings(config -> {
            naturalOnly(config);
            config.getPonds().getDams().setEnabled(true).setMaxRaise(4);
        }, MIN_Y, MAX_Y);
        Pond dammed = single(plan(dammedSettings, source, 0, 0, 64, 64, null, List.of()));
        int raise = dammed.level() - undammed.level();
        assertTrue("raise " + raise, raise > 0 && raise <= 4);
        assertEquals(raise, dammed.damRaise());
        assertEquals(0, undammed.damRaise());
        assertTrue(dammed.spillHeight() > undammed.spillHeight());
        assertTrue(dammed.spillHeight() <= undammed.spillHeight() + 4f + 1e-3f);
        assertTrue(dammed.level() < dammed.spillHeight());
    }

    @Test
    public void dryClimateDrawsDownLevel() {
        HydrologySettings settings = settings(PondPlannerTest::naturalOnly, MIN_Y, MAX_Y);
        DoubleBinaryOperator terrain = bowl(256, 256, 60, 100, 12, 0.1);
        Pond wet = single(plan(settings, new Terrain(terrain, 1f, ALLOW_ALL, null, MIN_Y, MAX_Y), 0, 0, 64, 64, null, List.of()));
        Pond dry = single(plan(settings, new Terrain(terrain, 0f, ALLOW_ALL, null, MIN_Y, MAX_Y), 0, 0, 64, 64, null, List.of()));
        Pond damp = single(plan(settings, new Terrain(terrain, 0.5f, ALLOW_ALL, null, MIN_Y, MAX_Y), 0, 0, 64, 64, null, List.of()));
        assertEquals(wet.id(), dry.id());
        assertEquals(settings.ponds().climateDrawdown(), wet.level() - dry.level());
        assertTrue(damp.level() < wet.level() && damp.level() > dry.level());
        assertContained(dry, terrain);
    }

    @Test
    public void pondMaskNeverTouchesHalo() {
        HydrologySettings settings = settings(config -> {
            naturalOnly(config);
            config.getPonds().setFreeboard(new KList<>(0, 1));
            config.getPonds().getShore().setRoundness(2D);
        }, MIN_Y, MAX_Y);
        List<DoubleBinaryOperator> terrains = List.of(
                (x, z) -> 120D + 6D * Math.sin(x / 23D) * Math.sin(z / 19D),
                bowl(44, 256, 30, 100, 12, 0.4));
        int checked = 0;
        for (DoubleBinaryOperator terrain : terrains) {
            List<Pond> ponds = plan(settings, new Terrain(terrain, 1f, ALLOW_ALL, null, MIN_Y, MAX_Y), 0, 0, 64, 64, null, List.of());
            for (Pond pond : ponds) {
                PondMask mask = pond.mask();
                for (int[] node : wetNodes(mask)) {
                    assertTrue(node[0] >= mask.step() && node[0] <= 63 * 8 - mask.step());
                    assertTrue(node[1] >= mask.step() && node[1] <= 63 * 8 - mask.step());
                }
                for (int i = 0; i < mask.width(); i++) {
                    assertFalse(mask.contains(mask.originX() + i * mask.step(), mask.originZ()));
                    assertFalse(mask.contains(mask.originX() + i * mask.step(), mask.originZ() + (mask.depth() - 1) * mask.step()));
                }
                for (int j = 0; j < mask.depth(); j++) {
                    assertFalse(mask.contains(mask.originX(), mask.originZ() + j * mask.step()));
                    assertFalse(mask.contains(mask.originX() + (mask.width() - 1) * mask.step(), mask.originZ() + j * mask.step()));
                }
                assertTrue("level " + pond.level() + " spill " + pond.spillHeight(), pond.level() >= (int) Math.floor(pond.spillHeight()) - 3);
                assertContained(pond, terrain);
                checked++;
            }
        }
        assertTrue("ponds checked " + checked, checked >= 4);
    }

    @Test
    public void levelsClampInsideWorldHeight() {
        DoubleBinaryOperator terrain = bowl(256, 256, 60, 100, 12, 0.1);
        HydrologySettings low = settings(PondPlannerTest::naturalOnly, MIN_Y, 108);
        Pond capped = single(plan(low, new Terrain(terrain, 1f, ALLOW_ALL, "lava", MIN_Y, 108), 0, 0, 64, 64, null, List.of()));
        assertEquals(107, capped.level());
        assertEquals("lava", capped.fluid());
        assertContained(capped, terrain);
        HydrologySettings high = settings(PondPlannerTest::naturalOnly, 106, MAX_Y);
        Pond floored = single(plan(high, new Terrain(terrain, 0f, ALLOW_ALL, "lava", 106, MAX_Y), 0, 0, 64, 64, null, List.of()));
        assertEquals(107, floored.level());
        assertContained(floored, terrain);
    }

    @Test
    public void blockedPolicyPlacesNoPond() {
        HydrologySettings settings = settings(config -> config.getPonds().getSeeded().setDensity(16D), MIN_Y, MAX_Y);
        DoubleBinaryOperator crater = bowl(256, 256, 60, 100, 12, 0.1);
        DoubleBinaryOperator terrain = (x, z) -> crater.applyAsDouble(x, z) + 0.05D * z;
        BitSet channel = new BitSet(64 * 64);
        channel.set(16 * 64 + 1, 16 * 64 + 63);
        Terrain allowed = new Terrain(terrain, 1f, ALLOW_ALL, null, MIN_Y, MAX_Y);
        assertFalse(plan(settings, allowed, 0, 0, 64, 64, channel, List.of()).isEmpty());
        Terrain blocked = new Terrain(terrain, 1f, HydrologySampleBuffer.ALLOW_STREAMS, null, MIN_Y, MAX_Y);
        PondPlanner planner = new PondPlanner(settings);
        Sampled sampled = sample(blocked, 8, 0, 0, 64, 64);
        CupSet cups = planner.subtractCups(sampled.grid(), ORIGIN, sampled.samples(), channel);
        assertEquals(0, cups.size());
        assertArrayEquals(sampled.samples().height(), sampled.grid().values(), 0f);
        assertTrue(flood(settings, planner, sampled, new HeightLattice(blocked, 4, false, 1 << 20), List.of(), cups).ponds().isEmpty());
    }

    @Test
    public void regionLakesOnlyBlockWindowPonds() {
        HydrologySettings settings = settings(PondPlannerTest::naturalOnly, MIN_Y, MAX_Y);
        Terrain source = new Terrain(bowl(256, 256, 60, 100, 12, 0.1), 1f, ALLOW_ALL, null, MIN_Y, MAX_Y);
        Pond open = single(plan(settings, source, 0, 0, 64, 64, null, List.of()));
        assertEquals(PondKind.NATURAL, open.kind());
        PondMask besideMask = lakeColumn(328);
        assertFalse(open.mask().overlaps(besideMask));
        assertTrue(plan(settings, source, 0, 0, 64, 64, null, List.of(lake(new PondMask(240, 240, 16, 2, 2, new long[]{0b1111L})))).isEmpty());
        assertTrue(plan(settings, source, 0, 0, 64, 64, null, List.of(lake(besideMask))).isEmpty());
        assertEquals(open, single(plan(settings, source, 0, 0, 64, 64, null, List.of(lake(lakeColumn(480))))));
    }

    @Test
    public void lakeSpillRefinementSamplesBoundedNeighbourhood() {
        HydrologySettings settings = settings(PondPlannerTest::naturalOnly, MIN_Y, MAX_Y);
        Terrain source = new Terrain(bowl(256, 256, 400, 100, 30, 0.05), 1f, ALLOW_ALL, null, MIN_Y, MAX_Y);
        CountingTerrain counting = new CountingTerrain(source);
        PondPlanner planner = new PondPlanner(settings);
        Sampled region = sample(source, 64, -1280, -1280, 48, 48);
        Pond lake = single(lakes(settings, planner, region, new HeightLattice(counting, 4, false, 1 << 20)));
        assertEquals(16, lake.mask().step());
        int offLattice = 0;
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (long point : counting.points()) {
            int x = (int) (point >> 32);
            int z = (int) point;
            if (Math.floorMod(x, 16) != 0 || Math.floorMod(z, 16) != 0) {
                offLattice++;
                minX = Math.min(minX, x);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxZ = Math.max(maxZ, z);
            }
        }
        assertTrue("fine samples off the 16-block lattice " + offLattice, offLattice > 0 && offLattice <= 64 * 64);
        assertTrue("window " + minX + ".." + maxX + " x " + minZ + ".." + maxZ, maxX - minX < 64 * 4 && maxZ - minZ < 64 * 4);
        assertTrue("spill " + lake.spillX() + "," + lake.spillZ(), lake.spillX() >= minX && lake.spillX() <= maxX
                && lake.spillZ() >= minZ && lake.spillZ() <= maxZ);
    }

    @Test
    public void lakeBoxStaysInsideCandidateBound() {
        HydrologySettings settings = settings(PondPlannerTest::naturalOnly, MIN_Y, MAX_Y);
        CountingTerrain counting = new CountingTerrain(new Terrain(bowl(64, 64, 400, 100, 30, 0.05), 1f, ALLOW_ALL, null, MIN_Y, MAX_Y));
        LakeCandidate wide = new LakeCandidate(64, 64, 100f, 448, 64, 130f, 3072, 64, new long[]{LakeCandidate.pack(1, 1)});
        new PondPlanner(settings).lakes(List.of(wide), new HeightLattice(counting, 4, false, 1 << 20));
        int bound = LakeCandidate.BOUND_CELLS * 64 + 32;
        int refine = 32 * 4;
        assertTrue(counting.points().contains(((long) (64 - bound) << 32) | ((64 - bound) & 0xFFFFFFFFL)));
        assertTrue(counting.points().contains(((long) (64 + bound - 16) << 32) | ((64 + bound - 16) & 0xFFFFFFFFL)));
        for (long point : counting.points()) {
            int x = (int) (point >> 32);
            int z = (int) point;
            assertTrue("sample " + x + "," + z, Math.abs(x - 64) <= bound + refine && Math.abs(z - 64) <= bound + refine);
        }
    }

    @Test
    public void pondLevelsNeverSitBelowTheSeaSurface() {
        HydrologySettings settings = settings(config -> {
            naturalOnly(config);
            config.getPonds().setFreeboard(new KList<>(2, 2));
        }, MIN_Y, MAX_Y);
        DoubleBinaryOperator coastal = bowl(256, 256, 60, SEA_LEVEL - 8, 9.5, 0.1);
        Pond pond = single(plan(settings, new Terrain(coastal, 1f, ALLOW_ALL, null, MIN_Y, MAX_Y).inland(), 0, 0, 64, 64, null, List.of()));
        assertTrue("spill " + pond.spillHeight(), pond.spillHeight() > SEA_LEVEL + 1 && pond.spillHeight() < SEA_LEVEL + 2);
        assertEquals("level clamps up to the sea surface", SEA_LEVEL, pond.level());
        assertContained(pond, coastal);
    }

    @Test
    public void pondLevelStaysWithinTheFillLimitOfItsBlockRim() {
        HydrologySettings settings = settings(PondPlannerTest::naturalOnly, MIN_Y, MAX_Y);
        int maxFill = settings.deformation().maxFill();
        DoubleBinaryOperator bowl = bowl(256, 256, 60, 100, 12, 0.1);
        Pond open = single(plan(settings, new Terrain(bowl, 1f, ALLOW_ALL, null, MIN_Y, MAX_Y), 0, 0, 64, 64, null, List.of()));
        int wetEdge = 256;
        while (open.mask().contains(wetEdge + 1, 256)) {
            wetEdge++;
        }
        int trench = wetEdge + 1;
        assertTrue("the trench lies between fine nodes " + trench, Math.floorMod(trench, 4) != 0 && Math.floorMod(trench + 1, 4) != 0);
        assertTrue("the untrenched pond stands above the trench " + open.level(), open.level() > 101 + maxFill);
        DoubleBinaryOperator trenched = (x, z) -> (x == trench || x == trench + 1) && Math.abs(z - 256) <= 6 ? 101D : bowl.applyAsDouble(x, z);
        Pond pond = single(plan(settings, new Terrain(trenched, 1f, ALLOW_ALL, null, MIN_Y, MAX_Y), 0, 0, 64, 64, null, List.of()));
        int rim = Integer.MAX_VALUE;
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int z = 180; z <= 332; z++) {
            for (int x = 180; x <= 332; x++) {
                if (!pond.mask().contains(x, z)) {
                    continue;
                }
                for (int[] side : sides) {
                    if (!pond.mask().contains(x + side[0], z + side[1])) {
                        rim = Math.min(rim, (int) Math.floor(trenched.applyAsDouble(x + side[0], z + side[1])));
                    }
                }
            }
        }
        assertTrue("level " + pond.level() + " within the fill limit of rim " + rim, pond.level() <= rim + maxFill);
        assertTrue("level " + pond.level() + " above floor " + pond.floor(), pond.level() > pond.floor());
        assertContained(pond, trenched);
    }

    private static void naturalOnly(IrisHydrology config) {
        config.getPonds().getSeeded().setEnabled(false);
    }

    private static void seededOnly(IrisHydrology config) {
        config.getPonds().getNatural().setEnabled(false);
    }

    private static HydrologySettings settings(Consumer<IrisHydrology> edit, int minY, int maxY) {
        IrisHydrology config = new IrisHydrology();
        edit.accept(config);
        return HydrologySettings.compile(config, SEED, SEA_LEVEL, minY, maxY);
    }

    private static DoubleBinaryOperator bowl(double centreX, double centreZ, double radius, double floor, double rise, double outerSlope) {
        return (x, z) -> {
            double distance = Math.hypot(x - centreX, z - centreZ);
            if (distance < radius) {
                double ratio = distance / radius;
                return floor + rise * ratio * ratio;
            }
            return floor + rise - outerSlope * (distance - radius);
        };
    }

    private static Pond single(List<Pond> ponds) {
        assertEquals("ponds " + ponds, 1, ponds.size());
        return ponds.get(0);
    }

    private static List<Pond> plan(HydrologySettings settings, Terrain terrain, int originX, int originZ, int width, int depth,
                                   BitSet channels, List<Pond> regionLakes) {
        return planned(settings, terrain, originX, originZ, width, depth, channels, regionLakes).ponds();
    }

    private static Planned planned(HydrologySettings settings, Terrain terrain, int originX, int originZ, int width, int depth,
                                   BitSet channels, List<Pond> regionLakes) {
        PondPlanner planner = new PondPlanner(settings);
        Sampled sampled = sample(terrain, 8, originX, originZ, width, depth);
        CupSet cups = planner.subtractCups(sampled.grid(), ORIGIN, sampled.samples(), channels == null ? new BitSet() : channels);
        return flood(settings, planner, sampled, new HeightLattice(terrain, 4, false, 1 << 20), regionLakes, cups);
    }

    private static Sampled sample(HydrologyTerrainSource terrain, int cell, int originX, int originZ, int width, int depth) {
        HeightLattice lattice = new HeightLattice(terrain, cell, false, 1 << 20);
        HydrologySampleBuffer samples = new HydrologySampleBuffer(width * depth);
        lattice.fill(Math.floorDiv(originX, cell), Math.floorDiv(originZ, cell), width, depth, samples);
        DemGrid grid = new DemGrid(originX, originZ, cell, width, depth);
        System.arraycopy(samples.height(), 0, grid.values(), 0, width * depth);
        return new Sampled(grid, samples);
    }

    private static Planned flood(HydrologySettings settings, PondPlanner planner, Sampled sampled, HeightLattice fine,
                                 List<Pond> regionLakes, CupSet cups) {
        Planned drained = drain(settings, sampled);
        return new Planned(drained.grid(), drained.filled(), drained.basins(),
                planner.ponds(drained.grid(), drained.filled(), drained.basins(), regionLakes, fine, cups));
    }

    private static List<Pond> lakes(HydrologySettings settings, PondPlanner planner, Sampled sampled, HeightLattice fine) {
        return planner.lakes(planner.lakeCandidates(sampled.grid(), drain(settings, sampled).basins()), fine);
    }

    private static Planned drain(HydrologySettings settings, Sampled sampled) {
        DemGrid grid = sampled.grid();
        BitSet seeds = new BitSet(grid.width() * grid.depth());
        for (int j = 0; j < grid.depth(); j++) {
            for (int i = 0; i < grid.width(); i++) {
                int cell = grid.index(i, j);
                if (i == 0 || j == 0 || i == grid.width() - 1 || j == grid.depth() - 1 || sampled.samples().ocean()[cell]) {
                    seeds.set(cell);
                }
            }
        }
        float[] filled = PriorityFlood.fill(grid, seeds, settings.drainage().fillEpsilon());
        return new Planned(grid, filled, BasinExtractor.basins(grid, filled, 0.5f, settings.seaLevel()), List.of());
    }

    private static List<Pond> drainageOrder(Planned planned, int head) {
        DemGrid grid = planned.grid();
        int[] receivers = D8Router.receivers(grid, planned.filled());
        int[] basinOf = new int[receivers.length];
        Arrays.fill(basinOf, -1);
        for (int index = 0; index < planned.basins().size(); index++) {
            for (int cell : planned.basins().get(index).cells()) {
                basinOf[cell] = index;
            }
        }
        List<Pond> ordered = new ArrayList<>();
        for (int cell = head; cell >= 0; cell = receivers[cell]) {
            if (basinOf[cell] < 0) {
                continue;
            }
            int floor = planned.basins().get(basinOf[cell]).floorIndex();
            for (Pond pond : planned.ponds()) {
                int pondFloor = grid.index((pond.floorX() - grid.originX()) / grid.cell(), (pond.floorZ() - grid.originZ()) / grid.cell());
                if (pondFloor == floor && !ordered.contains(pond)) {
                    ordered.add(pond);
                }
            }
        }
        return ordered;
    }

    private static PondMask lakeColumn(int x) {
        return new PondMask(x, 208, 16, 1, 7, new long[]{0b1111111L});
    }

    private static void assertRasterRimContained(HydrologySettings settings, Pond pond, DoubleBinaryOperator natural) {
        Map<Long, ChunkWaterRaster> rasters = new HashMap<>();
        ChunkRasterizer rasterizer = new ChunkRasterizer(settings);
        int raised = 0;
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] wet : wetNodes(pond.mask())) {
            for (int[] side : sides) {
                int x = wet[0] + side[0];
                int z = wet[1] + side[1];
                if (pond.mask().contains(x, z)) {
                    continue;
                }
                long key = ((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL);
                ChunkWaterRaster raster = rasters.get(key);
                if (raster == null) {
                    WindowPlan window = new WindowPlan(WindowKey.containing(x, z), List.of(), List.of(pond), List.of());
                    IndexedWindow indexed = new IndexedWindow(window, ReachIndex.build(List.of(), List.of(pond)));
                    List<RegionPlan> regions = new ArrayList<>();
                    for (RegionKey region : ChunkRasterizer.lakeRegions(x >> 4, z >> 4)) {
                        regions.add(new RegionPlan(region, List.of(), List.of()));
                    }
                    raster = rasterizer.rasterize(x >> 4, z >> 4, (px, pz) -> (int) Math.round(natural.applyAsDouble(px, pz)),
                            List.of(indexed), regions);
                    rasters.put(key, raster);
                }
                int ground = (int) Math.round(natural.applyAsDouble(x, z));
                int actual = raster.terrainHeight(x & 15, z & 15);
                assertTrue("dry rim contains the water at " + x + "," + z, actual >= pond.level());
                assertTrue("rim fill remains bounded at " + x + "," + z, actual <= ground + settings.deformation().maxFill());
                if (actual > ground) {
                    raised++;
                }
            }
        }
        assertTrue("sub-lattice rim needs real raster berms", raised > 0);
    }

    private static Pond lake(PondMask mask) {
        return new Pond(42L, PondKind.LAKE, 110, 100, mask.originX(), mask.originZ(), 111f, mask, 0L, 0, "water", mask.originX(), mask.originZ());
    }

    private static List<int[]> wetNodes(PondMask mask) {
        List<int[]> nodes = new ArrayList<>();
        for (int j = 0; j < mask.depth(); j++) {
            for (int i = 0; i < mask.width(); i++) {
                int x = mask.originX() + i * mask.step();
                int z = mask.originZ() + j * mask.step();
                if (mask.contains(x, z)) {
                    nodes.add(new int[]{x, z});
                }
            }
        }
        return nodes;
    }

    private static void assertContained(Pond pond, DoubleBinaryOperator natural) {
        PondMask mask = pond.mask();
        int step = mask.step();
        int maxFill = new IrisHydrology().getDeformation().getMaxFill();
        List<int[]> nodes = wetNodes(mask);
        assertFalse(nodes.isEmpty());
        assertTrue("floor " + pond.floor() + " level " + pond.level(), pond.floor() < pond.level());
        int[][] directions = {{step, 0}, {-step, 0}, {0, step}, {0, -step}};
        for (int[] node : nodes) {
            for (int[] direction : directions) {
                int x = node[0] + direction[0];
                int z = node[1] + direction[1];
                if (!mask.contains(x, z)) {
                    long edge = Math.round(natural.applyAsDouble(x, z));
                    assertTrue("edge " + x + "," + z + " natural " + edge + " level " + pond.level(), edge + maxFill >= pond.level());
                }
            }
        }
    }

    private record Sampled(DemGrid grid, HydrologySampleBuffer samples) {
    }

    private record Planned(DemGrid grid, float[] filled, List<Basin> basins, List<Pond> ponds) {
    }

    private static final class CountingTerrain implements HydrologyTerrainSource {
        private final Terrain delegate;
        private final Set<Long> points = new HashSet<>();

        private CountingTerrain(Terrain delegate) {
            this.delegate = delegate;
        }

        @Override
        public void sample(int[] xs, int[] zs, HydrologySampleBuffer out, boolean baseOnly) {
            for (int index = 0; index < xs.length; index++) {
                points.add(((long) xs[index] << 32) | (zs[index] & 0xFFFFFFFFL));
            }
            delegate.sample(xs, zs, out, baseOnly);
        }

        @Override
        public int seaLevel() {
            return delegate.seaLevel();
        }

        @Override
        public int minY() {
            return delegate.minY();
        }

        @Override
        public int maxY() {
            return delegate.maxY();
        }

        private Set<Long> points() {
            return points;
        }
    }

    private static final class Terrain implements HydrologyTerrainSource {
        private final DoubleBinaryOperator height;
        private final float rainfall;
        private final byte permission;
        private final String fluid;
        private final int minY;
        private final int maxY;
        private final boolean inland;

        private Terrain(DoubleBinaryOperator height, float rainfall, byte permission, String fluid, int minY, int maxY) {
            this(height, rainfall, permission, fluid, minY, maxY, false);
        }

        private Terrain(DoubleBinaryOperator height, float rainfall, byte permission, String fluid, int minY, int maxY, boolean inland) {
            this.height = height;
            this.rainfall = rainfall;
            this.permission = permission;
            this.fluid = fluid;
            this.minY = minY;
            this.maxY = maxY;
            this.inland = inland;
        }

        private Terrain inland() {
            return new Terrain(height, rainfall, permission, fluid, minY, maxY, true);
        }

        @Override
        public void sample(int[] xs, int[] zs, HydrologySampleBuffer out, boolean baseOnly) {
            for (int index = 0; index < xs.length; index++) {
                float value = (float) height.applyAsDouble(xs[index], zs[index]);
                out.height()[index] = value;
                out.ocean()[index] = !inland && value <= SEA_LEVEL;
                out.rainfall()[index] = rainfall;
                out.pondDensity()[index] = 1f;
                out.permission()[index] = permission;
                out.fluidProfile()[index] = fluid;
            }
        }

        @Override
        public int seaLevel() {
            return SEA_LEVEL;
        }

        @Override
        public int minY() {
            return minY;
        }

        @Override
        public int maxY() {
            return maxY;
        }
    }
}
