package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondKind;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import org.junit.AfterClass;
import org.junit.Test;

import java.util.List;
import java.util.function.IntBinaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PlannedSceneTest {
    private static final long SEED = 1337L;
    private static final int SEA_LEVEL = SyntheticTerrain.SEA_LEVEL;
    private static final double RAINFALL = 0.5D;
    private static final float SPACING = 2f;
    private static PlannedScene openFractal;

    @AfterClass
    public static void dropScene() {
        openFractal = null;
    }

    @Test
    public void reproducesTodaysOutletGap() {
        assertFalse("detached outlets on openFractal(1) with rainfall " + RAINFALL + " over 2x2 windows",
                WaterInvariants.detachedOutlets(openFractal()).isEmpty());
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
    public void reproducesTodaysConfluenceTongue() {
        HydrologySettings settings = PlannedScene.packSettings(SEED);
        for (int tributaryLevel : new int[]{70, 66}) {
            List<Long> tongues = tongues(settings, new int[]{144, 136}, new int[]{144, 56}, (x, z) -> z >= 136 ? 66 : tributaryLevel, true);
            assertEquals("tributary at " + tributaryLevel, tributaryLevel == 70 ? List.of(303L) : List.of(), tongues);
        }
    }

    @Test
    public void confluenceTonguesCoverSiblingFootprintsOutsideTheDownstreamCap() {
        HydrologySettings settings = PlannedScene.packSettings(SEED);
        IntBinaryOperator perchedUpstream = (x, z) -> x >= 128 ? 66 : 70;
        assertEquals("perched tributary inside the sibling channel", List.of(303L),
                tongues(settings, new int[]{144, 136}, new int[]{48, 112}, perchedUpstream, true));
        assertEquals("without the sibling the perched stretch lies outside the downstream cap", List.of(),
                tongues(settings, new int[]{144, 136}, new int[]{48, 112}, perchedUpstream, false));
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
        assertEquals(skipped, List.of(), tongues(settings, new int[]{16, 256}, new int[]{16, 176}, (x, z) -> z >= 256 ? 66 : 70, true));
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

    private static List<Long> tongues(HydrologySettings settings, int[] junction, int[] tributaryStart, IntBinaryOperator tributaryGrade,
                                      boolean sibling) {
        HydrologyTier river = settings.tiers().get(1);
        Reach below = straight(302L, river, 0L, 8f, junction, new int[]{junction[0] + 104, junction[1]}, (x, z) -> 66);
        Reach tributary = straight(303L, settings.tiers().get(0), below.id(), 2f, tributaryStart, junction, tributaryGrade);
        List<Reach> reaches = sibling
                ? List.of(straight(301L, river, below.id(), 8f, new int[]{junction[0] - 112, junction[1]}, junction, (x, z) -> 66), tributary, below)
                : List.of(tributary, below);
        return WaterInvariants.confluenceTongues(PlannedScene.of(SyntheticTerrain.flat(75D), settings, reaches, List.of()));
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
            styles[point] = drop >= tier.waterfallMinimumDrop() ? ReachStyle.WATERFALL
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
