package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondKind;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import org.junit.Test;

import java.util.List;
import java.util.function.IntBinaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PlannedSceneTest {
    private static final long SEED = 1337L;
    private static final int SEA_LEVEL = SyntheticTerrain.SEA_LEVEL;
    private static final float SPACING = 2f;
    private static PlannedScene openFractal;

    @Test
    public void reproducesTodaysOutletGap() {
        assertFalse("detached outlets on openFractal(1) over 2x2 windows", WaterInvariants.detachedOutlets(openFractal()).isEmpty());
    }

    @Test
    public void detachedOutletsSeparateLipOutletsFromOffshoreOutlets() {
        HydrologySettings settings = PlannedScene.packSettings(SEED);
        HydrologyTier stream = settings.tiers().get(0);
        Reach lip = straight(11L, stream, 0L, 2f, new int[]{69, 64}, new int[]{140, 64}, (x, z) -> 100);
        Reach offshore = straight(12L, stream, 0L, 2f, new int[]{80, 64}, new int[]{140, 64}, (x, z) -> 98);
        for (Reach outlet : List.of(lip, offshore)) {
            Pond pond = pond(21L, 100, disk(64, 64, 8), outlet.id());
            PlannedScene scene = PlannedScene.of(SyntheticTerrain.flat(104D), settings, List.of(outlet), List.of(pond));
            assertEquals("outlet " + outlet.id(), outlet == lip ? List.of() : List.of(outlet.id()), WaterInvariants.detachedOutlets(scene));
        }
    }

    @Test
    public void reproducesTodaysConfluenceTongue() {
        HydrologySettings settings = PlannedScene.packSettings(SEED);
        HydrologyTier river = settings.tiers().get(1);
        HydrologyTier stream = settings.tiers().get(0);
        Reach below = straight(302L, river, 0L, 8f, new int[]{16, 8}, new int[]{120, 8}, (x, z) -> 66);
        Reach main = straight(301L, river, below.id(), 8f, new int[]{-96, 8}, new int[]{16, 8}, (x, z) -> 66);
        for (int tributaryLevel : new int[]{70, 66}) {
            Reach tributary = straight(303L, stream, below.id(), 2f, new int[]{16, -72}, new int[]{16, 8}, (x, z) -> z >= 8 ? 66 : tributaryLevel);
            PlannedScene scene = PlannedScene.of(SyntheticTerrain.flat(75D), settings, List.of(main, tributary, below), List.of());
            assertEquals("tributary at " + tributaryLevel, tributaryLevel == 70 ? List.of(303L) : List.of(),
                    WaterInvariants.confluenceTongues(scene));
        }
    }

    @Test
    public void subSeaAirFindsWaterStandingBelowTheSeaSurface() {
        HydrologySettings settings = PlannedScene.packSettings(SEED);
        for (int level : new int[]{SEA_LEVEL - 4, SEA_LEVEL}) {
            Pond pond = pond(31L, level, disk(64, 64, 8), 0L);
            PlannedScene scene = PlannedScene.of(SyntheticTerrain.flat(SEA_LEVEL + 5D), settings, List.of(), List.of(pond));
            assertEquals("pond at " + level, level < SEA_LEVEL ? List.of(31L) : List.of(), WaterInvariants.subSeaAir(scene, SEA_LEVEL));
        }
    }

    private static synchronized PlannedScene openFractal() {
        if (openFractal == null) {
            openFractal = PlannedScene.plan(SyntheticTerrain.openFractal(1L), PlannedScene.packSettings(SEED), 2, 2);
        }
        return openFractal;
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
