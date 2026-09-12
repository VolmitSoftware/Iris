package art.arcane.iris.generation.hydrology.runtime;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.hydrology.HydrologyNaturalTerrainSampler;
import art.arcane.iris.generation.hydrology.HydrologyRoutingTerrainSampler;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSample;
import art.arcane.iris.generation.hydrology.HydrologyForkJoin;
import art.arcane.iris.generation.concurrent.MultiBurst;
import it.unimi.dsi.fastutil.longs.Long2DoubleLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

final class IrisHydrologyRoutingTerrainSampler implements HydrologyNaturalTerrainSampler, AutoCloseable {
    private static final int MINIMUM_PARALLEL_BASIS_SAMPLES = 64;
    private static final int COORDINATE_MIX = 0x9E3779B9;

    private final BasisProvider basisProvider;
    private final IrisHydrologyNaturalHeightProvider heightProvider;
    private final IrisHydrologyNaturalOceanClassifier oceanClassifier;
    private final int seaLevel;
    private final int maximumEntries;
    private final SamplingOptions samplingOptions;
    private final Object lock;
    private final Long2ObjectLinkedOpenHashMap<TerrainBasis> bases;
    private final Long2DoubleLinkedOpenHashMap naturalHeights;
    private final Long2ObjectLinkedOpenHashMap<NaturalClassification> oceanClassifications;

    IrisHydrologyRoutingTerrainSampler(Sources sources, SamplingOptions samplingOptions) {
        Objects.requireNonNull(sources, "sources");
        this.samplingOptions = Objects.requireNonNull(samplingOptions, "samplingOptions");
        this.basisProvider = Objects.requireNonNull(sources.basisProvider(), "basisProvider");
        this.heightProvider = Objects.requireNonNull(sources.heightProvider(), "heightProvider");
        this.oceanClassifier = Objects.requireNonNull(sources.oceanClassifier(), "oceanClassifier");
        this.seaLevel = sources.seaLevel();
        int maximumEntries = samplingOptions.maximumEntries();
        this.maximumEntries = maximumEntries;
        this.lock = new Object();
        this.bases = new Long2ObjectLinkedOpenHashMap<>(maximumEntries);
        this.naturalHeights = new Long2DoubleLinkedOpenHashMap(maximumEntries);
        this.naturalHeights.defaultReturnValue(Double.NaN);
        this.oceanClassifications = new Long2ObjectLinkedOpenHashMap<>(maximumEntries);
    }

    @Override
    public HydrologyTerrainSample[] sampleGrid(GridRequest request) {
        int minimumX = request.minimumX();
        int minimumZ = request.minimumZ();
        int width = request.width();
        int spacing = request.spacing();
        int planeWidth = Math.addExact(width, 1);
        TerrainBasis[] plane = new TerrainBasis[Math.multiplyExact(planeWidth, planeWidth)];
        fillBasisPlane(plane, planeWidth, minimumX, minimumZ, spacing);
        HydrologyTerrainSample[] samples = new HydrologyTerrainSample[Math.multiplyExact(width, width)];
        double slopeScale = 3D / spacing;
        int sampleIndex = 0;
        for (int gridZ = 0; gridZ < width; gridZ++) {
            int planeIndex = gridZ * planeWidth;
            for (int gridX = 0; gridX < width; gridX++) {
                TerrainBasis center = plane[planeIndex];
                double slope = scaledRoutingSlope(
                        center.naturalHeight(),
                        plane[planeIndex + 1].naturalHeight(),
                        plane[planeIndex + planeWidth].naturalHeight(),
                        slopeScale
                );
                samples[sampleIndex++] = center.terrain().withSlope(slope);
                planeIndex++;
            }
        }
        return samples;
    }

    @Override
    public NaturalClassification classifyNatural(int blockX, int blockZ) {
        long packed = pack(blockX, blockZ);
        synchronized (lock) {
            TerrainBasis basis = bases.getAndMoveToLast(packed);
            if (basis != null) {
                return basis.terrain().ocean() ? NaturalClassification.OCEAN : NaturalClassification.LAND;
            }
            NaturalClassification cached = oceanClassifications.getAndMoveToLast(packed);
            if (cached != null) {
                return cached;
            }
        }
        NaturalClassification sampled = NaturalClassification.LAND;
        if (oceanClassifier.isOcean(blockX, blockZ)) {
            double height = naturalHeight(packed, blockX, blockZ);
            if (!Double.isFinite(height)) {
                return NaturalClassification.UNAVAILABLE;
            }
            if (physicalOcean(true, height, seaLevel)) {
                sampled = NaturalClassification.OCEAN;
            }
        }
        synchronized (lock) {
            TerrainBasis basis = bases.getAndMoveToLast(packed);
            if (basis != null) {
                return basis.terrain().ocean() ? NaturalClassification.OCEAN : NaturalClassification.LAND;
            }
            NaturalClassification existing = oceanClassifications.getAndMoveToLast(packed);
            if (existing != null) {
                return existing;
            }
            oceanClassifications.putAndMoveToLast(packed, sampled);
            evictOldest(oceanClassifications);
        }
        return sampled;
    }

    @Override
    public HydrologyTerrainSample sampleBasis(int blockX, int blockZ) {
        // Point samples carry the forward slope; the aligned grid derives its own from neighbouring bases.
        TerrainBasis basis = basis(blockX, blockZ);
        return basis.terrain().withSlope(localSlope(blockX, blockZ, basis.naturalHeight()));
    }

    @Override
    public HydrologyTerrainSample sampleBasisWithoutSlope(int blockX, int blockZ) {
        return basis(blockX, blockZ).terrain();
    }

    TerrainBasis basis(int blockX, int blockZ) {
        long packed = pack(blockX, blockZ);
        synchronized (lock) {
            TerrainBasis cached = bases.getAndMoveToLast(packed);
            if (cached != null) {
                return cached;
            }
        }
        double naturalHeight = naturalHeight(packed, blockX, blockZ);
        TerrainBasis sampled = basisProvider.sample(blockX, blockZ, naturalHeight);
        if (sampled == null) {
            throw new NullPointerException(
                    "Hydrology terrain basis provider returned null at " + blockX + "," + blockZ
            );
        }
        synchronized (lock) {
            TerrainBasis existing = bases.getAndMoveToLast(packed);
            if (existing != null) {
                return existing;
            }
            bases.putAndMoveToLast(packed, sampled);
            oceanClassifications.remove(packed);
            evictOldest(bases);
        }
        return sampled;
    }

    int basisCacheSize() {
        synchronized (lock) {
            return bases.size();
        }
    }

    int naturalHeightCacheSize() {
        synchronized (lock) {
            return naturalHeights.size();
        }
    }

    int oceanClassificationCacheSize() {
        synchronized (lock) {
            return oceanClassifications.size();
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            bases.clear();
            naturalHeights.clear();
            oceanClassifications.clear();
        }
    }

    static double localSlope(double naturalHeight, double easternHeight, double southernHeight) {
        double deltaX = easternHeight - naturalHeight;
        double deltaZ = southernHeight - naturalHeight;
        return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
    }

    static boolean physicalOcean(boolean oceanIntent, double naturalHeight, int seaLevel) {
        return oceanIntent && Double.isFinite(naturalHeight) && StrictMath.round(naturalHeight) < seaLevel;
    }

    double localSlope(int blockX, int blockZ, double naturalHeight) {
        double easternHeight = naturalHeight(blockX + 3, blockZ);
        double southernHeight = naturalHeight(blockX, blockZ + 3);
        return localSlope(naturalHeight, easternHeight, southernHeight);
    }

    static double routingSlope(
            double naturalHeight,
            double easternHeight,
            double southernHeight,
            int spacing
    ) {
        if (spacing < 1) {
            throw new IllegalArgumentException("spacing must be positive");
        }
        double scale = 3D / spacing;
        return scaledRoutingSlope(naturalHeight, easternHeight, southernHeight, scale);
    }

    private static double scaledRoutingSlope(
            double naturalHeight,
            double easternHeight,
            double southernHeight,
            double scale
    ) {
        double deltaX = (easternHeight - naturalHeight) * scale;
        double deltaZ = (southernHeight - naturalHeight) * scale;
        return StrictMath.sqrt(deltaX * deltaX + deltaZ * deltaZ);
    }

    private void fillBasisPlane(
            TerrainBasis[] plane,
            int planeWidth,
            int minimumX,
            int minimumZ,
            int spacing
    ) {
        int workerCount = gridWorkerCount(planeWidth, plane.length);
        if (workerCount == 1) {
            fillBasisRows(plane, planeWidth, minimumX, minimumZ, spacing, 0, planeWidth);
            return;
        }
        int rowsPerWorker = Math.ceilDiv(planeWidth, workerCount);
        ArrayList<Callable<Void>> rows = new ArrayList<>(workerCount);
        for (int workerIndex = 0; workerIndex < workerCount; workerIndex++) {
            int minimumRow = workerIndex * rowsPerWorker;
            if (minimumRow >= planeWidth) {
                break;
            }
            int maximumRow = Math.min(planeWidth, minimumRow + rowsPerWorker);
            rows.add(() -> {
                fillBasisRows(plane, planeWidth, minimumX, minimumZ, spacing, minimumRow, maximumRow);
                return null;
            });
        }
        HydrologyForkJoin.invokeAll(rows, samplingOptions.executor());
    }

    private void fillBasisRows(
            TerrainBasis[] plane,
            int planeWidth,
            int minimumX,
            int minimumZ,
            int spacing,
            int minimumRow,
            int maximumRow
    ) {
        for (int gridZ = minimumRow; gridZ < maximumRow; gridZ++) {
            int z = Math.toIntExact((long) minimumZ + (long) gridZ * spacing);
            int planeIndex = gridZ * planeWidth;
            for (int gridX = 0; gridX < planeWidth; gridX++) {
                int x = Math.toIntExact((long) minimumX + (long) gridX * spacing);
                plane[planeIndex++] = basis(x, z);
            }
        }
    }

    private int gridWorkerCount(int planeWidth, int planeSize) {
        if (samplingOptions.maximumWorkers() == 1
                || planeSize < MINIMUM_PARALLEL_BASIS_SAMPLES) {
            return 1;
        }
        return Math.min(planeWidth, samplingOptions.maximumWorkers());
    }


    private void evictOldest(Long2ObjectLinkedOpenHashMap<?> cache) {
        if (cache.size() <= maximumEntries) {
            return;
        }
        cache.removeFirst();
    }

    private void evictOldest(Long2DoubleLinkedOpenHashMap cache) {
        if (cache.size() <= maximumEntries) {
            return;
        }
        cache.removeFirstDouble();
    }

    private double naturalHeight(int blockX, int blockZ) {
        return naturalHeight(pack(blockX, blockZ), blockX, blockZ);
    }

    private double naturalHeight(long packed, int blockX, int blockZ) {
        synchronized (lock) {
            double cached = naturalHeights.getAndMoveToLast(packed);
            if (!Double.isNaN(cached) || naturalHeights.containsKey(packed)) {
                return cached;
            }
        }
        double sampled = heightProvider.sample(blockX, blockZ);
        if (!Double.isFinite(sampled)) {
            // Never memoize a broken sample: the basis provider rejects it with a diagnostic and a
            // later sample of the same column gets a fresh chance instead of the cached failure.
            return sampled;
        }
        synchronized (lock) {
            double existing = naturalHeights.getAndMoveToLast(packed);
            if (!Double.isNaN(existing) || naturalHeights.containsKey(packed)) {
                return existing;
            }
            naturalHeights.putAndMoveToLast(packed, sampled);
            evictOldest(naturalHeights);
        }
        return sampled;
    }

    private static long pack(int blockX, int blockZ) {
        int mixedZ = blockZ ^ Integer.rotateLeft(blockX * COORDINATE_MIX, 16);
        return ((long) blockX << 32) ^ (mixedZ & 0xffffffffL);
    }

    @FunctionalInterface
    interface BasisProvider {
        TerrainBasis sample(int blockX, int blockZ, double naturalHeight);
    }

    record Sources(
            BasisProvider basisProvider,
            IrisHydrologyNaturalHeightProvider heightProvider,
            IrisHydrologyNaturalOceanClassifier oceanClassifier,
            int seaLevel
    ) {
        Sources {
            Objects.requireNonNull(basisProvider, "basisProvider");
            Objects.requireNonNull(heightProvider, "heightProvider");
            Objects.requireNonNull(oceanClassifier, "oceanClassifier");
        }
    }

    record SamplingOptions(
            int maximumEntries,
            int maximumWorkers,
            Executor executor
    ) {
        SamplingOptions {
            if (maximumEntries < 1) {
                throw new IllegalArgumentException("maximumEntries must be positive");
            }
            if (maximumWorkers < 1) {
                throw new IllegalArgumentException("maximumWorkers must be positive");
            }
            Objects.requireNonNull(executor, "executor");
        }

        static SamplingOptions production(int maximumEntries) {
            int configuredWorkers = IrisSettings.getThreadCount(
                    IrisSettings.get().getConcurrency().getParallelism()
            );
            return new SamplingOptions(
                    maximumEntries,
                    Math.max(1, configuredWorkers),
                    MultiBurst.burst
            );
        }

        static SamplingOptions serial(int maximumEntries) {
            return new SamplingOptions(maximumEntries, 1, Runnable::run);
        }
    }

    record TerrainBasis(double naturalHeight, HydrologyTerrainSample terrain) {
        TerrainBasis {
            if (!Double.isFinite(naturalHeight)) {
                throw new IllegalArgumentException("naturalHeight must be finite");
            }
            Objects.requireNonNull(terrain, "terrain");
        }
    }
}
