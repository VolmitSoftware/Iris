package art.arcane.iris.platform.bukkit.nms.v26_2_R1;

import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.generation.runtime.DimensionStackContext;
import art.arcane.iris.generation.runtime.DimensionStackLayout;
import art.arcane.iris.generation.runtime.DimensionTerrainContext;
import art.arcane.iris.generation.cache.AtomicCache;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
import java.util.stream.Stream;

public class CustomBiomeSource extends BiomeSource {
    private static final int NOISE_BIOME_CACHE_MAX = 262144;
    private static final int STRONGHOLD_RING_SEARCH_Y = 0;
    private static final int STRONGHOLD_RING_SEARCH_RADIUS = 112;
    private static final int STRONGHOLD_RING_SEARCH_QUART_STEP = 4;

    private final long seed;
    private final Engine engine;
    private final BukkitChunkGenerator platformGenerator;
    private final Registry<Biome> biomeCustomRegistry;
    private final Registry<Biome> biomeRegistry;
    private final AtomicCache<RegistryAccess> registryAccess = new AtomicCache<>();
    private final Holder<Biome> fallbackBiome;
    private final ConcurrentHashMap<RuntimeNoiseKey, Holder<Biome>> noiseBiomeCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RuntimeNoiseKey, Holder<Biome>> structureBiomeCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RuntimeColumnKey, Holder<Biome>> surfaceStructureBiomeCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RuntimeColumnKey, Holder<Biome>> naturalSurfaceStructureBiomeCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, RuntimeBiomeState> runtimeBiomeStates = new ConcurrentHashMap<>();

    public CustomBiomeSource(long seed, Engine engine, World world) {
        this.engine = engine;
        this.platformGenerator = world.getGenerator() instanceof BukkitChunkGenerator generator
                ? generator
                : null;
        this.seed = seed;
        this.biomeCustomRegistry = registry().lookup(Registries.BIOME).orElse(null);
        this.biomeRegistry = ((RegistryAccess) getFor(RegistryAccess.Frozen.class, ((CraftServer) Bukkit.getServer()).getHandle().getServer())).lookup(Registries.BIOME).orElse(null);
        this.fallbackBiome = resolveFallbackBiome(this.biomeRegistry, this.biomeCustomRegistry);
        if (engine instanceof IrisEngine irisEngine) {
            irisEngine.addGenerationRuntimeRetirementListener(this::evictRuntimeCaches);
        }
        runtimeBiomeState();
    }

    private static List<Holder<Biome>> getAllBiomes(
            Registry<Biome> customRegistry,
            Registry<Biome> registry,
            Engine engine,
            boolean includeDimensionStack
    ) {
        LinkedHashSet<Holder<Biome>> biomes = new LinkedHashSet<>();

        for (OwnedBiome ownedBiome : getHostOwnedBiomes(engine)) {
            IrisBiome i = ownedBiome.biome();
            Holder<Biome> vanillaHolder = resolveBiomeHolder(registry, i.getStructureDerivativeKey());
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

    private static List<Holder<Biome>> getVisibleBiomes(
            Registry<Biome> customRegistry,
            Registry<Biome> registry,
            Engine engine,
            Holder<Biome> fallback
    ) {
        LinkedHashSet<Holder<Biome>> biomes = new LinkedHashSet<>();
        boolean fallbackPossible = false;
        for (OwnedBiome ownedBiome : getOwnedBiomes(engine)) {
            IrisBiome irisBiome = ownedBiome.biome();
            if (irisBiome.isCustom()) {
                for (IrisBiomeCustom customBiome : irisBiome.getCustomDerivitives()) {
                    Holder<Biome> customHolder = resolveCustomBiomeHolder(
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

    private static boolean addVisibleBiomeHolder(
            Set<Holder<Biome>> biomes,
            Registry<Biome> registry,
            String biomeKey
    ) {
        Holder<Biome> holder = resolveBiomeHolder(registry, biomeKey);
        if (holder == null) {
            return false;
        }
        biomes.add(holder);
        return true;
    }

    private static Object getFor(Class<?> type, Object source) {
        Object o = fieldFor(type, source);

        if (o != null) {
            return o;
        }

        o = invokeFor(type, source);

        if (o != null) {
            return o;
        }

        throw new IllegalStateException("Iris cannot resolve a " + type.getName()
                + " from " + source.getClass().getName() + " on this server version");
    }

    private static Object fieldFor(Class<?> returns, Object in) {
        for (Class<?> sourceType = in.getClass(); sourceType != null; sourceType = sourceType.getSuperclass()) {
            Object o = fieldForClass(returns, sourceType, in);

            if (o != null) {
                return o;
            }
        }

        return null;
    }

    private static Object invokeFor(Class<?> returns, Object in) {
        for (Method i : in.getClass().getMethods()) {
            if (i.getReturnType().equals(returns)) {
                i.setAccessible(true);
                try {
                    IrisLogging.debug("[NMS] Found " + returns.getSimpleName() + " in " + in.getClass().getSimpleName() + "." + i.getName() + "()");
                    return i.invoke(in);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    throw new IllegalStateException("Iris failed to invoke " + in.getClass().getName() + "."
                            + i.getName() + "() for " + returns.getName(), e);
                }
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T fieldForClass(Class<T> returnType, Class<?> sourceType, Object in) {
        for (Field i : sourceType.getDeclaredFields()) {
            if (i.getType().equals(returnType)) {
                i.setAccessible(true);
                try {
                    IrisLogging.debug("[NMS] Found " + returnType.getSimpleName() + " in " + sourceType.getSimpleName() + "." + i.getName());
                    return (T) i.get(in);
                } catch (IllegalAccessException | RuntimeException e) {
                    throw new IllegalStateException("Iris failed to read " + sourceType.getName() + "."
                            + i.getName() + " for " + returnType.getName(), e);
                }
            }
        }
        return null;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return possibleBiomes(true).stream();
    }

    @Override
    public Set<Holder<Biome>> possibleBiomes() {
        return possibleBiomes(true);
    }

    Set<Holder<Biome>> possibleStructureBiomes() {
        return possibleBiomes(false);
    }

    private Set<Holder<Biome>> possibleBiomes(boolean includeDimensionStack) {
        GenerationSessionLease lease = tryAcquireGenerationLease("bukkit_possible_biomes");
        if (lease == null) {
            throw new IllegalStateException("Iris possible biome lookup was rejected during an engine transition");
        }
        try (lease; IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            if (!isRuntimeAvailable()) {
                throw new IllegalStateException("Iris possible biome lookup has no active engine runtime");
            }
            runtimeBiomeState();
            World world = BukkitWorldBinding.world(engine.getWorld());
            if (world == null) {
                throw new IllegalStateException("Iris biome source has no bound Bukkit world");
            }
            Registry<Biome> customRegistry = ((RegistryAccess) getFor(
                    RegistryAccess.Frozen.class, ((CraftServer) Bukkit.getServer()).getHandle().getServer()))
                    .lookup(Registries.BIOME).orElse(null);
            Registry<Biome> worldRegistry = ((CraftWorld) world).getHandle().registryAccess()
                    .lookup(Registries.BIOME).orElse(null);
            return Set.copyOf(getAllBiomes(
                    customRegistry, worldRegistry, engine, includeDimensionStack));
        }
    }

    private KMap<String, Holder<Biome>> fillCustomBiomes(Registry<Biome> customRegistry, Engine engine, Holder<Biome> fallback) {
        KMap<String, Holder<Biome>> m = new KMap<>();
        if (customRegistry == null) {
            return m;
        }

        for (OwnedBiome ownedBiome : getOwnedBiomes(engine)) {
            IrisBiome i = ownedBiome.biome();
            if (i.isCustom()) {
                for (IrisBiomeCustom j : i.getCustomDerivitives()) {
                    String key = customBiomeKey(ownedBiome.dimension(), j.getId());
                    Holder<Biome> holder = resolveCustomBiomeHolder(
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

    private Map<Biome, Holder<Biome>> fillVanillaSpawnBiomes(Registry<Biome> customRegistry, Registry<Biome> registry, Engine engine) {
        IdentityHashMap<Biome, Holder<Biome>> spawnBiomes = new IdentityHashMap<>();
        if (customRegistry == null || registry == null) {
            return Collections.unmodifiableMap(spawnBiomes);
        }

        for (OwnedBiome ownedBiome : getOwnedBiomes(engine)) {
            IrisBiome irisBiome = ownedBiome.biome();
            if (!irisBiome.isCustom()) {
                continue;
            }
            Holder<Biome> vanillaHolder = NMSBinding.biomeToBiomeBase(registry, irisBiome.getVanillaDerivative());
            if (vanillaHolder == null) {
                continue;
            }
            for (IrisBiomeCustom customBiome : irisBiome.getCustomDerivitives()) {
                Holder<Biome> customHolder = resolveCustomBiomeHolder(
                        customRegistry, engine, ownedBiome.dimension(), customBiome.getId());
                if (customHolder != null) {
                    spawnBiomes.putIfAbsent(customHolder.value(), vanillaHolder);
                }
            }
        }

        return Collections.unmodifiableMap(spawnBiomes);
    }

    Holder<Biome> getRetainedVanillaSpawnBiome(String derivativeKey) {
        return resolveBiomeHolder(biomeRegistry, derivativeKey);
    }

    Holder<Biome> getVanillaSpawnBiome(Holder<Biome> biome) {
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
            return runtimeBiomeState().vanillaSpawnBiomes().get(biome.value());
        }
    }

    Holder<Biome> getVisibleSurfaceBiome(int blockX, int blockZ) {
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
            int quartX = QuartPos.fromBlock(blockX);
            int quartZ = QuartPos.fromBlock(blockZ);
            int sampleX = QuartPos.toBlock(quartX);
            int sampleZ = QuartPos.toBlock(quartZ);
            boolean cacheable = isBiomeCacheable(engine, sampleX, sampleZ);
            DimensionStackContext stackContext = engine.getDimensionStackContext();
            DimensionStackLayout.Layer layer = stackContext == null
                    ? null
                    : stackContext.getLayout(sampleX, sampleZ).surfaceLayer();
            int internalY = layer == null
                    ? Engine.hostHeight(engine, sampleX, sampleZ, true)
                    : layer.clippedSurfaceY();
            int quartY = QuartPos.fromBlock(internalY + engine.getWorld().minHeight());
            return getVisibleNoiseBiomeWithActiveGenerationLease(
                    quartX, quartY, quartZ, null, null, cacheable);
        }
    }

    private RegistryAccess registry() {
        RegistryAccess access = registryAccess.aquire(() -> (RegistryAccess) getFor(RegistryAccess.Frozen.class, ((CraftServer) Bukkit.getServer()).getHandle().getServer()));

        if (access == null) {
            throw new IllegalStateException("Iris cannot resolve the Minecraft registry access on this server version");
        }

        return access;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
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

    private Holder<Biome> getStructureNoiseBiomeWithActiveGenerationLease(int x, int y, int z) {
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
            Holder<Biome> cachedHolder = structureBiomeCache.get(cacheKey);
            if (cachedHolder != null) {
                return cachedHolder;
            }
        }

        Holder<Biome> resolvedHolder = resolveStructureBiomeHolder(x, y, z);
        if (!cacheable) {
            return resolvedHolder;
        }
        Holder<Biome> existingHolder = structureBiomeCache.putIfAbsent(cacheKey, resolvedHolder);
        if (existingHolder != null) {
            return existingHolder;
        }

        if (structureBiomeCache.size() > NOISE_BIOME_CACHE_MAX) {
            structureBiomeCache.clear();
        }

        return resolvedHolder;
    }

    @Override
    public Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(
            int x,
            int y,
            int z,
            int searchRadius,
            Predicate<Holder<Biome>> allowed,
            RandomSource random,
            Climate.Sampler sampler
    ) {
        int quartStep = horizontalBiomeSearchQuartStep(y, searchRadius);
        if (quartStep == 1) {
            return super.findBiomeHorizontal(x, y, z, searchRadius, allowed, random, sampler);
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
    public Pair<BlockPos, Holder<Biome>> findClosestBiome3d(
            BlockPos origin,
            int searchRadius,
            int sampleResolutionHorizontal,
            int sampleResolutionVertical,
            Predicate<Holder<Biome>> allowed,
            Climate.Sampler sampler,
            LevelReader level
    ) {
        GenerationSessionLease lease = tryAcquireGenerationLease("bukkit_locate_visible_biome");
        if (lease == null) {
            throw new IllegalStateException("Iris visible biome search was rejected during an engine transition");
        }
        try (lease; IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            prepareVisibleBiomeBatch();
            DimensionStackContext stackContext = engine.getDimensionStackContext();
            if (stackContext == null) {
                return super.findClosestBiome3d(
                        origin, searchRadius, sampleResolutionHorizontal, sampleResolutionVertical,
                        allowed, sampler, level);
            }
            Set<Holder<Biome>> candidates = new HashSet<>();
            for (Holder<Biome> biome : getVisibleBiomes(
                    biomeCustomRegistry, biomeRegistry, engine, fallbackBiome)) {
                if (allowed.test(biome)) {
                    candidates.add(biome);
                }
            }
            if (candidates.isEmpty()) {
                return null;
            }
            int sampleRadius = Math.floorDiv(searchRadius, sampleResolutionHorizontal);
            int[] sampleYs = Mth.outFromOrigin(
                    origin.getY(), level.getMinY() + 1, level.getMaxY() + 1, sampleResolutionVertical).toArray();
            for (BlockPos.MutableBlockPos sampleColumn : BlockPos.spiralAround(
                    BlockPos.ZERO, sampleRadius, Direction.EAST, Direction.SOUTH)) {
                int blockX = origin.getX() + sampleColumn.getX() * sampleResolutionHorizontal;
                int blockZ = origin.getZ() + sampleColumn.getZ() * sampleResolutionHorizontal;
                int quartX = QuartPos.fromBlock(blockX);
                int quartZ = QuartPos.fromBlock(blockZ);
                DimensionStackLayout stackLayout = stackContext.sample(
                        QuartPos.toBlock(quartX), QuartPos.toBlock(quartZ));
                for (int blockY : sampleYs) {
                    int quartY = QuartPos.fromBlock(blockY);
                    Holder<Biome> visibleBiome = resolveVisibleBiomeHolder(
                            quartX, quartY, quartZ, null, stackLayout);
                    if (candidates.contains(visibleBiome)) {
                        return Pair.of(new BlockPos(blockX, blockY, blockZ), visibleBiome);
                    }
                }
            }
            return null;
        }
    }

    static int horizontalBiomeSearchQuartStep(int blockY, int searchRadius) {
        return blockY == STRONGHOLD_RING_SEARCH_Y && searchRadius == STRONGHOLD_RING_SEARCH_RADIUS
                ? STRONGHOLD_RING_SEARCH_QUART_STEP
                : 1;
    }

    @Override
    public Set<Holder<Biome>> getBiomesWithin(int x, int y, int z, int radius, Climate.Sampler sampler) {
        GenerationSessionLease lease = tryAcquireGenerationLease("bukkit_biomes_within");
        if (lease == null) {
            throw new IllegalStateException("Iris biome radius lookup was rejected during an engine transition");
        }
        try (lease; IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            if (!isRuntimeAvailable()) {
                throw new IllegalStateException("Iris biome radius lookup has no active engine runtime");
            }
            int minQuartY = QuartPos.fromBlock(y - radius);
            boolean monumentQuery = radius == 29
                    && y == engine.getMinHeight() + engine.getDimension().getFluidHeight();
            if (!monumentQuery && !isGuaranteedSurfaceBiome(minQuartY)) {
                return super.getBiomesWithin(x, y, z, radius, sampler);
            }
            int minQuartX = QuartPos.fromBlock(x - radius);
            int maxQuartX = QuartPos.fromBlock(x + radius);
            int minQuartZ = QuartPos.fromBlock(z - radius);
            int maxQuartZ = QuartPos.fromBlock(z + radius);
            int columns = (maxQuartX - minQuartX + 1) * (maxQuartZ - minQuartZ + 1);
            Set<Holder<Biome>> biomes = new HashSet<>(columns);
            for (int quartZ = minQuartZ; quartZ <= maxQuartZ; quartZ++) {
                for (int quartX = minQuartX; quartX <= maxQuartX; quartX++) {
                    biomes.add(getSurfaceStructureBiomeHolder(quartX, quartZ));
                }
            }
            return biomes;
        }
    }

    private Holder<Biome> getSurfaceStructureBiomeHolder(int x, int z) {
        int blockX = x << 2;
        int blockZ = z << 2;
        try (GenerationHistoryRuntimeRouter.CoordinateScope historyScope =
                     openHistoryCoordinateScope(blockX, blockZ, "bukkit_surface_structure_biome")) {
            RuntimeColumnKey columnKey = new RuntimeColumnKey(
                    engine.getCacheID(), packColumnKey(x, z));
            Holder<Biome> surfaceHolder = surfaceStructureBiomeCache.get(columnKey);
            if (surfaceHolder != null) {
                return surfaceHolder;
            }
            Holder<Biome> resolvedSurfaceHolder = resolveSurfaceStructureBiomeHolder(x, z);
            Holder<Biome> existingSurfaceHolder = surfaceStructureBiomeCache.putIfAbsent(
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

    private Pair<BlockPos, Holder<Biome>> findNaturalSurfaceBiomeHorizontal(
            int x,
            int y,
            int z,
            int searchRadius,
            int quartStep,
            Predicate<Holder<Biome>> allowed,
            RandomSource random
    ) {
        int centerQuartX = QuartPos.fromBlock(x);
        int centerQuartZ = QuartPos.fromBlock(z);
        int quartRadius = QuartPos.fromBlock(searchRadius);
        Pair<BlockPos, Holder<Biome>> selected = null;
        int matches = 0;
        for (int radius = 0; radius <= quartRadius; radius += quartStep) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ += quartStep) {
                for (int offsetX = -radius; offsetX <= radius; offsetX += quartStep) {
                    int quartX = centerQuartX + offsetX;
                    int quartZ = centerQuartZ + offsetZ;
                    Holder<Biome> holder = getNaturalSurfaceStructureBiomeHolder(quartX, quartZ);
                    if (!allowed.test(holder)) {
                        continue;
                    }
                    if (selected == null || random.nextInt(matches + 1) == 0) {
                        selected = Pair.of(new BlockPos(
                                QuartPos.toBlock(quartX),
                                y,
                                QuartPos.toBlock(quartZ)), holder);
                    }
                    matches++;
                }
            }
        }
        return selected;
    }

    private Holder<Biome> getNaturalSurfaceStructureBiomeHolder(int x, int z) {
        int blockX = x << 2;
        int blockZ = z << 2;
        try (GenerationHistoryRuntimeRouter.CoordinateScope historyScope =
                     openHistoryCoordinateScope(blockX, blockZ, "bukkit_natural_structure_biome")) {
            RuntimeColumnKey columnKey = new RuntimeColumnKey(
                    engine.getCacheID(), packColumnKey(x, z));
            Holder<Biome> cachedHolder = naturalSurfaceStructureBiomeCache.get(columnKey);
            if (cachedHolder != null) {
                return cachedHolder;
            }
            Holder<Biome> resolvedHolder = resolveNaturalSurfaceStructureBiomeHolder(x, z);
            Holder<Biome> existingHolder = naturalSurfaceStructureBiomeCache.putIfAbsent(
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

    private Holder<Biome> resolveSurfaceStructureBiomeHolder(int x, int z) {
        int blockX = x << 2;
        int blockZ = z << 2;
        IrisBiome irisBiome = resolveSurfaceStructureBiome(engine, blockX, blockZ);
        if (irisBiome == null) {
            throw new IllegalStateException("Iris returned no surface structure biome at block "
                    + blockX + "," + blockZ);
        }
        Holder<Biome> holder = resolveBiomeHolder(biomeRegistry, irisBiome.getStructureDerivativeKey());
        if (holder == null) {
            throw new IllegalStateException("Iris structure biome derivative '"
                    + irisBiome.getStructureDerivativeKey() + "' is not registered at block "
                    + blockX + "," + blockZ);
        }
        return holder;
    }

    private Holder<Biome> resolveNaturalSurfaceStructureBiomeHolder(int x, int z) {
        int blockX = x << 2;
        int blockZ = z << 2;
        IrisBiome irisBiome = engine.getComplex().getNaturalTrueBiomeStream().get(blockX, blockZ);
        if (irisBiome == null) {
            throw new IllegalStateException("Iris returned no natural structure biome at block "
                    + blockX + "," + blockZ);
        }
        Holder<Biome> holder = resolveBiomeHolder(biomeRegistry, irisBiome.getStructureDerivativeKey());
        if (holder == null) {
            throw new IllegalStateException("Iris natural structure biome derivative '"
                    + irisBiome.getStructureDerivativeKey() + "' is not registered at block "
                    + blockX + "," + blockZ);
        }
        return holder;
    }

    public Holder<Biome> getVisibleNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        try (GenerationHistoryRuntimeRouter.CoordinateScope historyScope = openHistoryCoordinateScope(
                     x << 2, z << 2, "bukkit_visible_biome");
             GenerationSessionLease lease = requireGenerationLease(
                     "bukkit_visible_biome",
                     "Iris visible biome lookup was rejected during an engine transition");
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            prepareVisibleBiomeBatch();
            return getVisibleNoiseBiomeWithActiveGenerationLease(x, y, z, sampler);
        }
    }

    void prepareVisibleBiomeBatch() {
        if (!isRuntimeAvailable()) {
            throw new IllegalStateException("Iris visible biome lookup has no active engine runtime");
        }
        runtimeBiomeState();
    }

    Holder<Biome> getVisibleNoiseBiomeWithActiveGenerationLease(
            int x,
            int y,
            int z,
            Climate.Sampler sampler
    ) {
        return getVisibleNoiseBiomeWithActiveGenerationLease(x, y, z, sampler, null);
    }

    Holder<Biome> getVisibleNoiseBiomeWithActiveGenerationLease(
            int x,
            int y,
            int z,
            Climate.Sampler sampler,
            IrisDimensionCarvingResolver.State resolverState
    ) {
        boolean cacheable = isBiomeCacheable(x, z);
        return getVisibleNoiseBiomeWithActiveGenerationLease(
                x, y, z, sampler, resolverState, cacheable);
    }

    private Holder<Biome> getVisibleNoiseBiomeWithActiveGenerationLease(
            int x,
            int y,
            int z,
            Climate.Sampler sampler,
            IrisDimensionCarvingResolver.State resolverState,
            boolean cacheable
    ) {
        if (platformGenerator != null && platformGenerator.usesFlatStudioTerrain()) {
            return fallbackBiome;
        }
        RuntimeNoiseKey cacheKey = new RuntimeNoiseKey(
                engine.getCacheID(), packNoiseKey(x, y, z));
        if (cacheable) {
            Holder<Biome> cachedHolder = noiseBiomeCache.get(cacheKey);
            if (cachedHolder != null) {
                return cachedHolder;
            }
        }

        Holder<Biome> resolvedHolder = resolveVisibleBiomeHolder(x, y, z, resolverState);
        if (!cacheable) {
            return resolvedHolder;
        }
        Holder<Biome> existingHolder = noiseBiomeCache.putIfAbsent(cacheKey, resolvedHolder);
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
                QuartPos.toBlock(quartX),
                QuartPos.toBlock(quartZ)
        );
    }

    static boolean isBiomeCacheable(Engine engine, int blockX, int blockZ) {
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

    private RuntimeBiomeState runtimeBiomeState() {
        IrisDimension dimension = engine.getDimension();
        int runtimeId = engine.getCacheID();
        RuntimeBiomeState existing = runtimeBiomeStates.get(runtimeId);
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
            KMap<String, Holder<Biome>> refreshedCustomBiomes = fillCustomBiomes(
                    biomeCustomRegistry, engine, fallbackBiome);
            Map<Biome, Holder<Biome>> refreshedSpawnBiomes = fillVanillaSpawnBiomes(
                    biomeCustomRegistry, biomeRegistry, engine);
            RuntimeBiomeState created = new RuntimeBiomeState(
                    dimension,
                    refreshedCustomBiomes,
                    refreshedSpawnBiomes);
            runtimeBiomeStates.put(runtimeId, created);
            return created;
        }
    }

    private Holder<Biome> resolveStructureBiomeHolder(int x, int y, int z) {
        BiomeResolution resolution = resolveStructureBiomeResolution(x, y, z);
        if (resolution == null) {
            throw new IllegalStateException("Iris returned no structure biome at quart "
                    + x + "," + y + "," + z);
        }

        Holder<Biome> holder = resolveBiomeHolder(
                biomeRegistry, resolution.irisBiome.getStructureDerivativeKey());
        if (holder == null) {
            throw new IllegalStateException("Iris structure biome derivative '"
                    + resolution.irisBiome.getStructureDerivativeKey() + "' is not registered at block "
                    + resolution.blockX + "," + resolution.blockY + "," + resolution.blockZ);
        }
        return holder;
    }

    private Holder<Biome> resolveVisibleBiomeHolder(
            int x,
            int y,
            int z,
            IrisDimensionCarvingResolver.State resolverState
    ) {
        return resolveVisibleBiomeHolder(x, y, z, resolverState, null);
    }

    private Holder<Biome> resolveVisibleBiomeHolder(
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

    private Holder<Biome> resolveVisibleBiomeHolder(BiomeResolution resolution) {
        if (resolution.irisBiome.isCustom()) {
            return resolveCustomHolder(resolution);
        }

        org.bukkit.block.Biome vanillaBiome = resolution.underground
                ? resolution.irisBiome.getGroundBiome(resolution.rng, engine, resolution.blockX, resolution.blockY, resolution.blockZ)
                : resolution.irisBiome.getSkyBiome(resolution.rng, engine, resolution.blockX, resolution.blockY, resolution.blockZ);
        Holder<Biome> holder = NMSBinding.biomeToBiomeBase(biomeRegistry, vanillaBiome);
        if (holder != null) {
            return holder;
        }

        return getFallbackBiome();
    }

    private Holder<Biome> resolveCustomHolder(BiomeResolution resolution) {
        IrisBiomeCustom customBiome = resolution.irisBiome.getCustomBiome(resolution.rng, engine, resolution.blockX, resolution.blockY, resolution.blockZ);
        if (customBiome != null) {
            Holder<Biome> holder = runtimeBiomeState().customBiomes().get(customBiomeKey(
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

    private Holder<Biome> resolvePhysicalBiomeHolder(String physicalKey) {
        Holder<Biome> holder = resolveBiomeHolder(biomeCustomRegistry, physicalKey);
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

    private Holder<Biome> getFallbackBiome() {
        if (fallbackBiome != null) {
            return fallbackBiome;
        }

        Holder<Biome> holder = resolveFallbackBiome(biomeRegistry, biomeCustomRegistry);
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

    private static Holder<Biome> resolveCustomBiomeHolder(
            Registry<Biome> customRegistry,
            Engine engine,
            IrisDimension dimension,
            String customBiomeId
    ) {
        if (customRegistry == null || engine == null || dimension == null
                || customBiomeId == null || customBiomeId.isBlank()) {
            return null;
        }

        String physicalKey = engine.getData().customBiomeResourceKey(dimension, customBiomeId);
        Identifier resourceLocation = Identifier.tryParse(physicalKey);
        if (resourceLocation == null) {
            throw new IllegalStateException("Invalid Iris custom biome resource key '" + physicalKey + "'.");
        }
        Biome biome = customRegistry.getValue(resourceLocation);
        if (biome == null) {
            return null;
        }

        Optional<ResourceKey<Biome>> optionalBiomeKey = customRegistry.getResourceKey(biome);
        if (optionalBiomeKey.isEmpty()) {
            return null;
        }

        Optional<Holder.Reference<Biome>> optionalReferenceHolder = customRegistry.get(optionalBiomeKey.get());
        if (optionalReferenceHolder.isEmpty()) {
            return null;
        }

        return optionalReferenceHolder.get();
    }

    static String customBiomeKey(IrisDimension dimension, String customBiomeId) {
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

    private static Holder<Biome> resolveBiomeHolder(Registry<Biome> registry, String biomeKey) {
        if (registry == null || biomeKey == null || biomeKey.isBlank()) {
            return null;
        }
        Identifier identifier = Identifier.tryParse(biomeKey);
        if (identifier == null) {
            return null;
        }
        return registry.get(ResourceKey.create(Registries.BIOME, identifier)).orElse(null);
    }

    private static Holder<Biome> resolveFallbackBiome(Registry<Biome> registry, Registry<Biome> customRegistry) {
        Holder<Biome> plains = NMSBinding.biomeToBiomeBase(registry, org.bukkit.block.Biome.PLAINS);
        if (plains != null) {
            return plains;
        }

        Holder<Biome> vanilla = firstHolder(registry);
        if (vanilla != null) {
            return vanilla;
        }

        return firstHolder(customRegistry);
    }

    private static Holder<Biome> firstHolder(Registry<Biome> registry) {
        if (registry == null) {
            return null;
        }

        for (Biome biome : registry) {
            Optional<ResourceKey<Biome>> optionalBiomeKey = registry.getResourceKey(biome);
            if (optionalBiomeKey.isEmpty()) {
                continue;
            }

            Optional<Holder.Reference<Biome>> optionalHolder = registry.get(optionalBiomeKey.get());
            if (optionalHolder.isPresent()) {
                return optionalHolder.get();
            }
        }

        return null;
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

    private record RuntimeBiomeState(
            IrisDimension dimension,
            KMap<String, Holder<Biome>> customBiomes,
            Map<Biome, Holder<Biome>> vanillaSpawnBiomes
    ) {
    }
}
