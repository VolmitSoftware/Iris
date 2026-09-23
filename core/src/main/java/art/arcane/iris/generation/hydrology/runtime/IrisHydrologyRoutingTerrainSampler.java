package art.arcane.iris.generation.hydrology.runtime;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.volmlib.util.cache.CacheKey;
import art.arcane.iris.generation.hydrology.HydrologyNaturalTerrainSampler;
import art.arcane.iris.generation.hydrology.HydrologyRoutingTerrainSampler;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSample;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSampler;
import art.arcane.iris.generation.hydrology.RiverFootprint;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationScope;
import art.arcane.iris.generation.hydrology.HydrologyForkJoin;
import art.arcane.iris.generation.concurrent.MultiBurst;
import it.unimi.dsi.fastutil.longs.Long2DoubleLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

final class IrisHydrologyRoutingTerrainSampler implements HydrologyNaturalTerrainSampler, AutoCloseable {
    private static final int MINIMUM_PARALLEL_BASIS_SAMPLES = 64;
    private static final int COORDINATE_MIX = 0x9E3779B9;
    private static final int CACHE_STRIPES = 16;
    private static final int MINIMUM_STRIPE_ENTRIES = 256;

    private final BasisProvider basisProvider;
    private final IrisHydrologyNaturalHeightProvider heightProvider;
    private final IrisHydrologyNaturalOceanClassifier oceanClassifier;
    private final int seaLevel;
    private final SamplingOptions samplingOptions;
    private final CacheStripe[] stripes;

    IrisHydrologyRoutingTerrainSampler(Sources sources, SamplingOptions samplingOptions) {
        Objects.requireNonNull(sources, "sources");
        this.samplingOptions = Objects.requireNonNull(samplingOptions, "samplingOptions");
        this.basisProvider = Objects.requireNonNull(sources.basisProvider(), "basisProvider");
        this.heightProvider = Objects.requireNonNull(sources.heightProvider(), "heightProvider");
        this.oceanClassifier = Objects.requireNonNull(sources.oceanClassifier(), "oceanClassifier");
        this.seaLevel = sources.seaLevel();
        int maximumEntries = samplingOptions.maximumEntries();
        int stripeCount = Math.min(CACHE_STRIPES, Integer.highestOneBit(Math.max(1, maximumEntries / MINIMUM_STRIPE_ENTRIES)));
        this.stripes = new CacheStripe[stripeCount];
        for (int index = 0; index < stripeCount; index++) {
            int capacity = maximumEntries / stripeCount + (index < maximumEntries % stripeCount ? 1 : 0);
            stripes[index] = new CacheStripe(capacity);
        }
    }

    @Override
    public boolean supportsSharedGridSamples() {
        return true;
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
        CacheStripe stripe = stripe(packed);
        synchronized (stripe) {
            TerrainBasis basis = stripe.bases.getAndMoveToLast(packed);
            if (basis != null) {
                return basis.terrain().ocean() ? NaturalClassification.OCEAN : NaturalClassification.LAND;
            }
            NaturalClassification cached = stripe.oceanClassifications.getAndMoveToLast(packed);
            if (cached != null) {
                return cached;
            }
        }
        NaturalClassification sampled = NaturalClassification.LAND;
        if (oceanClassifier.isOcean(blockX, blockZ)) {
            double height = naturalHeight(stripe, packed, blockX, blockZ);
            if (!Double.isFinite(height)) {
                return NaturalClassification.UNAVAILABLE;
            }
            if (physicalOcean(true, height, seaLevel)) {
                sampled = NaturalClassification.OCEAN;
            }
        }
        synchronized (stripe) {
            TerrainBasis basis = stripe.bases.getAndMoveToLast(packed);
            if (basis != null) {
                return basis.terrain().ocean() ? NaturalClassification.OCEAN : NaturalClassification.LAND;
            }
            NaturalClassification existing = stripe.oceanClassifications.getAndMoveToLast(packed);
            if (existing != null) {
                return existing;
            }
            stripe.oceanClassifications.putAndMoveToLast(packed, sampled);
            stripe.evictOldest(stripe.oceanClassifications);
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

    @Override
    public HydrologyTerrainSample[] sampleBasisWithoutSlopeBatch(long[] coordinates, int count) {
        Objects.checkFromIndexSize(0, count, coordinates.length);
        if (count > HydrologyTerrainSampler.MAXIMUM_BATCH_SIZE) {
            throw new IllegalArgumentException("Terrain basis batch exceeds its sample limit");
        }
        int workers = Math.min(Math.min(samplingOptions.maximumWorkers(), Runtime.getRuntime().availableProcessors()),
                Math.ceilDiv(count, MINIMUM_PARALLEL_BASIS_SAMPLES));
        Supplier<NativeGenerationScope> scope = workers > 1 ? samplingOptions.batchScopes().capture() : null;
        if (scope == null) {
            return HydrologyNaturalTerrainSampler.super.sampleBasisWithoutSlopeBatch(coordinates, count);
        }
        HydrologyTerrainSample[] samples = new HydrologyTerrainSample[count];
        int perWorker = Math.ceilDiv(count, workers);
        ArrayList<Callable<Void>> tasks = new ArrayList<>(workers);
        for (int worker = 0; worker < workers; worker++) {
            int start = worker * perWorker;
            int end = Math.min(count, start + perWorker);
            if (start >= end) {
                break;
            }
            tasks.add(() -> {
                try (NativeGenerationScope ignored = scope.get()) {
                    for (int index = start; index < end; index++) {
                        if (Thread.currentThread().isInterrupted()) {
                            throw new CancellationException("Terrain basis sampling interrupted");
                        }
                        samples[index] = sampleBasisWithoutSlope(RiverFootprint.unpackX(coordinates[index]),
                                RiverFootprint.unpackZ(coordinates[index]));
                    }
                }
                return null;
            });
        }
        HydrologyForkJoin.invokeAll(tasks, samplingOptions.executor());
        return samples;
    }

    @Override
    public double sampleLandHeight(int blockX, int blockZ) {
        long packed = pack(blockX, blockZ);
        CacheStripe stripe = stripe(packed);
        synchronized (stripe) {
            TerrainBasis cached = stripe.bases.getAndMoveToLast(packed);
            if (cached != null) {
                return cached.terrain().ocean() ? Double.NaN : cached.terrain().naturalHeight();
            }
        }
        double naturalHeight = naturalHeight(stripe, packed, blockX, blockZ);
        if (!Double.isFinite(naturalHeight)) {
            TerrainBasis basis = loadBasis(blockX, blockZ, stripe, packed, naturalHeight);
            return basis.terrain().ocean() ? Double.NaN : basis.terrain().naturalHeight();
        }
        long roundedHeight = StrictMath.round(naturalHeight);
        if (roundedHeight >= seaLevel) {
            return (int) roundedHeight;
        }
        NaturalClassification classification = classifyNatural(blockX, blockZ);
        if (classification == NaturalClassification.UNAVAILABLE) {
            TerrainBasis basis = loadBasis(blockX, blockZ, stripe, packed, naturalHeight);
            return basis.terrain().ocean() ? Double.NaN : basis.terrain().naturalHeight();
        }
        return classification == NaturalClassification.OCEAN ? Double.NaN : (int) roundedHeight;
    }

    TerrainBasis basis(int blockX, int blockZ) {
        long packed = pack(blockX, blockZ);
        CacheStripe stripe = stripe(packed);
        synchronized (stripe) {
            TerrainBasis cached = stripe.bases.getAndMoveToLast(packed);
            if (cached != null) {
                return cached;
            }
        }
        return loadBasis(blockX, blockZ, stripe, packed, naturalHeight(stripe, packed, blockX, blockZ));
    }

    private TerrainBasis loadBasis(int blockX, int blockZ, CacheStripe stripe, long packed, double naturalHeight) {
        TerrainBasis sampled = basisProvider.sample(blockX, blockZ, naturalHeight);
        if (sampled == null) {
            throw new NullPointerException(
                    "Hydrology terrain basis provider returned null at " + blockX + "," + blockZ
            );
        }
        synchronized (stripe) {
            TerrainBasis existing = stripe.bases.getAndMoveToLast(packed);
            if (existing != null) {
                return existing;
            }
            stripe.bases.putAndMoveToLast(packed, sampled);
            stripe.oceanClassifications.remove(packed);
            stripe.evictOldest(stripe.bases);
        }
        return sampled;
    }

    int basisCacheSize() {
        int size = 0;
        for (CacheStripe stripe : stripes) {
            synchronized (stripe) {
                size += stripe.bases.size();
            }
        }
        return size;
    }

    int naturalHeightCacheSize() {
        int size = 0;
        for (CacheStripe stripe : stripes) {
            synchronized (stripe) {
                size += stripe.naturalHeights.size();
            }
        }
        return size;
    }

    int oceanClassificationCacheSize() {
        int size = 0;
        for (CacheStripe stripe : stripes) {
            synchronized (stripe) {
                size += stripe.oceanClassifications.size();
            }
        }
        return size;
    }

    @Override
    public void close() {
        for (CacheStripe stripe : stripes) {
            synchronized (stripe) {
                stripe.bases.clear();
                stripe.naturalHeights.clear();
                stripe.oceanClassifications.clear();
            }
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


    private double naturalHeight(int blockX, int blockZ) {
        long packed = pack(blockX, blockZ);
        return naturalHeight(stripe(packed), packed, blockX, blockZ);
    }

    private double naturalHeight(CacheStripe stripe, long packed, int blockX, int blockZ) {
        synchronized (stripe) {
            double cached = stripe.naturalHeights.getAndMoveToLast(packed);
            if (!Double.isNaN(cached) || stripe.naturalHeights.containsKey(packed)) {
                return cached;
            }
            TerrainBasis cachedBasis = stripe.bases.getAndMoveToLast(packed);
            if (cachedBasis != null) {
                return cachedBasis.naturalHeight();
            }
        }
        double sampled = heightProvider.sample(blockX, blockZ);
        if (!Double.isFinite(sampled)) {
            // Never memoize a broken sample: the basis provider rejects it with a diagnostic and a
            // later sample of the same column gets a fresh chance instead of the cached failure.
            return sampled;
        }
        synchronized (stripe) {
            double existing = stripe.naturalHeights.getAndMoveToLast(packed);
            if (!Double.isNaN(existing) || stripe.naturalHeights.containsKey(packed)) {
                return existing;
            }
            stripe.naturalHeights.putAndMoveToLast(packed, sampled);
            stripe.evictOldest(stripe.naturalHeights);
        }
        return sampled;
    }

    private CacheStripe stripe(long packed) {
        return stripes[(int) CacheKey.mix(packed) & (stripes.length - 1)];
    }

    private static long pack(int blockX, int blockZ) {
        int mixedZ = blockZ ^ Integer.rotateLeft(blockX * COORDINATE_MIX, 16);
        return ((long) blockX << 32) ^ (mixedZ & 0xffffffffL);
    }

    private static final class CacheStripe {
        private final int maximumEntries;
        private final Long2ObjectLinkedOpenHashMap<TerrainBasis> bases;
        private final Long2DoubleLinkedOpenHashMap naturalHeights;
        private final Long2ObjectLinkedOpenHashMap<NaturalClassification> oceanClassifications;

        private CacheStripe(int maximumEntries) {
            this.maximumEntries = maximumEntries;
            this.bases = new Long2ObjectLinkedOpenHashMap<>(maximumEntries);
            this.naturalHeights = new Long2DoubleLinkedOpenHashMap(maximumEntries);
            this.naturalHeights.defaultReturnValue(Double.NaN);
            this.oceanClassifications = new Long2ObjectLinkedOpenHashMap<>(maximumEntries);
        }

        private void evictOldest(Long2ObjectLinkedOpenHashMap<?> cache) {
            if (cache.size() > maximumEntries) {
                cache.removeFirst();
            }
        }

        private void evictOldest(Long2DoubleLinkedOpenHashMap cache) {
            if (cache.size() > maximumEntries) {
                cache.removeFirstDouble();
            }
        }
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
            Executor executor,
            BatchScopes batchScopes
    ) {
        SamplingOptions(int maximumEntries, int maximumWorkers, Executor executor) {
            this(maximumEntries, maximumWorkers, executor, () -> () -> () -> {});
        }

        SamplingOptions {
            if (maximumEntries < 1) {
                throw new IllegalArgumentException("maximumEntries must be positive");
            }
            if (maximumWorkers < 1) {
                throw new IllegalArgumentException("maximumWorkers must be positive");
            }
            Objects.requireNonNull(executor, "executor");
            Objects.requireNonNull(batchScopes, "batchScopes");
        }

        static SamplingOptions production(int maximumEntries, BatchScopes batchScopes) {
            int configuredWorkers = IrisSettings.getThreadCount(
                    IrisSettings.get().getConcurrency().getParallelism()
            );
            return new SamplingOptions(
                    maximumEntries,
                    Math.max(1, configuredWorkers),
                    MultiBurst.burst,
                    batchScopes
            );
        }

        static SamplingOptions serial(int maximumEntries) {
            return new SamplingOptions(maximumEntries, 1, Runnable::run);
        }
    }

    interface BatchScopes {
        Supplier<NativeGenerationScope> capture();
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
