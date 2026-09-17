/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.modded;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.pack.PackValidationRegistry;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.NativeFeatureGenerationPolicy;
import art.arcane.iris.engine.framework.GenerationSessionException;
import art.arcane.iris.engine.framework.GenerationSessionLease;
import art.arcane.iris.engine.framework.NativeStructureStartPlan;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.structure.vanilla.IrisImportedStructureControl;
import art.arcane.iris.nativegen.NativeStructureStartInjector;
import art.arcane.iris.nativegen.NativeStructureReferenceRepair;
import art.arcane.iris.nativegen.NativeStructureVanillaLocator;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.context.IrisContext;
import art.arcane.iris.util.project.hunk.Hunk;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.io.File;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntBinaryOperator;

public final class IrisModdedChunkGenerator extends ChunkGenerator {
    // Vanilla-shaped fallback for an unbound generator (matches IrisDimension defaults). getMinY,
    // getSeaLevel and getGenDepth are called from world creation and client screens, so they must
    // answer without disk I/O and without throwing before a level is bound.
    private static final ModdedDimensionMetadata.DimensionMetadata UNBOUND_HEIGHTS =
            new ModdedDimensionMetadata.DimensionMetadata(-64, 320, 63);
    public static final MapCodec<IrisModdedChunkGenerator> CODEC = RecordCodecBuilder.mapCodec((RecordCodecBuilder.Instance<IrisModdedChunkGenerator> instance) -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter((IrisModdedChunkGenerator generator) -> generator.serializedBiomeSource),
            Codec.STRING.fieldOf("dimension").forGetter((IrisModdedChunkGenerator generator) -> generator.dimensionKey)
    ).apply(instance, IrisModdedChunkGenerator::new));

    public static void startGenPool() {
        ModdedGenPool.start();
    }

    public static void shutdownGenPool() {
        ModdedGenPool.shutdown();
    }

    private final String dimensionKey;
    private final String defaultPack;
    private final String defaultDimensionKey;
    private final BiomeSource serializedBiomeSource;
    final IrisModdedBiomeSource structureBiomeSource;
    private final ModdedEngineBinding<Engine> engineBinding = new ModdedEngineBinding<>(60L, TimeUnit.SECONDS);
    private final ModdedNativeStructureStage nativeStructures = new ModdedNativeStructureStage(this);
    private final ModdedSpawnTableMerger spawnTables = new ModdedSpawnTableMerger(this);
    private final ModdedImportedFeatureStage importedFeatures;
    private final AtomicBoolean announced = new AtomicBoolean(false);
    private volatile boolean unloading;
    private volatile Engine engine;
    private volatile String activePack;
    private volatile String activeDimensionKey;
    private volatile long seedOverride = Long.MIN_VALUE;
    private volatile long lastChunkGenAt = 0L;
    private volatile Set<String> configuredStructureBiomeKeys;
    private volatile ModdedDimensionMetadata.ConfiguredPack configuredPack;
    private volatile ModdedDimensionMetadata.DimensionMetadata heightMetadata;
    private volatile ServerLevel boundLevel;

    public IrisModdedChunkGenerator(BiomeSource biomeSource, String dimensionKey) {
        this(biomeSource, dimensionKey, new IrisModdedBiomeSource(biomeSource));
    }

    private IrisModdedChunkGenerator(BiomeSource serializedBiomeSource, String dimensionKey, IrisModdedBiomeSource structureBiomeSource) {
        this(serializedBiomeSource, dimensionKey, structureBiomeSource,
                new ModdedImportedFeatureStage(structureBiomeSource));
    }

    private IrisModdedChunkGenerator(BiomeSource serializedBiomeSource, String dimensionKey,
                                     IrisModdedBiomeSource structureBiomeSource,
                                     ModdedImportedFeatureStage importedFeatures) {
        // Two-argument ChunkGenerator constructor: the getter maps Iris custom-biome holders onto the
        // generation settings of their vanilla derivative, which is what feeds the per-step feature lists and
        // BiomeFilter's hasFeature gate. It is a pass-through to vanilla's default getter until a pack turns
        // importedFeatures on, so with the control off nothing about generation changes.
        super(structureBiomeSource, importedFeatures::generationSettings);
        this.importedFeatures = importedFeatures;
        importedFeatures.bind(this);
        this.dimensionKey = dimensionKey;
        this.serializedBiomeSource = serializedBiomeSource;
        this.structureBiomeSource = structureBiomeSource;
        this.structureBiomeSource.bind(this);
        int colon = dimensionKey.indexOf(':');
        this.defaultPack = colon >= 0 ? dimensionKey.substring(0, colon) : dimensionKey;
        this.defaultDimensionKey = colon >= 0 ? dimensionKey.substring(colon + 1) : dimensionKey;
        this.activePack = defaultPack;
        this.activeDimensionKey = defaultDimensionKey;
    }

    public synchronized void repoint(String pack, String packDimensionKey, long seed) {
        ServerLevel level = boundLevel();
        if (level != null) {
            repointAndBind(level, pack, packDimensionKey, seed);
            return;
        }
        applyUnboundConfiguration(pack, packDimensionKey, seed);
    }

    synchronized void repointAndBind(ServerLevel level, String pack, String packDimensionKey, long seed) {
        requireBindingAllowed();
        if (level.getChunkSource().getGenerator() != this) {
            throw new IllegalArgumentException("ServerLevel does not use Iris generator '" + dimensionKey + "'");
        }
        requireGlobalStructureGeneration(
                level.getServer().getWorldGenSettings().options().generateStructures(), dimensionKey);
        Engine replacement = ModdedWorldEngines.prepareReplacement(level, pack, packDimensionKey, seed);
        try {
            ModdedWorldEngines.installReplacement(level, replacement);
            replacement.getPlatformHooks().applyWorldBoundary(replacement);
        } catch (Throwable error) {
            try {
                ModdedWorldEngines.closeUnregistered(replacement);
            } catch (Throwable cleanupError) {
                error.addSuppressed(cleanupError);
            }
            if (error instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (error instanceof Error fatalError) {
                throw fatalError;
            }
            throw new IllegalStateException("Iris generator '" + dimensionKey + "' failed to replace its engine", error);
        }
        this.boundLevel = level;
        this.activePack = pack;
        this.activeDimensionKey = packDimensionKey;
        this.seedOverride = seed;
        this.engine = replacement;
        this.configuredStructureBiomeKeys = null;
        this.configuredPack = null;
        this.heightMetadata = engineHeights(replacement);
        this.engineBinding.reset();
        this.engineBinding.complete(replacement);
        this.announced.set(false);
        this.structureBiomeSource.clearCaches();
        this.importedFeatures.invalidate();
        this.nativeStructures.clearWorldCheckStructureShifts();
        this.spawnTables.resetVanillaSpawnBiomes();
        // Bind time: a feature-order cycle in the new pack is reported here, once, and degrades to features-off.
        // Never waits on a build owned by another thread: this method owns the generator monitor and the build
        // path can need it.
        this.importedFeatures.prepareWithoutWaiting(replacement);
    }

    public synchronized void unbindEngine() {
        unloading = true;
        ServerLevel level = boundLevel();
        unbindEngine(level);
    }

    synchronized void unbindEngine(ServerLevel level) {
        unloading = true;
        if (level != null) {
            ModdedWorldEngines.evictOrThrow(level);
        }
        clearEngineBinding();
    }

    private void clearEngineBinding() {
        this.engine = null;
        this.boundLevel = null;
        this.configuredStructureBiomeKeys = null;
        this.configuredPack = null;
        this.engineBinding.reset();
        this.announced.set(false);
        this.structureBiomeSource.clearCaches();
        this.importedFeatures.invalidate();
        this.nativeStructures.clearWorldCheckStructureShifts();
        this.spawnTables.resetVanillaSpawnBiomes();
    }

    private void applyUnboundConfiguration(String pack, String packDimensionKey, long seed) {
        this.activePack = pack;
        this.activeDimensionKey = packDimensionKey;
        this.seedOverride = seed;
        this.engine = null;
        this.boundLevel = null;
        this.configuredStructureBiomeKeys = null;
        this.configuredPack = null;
        this.engineBinding.reset();
        this.announced.set(false);
        this.structureBiomeSource.clearCaches();
        this.importedFeatures.invalidate();
        this.nativeStructures.clearWorldCheckStructureShifts();
        this.spawnTables.resetVanillaSpawnBiomes();
        primeHeightMetadata();
    }

    public synchronized void resetToDefault() {
        repoint(defaultPack, defaultDimensionKey, Long.MIN_VALUE);
    }

    public String activePack() {
        return activePack;
    }

    public String activeDimensionKey() {
        return activeDimensionKey;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState, long seed) {
        ChunkGeneratorStructureState state = ChunkGeneratorStructureState.createForNormal(
                randomState, seed, structureBiomeSource.forStructureState(structureSets), structureSets);
        return ModdedStructureSetFrequencyOverrides.apply(state, configuredImportedStructures());
    }

    private IrisImportedStructureControl configuredImportedStructures() {
        Engine current = engine;
        if (current != null && !current.isClosed() && !current.isClosing()) {
            IrisDimension dimension = current.getDimension();
            if (dimension != null && dimension.getImportedStructures() != null) {
                return dimension.getImportedStructures();
            }
        }
        IrisImportedStructureControl importedStructures = configuredPack().dimension().getImportedStructures();
        return importedStructures == null ? new IrisImportedStructureControl() : importedStructures;
    }

    @Override
    public Pair<BlockPos, Holder<Structure>> findNearestMapStructure(ServerLevel level, HolderSet<Structure> holders,
                                                                     BlockPos pos, int radius,
                                                                     boolean findUnexplored) {
        Engine current = engine();
        try (GenerationSessionLease lease = requireGenerationLease(current, "modded_structure_locate");
             IrisContext.Scope ignored = IrisContext.open(current, lease.sessionId(), null)) {
            HolderSet<Structure> reachable = nativeStructures.filterReachableNativeStructures(
                    level, holders, current);
            NativeStructureVanillaLocator.Candidate nativeCandidate =
                    reachable.size() == 0 ? null
                            : NativeStructureVanillaLocator.predict(
                                    level, reachable, pos, radius, findUnexplored);
            return nativeStructures.findNearestIrisStructure(
                    level, holders, pos, Math.max(0, radius), findUnexplored, current, nativeCandidate);
        }
    }

    public boolean isNativeStructureReachable(Holder<Structure> structure) {
        return structure != null && structureBiomeSource.isStructureReachable(structure);
    }

    private ServerLevel boundLevel() {
        ServerLevel cached = boundLevel;
        if (cached != null) {
            return cached;
        }
        MinecraftServer server = ModdedEngineBootstrap.currentServer();
        if (server == null) {
            return null;
        }
        ServerLevel resolved = resolveBoundLevel(server, ModdedServerLevels.levels(server));
        if (resolved != null) {
            boundLevel = resolved;
        }
        return resolved;
    }

    ServerLevel resolveBoundLevel(MinecraftServer server, List<ServerLevel> snapshot) {
        for (ServerLevel level : snapshot) {
            if (level.getChunkSource().getGenerator() == this) {
                return level;
            }
        }
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        return overworld != null && overworld.getChunkSource().getGenerator() == this ? overworld : null;
    }

    Engine engine() {
        Engine cached = readyEngine();
        if (cached != null) {
            return cached;
        }
        ServerLevel level = boundLevel();
        if (level == null) {
            throw new IllegalStateException("Iris generator '" + dimensionKey + "' has no bound ServerLevel yet");
        }
        return bindGenerationLevel(level);
    }

    private Engine engine(ResourceKey<Level> levelKey) {
        Engine cached = readyEngine();
        if (cached != null) {
            return cached;
        }
        ServerLevel level = boundLevel;
        if (level == null) {
            level = requirePublishedLevel(ModdedEngineBootstrap.currentServer(), levelKey);
        } else {
            requireGeneratorLevel(level, levelKey);
        }
        return bindGenerationLevel(level);
    }

    private Engine engine(ServerLevel generationLevel) {
        Engine cached = readyEngine();
        if (cached != null) {
            return cached;
        }
        ServerLevel level = boundLevel == null ? generationLevel : boundLevel;
        requireGeneratorLevel(level, generationLevel.dimension());
        return bindGenerationLevel(level);
    }

    private Engine readyEngine() {
        requireBindingAllowed();
        Engine cached = engine;
        requireCompletedShutdown(cached);
        if (cached != null && !cached.isClosed()) {
            return cached;
        }
        return null;
    }

    private Engine bindGenerationLevel(ServerLevel level) {
        bindLevel(level);
        Engine bound = readyEngine();
        if (bound == null) {
            throw new IllegalStateException("Iris generator '" + dimensionKey
                    + "' completed generation binding without a ready engine");
        }
        return bound;
    }

    ServerLevel requirePublishedLevel(MinecraftServer server, ResourceKey<Level> levelKey) {
        if (server == null) {
            throw new IllegalStateException("Iris generator '" + dimensionKey
                    + "' cannot resolve level '" + levelKey.identifier() + "': server is unavailable");
        }
        ServerLevel level = server.getLevel(levelKey);
        if (level == null) {
            throw new IllegalStateException("Iris generator '" + dimensionKey
                    + "' has no published ServerLevel for '" + levelKey.identifier() + "'");
        }
        requireGeneratorLevel(level, levelKey);
        return level;
    }

    private void requireGeneratorLevel(ServerLevel level, ResourceKey<Level> levelKey) {
        if (!levelKey.equals(level.dimension())) {
            throw new IllegalStateException("Iris generator '" + dimensionKey + "' resolved level '"
                    + level.dimension().identifier() + "' while binding '" + levelKey.identifier() + "'");
        }
        ChunkGenerator publishedGenerator = level.getChunkSource().getGenerator();
        if (publishedGenerator != this) {
            throw new IllegalStateException("Published ServerLevel '" + levelKey.identifier()
                    + "' does not use Iris generator '" + dimensionKey + "'");
        }
    }

    synchronized void bindLevel(ServerLevel level) {
        if (level.getChunkSource().getGenerator() != this) {
            throw new IllegalArgumentException("ServerLevel does not use Iris generator '" + dimensionKey + "'");
        }
        Engine current = engineIfBound();
        if (boundLevel == level && current != null && current.getComplex() != null) {
            return;
        }
        requireCompletedShutdown(engine);
        unloading = false;
        Engine bound = bindEngine(level);
        nativeStructures.installVolumeIndex(level, bound);
        // Bind time: a feature-order cycle is reported here, once, and degrades to features-off. Non-waiting for
        // the same reason as repointAndBind: this method owns the generator monitor.
        importedFeatures.prepareWithoutWaiting(bound);
        ModdedIrisLog.info("Iris bound {}: chunk system {}", level.dimension().identifier(), ModdedGenPool.describeChunkSystem());
    }

    private Engine bindEngine(ServerLevel level) {
        requireBindingAllowed();
        try {
            requireGlobalStructureGeneration(
                    level.getServer().getWorldGenSettings().options().generateStructures(), dimensionKey);
        } catch (RuntimeException error) {
            engineBinding.fail(error);
            throw error;
        }
        // Cache the owning level so hot paths never scan the level map to find themselves.
        boundLevel = level;
        Engine cached = engine;
        requireCompletedShutdown(cached);
        if (cached != null && !cached.isClosed() && cached.getComplex() != null) {
            engineBinding.complete(cached);
            return cached;
        }
        synchronized (this) {
            requireBindingAllowed();
            Engine existing = engine;
            requireCompletedShutdown(existing);
            if (existing != null && !existing.isClosed() && existing.getComplex() != null) {
                engineBinding.complete(existing);
                return existing;
            }
            try {
                Engine created = ModdedWorldEngines.get(level, activePack, activeDimensionKey, seedOverride);
                requireCompletedShutdown(created);
                if (created.isClosed() || created.getComplex() == null) {
                    throw new IllegalStateException("Iris generator '" + dimensionKey
                            + "' created an engine without a ready biome complex");
                }
                engine = created;
                boundLevel = level;
                heightMetadata = engineHeights(created);
                configuredStructureBiomeKeys = null;
                structureBiomeSource.clearCaches();
                engineBinding.complete(created);
                return created;
            } catch (Throwable error) {
                engineBinding.fail(error);
                if (error instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (error instanceof Error fatalError) {
                    throw fatalError;
                }
                throw new IllegalStateException("Iris generator '" + dimensionKey + "' failed to bind", error);
            }
        }
    }

    private void requireCompletedShutdown(Engine current) {
        if (current == null || !current.isClosing() || current.isClosed()) {
            return;
        }
        // closing && !closed is usually a TRANSIENT seal: hotloadComplex/hotloadSilently set closing while
        // they swap the runtime and clear it on completion. Wait the transition out instead of crashing the
        // chunk pipeline; only a seal that never resolves is a genuine incomplete shutdown.
        long deadline = System.currentTimeMillis() + 30_000L;
        while (System.currentTimeMillis() < deadline && !current.hasFailed()) {
            if (current.isClosed() || !current.isClosing()) {
                return;
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new IllegalStateException("Iris generator '" + dimensionKey
                + "' cannot bind while its previous engine shutdown remains incomplete"
                + (current.hasFailed() ? " (engine failed)" : " (waited 30s)"));
    }

    private void requireBindingAllowed() {
        if (unloading) {
            throw new IllegalStateException("Iris generator '" + dimensionKey + "' is unloading and cannot bind an engine");
        }
    }

    static void requireGlobalStructureGeneration(boolean enabled, String dimensionKey) {
        if (enabled) {
            return;
        }
        String remedy = integratedEnvironment()
                ? "enable 'Generate Structures' for this world; Iris requires it, then deny families through "
                        + "importedStructures.disabled or complete keys through importedStructures.disabledExact"
                : "set generate-structures=true in server.properties, restart the server, "
                        + "then deny families through importedStructures.disabled or complete keys through "
                        + "importedStructures.disabledExact";
        throw new IllegalStateException("Iris generator '" + dimensionKey
                + "' cannot bind while generate-structures=false; " + remedy);
    }

    private static boolean integratedEnvironment() {
        try {
            return ModdedEngineBootstrap.loader().clientEnvironment();
        } catch (Throwable e) {
            // No loader bound (unit tests, very early boot): assume dedicated wording.
            return false;
        }
    }

    Engine engineOrNull() {
        requireBindingAllowed();
        Engine cached = engine;
        requireCompletedShutdown(cached);
        if (cached != null && !cached.isClosed()) {
            return cached;
        }
        try {
            return engine();
        } catch (Throwable ignored) {
            return null;
        }
    }

    Engine structureEngineOrNull() {
        requireBindingAllowed();
        engineBinding.throwIfFailed(dimensionKey);
        Engine cached = engine;
        requireCompletedShutdown(cached);
        if (cached != null && !cached.isClosed() && cached.getComplex() != null) {
            return cached;
        }
        ServerLevel level = boundLevel();
        return level == null ? null : bindEngine(level);
    }

    Engine awaitStructureEngine() {
        requireBindingAllowed();
        Engine current = engine;
        requireCompletedShutdown(current);
        if (current != null && !current.isClosed() && current.getComplex() != null) {
            return current;
        }
        Engine bound = engineBinding.await(dimensionKey);
        requireCompletedShutdown(bound);
        if (bound.isClosed() || bound.getComplex() == null) {
            throw new IllegalStateException("Iris generator '" + dimensionKey
                    + "' completed bootstrap without a ready biome complex");
        }
        return bound;
    }

    private Engine requireDataQueryEngine(String operation) {
        requireBindingAllowed();
        Engine current = engine;
        requireCompletedShutdown(current);
        if (current != null && !current.isClosed() && current.getComplex() != null) {
            return current;
        }
        ServerLevel level = boundLevel();
        if (level != null) {
            return bindEngine(level);
        }
        MinecraftServer server = ModdedEngineBootstrap.currentServer();
        if (server == null) {
            throw new IllegalStateException("Iris generator '" + dimensionKey + "' cannot answer "
                    + operation + " without an active server");
        }
        if (server.isSameThread()) {
            throw new IllegalStateException("Iris generator '" + dimensionKey + "' cannot answer "
                    + operation + " on the server thread before its ServerLevel is bound");
        }
        return awaitStructureEngine();
    }

    long visibleBiomeSeed() {
        long configuredSeed = seedOverride;
        if (configuredSeed != Long.MIN_VALUE) {
            return configuredSeed;
        }
        ServerLevel level = boundLevel();
        if (level != null) {
            return level.getSeed();
        }
        Engine current = engine;
        return current == null ? 0L : current.getWorld().getRawWorldSeed();
    }

    Set<String> configuredStructureBiomeKeys() {
        Set<String> cached = configuredStructureBiomeKeys;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (configuredStructureBiomeKeys != null) {
                return configuredStructureBiomeKeys;
            }
            Engine current = engine;
            if (current != null && !current.isClosed() && !current.isClosing()) {
                try (GenerationSessionLease lease = current.acquireGenerationLease("modded_configured_biome_keys");
                     IrisContext.Scope ignored = IrisContext.open(current, lease.sessionId(), null)) {
                    Set<String> resolved = ModdedDimensionMetadata.collectConfiguredBiomeKeys(
                            current.getAllBiomes(), current.getDimension().getLoadKey());
                    configuredStructureBiomeKeys = resolved;
                    return resolved;
                } catch (GenerationSessionException e) {
                    if (!current.isClosing() && !e.isExpectedTeardown()) {
                        throw new IllegalStateException("Iris configured biome lookup could not acquire its engine runtime.", e);
                    }
                }
            }
            ModdedDimensionMetadata.ConfiguredPack configured = configuredPack();
            Set<String> resolved = ModdedDimensionMetadata.collectConfiguredBiomeKeys(
                    configured.dimension(), configured.data());
            configuredStructureBiomeKeys = resolved;
            return resolved;
        }
    }

    private static ModdedDimensionMetadata.DimensionMetadata engineHeights(Engine engine) {
        IrisDimension dimension = engine.getDimension();
        int minY = engine.getMinHeight();
        return new ModdedDimensionMetadata.DimensionMetadata(minY, engine.getMaxHeight(),
                dimension == null ? minY : minY + dimension.getFluidHeight());
    }

    /**
     * Resolves the pack height metadata on the calling thread so the vanilla height accessors stay pure
     * reads. Never fatal: a pack that cannot be read here falls back to {@link #UNBOUND_HEIGHTS} until a
     * bind succeeds.
     */
    private void primeHeightMetadata() {
        try {
            heightMetadata = configuredPack().metadata();
        } catch (Throwable e) {
            ModdedIrisLog.warn("Iris generator '{}' could not pre-resolve pack heights for {}:{}: {}",
                    dimensionKey, activePack, activeDimensionKey, e.toString());
        }
    }

    private ModdedDimensionMetadata.DimensionMetadata heightMetadata() {
        ModdedDimensionMetadata.DimensionMetadata cached = heightMetadata;
        if (cached != null) {
            return cached;
        }
        ModdedDimensionMetadata.ConfiguredPack pack = configuredPack;
        if (pack == null) {
            return UNBOUND_HEIGHTS;
        }
        ModdedDimensionMetadata.DimensionMetadata resolved = pack.metadata();
        heightMetadata = resolved;
        return resolved;
    }

    private ModdedDimensionMetadata.ConfiguredPack configuredPack() {
        ModdedDimensionMetadata.ConfiguredPack cached = configuredPack;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (configuredPack != null) {
                return configuredPack;
            }
            PackValidationRegistry.requireLoadable(activePack);
            File packDirectory = ModdedWorldEngines.resolvePack(activePack, activeDimensionKey);
            IrisData data = IrisData.get(packDirectory);
            IrisDimension dimension = data.getDimensionLoader().load(activeDimensionKey);
            if (dimension == null) {
                throw new IllegalStateException("Iris dimension '" + activeDimensionKey
                        + "' missing from pack " + packDirectory.getAbsolutePath());
            }
            ModdedDimensionMetadata.ConfiguredPack resolved = new ModdedDimensionMetadata.ConfiguredPack(
                    data, dimension, ModdedDimensionMetadata.dimensionMetadata(dimension));
            configuredPack = resolved;
            heightMetadata = resolved.metadata();
            return resolved;
        }
    }

    public String dimensionKey() {
        return dimensionKey;
    }

    /**
     * Operator-facing importedFeatures state: null when the control is off, "on" when the feature table is
     * live, "degraded" when the control is enabled but the table failed to build (feature-order cycle).
     */
    public String importedFeaturesStatus() {
        Engine current = engineIfBound();
        if (current == null || !NativeFeatureGenerationPolicy.isEnabled(current)) {
            return null;
        }
        return importedFeatures.active() ? "on" : "degraded";
    }

    public Engine engineIfBound() {
        Engine current = engine;
        return unloading || current == null || current.isClosing() || current.isClosed() ? null : current;
    }

    public Engine commandEngine() {
        return engine();
    }

    public long lastChunkGenAt() {
        return lastChunkGenAt;
    }

    public void onHotload() {
        configuredStructureBiomeKeys = null;
        structureBiomeSource.clearCaches();
        importedFeatures.invalidate();
        nativeStructures.clearWorldCheckStructureShifts();
        spawnTables.resetVanillaSpawnBiomes();
    }

    @Override
    public WeightedList<MobSpawnSettings.SpawnerData> getMobsAt(
            Holder<Biome> biome, StructureManager structureManager, MobCategory category, BlockPos pos) {
        WeightedList<MobSpawnSettings.SpawnerData> explicitSpawns = biome.value().getMobSettings().getMobs(category);
        WeightedList<MobSpawnSettings.SpawnerData> resolvedSpawns = super.getMobsAt(
                biome, structureManager, category, pos);
        if (resolvedSpawns != explicitSpawns) {
            return resolvedSpawns;
        }

        Registry<Biome> registry = structureManager.registryAccess().lookupOrThrow(Registries.BIOME);
        spawnTables.initializeVanillaSpawnBiomes(registry);
        Holder<Biome> vanillaSpawnBiome = spawnTables.vanillaSpawnBiome(biome.value());
        if (vanillaSpawnBiome == null) {
            return explicitSpawns;
        }

        WeightedList<MobSpawnSettings.SpawnerData> vanillaSpawns = vanillaSpawnBiome.value().getMobSettings().getMobs(category);
        if (explicitSpawns.isEmpty()) {
            return vanillaSpawns;
        }
        if (vanillaSpawns.isEmpty()) {
            return explicitSpawns;
        }

        return spawnTables.mergedSpawnTable(biome.value(), category, vanillaSpawns, explicitSpawns);
    }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender,
                                                       StructureManager structureManager, ChunkAccess chunk) {
        Engine current = engine();
        try (GenerationSessionLease lease = requireGenerationLease(current, "modded_create_biomes");
             IrisContext.Scope ignored = IrisContext.open(current, lease.sessionId(), null)) {
            chunk.fillBiomesFromNoise(structureBiomeSource::getVisibleNoiseBiome, randomState.sampler());
            return CompletableFuture.completedFuture(chunk);
        }
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        Engine generationEngine = engine();
        ChunkPos pos = chunk.getPos();
        lastChunkGenAt = System.currentTimeMillis();
        ModdedIrisLog.debug("Iris generating chunk {},{}", pos.x(), pos.z());

        PlatformBlockState air = IrisPlatforms.get().registries().air();

        if (ModdedGenPool.parallelChunkSystem()) {
            return CompletableFuture.completedFuture(
                    generateTerrain(chunk, generationEngine, pos, air));
        }
        return CompletableFuture.supplyAsync(
                () -> generateTerrain(chunk, generationEngine, pos, air),
                ModdedGenPool.pool());
    }

    private ChunkAccess generateTerrain(ChunkAccess chunk, Engine generationEngine, ChunkPos pos,
                                        PlatformBlockState air) {
        try (GenerationSessionLease lease = generationEngine.acquireGenerationLease("modded_chunk_pipeline");
             IrisContext.Scope ignored = IrisContext.open(generationEngine, lease.sessionId(), null)) {
            if (announced.compareAndSet(false, true)) {
                ModdedIrisLog.info("Iris generating {} through IrisModdedChunkGenerator (dim={} first chunk {},{})",
                        dimensionKey, generationEngine.getDimension().getLoadKey(), pos.x(), pos.z());
            }
            int dimMinY = generationEngine.getMinHeight();
            int dimMaxY = generationEngine.getMaxHeight();
            int height = dimMaxY - dimMinY;
            ModdedBlockBuffer blocks = new ModdedBlockBuffer(height, air);
            Hunk<PlatformBiome> biomes = Hunk.newArrayHunk(16, height, 16);
            generationEngine.generate(pos.getMinBlockX(), pos.getMinBlockZ(), blocks, biomes, false);

            writeBlocks(chunk, blocks, dimMinY, height);
            writeTerrainHeightmaps(chunk, generationEngine, pos, height);
            Heightmap.primeHeightmaps(chunk, EnumSet.of(
                    Heightmap.Types.MOTION_BLOCKING,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES));
            ModdedWorldManager.enqueueGenerated(generationEngine, pos.x(), pos.z());
            return chunk;
        } catch (GenerationSessionException e) {
            if (generationEngine.isClosing() || e.isExpectedTeardown()) {
                ModdedIrisLog.debug("Iris chunk {},{} skipped: engine sealed for hotload/teardown", pos.x(), pos.z());
                throw new IllegalStateException(
                        "Iris chunk generation was rejected during an engine transition.", e);
            }
            ModdedIrisLog.error("Iris failed to generate chunk {},{}", pos.x(), pos.z(), e);
            throw new IllegalStateException("Iris generation failed for chunk " + pos.x() + "," + pos.z(), e);
        } catch (Throwable e) {
            ModdedIrisLog.error("Iris failed to generate chunk {},{}", pos.x(), pos.z(), e);
            throw new IllegalStateException("Iris generation failed for chunk " + pos.x() + "," + pos.z(), e);
        }
    }

    private void writeTerrainHeightmaps(ChunkAccess chunk, Engine generationEngine, ChunkPos pos, int height) {
        int baseX = pos.getMinBlockX();
        int baseZ = pos.getMinBlockZ();
        writeTerrainHeightmap(chunk, Heightmap.Types.WORLD_SURFACE_WG, height,
                (x, z) -> generationEngine.getHeight(baseX + x, baseZ + z, false) + 1);
        writeTerrainHeightmap(chunk, Heightmap.Types.OCEAN_FLOOR_WG, height,
                (x, z) -> generationEngine.getHeight(baseX + x, baseZ + z, true) + 1);
    }

    private void writeTerrainHeightmap(ChunkAccess chunk, Heightmap.Types type, int height,
                                       IntBinaryOperator heightResolver) {
        Heightmap heightmap = chunk.getOrCreateHeightmapUnprimed(type);
        heightmap.setRawData(chunk, type, ModdedHeightmaps.terrainRawData(height, heightResolver));
    }

    public BiomeResolver regenBiomeResolver() {
        engine();
        return structureBiomeSource::getVisibleNoiseBiome;
    }

    private void writeBlocks(ChunkAccess chunk, ModdedBlockBuffer blocks, int dimMinY, int height) {
        int chunkMinY = chunk.getMinY();
        int chunkMaxY = chunkMinY + chunk.getHeight();
        int from = Math.max(dimMinY, chunkMinY);
        int to = Math.min(dimMinY + height, chunkMaxY);
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();

        for (int y = from; y < to; ) {
            int sectionIndex = chunk.getSectionIndex(y);
            LevelChunkSection section = chunk.getSection(sectionIndex);
            int sectionMinY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
            int sectionEnd = Math.min(sectionMinY + 16, to);
            section.acquire();
            try {
                for (int blockY = y; blockY < sectionEnd; blockY++) {
                    int bufferY = blockY - dimMinY;
                    int localY = blockY & 15;
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            PlatformBlockState state = blocks.rawOrNull(x, bufferY, z);
                            if (state == null) {
                                continue;
                            }
                            BlockState blockState = (BlockState) state.nativeHandle();
                            section.setBlockState(x, localY, z, blockState, false);
                            if (blockState.hasBlockEntity()) {
                                createDefaultBlockEntity(chunk, new BlockPos(baseX + x, blockY, baseZ + z), blockState);
                            }
                        }
                    }
                }
            } finally {
                section.release();
            }
            y = sectionEnd;
        }
    }

    static void createDefaultBlockEntity(ChunkAccess chunk, BlockPos position, BlockState state) {
        if (!(state.getBlock() instanceof EntityBlock entityBlock)) {
            return;
        }
        BlockEntity blockEntity = entityBlock.newBlockEntity(position, state);
        if (blockEntity != null) {
            chunk.setBlockEntity(blockEntity);
        }
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk) {
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        Engine current = engine();
        importedFeatures.prepare(current);
        try (GenerationSessionLease lease = requireGenerationLease(current, "modded_biome_decoration");
             IrisContext.Scope ignored = IrisContext.open(current, lease.sessionId(), null)) {
            nativeStructures.placeVanillaStructures(level, chunk, structureManager);
            // Vanilla's placed-feature pass, on THIS thread and never on ModdedGenPool: the FEATURES chunk
            // step writes into the eight neighbouring chunks and is not parallel-safe. Inert unless the
            // dimension set importedFeatures.enabled.
            importedFeatures.run(level, chunk, current);
        }
    }

    @Override
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState structureState, StructureManager structureManager, ChunkAccess chunk, StructureTemplateManager templateManager, ResourceKey<Level> levelKey) {
        Engine current = engine(levelKey);
        try (GenerationSessionLease lease = requireGenerationLease(current, "modded_create_structures");
             IrisContext.Scope ignored = IrisContext.open(current, lease.sessionId(), null)) {
            Map<Structure, StructureStart> previousStarts = new HashMap<>(chunk.getAllStarts());
            super.createStructures(registryAccess, structureState, structureManager, chunk, templateManager, levelKey);
            Map<Structure, NativeStructureStartPlan> configuredStarts = NativeStructureStartInjector.inject(
                    new NativeStructureStartInjector.InjectionContext(
                            current,
                            registryAccess,
                            structureState,
                            structureManager,
                            chunk,
                            templateManager,
                            levelKey,
                            this,
                            structureBiomeSource
                    ));
            nativeStructures.adjustGeneratedStructures(
                    registryAccess, chunk, previousStarts, configuredStarts, current, templateManager);
        }
    }

    @Override
    public void createReferences(WorldGenLevel level, StructureManager structureManager, ChunkAccess chunk) {
        Engine current = engine(level.getLevel());
        try (GenerationSessionLease lease = requireGenerationLease(current, "modded_create_references");
             IrisContext.Scope ignored = IrisContext.open(current, lease.sessionId(), null)) {
            NativeStructureReferenceRepair.createReferences(
                    current, level, structureManager, chunk);
        }
    }

    Integer worldCheckStructureShift(String structureId, ChunkPos startChunk) {
        return nativeStructures.worldCheckStructureShift(structureId, startChunk);
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        Registry<Biome> registry = region.registryAccess().lookupOrThrow(Registries.BIOME);
        spawnTables.initializeVanillaSpawnBiomes(registry);
        ChunkPos center = region.getCenter();
        Holder<Biome> visibleBiome = region.getBiome(center.getWorldPosition().atY(region.getMaxY()));
        Holder<Biome> vanillaBiome = spawnTables.vanillaSpawnBiome(visibleBiome.value());
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        random.setDecorationSeed(region.getSeed(), center.getMinBlockX(), center.getMinBlockZ());
        NaturalSpawner.spawnMobsForChunkGeneration(
                region, vanillaBiome == null ? visibleBiome : vanillaBiome, center, random);
    }

    @Override
    public int getGenDepth() {
        Engine current = engine;
        return current == null || current.isClosed()
                ? heightMetadata().depth()
                : current.getMaxHeight() - current.getMinHeight();
    }

    @Override
    public int getSeaLevel() {
        Engine current = engine;
        return current == null || current.isClosed()
                ? heightMetadata().seaLevel()
                : current.getMinHeight() + current.getDimension().getFluidHeight();
    }

    @Override
    public int getMinY() {
        Engine current = engine;
        return current == null || current.isClosed()
                ? heightMetadata().minY()
                : current.getMinHeight();
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor heightAccessor) {
        return ModdedDimensionMetadata.clampSpawnHeight(heightAccessor.getMinY(), heightAccessor.getHeight());
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
        Engine current = requireDataQueryEngine("base height");
        boolean ignoreFluid = !type.isOpaque().test(Blocks.WATER.defaultBlockState());
        try (GenerationSessionLease lease = current.acquireGenerationLease("modded_base_height");
             IrisContext.Scope ignored = IrisContext.open(current, lease.sessionId(), null)) {
            return heightAccessor.getMinY() + current.getHeight(x, z, ignoreFluid) + 1;
        } catch (GenerationSessionException e) {
            throw new IllegalStateException("Iris base height query could not acquire its engine runtime.", e);
        }
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
        int minY = heightAccessor.getMinY();
        BlockState[] states = new BlockState[heightAccessor.getHeight()];
        Engine current = requireDataQueryEngine("base column");
        try (GenerationSessionLease lease = current.acquireGenerationLease("modded_base_column");
             IrisContext.Scope ignored = IrisContext.open(current, lease.sessionId(), null)) {
            BlockState airState = Blocks.AIR.defaultBlockState();
            int surface = current.getHeight(x, z, true);
            int fluid = current.getHeight(x, z, false);
            BlockState stone = Blocks.STONE.defaultBlockState();
            BlockState water = Blocks.WATER.defaultBlockState();
            int solidEnd = Math.max(0, Math.min(states.length, surface + 1));
            int fluidEnd = Math.max(solidEnd, Math.max(0, Math.min(states.length, fluid + 1)));
            Arrays.fill(states, 0, solidEnd, stone);
            Arrays.fill(states, solidEnd, fluidEnd, water);
            Arrays.fill(states, fluidEnd, states.length, airState);
            return new NoiseColumn(minY, states);
        } catch (GenerationSessionException e) {
            throw new IllegalStateException("Iris base column query could not acquire its engine runtime.", e);
        }
    }

    GenerationSessionLease requireGenerationLease(Engine current, String operation) {
        try {
            return current.acquireGenerationLease(operation);
        } catch (GenerationSessionException exception) {
            throw new IllegalStateException("Iris " + operation + " could not acquire its engine runtime.", exception);
        }
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        info.add("Iris dimension: " + dimensionKey);
    }

}
