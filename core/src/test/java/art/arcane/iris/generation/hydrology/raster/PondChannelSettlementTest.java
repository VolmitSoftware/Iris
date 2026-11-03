package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.config.IrisBankStyle;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.IndexedWindow;
import art.arcane.iris.generation.hydrology.plan.RegionKey;
import art.arcane.iris.generation.hydrology.plan.RegionPlan;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.plan.WindowPlan;
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
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PondChannelSettlementTest {
    private static final WindowKey WINDOW = new WindowKey(0, 0);
    private static final int SEA_LEVEL = 40;
    private static final long SEED = 1337L;

    @Test
    public void aLowerChannelDrainsThePondWhereOnlyItsWidthOverlaps() {
        for (int offset : new int[]{-512, -19, 0, 513}) {
            Pond pond = pond(1L, PondKind.CHAINED, 100, circle(offset, offset, 8));
            Reach river = line(2L, offset + 12, offset - 20, offset + 12, offset + 20, 5f, 95, 95);
            assertEquals(94, settle(List.of(river), pond));
        }
    }

    @Test
    public void cardinalWaterContactDrainsButDiagonalProximityDoesNot() {
        Pond pond = pond(1L, PondKind.NATURAL, 100, circle(0, 0, 0));
        Reach cardinal = line(2L, 1, 0, 1, 1, 0.25f, 94, 94);
        Reach diagonal = line(2L, 1, 1, 1, 2, 0.25f, 94, 94);

        assertEquals(94, settle(List.of(cardinal), pond));
        assertEquals(100, settle(List.of(diagonal), pond));
    }

    @Test
    public void drySeparationHigherChannelsAndFixedLakesKeepTheirLevels() {
        PondMask mask = circle(0, 0, 8);
        Pond pond = pond(1L, PondKind.NATURAL, 100, mask);
        Pond lake = pond(1L, PondKind.LAKE, 100, mask);
        Reach separate = line(2L, 15, -20, 15, 20, 4f, 94, 94);
        Reach higher = line(2L, 0, -20, 0, 20, 4f, 104, 104);
        Reach lower = line(2L, 0, -20, 0, 20, 4f, 94, 94);

        assertEquals(100, settle(List.of(separate), pond));
        assertEquals(100, settle(List.of(higher), pond));
        assertEquals(100, settle(List.of(lower), lake));
    }

    @Test
    public void equalCanonicalHeadsDoNotTriggerAnotherReflood() {
        Pond pond = pond(1L, PondKind.CHAINED, 101, circle(0, 0, 8));
        Reach river = line(2L, 0, -20, 0, 20, 4f, 100, 100);

        assertEquals(101, settle(List.of(river), pond));
    }

    @Test
    public void theLowestCoveringChannelSettlesThePondLikeTheRasterOwner() {
        Pond pond = pond(1L, PondKind.CHAINED, 100, circle(0, 0, 4));
        Reach lower = line(4L, 2, -20, 2, 20, 8f, 94, 94);
        Reach nearer = line(3L, 0, -20, 0, 20, 8f, 100, 100);
        Pond single = pond(2L, PondKind.NATURAL, 100, circle(0, 0, 0));
        Reach alongside = line(3L, 2, -20, 2, 20, 8f, 100, 100);

        assertEquals(94, settle(List.of(lower, nearer), single));
        assertEquals(94, settle(List.of(nearer, lower), single));
        assertEquals(94, settle(List.of(lower, alongside), pond));
        assertEquals(94, settle(List.of(alongside, lower), pond));
    }

    @Test
    public void projectedStationChoosesOnlyTheTouchingSideOfADrop() {
        Reach river = line(2L, -20, 0, 20, 0, 2f, 100, 94);

        assertEquals(100, settle(List.of(river), pond(1L, PondKind.NATURAL, 100, circle(-5, 0, 0))));
        assertEquals(94, settle(List.of(river), pond(1L, PondKind.NATURAL, 100, circle(5, 0, 0))));
    }

    @Test
    public void adjacentPondsSettleIndependentlyOfReachAndPondOrder() {
        Pond first = pond(1L, PondKind.CHAINED, 104, circle(0, 0, 8));
        Pond second = pond(2L, PondKind.CHAINED, 102, circle(17, 0, 8));
        Reach across = line(3L, -20, 0, 40, 0, 4f, 96, 96);
        Reach lower = line(4L, 17, -20, 17, 20, 4f, 94, 94);

        assertArrayEquals(new int[]{96, 94}, PondChannelSettlement.levels(WINDOW, List.of(across, lower), List.of(first, second),
                new int[]{104, 102}, SEED, SEA_LEVEL));
        assertArrayEquals(new int[]{94, 96}, PondChannelSettlement.levels(WINDOW, List.of(lower, across), List.of(second, first),
                new int[]{102, 104}, SEED, SEA_LEVEL));
    }

    @Test
    public void settlementUsesTheRasterBrinkRule() {
        Reach river = line(2L, 504, 108, 504, 148, 6f, 100, 88);
        int curved = 0;
        for (long seed = 1L; seed <= 24L; seed++) {
            HydrologySettings settings = HydrologySettings.compile(new IrisHydrology(), seed, SEA_LEVEL, -64, 320);
            ChunkRasterizer rasterizer = new ChunkRasterizer(settings, ChannelFluids.TIER);
            IndexedWindow window = new IndexedWindow(new WindowPlan(WINDOW, List.of(river), List.of()), ReachIndex.build(WINDOW, List.of(river), List.of()));
            for (int z = 124; z <= 132; z++) {
                Pond pond = pond(1L, PondKind.NATURAL, 94, circle(504, z, 0));
                int lowest = Math.min(94, Math.min(rasterHead(rasterizer, window, 504, z), Math.min(rasterHead(rasterizer, window, 505, z),
                        Math.min(rasterHead(rasterizer, window, 503, z), Math.min(rasterHead(rasterizer, window, 504, z + 1), rasterHead(rasterizer, window, 504, z - 1))))));
                int settled = PondChannelSettlement.levels(WINDOW, List.of(river), List.of(pond), new int[]{94}, seed, SEA_LEVEL)[0];
                assertEquals("seed " + seed + " pond at z " + z, lowest, settled);
                boolean straightTouches = z >= 127;
                curved += straightTouches != (settled < 94) ? 1 : 0;
            }
        }
        assertTrue("ponds the curve moved on or off the lower water: " + curved, curved >= 12);
    }

    @Test
    public void settlementTreatsAStoppedTongueAsAStraightFall() {
        Reach river = line(2L, 504, 108, 504, 148, 6f, 100, 88);
        int stopped = 0;
        for (long seed = 1L; seed <= 24L; seed++) {
            Reach kept = TongueGuard.guard(river, (x, z) -> z < 128 ? 102 : 90, seed, SEA_LEVEL, 6);
            stopped += kept.straightFalls().length;
            assertSettlementMatchesTheRaster(seed, kept, 500, 508, 120, 136, 1);
        }
        assertTrue("stopped tongues " + stopped, stopped > 0);
    }

    @Test
    public void settlementFollowsAWideAngledBrink() {
        Reach river = line(2L, 400, 100, 460, 160, 16f, 100, 88);
        int moved = 0;
        for (long seed = 1L; seed <= 24L; seed++) {
            moved += assertSettlementMatchesTheRaster(seed, river, 418, 442, 118, 142, 3);
        }
        assertTrue("ponds on the lower water: " + moved, moved > 0);
    }

    @Test
    public void settlementSeesAHorseshoeWhereTheChannelNarrowsAtTheFall() {
        Reach river = narrowing(2L);
        int moved = 0;
        for (long seed = 1L; seed <= 24L; seed++) {
            moved += assertSettlementMatchesTheRaster(seed, river, 392, 408, 121, 128, 1);
        }
        assertTrue("ponds on the lower water: " + moved, moved > 0);
    }

    private static int assertSettlementMatchesTheRaster(long seed, Reach river, int minX, int maxX, int minZ, int maxZ, int stride) {
        HydrologySettings settings = HydrologySettings.compile(new IrisHydrology(), seed, SEA_LEVEL, -64, 320);
        ChunkRasterizer rasterizer = new ChunkRasterizer(settings, ChannelFluids.TIER);
        IndexedWindow window = new IndexedWindow(new WindowPlan(WINDOW, List.of(river), List.of()), ReachIndex.build(WINDOW, List.of(river), List.of()));
        Map<Long, ChunkWaterRaster> rasters = new HashMap<>();
        int lowered = 0;
        for (int x = minX; x <= maxX; x += stride) {
            for (int z = minZ; z <= maxZ; z += stride) {
                int lowest = 94;
                for (int[] column : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    lowest = Math.min(lowest, rasterHead(rasterizer, window, x + column[0], z + column[1], rasters));
                }
                Pond pond = pond(1L, PondKind.NATURAL, 94, circle(x, z, 0));
                int settled = PondChannelSettlement.levels(WINDOW, List.of(river), List.of(pond), new int[]{94}, seed, SEA_LEVEL)[0];
                assertEquals("seed " + seed + " pond at " + x + "," + z, lowest, settled);
                lowered += settled < 94 ? 1 : 0;
            }
        }
        return lowered;
    }

    private static Reach narrowing(long id) {
        int points = 21;
        float[] xs = new float[points];
        float[] zs = new float[points];
        int[] grade = new int[points];
        float[] widths = new float[points];
        float[] depth = new float[points];
        ReachStyle[] styles = new ReachStyle[points];
        for (int point = 0; point < points; point++) {
            xs[point] = 400f;
            zs[point] = 108f + 2f * point;
            grade[point] = zs[point] < 130f ? 100 : 88;
            widths[point] = zs[point] < 128f ? 40f : 6f;
            depth[point] = 2f;
            styles[point] = ReachStyle.RUN;
        }
        HydrologyTier tier = new HydrologyTier("river", 0f, 40f, 40f, 0f, 0.5f, 2, true, 6, 2, 0, IrisBankStyle.SMOOTH,
                new HydrologyTier.Estuary(48, 2.5f), new HydrologyTier.Delta(0, 35f, 64), null, "water", List.of());
        return new Reach(id, tier, 0L, xs, zs, grade, widths, depth, styles, 1000f);
    }

    private static int rasterHead(ChunkRasterizer rasterizer, IndexedWindow window, int x, int z, Map<Long, ChunkWaterRaster> rasters) {
        int chunkX = Math.floorDiv(x, 16);
        int chunkZ = Math.floorDiv(z, 16);
        ChunkWaterRaster raster = rasters.computeIfAbsent(((long) chunkX << 32) | (chunkZ & 0xffffffffL), key -> {
            List<RegionPlan> regions = new ArrayList<>();
            for (RegionKey region : ChunkRasterizer.lakeRegions(chunkX, chunkZ)) {
                regions.add(new RegionPlan(region, List.of(), List.of()));
            }
            return rasterizer.rasterize(chunkX, chunkZ, (a, b) -> 110D, List.of(window), regions);
        });
        int head = raster.waterHead(Math.floorMod(x, 16), Math.floorMod(z, 16));
        return head == Integer.MIN_VALUE ? Integer.MAX_VALUE : head;
    }

    private static int rasterHead(ChunkRasterizer rasterizer, IndexedWindow window, int x, int z) {
        int chunkX = Math.floorDiv(x, 16);
        int chunkZ = Math.floorDiv(z, 16);
        List<RegionPlan> regions = new ArrayList<>();
        for (RegionKey key : ChunkRasterizer.lakeRegions(chunkX, chunkZ)) {
            regions.add(new RegionPlan(key, List.of(), List.of()));
        }
        ChunkWaterRaster raster = rasterizer.rasterize(chunkX, chunkZ, (a, b) -> 110D, List.of(window), regions);
        int head = raster.waterHead(Math.floorMod(x, 16), Math.floorMod(z, 16));
        return head == Integer.MIN_VALUE ? Integer.MAX_VALUE : head;
    }

    private static int settle(List<Reach> reaches, Pond pond) {
        return PondChannelSettlement.levels(WINDOW, reaches, List.of(pond), new int[]{pond.level()}, SEED, SEA_LEVEL)[0];
    }

    private static Pond pond(long id, PondKind kind, int level, PondMask mask) {
        return new Pond(id, kind, level, 90, mask.originX(), mask.originZ(), level + 1f, mask, 0L, 0, "water",
                mask.originX(), mask.originZ());
    }

    private static PondMask circle(int x, int z, int radius) {
        int size = 2 * radius + 1;
        long[] bits = new long[(size * size + 63) >>> 6];
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    int cell = (dz + radius) * size + dx + radius;
                    bits[cell >>> 6] |= 1L << cell;
                }
            }
        }
        return new PondMask(x - radius, z - radius, 1, size, size, bits);
    }

    private static Reach line(long id, int x0, int z0, int x1, int z1, float width, int first, int last) {
        HydrologyTier tier = new HydrologyTier("river", 0f, width, width, 0f, 0.5f, 2, true, 6, 2, 0, IrisBankStyle.SMOOTH,
                new HydrologyTier.Estuary(48, 2.5f), new HydrologyTier.Delta(0, 35f, 64), null, "water", List.of());
        float[] widths = new float[]{width, width};
        float[] depth = new float[]{2f, 2f};
        ReachStyle[] styles = new ReachStyle[2];
        Arrays.fill(styles, ReachStyle.RUN);
        return new Reach(id, tier, 0L, new float[]{x0, x1}, new float[]{z0, z1}, new int[]{first, last}, widths, depth, styles, 1000f);
    }
}
