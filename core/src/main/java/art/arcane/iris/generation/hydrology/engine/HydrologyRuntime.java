package art.arcane.iris.generation.hydrology.engine;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyPolicy;
import art.arcane.iris.generation.hydrology.plan.HydrologyPlanCache;
import art.arcane.iris.generation.hydrology.plan.HydrologyPlanStore;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.IndexedWindow;
import art.arcane.iris.generation.hydrology.plan.RegionKey;
import art.arcane.iris.generation.hydrology.plan.RegionPlan;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureQuery;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureRef;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.raster.ChunkRasterizer;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.underground.SeaInlet;
import art.arcane.iris.generation.hydrology.underground.SeaInletRasterizer;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EnginePlatformHooks;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.PreparedHydrologyCacheIdentity;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.IrisWorld;

import java.io.File;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class HydrologyRuntime {
    private static final int RASTER_CAPACITY = 1024;
    private static final int KEEP_SCOPES = 2;
    private static final int CHUNKS_PER_WINDOW = WindowKey.SIZE >> 4;
    public static final int MAX_PREGENERATION_REGIONS = 4096;
    private static final int MAX_PREVIEW_REQUESTS = 64;
    private static final int PREFETCH_CAPACITY = 16;
    private static final HydrologyRuntime DISABLED = new HydrologyRuntime();

    private final HydrologySettings settings;
    private final HydrologyPlanCache cache;
    private final ChunkRasterizer rasterizer;
    private final IntBinaryOperator naturalHeight;
    private final BooleanSupplier mainThread;
    private final RasterLru rasters;
    private final ThreadLocal<LastRaster> last;
    private final Set<Long> queued;
    private final RasterFailures failures;
    private final RasterFailures windowPreviewFailures;
    private final RasterFailures regionPreviewFailures;
    private final Set<Object> previews;
    private final LinkedHashMap<WindowKey, WindowPrefetch> prefetches;
    private final HydrologyBinding binding;
    private final Function<PreparedHydrologyCacheIdentity, HydrologyPlanStore> stores;
    private final boolean caveFluidPolicies;
    private final ChunkRasterizer.AreaFluid areaFluid;
    private final AtomicLong rasterRevision = new AtomicLong();
    private volatile PreparedHydrologyCacheIdentity storeIdentity;
    private boolean storeOpen;
    private volatile boolean closed;

    private HydrologyRuntime() {
        this.settings = null;
        this.cache = null;
        this.rasterizer = null;
        this.naturalHeight = null;
        this.mainThread = null;
        this.rasters = null;
        this.last = null;
        this.queued = null;
        this.failures = null;
        this.windowPreviewFailures = null;
        this.regionPreviewFailures = null;
        this.previews = null;
        this.prefetches = null;
        this.binding = HydrologyBinding.UNBOUND;
        this.stores = identity -> null;
        this.caveFluidPolicies = false;
        this.areaFluid = ChunkRasterizer.AreaFluid.NONE;
    }

    public HydrologyRuntime(HydrologySettings settings, HydrologyPlanCache cache, IntBinaryOperator naturalHeight,
                            BooleanSupplier mainThread, LongSupplier ticker) {
        this(settings, cache, naturalHeight, mainThread, ticker, HydrologyBinding.UNBOUND, identity -> null, false,
                ChunkRasterizer.AreaFluid.NONE);
    }

    private HydrologyRuntime(HydrologySettings settings, HydrologyPlanCache cache, IntBinaryOperator naturalHeight,
                             BooleanSupplier mainThread, LongSupplier ticker, HydrologyBinding binding,
                             Function<PreparedHydrologyCacheIdentity, HydrologyPlanStore> stores, boolean caveFluidPolicies,
                             ChunkRasterizer.AreaFluid areaFluid) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.rasterizer = new ChunkRasterizer(settings);
        this.naturalHeight = Objects.requireNonNull(naturalHeight, "naturalHeight");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.rasters = new RasterLru();
        this.last = ThreadLocal.withInitial(LastRaster::new);
        this.queued = ConcurrentHashMap.newKeySet();
        this.failures = new RasterFailures(ticker);
        this.windowPreviewFailures = new RasterFailures(ticker);
        this.regionPreviewFailures = new RasterFailures(ticker);
        this.previews = new HashSet<>();
        this.prefetches = new LinkedHashMap<>(PREFETCH_CAPACITY * 2, 0.75f, true);
        this.binding = Objects.requireNonNull(binding, "binding");
        this.stores = Objects.requireNonNull(stores, "stores");
        this.caveFluidPolicies = caveFluidPolicies;
        this.areaFluid = Objects.requireNonNull(areaFluid, "areaFluid");
    }

    public static HydrologyRuntime create(Engine engine, IrisComplex complex) {
        IrisDimension dimension = engine.getDimension();
        IrisHydrology config = dimension.getHydrology();
        if (config == null || !config.isEnabled()) {
            return DISABLED;
        }
        Objects.requireNonNull(complex, "Hydrology needs the engine's terrain complex");
        HydrologySettings settings = HydrologySettings.compile(config, engine.getSeedManager().getBodies(), dimension.getFluidHeight(), 0,
                engine.getHeight());
        boolean caveFluidPolicies = settings.underground().cavePools().enabled()
                && requireBiomeFluids(dimension.getReachableBiomes(engine), settings);
        if (settings.density() == 0D) {
            return DISABLED;
        }
        HydrologyBinding binding = binding(engine, complex);
        IrisHydrologyTerrainSource source = new IrisHydrologyTerrainSource(complex, dimension, settings, MultiBurst.hydrology,
                MultiBurst.hydrology.parallelism(), binding);
        HydrologyPlanners planners = HydrologyPlanners.create(settings, source);
        EnginePlatformHooks hooks = engine.getPlatformHooks();
        BooleanSupplier mainThread = hooks::isMainThread;
        HydrologyPlanCache cache = HydrologyPlanCache.create(settings, planners.regions(), planners.windows(), MultiBurst.hydrology, mainThread);
        String dimensionKey = dimension.getLoadKey();
        IrisWorld world = engine.getWorld();
        return new HydrologyRuntime(settings, cache, source::naturalHeight, mainThread, System::nanoTime, binding,
                identity -> store(world, dimensionKey, settings, identity), caveFluidPolicies, source::channelFluid);
    }

    static boolean requireBiomeFluids(Collection<IrisBiome> biomes, HydrologySettings settings) {
        boolean named = false;
        for (IrisBiome biome : biomes) {
            IrisHydrologyPolicy policy = biome == null ? null : biome.getHydrologyPolicy();
            String fluid = policy == null ? null : policy.getFluid();
            if (fluid == null) {
                continue;
            }
            if (!settings.fluids().containsKey(fluid)) {
                throw new IllegalArgumentException("Biome '" + biome.getLoadKey() + "' hydrologyPolicy.fluid names '" + fluid
                        + "', which is not one of the hydrology fluids " + settings.fluids().keySet());
            }
            named = true;
        }
        return named;
    }

    public boolean caveFluidPolicies() {
        return caveFluidPolicies;
    }

    public void persistUnder(PreparedHydrologyCacheIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        if (cache != null) {
            storeIdentity = identity;
        }
    }

    public synchronized void openPlanStore() {
        if (cache == null || closed || storeOpen || !settings.persist()) {
            return;
        }
        HydrologyPlanStore store = stores.apply(storeIdentity);
        if (store != null) {
            cache.attachStore(store);
            storeOpen = true;
        }
    }

    public synchronized void closePlanStore() {
        if (cache == null || !storeOpen) {
            return;
        }
        cache.detachStore();
        storeOpen = false;
    }

    public boolean enabled() {
        return cache != null;
    }

    public HydrologySettings settings() {
        return settings;
    }

    public long previewRevision() {
        return cache == null ? 0L : cache.readyRevision() + rasterRevision.get();
    }

    public PreviewScope preview() {
        return new PreviewScope(cache == null ? null : last.get());
    }

    public ChunkWaterRaster raster(int chunkX, int chunkZ) {
        if (cache == null) {
            return ChunkWaterRaster.EMPTY;
        }
        LastRaster slot = last.get();
        if (slot.preview) {
            return rasterPreview(chunkX, chunkZ);
        }
        if (mainThread.getAsBoolean()) {
            return rasterSnapshot(chunkX, chunkZ);
        }
        requireOpen();
        long key = key(chunkX, chunkZ);
        ChunkWaterRaster known = remembered(key, slot);
        return known != null ? known : remember(key, planned(chunkX, chunkZ, key));
    }

    public ChunkWaterRaster rasterSnapshot(int chunkX, int chunkZ) {
        if (cache == null) {
            return ChunkWaterRaster.EMPTY;
        }
        if (closed) {
            return ChunkWaterRaster.PROVISIONAL;
        }
        long key = key(chunkX, chunkZ);
        ChunkWaterRaster known = remembered(key, last.get());
        if (known == null) {
            ChunkWaterRaster landed = queue(chunkX, chunkZ, key, false);
            return landed == null ? ChunkWaterRaster.PROVISIONAL : landed;
        }
        return known;
    }

    public ChunkWaterRaster rasterPreview(int chunkX, int chunkZ) {
        if (cache == null) {
            return ChunkWaterRaster.EMPTY;
        }
        if (closed) {
            return ChunkWaterRaster.PROVISIONAL;
        }
        long key = key(chunkX, chunkZ);
        ChunkWaterRaster known = remembered(key, last.get());
        if (known == null) {
            ChunkWaterRaster landed = queue(chunkX, chunkZ, key, true);
            return landed == null ? ChunkWaterRaster.PROVISIONAL : landed;
        }
        return known;
    }

    public HydrologyPlanPreview planPreview(int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        if (cache == null) {
            return HydrologyPlanPreview.EMPTY;
        }
        if (closed) {
            return HydrologyPlanPreview.PENDING;
        }
        WindowKey min = WindowKey.containing(minBlockX, minBlockZ);
        WindowKey max = WindowKey.containing(maxBlockX, maxBlockZ);
        boolean complete = true;
        List<Reach> reaches = new ArrayList<>();
        List<Pond> ponds = new ArrayList<>();
        List<SeaInlet> inlets = new ArrayList<>();
        for (int wz = min.wz() - 1; wz <= max.wz() + 1; wz++) {
            for (int wx = min.wx() - 1; wx <= max.wx() + 1; wx++) {
                WindowKey window = new WindowKey(wx, wz);
                Optional<IndexedWindow> plan = cache.windowIfReady(window);
                if (plan.isPresent()) {
                    reaches.addAll(plan.get().plan().reaches());
                    ponds.addAll(plan.get().plan().ponds());
                    inlets.addAll(plan.get().plan().inlets());
                } else {
                    complete = false;
                    requestPreview(window, windowPreviewFailures, key(wx, wz), "window " + wx + "," + wz, () -> cache.previewWindow(window));
                }
            }
        }
        Map<Long, Pond> lakes = new LinkedHashMap<>();
        for (RegionKey region : RegionKey.lakeOwners(minBlockX, minBlockZ, maxBlockX, maxBlockZ)) {
            Optional<RegionPlan> plan = cache.regionIfReady(region);
            if (plan.isPresent()) {
                for (Pond lake : plan.get().lakes()) {
                    lakes.putIfAbsent(lake.id(), lake);
                }
            } else {
                complete = false;
                requestPreview(region, regionPreviewFailures, key(region.rx(), region.rz()), "region " + region.rx() + "," + region.rz(),
                        () -> cache.previewRegion(region));
            }
        }
        return new HydrologyPlanPreview(reaches, ponds, List.copyOf(lakes.values()), inlets, complete);
    }

    public ChunkWaterRaster rasterAt(int blockX, int blockZ) {
        return raster(blockX >> 4, blockZ >> 4);
    }

    public CompletableFuture<Void> prepareWindowsAround(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ, int rings) {
        if (cache == null) {
            return CompletableFuture.completedFuture(null);
        }
        return cache.prepareWindowsAround(minChunkX, minChunkZ, maxChunkX, maxChunkZ, rings);
    }

    public HydrologyPregeneration openPregeneration() {
        return new HydrologyPregeneration(this);
    }

    void preparePregeneration(Object epoch, List<long[]> regionOrder) {
        Objects.requireNonNull(epoch, "epoch");
        Objects.requireNonNull(regionOrder, "regionOrder");
        if (regionOrder.size() > MAX_PREGENERATION_REGIONS) {
            throw new IllegalArgumentException("Pregeneration lists " + regionOrder.size() + " regions; hydrology prepares at most "
                    + MAX_PREGENERATION_REGIONS);
        }
        if (cache == null) {
            return;
        }
        int count = regionOrder.size();
        int[] chunkXs = new int[count];
        int[] chunkZs = new int[count];
        for (int index = 0; index < count; index++) {
            long[] region = regionOrder.get(index);
            if (region == null || region.length != 2) {
                throw new IllegalArgumentException("Pregeneration region " + index + " must be {regionX, regionZ}");
            }
            chunkXs[index] = Math.toIntExact(Math.multiplyExact(region[0], CHUNKS_PER_WINDOW));
            chunkZs[index] = Math.toIntExact(Math.multiplyExact(region[1], CHUNKS_PER_WINDOW));
        }
        for (int index = count - 1; index >= 0; index--) {
            cache.prepareWindowsAround(chunkXs[index], chunkZs[index], chunkXs[index] + CHUNKS_PER_WINDOW - 1,
                    chunkZs[index] + CHUNKS_PER_WINDOW - 1, 1, epoch);
        }
    }

    void cancelPregeneration(Object epoch) {
        if (cache != null) {
            cache.cancel(epoch);
        }
    }

    public Optional<HydrologyFeatureRef> nearestFeature(int blockX, int blockZ, HydrologyFeatureQuery query, int radius) {
        return nearestFeature(blockX, blockZ, query, radius, feature -> true);
    }

    public Optional<HydrologyFeatureRef> nearestFeature(int blockX, int blockZ, HydrologyFeatureQuery query, int radius,
                                                        Predicate<HydrologyFeatureRef> eligible) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(eligible, "eligible");
        if (radius < 0) {
            throw new IllegalArgumentException("Hydrology feature search radius must not be negative, got " + radius);
        }
        if (cache == null) {
            return Optional.empty();
        }
        if (mainThread.getAsBoolean()) {
            throw new IllegalStateException("Hydrology feature search was requested on the main thread.");
        }
        requireOpen();
        return new HydrologyFeatureSearch(settings, cache, blockX, blockZ, query, eligible, radius).nearest();
    }

    public HydrologyFeatureType channelType(HydrologyTier tier) {
        Objects.requireNonNull(tier, "tier");
        if (settings == null) {
            throw new IllegalStateException("Hydrology is disabled, so it has no channel tiers.");
        }
        return HydrologyFeatureSearch.channelType(settings, tier);
    }

    public List<SeaInlet> inlets(int chunkX, int chunkZ) {
        if (cache == null) {
            return List.of();
        }
        requireOpen();
        boolean main = mainThread.getAsBoolean();
        List<SeaInlet> touching = new ArrayList<>();
        for (CompletableFuture<IndexedWindow> plan : plans(neighbourhood(chunkX, chunkZ), List.of(), PlanMode.DEMAND).windowFutures()) {
            if (main && !plan.isDone()) {
                throw new IllegalStateException("The hydrology windows around chunk " + chunkX + "," + chunkZ
                        + " are not planned yet, so its sea inlets cannot be generated on the main thread.");
            }
            IndexedWindow window = main ? plan.join() : cache.awaitPlan(plan);
            for (SeaInlet inlet : window.plan().inlets()) {
                if (SeaInletRasterizer.touches(inlet, chunkX, chunkZ)) {
                    touching.add(inlet);
                }
            }
        }
        return touching;
    }

    public Set<HydrologyFeatureType> standingWaterTypes(int chunkX, int chunkZ, long featureId) {
        if (cache == null) {
            throw new IllegalStateException("Hydrology is disabled, so it has no standing water.");
        }
        if (mainThread.getAsBoolean()) {
            throw new IllegalStateException("Hydrology standing water was requested on the main thread.");
        }
        requireOpen();
        PlanSet plans = plans(neighbourhood(chunkX, chunkZ), ChunkRasterizer.lakeRegions(chunkX, chunkZ), PlanMode.DEMAND);
        for (CompletableFuture<IndexedWindow> window : plans.windowFutures()) {
            for (Pond pond : cache.awaitPlan(window).plan().ponds()) {
                if (pond.id() == featureId) {
                    return HydrologyFeatureSearch.standingTypes(pond);
                }
            }
        }
        for (CompletableFuture<RegionPlan> region : plans.lakeFutures()) {
            for (Pond lake : cache.awaitPlan(region).lakes()) {
                if (lake.id() == featureId) {
                    return HydrologyFeatureSearch.standingTypes(lake);
                }
            }
        }
        throw new IllegalStateException("Chunk " + chunkX + "," + chunkZ + " holds standing water " + featureId
                + " that none of its hydrology plans define.");
    }

    public synchronized void close() {
        if (cache == null) {
            return;
        }
        closed = true;
        cache.close();
        synchronized (rasters) {
            rasters.clear();
        }
        synchronized (prefetches) {
            prefetches.clear();
        }
    }

    private static HydrologyBinding binding(Engine engine, IrisComplex complex) {
        if (!(engine instanceof IrisEngine iris)) {
            return HydrologyBinding.UNBOUND;
        }
        return () -> {
            if (iris.getComplex() == complex) {
                return HydrologyBinding.NO_SCOPE;
            }
            IrisEngine.GenerationRuntimeScope runtime = iris.openOwningGenerationRuntimeScope(complex);
            if (runtime == null) {
                return HydrologyBinding.NO_SCOPE;
            }
            IrisContext.Scope context;
            try {
                context = IrisContext.open(iris, iris.getGenerationSessionId(), null);
            } catch (RuntimeException | Error failure) {
                runtime.close();
                throw failure;
            }
            return () -> {
                try {
                    context.close();
                } finally {
                    runtime.close();
                }
            };
        };
    }

    private static HydrologyPlanStore store(IrisWorld world, String dimension, HydrologySettings settings,
                                            PreparedHydrologyCacheIdentity identity) {
        String name = world == null ? "unknown" : world.name();
        File folder = world == null ? null : world.worldFolder();
        if (identity == null || folder == null) {
            IrisLogging.warnOnce("hydrology-plans-in-memory:" + name + ":" + dimension,
                    "Hydrology plans for world %s (%s) stay in memory and are planned again after a restart: %s", name, dimension,
                    folder == null ? "the world has no folder" : "its generation inputs have no stable fingerprint");
            return null;
        }
        Path root = folder.toPath().resolve("iris").resolve("hydrology").resolve(dimension);
        try {
            return new HydrologyPlanStore(root, identity.planScope(settings.seed(), settings.fingerprint()), KEEP_SCOPES, settings);
        } catch (UncheckedIOException failure) {
            IrisLogging.reportError("Hydrology plans for world " + name + " (" + dimension + ") cannot be saved in " + root
                    + "; they stay in memory.", failure);
            return null;
        }
    }

    private ChunkWaterRaster planned(int chunkX, int chunkZ, long chunk) {
        try {
            List<WindowKey> windowKeys = neighbourhood(chunkX, chunkZ);
            List<RegionKey> lakeKeys = ChunkRasterizer.lakeRegions(chunkX, chunkZ);
            PlanSet plans = plans(windowKeys, lakeKeys, PlanMode.DEMAND);
            List<IndexedWindow> windows = new ArrayList<>(windowKeys.size());
            for (CompletableFuture<IndexedWindow> window : plans.windowFutures()) {
                windows.add(cache.awaitPlan(window));
            }
            List<RegionPlan> lakes = new ArrayList<>(lakeKeys.size());
            for (CompletableFuture<RegionPlan> lake : plans.lakeFutures()) {
                lakes.add(cache.awaitPlan(lake));
            }
            ChunkWaterRaster raster;
            try (HydrologyBinding.Scope ignored = binding.open()) {
                raster = rasterizer.rasterize(chunkX, chunkZ, naturalHeight, windows, lakes, areaFluid);
            }
            prefetch(chunkX, chunkZ);
            return raster;
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (RuntimeException failure) {
            failures.failed(chunk);
            throw new IllegalStateException("Hydrology could not build the water raster for chunk " + chunkX + "," + chunkZ + ".", failure);
        }
    }

    private PlanSet plans(List<WindowKey> windowKeys, List<RegionKey> lakeKeys, PlanMode mode) {
        List<CompletableFuture<IndexedWindow>> windows = new ArrayList<>(windowKeys.size());
        for (WindowKey key : windowKeys) {
            windows.add(switch (mode) {
                case DEMAND -> cache.demandWindow(key);
                case PREFETCH -> cache.prefetchWindow(key);
                case PREVIEW -> cache.previewWindow(key);
            });
        }
        List<CompletableFuture<RegionPlan>> lakes = new ArrayList<>(lakeKeys.size());
        for (RegionKey key : lakeKeys) {
            lakes.add(switch (mode) {
                case DEMAND -> cache.demandRegion(key);
                case PREFETCH -> cache.prefetchRegion(key);
                case PREVIEW -> cache.previewRegion(key);
            });
        }
        return new PlanSet(windows, lakes);
    }

    private ChunkWaterRaster queue(int chunkX, int chunkZ, long key, boolean preview) {
        if (failures.backingOff(key) || !queued.add(key)) {
            return null;
        }
        if (preview && !admitPreview(key)) {
            queued.remove(key);
            return null;
        }
        ChunkWaterRaster landed = remembered(key, last.get());
        if (landed != null) {
            finish(key, preview);
            return landed;
        }
        try {
            PlanSet plans = plans(neighbourhood(chunkX, chunkZ), ChunkRasterizer.lakeRegions(chunkX, chunkZ),
                    preview ? PlanMode.PREVIEW : PlanMode.PREFETCH);
            if (!preview) {
                prefetch(chunkX, chunkZ);
            }
            plans.all().whenComplete((ignored, failure) -> {
                if (failure != null || closed) {
                    unqueue(chunkX, chunkZ, key, preview, failure);
                } else {
                    cache.runSpeculative(() -> rasterizeQueued(chunkX, chunkZ, key, preview, plans), () -> finish(key, preview));
                }
            });
        } catch (RuntimeException failure) {
            finish(key, preview);
            throw failure;
        }
        return null;
    }

    private void finish(long key, boolean preview) {
        queued.remove(key);
        if (preview) {
            releasePreview(key);
        }
    }

    private boolean admitPreview(Object key) {
        synchronized (previews) {
            if (previews.size() >= MAX_PREVIEW_REQUESTS || !previews.add(key)) {
                return false;
            }
        }
        return true;
    }

    private void releasePreview(Object key) {
        synchronized (previews) {
            previews.remove(key);
        }
    }

    private void requestPreview(Object key, RasterFailures previewFailures, long failureKey, String what,
                                Supplier<CompletableFuture<?>> request) {
        if (previewFailures.backingOff(failureKey) || !admitPreview(key)) {
            return;
        }
        CompletableFuture<?> planned;
        try {
            planned = request.get();
        } catch (RuntimeException failure) {
            releasePreview(key);
            throw failure;
        }
        planned.whenComplete((ignored, failure) -> {
            try {
                Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
                if (cause != null && !closed && !(cause instanceof CancellationException)) {
                    previewFailures.report(failureKey, "Hydrology could not plan " + what + " for a map preview; it stays provisional for 30 s.",
                            cause);
                }
            } finally {
                releasePreview(key);
            }
        });
    }

    private void unqueue(int chunkX, int chunkZ, long key, boolean preview, Throwable failure) {
        try {
            Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
            if (cause != null && !closed && !(cause instanceof CancellationException)) {
                failures.report(key, "Hydrology could not plan the water raster for chunk " + chunkX + "," + chunkZ
                        + "; its snapshots stay provisional for 30 s.", cause);
            }
        } finally {
            finish(key, preview);
        }
    }

    private void rasterizeQueued(int chunkX, int chunkZ, long key, boolean preview, PlanSet plans) {
        try {
            if (!closed) {
                try (HydrologyBinding.Scope ignored = binding.open()) {
                    remember(key, rasterizer.rasterize(chunkX, chunkZ, naturalHeight, plans.windows(), plans.lakes(), areaFluid));
                }
            }
        } catch (RuntimeException failure) {
            failures.report(key, "Hydrology could not build the queued water raster for chunk " + chunkX + "," + chunkZ
                    + "; its snapshots stay provisional for 30 s.", failure);
        } finally {
            finish(key, preview);
        }
    }

    private void prefetch(int chunkX, int chunkZ) {
        if (settings.prefetchRings() <= 0) {
            return;
        }
        WindowKey owner = WindowKey.containing(chunkX << 4, chunkZ << 4);
        synchronized (prefetches) {
            long evictions = cache.windowEvictions();
            WindowPrefetch known = prefetches.get(owner);
            if (known != null && !known.prepared().isCompletedExceptionally()
                    && (!known.prepared().isDone() || known.evictions() == evictions)) {
                return;
            }
            CompletableFuture<Void> prepared = cache.prepareWindowsAround(chunkX, chunkZ, chunkX, chunkZ, 1 + settings.prefetchRings());
            prefetches.put(owner, new WindowPrefetch(prepared, evictions));
            if (prefetches.size() > PREFETCH_CAPACITY) {
                prefetches.pollFirstEntry();
            }
        }
    }

    private static List<WindowKey> neighbourhood(int chunkX, int chunkZ) {
        WindowKey own = WindowKey.containing(chunkX << 4, chunkZ << 4);
        List<WindowKey> keys = new ArrayList<>(9);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                keys.add(new WindowKey(own.wx() + dx, own.wz() + dz));
            }
        }
        return keys;
    }

    private ChunkWaterRaster remembered(long key, LastRaster slot) {
        if (slot.raster != null && slot.key == key) {
            return slot.raster;
        }
        ChunkWaterRaster raster;
        synchronized (rasters) {
            raster = rasters.get(key);
        }
        if (raster != null) {
            slot.remember(key, raster);
        }
        return raster;
    }

    private ChunkWaterRaster remember(long key, ChunkWaterRaster raster) {
        synchronized (rasters) {
            if (rasters.put(key, raster) == null) {
                rasterRevision.incrementAndGet();
            }
        }
        last.get().remember(key, raster);
        return raster;
    }

    private void requireOpen() {
        if (closed) {
            throw new CancellationException("Hydrology runtime is closed.");
        }
    }

    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private enum PlanMode {
        DEMAND,
        PREFETCH,
        PREVIEW
    }

    private record PlanSet(List<CompletableFuture<IndexedWindow>> windowFutures, List<CompletableFuture<RegionPlan>> lakeFutures) {
        private CompletableFuture<Void> all() {
            List<CompletableFuture<?>> futures = new ArrayList<>(windowFutures.size() + lakeFutures.size());
            futures.addAll(windowFutures);
            futures.addAll(lakeFutures);
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]));
        }

        private List<IndexedWindow> windows() {
            List<IndexedWindow> windows = new ArrayList<>(windowFutures.size());
            for (CompletableFuture<IndexedWindow> future : windowFutures) {
                windows.add(future.join());
            }
            return windows;
        }

        private List<RegionPlan> lakes() {
            List<RegionPlan> lakes = new ArrayList<>(lakeFutures.size());
            for (CompletableFuture<RegionPlan> future : lakeFutures) {
                lakes.add(future.join());
            }
            return lakes;
        }
    }

    private record WindowPrefetch(CompletableFuture<Void> prepared, long evictions) {
    }

    private static final class LastRaster {
        private long key;
        private ChunkWaterRaster raster;
        private boolean preview;

        private void remember(long key, ChunkWaterRaster raster) {
            this.key = key;
            this.raster = raster;
        }
    }

    public static final class PreviewScope implements AutoCloseable {
        private final LastRaster slot;
        private final boolean previous;
        private boolean closed;

        private PreviewScope(LastRaster slot) {
            this.slot = slot;
            this.previous = slot != null && slot.preview;
            if (slot != null) {
                slot.preview = true;
            }
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                if (slot != null) {
                    slot.preview = previous;
                }
            }
        }
    }

    private static final class RasterLru extends LinkedHashMap<Long, ChunkWaterRaster> {
        private RasterLru() {
            super(RASTER_CAPACITY * 2, 0.75f, true);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, ChunkWaterRaster> eldest) {
            return size() > RASTER_CAPACITY;
        }
    }
}
