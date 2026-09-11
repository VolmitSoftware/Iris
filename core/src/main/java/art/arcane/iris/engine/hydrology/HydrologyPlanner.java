package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.cave.CaveVoxelView;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveContainmentPlanner;
import art.arcane.iris.engine.hydrology.surface.SurfaceCourseBuilder;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.iris.util.project.noise.SimplexNoise;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinWorkerThread;

public final class HydrologyPlanner {
    static final int ROUTING_CONTEXT_CACHE_SIZE = 64;
    static final int RESOLVED_OWNER_CACHE_SIZE = 64;
    static final int REFINED_EDGE_CACHE_SIZE = 4096;
    final long worldSeed;
    final HydrologyPlannerSettings settings;
    final HydrologyTerrainSampler sampler;
    final HydrologyRoutingTerrainSampler routingSampler;
    final HydrologyNaturalTerrainSampler naturalSampler;
    final HydrologyGeometrySampler geometrySampler;
    final int minimumY;
    final HydrologyCaveVoxelViewFactory caveViewFactory;
    final Cache<HydrologyTileKey, SourceRoutingContext> routingContexts;
    final ConcurrentHashMap<HydrologyTileKey, RoutingContextLoad> resolvingRoutingContexts;
    final Cache<HydrologyTileKey, CrossTileResolvedOwner> resolvedOwners;
    final Cache<RefinedEdgeKey, List<HydrologyPoint>> refinedEdgeCache;
    final ConcurrentHashMap<HydrologyTileKey, CompletableFuture<CrossTileResolvedOwner>> resolvingOwners;
    final ThreadLocal<PlanningSamples> planningSamples;
    final ThreadLocal<DraftProfile> draftProfiles;
    final SimplexNoise routeAnchorX;
    final SimplexNoise routeAnchorZ;
    final SimplexNoise routeWormPrimary;
    final SimplexNoise routeWormDetail;
    final SurfaceCourseBuilder surfaceCourseBuilder;
    final HydrologyCrossTileResolver crossTile;
    final HydrologyOutletPlanner outletPlanner;
    final HydrologySourcePlanner sourcePlanner;
    final HydrologyRouteGeometry routeGeometry;
    final HydrologyRoutePath routePaths;
    final HydrologySurfaceCoursePlanner surfaceCourses;
    final HydrologyTributaryPlanner tributaries;
    final HydrologyUndergroundCoursePlanner undergroundCourses;
    final HydrologySegmentBuilder segments;
    final HydrologyFeatureSitePlanner featureSites;
    final HydrologyRegionalPlanner regional;

    public HydrologyPlanner(long worldSeed, HydrologyPlannerSettings settings, HydrologyTerrainSampler sampler) {
        this(
                worldSeed,
                settings,
                sampler,
                HydrologyGeometrySampler.deterministic(sampler),
                -4096,
                footprint -> new HydrologyTerrainCaveVoxelView(sampler, settings.seaLevel(), -4096, 4096)
        );
    }

    public HydrologyPlanner(
            long worldSeed,
            HydrologyPlannerSettings settings,
            HydrologyTerrainSampler sampler,
            int worldHeight
    ) {
        this(
                worldSeed,
                settings,
                sampler,
                HydrologyGeometrySampler.deterministic(sampler),
                0,
                footprint -> new HydrologyTerrainCaveVoxelView(sampler, settings.seaLevel(), worldHeight)
        );
    }

    public HydrologyPlanner(
            long worldSeed,
            HydrologyPlannerSettings settings,
            HydrologyTerrainSampler sampler,
            CaveVoxelView caveView
    ) {
        this(
                worldSeed,
                settings,
                sampler,
                HydrologyGeometrySampler.deterministic(sampler),
                Integer.MIN_VALUE,
                footprint -> caveView
        );
    }

    public HydrologyPlanner(
            long worldSeed,
            HydrologyPlannerSettings settings,
            HydrologyTerrainSampler sampler,
            int minimumY,
            HydrologyCaveVoxelViewFactory caveViewFactory
    ) {
        this(
                worldSeed,
                settings,
                sampler,
                HydrologyGeometrySampler.deterministic(sampler),
                minimumY,
                caveViewFactory
        );
    }

    public HydrologyPlanner(
            long worldSeed,
            HydrologyPlannerSettings settings,
            HydrologyTerrainSampler sampler,
            HydrologyGeometrySampler geometrySampler,
            int minimumY,
            HydrologyCaveVoxelViewFactory caveViewFactory
    ) {
        this(
                worldSeed,
                settings,
                sampler,
                new PointRoutingTerrainSampler(sampler),
                geometrySampler,
                minimumY,
                caveViewFactory
        );
    }

    public HydrologyPlanner(
            long worldSeed,
            HydrologyPlannerSettings settings,
            HydrologyTerrainSampler sampler,
            HydrologyRoutingTerrainSampler routingSampler,
            HydrologyGeometrySampler geometrySampler,
            int minimumY,
            HydrologyCaveVoxelViewFactory caveViewFactory
    ) {
        this.worldSeed = worldSeed;
        this.settings = Objects.requireNonNull(settings, "settings");
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        this.routingSampler = Objects.requireNonNull(routingSampler, "routingSampler");
        this.naturalSampler = routingSampler instanceof HydrologyNaturalTerrainSampler pointSampler
                ? pointSampler
                : null;
        this.geometrySampler = Objects.requireNonNull(geometrySampler, "geometrySampler");
        this.minimumY = minimumY;
        this.caveViewFactory = Objects.requireNonNull(caveViewFactory, "caveViewFactory");
        this.routingContexts = Caffeine.newBuilder()
                .maximumSize(ROUTING_CONTEXT_CACHE_SIZE)
                .build();
        this.resolvingRoutingContexts = new ConcurrentHashMap<>();
        this.resolvedOwners = Caffeine.newBuilder()
                .maximumSize(RESOLVED_OWNER_CACHE_SIZE)
                .build();
        this.refinedEdgeCache = Caffeine.newBuilder()
                .maximumSize(REFINED_EDGE_CACHE_SIZE)
                .build();
        this.resolvingOwners = new ConcurrentHashMap<>();
        this.planningSamples = new ThreadLocal<>();
        this.draftProfiles = new ThreadLocal<>();
        this.routeAnchorX = new SimplexNoise(HydrologyHash.mix(worldSeed, HydrologyRouteGeometry.ROUTE_ANCHOR_X_SALT));
        this.routeAnchorZ = new SimplexNoise(HydrologyHash.mix(worldSeed, HydrologyRouteGeometry.ROUTE_ANCHOR_Z_SALT));
        this.routeWormPrimary = new SimplexNoise(HydrologyHash.mix(worldSeed, HydrologyRouteGeometry.ROUTE_WORM_PRIMARY_SALT));
        this.routeWormDetail = new SimplexNoise(HydrologyHash.mix(worldSeed, HydrologyRouteGeometry.ROUTE_WORM_DETAIL_SALT));
        this.surfaceCourseBuilder = new SurfaceCourseBuilder(
                settings.surface(),
                this::sampleBasisWithoutSlope,
                geometrySampler,
                settings.seaLevel()
        );
        this.crossTile = new HydrologyCrossTileResolver(this);
        this.outletPlanner = new HydrologyOutletPlanner(this);
        this.sourcePlanner = new HydrologySourcePlanner(this);
        this.routeGeometry = new HydrologyRouteGeometry(this);
        this.routePaths = new HydrologyRoutePath(this);
        this.surfaceCourses = new HydrologySurfaceCoursePlanner(this);
        this.tributaries = new HydrologyTributaryPlanner(this);
        this.undergroundCourses = new HydrologyUndergroundCoursePlanner(this);
        this.segments = new HydrologySegmentBuilder(this);
        this.featureSites = new HydrologyFeatureSitePlanner(this);
        this.regional = new HydrologyRegionalPlanner(this);
    }

    public long worldSeed() {
        return worldSeed;
    }

    public HydrologyPlannerSettings settings() {
        return settings;
    }

    /**
     * A tile with the same identity as a planned tile but no rivers, used when planning the tile
     * failed and terrain there must still generate.
     */
    public HydrologyTile emptyTile(HydrologyTileKey key) {
        Objects.requireNonNull(key, "key");
        return new HydrologyTile(
                key,
                worldSeed,
                settings.fingerprint(),
                settings.routing().tileSize(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                RiverFootprint.empty()
        );
    }

    public HydrologyTile plan(HydrologyTileKey key) {
        long started = System.nanoTime();
        CrossTileResolution resolution = resolveCrossTileOwner(key);
        long materializeStarted = System.nanoTime();
        HydrologyTile tile = crossTile.materializeAcceptedTile(resolution);
        long finished = System.nanoTime();
        IrisLogging.debug(
                "Hydrology tile %d,%d planned in %dms: owners=%d resolve=%dms materialize=%dms courses=%d on %s",
                key.tileX(),
                key.tileZ(),
                (finished - started) / 1_000_000L,
                resolution.ownerCount(),
                (materializeStarted - started) / 1_000_000L,
                (finished - materializeStarted) / 1_000_000L,
                tile.courses().size(),
                Thread.currentThread().getName()
        );
        return tile;
    }

    void clearOwnerDrafts() {
        resolvedOwners.invalidateAll();
        regional.clear();
    }

    void reuseResolvedTile(HydrologyTile tile) {
        Objects.requireNonNull(tile, "tile");
        if (tile.worldSeed() != worldSeed
                || tile.settingsFingerprint() != settings.fingerprint()
                || tile.tileSize() != settings.routing().tileSize()) {
            throw new IllegalArgumentException("Hydrology tile does not match this planner.");
        }
        HydrologyCaveCourseFilter.Result result = new HydrologyCaveCourseFilter.Result(
                tile.nodes(),
                tile.edges(),
                tile.outlets(),
                tile.courses(),
                tile.cavePlans()
        );
        result = regional.withoutRegional(result, tile.key());
        HydrologyOwnerDraft draft = new HydrologyOwnerDraft(
                tile.key(),
                result,
                tile.diagnosticCandidates(),
                null
        );
        resolvedOwners.put(tile.key(), new CrossTileResolvedOwner(draft, List.of()));
    }

    CrossTileResolution resolveCrossTileOwner(HydrologyTileKey key) {
        long started = System.nanoTime();
        CrossTileResolutionContext context = new CrossTileResolutionContext(
                key,
                maximumCrossTileDependencyOffset(key),
                maximumCrossTileDependencyOwners(key)
        );
        CrossTileResolvedOwner resolved = resolveCrossTileOwner(key, context);
        return new CrossTileResolution(
                resolved.draft(),
                resolved.observedRejections(),
                context.iterations(),
                context.ownerCount(),
                System.nanoTime() - started
        );
    }

    CrossTileResolvedOwner resolveCrossTileOwner(
            HydrologyTileKey key,
            CrossTileResolutionContext context
    ) {
        CrossTileResolvedOwner local = context.resolved(key);
        if (local != null) {
            return local;
        }
        CrossTileResolvedOwner cached = resolvedOwners.getIfPresent(key);
        if (cached != null) {
            context.remember(key, cached);
            return cached;
        }
        CompletableFuture<CrossTileResolvedOwner> owned = new CompletableFuture<>();
        CompletableFuture<CrossTileResolvedOwner> existing = resolvingOwners.putIfAbsent(key, owned);
        if (existing != null) {
            CrossTileResolvedOwner resolved = awaitResolvedOwner(existing);
            context.remember(key, resolved);
            return resolved;
        }
        if (!context.begin(key)) {
            resolvingOwners.remove(key, owned);
            throw new IllegalStateException("Cross-tile owner color dependencies must be acyclic.");
        }
        try {
            int ownerRank = crossTile.ownerColorRank(key);
            ColorRankedDraftAdmission admission = new ColorRankedDraftAdmission(key, ownerRank, context);
            HydrologyOwnerDraft draft;
            try (admission) {
                try {
                    draft = crossTile.compileOwnerDraft(
                            key,
                            admission,
                            key.equals(context.root())
                    );
                } catch (RuntimeException | Error failure) {
                    admission.primaryFailure = failure;
                    throw failure;
                }
            }
            CrossTileResolvedOwner resolved = new CrossTileResolvedOwner(draft, admission.observedRejections());
            context.remember(key, resolved);
            CrossTileResolvedOwner shared = resolved.withoutFootprintCompiler();
            resolvedOwners.put(key, shared);
            owned.complete(shared);
            return resolved;
        } catch (Throwable failure) {
            owned.completeExceptionally(failure);
            throw propagateOwnerFailure(failure);
        } finally {
            context.end(key);
            resolvingOwners.remove(key, owned);
        }
    }

    List<CrossTileResolvedOwner> resolveLowerRankOwners(
            List<HydrologyTileKey> candidateKeys,
            CrossTileResolutionContext context,
            Map<HydrologyTileKey, HydrologyForkJoin.Task<CrossTileResolvedOwner>> preparedOwners
    ) {
        if (candidateKeys.size() < 2 || !IrisPlatforms.isBound()) {
            ArrayList<CrossTileResolvedOwner> resolved = new ArrayList<>(candidateKeys.size());
            for (HydrologyTileKey candidateKey : candidateKeys) {
                HydrologyForkJoin.Task<CrossTileResolvedOwner> prepared = preparedOwners.get(candidateKey);
                CrossTileResolvedOwner owner = prepared == null
                        ? resolveCrossTileOwner(candidateKey, context) : prepared.await();
                context.remember(candidateKey, owner);
                resolved.add(owner);
            }
            return List.copyOf(resolved);
        }
        // Neighbour drafts use the current pool (or the burst pool from outside any pool).
        // Waiting workers claim their own tasks without helping unrelated owner drafts.
        ArrayList<Callable<CrossTileResolvedOwner>> tasks = new ArrayList<>(candidateKeys.size());
        for (HydrologyTileKey candidateKey : candidateKeys) {
            HydrologyForkJoin.Task<CrossTileResolvedOwner> prepared = preparedOwners.get(candidateKey);
            tasks.add(prepared == null ? () -> resolveIndependentOwner(candidateKey) : prepared::await);
        }
        List<CrossTileResolvedOwner> owners = HydrologyForkJoin.invokeAll(tasks, MultiBurst.burst);
        ArrayList<CrossTileResolvedOwner> resolved = new ArrayList<>(candidateKeys.size());
        for (int index = 0; index < candidateKeys.size(); index++) {
            CrossTileResolvedOwner owner = owners.get(index);
            context.remember(candidateKeys.get(index), owner);
            resolved.add(owner);
        }
        return List.copyOf(resolved);
    }

    CrossTileResolvedOwner resolveIndependentOwner(HydrologyTileKey key) {
        CrossTileResolutionContext context = new CrossTileResolutionContext(
                key,
                maximumCrossTileDependencyOffset(key),
                maximumCrossTileDependencyOwners(key)
        );
        return resolveCrossTileOwner(key, context);
    }

    static CrossTileResolvedOwner awaitResolvedOwner(
            CompletableFuture<CrossTileResolvedOwner> future
    ) {
        try {
            return future.join();
        } catch (CompletionException failure) {
            throw propagateOwnerFailure(failure.getCause());
        }
    }

    static RuntimeException propagateOwnerFailure(Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("Hydrology owner planning failed.", failure);
    }

    int maximumCrossTileDependencyOwners(HydrologyTileKey key) {
        long offset = maximumCrossTileDependencyOffset(key);
        long width = Math.addExact(Math.multiplyExact(2L, offset), 1L);
        return Math.toIntExact(Math.multiplyExact(width, width));
    }

    long maximumCrossTileDependencyOffset(HydrologyTileKey key) {
        return Math.multiplyExact(
                (long) crossTile.ownerColorRank(key),
                settings.crossTileColorPeriod() - 1L
        );
    }

    SourceRoutingContext sourceRoutingContext(HydrologyTileKey key) {
        SourceRoutingContext cached = routingContexts.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        RoutingContextLoad owned = new RoutingContextLoad(Thread.currentThread(), new CompletableFuture<>());
        RoutingContextLoad existing = resolvingRoutingContexts.putIfAbsent(key, owned);
        if (existing != null) {
            if (existing.owner() == Thread.currentThread()) {
                throw new IllegalStateException("Hydrology routing context cannot recursively load its own tile.");
            }
            try {
                return existing.future().join();
            } catch (CompletionException failure) {
                throw propagateOwnerFailure(failure.getCause());
            }
        }
        try {
            SourceRoutingContext resolved = routingContexts.getIfPresent(key);
            if (resolved == null) {
                resolved = sourcePlanner.compileSourceRoutingContext(key);
                routingContexts.put(key, resolved);
            }
            owned.future().complete(resolved);
            return resolved;
        } catch (Throwable failure) {
            owned.future().completeExceptionally(failure);
            throw propagateOwnerFailure(failure);
        } finally {
            resolvingRoutingContexts.remove(key, owned);
        }
    }

    int settingsSeaLevel(HydrologyTerrainSample terrain) {
        return settings.seaLevel();
    }

    static HydrologyPoint withY(HydrologyPoint point, int y) {
        return new HydrologyPoint(point.x(), y, point.z());
    }

    static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    int sampleGeometry(
            HydrologyGeometrySampler.Field field,
            String profileKey,
            int x,
            int z,
            long stableId,
            int minimum,
            int maximum
    ) {
        HydrologyGeometrySampler.Request request = new HydrologyGeometrySampler.Request(
                field,
                profileKey,
                x,
                z,
                stableId,
                minimum,
                maximum
        );
        PlanningSamples samples = planningSamples.get();
        if (samples != null) {
            Integer cached = samples.geometry.get(request);
            if (cached != null) {
                return cached;
            }
        }
        int sampled = geometrySampler.sample(request);
        if (sampled < minimum || sampled > maximum) {
            throw new IllegalStateException(
                    "Hydrology geometry sampler returned " + sampled + " outside " + minimum + ".." + maximum
                            + " for " + field + " at " + x + "," + z + "."
            );
        }
        if (samples != null) {
            samples.geometry.put(request, sampled);
        }
        return sampled;
    }

    HydrologyTerrainSample sampleBasis(int blockX, int blockZ) {
        PlanningSamples samples = planningSamples.get();
        if (samples == null) {
            return naturalSampler == null
                    ? sampler.sample(blockX, blockZ)
                    : naturalSampler.sampleBasis(blockX, blockZ);
        }
        long packed = RiverFootprint.pack(blockX, blockZ);
        HydrologyTerrainSample cached = samples.basis.get(packed);
        if (cached != null || samples.basis.containsKey(packed)) {
            return cached;
        }
        HydrologyTerrainSample sampled = naturalSampler == null
                ? sampler.sample(blockX, blockZ)
                : naturalSampler.sampleBasis(blockX, blockZ);
        samples.basis.put(packed, sampled);
        return sampled;
    }

    HydrologyTerrainSample sampleLandBasis(int blockX, int blockZ) {
        HydrologyTerrainSample terrain = sampleBasis(blockX, blockZ);
        return terrain == null || terrain.ocean() ? null : terrain;
    }

    HydrologyTerrainSample sampleBasisWithoutSlope(int blockX, int blockZ) {
        if (naturalSampler == null) {
            return sampleBasis(blockX, blockZ);
        }
        PlanningSamples samples = planningSamples.get();
        if (samples == null) {
            return naturalSampler.sampleBasisWithoutSlope(blockX, blockZ);
        }
        long packed = RiverFootprint.pack(blockX, blockZ);
        HydrologyTerrainSample fullSample = samples.basis.get(packed);
        if (fullSample != null || samples.basis.containsKey(packed)) {
            return fullSample;
        }
        HydrologyTerrainSample cached = samples.basisWithoutSlope.get(packed);
        if (cached != null || samples.basisWithoutSlope.containsKey(packed)) {
            return cached;
        }
        HydrologyTerrainSample sampled = naturalSampler.sampleBasisWithoutSlope(blockX, blockZ);
        samples.basisWithoutSlope.put(packed, sampled);
        return sampled;
    }

    HydrologyTerrainSample sampleLandBasisWithoutSlope(int blockX, int blockZ) {
        HydrologyTerrainSample terrain = sampleBasisWithoutSlope(blockX, blockZ);
        return terrain == null || terrain.ocean() ? null : terrain;
    }

    HydrologyTerrainSample sampleDetailed(int blockX, int blockZ) {
        PlanningSamples samples = planningSamples.get();
        if (samples == null) {
            return sampler.sample(blockX, blockZ);
        }
        long packed = RiverFootprint.pack(blockX, blockZ);
        HydrologyTerrainSample cached = samples.detailed.get(packed);
        if (cached != null || samples.detailed.containsKey(packed)) {
            return cached;
        }
        HydrologyTerrainSample sampled = sampler.sample(blockX, blockZ);
        samples.detailed.put(packed, sampled);
        return sampled;
    }

    HydrologyRoutingTerrainSampler.NaturalClassification classifyNatural(int blockX, int blockZ) {
        PlanningSamples samples = planningSamples.get();
        if (samples == null) {
            return naturalSampler.classifyNatural(blockX, blockZ);
        }
        long packed = RiverFootprint.pack(blockX, blockZ);
        HydrologyRoutingTerrainSampler.NaturalClassification cached = samples.classifications.get(packed);
        if (cached != null) {
            return cached;
        }
        HydrologyRoutingTerrainSampler.NaturalClassification sampled = Objects.requireNonNull(
                naturalSampler.classifyNatural(blockX, blockZ),
                "Hydrology natural terrain classifier returned null at " + blockX + "," + blockZ
        );
        samples.classifications.put(packed, sampled);
        return sampled;
    }

    static final class PointRoutingTerrainSampler implements HydrologyRoutingTerrainSampler {
        private final HydrologyTerrainSampler sampler;

        private PointRoutingTerrainSampler(HydrologyTerrainSampler sampler) {
            this.sampler = Objects.requireNonNull(sampler, "sampler");
        }

        @Override
        public HydrologyTerrainSample[] sampleGrid(GridRequest request) {
            int minimumX = request.minimumX();
            int minimumZ = request.minimumZ();
            int width = request.width();
            int spacing = request.spacing();
            HydrologyTerrainSample[] samples = new HydrologyTerrainSample[Math.multiplyExact(width, width)];
            for (int gridZ = 0; gridZ < width; gridZ++) {
                int z = Math.toIntExact((long) minimumZ + (long) gridZ * spacing);
                for (int gridX = 0; gridX < width; gridX++) {
                    int x = Math.toIntExact((long) minimumX + (long) gridX * spacing);
                    samples[gridZ * width + gridX] = sampler.sample(x, z);
                }
            }
            return samples;
        }

        @Override
        public NaturalClassification classifyNatural(int blockX, int blockZ) {
            HydrologyTerrainSample terrain = sampler.sample(blockX, blockZ);
            if (terrain == null) {
                return NaturalClassification.UNAVAILABLE;
            }
            return terrain.ocean() ? NaturalClassification.OCEAN : NaturalClassification.LAND;
        }
    }

    static final class PlanningSamples {
        private final Long2ObjectOpenHashMap<HydrologyTerrainSample> basis;
        private final Long2ObjectOpenHashMap<HydrologyTerrainSample> basisWithoutSlope;
        final Long2ObjectOpenHashMap<HydrologyTerrainSample> detailed;
        private final Long2ObjectOpenHashMap<HydrologyRoutingTerrainSampler.NaturalClassification> classifications;
        private final HashMap<HydrologyGeometrySampler.Request, Integer> geometry;
        final HashMap<MinimumLandHeightKey, Integer> minimumLandHeights;
        final HashMap<Integer, List<HydrologyGridOffset>> radialOffsets;
        final HashMap<UndergroundSegmentCapKey, Integer> undergroundSegmentCaps;
        final HashMap<Long, HydrologyPoint> routeAnchors;
        final HashMap<Long, HydrologyPoint> surfaceRouteAnchors;
        final IdentityHashMap<HydrologySampledGrid, HashMap<SurfaceRouteKey, List<HydrologyPoint>>> surfaceRoutes;
        final HydrologyCaveCourseFilter.CandidateCache caveCandidates;
        final HydrologyCaveContainmentPlanner.ValidationCache caveValidations;

        PlanningSamples() {
            this.basis = new Long2ObjectOpenHashMap<>();
            this.basisWithoutSlope = new Long2ObjectOpenHashMap<>();
            this.detailed = new Long2ObjectOpenHashMap<>();
            this.classifications = new Long2ObjectOpenHashMap<>();
            this.geometry = new HashMap<>();
            this.minimumLandHeights = new HashMap<>();
            this.radialOffsets = new HashMap<>();
            this.undergroundSegmentCaps = new HashMap<>();
            this.routeAnchors = new HashMap<>();
            this.surfaceRouteAnchors = new HashMap<>();
            this.surfaceRoutes = new IdentityHashMap<>();
            this.caveCandidates = new HydrologyCaveCourseFilter.CandidateCache();
            this.caveValidations = new HydrologyCaveContainmentPlanner.ValidationCache();
        }
    }

    final class ColorRankedDraftAdmission implements CrossTileDraftAdmission, AutoCloseable {
        private final HydrologyTileKey ownerKey;
        private final int ownerRank;
        private final CrossTileResolutionContext context;
        private final Set<Long> rejectedCourseIds;
        private final Map<HydrologyTileKey, HydrologyForkJoin.Task<CrossTileResolvedOwner>> preparedOwners = new LinkedHashMap<>();
        private final Set<HydrologyTileKey> demandedOwners = new HashSet<>();
        private Throwable primaryFailure;
        private final LinkedHashMap<CrossTileRejectionKey, CrossTileRejectedCourse> observedRejections;

        private ColorRankedDraftAdmission(
                HydrologyTileKey ownerKey,
                int ownerRank,
                CrossTileResolutionContext context
        ) {
            this.ownerKey = Objects.requireNonNull(ownerKey, "ownerKey");
            this.ownerRank = ownerRank;
            this.context = Objects.requireNonNull(context, "context");
            this.rejectedCourseIds = new HashSet<>();
            this.observedRejections = new LinkedHashMap<>();
        }

        @Override
        public void prepare() {
            if (ownerRank == 0 || settings.crossTileColorPeriod() != 2 || !sourcePlanner.hasRoutedSourceSearch()
                    || !(Thread.currentThread() instanceof ForkJoinWorkerThread worker)) {
                return;
            }
            int radius = settings.crossTileColorPeriod() - 1;
            ArrayList<HydrologyTileKey> candidates = new ArrayList<>();
            for (long tileZ = (long) ownerKey.tileZ() - radius; tileZ <= (long) ownerKey.tileZ() + radius; tileZ++) {
                for (long tileX = (long) ownerKey.tileX() - radius; tileX <= (long) ownerKey.tileX() + radius; tileX++) {
                    if (tileX < Integer.MIN_VALUE || tileX > Integer.MAX_VALUE
                            || tileZ < Integer.MIN_VALUE || tileZ > Integer.MAX_VALUE) {
                        continue;
                    }
                    HydrologyTileKey key = new HydrologyTileKey((int) tileX, (int) tileZ);
                    if (crossTile.ownerColorRank(key) >= ownerRank || preparedOwners.containsKey(key)
                            || resolvedOwners.getIfPresent(key) != null || resolvingOwners.containsKey(key)) {
                        continue;
                    }
                    candidates.add(key);
                }
            }
            candidates.sort(Comparator.comparingInt(crossTile::ownerColorRank).reversed()
                    .thenComparing(HydrologyTileKey::compareTo));
            for (HydrologyTileKey key : candidates) {
                if (resolvedOwners.getIfPresent(key) != null || resolvingOwners.containsKey(key)) {
                    continue;
                }
                HydrologyForkJoin.Task<CrossTileResolvedOwner> task = new HydrologyForkJoin.Task<>(() -> resolveIndependentOwner(key));
                preparedOwners.put(key, task);
                try {
                    worker.getPool().execute(task);
                } catch (RuntimeException | Error failure) {
                    preparedOwners.remove(key);
                    throw failure;
                }
                currentDraftProfile().earlyOwners++;
            }
        }

        @Override
        public void close() {
            Error failure = null;
            Map<HydrologyTileKey, RuntimeException> unusedFailures = new LinkedHashMap<>();
            for (Map.Entry<HydrologyTileKey, HydrologyForkJoin.Task<CrossTileResolvedOwner>> entry : preparedOwners.entrySet()) {
                try {
                    entry.getValue().await();
                } catch (RuntimeException unusedFailure) {
                    if (!demandedOwners.contains(entry.getKey())) {
                        unusedFailures.put(entry.getKey(), unusedFailure);
                    }
                } catch (Error failed) {
                    if (failed == primaryFailure) {
                        continue;
                    }
                    if (failure == null) {
                        failure = failed;
                    } else if (failure != failed) {
                        failure.addSuppressed(failed);
                    }
                }
            }
            for (Map.Entry<HydrologyTileKey, RuntimeException> entry : unusedFailures.entrySet()) {
                IrisLogging.reportError("Unused early hydrology owner " + entry.getKey().tileX()
                        + "," + entry.getKey().tileZ() + " failed to prepare.", entry.getValue());
            }
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public CrossTilePublicationAdmission admit(HydrologyCaveCourseFilter.Result result) {
            context.recordIteration();
            HydrologyCaveCourseFilter.Result current = HydrologyCaveCourseFilter.withoutCourses(
                    result,
                    rejectedCourseIds
            );
            demandedOwners.addAll(crossTile.conflictOwnerKeys(ownerKey, current));
            CrossTileBlockers blockers = crossTile.lowerRankBlockers(
                    ownerKey,
                    current,
                    ownerRank,
                    context,
                    preparedOwners
            );
            HydrologyCrossTileSurfaceAdmission.Result surfaceAdmission =
                    HydrologyCrossTileSurfaceAdmission.admit(
                            crossTile.surfaceClaims(current),
                            blockers.surfaceClaims()
                    );
            Map<Long, RiverCourse> currentCourses = crossTile.coursesById(current.courses());
            HashSet<Long> rejectedSurfaceOutlets = new HashSet<>();
            HashMap<Long, Long> surfaceWinners = new HashMap<>();
            for (HydrologyCrossTileSurfaceAdmission.Rejection rejection : surfaceAdmission.rejections()) {
                RiverCourse course = currentCourses.get(rejection.loser().courseId());
                if (course == null || course.outletId().isEmpty()) {
                    throw new IllegalStateException("Cross-tile surface admission rejected an unknown current course.");
                }
                long outletId = course.outletId().getAsLong();
                rejectedSurfaceOutlets.add(outletId);
                surfaceWinners.putIfAbsent(outletId, rejection.winnerCourseId());
            }
            HashSet<Long> newRejectedCourseIds = new HashSet<>();
            ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();
            for (RiverCourse course : current.courses()) {
                if (course.outletId().isEmpty()
                        || !rejectedSurfaceOutlets.contains(course.outletId().getAsLong())) {
                    continue;
                }
                long winnerCourseId = surfaceWinners.get(course.outletId().getAsLong());
                rejectCrossTileCourse(course, winnerCourseId, newRejectedCourseIds, diagnostics);
            }
            HydrologyCaveCourseFilter.Result surfaceAccepted = HydrologyCaveCourseFilter.withoutCourses(
                    current,
                    newRejectedCourseIds
            );
            HydrologyCrossTileCaveAdmission.Result caveAdmission = HydrologyCrossTileCaveAdmission.admit(
                    ownerRank,
                    crossTile.caveClaims(surfaceAccepted),
                    blockers.caveClaims()
            );
            if (newRejectedCourseIds.isEmpty() && caveAdmission.rejections().isEmpty()) {
                return new CrossTilePublicationAdmission(current, List.of(), false);
            }
            Map<Long, RiverCourse> surfaceAcceptedCourses = crossTile.coursesById(surfaceAccepted.courses());
            for (HydrologyCrossTileCaveAdmission.Rejection rejection : caveAdmission.rejections()) {
                HydrologyCrossTileCaveAdmission.Claim claim = rejection.loser();
                RiverCourse course = surfaceAcceptedCourses.get(claim.courseId());
                if (course == null) {
                    throw new IllegalStateException("Cross-tile cave admission rejected an unknown current course.");
                }
                rejectCrossTileCourse(course, rejection.winnerSourceId(), newRejectedCourseIds, diagnostics);
            }
            return new CrossTilePublicationAdmission(
                    HydrologyCaveCourseFilter.withoutCourses(current, newRejectedCourseIds),
                    diagnostics,
                    true
            );
        }

        private void rejectCrossTileCourse(
                RiverCourse course,
                long winnerCourseId,
                Set<Long> newRejectedCourseIds,
                List<HydrologyDiagnosticCandidate> diagnostics
        ) {
            if (!rejectedCourseIds.add(course.id())) {
                throw new IllegalStateException("Cross-tile admission repeated a rejected course.");
            }
            newRejectedCourseIds.add(course.id());
            CrossTileRejectionKey rejectionKey = new CrossTileRejectionKey(course.id(), winnerCourseId);
            if (observedRejections.containsKey(rejectionKey)) {
                return;
            }
            observedRejections.put(
                    rejectionKey,
                    new CrossTileRejectedCourse(course, winnerCourseId)
            );
            diagnostics.add(course.type() == RiverCourseType.SURFACE
                    ? crossTile.surfaceConflictDiagnostic(course, winnerCourseId)
                    : HydrologyCaveCourseFilter.overlapDiagnostic(course, winnerCourseId));
        }

        private List<CrossTileRejectedCourse> observedRejections() {
            return List.copyOf(observedRejections.values());
        }
    }

    DraftProfile currentDraftProfile() {
        DraftProfile profile = draftProfiles.get();
        return profile == null ? new DraftProfile() : profile;
    }
}
