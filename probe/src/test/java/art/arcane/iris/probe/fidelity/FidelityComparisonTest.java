package art.arcane.iris.probe.fidelity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class FidelityComparisonTest {
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;
    private static final double RANGE = MAX_Y - MIN_Y;

    @Test
    public void identicalStatsPass() {
        FidelityReport report = compare(varied(), varied());

        assertTrue(report.passed());
        for (FidelityMetric metric : report.metrics()) {
            assertEquals(metric.name(), 0.0D, metric.delta(), 1.0E-12D);
            assertTrue(metric.name(), metric.pass());
        }
        assertEquals(0.5D, metric(report, "biome.share.plains").iris(), 1.0E-12D);
        assertEquals(40.0D, metric(report, "height.p10").iris(), 0.0D);
        assertEquals(0.5D, metric(report, "height.landFraction").iris(), 1.0E-12D);
        assertEquals(0.125D, metric(report, "cave.band.-48").iris(), 1.0E-12D);
        assertEquals(93_750.0D, metric(report, "decoration.columnsPerKm2").iris(), 1.0E-6D);
    }

    @Test
    public void biomeShareBeyondThresholdFails() {
        FidelityStats iris = stats(chunk().biomes("plains", "forest", 8));
        FidelityStats export = stats(chunk().biomes("plains", "forest", 12));

        FidelityReport report = compare(iris, export);

        FidelityMetric distance = metric(report, "biome.totalVariation");
        assertEquals(0.25D, distance.delta(), 1.0E-12D);
        assertFalse(distance.pass());
        FidelityMetric forest = metric(report, "biome.share.forest");
        assertEquals(0.5D, forest.iris(), 1.0E-12D);
        assertEquals(0.25D, forest.export(), 1.0E-12D);
        assertEquals(0.5D, forest.delta(), 1.0E-12D);
        assertFalse(forest.pass());
        assertTrue(metric(report, "height.p50").pass());
        assertFalse(report.passed());
    }

    @Test
    public void biomesBelowTwoPercentAreOnlyCoveredByTotalVariation() {
        FidelityStats iris = new FidelityStats(MIN_Y, MAX_Y);
        for (int i = 0; i < 9; i++) {
            iris.accept(chunk().biomes("plains", "plains", 16), 1);
        }
        iris.accept(chunk().biomes("swamp", "plains", 1), 1);
        FidelityStats export = new FidelityStats(MIN_Y, MAX_Y);
        for (int i = 0; i < 10; i++) {
            export.accept(chunk().biomes("plains", "plains", 16), 1);
        }

        FidelityReport report = compare(iris, export);

        assertNull(metric(report, "biome.share.swamp"));
        assertEquals(0.00625D, metric(report, "biome.totalVariation").delta(), 1.0E-12D);
        assertTrue(report.passed());
    }

    @Test
    public void heightQuantileDeltaNormalisedByRange() {
        FidelityStats iris = stats(chunk().surface(64));

        FidelityReport close = compare(iris, stats(chunk().surface(94)));
        FidelityMetric closeMedian = metric(close, "height.p50");
        assertEquals(64.0D, closeMedian.iris(), 0.0D);
        assertEquals(94.0D, closeMedian.export(), 0.0D);
        assertEquals(30.0D / RANGE, closeMedian.delta(), 1.0E-12D);
        assertTrue(closeMedian.pass());

        FidelityReport far = compare(iris, stats(chunk().surface(112)));
        FidelityMetric farMedian = metric(far, "height.p50");
        assertEquals(48.0D / RANGE, farMedian.delta(), 1.0E-12D);
        assertFalse(farMedian.pass());
        assertFalse(metric(far, "height.p10").pass());
        assertFalse(metric(far, "height.p90").pass());
        assertFalse(far.passed());
    }

    @Test
    public void landFractionUsesHeightThreshold() {
        FidelityStats iris = stats(chunk().surface(64), chunk().surface(64));
        FidelityStats export = stats(chunk().surface(64), chunk().surface(60).sea(62));

        FidelityMetric land = metric(compare(iris, export), "height.landFraction");

        assertEquals(1.0D, land.iris(), 0.0D);
        assertEquals(0.5D, land.export(), 0.0D);
        assertEquals(0.5D, land.delta(), 0.0D);
        assertFalse(land.pass());
    }

    @Test
    public void caveBandDeltaFails() {
        FidelityStats iris = stats(chunk().surface(64).caves(0, 16, 2));
        FidelityStats export = stats(chunk().surface(64).caves(0, 16, 8));

        FidelityReport report = compare(iris, export);

        FidelityMetric band = metric(report, "cave.band.0");
        assertEquals(0.125D, band.iris(), 1.0E-12D);
        assertEquals(0.5D, band.export(), 1.0E-12D);
        assertEquals(0.375D, band.delta(), 1.0E-12D);
        assertFalse(band.pass());
        assertTrue(metric(report, "cave.band.-64").pass());
        assertNull(metric(report, "cave.band.64"));
        assertFalse(report.passed());
    }

    @Test
    public void terrainVoidBandsAreJudgedApartFromCaveBands() {
        FidelityStats iris = stats(chunk().surface(100).overhang(4, 80, 96).caves(0, 16, 4));
        FidelityStats export = stats(chunk().surface(100).caves(0, 16, 4));

        FidelityReport report = compare(iris, export);

        FidelityMetric caves = metric(report, "cave.band.80");
        assertEquals(0.0D, caves.iris(), 0.0D);
        assertEquals(0.0D, caves.export(), 0.0D);
        assertTrue(caves.pass());
        assertTrue(metric(report, "cave.band.0").pass());
        FidelityMetric voids = metric(report, "void.band.80");
        assertEquals(0.25D, voids.iris(), 1.0E-12D);
        assertEquals(0.0D, voids.export(), 0.0D);
        assertEquals(0.25D, voids.delta(), 1.0E-12D);
        assertFalse(voids.pass());
        assertNull(metric(report, "void.band.0"));
        assertFalse(report.passed());
    }

    @Test
    public void voidBandsUseTheCaveBandThreshold() {
        FidelityStats iris = stats(chunk().surface(100).overhang(4, 80, 96));
        FidelityStats export = stats(chunk().surface(100).overhang(2, 80, 96));

        FidelityMetric voids = metric(compare(iris, export), "void.band.80");

        assertEquals(0.125D, voids.delta(), 1.0E-12D);
        assertEquals(voids.delta() <= FidelityThresholds.defaults().caveBand(), voids.pass());
    }

    @Test
    public void thinVoidBandsAreObservedNotJudged() {
        FidelityStats iris = stats(chunk().surface(66).overhang(16, 64, 66));
        FidelityStats export = stats(chunk().surface(66));

        FidelityReport report = compare(iris, export);

        assertNull(metric(report, "void.band.64"));
        FidelityObservation thin = observation(report, "void.band.64");
        assertTrue(thin.iris() > 0.0D);
        assertEquals(0.0D, thin.export(), 0.0D);
    }

    @Test
    public void decorationDensityUsesRelativeError() {
        FidelityStats iris = stats(chunk().trees(4, 5));

        FidelityMetric dense = metric(compare(iris, stats(chunk().trees(8, 5))),
                "decoration.columnsPerKm2");
        assertEquals(1.0D, dense.delta(), 1.0E-12D);
        assertFalse(dense.pass());

        FidelityReport noiseOnly = compare(iris, stats(chunk().trees(8, 5).noBlocks()));
        assertNull(metric(noiseOnly, "decoration.columnsPerKm2"));
    }

    @Test
    public void wallShareUsesRelativeError() {
        FidelityStats iris = stats(chunk().surface(64).caves(-20, 0, 4).walls(1));

        FidelityMetric same = metric(compare(iris, stats(chunk().surface(64).caves(-20, 0, 4).walls(1))), "cave.wallShare");
        assertEquals(280.0D / 24_248.0D, same.iris(), 1.0E-12D);
        assertEquals(0.0D, same.delta(), 0.0D);
        assertTrue(same.pass());

        FidelityMetric thick = metric(compare(iris, stats(chunk().surface(64).caves(-20, 0, 4).walls(3))), "cave.wallShare");
        assertEquals(840.0D / 24_248.0D, thick.export(), 1.0E-12D);
        assertEquals(2.0D, thick.delta(), 1.0E-12D);
        assertFalse(thick.pass());

        assertNull(metric(compare(iris, stats(chunk().surface(64).caves(-20, 0, 4))), "cave.wallShare"));
    }

    @Test
    public void differentHeightRangesAreRejected() {
        FidelityStats shallow = new FidelityStats(0, 256);
        shallow.accept(new SyntheticFidelityChunk(0), 1);

        assertThrows(IllegalArgumentException.class, () -> compare(varied(), shallow));
    }

    @Test
    public void reportJsonListsEveryMetric() {
        FidelityStats iris = stats(chunk().surface(64));
        FidelityReport report = compare(iris, stats(chunk().surface(112)));

        JsonObject json = JsonParser.parseString(report.toJson()).getAsJsonObject();

        assertFalse(json.get("passed").getAsBoolean());
        assertEquals(report.metrics().size(), json.getAsJsonArray("metrics").size());
        JsonObject first = json.getAsJsonArray("metrics").get(0).getAsJsonObject();
        assertEquals(report.metrics().getFirst().name(), first.get("name").getAsString());
        assertEquals(report.metrics().getFirst().pass(), first.get("pass").getAsBoolean());
    }

    @Test
    public void thinCaveBandsAreObservedNotJudged() {
        FidelityStats iris = stats(chunk().surface(66).caves(64, 66, 16));
        FidelityStats export = stats(chunk().surface(66));

        FidelityReport report = compare(iris, export);

        assertNull(metric(report, "cave.band.64"));
        assertTrue(metric(report, "cave.band.48").pass());
        FidelityObservation thin = observation(report, "cave.band.64");
        assertTrue(thin.iris() > 0.0D);
        assertEquals(0.0D, thin.export(), 0.0D);
        assertTrue(report.passed());
    }

    @Test
    public void deepLavaShareIsObservedNotJudged() {
        FidelityStats iris = stats(chunk().surface(64).caves(-64, -56, 4));
        FidelityStats export = stats(chunk().surface(64).lavaPool(-64, -56, 4));

        FidelityReport report = compare(iris, export);

        FidelityObservation lava = report.observations().getFirst();
        assertEquals("cave.lavaShare.belowY-54", lava.name());
        assertEquals(0.0D, lava.iris(), 0.0D);
        assertEquals(0.2D, lava.export(), 1.0E-12D);
        assertNull(metric(report, "cave.lavaShare.belowY-54"));
    }

    @Test
    public void oneBlockWallShareIsObservedNotJudged() {
        FidelityStats iris = stats(chunk().surface(64).caves(-20, 0, 4).walls(1));
        FidelityStats export = stats(chunk().surface(64).caves(-20, 0, 4).walls(3));

        FidelityReport report = compare(iris, export);

        FidelityObservation oneBlock = observation(report, "cave.wallShare.oneBlock");
        assertEquals(280.0D / 24_248.0D, oneBlock.iris(), 1.0E-12D);
        assertEquals(280.0D / 24_248.0D, oneBlock.export(), 1.0E-12D);
        assertNull(metric(report, "cave.wallShare.oneBlock"));
    }

    @Test
    public void emptyReportDoesNotPass() {
        assertFalse(new FidelityReport(List.of(), List.of(), List.of()).passed());
    }

    @Test
    public void surfaceMetricsComeFromColumnsAndSubsurfaceMetricsFromChunks() {
        FidelityStats columns = stats(chunk().biomes("plains", "forest", 8).surface(70));
        FidelityStats exportColumns = stats(chunk().biomes("plains", "forest", 8).surface(70));
        FidelityStats chunks = stats(chunk().biomes("desert", "desert", 16).surface(20).caves(0, 16, 2).trees(3, 5));
        FidelityStats exportChunks = stats(chunk().biomes("ocean", "ocean", 16).surface(90).caves(0, 16, 2).trees(3, 5));

        FidelityReport report = FidelityComparison.compare(
                new FidelitySamples(columns, exportColumns, sampling("columns")),
                new FidelitySamples(chunks, exportChunks, sampling("chunks")),
                FidelityThresholds.defaults());

        assertTrue(report.toTable(), report.passed());
        assertEquals(0.5D, metric(report, "biome.share.plains").iris(), 1.0E-12D);
        assertNull(metric(report, "biome.share.desert"));
        assertEquals(70.0D, metric(report, "height.p50").export(), 0.0D);
        assertEquals(0.125D, metric(report, "cave.band.0").export(), 1.0E-12D);
        assertEquals(List.of("columns", "chunks"), report.sampling().stream().map(FidelitySampling::scope).toList());
    }

    @Test
    public void biomeSharesAreJudgedOnTheMeanOverEverySeed() {
        FidelitySamples first = seed(1337L, columns("plains", 8, "forest", 8), columns("plains", 12, "forest", 4));
        FidelitySamples second = seed(1338L, columns("plains", 8, "forest", 8), columns("plains", 4, "forest", 12));

        BiomeShareReport report = FidelityComparison.compareBiomeShares(List.of(first, second), Map.of(), FidelityThresholds.defaults());

        FidelityMetric forest = metric(report.mean(), "biome.share.forest");
        assertEquals(0.5D, forest.iris(), 1.0E-12D);
        assertEquals(0.5D, forest.export(), 1.0E-12D);
        assertTrue(forest.pass());
        assertEquals(0.0D, metric(report.mean(), "biome.totalVariation").delta(), 1.0E-12D);
        assertTrue(report.toTable(), report.passed());
        assertEquals(0.25D, metric(report.seeds().get(1337L), "biome.share.forest").export(), 1.0E-12D);
        assertFalse(metric(report.seeds().get(1338L), "biome.share.forest").pass());
        assertEquals(0, report.seedsPassing("biome.share.forest"));
        assertEquals(2, report.seedsCompared("biome.share.forest"));
        assertNull(metric(report.mean(), "height.p50"));
        FidelitySampling sampling = report.mean().sampling().getFirst();
        assertEquals(List.of(1337L, 1338L), sampling.seeds());
        assertEquals(32L, sampling.columns());
    }

    @Test
    public void meanSharesWeighEverySeedEqually() {
        FidelitySamples small = seed(1337L, columns("plains", 4, "forest", 0), columns("plains", 4, "forest", 0));
        FidelitySamples large = seed(1338L, columns("plains", 0, "forest", 12), columns("plains", 6, "forest", 6));

        BiomeShareReport report = FidelityComparison.compareBiomeShares(List.of(small, large), Map.of(), FidelityThresholds.defaults());

        FidelityMetric plains = metric(report.mean(), "biome.share.plains");
        assertEquals(0.5D, plains.iris(), 1.0E-12D);
        assertEquals(0.75D, plains.export(), 1.0E-12D);
        assertFalse(plains.pass());
        assertFalse(report.passed());
    }

    @Test
    public void biomeTypeSharesAreObservedWithoutAffectingTheVerdict() {
        FidelitySamples first = seed(1337L, columns("plains", 6, "ocean", 4, "beach", 2), columns("plains", 6, "ocean", 5, "beach", 1));
        FidelitySamples second = seed(1338L, columns("plains", 6, "ocean", 4, "beach", 2), columns("plains", 6, "ocean", 4, "beach", 2));
        Map<String, String> types = Map.of("plains", "land", "ocean", "sea", "beach", "shore");

        BiomeShareReport report = FidelityComparison.compareBiomeShares(List.of(first, second), types, FidelityThresholds.defaults());

        FidelityObservation sea = observation(report.mean(), "biome.type.sea");
        assertEquals(4.0D / 12.0D, sea.iris(), 1.0E-12D);
        assertEquals(4.5D / 12.0D, sea.export(), 1.0E-12D);
        assertEquals(0.5D, observation(report.mean(), "biome.type.land").export(), 1.0E-12D);
        assertEquals(1.0D / 12.0D, observation(report.seeds().get(1337L), "biome.type.shore").export(), 1.0E-12D);
        assertEquals(0.5D, observation(report.mean(), "height.landFraction").iris(), 1.0E-12D);
    }

    @Test
    public void meanSharesListEveryBiomeIncludingTheRareOnes() {
        FidelitySamples first = seed(1337L, columns("plains", 99, "swamp", 1), columns("plains", 100));
        FidelitySamples second = seed(1338L, columns("plains", 100), columns("plains", 98, "swamp", 2));

        BiomeShareReport report = FidelityComparison.compareBiomeShares(List.of(first, second), Map.of(), FidelityThresholds.defaults());

        assertNull(metric(report.mean(), "biome.share.swamp"));
        assertEquals(0.005D, report.irisShares().get("swamp"), 1.0E-12D);
        assertEquals(0.01D, report.exportShares().get("swamp"), 1.0E-12D);
        assertEquals(0.995D, report.irisShares().get("plains"), 1.0E-12D);
    }

    @Test
    public void biomeSharesNeedAtLeastOneSeed() {
        assertThrows(IllegalArgumentException.class,
                () -> FidelityComparison.compareBiomeShares(List.of(), Map.of(), FidelityThresholds.defaults()));
    }

    private static FidelityStats varied() {
        return stats(chunk().biomes("plains", "forest", 8).caves(-48, -32, 4).trees(3, 5).surface(70),
                chunk().biomes("plains", "forest", 8).surface(40).sea(62));
    }

    private static FidelityReport compare(FidelityStats iris, FidelityStats export) {
        return FidelityComparison.compare(new FidelitySamples(iris, export, sampling("columns")),
                new FidelitySamples(iris, export, sampling("chunks")), FidelityThresholds.defaults());
    }

    private static FidelitySampling sampling(String scope) {
        return new FidelitySampling(scope, 16, 1, List.of(1337L), 256L);
    }

    private static SyntheticFidelityChunk chunk() {
        return new SyntheticFidelityChunk(MIN_Y);
    }

    private static FidelityStats stats(SyntheticFidelityChunk... chunks) {
        FidelityStats stats = new FidelityStats(MIN_Y, MAX_Y);
        for (SyntheticFidelityChunk chunk : chunks) {
            stats.accept(chunk, 1);
        }
        return stats;
    }

    private static FidelitySamples seed(long seed, FidelityStats iris, FidelityStats export) {
        return new FidelitySamples(iris, export, new FidelitySampling("columns", 64, 16, List.of(seed), iris.columns()));
    }

    private static FidelityStats columns(Object... biomesAndCounts) {
        FidelityStats stats = new FidelityStats(MIN_Y, MAX_Y);
        for (int index = 0; index < biomesAndCounts.length; index += 2) {
            String biome = (String) biomesAndCounts[index];
            int count = (Integer) biomesAndCounts[index + 1];
            for (int column = 0; column < count; column++) {
                stats.accept(new FidelityColumn(biome, biome.equals("plains") ? 80 : 40, !biome.equals("plains")));
            }
        }
        return stats;
    }

    private static FidelityObservation observation(FidelityReport report, String name) {
        for (FidelityObservation observation : report.observations()) {
            if (observation.name().equals(name)) {
                return observation;
            }
        }
        return null;
    }

    private static FidelityMetric metric(FidelityReport report, String name) {
        for (FidelityMetric metric : report.metrics()) {
            if (metric.name().equals(name)) {
                return metric;
            }
        }
        return null;
    }
}
