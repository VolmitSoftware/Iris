package art.arcane.iris.probe.fidelity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

public final class FidelityComparison {
    private static final double MIN_BIOME_AREA = 0.02D;
    private static final List<Integer> HEIGHT_PERCENTILES = List.of(10, 50, 90);
    private static final long MIN_BAND_COLUMNS = 64L;
    private static final String UNKNOWN_TYPE = "unknown";

    private FidelityComparison() {
    }

    public static FidelityReport compare(FidelitySamples columns, FidelitySamples chunks, FidelityThresholds thresholds) {
        Objects.requireNonNull(columns, "columns");
        Objects.requireNonNull(chunks, "chunks");
        Objects.requireNonNull(thresholds, "thresholds");
        List<FidelityMetric> metrics = new ArrayList<>();
        addBiomeMetrics(columns.iris().biomeShares(), columns.export().biomeShares(), thresholds.biomeShare(), metrics);
        addHeightMetrics(columns.iris(), columns.export(), thresholds.heightQuantile(), metrics);
        List<FidelityObservation> observations = new ArrayList<>();
        addCaveMetrics(chunks.iris(), chunks.export(), thresholds.caveBand(), metrics, observations);
        addWallMetric(chunks.iris(), chunks.export(), thresholds.wallShare(), metrics);
        addDecorationMetric(chunks.iris(), chunks.export(), thresholds.objectDensity(), metrics);
        addLavaObservation(chunks.iris(), chunks.export(), observations);
        addOneBlockWallObservation(chunks.iris(), chunks.export(), observations);
        return new FidelityReport(metrics, List.of(columns.sampling(), chunks.sampling()), observations);
    }

    public static BiomeShareReport compareBiomeShares(List<FidelitySamples> seeds, Map<String, String> biomeTypes, FidelityThresholds thresholds) {
        Objects.requireNonNull(seeds, "seeds");
        Objects.requireNonNull(biomeTypes, "biomeTypes");
        Objects.requireNonNull(thresholds, "thresholds");
        if (seeds.isEmpty()) {
            throw new IllegalArgumentException("Biome shares need at least one surveyed seed");
        }
        SortedMap<Long, FidelityReport> reports = new TreeMap<>();
        List<Map<String, Double>> irisShares = new ArrayList<>(seeds.size());
        List<Map<String, Double>> exportShares = new ArrayList<>(seeds.size());
        double irisLand = 0.0D;
        double exportLand = 0.0D;
        List<Long> seedList = new ArrayList<>(seeds.size());
        long columns = 0L;
        for (FidelitySamples seed : seeds) {
            Map<String, Double> iris = seed.iris().biomeShares();
            Map<String, Double> export = seed.export().biomeShares();
            irisShares.add(iris);
            exportShares.add(export);
            double seedIrisLand = landFraction(seed.iris());
            double seedExportLand = landFraction(seed.export());
            irisLand += seedIrisLand;
            exportLand += seedExportLand;
            seedList.addAll(seed.sampling().seeds());
            columns += seed.sampling().columns();
            reports.put(seed.sampling().seeds().getFirst(), biomeReport(iris, export, seedIrisLand, seedExportLand, biomeTypes, List.of(seed.sampling()),
                    thresholds));
        }
        FidelitySampling first = seeds.getFirst().sampling();
        FidelitySampling pooled = new FidelitySampling(first.scope(), first.windowBlocks(), first.strideBlocks(), seedList, columns);
        Map<String, Double> irisMean = mean(irisShares);
        Map<String, Double> exportMean = mean(exportShares);
        FidelityReport mean = biomeReport(irisMean, exportMean, irisLand / seeds.size(), exportLand / seeds.size(), biomeTypes, List.of(pooled),
                thresholds);
        return new BiomeShareReport(mean, reports, irisMean, exportMean);
    }

    private static FidelityReport biomeReport(Map<String, Double> irisShares, Map<String, Double> exportShares, double irisLand, double exportLand,
                                              Map<String, String> biomeTypes, List<FidelitySampling> sampling, FidelityThresholds thresholds) {
        List<FidelityMetric> metrics = new ArrayList<>();
        addBiomeMetrics(irisShares, exportShares, thresholds.biomeShare(), metrics);
        List<FidelityObservation> observations = new ArrayList<>();
        Map<String, Double> irisTypes = typeShares(irisShares, biomeTypes);
        Map<String, Double> exportTypes = typeShares(exportShares, biomeTypes);
        Set<String> types = new TreeSet<>(irisTypes.keySet());
        types.addAll(exportTypes.keySet());
        for (String type : types) {
            observations.add(new FidelityObservation("biome.type." + type, irisTypes.getOrDefault(type, 0.0D), exportTypes.getOrDefault(type, 0.0D)));
        }
        observations.add(new FidelityObservation("height.landFraction", irisLand, exportLand));
        return new FidelityReport(metrics, sampling, observations);
    }

    private static double landFraction(FidelityStats stats) {
        return stats.heightColumns() == 0L ? 0.0D : stats.landFraction();
    }

    private static Map<String, Double> mean(List<Map<String, Double>> shares) {
        SortedMap<String, Double> mean = new TreeMap<>();
        for (Map<String, Double> seed : shares) {
            for (Map.Entry<String, Double> entry : seed.entrySet()) {
                mean.merge(entry.getKey(), entry.getValue() / shares.size(), Double::sum);
            }
        }
        return mean;
    }

    private static Map<String, Double> typeShares(Map<String, Double> shares, Map<String, String> biomeTypes) {
        SortedMap<String, Double> types = new TreeMap<>();
        for (Map.Entry<String, Double> entry : shares.entrySet()) {
            types.merge(biomeTypes.getOrDefault(entry.getKey(), UNKNOWN_TYPE), entry.getValue(), Double::sum);
        }
        return types;
    }

    private static void addBiomeMetrics(Map<String, Double> irisShares, Map<String, Double> exportShares, double threshold, List<FidelityMetric> metrics) {
        if (irisShares.isEmpty() || exportShares.isEmpty()) {
            return;
        }
        Set<String> biomes = new TreeSet<>(irisShares.keySet());
        biomes.addAll(exportShares.keySet());
        double difference = 0.0D;
        for (String biome : biomes) {
            difference += Math.abs(exportShares.getOrDefault(biome, 0.0D) - irisShares.getOrDefault(biome, 0.0D));
        }
        double distance = difference / 2.0D;
        metrics.add(new FidelityMetric("biome.totalVariation", 0.0D, distance, distance, distance <= threshold));
        for (Map.Entry<String, Double> entry : irisShares.entrySet()) {
            double share = entry.getValue();
            if (share < MIN_BIOME_AREA) {
                continue;
            }
            double exported = exportShares.getOrDefault(entry.getKey(), 0.0D);
            double error = relativeError(share, exported);
            metrics.add(new FidelityMetric("biome.share." + entry.getKey(), share, exported, error, error <= threshold));
        }
    }

    private static void addHeightMetrics(FidelityStats iris, FidelityStats export, double threshold, List<FidelityMetric> metrics) {
        if (iris.heightColumns() == 0L) {
            return;
        }
        if (export.heightColumns() == 0L) {
            metrics.add(new FidelityMetric("height.terrainColumns", iris.heightColumns(), 0.0D, 1.0D, false));
            return;
        }
        double range = iris.maxY() - iris.minY();
        for (int percentile : HEIGHT_PERCENTILES) {
            double irisY = iris.heightQuantile(percentile / 100.0D);
            double exportY = export.heightQuantile(percentile / 100.0D);
            double delta = Math.abs(exportY - irisY) / range;
            metrics.add(new FidelityMetric("height.p" + percentile, irisY, exportY, delta, delta <= threshold));
        }
        double irisLand = iris.landFraction();
        double exportLand = export.landFraction();
        double landDelta = Math.abs(exportLand - irisLand);
        metrics.add(new FidelityMetric("height.landFraction", irisLand, exportLand, landDelta, landDelta <= threshold));
    }

    private static void addCaveMetrics(FidelityStats iris, FidelityStats export, double threshold, List<FidelityMetric> metrics,
                                       List<FidelityObservation> observations) {
        long[] irisVoxels = iris.subsurfaceVoxels();
        long[] exportVoxels = export.subsurfaceVoxels();
        long[] irisVoids = iris.voidVoxels();
        long[] exportVoids = export.voidVoxels();
        long minimum = MIN_BAND_COLUMNS * FidelityStats.BAND_HEIGHT;
        for (int band = 0; band < irisVoxels.length; band++) {
            if (irisVoxels[band] == 0L || exportVoxels[band] == 0L) {
                continue;
            }
            boolean judged = irisVoxels[band] >= minimum && exportVoxels[band] >= minimum;
            addBandShare("cave.band." + iris.bandMinY(band), iris.caveShare(band), export.caveShare(band), judged, threshold, metrics, observations);
            if (irisVoids[band] > 0L || exportVoids[band] > 0L) {
                addBandShare("void.band." + iris.bandMinY(band), iris.voidShare(band), export.voidShare(band), judged, threshold, metrics, observations);
            }
        }
    }

    private static void addBandShare(String name, double irisShare, double exportShare, boolean judged, double threshold, List<FidelityMetric> metrics,
                                     List<FidelityObservation> observations) {
        if (!judged) {
            observations.add(new FidelityObservation(name, irisShare, exportShare));
            return;
        }
        double delta = Math.abs(exportShare - irisShare);
        metrics.add(new FidelityMetric(name, irisShare, exportShare, delta, delta <= threshold));
    }

    private static void addWallMetric(FidelityStats iris, FidelityStats export, double threshold, List<FidelityMetric> metrics) {
        if (iris.wallSolidVoxels() == 0L || export.wallSolidVoxels() == 0L) {
            return;
        }
        double irisShare = iris.wallShare();
        double exportShare = export.wallShare();
        double error = relativeError(irisShare, exportShare);
        metrics.add(new FidelityMetric("cave.wallShare", irisShare, exportShare, error, error <= threshold));
    }

    private static void addLavaObservation(FidelityStats iris, FidelityStats export, List<FidelityObservation> observations) {
        if (iris.deepVoxels() == 0L || export.deepVoxels() == 0L) {
            return;
        }
        observations.add(new FidelityObservation("cave.lavaShare.belowY" + FidelityStats.DEEP_LAVA_LINE, iris.deepLavaShare(), export.deepLavaShare()));
    }

    private static void addOneBlockWallObservation(FidelityStats iris, FidelityStats export, List<FidelityObservation> observations) {
        if (iris.wallSolidVoxels() == 0L || export.wallSolidVoxels() == 0L) {
            return;
        }
        observations.add(new FidelityObservation("cave.wallShare.oneBlock", iris.oneBlockWallShare(), export.oneBlockWallShare()));
    }

    private static void addDecorationMetric(FidelityStats iris, FidelityStats export, double threshold, List<FidelityMetric> metrics) {
        if (iris.blockColumns() == 0L || export.blockColumns() == 0L) {
            return;
        }
        double irisDensity = iris.decorationPerSquareKilometre();
        double exportDensity = export.decorationPerSquareKilometre();
        double error = relativeError(irisDensity, exportDensity);
        metrics.add(new FidelityMetric("decoration.columnsPerKm2", irisDensity, exportDensity, error, error <= threshold));
    }

    private static double relativeError(double reference, double measured) {
        if (reference == 0.0D) {
            return measured == 0.0D ? 0.0D : 1.0D;
        }
        return Math.abs(measured - reference) / reference;
    }
}
