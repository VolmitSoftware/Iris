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

import art.arcane.volmlib.nativelib.terrain.structure.StructureInjectionPolicy;
import art.arcane.volmlib.nativelib.terrain.StructureFrequencyControl;
import art.arcane.volmlib.nativelib.terrain.structure.StructureReferencePolicy;
import java.util.concurrent.Executor;

import art.arcane.volmlib.nativelib.terrain.NativeSpawnBiomePolicy;

import art.arcane.volmlib.nativelib.terrain.NativeSpawnSelection;

import art.arcane.volmlib.nativelib.terrain.NativeBlockColumn;

import art.arcane.volmlib.nativelib.terrain.NativeGenerationLease;

import art.arcane.volmlib.nativelib.terrain.NativeGenerationScope;

import art.arcane.volmlib.nativelib.terrain.NativeGenerationRoute;

import art.arcane.volmlib.nativelib.terrain.NativeModdedBiomePolicy;

import art.arcane.volmlib.nativelib.terrain.NativeBiomeSourceAccess;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeFeatureBiomeSource;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeDimensionRuntime;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeGeneratorOwner;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeGeneratorHandle;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeChunkGeneratorDefinition;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedGeneratorPolicy;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedChunkGenerator;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeGeneratorContext;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedStructureStage;
import art.arcane.iris.structure.nativegen.NativeStructureOwnershipRecord;

import art.arcane.iris.structure.nativegen.IrisStructurePolicy;

import art.arcane.iris.world.history.TerrainNativeBlockKeys;

import art.arcane.volmlib.nativelib.terrain.NativeChunkWritePolicy;

import art.arcane.iris.world.history.TerrainBoundarySignature;
import art.arcane.iris.world.history.NativeBiomeSpawnSelection;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.PackValidationRegistry;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationSessionException;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.structure.nativegen.NativeFeatureGenerationPolicy;
import art.arcane.iris.structure.nativegen.NativeStructureStartPlan;
import art.arcane.iris.world.history.GenerationEpoch;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.world.history.GenerationRegistryContract;
import art.arcane.iris.world.history.GenerationRegistryContractFactory;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.structure.nativegen.IrisImportedStructureControl;
import art.arcane.iris.structure.nativegen.NativeStructureVolumeIndex;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.volmlib.util.hunk.Hunk;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeBiomeResolver;
import art.arcane.iris.modded.service.ModdedChunkUpdateService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

public final class IrisModdedChunkGenerator implements NativeGeneratorOwner, NativeModdedGeneratorPolicy<Engine, NativeStructureStartPlan, NativeStructureOwnershipRecord> {
    // Vanilla-shaped fallback for an unbound generator (matches IrisDimension defaults). getMinY,
    // getSeaLevel and getGenDepth are called from world creation and client screens, so they must
    // answer without disk I/O and without throwing before a level is bound.
    public static final NativeChunkGeneratorDefinition DEFINITION = new NativeChunkGeneratorDefinition(
            "irisworldgen", "iris", IrisModdedChunkGenerator::new);
    private static final ModdedDimensionMetadata.DimensionMetadata UNBOUND_HEIGHTS =
            new ModdedDimensionMetadata.DimensionMetadata(-64, 320, 63);
    public static void startGenPool() {
        ModdedGenPool.start();
    }

    public static void shutdownGenPool() {
        ModdedGenPool.shutdown();
    }

    private final String dimensionKey;
    private final String defaultPack;
    private final String defaultDimensionKey;
    private final ModdedEngineBinding<Engine> engineBinding = new ModdedEngineBinding<>(60L, TimeUnit.SECONDS);
    private ModdedImportedFeatureStage importedFeatures;
    private final NativeModdedChunkGenerator<Engine, NativeStructureStartPlan, NativeStructureOwnershipRecord> nativeGenerator;
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
    private volatile NativeWorld boundLevel;
    private volatile IrisEngine retirementListenerEngine;

    public IrisModdedChunkGenerator(NativeGeneratorContext context) {
        this.dimensionKey = context.dimensionKey();
        int colon = dimensionKey.indexOf(':');
        this.defaultPack = colon >= 0 ? dimensionKey.substring(0, colon) : dimensionKey;
        this.defaultDimensionKey = colon >= 0 ? dimensionKey.substring(colon + 1) : dimensionKey;
        this.activePack = defaultPack;
        this.activeDimensionKey = defaultDimensionKey;
        this.nativeGenerator = new NativeModdedChunkGenerator<>(new NativeModdedChunkGenerator.Options<>(context, this, this, DEFINITION));
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
        NativeWorld level = boundLevel();
        if (level != null) {
            this.generationMode = generationMode;
            repointAndBind(level, pack, packDimensionKey, seed);
            return;
        }
        applyUnboundConfiguration(pack, packDimensionKey, seed, packRoot, generationMode);
    }

    synchronized void repointAndBind(NativeWorld level, String pack, String packDimensionKey, long seed) {
        requireBindingAllowed();
        if (!nativeGenerator.represents(level)) {
            throw new IllegalArgumentException("NativeWorld does not use Iris generator '" + dimensionKey + "'");
        }
        requireGlobalStructureGeneration(
                NativeModdedServer.forWorld(level).generateStructures(), dimensionKey);
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
            nativeGenerator.installVolumeIndex(level, replacement);
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
        NativeWorld level = boundLevel();
        unbindEngine(level == null ? null : level);
    }

    synchronized void unbindEngine(NativeWorld world) {
        unloading = true;
        if (world != null) {
            ModdedWorldEngines.evictOrThrow(world);
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
        this.nativeGenerator.clearCaches();
        this.importedFeatures.invalidate();
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
        this.nativeGenerator.clearCaches();
        this.importedFeatures.invalidate();
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

    private NativeWorld boundLevel() {
        NativeWorld cached = boundLevel;
        if (cached != null) {
            return cached;
        }
        NativeModdedServer server = server();
        if (server == null) {
            return null;
        }
        NativeWorld resolved = resolveBoundLevel(server, server.worlds());
        if (resolved != null) {
            boundLevel = resolved;
        }
        return resolved;
    }

    NativeWorld resolveBoundLevel(NativeModdedServer server, List<NativeWorld> snapshot) {
        for (NativeWorld level : snapshot) {
            if (nativeGenerator.represents(level)) {
                return level;
            }
        }
        NativeWorld overworld = server.overworld();
        return overworld != null && nativeGenerator.represents(overworld) ? overworld : null;
    }

    Engine engine() {
        Engine cached = readyEngine();
        if (cached != null) {
            return cached;
        }
        NativeWorld level = boundLevel();
        if (level == null) {
            throw new IllegalStateException("Iris generator '" + dimensionKey + "' has no bound NativeWorld yet");
        }
        return bindGenerationLevel(level);
    }

    private Engine engine(String levelKey) {
        Engine cached = readyEngine();
        if (cached != null) {
            return cached;
        }
        NativeWorld level = boundLevel;
        if (level == null) {
            level = requirePublishedLevel(server(), levelKey);
        } else {
            requireGeneratorLevel(level, levelKey);
        }
        return bindGenerationLevel(level);
    }

    private Engine engine(NativeWorld generationLevel) {
        Engine cached = readyEngine();
        if (cached != null) {
            return cached;
        }
        NativeWorld level = boundLevel == null ? generationLevel : boundLevel;
        requireGeneratorLevel(level, generationLevel.name());
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

    private Engine bindGenerationLevel(NativeWorld level) {
        bindLevel(level);
        Engine bound = readyEngine();
        if (bound == null) {
            throw new IllegalStateException("Iris generator '" + dimensionKey
                    + "' completed generation binding without a ready engine");
        }
        return bound;
    }

    NativeWorld requirePublishedLevel(NativeModdedServer server, String levelKey) {
        if (server == null) {
            throw new IllegalStateException("Iris generator '" + dimensionKey
                    + "' cannot resolve level '" + levelKey + "': server is unavailable");
        }
        NativeWorld level = server.world(levelKey);
        if (level == null) {
            throw new IllegalStateException("Iris generator '" + dimensionKey
                    + "' has no published NativeWorld for '" + levelKey + "'");
        }
        requireGeneratorLevel(level, levelKey);
        return level;
    }

    private void requireGeneratorLevel(NativeWorld level, String levelKey) {
        if (!levelKey.equals(level.name())) {
            throw new IllegalStateException("Iris generator '" + dimensionKey + "' resolved level '"
                    + level.name() + "' while binding '" + levelKey + "'");
        }
        if (!nativeGenerator.represents(level)) {
            throw new IllegalStateException("Published NativeWorld '" + levelKey
                    + "' does not use Iris generator '" + dimensionKey + "'");
        }
    }

    synchronized void bindLevel(NativeWorld level) {
        if (!nativeGenerator.represents(level)) {
            throw new IllegalArgumentException("NativeWorld does not use Iris generator '" + dimensionKey + "'");
        }
        Engine current = engineIfBound();
        if (NativeDimensionRuntime.sameWorld(boundLevel, level) && current != null && current.getComplex() != null) {
            return;
        }
        requireCompletedShutdown(engine);
        unloading = false;
        Engine bound = bindEngine(level);
        nativeGenerator.installVolumeIndex(level, bound);
        // Bind time: a feature-order cycle is reported here, once, and degrades to features-off. Non-waiting for
        // the same reason as repointAndBind: this method owns the generator monitor.
        importedFeatures.prepareWithoutWaiting(bound);
        ModdedIrisLog.info("Iris bound {}: chunk system {}", level.name(), ModdedGenPool.describeChunkSystem());
    }

    private Engine bindEngine(NativeWorld level) {
        requireBindingAllowed();
        try {
            requireGlobalStructureGeneration(
                    NativeModdedServer.forWorld(level).generateStructures(), dimensionKey);
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
                nativeGenerator.clearCaches();
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
        NativeWorld level = boundLevel();
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
        NativeWorld level = boundLevel();
        if (level != null) {
            return bindEngine(level);
        }
        NativeModdedServer server = server();
        if (server == null) {
            throw new IllegalStateException("Iris generator '" + dimensionKey + "' cannot answer "
                    + operation + " without an active server");
        }
        if (server.isServerThread()) {
            throw new IllegalStateException("Iris generator '" + dimensionKey + "' cannot answer "
                    + operation + " on the server thread before its NativeWorld is bound");
        }
        return awaitStructureEngine();
    }

    long visibleBiomeSeed() {
        long configuredSeed = seedOverride;
        if (configuredSeed != Long.MIN_VALUE) {
            return configuredSeed;
        }
        NativeWorld level = boundLevel();
        if (level != null) {
            return level.seed();
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

    public void prepareRuntimeHotload(NativeWorld level, Engine current) {
        if (this.engine != current || !nativeGenerator.represents(level)) {
            throw new IllegalStateException("Iris generator '" + dimensionKey
                    + "' cannot prepare caches for an unrelated engine hotload");
        }
        resetRuntimeCaches();
        nativeGenerator.installVolumeIndex(level, current);
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
                ModdedBiomePolicy.collectStructureBiomeKeys(active, activeData),
                ModdedBiomePolicy.collectStructureBiomeKeys(replacement, replacementData));
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
        nativeGenerator.clearCaches();
        importedFeatures.invalidate();
    }

    public void updateRegeneratedChunk(int chunkX, int chunkZ) {
        ModdedChunkUpdateService service = ModdedEngineBootstrap.services().service(ModdedChunkUpdateService.class);
        if (service == null) {
            throw new IllegalStateException("Iris chunk update service is unavailable during regeneration");
        }
        service.updateRegeneratedChunk(engine(), boundLevel(), chunkX, chunkZ);
    }

    public NativeBiomeResolver regenBiomeResolver() {
        engine();
        return nativeGenerator.biomeResolver();
    }

    public int getGenDepth() {
        Engine current = engine;
        return current == null || current.isClosed()
                ? heightMetadata().depth()
                : current.getMaxHeight() - current.getMinHeight();
    }

    public int getSeaLevel() {
        Engine current = engine;
        return current == null || current.isClosed()
                ? heightMetadata().seaLevel()
                : current.getMinHeight() + current.getDimension().getFluidHeight();
    }

    public int getMinY() {
        Engine current = engine;
        return current == null || current.isClosed()
                ? heightMetadata().minY()
                : current.getMinHeight();
    }

    public boolean allowsNativeChunkWrite(int chunkX, int chunkZ) {
        Engine current = engine;
        return current == null || !current.isClosing() && !current.isClosed()
                && current.getComplex().allowsMantleChunkWrite(chunkX, chunkZ);
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
        nativeGenerator.evictRuntime(runtimeIdentity);
        importedFeatures.evictRuntime(runtimeIdentity);
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

    public Integer worldCheckStructureShift(String key, int chunkX, int chunkZ) {
        return nativeGenerator.worldCheckStructureShift(key, chunkX, chunkZ);
    }

    @Override
    public NativeGeneratorHandle nativeGenerator() {
        return nativeGenerator;
    }

    @Override
    public <H, S> NativeModdedBiomePolicy<H, S> biomePolicy(NativeBiomeSourceAccess<H, S> source) {
        ModdedBiomePolicy<H, S> policy = new ModdedBiomePolicy<>(source);
        policy.bind(new ModdedBiomePolicy.RuntimeCallbacks(this::structureEngineOrNull, this::awaitStructureEngine,
                this::allowsGenerationHistoryBypass, this::configuredStructureBiomeKeys,
                IrisModdedChunkGenerator::retainedBiomeKeys, this::visibleBiomeSeed));
        return policy;
    }

    @Override
    public FeatureStage<Engine> featureStage(NativeFeatureBiomeSource source) {
        importedFeatures = new ModdedImportedFeatureStage(source);
        importedFeatures.bind(this);
        return importedFeatures;
    }

    @Override
    public NativeModdedStructureStage.Policy<Engine, NativeStructureStartPlan, NativeStructureOwnershipRecord> structures() {
        return new ModdedNativeStructurePolicy(this);
    }

    @Override
    public NativeSpawnBiomePolicy<Engine> spawns() {
        return new ModdedSpawnBiomePolicy(this::engine, this::requireGenerationLease);
    }

    @Override
    public NativeModdedServer server() {
        return ModdedEngineBootstrap.currentServer();
    }

    @Override
    public Engine current() {
        return engine();
    }

    @Override
    public Engine current(NativeWorld world) {
        return engine(world);
    }

    @Override
    public Engine current(String worldKey) {
        return engine(worldKey);
    }

    @Override
    public Engine queryCurrent(String operation) {
        return requireDataQueryEngine(operation);
    }

    @Override
    public NativeGenerationRoute route(Engine current, Position position, String operation) {
        return openHistoryRoute(current, position.x(), position.z(), operation);
    }

    @Override
    public NativeGenerationScope coordinateScope(Engine current, Position position, String operation) {
        return openHistoryCoordinateScope(current, position.x(), position.z(), operation);
    }

    @Override
    public NativeGenerationLease lease(Engine current, String operation) {
        return requireGenerationLease(current, operation);
    }

    @Override
    public NativeGenerationScope context(Engine current, long sessionId) {
        return IrisContext.open(current, sessionId, null);
    }

    @Override
    public NativeChunkWritePolicy chunkWrites(Engine current) {
        return current.getComplex()::allowsMantleChunkWrite;
    }

    @Override
    public boolean allowsNewGeneration(Engine current, int x, int z) {
        return current.getComplex().allowsNewGenerationChunk(x, z);
    }

    @Override
    public boolean historyBypass(Engine current) {
        return allowsGenerationHistoryBypass(current);
    }

    @Override
    public boolean stacked(Engine current) {
        return current.getDimensionStackContext() != null;
    }

    @Override
    public int runtimeId(Engine current) {
        return current.getCacheID();
    }

    @Override
    public int terrainHeight(Engine current, int x, int z, boolean ignoreFluid, boolean host) {
        return host && current.getDimensionStackContext() != null
                ? Engine.hostHeight(current, x, z, ignoreFluid)
                : current.getHeight(x, z, ignoreFluid);
    }

    @Override
    public NativeBlockColumn resolvedColumn(Engine current, int x, int z) {
        return current.getComplex().resolvedTerrainColumn(x, z).map(TerrainBoundarySignature::geometry).orElse(null);
    }

    @Override
    public String placementKey(String key) {
        return TerrainNativeBlockKeys.placementKey(key);
    }

    @Override
    public Terrain generate(Engine current, int chunkX, int chunkZ) throws Exception {
        if (announced.compareAndSet(false, true)) {
            ModdedIrisLog.info("Iris generating {} through IrisModdedChunkGenerator (dim={} first chunk {},{})",
                    dimensionKey, current.getDimension().getLoadKey(), chunkX, chunkZ);
        }
        int minimumY = current.getMinHeight();
        int height = current.getMaxHeight() - minimumY;
        ModdedBlockBuffer blocks = new ModdedBlockBuffer(height, IrisPlatforms.get().registries().air());
        Hunk<NativeBiome> biomes = Hunk.newArrayHunk(16, height, 16);
        current.generate(chunkX << 4, chunkZ << 4, blocks, biomes, false);
        return new Terrain(minimumY, height, blocks);
    }

    @Override
    public void generated(Engine current, int x, int z) {
        ModdedWorldManager.enqueueGenerated(current, x, z);
    }

    @Override
    public void generating(int x, int z) {
        lastChunkGenAt = System.currentTimeMillis();
        ModdedIrisLog.debug("Iris generating chunk {},{}", x, z);
    }

    @Override
    public RuntimeException generationFailure(Engine current, Position chunk, Throwable failure) {
        if (failure instanceof GenerationSessionException session && (current.isClosing() || session.isExpectedTeardown())) {
            ModdedIrisLog.debug("Iris chunk {},{} skipped: engine sealed for hotload/teardown", chunk.x(), chunk.z());
            return new IllegalStateException("Iris chunk generation was rejected during an engine transition.", failure);
        }
        ModdedIrisLog.error("Iris failed to generate chunk {},{}", chunk.x(), chunk.z(), failure);
        return new IllegalStateException("Iris generation failed for chunk " + chunk.x() + "," + chunk.z(), failure);
    }

    @Override
    public byte[] terrainReceipt(NativeGenerationRoute nativeRoute) throws IOException {
        GenerationHistoryRuntimeRouter.RuntimeRoute route = (GenerationHistoryRuntimeRouter.RuntimeRoute) nativeRoute;
        return ModdedNativeTerrainReceipts.encode(route);
    }

    @Override
    public long structureActivation(NativeGenerationRoute route) {
        return ((GenerationHistoryRuntimeRouter.RuntimeRoute) route).activation().activationId();
    }

    @Override
    public NativeGenerationLease terrainLease(Engine current) throws GenerationSessionException {
        return current.acquireGenerationLease("modded_chunk_pipeline");
    }

    @Override
    public NativeGenerationLease queryLease(Engine current, String operation) throws GenerationSessionException {
        return current.acquireGenerationLease("modded_" + operation.replace(' ', '_'));
    }

    @Override
    public RuntimeException queryFailure(String operation, Throwable failure) {
        if (failure instanceof GenerationSessionException) {
            return new IllegalStateException("Iris " + operation + " query could not acquire its engine runtime.", failure);
        }
        if (failure instanceof RuntimeException runtime) {
            return runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("Iris " + operation + " query failed.", failure);
    }

    @Override
    public void addDebugInformation(List<String> info) {
        info.add("Iris dimension: " + dimensionKey);
    }

    @Override
    public StructureInjectionPolicy<NativeStructureStartPlan> injectionPolicy(Engine current) {
        return new IrisStructurePolicy(current);
    }

    @Override
    public boolean parallelChunkSystem() {
        return ModdedGenPool.parallelChunkSystem();
    }

    @Override
    public Executor executor() {
        return ModdedGenPool.pool();
    }

    @Override
    public NativeSpawnSelection spawnSelection(Engine current, SpawnQuery query) {
        return NativeBiomeSpawnSelection.at(current, query.x(), query.y(), query.z(), query.visibleBiomeKey());
    }

    @Override
    public StructureReferencePolicy<NativeStructureStartPlan, NativeStructureOwnershipRecord> structurePolicy(Engine current) {
        return new IrisStructurePolicy(current);
    }

    @Override
    public StructureFrequencyControl structureFrequencies() {
        return configuredImportedStructures();
    }

    @Override
    public int depth() {
        return getGenDepth();
    }

    @Override
    public int seaLevel() {
        return getSeaLevel();
    }

    @Override
    public int minimumY() {
        return getMinY();
    }

    @Override
    public int spawnHeight(int minimumY, int height) {
        return ModdedDimensionMetadata.clampSpawnHeight(minimumY, height);
    }

}
