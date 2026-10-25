package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.config.IrisBankStyle;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyMaterials;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondKind;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ReachIndexTest {
    private static final int BANK_BAND = 4;
    private static final HydrologyTier TIER = new HydrologyTier("test", 0f, 1f, 4f, 1f, 0.4f, 1, true, 6, BANK_BAND, IrisBankStyle.SMOOTH,
            false, new IrisHydrologyMaterials(), "water", List.of());

    @Test
    public void reachIndexReturnsReachesWithinHalo() {
        Reach inside = reach(1L, new float[]{-40f, 10f, 60f}, new float[]{8f, 8f, 9f}, 2f);
        Reach east = reach(2L, new float[]{41f, 41f}, new float[]{-30f, 50f}, 1f);
        Reach far = reach(3L, new float[]{1000f, 1040f}, new float[]{1000f, 1000f}, 3f);
        Reach diagonal = reach(4L, new float[]{-200f, 200f}, new float[]{-200f, 200f}, 1f);
        Pond pond = pond(10L, new PondMask(100, 100, 4, 3, 3, new long[]{0x1FFL}));
        Pond distant = pond(11L, new PondMask(-900, 400, 4, 2, 2, new long[]{0xFL}));
        ReachIndex index = ReachIndex.build(List.of(inside, east, far, diagonal), List.of(pond, distant));

        assertEquals(List.of(inside, diagonal), reaches(index, 0, 0, 0));
        assertEquals("corridor edge at x=36 is 20 blocks east of the chunk", List.of(inside, diagonal), reaches(index, 0, 0, 19));
        assertEquals(List.of(inside, east, diagonal), reaches(index, 0, 0, 20));
        assertEquals(List.of(far), reaches(index, 63, 62, 0));
        assertEquals(List.of(), reaches(index, 30, -30, 48));
        assertEquals(List.of(pond), ponds(index, 6, 6, 0));
        assertEquals("mask footprint ends at x=110", List.of(), ponds(index, 7, 6, 2));
        assertEquals(List.of(pond), ponds(index, 7, 6, 3));
        assertEquals(List.of(), ponds(index, 0, 0, 48));

        List<Reach> outReaches = new ArrayList<>(List.of(far));
        List<Pond> outPonds = new ArrayList<>(List.of(distant));
        index.candidates(6, 6, 0, outReaches, outPonds);
        assertEquals("candidates append in plan order", List.of(far, diagonal), outReaches);
        assertEquals(List.of(distant, pond), outPonds);

        List<Reach> none = new ArrayList<>();
        List<Pond> noPonds = new ArrayList<>();
        ReachIndex.build(List.of(), List.of()).candidates(0, 0, 48, none, noPonds);
        assertTrue(none.isEmpty() && noPonds.isEmpty());

        SplittableRandom random = new SplittableRandom(1337L);
        List<Reach> walks = new ArrayList<>();
        for (int walk = 0; walk < 60; walk++) {
            walks.add(randomWalk(100L + walk, random));
        }
        List<Pond> scattered = new ArrayList<>();
        for (int count = 0; count < 30; count++) {
            int step = 1 + random.nextInt(8);
            int width = 1 + random.nextInt(12);
            int depth = 1 + random.nextInt(12);
            scattered.add(pond(500L + count, new PondMask(random.nextInt(-700, 700), random.nextInt(-700, 700), step, width, depth,
                    new long[(width * depth + 63) >>> 6])));
        }
        ReachIndex randomIndex = ReachIndex.build(walks, scattered);
        int matched = 0;
        for (int query = 0; query < 400; query++) {
            int chunkX = random.nextInt(-50, 50);
            int chunkZ = random.nextInt(-50, 50);
            int halo = random.nextInt(0, 64);
            List<Reach> expectedReaches = new ArrayList<>();
            for (Reach reach : walks) {
                if (corridorMeets(reach, chunkX, chunkZ, halo)) {
                    expectedReaches.add(reach);
                }
            }
            List<Pond> expectedPonds = new ArrayList<>();
            for (Pond candidate : scattered) {
                if (maskMeets(candidate.mask(), chunkX, chunkZ, halo)) {
                    expectedPonds.add(candidate);
                }
            }
            List<Reach> gotReaches = new ArrayList<>();
            List<Pond> gotPonds = new ArrayList<>();
            randomIndex.candidates(chunkX, chunkZ, halo, gotReaches, gotPonds);
            assertEquals("chunk " + chunkX + "," + chunkZ + " halo " + halo, expectedReaches, gotReaches);
            assertEquals("chunk " + chunkX + "," + chunkZ + " halo " + halo, expectedPonds, gotPonds);
            matched += gotReaches.size() + gotPonds.size();
        }
        assertTrue("random queries found candidates: " + matched, matched > 200);
    }

    @Test
    public void pointInsideWideTrunkNearTributaryCentreResolvesToTrunk() {
        float[] trunkXs = new float[11];
        for (int point = 0; point < trunkXs.length; point++) {
            trunkXs[point] = -100f + 20f * point;
        }
        Reach trunk = reach(50L, trunkXs, new float[11], 12f);
        Reach tributary = reach(7L, new float[]{20f, 20f}, new float[]{80f, 0f}, 1f);

        ReachHit alone = ReachIndex.nearest(List.of(tributary), 17D, 8D);
        assertEquals("the tributary alone only offers a bank", ReachHit.Coverage.BANK, alone.coverage());
        assertEquals(3f, alone.distance(), 1e-5f);
        for (List<Reach> candidates : List.of(List.of(tributary, trunk), List.of(trunk, tributary))) {
            ReachHit hit = ReachIndex.nearest(candidates, 17D, 8D);
            assertSame("inside the trunk, nearer the tributary centre", trunk, hit.reach());
            assertEquals(ReachHit.Coverage.CHANNEL, hit.coverage());
            assertEquals(5, hit.segment());
            assertEquals(0.85f, hit.t(), 1e-6f);
            assertEquals(8f, hit.distance(), 1e-5f);
        }
        ReachHit both = ReachIndex.nearest(List.of(trunk, tributary), 20.5D, 6D);
        assertSame("inside both channels the nearer covering centre wins", tributary, both.reach());
        assertEquals(ReachHit.Coverage.CHANNEL, both.coverage());
        assertEquals(0.5f, both.distance(), 1e-5f);
    }

    @Test
    public void bankHitWinsOverCloserUncoveredReach() {
        Reach wide = new Reach(3L, tier("wide-bank", 6), -1L, new float[]{0f, 0f}, new float[]{-50f, 50f}, new int[]{64, 64},
                new float[]{2f, 2f}, new float[]{1f, 1f}, new ReachStyle[]{ReachStyle.RUN, ReachStyle.RUN}, 100f);
        Reach narrow = new Reach(2L, tier("no-bank", 0), -1L, new float[]{10f, 10f}, new float[]{-50f, 50f}, new int[]{64, 64},
                new float[]{0.5f, 0.5f}, new float[]{1f, 1f}, new ReachStyle[]{ReachStyle.RUN, ReachStyle.RUN}, 100f);
        for (List<Reach> candidates : List.of(List.of(narrow, wide), List.of(wide, narrow))) {
            ReachHit hit = ReachIndex.nearest(candidates, 7D, 0D);
            assertSame("within the wide reach's bank band", wide, hit.reach());
            assertEquals(ReachHit.Coverage.BANK, hit.coverage());
            assertEquals(7f, hit.distance(), 1e-5f);
            assertEquals(0.5f, hit.t(), 1e-6f);
        }
    }

    @Test
    public void pointOutsideEveryCorridorResolvesToNearest() {
        Reach west = reach(9L, new float[]{-10f, -10f}, new float[]{-40f, 40f}, 1f);
        Reach east = reach(4L, new float[]{10f, 10f}, new float[]{-40f, 40f}, 1f);
        Reach far = reach(1L, new float[]{500f, 520f}, new float[]{0f, 0f}, 1f);
        ReachHit nearest = ReachIndex.nearest(List.of(west, east, far), 60D, 20D);
        assertSame(east, nearest.reach());
        assertEquals(ReachHit.Coverage.OUTSIDE, nearest.coverage());
        assertEquals(50f, nearest.distance(), 1e-5f);
        for (List<Reach> candidates : List.of(List.of(west, east, far), List.of(far, east, west))) {
            ReachHit tie = ReachIndex.nearest(candidates, 0D, 0D);
            assertSame("equal distances resolve to the lower id", east, tie.reach());
            assertEquals(ReachHit.Coverage.OUTSIDE, tie.coverage());
            assertEquals(10f, tie.distance(), 1e-5f);
        }
        assertNull(ReachIndex.nearest(List.of(), 0D, 0D));
    }

    @Test
    public void feedersGroupOwnedReachesByDownstreamId() {
        Reach west = linked(21L, 30L, -20f);
        Reach east = linked(22L, 30L, 20f);
        Reach other = linked(23L, 31L, 60f);
        ReachIndex index = ReachIndex.build(List.of(west, east, other), List.of());
        assertEquals(List.of(west, east), index.feeders(30L));
        assertEquals(List.of(other), index.feeders(31L));
        assertTrue(index.feeders(99L).isEmpty());
    }

    private static Reach linked(long id, long downstreamId, float x) {
        Reach base = reach(id, new float[]{x, x}, new float[]{-10f, 10f}, 1f);
        return new Reach(id, TIER, downstreamId, base.xs(), base.zs(), base.grade(), base.halfWidth(), base.depth(), base.style(), 100f);
    }

    private static HydrologyTier tier(String name, int bankBand) {
        return new HydrologyTier(name, 0f, 0.5f, 4f, 1f, 0.4f, 1, true, 6, bankBand, IrisBankStyle.SMOOTH, false, new IrisHydrologyMaterials(),
                "water", List.of());
    }

    private static List<Reach> reaches(ReachIndex index, int chunkX, int chunkZ, int halo) {
        List<Reach> reaches = new ArrayList<>();
        index.candidates(chunkX, chunkZ, halo, reaches, new ArrayList<>());
        return reaches;
    }

    private static List<Pond> ponds(ReachIndex index, int chunkX, int chunkZ, int halo) {
        List<Pond> ponds = new ArrayList<>();
        index.candidates(chunkX, chunkZ, halo, new ArrayList<>(), ponds);
        return ponds;
    }

    private static boolean corridorMeets(Reach reach, int chunkX, int chunkZ, int halo) {
        float minX = chunkX * 16f - halo;
        float maxX = chunkX * 16f + 16f + halo;
        float minZ = chunkZ * 16f - halo;
        float maxZ = chunkZ * 16f + 16f + halo;
        for (int point = 0; point + 1 < reach.xs().length; point++) {
            float radius = Math.max(reach.halfWidth()[point], reach.halfWidth()[point + 1]) + reach.tier().bankBand();
            if (Math.min(reach.xs()[point], reach.xs()[point + 1]) - radius <= maxX
                    && Math.max(reach.xs()[point], reach.xs()[point + 1]) + radius >= minX
                    && Math.min(reach.zs()[point], reach.zs()[point + 1]) - radius <= maxZ
                    && Math.max(reach.zs()[point], reach.zs()[point + 1]) + radius >= minZ) {
                return true;
            }
        }
        return false;
    }

    private static boolean maskMeets(PondMask mask, int chunkX, int chunkZ, int halo) {
        int half = mask.step() >> 1;
        int minX = mask.originX() - half;
        int minZ = mask.originZ() - half;
        int maxX = minX + mask.width() * mask.step();
        int maxZ = minZ + mask.depth() * mask.step();
        return minX < chunkX * 16 + 16 + halo && maxX > chunkX * 16 - halo
                && minZ < chunkZ * 16 + 16 + halo && maxZ > chunkZ * 16 - halo;
    }

    private static Reach randomWalk(long id, SplittableRandom random) {
        int points = 2 + random.nextInt(40);
        float[] xs = new float[points];
        float[] zs = new float[points];
        float[] halfWidth = new float[points];
        xs[0] = (float) random.nextDouble(-800D, 800D);
        zs[0] = (float) random.nextDouble(-800D, 800D);
        halfWidth[0] = (float) random.nextDouble(0.5D, 12D);
        for (int point = 1; point < points; point++) {
            xs[point] = xs[point - 1] + (float) random.nextDouble(-24D, 24D);
            zs[point] = zs[point - 1] + (float) random.nextDouble(-24D, 24D);
            halfWidth[point] = (float) random.nextDouble(0.5D, 12D);
        }
        return reach(id, xs, zs, halfWidth);
    }

    private static Reach reach(long id, float[] xs, float[] zs, float halfWidth) {
        float[] widths = new float[xs.length];
        Arrays.fill(widths, halfWidth);
        return reach(id, xs, zs, widths);
    }

    private static Reach reach(long id, float[] xs, float[] zs, float[] halfWidth) {
        int points = xs.length;
        int[] grade = new int[points];
        float[] depth = new float[points];
        ReachStyle[] style = new ReachStyle[points];
        Arrays.fill(grade, 64);
        Arrays.fill(depth, 1f);
        Arrays.fill(style, ReachStyle.RUN);
        return new Reach(id, TIER, -1L, xs, zs, grade, halfWidth, depth, style, 100f);
    }

    private static Pond pond(long id, PondMask mask) {
        return new Pond(id, PondKind.NATURAL, 70, 66, mask.originX(), mask.originZ(), 70.5f, mask, -1L, 0, "water", mask.originX(),
                mask.originZ());
    }
}
