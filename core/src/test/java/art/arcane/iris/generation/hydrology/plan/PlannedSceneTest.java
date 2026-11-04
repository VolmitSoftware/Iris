package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.raster.WaterHeads;
import art.arcane.iris.generation.hydrology.water.ConfluenceGrader;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondKind;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachShaper;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import org.junit.AfterClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntBinaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PlannedSceneTest {
    private static final long SEED = 1337L;
    private static final int SEA_LEVEL = SyntheticTerrain.SEA_LEVEL;
    private static final double RAINFALL = 0.5D;
    private static final float SPACING = 2f;
    private static final float OUTLET_LENGTH = 8f;
    private static PlannedScene openFractal;

    @AfterClass
    public static void dropScene() {
        openFractal = null;
    }

    @Test
    public void outletsAreConnected() {
        assertEquals("detached outlets on openFractal(1) with rainfall " + RAINFALL + " over 2x2 windows", List.of(),
                WaterInvariants.detachedOutlets(openFractal()));
    }

    @Test
    public void oneBlockTiersKeepOutletsAndConfluencesConnected() {
        IrisHydrology config = PlannedScene.packConfig();
        config.getTiers().forEach(tier -> tier.setStepHeight(1).setCascades(false));
        HydrologySettings settings = HydrologySettings.compile(config, SEED, SEA_LEVEL, SyntheticTerrain.MIN_Y, SyntheticTerrain.MAX_Y);
        PlannedScene scene = PlannedScene.plan(SyntheticTerrain.openFractal(1L).withRainfall((x, z) -> RAINFALL), settings, 2, 2);
        assertEquals("detached outlets", List.of(), WaterInvariants.detachedOutlets(scene));
        assertEquals("confluence tongues", List.of(), WaterInvariants.confluenceTongues(scene));
        Map<Long, Reach> byId = new HashMap<>();
        int oneBlockDrops = 0;
        for (Reach reach : scene.reaches()) {
            byId.put(reach.id(), reach);
            for (int point = 1; point < reach.grade().length; point++) {
                oneBlockDrops += reach.grade()[point - 1] - reach.grade()[point] == 1 ? 1 : 0;
            }
        }
        assertTrue("one-block drops in the plan " + oneBlockDrops, oneBlockDrops > 100);
        int outlets = 0;
        for (Pond pond : scene.ponds()) {
            Reach outlet = byId.get(pond.outletReachId());
            if (outlet != null) {
                outlets++;
                assertTrue("outlet " + outlet.id() + " starts at " + outlet.grade()[0] + " above its pond's surface " + WaterHeads.standing(pond.level(), SEA_LEVEL),
                        outlet.grade()[0] <= WaterHeads.standing(pond.level(), SEA_LEVEL));
            }
        }
        assertTrue("outlets checked " + outlets, outlets > 0);
    }

    @Test
    public void confluencesHaveNoTongues() {
        assertEquals("confluence tongues on openFractal(1) with rainfall " + RAINFALL + " over 2x2 windows", List.of(),
                WaterInvariants.confluenceTongues(openFractal()));
    }

    @Test
    public void feedersAtALedInOutletsExitPoolBesideTheLeadIn() {
        PlannedScene scene = openFractal();
        Map<Long, Reach> byId = new HashMap<>();
        Set<Long> fed = new HashSet<>();
        for (Reach reach : scene.reaches()) {
            byId.put(reach.id(), reach);
            fed.add(reach.downstreamId());
        }
        int junctions = 0;
        int besideTheLeadIn = 0;
        for (Reach feeder : scene.reaches()) {
            Reach outlet = byId.get(feeder.downstreamId());
            int last = feeder.xs().length - 1;
            if (outlet == null || outlet.xs().length < 2 || outlet.xs()[1] != feeder.xs()[last] || outlet.zs()[1] != feeder.zs()[last]
                    || !owner(feeder).equals(owner(outlet))) {
                continue;
            }
            junctions++;
            int level = outlet.grade()[0];
            double arc = 0D;
            for (int point = last - 1; point >= 0 && feeder.style()[point] != ReachStyle.POND_OUTLET; point--) {
                arc += Math.hypot(feeder.xs()[point + 1] - feeder.xs()[point], feeder.zs()[point + 1] - feeder.zs()[point]);
                float reach = feeder.halfWidth()[point] + ConfluenceGrader.POOL_MARGIN;
                double edge = edgeDistance(outlet, 0, feeder.xs()[point], feeder.zs()[point]);
                if (arc > ConfluenceGrader.POOL_CAP || edge > reach) {
                    break;
                }
                int grade = feeder.grade()[point];
                if (grade != level && (point == 0 && fed.contains(feeder.id()) || edge > 0D && grade - level > ConfluenceGrader.MARGIN_LOWERING)) {
                    ReachStyle style = feeder.style()[point + 1];
                    assertTrue(feeder + " point " + point + " stands above the pool of led-in outlet " + outlet + " and drops into it as " + style,
                            style == ReachStyle.CASCADE || style == ReachStyle.WATERFALL);
                    break;
                }
                assertEquals(feeder + " point " + point + " in the pool of led-in outlet " + outlet, level, grade);
                besideTheLeadIn += edgeDistance(outlet, 1, feeder.xs()[point], feeder.zs()[point]) > reach ? 1 : 0;
            }
        }
        assertTrue("feeders joining a led-in outlet at its exit " + junctions, junctions > 0);
        assertTrue("feeder points pooled only through an outlet's lead-in " + besideTheLeadIn, besideTheLeadIn > 0);
    }

    @Test
    public void detachedOutletsCatchShoreGapsAndLowerStarts() {
        HydrologySettings settings = PlannedScene.packSettings(SEED);
        HydrologyTier stream = settings.tiers().get(0);
        int[] starts = {133, 144, 133, 144};
        int[] grades = {100, 100, 98, 98};
        for (int index = 0; index < starts.length; index++) {
            int level = grades[index];
            Reach outlet = straight(11L + index, stream, 0L, 2f, new int[]{starts[index], 128}, new int[]{204, 128}, (x, z) -> level);
            Pond pond = pond(21L, 100, disk(128, 128, 8), outlet.id());
            PlannedScene scene = PlannedScene.of(SyntheticTerrain.flat(104D), settings, List.of(outlet), List.of(pond));
            assertEquals("outlet starting at x " + starts[index] + " with grade " + level, index == 0 ? List.of() : List.of(outlet.id()),
                    WaterInvariants.detachedOutlets(scene));
        }
    }

    @Test
    public void outletOwnedByAWiderReceivingReachAtThePondLevelIsConnected() {
        HydrologySettings settings = PlannedScene.packSettings(SEED);
        for (int receivingGrade : new int[]{100, 98}) {
            Reach receiving = straight(52L, settings.tiers().get(2), 0L, 18f, new int[]{150, 128}, new int[]{300, 128}, (x, z) -> receivingGrade);
            Reach outlet = straight(51L, settings.tiers().get(0), receiving.id(), 2f, new int[]{133, 128}, new int[]{150, 128}, (x, z) -> 100);
            Pond pond = pond(21L, 100, disk(128, 128, 8), outlet.id());
            PlannedScene scene = PlannedScene.of(SyntheticTerrain.flat(104D), settings, List.of(outlet, receiving), List.of(pond));
            int outletColumns = 0;
            for (int z = 120; z <= 136; z++) {
                for (int x = 120; x <= 160; x++) {
                    outletColumns += scene.feature(x, z) == outlet.id() ? 1 : 0;
                }
            }
            assertEquals("outlet columns owned by the receiving reach at " + receivingGrade, 0, outletColumns);
            assertEquals("receiving reach at " + receivingGrade, receivingGrade == 100 ? List.of() : List.of(outlet.id()),
                    WaterInvariants.detachedOutlets(scene));
        }
    }

    @Test
    public void unsettledFeederPerchedAboveTheJunctionIsATongue() {
        HydrologySettings settings = PlannedScene.packSettings(SEED);
        for (int tributaryLevel : new int[]{70, 66}) {
            Reach tributary = tributary(settings, new int[]{144, 56}, new int[]{144, 136}, 2f, (x, z) -> z >= 136 ? 66 : tributaryLevel);
            assertEquals("tributary at " + tributaryLevel, tributaryLevel > 66 ? List.of(tributary.id()) : List.of(),
                    tongues(settings, tributary, 66, true, List.of()));
        }
    }

    @Test
    public void tributaryFallingFarIntoTheJunctionAtTheBankIsNoTongue() {
        HydrologySettings settings = PlannedScene.packSettings(SEED);
        for (int beside : new int[]{70, 74}) {
            Reach tributary = tributary(settings, new int[]{144, 56}, new int[]{144, 136}, 2f, (x, z) -> z >= 128 ? 66 : beside);
            boolean falls = beside - 66 > ConfluenceGrader.MARGIN_LOWERING;
            assertEquals("tributary " + (beside - 66) + " blocks above the junction beside the receiving bank", falls ? List.of() : List.of(tributary.id()),
                    tongues(settings, tributary, 66, true, List.of()));
        }
    }

    @Test
    public void startHeldForItsOwnFeederMustFallIntoTheJunction() {
        HydrologySettings settings = PlannedScene.packSettings(SEED);
        Reach stub = tributary(settings, new int[]{144, 124}, new int[]{144, 136}, 2f, (x, z) -> z <= 124 ? 70 : 66);
        Reach upstream = straight(304L, settings.tiers().get(0), stub.id(), 1f, new int[]{144, 56}, new int[]{144, 124}, (x, z) -> 70);
        assertEquals("a stub perched at its start", List.of(stub.id()), tongues(settings, stub, 66, true, List.of()));
        assertEquals("a stub holding its start for the reach that feeds it, cascading into the junction", List.of(),
                WaterInvariants.confluenceTongues(scene(settings, List.of(stub, upstream), 66, true, List.of())));
        Reach riffling = tributary(settings, new int[]{144, 124}, new int[]{144, 136}, 2f, (x, z) -> z <= 124 ? 68 : 67);
        Reach feeding = straight(304L, settings.tiers().get(0), riffling.id(), 1f, new int[]{144, 56}, new int[]{144, 124}, (x, z) -> 68);
        assertEquals("a held start whose 1-block riffle into the junction shows as a 2-block step", List.of(riffling.id()),
                WaterInvariants.confluenceTongues(scene(settings, List.of(riffling, feeding), 67, true, List.of())));
    }

    @Test
    public void tonguesCountTheSiblingChannelAsReceivingWater() {
        HydrologySettings settings = PlannedScene.packSettings(SEED);
        Reach tributary = tributary(settings, new int[]{48, 112}, new int[]{144, 136}, 2f, (x, z) -> x >= 128 ? 66 : 70);
        assertEquals("perched tributary beside the sibling channel", List.of(tributary.id()), tongues(settings, tributary, 66, true, List.of()));
        assertEquals("without the sibling the perched stretch lies outside the pool", List.of(), tongues(settings, tributary, 66, false, List.of()));
    }

    @Test
    public void heldPondOutletMustFallIntoTheJunction() {
        HydrologySettings settings = PlannedScene.packSettings(SEED);
        int[] lip = {144, 120};
        int[] junction = {144, 136};
        Reach falling = outlet(tributary(settings, lip, junction, 2f, (x, z) -> z >= 130 ? 66 : 70));
        assertEquals("outlet held on its pond's lip 4 blocks above the junction, cascading into it", List.of(),
                tongues(settings, falling, 66, true, List.of(source(70, falling))));
        Reach stepping = outlet(tributary(settings, lip, junction, 2f, (x, z) -> z >= 130 ? 67 : 68));
        assertEquals("held outlet whose 1-block riffle into the junction shows as a 2-block step", List.of(stepping.id()),
                tongues(settings, stepping, 67, true, List.of(source(68, stepping))));
        Reach above = outlet(tributary(settings, lip, junction, 2f, (x, z) -> 70));
        assertEquals("held outlet whose own end stays above the junction", List.of(above.id()),
                tongues(settings, above, 66, true, List.of(source(70, above))));
    }

    @Test
    public void outletZoneStepThatShowsAsTwoBlocksStaysHeld() {
        HydrologySettings settings = PlannedScene.packSettings(SEED);
        int[] lip = {144, 120};
        int[] junction = {144, 136};
        IntBinaryOperator grade = (x, z) -> z >= 130 ? 66 : z >= 126 ? 69 : 70;
        Reach styled = zoneStyled(tributary(settings, lip, junction, 2f, grade), settings.seaLevel());
        assertEquals("the 1-block drop inside the outlet zone shows as 2 blocks", ReachStyle.CASCADE, styled.style()[3]);
        assertEquals("outlet zone stepping 1 block inside the zone, then cascading into the junction", List.of(),
                tongues(settings, styled, 66, true, List.of(pond(21L, 70, disk(144, 106, 8), styled.id()))));
        Reach unstyled = outlet(tributary(settings, lip, junction, 2f, grade));
        assertEquals("the same outlet zone with the step left unstyled", List.of(unstyled.id()),
                tongues(settings, unstyled, 66, true, List.of(pond(21L, 70, disk(144, 106, 8), unstyled.id()))));
    }

    @Test
    public void pondOutletWithAPerchedUnheldTailIsATongue() {
        HydrologySettings settings = PlannedScene.packSettings(SEED);
        Reach outlet = outlet(tributary(settings, new int[]{144, 56}, new int[]{144, 136}, 2f, (x, z) -> z >= 136 ? 66 : 70));
        Pond pond = pond(21L, 70, disk(144, 46, 10), outlet.id());
        assertEquals("outlet held only near its pond, cascading into the junction from a perched tail", List.of(outlet.id()),
                tongues(settings, outlet, 66, true, List.of(pond)));
    }

    @Test
    public void tongueScanKeepsTheInteriorPartOfAPoolCrossingTheEdgeMargin() {
        HydrologySettings settings = PlannedScene.packSettings(SEED);
        Reach tributary = tributary(settings, new int[]{36, 176}, new int[]{36, 256}, 4f, (x, z) -> z >= 256 ? 66 : 70);
        assertEquals("perched tributary columns inside the edge margin line", List.of(tributary.id()), tongues(settings, tributary, 66, true, List.of()));
    }

    @Test
    public void subSeaAirFindsWaterStandingBelowTheSeaSurface() {
        HydrologySettings settings = PlannedScene.packSettings(SEED);
        for (int level : new int[]{SEA_LEVEL - 4, SEA_LEVEL}) {
            Pond pond = pond(31L, level, disk(128, 128, 8), 0L);
            PlannedScene scene = PlannedScene.of(SyntheticTerrain.flat(SEA_LEVEL + 5D), settings, List.of(), List.of(pond));
            assertEquals("pond at " + level, level < SEA_LEVEL ? List.of(31L) : List.of(), WaterInvariants.subSeaAir(scene, SEA_LEVEL));
        }
    }

    @Test
    public void invariantsSkipFeaturesWithinTheEdgeMargin() {
        HydrologySettings settings = PlannedScene.packSettings(SEED);
        String skipped = "features within " + WaterInvariants.EDGE_MARGIN + " blocks of the scene edge are skipped";
        Reach offshore = straight(41L, settings.tiers().get(0), 0L, 2f, new int[]{36, 256}, new int[]{100, 256}, (x, z) -> 98);
        PlannedScene outlet = PlannedScene.of(SyntheticTerrain.flat(104D), settings, List.of(offshore),
                List.of(pond(42L, 100, disk(20, 256, 8), offshore.id())));
        assertEquals(skipped, List.of(), WaterInvariants.detachedOutlets(outlet));
        Reach tributary = tributary(settings, new int[]{16, 176}, new int[]{16, 256}, 2f, (x, z) -> z >= 256 ? 66 : 70);
        assertEquals(skipped, List.of(), tongues(settings, tributary, 66, true, List.of()));
        PlannedScene sunken = PlannedScene.of(SyntheticTerrain.flat(SEA_LEVEL + 5D), settings, List.of(),
                List.of(pond(43L, SEA_LEVEL - 4, disk(20, 256, 8), 0L)));
        assertEquals(skipped, List.of(), WaterInvariants.subSeaAir(sunken, SEA_LEVEL));
    }

    private static synchronized PlannedScene openFractal() {
        if (openFractal == null) {
            openFractal = PlannedScene.plan(SyntheticTerrain.openFractal(1L).withRainfall((x, z) -> RAINFALL), PlannedScene.packSettings(SEED), 2, 2);
        }
        return openFractal;
    }

    private static WindowKey owner(Reach reach) {
        int last = reach.xs().length - 1;
        return WindowKey.containing((int) Math.floor(reach.xs()[last]), (int) Math.floor(reach.zs()[last]));
    }

    private static double edgeDistance(Reach channel, int from, double x, double z) {
        double nearest = Double.POSITIVE_INFINITY;
        for (int segment = from; segment + 1 < channel.xs().length; segment++) {
            double t = WaterInvariants.along(channel, segment, x, z);
            nearest = Math.min(nearest, WaterInvariants.offset(channel, segment, t, x, z) - WaterInvariants.width(channel, segment, t));
        }
        return nearest;
    }

    private static Reach tributary(HydrologySettings settings, int[] from, int[] junction, float halfWidth, IntBinaryOperator grade) {
        return straight(303L, settings.tiers().get(0), 302L, halfWidth, from, junction, grade);
    }

    private static List<Long> tongues(HydrologySettings settings, Reach tributary, int receivingLevel, boolean sibling, List<Pond> ponds) {
        return WaterInvariants.confluenceTongues(scene(settings, List.of(tributary), receivingLevel, sibling, ponds));
    }

    private static PlannedScene scene(HydrologySettings settings, List<Reach> upstream, int receivingLevel, boolean sibling, List<Pond> ponds) {
        HydrologyTier river = settings.tiers().get(1);
        Reach tributary = upstream.get(0);
        int last = tributary.xs().length - 1;
        int[] junction = {Math.round(tributary.xs()[last]), Math.round(tributary.zs()[last])};
        Reach below = straight(302L, river, 0L, 8f, junction, new int[]{junction[0] + 104, junction[1]}, (x, z) -> receivingLevel);
        List<Reach> reaches = new ArrayList<>(upstream);
        reaches.add(below);
        if (sibling) {
            reaches.add(straight(301L, river, below.id(), 8f, new int[]{junction[0] - 112, junction[1]}, junction, (x, z) -> receivingLevel));
        }
        return PlannedScene.of(SyntheticTerrain.flat(75D), settings, reaches, ponds);
    }

    private static Reach outlet(Reach reach) {
        ReachStyle[] style = reach.style().clone();
        float along = 0f;
        for (int point = 0; point < style.length; point++) {
            if (point > 0) {
                along += (float) Math.hypot(reach.xs()[point] - reach.xs()[point - 1], reach.zs()[point] - reach.zs()[point - 1]);
            }
            if (along <= OUTLET_LENGTH && (style[point] == ReachStyle.RUN || style[point] == ReachStyle.RIFFLE)) {
                style[point] = ReachStyle.POND_OUTLET;
            }
        }
        return new Reach(reach.id(), reach.tier(), reach.downstreamId(), reach.xs(), reach.zs(), reach.grade(), reach.halfWidth(), reach.depth(),
                style, reach.catchment());
    }

    private static Reach zoneStyled(Reach reach, int seaLevel) {
        boolean[] zone = new boolean[reach.xs().length];
        float along = 0f;
        for (int point = 0; point < zone.length; point++) {
            if (point > 0) {
                along += (float) Math.hypot(reach.xs()[point] - reach.xs()[point - 1], reach.zs()[point] - reach.zs()[point - 1]);
            }
            zone[point] = along <= OUTLET_LENGTH;
        }
        ReachStyle[] style = ReachShaper.styles(reach.grade(), new boolean[zone.length], zone, reach.tier().waterfallMinDrop(), seaLevel, reach.tier().stepHeight());
        return new Reach(reach.id(), reach.tier(), reach.downstreamId(), reach.xs(), reach.zs(), reach.grade(), reach.halfWidth(), reach.depth(),
                style, reach.catchment());
    }

    private static Pond source(int level, Reach outlet) {
        return pond(21L, level, disk(144, 112, 10), outlet.id());
    }

    private static Reach straight(long id, HydrologyTier tier, long downstreamId, float halfWidth, int[] from, int[] to, IntBinaryOperator grade) {
        double length = Math.hypot(to[0] - from[0], to[1] - from[1]);
        int points = Math.max(2, (int) Math.round(length / SPACING) + 1);
        float[] xs = new float[points];
        float[] zs = new float[points];
        int[] grades = new int[points];
        float[] halfWidths = new float[points];
        float[] depths = new float[points];
        ReachStyle[] styles = new ReachStyle[points];
        for (int point = 0; point < points; point++) {
            float t = point / (float) (points - 1);
            xs[point] = from[0] + (to[0] - from[0]) * t;
            zs[point] = from[1] + (to[1] - from[1]) * t;
            grades[point] = grade.applyAsInt(Math.round(xs[point]), Math.round(zs[point]));
            halfWidths[point] = halfWidth;
            depths[point] = tier.depth(halfWidth);
            int drop = point == 0 ? 0 : grades[point - 1] - grades[point];
            styles[point] = drop >= tier.waterfallMinDrop() ? ReachStyle.WATERFALL
                    : drop >= 2 ? ReachStyle.CASCADE : drop == 1 ? ReachStyle.RIFFLE : ReachStyle.RUN;
        }
        return new Reach(id, tier, downstreamId, xs, zs, grades, halfWidths, depths, styles, 1000f);
    }

    private static Pond pond(long id, int level, PondMask mask, long outletReachId) {
        int centreX = mask.originX() + mask.width() / 2 * mask.step();
        int centreZ = mask.originZ() + mask.depth() / 2 * mask.step();
        return new Pond(id, PondKind.NATURAL, level, level - 6, centreX, centreZ, level + 1.5f, mask, outletReachId, 0, "water", centreX, centreZ);
    }

    private static PondMask disk(int centreX, int centreZ, int radius) {
        int originX = centreX - radius - 1;
        int originZ = centreZ - radius - 1;
        int size = 2 * radius + 3;
        long[] bits = new long[(size * size + 63) >>> 6];
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                if (Math.hypot(originX + i - centreX, originZ + j - centreZ) <= radius) {
                    int index = j * size + i;
                    bits[index >>> 6] |= 1L << index;
                }
            }
        }
        return new PondMask(originX, originZ, 1, size, size, bits);
    }
}
