package art.arcane.iris.engine.hydrology.runtime;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.hydrology.HydrologyColumnLayer;
import art.arcane.iris.engine.hydrology.HydrologyColumnSample;
import art.arcane.iris.engine.hydrology.HydrologyColumnSnapshot;
import art.arcane.iris.engine.hydrology.HydrologyDiagnosticCandidate;
import art.arcane.iris.engine.hydrology.HydrologyDiagnosticRenderSample;
import art.arcane.iris.engine.hydrology.HydrologyFeatureQuery;
import art.arcane.iris.engine.hydrology.HydrologyFeatureRef;
import art.arcane.iris.engine.hydrology.HydrologyFeatureType;
import art.arcane.iris.engine.hydrology.HydrologyGeometrySampler;
import art.arcane.iris.engine.hydrology.HydrologyPlanner;
import art.arcane.iris.engine.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.engine.hydrology.HydrologyRenderSample;
import art.arcane.iris.engine.hydrology.HydrologyTerrainSample;
import art.arcane.iris.engine.hydrology.HydrologyTile;
import art.arcane.iris.engine.hydrology.HydrologyTileCache;
import art.arcane.iris.engine.hydrology.HydrologyTileKey;
import art.arcane.iris.engine.hydrology.cave.HydrologyCavePlan;
import art.arcane.iris.engine.hydrology.policy.EffectiveRiverPolicy;
import art.arcane.iris.engine.hydrology.policy.RiverConfinement;
import art.arcane.iris.engine.hydrology.policy.RiverPolicyResolver;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDeepFluidConfig;
import art.arcane.iris.engine.object.IrisHydrology;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.engine.object.IrisRiverHydrology;
import art.arcane.iris.engine.object.IrisRiverProfile;
import art.arcane.iris.engine.object.IrisRiverRoutingMode;
import art.arcane.iris.engine.object.IrisStyledRange;
import art.arcane.iris.engine.object.IrisSurfaceRiverBankConfig;
import art.arcane.iris.engine.object.IrisSurfaceRiverChannelConfig;
import art.arcane.iris.engine.object.IrisSurfaceRiverConfig;
import art.arcane.iris.engine.object.IrisUndergroundRiverConfig;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.volmlib.util.math.RNG;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

public final class IrisHydrologyRuntime implements AutoCloseable {
    private static final int MAXIMUM_CACHE_TILES = 64;
    private static final int MAXIMUM_TERRAIN_SAMPLES = 65_536;
    private static final int MAXIMUM_RESOLVED_POLICIES = 1024;
    private static final int MAXIMUM_FEATURE_SEARCH_TILES = 1_089;
    private static final int BIOME_PATCH_SCALE = 32;
    private static final long SURFACE_WIDTH_SALT = 0x5355524657494454L;
    private static final long SURFACE_DEPTH_SALT = 0x5355524644455054L;
    private static final long UNDERGROUND_LEVEL_SALT = 0x554e4445524c564cL;
    private static final long UNDERGROUND_WIDTH_SALT = 0x554e444552574944L;
    private static final long UNDERGROUND_DEPTH_SALT = 0x554e444552444550L;
    private static final long UNDERGROUND_HEADROOM_SALT = 0x554e444552484541L;
    private static final long DEEP_HEIGHT_SALT = 0x4445455048454947L;

    private final IrisHydrologyRuntimeContext context;
    private final IrisRiverHydrology rivers;
    private final HydrologyPlannerSettings settings;
    private final HydrologyTileCache cache;
    private final IrisHydrologyRoutingTerrainSampler routingTerrainSampler;
    private final Set<String> profileKeys;
    private final String defaultProfileKey;
    private final Object terrainSampleLock;
    private final Cache<HydrologyTileKey, Boolean> unplannedQueries = Caffeine.newBuilder()
            .maximumSize(MAXIMUM_CACHE_TILES)
            .build();
    private final Cache<PolicyKey, ResolvedPolicy> resolvedPolicies = Caffeine.newBuilder()
            .maximumSize(MAXIMUM_RESOLVED_POLICIES)
            .build();
    private final LinkedHashMap<Long, HydrologyTerrainSample> terrainSamples;

    public IrisHydrologyRuntime(IrisHydrologyRuntimeContext context) {
        this.context = Objects.requireNonNull(context);
        IrisHydrology hydrology = Objects.requireNonNull(context.dimension().getHydrology());
        this.rivers = Objects.requireNonNull(hydrology.getRivers());
        this.profileKeys = profileKeys(rivers.getProfiles());
        this.defaultProfileKey = profileKeys.isEmpty() ? "default" : profileKeys.iterator().next();
        this.terrainSampleLock = new Object();
        this.terrainSamples = new LinkedHashMap<>(MAXIMUM_TERRAIN_SAMPLES, 0.75F, true);
        this.settings = IrisHydrologySettingsCompiler.compile(context.dimension(), hydrology, context::data);
        HydrologyGeometrySampler geometrySampler = geometrySampler(context, hydrology);
        IrisHydrologyRoutingTerrainSampler.Sources terrainSources = new IrisHydrologyRoutingTerrainSampler.Sources(
                this::createTerrainBasis,
                (int x, int z) -> context.naturalHeightProvider().sample(x, z),
                (int x, int z) -> context.naturalOceanClassifier().isOcean(x, z),
                settings.seaLevel()
        );
        this.routingTerrainSampler = new IrisHydrologyRoutingTerrainSampler(
                terrainSources,
                IrisHydrologyRoutingTerrainSampler.SamplingOptions.production(MAXIMUM_TERRAIN_SAMPLES)
        );
        HydrologyPlanner planner = new HydrologyPlanner(
                context.seed(),
                settings,
                this::sampleTerrain,
                routingTerrainSampler,
                geometrySampler,
                0,
                context.caveViewFactory()
        );
        this.cache = new HydrologyTileCache(
                planner,
                MAXIMUM_CACHE_TILES,
                IrisPlatforms.isBound() ? MultiBurst.hydrology : null,
                context.waitingForbidden()
        );
        IrisLogging.debug("Hydrology runtime: tileSize=%d publicationRadius=%d planningThreads=%d",
                settings.routing().tileSize(), settings.publicationRadius(), MultiBurst.hydrology.parallelism());
    }

    public HydrologyPlannerSettings settings() {
        return settings;
    }

    public HydrologyTile tile(HydrologyTileKey key) {
        return cache.get(key);
    }

    /**
     * Whether the column can be sampled without planning: true when its tiles are cached. Otherwise the
     * planning pool is asked for them and the caller should answer from natural terrain for now.
     */
    public boolean isPlanned(double x, double z) {
        int blockX = (int) StrictMath.floor(x);
        int blockZ = (int) StrictMath.floor(z);
        boolean planned = cache.isPlanned(blockX, blockZ);
        if (!planned) {
            int tileSize = settings.routing().tileSize();
            HydrologyTileKey key = new HydrologyTileKey(Math.floorDiv(blockX, tileSize), Math.floorDiv(blockZ, tileSize));
            if (unplannedQueries.asMap().putIfAbsent(key, Boolean.TRUE) == null) {
                IrisLogging.debug("Hydrology tile %d,%d queried before it was planned; natural terrain answers until the plan lands",
                        key.tileX(), key.tileZ());
            }
        }
        return planned;
    }

    public Optional<HydrologyColumnSample> sample(double x, double z) {
        int blockX = (int) StrictMath.floor(x);
        int blockZ = (int) StrictMath.floor(z);
        return cache.columnAt(blockX, blockZ);
    }

    public HydrologyColumnSnapshot sampleSnapshot(int x, int z) {
        return cache.columnSnapshot(x, z);
    }

    /** Plans a bounded window of tiles touching the block area ahead of time, nearest the centre first. */
    public void prefetchArea(int minimumBlockX, int minimumBlockZ, int maximumBlockX, int maximumBlockZ, int centreBlockX, int centreBlockZ) {
        cache.prefetchArea(minimumBlockX, minimumBlockZ, maximumBlockX, maximumBlockZ, centreBlockX, centreBlockZ);
    }

    public void preparePregeneration(int centerBlockX, int centerBlockZ) {
        cache.preparePregeneration(centerBlockX, centerBlockZ);
    }

    public void setNeighbourPrefetchEnabled(boolean enabled) {
        cache.setNeighbourPrefetchEnabled(enabled);
    }

    public void enableSharedCache(String runtimeIdentity, Path persistentRoot) {
        cache.enableSharedCache(new HydrologyTileCache.SharedCacheScope(
                runtimeIdentity,
                context.seed(),
                context.worldHeight(),
                context.dimension().getLoadKey(),
                settings.fingerprint()), persistentRoot);
    }

    public void prepareChunkColumns(int blockX, int blockZ) {
        cache.prepareChunkColumns(blockX, blockZ);
    }

    public HydrologyRenderSample renderSample(double x, double z) {
        int blockX = (int) StrictMath.floor(x);
        int blockZ = (int) StrictMath.floor(z);
        return cache.renderAt(blockX, blockZ);
    }

    public boolean hasAcceptedSurfaceBiomeInChunk(String biomeKey, int chunkX, int chunkZ) {
        if (biomeKey == null || biomeKey.isBlank()) {
            return false;
        }
        int minimumX = chunkX << 4;
        int minimumZ = chunkZ << 4;
        int maximumX = Math.addExact(minimumX, 16);
        int maximumZ = Math.addExact(minimumZ, 16);
        int tileSize = settings.routing().tileSize();
        int publicationRadius = settings.publicationRadius();
        int minimumTileX = tileCoordinate((long) minimumX - publicationRadius, tileSize);
        int maximumTileX = tileCoordinate((long) maximumX - 1L + publicationRadius, tileSize);
        int minimumTileZ = tileCoordinate((long) minimumZ - publicationRadius, tileSize);
        int maximumTileZ = tileCoordinate((long) maximumZ - 1L + publicationRadius, tileSize);
        for (int tileZ = minimumTileZ; tileZ <= maximumTileZ; tileZ++) {
            for (int tileX = minimumTileX; tileX <= maximumTileX; tileX++) {
                HydrologyTile tile = cache.get(new HydrologyTileKey(tileX, tileZ));
                for (HydrologyColumnSample column : tile.footprint().columnsIn(
                        minimumX,
                        minimumZ,
                        maximumX,
                        maximumZ
                )) {
                    HydrologyColumnLayer layer = column.primarySurfaceLayer().orElse(null);
                    if (layer != null && biomeKey.equals(layer.biomeKey())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public List<HydrologyCavePlan> cavePlansIn(
            int minimumX,
            int minimumZ,
            int maximumX,
            int maximumZ
    ) {
        if (maximumX <= minimumX || maximumZ <= minimumZ) {
            return List.of();
        }
        int tileSize = settings.routing().tileSize();
        int publicationRadius = settings.publicationRadius();
        int minimumTileX = tileCoordinate((long) minimumX - publicationRadius, tileSize);
        int maximumTileX = tileCoordinate((long) maximumX - 1L + publicationRadius, tileSize);
        int minimumTileZ = tileCoordinate((long) minimumZ - publicationRadius, tileSize);
        int maximumTileZ = tileCoordinate((long) maximumZ - 1L + publicationRadius, tileSize);
        LinkedHashMap<Long, HydrologyCavePlan> plans = new LinkedHashMap<>();
        for (int tileZ = minimumTileZ; tileZ <= maximumTileZ; tileZ++) {
            for (int tileX = minimumTileX; tileX <= maximumTileX; tileX++) {
                HydrologyTile tile = cache.get(new HydrologyTileKey(tileX, tileZ));
                for (HydrologyCavePlan plan : tile.cavePlans()) {
                    HydrologyCavePlan existing = plans.putIfAbsent(plan.source().sourceId(), plan);
                    if (existing != null && !existing.equals(plan)) {
                        throw new IllegalStateException("Hydrology cave plan id collision.");
                    }
                }
            }
        }
        return List.copyOf(plans.values());
    }

    public HydrologyRenderSample sampleRenderFootprint(
            double minimumX,
            double minimumZ,
            double maximumX,
            double maximumZ
    ) {
        int minX = (int) StrictMath.floor(Math.min(minimumX, maximumX));
        int minZ = (int) StrictMath.floor(Math.min(minimumZ, maximumZ));
        int maxX = (int) StrictMath.ceil(Math.max(minimumX, maximumX));
        int maxZ = (int) StrictMath.ceil(Math.max(minimumZ, maximumZ));
        if (maxX <= minX) {
            maxX = minX + 1;
        }
        if (maxZ <= minZ) {
            maxZ = minZ + 1;
        }
        LinkedHashMap<Long, HydrologyFeatureRef> features = new LinkedHashMap<>();
        int tileSize = settings.routing().tileSize();
        int publicationRadius = settings.publicationRadius();
        int minimumTileX = tileCoordinate((long) minX - publicationRadius, tileSize);
        int maximumTileX = tileCoordinate((long) maxX - 1L + publicationRadius, tileSize);
        int minimumTileZ = tileCoordinate((long) minZ - publicationRadius, tileSize);
        int maximumTileZ = tileCoordinate((long) maxZ - 1L + publicationRadius, tileSize);
        for (int tileZ = minimumTileZ; tileZ <= maximumTileZ; tileZ++) {
            for (int tileX = minimumTileX; tileX <= maximumTileX; tileX++) {
                HydrologyTile tile = cache.get(new HydrologyTileKey(tileX, tileZ));
                for (HydrologyColumnSample column : tile.footprint().columnsIn(minX, minZ, maxX, maxZ)) {
                    for (HydrologyFeatureRef feature : column.renderSample().features()) {
                        HydrologyFeatureRef existing = features.putIfAbsent(feature.id(), feature);
                        if (existing != null && !existing.equals(feature)) {
                            throw new IllegalStateException("Hydrology feature id collision in renderer footprint.");
                        }
                    }
                }
            }
        }
        return new HydrologyRenderSample(minX, minZ, List.copyOf(features.values()));
    }

    public HydrologyDiagnosticRenderSample sampleDiagnosticFootprint(
            double minimumX,
            double minimumZ,
            double maximumX,
            double maximumZ
    ) {
        int minX = (int) StrictMath.floor(Math.min(minimumX, maximumX));
        int minZ = (int) StrictMath.floor(Math.min(minimumZ, maximumZ));
        int maxX = (int) StrictMath.ceil(Math.max(minimumX, maximumX));
        int maxZ = (int) StrictMath.ceil(Math.max(minimumZ, maximumZ));
        if (maxX <= minX) {
            maxX = minX + 1;
        }
        if (maxZ <= minZ) {
            maxZ = minZ + 1;
        }
        int tileSize = settings.routing().tileSize();
        int publicationRadius = settings.publicationRadius();
        int minimumTileX = tileCoordinate((long) minX - publicationRadius, tileSize);
        int maximumTileX = tileCoordinate((long) maxX - 1L + publicationRadius, tileSize);
        int minimumTileZ = tileCoordinate((long) minZ - publicationRadius, tileSize);
        int maximumTileZ = tileCoordinate((long) maxZ - 1L + publicationRadius, tileSize);
        LinkedHashMap<Long, HydrologyDiagnosticCandidate> candidates = new LinkedHashMap<>();
        for (int tileZ = minimumTileZ; tileZ <= maximumTileZ; tileZ++) {
            for (int tileX = minimumTileX; tileX <= maximumTileX; tileX++) {
                HydrologyTile tile = cache.get(new HydrologyTileKey(tileX, tileZ));
                for (HydrologyDiagnosticCandidate candidate : tile.diagnosticCandidates()) {
                    if (candidate.point().x() < minX || candidate.point().x() >= maxX
                            || candidate.point().z() < minZ || candidate.point().z() >= maxZ) {
                        continue;
                    }
                    HydrologyDiagnosticCandidate existing = candidates.putIfAbsent(candidate.id(), candidate);
                    if (existing != null && !existing.equals(candidate)) {
                        throw new IllegalStateException("Hydrology diagnostic candidate id collision.");
                    }
                }
            }
        }
        return new HydrologyDiagnosticRenderSample(minX, minZ, List.copyOf(candidates.values()));
    }

    public Optional<HydrologyFeatureRef> nearestFeature(
            Set<HydrologyFeatureType> types,
            String profileKey,
            int x,
            int z,
            int maximumDistance
    ) {
        return nearestFeature(
                types,
                profileKey,
                x,
                z,
                maximumDistance,
                feature -> true,
                (int visited) -> { }
        );
    }

    /**
     * Walks tiles outward from the origin, planning each ring's tiles together on the hydrology pool, and
     * stops as soon as no unvisited ring can hold a nearer feature. {@code progress} receives the running
     * tile count after every ring.
     */
    public Optional<HydrologyFeatureRef> nearestFeature(
            Set<HydrologyFeatureType> types,
            String profileKey,
            int x,
            int z,
            int maximumDistance,
            IntConsumer progress
    ) {
        return nearestFeature(
                types,
                profileKey,
                x,
                z,
                maximumDistance,
                feature -> true,
                progress
        );
    }

    public Optional<HydrologyFeatureRef> nearestFeature(
            Set<HydrologyFeatureType> types,
            String profileKey,
            int x,
            int z,
            int maximumDistance,
            Predicate<HydrologyFeatureRef> eligibility,
            IntConsumer progress
    ) {
        Objects.requireNonNull(types);
        Predicate<HydrologyFeatureRef> requiredEligibility = Objects.requireNonNull(
                eligibility,
                "eligibility"
        );
        Objects.requireNonNull(progress);
        if (types.isEmpty() || maximumDistance < 0) {
            return Optional.empty();
        }
        int tileSize = settings.routing().tileSize();
        int publicationRadius = settings.publicationRadius();
        long tileCount = HydrologyFeatureSearchBounds.tileCount(
                x, z, maximumDistance, tileSize, publicationRadius);
        if (tileCount > MAXIMUM_FEATURE_SEARCH_TILES) {
            throw new IllegalArgumentException("Hydrology feature search exceeds the bounded tile limit.");
        }
        int originTileX = tileCoordinate(x, tileSize);
        int originTileZ = tileCoordinate(z, tileSize);
        int ringLimit = HydrologyFeatureSearch.ringLimit(maximumDistance, tileSize, publicationRadius);
        HydrologyFeatureRef nearest = null;
        long nearestDistanceSquared = Long.MAX_VALUE;
        int visited = 0;
        for (int ring = 0; ring <= ringLimit; ring++) {
            long lowerBound = HydrologyFeatureSearch.lowerBound(ring, tileSize, publicationRadius);
            if (nearest != null && lowerBound * lowerBound > nearestDistanceSquared) {
                break;
            }
            for (HydrologyTile tile : cache.tiles(HydrologyFeatureSearch.ring(originTileX, originTileZ, ring))) {
                visited++;
                HydrologyFeatureRef feature = tile.nearestFeature(
                        types,
                        profileKey,
                        x,
                        z,
                        maximumDistance,
                        requiredEligibility
                ).orElse(null);
                if (feature == null) {
                    continue;
                }
                long deltaX = (long) feature.x() - x;
                long deltaZ = (long) feature.z() - z;
                long distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
                if (nearest == null || distanceSquared < nearestDistanceSquared
                        || distanceSquared == nearestDistanceSquared && feature.id() < nearest.id()) {
                    nearest = feature;
                    nearestDistanceSquared = distanceSquared;
                }
            }
            progress.accept(visited);
        }
        return Optional.ofNullable(nearest);
    }

    public int maximumFeatureSearchDistance(int x, int z, int requestedDistance) {
        return HydrologyFeatureSearchBounds.maximumDistance(
                x,
                z,
                requestedDistance,
                settings.routing().tileSize(),
                settings.publicationRadius(),
                MAXIMUM_FEATURE_SEARCH_TILES
        );
    }

    public Set<String> profileKeys() {
        return profileKeys;
    }

    public List<String> featureQueryKeys() {
        ArrayList<String> profiledIds = new ArrayList<>(settings.deepFluids().size() + settings.surfacePools().size());
        for (HydrologyPlannerSettings.DeepFluid deepFluid : settings.deepFluids()) {
            profiledIds.add(deepFluid.id());
        }
        for (HydrologyPlannerSettings.SurfacePool pool : settings.surfacePools()) {
            profiledIds.add(pool.id());
        }
        return HydrologyFeatureQuery.suggestions(profiledIds);
    }

    @Override
    public void close() {
        cache.close();
        unplannedQueries.invalidateAll();
        resolvedPolicies.invalidateAll();
        routingTerrainSampler.close();
        synchronized (terrainSampleLock) {
            terrainSamples.clear();
        }
    }

    private HydrologyTerrainSample sampleTerrain(int x, int z) {
        long packed = ((long) x << 32) ^ (z & 0xffffffffL);
        synchronized (terrainSampleLock) {
            HydrologyTerrainSample cached = terrainSamples.get(packed);
            if (cached != null) {
                return cached;
            }
        }
        HydrologyTerrainSample sampled = createDetailedTerrainSample(x, z);
        synchronized (terrainSampleLock) {
            HydrologyTerrainSample existing = terrainSamples.get(packed);
            if (existing != null) {
                return existing;
            }
            terrainSamples.put(packed, sampled);
            if (terrainSamples.size() > MAXIMUM_TERRAIN_SAMPLES) {
                Iterator<Long> iterator = terrainSamples.keySet().iterator();
                iterator.next();
                iterator.remove();
            }
        }
        return sampled;
    }

    private HydrologyTerrainSample createDetailedTerrainSample(int x, int z) {
        return routingTerrainSampler.sampleBasis(x, z);
    }

    /**
     * The message for a column whose natural height is not a finite number. It carries both the
     * raw stream sample and the natural sample plus the terrain breakdown, so the log of a failed
     * tile says which generator, overlay or cache produced the value.
     */
    static String nonFiniteHeightMessage(
            int x,
            int z,
            double rawNaturalHeight,
            double sampledNaturalHeight,
            IrisHydrologyNaturalHeightDescriber describer
    ) {
        String breakdown;
        try {
            breakdown = describer.describe(x, z);
        } catch (RuntimeException failure) {
            breakdown = "breakdown unavailable: " + failure.getClass().getSimpleName() + ": " + failure.getMessage();
        }
        return "Hydrology natural height was not finite at " + x + "," + z
                + " (raw=" + rawNaturalHeight + ", sampled=" + sampledNaturalHeight + "; " + breakdown + ")";
    }

    private IrisHydrologyRoutingTerrainSampler.TerrainBasis createTerrainBasis(
            int x,
            int z,
            double rawNaturalHeight
    ) {
        IrisHydrologyNaturalSample naturalSample = Objects.requireNonNull(
                context.naturalSampleProvider().sample(x, z, rawNaturalHeight),
                "Hydrology natural sample provider returned null at " + x + "," + z
        );
        double sampledNaturalHeight = naturalSample.naturalHeight();
        double resolvedNaturalHeight = Double.isFinite(rawNaturalHeight)
                ? rawNaturalHeight
                : sampledNaturalHeight;
        if (!Double.isFinite(resolvedNaturalHeight)) {
            // A non-finite height is a transient fault in a shared sampling cache, not a fact about
            // the terrain: sample the column once more before giving the tile up.
            double resampledNaturalHeight = context.naturalHeightProvider().sample(x, z);
            if (Double.isFinite(resampledNaturalHeight)) {
                return createTerrainBasis(x, z, resampledNaturalHeight);
            }
            throw new IllegalStateException(nonFiniteHeightMessage(
                    x,
                    z,
                    rawNaturalHeight,
                    sampledNaturalHeight,
                    context.naturalHeightDescriber()
            ));
        }
        int naturalHeight = (int) StrictMath.round(resolvedNaturalHeight);
        boolean ocean = IrisHydrologyRoutingTerrainSampler.physicalOcean(
                naturalSample.ocean(), resolvedNaturalHeight, settings.seaLevel());
        IrisBiome biome = naturalSample.biome();
        IrisRegion region = naturalSample.region();
        ResolvedPolicy resolvedPolicy = resolvePolicy(region, biome);
        EffectiveRiverPolicy policy = resolvedPolicy.policy();
        String parentBiomeKey = requireBiomeKey(biome);
        List<String> profiles = resolvedPolicy.profiles();
        int configuredFluidY = settings.underground().minimumFluidY()
                + (settings.underground().maximumFluidY() - settings.underground().minimumFluidY()) / 2;
        if (!rivers.getUnderground().getFluidLevel().isFlat()) {
            int lowestFluidY = Math.max(
                    settings.underground().minimumFluidY(),
                    settings.underground().maximumDepth() + settings.underground().minimumFloorCover()
            );
            int highestFluidY = Math.min(
                    settings.underground().maximumFluidY(),
                    naturalHeight - settings.underground().minimumHeadroom()
                            - settings.underground().minimumRockCover()
            );
            if (lowestFluidY <= highestFluidY) {
                configuredFluidY = lowestFluidY + (highestFluidY - lowestFluidY) / 2;
            }
        }
        int caveFloorY = configuredFluidY - settings.underground().maximumDepth();
        boolean caveAvailable = caveFloorY > 0
                && configuredFluidY + settings.underground().maximumHeadroom()
                        + settings.underground().minimumRockCover() <= naturalHeight;
        boolean routingAllowed = policy.allowsTransit() && policy.allowsRouting() && !ocean;
        boolean sourceAllowed = routingAllowed && policy.allowsSources();
        double sourceWeight = switch (policy.placement()) {
            case DISABLED, TRANSIT_ONLY -> 0D;
            case NATURAL -> 1D;
            case PREFERRED_HEADWATER -> 4D;
            case REQUIRED_HEADWATER -> 8D;
        };
        double routingCost = policy.routing() == IrisRiverRoutingMode.AVOID ? 1024D : 0D;
        double routingPreference = policy.routing() == IrisRiverRoutingMode.PREFER ? 0.5D : 1D;
        double biomePatchNoise = coherentPatchNoise(context.seed(), x, z);
        HydrologyTerrainSample terrain = new HydrologyTerrainSample(
                naturalHeight,
                0D,
                ocean,
                caveAvailable,
                caveFloorY,
                configuredFluidY,
                routingAllowed,
                routingAllowed && policy.outletAdmission(),
                sourceAllowed,
                sourceAllowed && policy.requiresHeadwaters(),
                sourceAllowed && caveAvailable,
                sourceAllowed && caveAvailable && policy.requiresHeadwaters(),
                routingCost,
                sourceWeight,
                sourceWeight,
                policy.widthMultiplier(),
                policy.depthMultiplier(),
                policy.incisionMultiplier(),
                policy.routingMultiplier() * routingPreference,
                policy.bankMultiplier(),
                parentBiomeKey,
                selectKey(policy.surfaceBiomes(), parentBiomeKey, biomePatchNoise, 1),
                selectKey(policy.mouthBiomes(), parentBiomeKey, biomePatchNoise, 2),
                selectKey(policy.shoreBiomes(), parentBiomeKey, biomePatchNoise, 3),
                selectKey(policy.bankBiomes(), parentBiomeKey, biomePatchNoise, 4),
                selectKey(policy.floodedCaveBiomes(), parentBiomeKey, biomePatchNoise, 5),
                profiles,
                policy.surfacePools(),
                policy.shoreBiomeWidth() == null ? Double.NaN : policy.shoreBiomeWidth(),
                confinesKey(policy.confinement(), region, biome),
                policy.shoreWidth() == null ? Double.NaN : policy.shoreWidth(),
                policy.erosion() == null || policy.erosion(),
                policy.surfacePolicy()
        );
        return new IrisHydrologyRoutingTerrainSampler.TerrainBasis(resolvedNaturalHeight, terrain);
    }

    /** The area a river at this column must stay inside, named so that neighbouring areas never match. */
    static String confinesKey(RiverConfinement confinement, IrisRegion region, IrisBiome biome) {
        return switch (confinement) {
            case NONE -> null;
            case REGION -> region == null ? null : "region:" + region.getLoadKey();
            case BIOME -> biome == null ? null : "biome:" + biome.getLoadKey();
        };
    }

    private ResolvedPolicy resolvePolicy(IrisRegion region, IrisBiome biome) {
        IrisData data = policyData(region, biome);
        ResourceLoader<IrisBiome> loader = data == null ? null : data.getBiomeLoader();
        PolicyKey key = new PolicyKey(region, biome, data, loader);
        ResolvedPolicy cached = resolvedPolicies.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        RiverPolicyResolver.Resolution resolution = RiverPolicyResolver.resolveWithStatus(context.dimension(), region, biome);
        EffectiveRiverPolicy policy = resolution.policy();
        ResolvedPolicy resolved = new ResolvedPolicy(policy, validProfiles(policy.profiles()));
        if (resolution.complete() && data == policyData(region, biome)
                && loader == (data == null ? null : data.getBiomeLoader())) {
            resolvedPolicies.put(key, resolved);
        }
        return resolved;
    }

    private IrisData policyData(IrisRegion region, IrisBiome biome) {
        IrisData data = biome == null ? null : biome.getLoader();
        if (data == null && region != null) {
            data = region.getLoader();
        }
        if (data == null) {
            data = context.dimension().getLoader();
        }
        return data;
    }

    private List<String> validProfiles(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of(defaultProfileKey);
        }
        ArrayList<String> valid = new ArrayList<>();
        for (String key : requested) {
            if (key != null && profileKeys.contains(key) && !valid.contains(key)) {
                valid.add(key);
            }
        }
        return valid.isEmpty() ? List.of(defaultProfileKey) : List.copyOf(valid);
    }

    private String selectKey(List<String> keys, String fallback, double patchNoise, int salt) {
        if (keys == null || keys.isEmpty()) {
            return fallback;
        }
        return keys.get(keyIndex(patchNoise, salt, keys.size()));
    }

    static int coherentKeyIndex(long seed, int x, int z, int salt, int keyCount) {
        if (keyCount <= 0) {
            throw new IllegalArgumentException("keyCount must be positive");
        }
        if (keyCount == 1) {
            return 0;
        }
        return keyIndex(coherentPatchNoise(seed, x, z), salt, keyCount);
    }

    private static int keyIndex(double patchNoise, int salt, int keyCount) {
        double shifted = patchNoise + salt * 0.3819660112501051D;
        shifted -= StrictMath.floor(shifted);
        return Math.min(keyCount - 1, (int) StrictMath.floor(shifted * keyCount));
    }

    private static double coherentPatchNoise(long seed, int x, int z) {
        int cellX = Math.floorDiv(x, BIOME_PATCH_SCALE);
        int cellZ = Math.floorDiv(z, BIOME_PATCH_SCALE);
        double localX = Math.floorMod(x, BIOME_PATCH_SCALE) / (double) BIOME_PATCH_SCALE;
        double localZ = Math.floorMod(z, BIOME_PATCH_SCALE) / (double) BIOME_PATCH_SCALE;
        double smoothX = localX * localX * (3D - 2D * localX);
        double smoothZ = localZ * localZ * (3D - 2D * localZ);
        double top = interpolate(
                keyNoise(seed, cellX, cellZ),
                keyNoise(seed, cellX + 1, cellZ),
                smoothX
        );
        double bottom = interpolate(
                keyNoise(seed, cellX, cellZ + 1),
                keyNoise(seed, cellX + 1, cellZ + 1),
                smoothX
        );
        return interpolate(top, bottom, smoothZ);
    }

    private static double keyNoise(long seed, int cellX, int cellZ) {
        long mixed = avalanche(seed ^ 0x9e3779b97f4a7c15L);
        mixed = avalanche(mixed ^ avalanche(cellX + 0x9e3779b97f4a7c15L));
        mixed = avalanche(mixed ^ avalanche(cellZ + 0x9e3779b97f4a7c15L));
        return (avalanche(mixed) >>> 11) * 0x1.0p-53;
    }

    private static long avalanche(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }

    private static double interpolate(double first, double second, double progress) {
        return first + (second - first) * progress;
    }

    private static HydrologyGeometrySampler geometrySampler(
            IrisHydrologyRuntimeContext context,
            IrisHydrology hydrology
    ) {
        IrisRiverHydrology rivers = hydrology.getRivers();
        IrisSurfaceRiverConfig surface = rivers.getSurface();
        IrisSurfaceRiverChannelConfig channel = surface.getChannel();
        IrisSurfaceRiverBankConfig banks = surface.getBanks();
        IrisUndergroundRiverConfig underground = rivers.getUnderground();
        ProceduralStream<Double> surfaceWidth = styledStream(context, channel.getWidth(), SURFACE_WIDTH_SALT);
        ProceduralStream<Double> surfaceDepth = styledStream(context, channel.getDepth(), SURFACE_DEPTH_SALT);
        ProceduralStream<Double> surfaceBlend = ProceduralStream.ofDouble((x, z) -> (double) banks.getMaximumBlendWidth());
        ProceduralStream<Double> undergroundLevel = styledStream(
                context,
                underground.getFluidLevel(),
                UNDERGROUND_LEVEL_SALT
        );
        ProceduralStream<Double> undergroundWidth = styledStream(
                context,
                underground.getChannelWidth(),
                UNDERGROUND_WIDTH_SALT
        );
        ProceduralStream<Double> undergroundDepth = styledStream(
                context,
                underground.getDepth(),
                UNDERGROUND_DEPTH_SALT
        );
        ProceduralStream<Double> undergroundHeadroom = styledStream(
                context,
                underground.getHeadroom(),
                UNDERGROUND_HEADROOM_SALT
        );
        LinkedHashMap<String, ProceduralStream<Double>> deepHeights = new LinkedHashMap<>();
        for (IrisDeepFluidConfig deepFluid : hydrology.getDeepFluids()) {
            long profileSalt = DEEP_HEIGHT_SALT ^ deepFluid.getId().hashCode();
            deepHeights.put(deepFluid.getId(), styledStream(context, deepFluid.getHeight(), profileSalt));
        }
        int minimumWorldY = context.dimension().getMinHeight();
        return request -> {
            ProceduralStream<Double> stream = switch (request.field()) {
                case SURFACE_WIDTH -> surfaceWidth;
                case SURFACE_DEPTH -> surfaceDepth;
                case SURFACE_BLEND_WIDTH -> surfaceBlend;
                case UNDERGROUND_FLUID_LEVEL -> undergroundLevel;
                case UNDERGROUND_WIDTH -> undergroundWidth;
                case UNDERGROUND_DEPTH -> undergroundDepth;
                case UNDERGROUND_HEADROOM -> undergroundHeadroom;
                case DEEP_FLUID_HEIGHT -> Objects.requireNonNull(
                        deepHeights.get(request.profileKey()),
                        "Missing deep-fluid height style for " + request.profileKey() + "."
                );
            };
            double sampled = stream.get(request.x(), request.z());
            if (!Double.isFinite(sampled)) {
                throw new IllegalStateException(
                        "Hydrology geometry style returned a non-finite value for " + request.field() + "."
                );
            }
            long rounded = StrictMath.round(sampled);
            if (request.field() == HydrologyGeometrySampler.Field.UNDERGROUND_FLUID_LEVEL
                    || request.field() == HydrologyGeometrySampler.Field.DEEP_FLUID_HEIGHT) {
                rounded = Math.subtractExact(rounded, minimumWorldY);
            }
            int value = Math.toIntExact(rounded);
            return Math.max(request.minimum(), Math.min(request.maximum(), value));
        };
    }

    private static ProceduralStream<Double> styledStream(
            IrisHydrologyRuntimeContext context,
            IrisStyledRange range,
            long salt
    ) {
        return range.stream(new RNG(context.seed() ^ salt), context.data());
    }

    private static int tileCoordinate(long blockCoordinate, int tileSize) {
        return Math.toIntExact(Math.floorDiv(blockCoordinate, tileSize));
    }

    private static Set<String> profileKeys(List<IrisRiverProfile> profiles) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (IrisRiverProfile profile : profiles) {
            if (profile != null && profile.getId() != null && !profile.getId().isBlank()) {
                keys.add(profile.getId().trim());
            }
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(keys));
    }

    private static String requireBiomeKey(IrisBiome biome) {
        if (biome == null || biome.getLoadKey() == null || biome.getLoadKey().isBlank()) {
            throw new IllegalStateException("Hydrology terrain sampling requires a loaded parent biome.");
        }
        return biome.getLoadKey();
    }

    private record ResolvedPolicy(EffectiveRiverPolicy policy, List<String> profiles) {
    }

    private record PolicyKey(IrisRegion region, IrisBiome biome, IrisData data, ResourceLoader<IrisBiome> loader) {
        @Override
        public boolean equals(Object compared) {
            return compared instanceof PolicyKey other
                    && region == other.region && biome == other.biome && data == other.data && loader == other.loader;
        }

        @Override
        public int hashCode() {
            return ((31 * System.identityHashCode(region) + System.identityHashCode(biome)) * 31
                    + System.identityHashCode(data)) * 31 + System.identityHashCode(loader);
        }
    }

}
