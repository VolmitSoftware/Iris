package art.arcane.iris.platform.bukkit.nms;

import art.arcane.volmlib.nativelib.terrain.NativeBiomeRegistry;
import art.arcane.volmlib.nativelib.terrain.NativeBiomeSourcePolicy;
import java.util.function.IntUnaryOperator;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.generation.runtime.DimensionStackContext;
import art.arcane.iris.generation.runtime.DimensionStackLayout;
import art.arcane.iris.generation.runtime.DimensionTerrainContext;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationSessionException;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingResolver;
import art.arcane.iris.platform.generation.BukkitChunkGenerator;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.math.RNG;
import org.bukkit.block.Biome;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.Objects;

public final class BukkitBiomePolicy<H, V> implements NativeBiomeSourcePolicy<H> {
    private static final int NOISE_BIOME_CACHE_MAX = 262144;
    private static final int STRONGHOLD_RING_SEARCH_Y = 0;
    private static final int STRONGHOLD_RING_SEARCH_RADIUS = 112;
    private static final int STRONGHOLD_RING_SEARCH_QUART_STEP = 4;

    private final long seed;
    private final Engine engine;
    private final BukkitChunkGenerator platformGenerator;
    private final NativeBiomeRegistry<H, V> biomeCustomRegistry;
    private final NativeBiomeRegistry<H, V> biomeRegistry;
    private final Supplier<NativeBiomeRegistry<H, V>> possibleBiomeRegistry;
    private final H fallbackBiome;
    private final ConcurrentHashMap<RuntimeNoiseKey, H> noiseBiomeCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RuntimeNoiseKey, H> structureBiomeCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RuntimeColumnKey, H> surfaceStructureBiomeCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RuntimeColumnKey, H> naturalSurfaceStructureBiomeCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, RuntimeBiomeState<H, V>> runtimeBiomeStates = new ConcurrentHashMap<>();

    public BukkitBiomePolicy(RuntimeOptions<H, V> options, NativeBiomeRegistry<H, V> registry) {
        Engine engine = options.engine();
        this.engine = engine;
        this.platformGenerator = options.platformGenerator();
        this.possibleBiomeRegistry = options.possibleBiomeRegistry();
        this.seed = options.seed();
        this.biomeCustomRegistry = registry;
        this.biomeRegistry = biomeCustomRegistry;
        this.fallbackBiome = resolveFallbackBiome(this.biomeRegistry, this.biomeCustomRegistry);
        if (engine instanceof IrisEngine irisEngine) {
            irisEngine.addGenerationRuntimeRetirementListener(this::evictRuntimeCaches);
        }
        runtimeBiomeState();
    }

    private static <H, V> List<H> getAllBiomes(
            NativeBiomeRegistry<H, V> customRegistry,
            NativeBiomeRegistry<H, V> registry,
            Engine engine,
            boolean includeDimensionStack
    ) {
        LinkedHashSet<H> biomes = new LinkedHashSet<>();

        for (OwnedBiome ownedBiome : getHostOwnedBiomes(engine)) {
            IrisBiome i = ownedBiome.biome();
            H vanillaHolder = resolveBiomeHolder(registry, i.getStructureDerivativeKey());
            if (vanillaHolder == null) {
                throw new IllegalStateException("Iris structure biome derivative '"
                        + i.getStructureDerivativeKey() + "' is not registered for biome '" + i.getLoadKey() + "'");
            }
            biomes.add(vanillaHolder);
        }

        if (includeDimensionStack) {
            biomes.addAll(getVisibleBiomes(
                    customRegistry,
                    registry,
                    engine,
                    resolveFallbackBiome(registry, customRegistry)
            ));
        }

        if (biomes.isEmpty()) {
            throw new IllegalStateException("Iris pack '" + engine.getName()
                    + "' has no registered structure biomes");
        }
        return new ArrayList<>(biomes);
    }

    private static <H, V> List<H> getVisibleBiomes(
            NativeBiomeRegistry<H, V> customRegistry,
            NativeBiomeRegistry<H, V> registry,
            Engine engine,
            H fallback
    ) {
        LinkedHashSet<H> biomes = new LinkedHashSet<>();
        boolean fallbackPossible = false;
        for (OwnedBiome ownedBiome : getOwnedBiomes(engine)) {
            IrisBiome irisBiome = ownedBiome.biome();
            if (irisBiome.isCustom()) {
                for (IrisBiomeCustom customBiome : irisBiome.getCustomDerivitives()) {
                    H customHolder = resolveCustomBiomeHolder(
                            customRegistry, engine, ownedBiome.dimension(), customBiome.getId());
                    if (customHolder == null) {
                        throw new IllegalStateException("Iris custom visible biome '"
                                + customBiomeKey(ownedBiome.dimension(), customBiome.getId())
                                + "' is not registered");
                    }
                    biomes.add(customHolder);
                }
                continue;
            }
            fallbackPossible |= !addVisibleBiomeHolder(
                    biomes, registry, irisBiome.getDerivativeKey());
            for (String scatter : irisBiome.getBiomeScatter()) {
                fallbackPossible |= !addVisibleBiomeHolder(biomes, registry, scatter);
            }
            for (String scatter : irisBiome.getBiomeSkyScatter()) {
                fallbackPossible |= !addVisibleBiomeHolder(biomes, registry, scatter);
            }
        }
        if (fallbackPossible && fallback != null) {
            biomes.add(fallback);
        }
        return new ArrayList<>(biomes);
    }

    private static <H, V> boolean addVisibleBiomeHolder(
            Set<H> biomes,
            NativeBiomeRegistry<H, V> registry,
            String biomeKey
    ) {
        H holder = resolveBiomeHolder(registry, biomeKey);
        if (holder == null) {
            return false;
        }
        biomes.add(holder);
        return true;
    }

    @SuppressWarnings("unchecked")

    @Override
    public Set<H> possibleBiomes() {
        return possibleBiomes(true);
    }

    public Set<H> possibleStructureBiomes() {
        return possibleBiomes(false);
    }

    private Set<H> possibleBiomes(boolean includeDimensionStack) {
        GenerationSessionLease lease = tryAcquireGenerationLease("bukkit_possible_biomes");
        if (lease == null) {
            throw new IllegalStateException("Iris possible biome lookup was rejected during an engine transition");
        }
        try (lease; IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            if (!isRuntimeAvailable()) {
                throw new IllegalStateException("Iris possible biome lookup has no active engine runtime");
            }
            runtimeBiomeState();
            NativeBiomeRegistry<H, V> customRegistry = biomeCustomRegistry;
            NativeBiomeRegistry<H, V> worldRegistry = Objects.requireNonNull(possibleBiomeRegistry.get(), "possible biome registry");
            return Set.copyOf(getAllBiomes(
                    customRegistry, worldRegistry, engine, includeDimensionStack));
        }
    }

    private KMap<String, H> fillCustomBiomes(NativeBiomeRegistry<H, V> customRegistry, Engine engine, H fallback) {
        KMap<String, H> m = new KMap<>();
        if (customRegistry == null) {
            return m;
        }

        for (OwnedBiome ownedBiome : getOwnedBiomes(engine)) {
            IrisBiome i = ownedBiome.biome();
            if (i.isCustom()) {
                for (IrisBiomeCustom j : i.getCustomDerivitives()) {
                    String key = customBiomeKey(ownedBiome.dimension(), j.getId());
                    H holder = resolveCustomBiomeHolder(
                            customRegistry, engine, ownedBiome.dimension(), j.getId());
                    if (holder == null) {
                        if (fallback != null) {
                            m.put(key, fallback);
                        }
                        IrisLogging.error("Cannot find biome for IrisBiomeCustom " + key
                                + " from engine " + engine.getName());
                        continue;
                    }
                    m.put(key, holder);
                }
            }
        }

        return m;
    }

    private Map<V, H> fillVanillaSpawnBiomes(NativeBiomeRegistry<H, V> customRegistry, NativeBiomeRegistry<H, V> registry, Engine engine) {
        IdentityHashMap<V, H> spawnBiomes = new IdentityHashMap<>();
        if (customRegistry == null || registry == null) {
            return Collections.unmodifiableMap(spawnBiomes);
        }

        for (OwnedBiome ownedBiome : getOwnedBiomes(engine)) {
            IrisBiome irisBiome = ownedBiome.biome();
            if (!irisBiome.isCustom()) {
                continue;
            }
            H vanillaHolder = registry.lookup(irisBiome.getVanillaDerivative());
            if (vanillaHolder == null) {
                continue;
            }
            for (IrisBiomeCustom customBiome : irisBiome.getCustomDerivitives()) {
                H customHolder = resolveCustomBiomeHolder(
                        customRegistry, engine, ownedBiome.dimension(), customBiome.getId());
                if (customHolder != null) {
                    spawnBiomes.putIfAbsent(customRegistry.value(customHolder), vanillaHolder);
                }
            }
        }

        return Collections.unmodifiableMap(spawnBiomes);
    }

    public H getRetainedVanillaSpawnBiome(String derivativeKey) {
        return resolveBiomeHolder(biomeRegistry, derivativeKey);
    }

    public H getVanillaSpawnBiome(H biome) {
        if (biome == null) {
            return null;
        }
        GenerationSessionLease lease = tryAcquireGenerationLease("bukkit_spawn_biome");
        if (lease == null) {
            return null;
        }
        try (lease; IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            if (!isRuntimeAvailable()) {
                return null;
            }
            return runtimeBiomeState().vanillaSpawnBiomes().get(biomeRegistry.value(biome));
        }
    }

    public H getVisibleSurfaceBiome(int blockX, int blockZ) {
        GenerationSessionLease lease = tryAcquireGenerationLease("bukkit_surface_spawn_biome");
        if (lease == null) {
            return null;
        }
        try (lease; IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            if (!isRuntimeAvailable()) {
                return null;
            }
            if (platformGenerator != null && platformGenerator.usesFlatStudioTerrain()) {
                return fallbackBiome;
            }
            runtimeBiomeState();
            int quartX = ((blockX) >> 2);
            int quartZ = ((blockZ) >> 2);
            int sampleX = ((quartX) << 2);
            int sampleZ = ((quartZ) << 2);
            boolean cacheable = isBiomeCacheable(engine, sampleX, sampleZ);
            DimensionStackContext stackContext = engine.getDimensionStackContext();
            DimensionStackLayout.Layer layer = stackContext == null
                    ? null
                    : stackContext.getLayout(sampleX, sampleZ).surfaceLayer();
            int internalY = layer == null
                    ? Engine.hostHeight(engine, sampleX, sampleZ, true)
                    : layer.clippedSurfaceY();
            int quartY = ((internalY + engine.getWorld().minHeight()) >> 2);
            return getVisibleNoiseBiomeWithActiveGenerationLease(
                    quartX, quartY, quartZ, null, cacheable);
        }
    }

    @Override
    public H getNoiseBiome(int x, int y, int z) {
        int blockX = x << 2;
        int blockZ = z << 2;
        try (GenerationHistoryRuntimeRouter.CoordinateScope historyScope =
                     openHistoryCoordinateScope(blockX, blockZ, "bukkit_structure_biome");
             GenerationSessionLease lease = requireGenerationLease(
                     "bukkit_structure_biome",
                     "Iris structure biome lookup was rejected during an engine transition");
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            if (!isRuntimeAvailable()) {
                throw new IllegalStateException("Iris structure biome lookup has no active engine runtime");
            }
            return getStructureNoiseBiomeWithActiveGenerationLease(x, y, z);
        }
    }

    private H getStructureNoiseBiomeWithActiveGenerationLease(int x, int y, int z) {
        if (platformGenerator != null && platformGenerator.usesFlatStudioTerrain()) {
            return fallbackBiome;
        }
        if (isGuaranteedSurfaceBiome(y)) {
            return getSurfaceStructureBiomeHolder(x, z);
        }

        boolean cacheable = isBiomeCacheable(x, z);
        RuntimeNoiseKey cacheKey = new RuntimeNoiseKey(
                engine.getCacheID(), packNoiseKey(x, y, z));
        if (cacheable) {
            H cachedHolder = structureBiomeCache.get(cacheKey);
            if (cachedHolder != null) {
                return cachedHolder;
            }
        }

        H resolvedHolder = resolveStructureBiomeHolder(x, y, z);
        if (!cacheable) {
            return resolvedHolder;
        }
        H existingHolder = structureBiomeCache.putIfAbsent(cacheKey, resolvedHolder);
        if (existingHolder != null) {
            return existingHolder;
        }

        if (structureBiomeCache.size() > NOISE_BIOME_CACHE_MAX) {
            structureBiomeCache.clear();
        }

        return resolvedHolder;
    }

    @Override
    public BiomeLocation<H> findBiomeHorizontal(HorizontalQuery<H> query) {
        int x = query.x();
        int y = query.y();
        int z = query.z();
        int searchRadius = query.radius();
        Predicate<H> allowed = query.allowed();
        IntUnaryOperator random = query.random();
        int quartStep = horizontalBiomeSearchQuartStep(y, searchRadius);
        if (quartStep == 1) {
            return query.fallback().get();
        }
        GenerationSessionLease lease = tryAcquireGenerationLease("bukkit_structure_ring_biome");
        if (lease == null) {
            throw new IllegalStateException("Iris structure ring biome lookup was rejected during an engine transition");
        }
        try (lease; IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            if (!isRuntimeAvailable()) {
                throw new IllegalStateException("Iris structure ring biome lookup has no active engine runtime");
            }
            return findNaturalSurfaceBiomeHorizontal(
                    x, y, z, searchRadius, quartStep, allowed, random);
        }
    }

    @Override
    public BiomeLocation<H> findClosestBiome3d(ClosestQuery<H> query) {
        GenerationSessionLease lease = tryAcquireGenerationLease("bukkit_locate_visible_biome");
        if (lease == null) {
            throw new IllegalStateException("Iris visible biome search was rejected during an engine transition");
        }
        try (lease; IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            prepareVisibleBiomeBatch();
            DimensionStackContext stackContext = engine.getDimensionStackContext();
            if (stackContext == null) {
                return query.fallback().get();
            }
            Set<H> candidates = new HashSet<>();
            for (H biome : getVisibleBiomes(
                    biomeCustomRegistry, biomeRegistry, engine, fallbackBiome)) {
                if (query.allowed().test(biome)) {
                    candidates.add(biome);
                }
            }
            if (candidates.isEmpty()) {
                return null;
            }
            return query.search().search(candidates, (blockX, blockZ) -> visibleColumn(stackContext, blockX, blockZ));
        }
    }

    private Column<H> visibleColumn(DimensionStackContext context, int blockX, int blockZ) {
        int quartX = blockX >> 2;
        int quartZ = blockZ >> 2;
        DimensionStackLayout stackLayout = context.sample(quartX << 2, quartZ << 2);
        return blockY -> resolveVisibleBiomeHolder(quartX, blockY >> 2, quartZ, null, stackLayout);
    }

    public static int horizontalBiomeSearchQuartStep(int blockY, int searchRadius) {
        return blockY == STRONGHOLD_RING_SEARCH_Y && searchRadius == STRONGHOLD_RING_SEARCH_RADIUS
                ? STRONGHOLD_RING_SEARCH_QUART_STEP
                : 1;
    }

    @Override
    public Set<H> getBiomesWithin(NearbyQuery<H> query) {
        int x = query.x();
        int y = query.y();
        int z = query.z();
        int radius = query.radius();
        GenerationSessionLease lease = tryAcquireGenerationLease("bukkit_biomes_within");
        if (lease == null) {
            throw new IllegalStateException("Iris biome radius lookup was rejected during an engine transition");
        }
        try (lease; IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            if (!isRuntimeAvailable()) {
                throw new IllegalStateException("Iris biome radius lookup has no active engine runtime");
            }
            int minQuartY = ((y - radius) >> 2);
            boolean monumentQuery = radius == 29
                    && y == engine.getMinHeight() + engine.getDimension().getFluidHeight();
            if (!monumentQuery && !isGuaranteedSurfaceBiome(minQuartY)) {
                return query.fallback().get();
            }
            int minQuartX = ((x - radius) >> 2);
            int maxQuartX = ((x + radius) >> 2);
            int minQuartZ = ((z - radius) >> 2);
            int maxQuartZ = ((z + radius) >> 2);
            int columns = (maxQuartX - minQuartX + 1) * (maxQuartZ - minQuartZ + 1);
            Set<H> biomes = new HashSet<>(columns);
            for (int quartZ = minQuartZ; quartZ <= maxQuartZ; quartZ++) {
                for (int quartX = minQuartX; quartX <= maxQuartX; quartX++) {
                    biomes.add(getSurfaceStructureBiomeHolder(quartX, quartZ));
                }
            }
            return biomes;
        }
    }

    private H getSurfaceStructureBiomeHolder(int x, int z) {
        int blockX = x << 2;
        int blockZ = z << 2;
        try (GenerationHistoryRuntimeRouter.CoordinateScope historyScope =
                     openHistoryCoordinateScope(blockX, blockZ, "bukkit_surface_structure_biome")) {
            RuntimeColumnKey columnKey = new RuntimeColumnKey(
                    engine.getCacheID(), packColumnKey(x, z));
            H surfaceHolder = surfaceStructureBiomeCache.get(columnKey);
            if (surfaceHolder != null) {
                return surfaceHolder;
            }
            H resolvedSurfaceHolder = resolveSurfaceStructureBiomeHolder(x, z);
            H existingSurfaceHolder = surfaceStructureBiomeCache.putIfAbsent(
                    columnKey, resolvedSurfaceHolder);
            if (existingSurfaceHolder != null) {
                return existingSurfaceHolder;
            }
            if (surfaceStructureBiomeCache.size() > NOISE_BIOME_CACHE_MAX) {
                surfaceStructureBiomeCache.clear();
            }
            return resolvedSurfaceHolder;
        }
    }

    private BiomeLocation<H> findNaturalSurfaceBiomeHorizontal(
            int x,
            int y,
            int z,
            int searchRadius,
            int quartStep,
            Predicate<H> allowed,
            IntUnaryOperator random
    ) {
        int centerQuartX = ((x) >> 2);
        int centerQuartZ = ((z) >> 2);
        int quartRadius = ((searchRadius) >> 2);
        BiomeLocation<H> selected = null;
        int matches = 0;
        for (int radius = 0; radius <= quartRadius; radius += quartStep) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ += quartStep) {
                for (int offsetX = -radius; offsetX <= radius; offsetX += quartStep) {
                    int quartX = centerQuartX + offsetX;
                    int quartZ = centerQuartZ + offsetZ;
                    H holder = getNaturalSurfaceStructureBiomeHolder(quartX, quartZ);
                    if (!allowed.test(holder)) {
                        continue;
                    }
                    if (selected == null || random.applyAsInt(matches + 1) == 0) {
                        selected = new BiomeLocation<>(quartX << 2, y, quartZ << 2, holder);
                    }
                    matches++;
                }
            }
        }
        return selected;
    }

    private H getNaturalSurfaceStructureBiomeHolder(int x, int z) {
        int blockX = x << 2;
        int blockZ = z << 2;
        try (GenerationHistoryRuntimeRouter.CoordinateScope historyScope =
                     openHistoryCoordinateScope(blockX, blockZ, "bukkit_natural_structure_biome")) {
            RuntimeColumnKey columnKey = new RuntimeColumnKey(
                    engine.getCacheID(), packColumnKey(x, z));
            H cachedHolder = naturalSurfaceStructureBiomeCache.get(columnKey);
            if (cachedHolder != null) {
                return cachedHolder;
            }
            H resolvedHolder = resolveNaturalSurfaceStructureBiomeHolder(x, z);
            H existingHolder = naturalSurfaceStructureBiomeCache.putIfAbsent(
                    columnKey, resolvedHolder);
            if (existingHolder != null) {
                return existingHolder;
            }
            if (naturalSurfaceStructureBiomeCache.size() > NOISE_BIOME_CACHE_MAX) {
                naturalSurfaceStructureBiomeCache.clear();
            }
            return resolvedHolder;
        }
    }

    private boolean isGuaranteedSurfaceBiome(int quartY) {
        if (engine == null || engine.isClosed() || engine.getComplex() == null) {
            return false;
        }
        int worldMinHeight = engine.getWorld().minHeight();
        int internalY = (quartY << 2) - worldMinHeight;
        int caveSwitchInternalY = Math.max(-8 - worldMinHeight, 40);
        return internalY > caveSwitchInternalY;
    }

    private H resolveSurfaceStructureBiomeHolder(int x, int z) {
        int blockX = x << 2;
        int blockZ = z << 2;
        IrisBiome irisBiome = resolveSurfaceStructureBiome(engine, blockX, blockZ);
        if (irisBiome == null) {
            throw new IllegalStateException("Iris returned no surface structure biome at block "
                    + blockX + "," + blockZ);
        }
        H holder = resolveBiomeHolder(biomeRegistry, irisBiome.getStructureDerivativeKey());
        if (holder == null) {
            throw new IllegalStateException("Iris structure biome derivative '"
                    + irisBiome.getStructureDerivativeKey() + "' is not registered at block "
                    + blockX + "," + blockZ);
        }
        return holder;
    }

    private H resolveNaturalSurfaceStructureBiomeHolder(int x, int z) {
        int blockX = x << 2;
        int blockZ = z << 2;
        IrisBiome irisBiome = engine.getComplex().getNaturalTrueBiomeStream().get(blockX, blockZ);
        if (irisBiome == null) {
            throw new IllegalStateException("Iris returned no natural structure biome at block "
                    + blockX + "," + blockZ);
        }
        H holder = resolveBiomeHolder(biomeRegistry, irisBiome.getStructureDerivativeKey());
        if (holder == null) {
            throw new IllegalStateException("Iris natural structure biome derivative '"
                    + irisBiome.getStructureDerivativeKey() + "' is not registered at block "
                    + blockX + "," + blockZ);
        }
        return holder;
    }

    public H getVisibleNoiseBiome(int x, int y, int z) {
        try (GenerationHistoryRuntimeRouter.CoordinateScope historyScope = openHistoryCoordinateScope(
                     x << 2, z << 2, "bukkit_visible_biome");
             GenerationSessionLease lease = requireGenerationLease(
                     "bukkit_visible_biome",
                     "Iris visible biome lookup was rejected during an engine transition");
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            prepareVisibleBiomeBatch();
            return getVisibleNoiseBiomeWithActiveGenerationLease(x, y, z);
        }
    }

    public void prepareVisibleBiomeBatch() {
        if (!isRuntimeAvailable()) {
            throw new IllegalStateException("Iris visible biome lookup has no active engine runtime");
        }
        runtimeBiomeState();
    }

    public H getVisibleNoiseBiomeWithActiveGenerationLease(
            int x,
            int y,
            int z
    ) {
        return getVisibleNoiseBiomeWithActiveGenerationLease(x, y, z, null);
    }

    public H getVisibleNoiseBiomeWithActiveGenerationLease(
            int x,
            int y,
            int z,
            IrisDimensionCarvingResolver.State resolverState
    ) {
        boolean cacheable = isBiomeCacheable(x, z);
        return getVisibleNoiseBiomeWithActiveGenerationLease(
                x, y, z, resolverState, cacheable);
    }

    private H getVisibleNoiseBiomeWithActiveGenerationLease(
            int x,
            int y,
            int z,
            IrisDimensionCarvingResolver.State resolverState,
            boolean cacheable
    ) {
        if (platformGenerator != null && platformGenerator.usesFlatStudioTerrain()) {
            return fallbackBiome;
        }
        RuntimeNoiseKey cacheKey = new RuntimeNoiseKey(
                engine.getCacheID(), packNoiseKey(x, y, z));
        if (cacheable) {
            H cachedHolder = noiseBiomeCache.get(cacheKey);
            if (cachedHolder != null) {
                return cachedHolder;
            }
        }

        H resolvedHolder = resolveVisibleBiomeHolder(x, y, z, resolverState);
        if (!cacheable) {
            return resolvedHolder;
        }
        H existingHolder = noiseBiomeCache.putIfAbsent(cacheKey, resolvedHolder);
        if (existingHolder != null) {
            return existingHolder;
        }

        if (noiseBiomeCache.size() > NOISE_BIOME_CACHE_MAX) {
            noiseBiomeCache.clear();
        }

        return resolvedHolder;
    }

    private boolean isBiomeCacheable(int quartX, int quartZ) {
        return isBiomeCacheable(
                engine,
                ((quartX) << 2),
                ((quartZ) << 2)
        );
    }

    public static boolean isBiomeCacheable(Engine engine, int blockX, int blockZ) {
        return !engine.answersFromNaturalTerrain(blockX, blockZ);
    }

    private GenerationSessionLease tryAcquireGenerationLease(String operation) {
        if (engine.isClosed()) {
            return null;
        }
        try {
            return engine.acquireGenerationLease(operation);
        } catch (GenerationSessionException e) {
            if (engine.isClosing() || e.isExpectedTeardown()) {
                return null;
            }
            throw new IllegalStateException("Iris biome source could not acquire generation session for "
                    + operation + ".", e);
        }
    }

    private GenerationSessionLease requireGenerationLease(String operation, String rejectionMessage) {
        GenerationSessionLease lease = tryAcquireGenerationLease(operation);
        if (lease == null) {
            throw new IllegalStateException(rejectionMessage);
        }
        return lease;
    }

    private boolean isRuntimeAvailable() {
        return !engine.isClosed() && engine.getComplex() != null;
    }

    private GenerationHistoryRuntimeRouter.CoordinateScope openHistoryCoordinateScope(
            int blockX,
            int blockZ,
            String operation
    ) {
        if (!(engine instanceof IrisEngine irisEngine)) {
            return null;
        }
        try {
            return irisEngine.openGenerationHistoryCoordinateScope(blockX, blockZ);
        } catch (IOException failure) {
            throw new IllegalStateException("Iris " + operation
                    + " could not route generation history at "
                    + blockX + "," + blockZ + ".", failure);
        }
    }

    private void evictRuntimeCaches(int runtimeId) {
        runtimeBiomeStates.remove(runtimeId);
        noiseBiomeCache.keySet().removeIf(key -> key.runtimeId() == runtimeId);
        structureBiomeCache.keySet().removeIf(key -> key.runtimeId() == runtimeId);
        surfaceStructureBiomeCache.keySet().removeIf(key -> key.runtimeId() == runtimeId);
        naturalSurfaceStructureBiomeCache.keySet().removeIf(key -> key.runtimeId() == runtimeId);
    }

    private RuntimeBiomeState<H, V> runtimeBiomeState() {
        IrisDimension dimension = engine.getDimension();
        int runtimeId = engine.getCacheID();
        RuntimeBiomeState<H, V> existing = runtimeBiomeStates.get(runtimeId);
        if (existing != null) {
            if (existing.dimension() != dimension) {
                throw new IllegalStateException("Iris biome runtime cache ID is shared by different dimensions.");
            }
            return existing;
        }
        synchronized (runtimeBiomeStates) {
            existing = runtimeBiomeStates.get(runtimeId);
            if (existing != null) {
                if (existing.dimension() != dimension) {
                    throw new IllegalStateException("Iris biome runtime cache ID is shared by different dimensions.");
                }
                return existing;
            }
            KMap<String, H> refreshedCustomBiomes = fillCustomBiomes(
                    biomeCustomRegistry, engine, fallbackBiome);
            Map<V, H> refreshedSpawnBiomes = fillVanillaSpawnBiomes(
                    biomeCustomRegistry, biomeRegistry, engine);
            RuntimeBiomeState<H, V> created = new RuntimeBiomeState<>(
                    dimension,
                    refreshedCustomBiomes,
                    refreshedSpawnBiomes);
            runtimeBiomeStates.put(runtimeId, created);
            return created;
        }
    }

    private H resolveStructureBiomeHolder(int x, int y, int z) {
        BiomeResolution resolution = resolveStructureBiomeResolution(x, y, z);
        if (resolution == null) {
            throw new IllegalStateException("Iris returned no structure biome at quart "
                    + x + "," + y + "," + z);
        }

        H holder = resolveBiomeHolder(
                biomeRegistry, resolution.irisBiome.getStructureDerivativeKey());
        if (holder == null) {
            throw new IllegalStateException("Iris structure biome derivative '"
                    + resolution.irisBiome.getStructureDerivativeKey() + "' is not registered at block "
                    + resolution.blockX + "," + resolution.blockY + "," + resolution.blockZ);
        }
        return holder;
    }

    private H resolveVisibleBiomeHolder(
            int x,
            int y,
            int z,
            IrisDimensionCarvingResolver.State resolverState
    ) {
        return resolveVisibleBiomeHolder(x, y, z, resolverState, null);
    }

    private H resolveVisibleBiomeHolder(
            int x,
            int y,
            int z,
            IrisDimensionCarvingResolver.State resolverState,
            DimensionStackLayout stackLayout
    ) {
        int blockX = x << 2;
        int blockY = y << 2;
        int blockZ = z << 2;
        Optional<String> historicalKey = engine.getComplex().historicalPhysicalBiomeKeyAt(
                blockX, blockY, blockZ);
        if (historicalKey.isPresent()) {
            return resolvePhysicalBiomeHolder(historicalKey.get());
        }
        BiomeResolution resolution = resolveBiomeResolution(
                x, y, z, resolverState, true, stackLayout);
        if (resolution == null) {
            return getFallbackBiome();
        }

        return resolveVisibleBiomeHolder(resolution);
    }

    private H resolveVisibleBiomeHolder(BiomeResolution resolution) {
        if (resolution.irisBiome.isCustom()) {
            return resolveCustomHolder(resolution);
        }

        Biome vanillaBiome = resolution.underground
                ? resolution.irisBiome.getGroundBiome(resolution.rng, engine, resolution.blockX, resolution.blockY, resolution.blockZ)
                : resolution.irisBiome.getSkyBiome(resolution.rng, engine, resolution.blockX, resolution.blockY, resolution.blockZ);
        H holder = biomeRegistry.lookup(vanillaBiome);
        if (holder != null) {
            return holder;
        }

        return getFallbackBiome();
    }

    private H resolveCustomHolder(BiomeResolution resolution) {
        IrisBiomeCustom customBiome = resolution.irisBiome.getCustomBiome(resolution.rng, engine, resolution.blockX, resolution.blockY, resolution.blockZ);
        if (customBiome != null) {
            H holder = runtimeBiomeState().customBiomes().get(customBiomeKey(
                    resolution.dimension, customBiome.getId()));
            if (holder != null) {
                return holder;
            }
        }

        return getFallbackBiome();
    }

    private BiomeResolution resolveStructureBiomeResolution(int x, int y, int z) {
        return resolveBiomeResolution(x, y, z, null, false);
    }

    private H resolvePhysicalBiomeHolder(String physicalKey) {
        H holder = resolveBiomeHolder(biomeCustomRegistry, physicalKey);
        if (holder == null) {
            holder = resolveBiomeHolder(biomeRegistry, physicalKey);
        }
        if (holder == null) {
            throw new IllegalStateException("Historical Iris biome '" + physicalKey
                    + "' is not registered in the active world registry.");
        }
        return holder;
    }

    private BiomeResolution resolveBiomeResolution(
            int x,
            int y,
            int z,
            IrisDimensionCarvingResolver.State resolverState
    ) {
        return resolveBiomeResolution(x, y, z, resolverState, true);
    }

    private BiomeResolution resolveBiomeResolution(
            int x,
            int y,
            int z,
            IrisDimensionCarvingResolver.State resolverState,
            boolean includeDimensionStack
    ) {
        return resolveBiomeResolution(
                x, y, z, resolverState, includeDimensionStack, null);
    }

    private BiomeResolution resolveBiomeResolution(
            int x,
            int y,
            int z,
            IrisDimensionCarvingResolver.State resolverState,
            boolean includeDimensionStack,
            DimensionStackLayout stackLayout
    ) {
        if (engine == null || engine.isClosed()) {
            return null;
        }

        if (engine.getComplex() == null) {
            return null;
        }

        int blockX = x << 2;
        int blockZ = z << 2;
        int blockY = y << 2;
        int worldMinHeight = engine.getWorld().minHeight();
        int internalY = blockY - worldMinHeight;
        int caveSwitchInternalY = Math.max(-8 - worldMinHeight, 40);
        DimensionStackLayout.Layer stackLayer = includeDimensionStack
                ? stackLayout == null
                        ? resolveDimensionStackLayer(engine, blockX, internalY, blockZ)
                        : stackLayout.layerAt(internalY)
                : null;
        IrisDimension owningDimension = stackLayer == null
                ? engine.getDimension()
                : stackLayer.terrainContext().getDimension();
        if (stackLayer != null && !stackLayer.terrainContext().isSelfReferencing()) {
            IrisBiome stackedBiome = stackLayer.biome();
            return stackedBiome == null
                    ? null
                    : createBiomeResolution(
                            stackedBiome, false, owningDimension, blockX, blockY, blockZ);
        }
        boolean deepUnderground = internalY <= caveSwitchInternalY;
        boolean underground = false;
        IrisBiome irisBiome;
        if (stackLayer != null) {
            int surfaceInternalY = stackLayer.surfaceY();
            underground = internalY <= surfaceInternalY - 8;
            irisBiome = underground
                    ? engine.getCaveBiome(
                            blockX,
                            internalY,
                            blockZ,
                            resolverState,
                            stackLayer.biome(),
                            surfaceInternalY
                    )
                    : stackLayer.biome();
        } else if (deepUnderground) {
            int surfaceInternalY = Engine.hostHeight(engine, blockX, blockZ, true);
            underground = internalY <= surfaceInternalY - 8;
            irisBiome = underground
                    ? engine.getCaveBiome(blockX, internalY, blockZ, resolverState)
                    : engine.getHostSurfaceBiome(blockX, blockZ);
        } else {
            irisBiome = engine.getHostSurfaceBiome(blockX, blockZ);
        }
        if (irisBiome == null && underground) {
            irisBiome = stackLayer == null
                    ? engine.getHostSurfaceBiome(blockX, blockZ)
                    : stackLayer.biome();
        }
        if (irisBiome == null) {
            return null;
        }

        return createBiomeResolution(
                irisBiome, underground, owningDimension, blockX, blockY, blockZ);
    }

    private BiomeResolution createBiomeResolution(
            IrisBiome irisBiome,
            boolean underground,
            IrisDimension dimension,
            int blockX,
            int blockY,
            int blockZ
    ) {
        RNG noiseRng = new RNG(seed
                ^ (((long) blockX) * 341873128712L)
                ^ (((long) blockY) * 132897987541L)
                ^ (((long) blockZ) * 42317861L));
        return new BiomeResolution(
                irisBiome, underground, dimension, blockX, blockY, blockZ, noiseRng);
    }

    private H getFallbackBiome() {
        if (fallbackBiome != null) {
            return fallbackBiome;
        }

        H holder = resolveFallbackBiome(biomeRegistry, biomeCustomRegistry);
        if (holder != null) {
            return holder;
        }

        throw new IllegalStateException("Unable to resolve any biome holder fallback for Iris biome source");
    }

    private static long packNoiseKey(int x, int y, int z) {
        return (((long) x & 67108863L) << 38)
                | (((long) z & 67108863L) << 12)
                | ((long) y & 4095L);
    }

    private static long packColumnKey(int x, int z) {
        return ((long) x << 32) ^ ((long) z & 4294967295L);
    }

    private static <H, V> H resolveCustomBiomeHolder(
            NativeBiomeRegistry<H, V> customRegistry,
            Engine engine,
            IrisDimension dimension,
            String customBiomeId
    ) {
        if (customRegistry == null || engine == null || dimension == null
                || customBiomeId == null || customBiomeId.isBlank()) {
            return null;
        }

        return customRegistry.lookupCustom(engine.getData().customBiomeResourceKey(dimension, customBiomeId));
    }

    public static String customBiomeKey(IrisDimension dimension, String customBiomeId) {
        return dimension.getCustomBiomeKey(customBiomeId);
    }

    private static List<OwnedBiome> getOwnedBiomes(Engine engine) {
        ArrayList<OwnedBiome> biomes = new ArrayList<>(getHostOwnedBiomes(engine));
        DimensionStackContext stackContext = engine.getDimensionStackContext();
        if (stackContext == null) {
            return biomes;
        }
        for (DimensionTerrainContext terrainContext : stackContext.getLayersBottomToTop()) {
            if (terrainContext.isSelfReferencing()) {
                continue;
            }
            IrisDimension dimension = terrainContext.getDimension();
            addOwnedBiomes(
                    biomes,
                    dimension,
                    dimension.getReachableBiomes(terrainContext)
            );
        }
        return biomes;
    }

    private static List<OwnedBiome> getHostOwnedBiomes(Engine engine) {
        IrisDimension hostDimension = engine.getDimension();
        ArrayList<OwnedBiome> biomes = new ArrayList<>();
        addOwnedBiomes(biomes, hostDimension, hostDimension.getReachableBiomes(engine));
        return biomes;
    }

    private static void addOwnedBiomes(
            List<OwnedBiome> target,
            IrisDimension dimension,
            Iterable<IrisBiome> biomes
    ) {
        for (IrisBiome biome : biomes) {
            if (biome != null) {
                target.add(new OwnedBiome(dimension, biome));
            }
        }
    }

    private static DimensionStackLayout.Layer resolveDimensionStackLayer(
            Engine engine,
            int blockX,
            int internalY,
            int blockZ
    ) {
        DimensionStackContext stackContext = engine.getDimensionStackContext();
        return stackContext == null ? null : stackContext.getLayerAt(blockX, internalY, blockZ);
    }

    private static IrisBiome resolveSurfaceStructureBiome(Engine engine, int blockX, int blockZ) {
        return engine.getComplex().getTrueBiomeStream().get(blockX, blockZ);
    }

    private static <H, V> H resolveBiomeHolder(NativeBiomeRegistry<H, V> registry, String biomeKey) {
        return registry == null ? null : registry.lookup(biomeKey);
    }

    private static <H, V> H resolveFallbackBiome(NativeBiomeRegistry<H, V> registry, NativeBiomeRegistry<H, V> customRegistry) {
        H plains = registry.lookup(Biome.PLAINS);
        if (plains != null) {
            return plains;
        }

        H vanilla = firstHolder(registry);
        if (vanilla != null) {
            return vanilla;
        }

        return firstHolder(customRegistry);
    }

    private static <H, V> H firstHolder(NativeBiomeRegistry<H, V> registry) {
        return registry == null ? null : registry.first();
    }

    private static final class BiomeResolution {
        private final IrisBiome irisBiome;
        private final boolean underground;
        private final IrisDimension dimension;
        private final int blockX;
        private final int blockY;
        private final int blockZ;
        private final RNG rng;

        private BiomeResolution(
                IrisBiome irisBiome,
                boolean underground,
                IrisDimension dimension,
                int blockX,
                int blockY,
                int blockZ,
                RNG rng
        ) {
            this.irisBiome = irisBiome;
            this.underground = underground;
            this.dimension = dimension;
            this.blockX = blockX;
            this.blockY = blockY;
            this.blockZ = blockZ;
            this.rng = rng;
        }
    }

    private record OwnedBiome(IrisDimension dimension, IrisBiome biome) {
    }

    private record RuntimeNoiseKey(int runtimeId, long coordinateKey) {
    }

    private record RuntimeColumnKey(int runtimeId, long coordinateKey) {
    }

    private record RuntimeBiomeState<H, V>(
            IrisDimension dimension,
            KMap<String, H> customBiomes,
            Map<V, H> vanillaSpawnBiomes
    ) {
    }
    public record RuntimeOptions<H, V>(long seed, Engine engine, BukkitChunkGenerator platformGenerator,
                                      Supplier<NativeBiomeRegistry<H, V>> possibleBiomeRegistry) {
        public RuntimeOptions {
            Objects.requireNonNull(engine, "engine");
            Objects.requireNonNull(possibleBiomeRegistry, "possible biome registry");
        }
    }
    @Override
    public VisibleResolver<H> visibleResolver() {
        IrisDimensionCarvingResolver.State state = new IrisDimensionCarvingResolver.State();
        return (x, y, z) -> getVisibleNoiseBiomeWithActiveGenerationLease(x, y, z, state);
    }

}
