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

import art.arcane.iris.nativegen.NativeTransitionColumn;
import art.arcane.iris.nativegen.NativeTerrainHeightCache;
import art.arcane.iris.world.history.TerrainBoundarySignature;
import art.arcane.iris.world.history.NativeBiomeSpawnSelection;
import java.util.Optional;
import art.arcane.iris.nativegen.NativeGenerationWriteGuard;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.PackValidationRegistry;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationSessionException;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.structure.nativegen.NativeFeatureGenerationPolicy;
import art.arcane.iris.structure.nativegen.NativeStructureStartPlan;
import art.arcane.iris.world.history.GenerationEpoch;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.world.history.GenerationRegistryContract;
import art.arcane.iris.world.history.GenerationRegistryContractFactory;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.structure.nativegen.IrisImportedStructureControl;
import art.arcane.iris.nativegen.NativeStructureStartInjector;
import art.arcane.iris.nativegen.NativeStructureReferenceRepair;
import art.arcane.iris.nativegen.NativeStructureVanillaLocator;
import art.arcane.iris.nativegen.NativeStructureVolumeIndex;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.volmlib.util.hunk.Hunk;
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
import net.minecraft.world.level.chunk.status.ChunkStatus;
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
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntBinaryOperator;
import java.util.function.IntConsumer;

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
    private final NativeTerrainHeightCache terrainHeights = new NativeTerrainHeightCache();
    private final AtomicBoolean announced = new AtomicBoolean(false);
    private final IntConsumer generationRuntimeRetirementListener = this::retireGenerationRuntimeCaches;
    private volatile boolean unloading;
    private volatile Engine engine;
    private volatile String activePack;
    private volatile String activeDimensionKey;
    private volatile Path immutablePackRoot;
    private volatile ModdedGenerationMode generationMode = ModdedGenerationMode.PERSISTENT_RESTORE;
    private volatile long seedOverride = Long.MIN_VALUE;
    private volatile long lastChunkGenAt = 0L;
    private volatile Set<String> configuredStructureBiomeKeys;
    private volatile ModdedDimensionMetadata.ConfiguredPack configuredPack;
    private volatile ModdedDimensionMetadata.DimensionMetadata heightMetadata;
    private volatile ServerLevel boundLevel;
    private volatile IrisEngine retirementListenerEngine;

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
        repoint(
                pack,
                packDimensionKey,
                seed,
                null,
                ModdedGenerationMode.PERSISTENT_RESTORE
        );
    }

    synchronized void repoint(String pack, String packDimensionKey, long seed, Path packRoot) {
        repoint(
                pack,
                packDimensionKey,
                seed,
                packRoot,
                ModdedGenerationMode.PERSISTENT_RESTORE
        );
    }

    synchronized void repoint(
            String pack,
            String packDimensionKey,
            long seed,
            Path packRoot,
            ModdedGenerationMode generationMode
    ) {
        ServerLevel level = boundLevel();
        if (level != null) {
            this.generationMode = generationMode;
            repointAndBind(level, pack, packDimensionKey, seed);
            return;
        }
        applyUnboundConfiguration(pack, packDimensionKey, seed, packRoot, generationMode);
    }

    synchronized void repointAndBind(ServerLevel level, String pack, String packDimensionKey, long seed) {
        requireBindingAllowed();
        if (level.getChunkSource().getGenerator() != this) {
            throw new IllegalArgumentException("ServerLevel does not use Iris generator '" + dimensionKey + "'");
        }
        requireGlobalStructureGeneration(
                level.getServer().getWorldGenSettings().options().generateStructures(), dimensionKey);
        Engine activeEngine = engineIfBound();
        Engine replacement = ModdedWorldEngines.prepareReplacement(
                level,
                pack,
                packDimensionKey,
                seed,
                generationMode
        );
        try {
            if (activeEngine != null) {
                requireStructureBiomeUniverseCompatible(
                        activeEngine.getDimension(), replacement.getDimension());
            }
            nativeStructures.installVolumeIndex(level, replacement);
            ModdedWorldEngines.installReplacement(level, replacement);
            replacement.getPlatformHooks().applyWorldBoundary(replacement);
        } catch (Throwable error) {
            NativeStructureVolumeIndex.uninstall(replacement);
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
        bindGenerationRuntimeRetirementListener(replacement);
        this.immutablePackRoot = replacement.getData().getDataFolder().toPath().toAbsolutePath().normalize();
        this.configuredStructureBiomeKeys = null;
        this.configuredPack = null;
        this.heightMetadata = engineHeights(replacement);
        this.engineBinding.reset();
        this.announced.set(false);
        resetRuntimeCaches();
        this.engineBinding.complete(replacement);
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
        unbindGenerationRuntimeRetirementListener();
        this.engine = null;
        this.boundLevel = null;
        this.configuredStructureBiomeKeys = null;
        this.configuredPack = null;
        this.immutablePackRoot = null;
        this.engineBinding.reset();
        this.announced.set(false);
        this.structureBiomeSource.clearCaches();
        this.importedFeatures.invalidate();
        this.nativeStructures.clearWorldCheckStructureShifts();
        this.spawnTables.resetVanillaSpawnBiomes();
    }

    private void applyUnboundConfiguration(
            String pack,
            String packDimensionKey,
            long seed,
            Path packRoot,
            ModdedGenerationMode generationMode
    ) {
        unbindGenerationRuntimeRetirementListener();
        this.activePack = pack;
        this.activeDimensionKey = packDimensionKey;
        this.seedOverride = seed;
        this.immutablePackRoot = packRoot == null ? null : packRoot.toAbsolutePath().normalize();
        this.generationMode = Objects.requireNonNull(generationMode, "generationMode");
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
        repoint(
                defaultPack,
                defaultDimensionKey,
                Long.MIN_VALUE,
                null,
                ModdedGenerationMode.PERSISTENT_RESTORE
        );
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
        int chunkX = Math.floorDiv(pos.getX(), 16);
        int chunkZ = Math.floorDiv(pos.getZ(), 16);
        try (GenerationHistoryRuntimeRouter.RuntimeRoute route = openHistoryRoute(
                     current, chunkX, chunkZ, "modded_structure_locate");
             GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope runtimeScope = openHistoryRuntimeScope(route);
             GenerationSessionLease lease = requireGenerationLease(current, "modded_structure_locate");
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
                Engine created = ModdedWorldEngines.get(
                        level,
                        activePack,
                        activeDimensionKey,
                        seedOverride,
                        generationMode
                );
                requireCompletedShutdown(created);
                if (created.isClosed() || created.getComplex() == null) {
                    throw new IllegalStateException("Iris generator '" + dimensionKey
                            + "' created an engine without a ready biome complex");
                }
                engine = created;
                bindGenerationRuntimeRetirementListener(created);
                boundLevel = level;
                immutablePackRoot = created.getData().getDataFolder().toPath().toAbsolutePath().normalize();
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
                Set<String> retained = retainedBiomeKeys(current);
                if (!retained.isEmpty()) {
                    configuredStructureBiomeKeys = retained;
                    return retained;
                }
                try (GenerationSessionLease lease = current.acquireGenerationLease("modded_configured_biome_keys");
                     IrisContext.Scope ignored = IrisContext.open(current, lease.sessionId(), null)) {
                    Set<String> resolved = ModdedDimensionMetadata.collectConfiguredBiomeKeys(current);
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

    static Set<String> retainedBiomeKeys(Engine engine) {
        if (!(engine instanceof IrisEngine irisEngine)) {
            return Set.of();
        }
        GenerationHistoryRuntimeRouter router = irisEngine.getGenerationHistoryRuntimeRouter().orElse(null);
        if (router == null) {
            return Set.of();
        }
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        Collection<GenerationEpoch> epochs = router.history().manifest().epochs();
        for (GenerationEpoch epoch : epochs) {
            GenerationRegistryContract contract = epoch.registryContract();
            for (GenerationRegistryContract.PhysicalResourceKey key : contract.definitions().keySet()) {
                if (GenerationRegistryContractFactory.BIOME_REGISTRY.equals(key.registryKey())) {
                    keys.add(key.resourceKey());
                }
            }
        }
        return Set.copyOf(keys);
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
            Path packRoot = immutablePackRoot;
            File packDirectory;
            if (packRoot == null) {
                PackValidationRegistry.requireLoadable(activePack);
                packDirectory = ModdedWorldEngines.resolvePack(activePack, activeDimensionKey);
            } else {
                PackValidationRegistry.requireLoadable(packRoot);
                packDirectory = packRoot.toFile();
            }
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

    public void prepareRuntimeHotload(ServerLevel level, Engine current) {
        if (this.engine != current || level.getChunkSource().getGenerator() != this) {
            throw new IllegalStateException("Iris generator '" + dimensionKey
                    + "' cannot prepare caches for an unrelated engine hotload");
        }
        resetRuntimeCaches();
        nativeStructures.installVolumeIndex(level, current);
    }

    public static void requireStructureBiomeUniverseCompatible(
            IrisDimension active,
            IrisDimension replacement
    ) {
        IrisData activeData = Objects.requireNonNull(active.getLoader(),
                "Active Iris dimension has no pack loader");
        IrisData replacementData = Objects.requireNonNull(replacement.getLoader(),
                "Replacement Iris dimension has no pack loader");
        requireStructureBiomeUniverseCompatible(
                IrisModdedBiomeSource.collectStructureBiomeKeys(active, activeData),
                IrisModdedBiomeSource.collectStructureBiomeKeys(replacement, replacementData));
    }

    static void requireStructureBiomeUniverseCompatible(Set<String> active, Set<String> replacement) {
        if (active.equals(replacement)) {
            return;
        }
        throw new IllegalArgumentException("Iris cannot hotload a changed host structure-biome derivative set "
                + "from " + new TreeSet<>(active) + " to " + new TreeSet<>(replacement)
                + ". Restart the server so Minecraft can rebuild its immutable structure state.");
    }

    private void resetRuntimeCaches() {
        configuredStructureBiomeKeys = null;
        structureBiomeSource.clearCaches();
        importedFeatures.invalidate();
        nativeStructures.clearWorldCheckStructureShifts();
        spawnTables.resetVanillaSpawnBiomes();
    }

    @Override
    public WeightedList<MobSpawnSettings.SpawnerData> getMobsAt(
            Holder<Biome> biome, StructureManager structureManager, MobCategory category, BlockPos pos) {
        Engine current = engine();
        NativeBiomeSpawnSelection selection = NativeBiomeSpawnSelection.at(current, pos.getX(), pos.getY(), pos.getZ(),
                biome.unwrapKey().map(key -> key.identifier().toString()).orElse(""));
        if (selection.mode() == NativeBiomeSpawnSelection.Mode.LOADING) {
            return WeightedList.of(List.of());
        }
        try (GenerationHistoryRuntimeRouter.CoordinateScope historyScope = openHistoryCoordinateScope(
                     current, pos.getX(), pos.getZ(), "modded_mob_spawn_table");
             GenerationSessionLease lease = requireGenerationLease(current, "modded_mob_spawn_table");
             IrisContext.Scope ignored = IrisContext.open(current, lease.sessionId(), null)) {
            WeightedList<MobSpawnSettings.SpawnerData> explicitSpawns =
                    biome.value().getMobSettings().getMobs(category);
            WeightedList<MobSpawnSettings.SpawnerData> resolvedSpawns = super.getMobsAt(
                    biome, structureManager, category, pos);
            if (resolvedSpawns != explicitSpawns) {
                return resolvedSpawns;
            }

            Registry<Biome> registry = structureManager.registryAccess().lookupOrThrow(Registries.BIOME);
            Holder<Biome> vanillaSpawnBiome;
            if (selection.mode() == NativeBiomeSpawnSelection.Mode.RETAINED) {
                vanillaSpawnBiome = spawnTables.resolveBiomeHolder(registry, selection.derivativeKey());
            } else if (selection.mode() == NativeBiomeSpawnSelection.Mode.CURRENT) {
                spawnTables.initializeVanillaSpawnBiomes(registry);
                vanillaSpawnBiome = spawnTables.vanillaSpawnBiome(biome.value());
            } else {
                vanillaSpawnBiome = null;
            }
            if (vanillaSpawnBiome == null) {
                return explicitSpawns;
            }

            WeightedList<MobSpawnSettings.SpawnerData> vanillaSpawns =
                    vanillaSpawnBiome.value().getMobSettings().getMobs(category);
            if (explicitSpawns.isEmpty()) {
                return vanillaSpawns;
            }
            if (vanillaSpawns.isEmpty()) {
                return explicitSpawns;
            }

            return spawnTables.mergedSpawnTable(
                    current,
                    biome.value(),
                    vanillaSpawnBiome.value(),
                    category,
                    vanillaSpawns,
                    explicitSpawns
            );
        }
    }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender,
                                                       StructureManager structureManager, ChunkAccess chunk) {
        Engine current = engine();
        ChunkPos chunkPos = chunk.getPos();
        try (GenerationHistoryRuntimeRouter.RuntimeRoute route = openHistoryRoute(
                     current, chunkPos.x(), chunkPos.z(), "modded_create_biomes");
             GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope runtimeScope = openHistoryRuntimeScope(route);
             GenerationSessionLease lease = requireGenerationLease(current, "modded_create_biomes");
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
        GenerationHistoryRuntimeRouter.RuntimeRoute route = openHistoryRoute(
                generationEngine, pos.x(), pos.z(), "modded_chunk_pipeline");

        try {
            if (ModdedGenPool.parallelChunkSystem()) {
                try {
                    return CompletableFuture.completedFuture(
                            generateTerrain(chunk, generationEngine, pos, air, route));
                } finally {
                    if (route != null) {
                        route.close();
                    }
                }
            }
            CompletableFuture<ChunkAccess> pipeline = CompletableFuture.supplyAsync(
                    () -> generateTerrain(chunk, generationEngine, pos, air, route),
                    ModdedGenPool.pool());
            return closeRouteOnCompletion(pipeline, route);
        } catch (RuntimeException | Error failure) {
            closeHistoryRoute(route, failure);
            throw failure;
        }
    }

    private ChunkAccess generateTerrain(ChunkAccess chunk, Engine generationEngine, ChunkPos pos,
                                        PlatformBlockState air,
                                        GenerationHistoryRuntimeRouter.RuntimeRoute route) {
        try (GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope runtimeScope = openHistoryRuntimeScope(route);
             GenerationSessionLease lease = generationEngine.acquireGenerationLease("modded_chunk_pipeline");
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
            if (route != null) {
                BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
                route.claimGeneratedSemantics((x, y, z) -> {
                    BlockState state = chunk.getBlockState(position.set(x, dimMinY + y, z));
                    return state.isAir() || state.liquid();
                });
            }
            ModdedNativeTerrainReceipts.persist(chunk, route);
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

    private static CompletableFuture<ChunkAccess> closeRouteOnCompletion(
            CompletableFuture<ChunkAccess> pipeline,
            GenerationHistoryRuntimeRouter.RuntimeRoute route
    ) {
        if (route == null) {
            return pipeline;
        }
        CompletableFuture<ChunkAccess> completion = new CompletableFuture<>();
        pipeline.whenComplete((ChunkAccess chunk, Throwable failure) -> {
            boolean cancelled = isCancellationFailure(failure);
            Throwable completionFailure = failure;
            try {
                route.close();
            } catch (Throwable closeFailure) {
                completionFailure = appendFailure(completionFailure, closeFailure);
            }
            if (completionFailure == null) {
                completion.complete(chunk);
            } else if (cancelled) {
                completion.cancel(false);
            } else {
                completion.completeExceptionally(completionFailure);
            }
        });
        route.detachThread();
        return completion;
    }

    private static void closeHistoryRoute(
            GenerationHistoryRuntimeRouter.RuntimeRoute route,
            Throwable failure
    ) {
        if (route == null) {
            return;
        }
        try {
            route.close();
        } catch (Throwable closeFailure) {
            if (failure != closeFailure) {
                failure.addSuppressed(closeFailure);
            }
        }
    }

    private static Throwable appendFailure(Throwable failure, Throwable closeFailure) {
        if (failure == null) {
            return closeFailure;
        }
        if (failure != closeFailure) {
            failure.addSuppressed(closeFailure);
        }
        return failure;
    }

    private static boolean isCancellationFailure(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current instanceof CancellationException;
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
        Engine current = engine(region.getLevel());
        ChunkPos chunkPos = chunk.getPos();
        try (GenerationHistoryRuntimeRouter.RuntimeRoute route = openHistoryRoute(
                     current, chunkPos.x(), chunkPos.z(), "modded_apply_carvers");
             GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope runtimeScope = openHistoryRuntimeScope(route);
             GenerationSessionLease lease = requireGenerationLease(current, "modded_apply_carvers");
             IrisContext.Scope ignored = IrisContext.open(current, lease.sessionId(), null)) {
        }
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
        Engine current = engine(region.getLevel());
        ChunkPos chunkPos = chunk.getPos();
        try (GenerationHistoryRuntimeRouter.RuntimeRoute route = openHistoryRoute(
                     current, chunkPos.x(), chunkPos.z(), "modded_build_surface");
             GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope runtimeScope = openHistoryRuntimeScope(route);
             GenerationSessionLease lease = requireGenerationLease(current, "modded_build_surface");
             IrisContext.Scope ignored = IrisContext.open(current, lease.sessionId(), null)) {
        }
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        Engine current = engine(level.getLevel());
        ChunkPos chunkPos = chunk.getPos();
        try (GenerationHistoryRuntimeRouter.RuntimeRoute route = openHistoryRoute(
                     current, chunkPos.x(), chunkPos.z(), "modded_biome_decoration");
             GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope runtimeScope = openHistoryRuntimeScope(route);
             GenerationSessionLease lease = requireGenerationLease(current, "modded_biome_decoration");
             IrisContext.Scope ignored = IrisContext.open(current, lease.sessionId(), null)) {
            if (chunk.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES)) {
                return;
            }
            nativeStructures.placeVanillaStructures(level, chunk, structureManager);
            if (!allowsRoutedDiscreteGeneration(
                    current,
                    route,
                    chunkPos,
                    chunk,
                    ChunkStatus.FEATURES
            )) {
                return;
            }
            if (!NativeGenerationWriteGuard.allowsDecoration(current, level, chunkPos)) {
                return;
            }
            importedFeatures.prepare(current);
            // Vanilla's placed-feature pass, on THIS thread and never on ModdedGenPool: the FEATURES chunk
            // step writes into the eight neighbouring chunks and is not parallel-safe. Inert unless the
            // dimension set importedFeatures.enabled.
            importedFeatures.run(level, chunk, current);
        }
    }

    @Override
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState structureState, StructureManager structureManager, ChunkAccess chunk, StructureTemplateManager templateManager, ResourceKey<Level> levelKey) {
        Engine current = engine(levelKey);
        ChunkPos chunkPos = chunk.getPos();
        try (GenerationHistoryRuntimeRouter.RuntimeRoute route = openHistoryRoute(
                     current, chunkPos.x(), chunkPos.z(), "modded_create_structures");
             GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope runtimeScope = openHistoryRuntimeScope(route);
             GenerationSessionLease lease = requireGenerationLease(current, "modded_create_structures");
             IrisContext.Scope ignored = IrisContext.open(current, lease.sessionId(), null)) {
            if (!allowsRoutedDiscreteGeneration(
                    current,
                    route,
                    chunkPos,
                    chunk,
                    ChunkStatus.STRUCTURE_STARTS
            )) {
                return;
            }
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
            ModdedNativeTerrainReceipts.persistStructureActivation(chunk, route);
        }
    }

    @Override
    public void createReferences(WorldGenLevel level, StructureManager structureManager, ChunkAccess chunk) {
        Engine current = engine(level.getLevel());
        ChunkPos chunkPos = chunk.getPos();
        try (GenerationHistoryRuntimeRouter.RuntimeRoute route = openHistoryRoute(
                     current, chunkPos.x(), chunkPos.z(), "modded_create_references");
             GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope runtimeScope = openHistoryRuntimeScope(route);
             GenerationSessionLease lease = requireGenerationLease(current, "modded_create_references");
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
        ChunkPos center = region.getCenter();
        Engine current = engine(region.getLevel());
        try (GenerationHistoryRuntimeRouter.RuntimeRoute route = openHistoryRoute(
                     current, center.x(), center.z(), "modded_spawn_original_mobs");
             GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope runtimeScope = openHistoryRuntimeScope(route);
             GenerationSessionLease lease = requireGenerationLease(current, "modded_spawn_original_mobs");
             IrisContext.Scope ignored = IrisContext.open(current, lease.sessionId(), null)) {
            ChunkAccess centerChunk = region.getChunk(center.x(), center.z());
            if (!allowsRoutedDiscreteGeneration(
                    current,
                    route,
                    center,
                    centerChunk,
                    ChunkStatus.SPAWN
            )) {
                return;
            }
            Registry<Biome> registry = region.registryAccess().lookupOrThrow(Registries.BIOME);
            spawnTables.initializeVanillaSpawnBiomes(registry);
            Holder<Biome> visibleBiome;
            if (current.getDimensionStackContext() == null) {
                visibleBiome = region.getBiome(center.getWorldPosition().atY(region.getMaxY()));
            } else {
                visibleBiome = structureBiomeSource.getVisibleSurfaceBiome(
                        center.getMinBlockX() + 8,
                        center.getMinBlockZ() + 8);
                if (visibleBiome == null) {
                    visibleBiome = region.getBiome(center.getWorldPosition().atY(region.getMaxY()));
                }
            }
            Holder<Biome> vanillaBiome = spawnTables.vanillaSpawnBiome(visibleBiome.value());
            WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
            random.setDecorationSeed(region.getSeed(), center.getMinBlockX(), center.getMinBlockZ());
            NaturalSpawner.spawnMobsForChunkGeneration(
                    region, vanillaBiome == null ? visibleBiome : vanillaBiome, center, random);
        }
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
        try (GenerationHistoryRuntimeRouter.CoordinateScope historyScope = openHistoryCoordinateScope(
                     current, x, z, "modded_base_height");
             GenerationSessionLease lease = current.acquireGenerationLease("modded_base_height");
             IrisContext.Scope ignored = IrisContext.open(current, lease.sessionId(), null)) {
            NativeTerrainHeightCache.Query query = new NativeTerrainHeightCache.Query(
                    current.getCacheID(), x, z, type, heightAccessor.getMinY(), heightAccessor.getHeight());
            OptionalInt resolved = terrainHeights.resolvedHeight(query,
                    () -> resolvedBaseHeight(current, x, z, type, heightAccessor));
            if (resolved.isPresent()) {
                return resolved.getAsInt();
            }
            boolean ignoreFluid = !type.isOpaque().test(Blocks.WATER.defaultBlockState());
            int height = current.getDimensionStackContext() == null
                    ? current.getHeight(x, z, ignoreFluid)
                    : Engine.hostHeight(current, x, z, ignoreFluid);
            return heightAccessor.getMinY() + height + 1;
        } catch (GenerationSessionException e) {
            throw new IllegalStateException("Iris base height query could not acquire its engine runtime.", e);
        }
    }

    private OptionalInt resolvedBaseHeight(Engine current, int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor) {
        Optional<TerrainBoundarySignature> resolved = current.getComplex().resolvedTerrainColumn(x, z);
        return resolved.isPresent()
                ? OptionalInt.of(NativeTransitionColumn.height(resolved.get(), type, heightAccessor))
                : OptionalInt.empty();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
        int minY = heightAccessor.getMinY();
        Engine current = requireDataQueryEngine("base column");
        try (GenerationHistoryRuntimeRouter.CoordinateScope historyScope = openHistoryCoordinateScope(
                     current, x, z, "modded_base_column");
             GenerationSessionLease lease = current.acquireGenerationLease("modded_base_column");
             IrisContext.Scope ignored = IrisContext.open(current, lease.sessionId(), null)) {
            Optional<TerrainBoundarySignature> resolved = current.getComplex().resolvedTerrainColumn(x, z);
            if (resolved.isPresent()) {
                return NativeTransitionColumn.column(resolved.get(), heightAccessor);
            }
            BlockState[] states = new BlockState[heightAccessor.getHeight()];
            BlockState airState = Blocks.AIR.defaultBlockState();
            boolean dimensionStack = current.getDimensionStackContext() != null;
            int surface = dimensionStack
                    ? Engine.hostHeight(current, x, z, true)
                    : current.getHeight(x, z, true);
            int fluid = dimensionStack
                    ? Engine.hostHeight(current, x, z, false)
                    : current.getHeight(x, z, false);
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

    public boolean allowsNativeChunkWrite(int chunkX, int chunkZ) {
        Engine current = engine;
        return current == null || !current.isClosing() && !current.isClosed()
                && current.getComplex().allowsMantleChunkWrite(chunkX, chunkZ);
    }

    private boolean allowsRoutedDiscreteGeneration(
            Engine current,
            GenerationHistoryRuntimeRouter.RuntimeRoute route,
            ChunkPos chunkPos,
            ChunkAccess chunk,
            ChunkStatus stage
    ) {
        if (chunk.getPersistedStatus().isOrAfter(stage)) {
            return false;
        }
        if (route == null) {
            return allowsGenerationHistoryBypass(current);
        }
        return NativeGenerationWriteGuard.allowsPendingStage(current, chunk, stage)
                || current.getComplex().allowsNewGenerationChunk(chunkPos.x(), chunkPos.z());
    }

    private void bindGenerationRuntimeRetirementListener(Engine current) {
        if (!(current instanceof IrisEngine irisEngine)) {
            unbindGenerationRuntimeRetirementListener();
            return;
        }
        IrisEngine previous = retirementListenerEngine;
        if (previous == irisEngine) {
            return;
        }
        if (previous != null) {
            previous.removeGenerationRuntimeRetirementListener(generationRuntimeRetirementListener);
        }
        irisEngine.addGenerationRuntimeRetirementListener(generationRuntimeRetirementListener);
        retirementListenerEngine = irisEngine;
    }

    private void unbindGenerationRuntimeRetirementListener() {
        IrisEngine previous = retirementListenerEngine;
        if (previous == null) {
            return;
        }
        retirementListenerEngine = null;
        previous.removeGenerationRuntimeRetirementListener(generationRuntimeRetirementListener);
    }

    private void retireGenerationRuntimeCaches(int runtimeIdentity) {
        terrainHeights.evictRuntime(runtimeIdentity);
        structureBiomeSource.evictRuntime(runtimeIdentity);
        importedFeatures.evictRuntime(runtimeIdentity);
        spawnTables.evictRuntime(runtimeIdentity);
    }

    private GenerationHistoryRuntimeRouter.RuntimeRoute openHistoryRoute(
            Engine current,
            int chunkX,
            int chunkZ,
            String operation
    ) {
        if (allowsGenerationHistoryBypass(current)) {
            return null;
        }
        GenerationHistoryRuntimeRouter router = requireHistoryRouter(current, operation);
        try {
            return router.openRoute(chunkX, chunkZ);
        } catch (IOException failure) {
            throw new IllegalStateException("Iris " + operation + " could not route chunk "
                    + chunkX + "," + chunkZ + " through generation history.", failure);
        }
    }

    private GenerationHistoryRuntimeRouter.CoordinateScope openHistoryCoordinateScope(
            Engine current,
            int blockX,
            int blockZ,
            String operation
    ) {
        if (allowsGenerationHistoryBypass(current)) {
            return null;
        }
        GenerationHistoryRuntimeRouter router = requireHistoryRouter(current, operation);
        try {
            return router.openCoordinateScope(blockX, blockZ);
        } catch (IOException failure) {
            throw new IllegalStateException("Iris " + operation + " could not route block "
                    + blockX + "," + blockZ + " through generation history.", failure);
        }
    }

    private GenerationHistoryRuntimeRouter requireHistoryRouter(Engine current, String operation) {
        if (!(current instanceof IrisEngine irisEngine)) {
            throw new IllegalStateException("Iris " + operation + " requires an IrisEngine runtime.");
        }
        return irisEngine.getGenerationHistoryRuntimeRouter().orElseThrow(() ->
                new IllegalStateException("Iris " + operation
                        + " requires an attached generation-history runtime router."));
    }

    boolean allowsGenerationHistoryBypass(Engine current) {
        if (generationMode != ModdedGenerationMode.TRANSIENT_STUDIO) {
            return false;
        }
        if (current instanceof IrisEngine irisEngine && irisEngine.isStudio()) {
            return true;
        }
        throw new IllegalStateException("Only an explicitly transient Iris Studio engine may bypass "
                + "generation-history routing.");
    }

    private static GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope openHistoryRuntimeScope(
            GenerationHistoryRuntimeRouter.RuntimeRoute route
    ) {
        return route == null ? null : route.openRuntimeScope();
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
