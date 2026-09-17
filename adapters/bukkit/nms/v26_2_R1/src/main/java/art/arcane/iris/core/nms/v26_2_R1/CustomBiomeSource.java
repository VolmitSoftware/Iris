package art.arcane.iris.core.nms.v26_2_R1;

import art.arcane.iris.engine.object.world.biome.custom.IrisBiomeCustom;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.engine.data.cache.AtomicCache;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.GenerationSessionException;
import art.arcane.iris.engine.framework.GenerationSessionLease;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.terrain.carving.IrisDimensionCarvingResolver;
import art.arcane.iris.util.project.context.IrisContext;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.math.RNG;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
    private final Registry<Biome> biomeCustomRegistry;
    private final Registry<Biome> biomeRegistry;
    private final AtomicCache<RegistryAccess> registryAccess = new AtomicCache<>();
    private final Holder<Biome> fallbackBiome;
    private final ConcurrentHashMap<Long, Holder<Biome>> noiseBiomeCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Holder<Biome>> structureBiomeCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Holder<Biome>> surfaceStructureBiomeCache = new ConcurrentHashMap<>();
    private volatile KMap<String, Holder<Biome>> customBiomes;
    private volatile Map<Biome, Holder<Biome>> vanillaSpawnBiomes;
    private volatile IrisDimension cacheDimension;
    private volatile int cacheRuntimeId;

    public CustomBiomeSource(long seed, Engine engine, World world) {
        this.engine = engine;
        this.seed = seed;
        this.biomeCustomRegistry = registry().lookup(Registries.BIOME).orElse(null);
        this.biomeRegistry = ((RegistryAccess) getFor(RegistryAccess.Frozen.class, ((CraftServer) Bukkit.getServer()).getHandle().getServer())).lookup(Registries.BIOME).orElse(null);
        this.fallbackBiome = resolveFallbackBiome(this.biomeRegistry, this.biomeCustomRegistry);
        this.customBiomes = fillCustomBiomes(this.biomeCustomRegistry, engine, this.fallbackBiome);
        this.vanillaSpawnBiomes = fillVanillaSpawnBiomes(this.biomeCustomRegistry, this.biomeRegistry, engine);
        this.cacheDimension = engine.getDimension();
        this.cacheRuntimeId = engine.getCacheID();
    }

    private static List<Holder<Biome>> getAllBiomes(Registry<Biome> customRegistry, Registry<Biome> registry, Engine engine) {
        LinkedHashSet<Holder<Biome>> biomes = new LinkedHashSet<>();

        for (IrisBiome i : engine.getAllBiomes()) {
            Holder<Biome> vanillaHolder = resolveBiomeHolder(registry, i.getStructureDerivativeKey());
            if (vanillaHolder == null) {
                throw new IllegalStateException("Iris structure biome derivative '"
                        + i.getStructureDerivativeKey() + "' is not registered for biome '" + i.getLoadKey() + "'");
            }
            biomes.add(vanillaHolder);

            if (i.isCustom()) {
                for (IrisBiomeCustom j : i.getCustomDerivitives()) {
                    Holder<Biome> customHolder = resolveCustomBiomeHolder(customRegistry, engine, j.getId());
                    if (customHolder == null) {
                        throw new IllegalStateException("Iris custom structure biome '"
                                + engine.getDimension().getLoadKey() + ":" + j.getId() + "' is not registered");
                    }
                    biomes.add(customHolder);
                }
            }
        }

        if (biomes.isEmpty()) {
            throw new IllegalStateException("Iris pack '" + engine.getName()
                    + "' has no registered structure biomes");
        }
        return new ArrayList<>(biomes);
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
        return possibleStructureBiomes().stream();
    }

    Set<Holder<Biome>> possibleStructureBiomes() {
        GenerationSessionLease lease = tryAcquireGenerationLease("bukkit_possible_biomes");
        if (lease == null) {
            throw new IllegalStateException("Iris possible biome lookup was rejected during an engine transition");
        }
        try (lease; IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            if (!isRuntimeAvailable()) {
                throw new IllegalStateException("Iris possible biome lookup has no active engine runtime");
            }
            ensureCachesCurrent();
            World world = BukkitWorldBinding.world(engine.getWorld());
            if (world == null) {
                throw new IllegalStateException("Iris biome source has no bound Bukkit world");
            }
            Registry<Biome> customRegistry = ((RegistryAccess) getFor(
                    RegistryAccess.Frozen.class, ((CraftServer) Bukkit.getServer()).getHandle().getServer()))
                    .lookup(Registries.BIOME).orElse(null);
            Registry<Biome> worldRegistry = ((CraftWorld) world).getHandle().registryAccess()
                    .lookup(Registries.BIOME).orElse(null);
            return Set.copyOf(getAllBiomes(customRegistry, worldRegistry, engine));
        }
    }

    private KMap<String, Holder<Biome>> fillCustomBiomes(Registry<Biome> customRegistry, Engine engine, Holder<Biome> fallback) {
        KMap<String, Holder<Biome>> m = new KMap<>();
        if (customRegistry == null) {
            return m;
        }

        for (IrisBiome i : engine.getAllBiomes()) {
            if (i.isCustom()) {
                for (IrisBiomeCustom j : i.getCustomDerivitives()) {
                    Holder<Biome> holder = resolveCustomBiomeHolder(customRegistry, engine, j.getId());
                    if (holder == null) {
                        if (fallback != null) {
                            m.put(j.getId(), fallback);
                        }
                        IrisLogging.error("Cannot find biome for IrisBiomeCustom " + j.getId() + " from engine " + engine.getName());
                        continue;
                    }
                    m.put(j.getId(), holder);
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

        for (IrisBiome irisBiome : engine.getAllBiomes()) {
            if (!irisBiome.isCustom()) {
                continue;
            }
            Holder<Biome> vanillaHolder = NMSBinding.biomeToBiomeBase(registry, irisBiome.getVanillaDerivative());
            if (vanillaHolder == null) {
                continue;
            }
            for (IrisBiomeCustom customBiome : irisBiome.getCustomDerivitives()) {
                Holder<Biome> customHolder = resolveCustomBiomeHolder(customRegistry, engine, customBiome.getId());
                if (customHolder != null) {
                    spawnBiomes.putIfAbsent(customHolder.value(), vanillaHolder);
                }
            }
        }

        return Collections.unmodifiableMap(spawnBiomes);
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
            ensureCachesCurrent();
            return vanillaSpawnBiomes.get(biome.value());
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
        GenerationSessionLease lease = tryAcquireGenerationLease("bukkit_structure_biome");
        if (lease == null) {
            throw new IllegalStateException("Iris structure biome lookup was rejected during an engine transition");
        }
        try (lease; IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            if (!isRuntimeAvailable()) {
                throw new IllegalStateException("Iris structure biome lookup has no active engine runtime");
            }
            ensureCachesCurrent();
            if (isGuaranteedSurfaceBiome(y)) {
                return getSurfaceStructureBiomeHolder(x, z);
            }

            long cacheKey = packNoiseKey(x, y, z);
            Holder<Biome> cachedHolder = structureBiomeCache.get(cacheKey);
            if (cachedHolder != null) {
                return cachedHolder;
            }

            Holder<Biome> resolvedHolder = resolveStructureBiomeHolder(x, y, z);
            Holder<Biome> existingHolder = structureBiomeCache.putIfAbsent(cacheKey, resolvedHolder);
            if (existingHolder != null) {
                return existingHolder;
            }

            if (structureBiomeCache.size() > NOISE_BIOME_CACHE_MAX) {
                structureBiomeCache.clear();
            }

            return resolvedHolder;
        }
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
        return super.findBiomeHorizontal(
                x, y, z, searchRadius, quartStep, allowed, random, false, sampler);
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
            ensureCachesCurrent();
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
        long columnKey = packColumnKey(x, z);
        Holder<Biome> surfaceHolder = surfaceStructureBiomeCache.get(columnKey);
        if (surfaceHolder != null) {
            return surfaceHolder;
        }
        Holder<Biome> resolvedSurfaceHolder = resolveSurfaceStructureBiomeHolder(x, z);
        Holder<Biome> existingSurfaceHolder = surfaceStructureBiomeCache.putIfAbsent(columnKey, resolvedSurfaceHolder);
        if (existingSurfaceHolder != null) {
            return existingSurfaceHolder;
        }
        if (surfaceStructureBiomeCache.size() > NOISE_BIOME_CACHE_MAX) {
            surfaceStructureBiomeCache.clear();
        }
        return resolvedSurfaceHolder;
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
        IrisBiome irisBiome = engine.getComplex().getTrueBiomeStream().get(blockX, blockZ);
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

    public Holder<Biome> getVisibleNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        GenerationSessionLease lease = tryAcquireGenerationLease("bukkit_visible_biome");
        if (lease == null) {
            throw new IllegalStateException("Iris visible biome lookup was rejected during an engine transition");
        }
        try (lease; IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            prepareVisibleBiomeBatch();
            return getVisibleNoiseBiomeWithActiveGenerationLease(x, y, z, sampler);
        }
    }

    void prepareVisibleBiomeBatch() {
        if (!isRuntimeAvailable()) {
            throw new IllegalStateException("Iris visible biome lookup has no active engine runtime");
        }
        ensureCachesCurrent();
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
        long cacheKey = packNoiseKey(x, y, z);
        Holder<Biome> cachedHolder = noiseBiomeCache.get(cacheKey);
        if (cachedHolder != null) {
            return cachedHolder;
        }

        Holder<Biome> resolvedHolder = resolveVisibleBiomeHolder(x, y, z, resolverState);
        Holder<Biome> existingHolder = noiseBiomeCache.putIfAbsent(cacheKey, resolvedHolder);
        if (existingHolder != null) {
            return existingHolder;
        }

        if (noiseBiomeCache.size() > NOISE_BIOME_CACHE_MAX) {
            noiseBiomeCache.clear();
        }

        return resolvedHolder;
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

    private boolean isRuntimeAvailable() {
        return !engine.isClosed() && engine.getComplex() != null;
    }

    private void ensureCachesCurrent() {
        IrisDimension dimension = engine.getDimension();
        int runtimeId = engine.getCacheID();
        if (cacheDimension == dimension && cacheRuntimeId == runtimeId) {
            return;
        }
        synchronized (this) {
            if (cacheDimension == dimension && cacheRuntimeId == runtimeId) {
                return;
            }
            KMap<String, Holder<Biome>> refreshedCustomBiomes = fillCustomBiomes(
                    biomeCustomRegistry, engine, fallbackBiome);
            Map<Biome, Holder<Biome>> refreshedSpawnBiomes = fillVanillaSpawnBiomes(
                    biomeCustomRegistry, biomeRegistry, engine);
            noiseBiomeCache.clear();
            structureBiomeCache.clear();
            surfaceStructureBiomeCache.clear();
            customBiomes = refreshedCustomBiomes;
            vanillaSpawnBiomes = refreshedSpawnBiomes;
            cacheDimension = dimension;
            cacheRuntimeId = runtimeId;
        }
    }

    private Holder<Biome> resolveStructureBiomeHolder(int x, int y, int z) {
        BiomeResolution resolution = resolveBiomeResolution(x, y, z);
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
        BiomeResolution resolution = resolveBiomeResolution(x, y, z, resolverState);
        if (resolution == null) {
            return getFallbackBiome();
        }

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
            Holder<Biome> holder = customBiomes.get(customBiome.getId());
            if (holder != null) {
                return holder;
            }
        }

        return getFallbackBiome();
    }

    private BiomeResolution resolveBiomeResolution(int x, int y, int z) {
        return resolveBiomeResolution(x, y, z, null);
    }

    private BiomeResolution resolveBiomeResolution(
            int x,
            int y,
            int z,
            IrisDimensionCarvingResolver.State resolverState
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
        boolean deepUnderground = internalY <= caveSwitchInternalY;
        boolean underground = false;
        IrisBiome irisBiome;
        if (deepUnderground) {
            int surfaceInternalY = engine.getComplex().getHeightStream().get(blockX, blockZ).intValue();
            underground = internalY <= surfaceInternalY - 8;
            irisBiome = underground
                    ? engine.getCaveBiome(blockX, internalY, blockZ, resolverState)
                    : engine.getComplex().getTrueBiomeStream().get(blockX, blockZ);
        } else {
            irisBiome = engine.getComplex().getTrueBiomeStream().get(blockX, blockZ);
        }
        if (irisBiome == null && underground) {
            irisBiome = engine.getComplex().getTrueBiomeStream().get(blockX, blockZ);
        }
        if (irisBiome == null) {
            return null;
        }

        return createBiomeResolution(irisBiome, underground, blockX, blockY, blockZ);
    }

    private BiomeResolution createBiomeResolution(
            IrisBiome irisBiome,
            boolean underground,
            int blockX,
            int blockY,
            int blockZ
    ) {
        RNG noiseRng = new RNG(seed
                ^ (((long) blockX) * 341873128712L)
                ^ (((long) blockY) * 132897987541L)
                ^ (((long) blockZ) * 42317861L));
        return new BiomeResolution(irisBiome, underground, blockX, blockY, blockZ, noiseRng);
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

    private static Holder<Biome> resolveCustomBiomeHolder(Registry<Biome> customRegistry, Engine engine, String customBiomeId) {
        if (customRegistry == null || engine == null || customBiomeId == null || customBiomeId.isBlank()) {
            return null;
        }

        Identifier resourceLocation = Identifier.fromNamespaceAndPath(
                engine.getDimension().getLoadKey().toLowerCase(java.util.Locale.ROOT),
                customBiomeId.toLowerCase(java.util.Locale.ROOT)
        );
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
        private final int blockX;
        private final int blockY;
        private final int blockZ;
        private final RNG rng;

        private BiomeResolution(IrisBiome irisBiome, boolean underground, int blockX, int blockY, int blockZ, RNG rng) {
            this.irisBiome = irisBiome;
            this.underground = underground;
            this.blockX = blockX;
            this.blockY = blockY;
            this.blockZ = blockZ;
            this.rng = rng;
        }
    }
}
