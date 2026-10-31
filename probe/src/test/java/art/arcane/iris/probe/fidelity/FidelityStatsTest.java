package art.arcane.iris.probe.fidelity;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class FidelityStatsTest {
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;

    @Test
    public void strideSamplingCounts() {
        int[] strides = {1, 3, 4, 16};
        long[] expected = {256L, 36L, 16L, 1L};
        for (int i = 0; i < strides.length; i++) {
            FidelityStats stats = new FidelityStats(MIN_Y, MAX_Y);
            stats.accept(chunk(), strides[i]);
            assertEquals(expected[i], stats.columns());
            assertEquals(expected[i], stats.heightColumns());
            assertEquals(expected[i], stats.blockColumns());
            assertEquals(expected[i], stats.biomeColumns().values().stream().mapToLong(Long::longValue).sum());
        }
    }

    @Test
    public void strideOutsideChunkIsRejected() {
        FidelityStats stats = new FidelityStats(MIN_Y, MAX_Y);
        assertThrows(IllegalArgumentException.class, () -> stats.accept(chunk(), 0));
        assertThrows(IllegalArgumentException.class, () -> stats.accept(chunk(), 17));
    }

    @Test
    public void mergeIsAssociative() {
        FidelityStats a = stats(chunk().surface(40).biomes("plains", "forest", 4).caves(-20, 0, 3).walls(2));
        FidelityStats b = stats(chunk().surface(40).sea(62).biomes("ocean", "ocean", 16).overhang(3, 20, 30));
        FidelityStats c = stats(chunk().surface(90).trees(5, 6).caves(-64, -30, 16).lavaPool(-64, -60, 3).noBlocks());

        FidelityStats left = a.merge(b).merge(c);
        FidelityStats right = a.merge(b.merge(c));
        FidelityStats combined = new FidelityStats(MIN_Y, MAX_Y);
        combined.accept(chunk().surface(40).biomes("plains", "forest", 4).caves(-20, 0, 3).walls(2), 1);
        combined.accept(chunk().surface(40).sea(62).biomes("ocean", "ocean", 16).overhang(3, 20, 30), 1);
        combined.accept(chunk().surface(90).trees(5, 6).caves(-64, -30, 16).lavaPool(-64, -60, 3).noBlocks(), 1);

        assertSameStats(left, right);
        assertSameStats(left, combined);
        assertSameStats(a, stats(chunk().surface(40).biomes("plains", "forest", 4).caves(-20, 0, 3).walls(2)));
        assertEquals(768L, left.columns());
    }

    @Test
    public void columnsCountBiomeHeightAndWater() {
        FidelityStats stats = new FidelityStats(MIN_Y, MAX_Y);
        stats.accept(new FidelityColumn("plains", 70, false));
        stats.accept(new FidelityColumn("ocean", 20, true));
        stats.accept(new FidelityColumn(null, MIN_Y - 1, false));

        assertEquals(3L, stats.columns());
        assertEquals(2L, stats.heightColumns());
        assertEquals(1L, stats.fluidColumns());
        assertEquals(0.5D, stats.landFraction(), 0.0D);
        assertEquals(20.0D, stats.heightQuantile(0.5D), 0.0D);
        assertEquals(70.0D, stats.heightQuantile(1.0D), 0.0D);
        assertEquals(Map.of("plains", 1L, "ocean", 1L), stats.biomeColumns());
        assertEquals(0L, stats.blockColumns());
        assertThrows(IllegalArgumentException.class, () -> stats.accept(new FidelityColumn("peak", MAX_Y, false)));
    }

    @Test
    public void mergeRejectsDifferentHeightRanges() {
        FidelityStats overworld = new FidelityStats(MIN_Y, MAX_Y);
        FidelityStats shallow = new FidelityStats(0, 256);
        assertThrows(IllegalArgumentException.class, () -> overworld.merge(shallow));
    }

    @Test
    public void decorationAboveTerrainIsCountedButNotMeasuredAsHeight() {
        FidelityStats stats = stats(chunk().surface(70).trees(4, 6));

        assertEquals(70.0D, stats.heightQuantile(1.0D), 0.0D);
        assertEquals(64L, stats.decorationColumns());
        assertEquals(256L, stats.blockColumns());
        assertEquals(250_000.0D, stats.decorationPerSquareKilometre(), 1.0E-6D);
    }

    @Test
    public void noiseOnlyChunksSkipDecoration() {
        FidelityStats stats = stats(chunk().trees(4, 6).noBlocks());

        assertEquals(0L, stats.blockColumns());
        assertEquals(0L, stats.decorationColumns());
    }

    @Test
    public void subsurfaceOpenVoxelsAreSharedPerBand() {
        FidelityStats stats = stats(chunk().surface(64).caves(-64, -48, 4));

        assertEquals(24, stats.bandCount());
        assertEquals(0.25D, stats.caveShare(0), 1.0E-12D);
        assertEquals(0.0D, stats.caveShare(1), 0.0D);
        assertEquals(0.0D, stats.voidShare(0), 0.0D);
        assertEquals(256L * 16L, stats.subsurfaceVoxels()[7]);
        assertEquals(0L, stats.subsurfaceVoxels()[8]);
        assertEquals(1.0D, stats.landFraction(), 0.0D);
    }

    @Test
    public void snowCappedCanopyDoesNotRoofCaves() {
        FidelityStats stats = stats(chunk().surface(64).canopy(4, 90, "minecraft:snow"));

        assertEquals(0L, sum(stats.caveVoxels()) + sum(stats.voidVoxels()));
        assertEquals(0L, stats.subsurfaceVoxels()[(80 - MIN_Y) / 16]);
        assertEquals(256L * 16L, stats.subsurfaceVoxels()[(48 - MIN_Y) / 16]);
    }

    @Test
    public void cavesUnderACanopyStillCount() {
        FidelityStats stats = stats(chunk().surface(64).caves(48, 64, 4).canopy(4, 90, "minecraft:snow"));

        assertEquals(0.25D, stats.caveShare((48 - MIN_Y) / 16), 1.0E-12D);
    }

    @Test
    public void openSpaceUnderAFloatingRoofIsATerrainVoid() {
        FidelityStats stats = stats(chunk().surface(64).roof(4, 90, 95));

        int band = (80 - MIN_Y) / 16;
        assertEquals(64L * 15L, stats.subsurfaceVoxels()[band]);
        assertEquals(64L * 10L, stats.voidVoxels()[band]);
        assertEquals(0L, stats.caveVoxels()[band]);
    }

    @Test
    public void overhangsBelowTheTerrainTopAreVoidsAndCarvedSpaceIsCave() {
        FidelityStats stats = stats(chunk().surface(100).overhang(4, 80, 96).caves(0, 16, 8));

        int overhangBand = (80 - MIN_Y) / 16;
        int caveBand = (0 - MIN_Y) / 16;
        assertEquals(0.25D, stats.voidShare(overhangBand), 1.0E-12D);
        assertEquals(0.0D, stats.caveShare(overhangBand), 0.0D);
        assertEquals(0.5D, stats.caveShare(caveBand), 1.0E-12D);
        assertEquals(0.0D, stats.voidShare(caveBand), 0.0D);
        assertEquals(256L * 16L, stats.subsurfaceVoxels()[overhangBand]);
    }

    @Test
    public void deepLavaShareCoversSubsurfaceBelowTheLavaLine() {
        FidelityStats stats = stats(chunk().surface(64).lavaPool(-64, -56, 4).caves(-56, -40, 4));

        assertEquals(256L * (FidelityStats.DEEP_LAVA_LINE - MIN_Y), stats.deepVoxels());
        assertEquals(4L * 16L * 8L, stats.deepLavaVoxels());
        assertEquals(0.2D, stats.deepLavaShare(), 1.0E-12D);
    }

    @Test
    public void wallShareCountsSolidSubsurfaceVoxelsOfInteriorColumns() {
        FidelityStats stats = stats(chunk().surface(64).caves(-20, 0, 4).walls(1));

        assertEquals(14L * 20L, stats.wallVoxels());
        assertEquals(42L * 108L + 154L * 128L, stats.wallSolidVoxels());
        assertEquals(280.0D / 24_248.0D, stats.wallShare(), 1.0E-12D);
    }

    @Test
    public void oneBlockWallsCountSolidVoxelsBesideCaveAir() {
        FidelityStats stats = stats(chunk().surface(64).caves(-20, 0, 4).walls(3));

        assertEquals(3L * 14L * 20L, stats.wallVoxels());
        assertEquals(14L * 20L, stats.oneBlockWallVoxels());
        assertEquals(280.0D / 24_248.0D, stats.oneBlockWallShare(), 1.0E-12D);
    }

    @Test
    public void chunksWithoutWallsAddNoWallVoxels() {
        FidelityStats stats = stats(chunk().surface(64).caves(-20, 0, 4));

        assertEquals(0L, stats.wallSolidVoxels());
        assertEquals(0L, stats.wallVoxels());
        assertEquals(0L, stats.oneBlockWallVoxels());
        assertThrows(IllegalStateException.class, stats::wallShare);
        assertThrows(IllegalStateException.class, stats::oneBlockWallShare);
    }

    @Test
    public void submergedColumnsCountAsFluid() {
        FidelityStats stats = stats(chunk().surface(40).sea(62));

        assertEquals(256L, stats.fluidColumns());
        assertEquals(0.0D, stats.landFraction(), 0.0D);
        assertEquals(40.0D, stats.heightQuantile(0.5D), 0.0D);
    }

    private static long sum(long[] voxels) {
        long total = 0L;
        for (long count : voxels) {
            total += count;
        }
        return total;
    }

    private static SyntheticFidelityChunk chunk() {
        return new SyntheticFidelityChunk(MIN_Y);
    }

    private static FidelityStats stats(SyntheticFidelityChunk chunk) {
        FidelityStats stats = new FidelityStats(MIN_Y, MAX_Y);
        stats.accept(chunk, 1);
        return stats;
    }

    private static void assertSameStats(FidelityStats expected, FidelityStats actual) {
        assertEquals(expected.minY(), actual.minY());
        assertEquals(expected.maxY(), actual.maxY());
        assertEquals(expected.columns(), actual.columns());
        assertEquals(expected.heightColumns(), actual.heightColumns());
        assertEquals(expected.fluidColumns(), actual.fluidColumns());
        assertEquals(expected.blockColumns(), actual.blockColumns());
        assertEquals(expected.decorationColumns(), actual.decorationColumns());
        assertEquals(expected.riverColumns(), actual.riverColumns());
        assertEquals(expected.biomeColumns(), actual.biomeColumns());
        assertArrayEquals(expected.heightHistogram(), actual.heightHistogram());
        assertArrayEquals(expected.subsurfaceVoxels(), actual.subsurfaceVoxels());
        assertArrayEquals(expected.caveVoxels(), actual.caveVoxels());
        assertArrayEquals(expected.voidVoxels(), actual.voidVoxels());
        assertEquals(expected.deepVoxels(), actual.deepVoxels());
        assertEquals(expected.deepLavaVoxels(), actual.deepLavaVoxels());
        assertEquals(expected.wallSolidVoxels(), actual.wallSolidVoxels());
        assertEquals(expected.wallVoxels(), actual.wallVoxels());
        assertEquals(expected.oneBlockWallVoxels(), actual.oneBlockWallVoxels());
    }
}
