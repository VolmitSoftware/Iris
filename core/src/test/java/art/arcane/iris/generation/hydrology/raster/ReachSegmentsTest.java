package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.config.IrisBankStyle;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyMaterials;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ReachSegmentsTest {
    private static final HydrologyTier TIER = new HydrologyTier("test", 0f, 1f, 4f, 1f, 0.4f, 1, true, 6, 2, 4, IrisBankStyle.SMOOTH,
            new HydrologyTier.Estuary(48, 2.5f), new HydrologyTier.Delta(0, 35f, 64), new IrisHydrologyMaterials(), "water", List.of());

    @Test
    public void coveredChannelLookupsMatchTheFullScan() {
        SplittableRandom random = new SplittableRandom(1337L);
        int hits = 0;
        for (int trial = 0; trial < 40; trial++) {
            int originX = random.nextInt(-2000, 2000) << 4;
            int originZ = random.nextInt(-2000, 2000) << 4;
            List<Reach> reaches = new ArrayList<>();
            int count = 1 + random.nextInt(12);
            for (int walk = 0; walk < count; walk++) {
                reaches.add(randomWalk(trial * 100L + walk, originX + 8, originZ + 8, random));
            }
            ReachSegments scanned = segments(reaches, originX, originZ);
            ReachSegments covered = segments(reaches, originX, originZ);
            covered.coverChannels(originX - 17, originZ - 17, originX + 32, originZ + 32);
            ReachCursor scan = new ReachCursor();
            ReachCursor cover = new ReachCursor();
            for (int z = originZ - 20; z <= originZ + 35; z++) {
                for (int x = originX - 20; x <= originX + 35; x++) {
                    ReachSegments.Scope scope = random.nextInt(4) == 0 ? ReachSegments.Scope.SHORE : ReachSegments.Scope.CHANNEL;
                    scanned.nearest(x, z, scope, scan);
                    covered.nearest(x, z, scope, cover);
                    String at = scope + " at " + x + "," + z;
                    assertSameChoice(at + " channel", scan.channel, cover.channel);
                    assertSameChoice(at + " bank", scan.bank, cover.bank);
                    assertSameChoice(at + " edge", scan.edge, cover.edge);
                    if (scan.channel.reach != null) {
                        hits++;
                    }
                }
            }
        }
        assertTrue("random walks cross the covered area, hits=" + hits, hits > 1000);
    }

    private static void assertSameChoice(String what, ReachCursor.Choice expected, ReachCursor.Choice actual) {
        assertSame(what, expected.reach, actual.reach);
        assertEquals(what, expected.segment, actual.segment);
        assertEquals(what, expected.index, actual.index);
        assertEquals(what, Double.doubleToLongBits(expected.t), Double.doubleToLongBits(actual.t));
        assertEquals(what, Double.doubleToLongBits(expected.distance), Double.doubleToLongBits(actual.distance));
    }

    private static ReachSegments segments(List<Reach> reaches, int originX, int originZ) {
        ReachSegments segments = new ReachSegments(16);
        for (Reach reach : reaches) {
            segments.addNear(reach, originX - 1f, originZ - 1f, originX + 16f, originZ + 16f, ChunkWaterRaster.MAX_SHORE_DISTANCE + 1f);
        }
        return segments;
    }

    private static Reach randomWalk(long id, int centerX, int centerZ, SplittableRandom random) {
        int points = 2 + random.nextInt(30);
        float[] xs = new float[points];
        float[] zs = new float[points];
        float[] halfWidth = new float[points];
        xs[0] = centerX + (float) random.nextDouble(-60D, 60D);
        zs[0] = centerZ + (float) random.nextDouble(-60D, 60D);
        halfWidth[0] = (float) random.nextDouble(0.5D, 9D);
        for (int point = 1; point < points; point++) {
            xs[point] = xs[point - 1] + (float) random.nextDouble(-12D, 12D);
            zs[point] = zs[point - 1] + (float) random.nextDouble(-12D, 12D);
            halfWidth[point] = random.nextInt(8) == 0 ? halfWidth[point - 1] : (float) random.nextDouble(0.5D, 9D);
        }
        int[] grade = new int[points];
        float[] depth = new float[points];
        ReachStyle[] style = new ReachStyle[points];
        Arrays.fill(grade, 64);
        Arrays.fill(depth, 1f);
        Arrays.fill(style, ReachStyle.RUN);
        return new Reach(id, TIER, -1L, xs, zs, grade, halfWidth, depth, style, 100f);
    }
}
