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

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.pack.PackValidationRegistry;
import art.arcane.iris.engine.IrisEngine;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineTarget;
import art.arcane.iris.engine.history.GenerationActivation;
import art.arcane.iris.engine.history.GenerationEpoch;
import art.arcane.iris.engine.history.GenerationEpochContractFactory;
import art.arcane.iris.engine.history.GenerationHistory;
import art.arcane.iris.engine.history.GenerationAdmission;
import art.arcane.iris.engine.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.engine.history.IrisBoundarySignatureSampler;
import art.arcane.iris.engine.history.TransitionGenerationPlan;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisDimensionRuntimeContract;
import art.arcane.iris.engine.object.IrisWorld;
import art.arcane.iris.modded.command.ModdedGuiHost;
import art.arcane.iris.spi.IrisPlatforms;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class ModdedWorldEngines {
    private static final ConcurrentHashMap<ServerLevel, Engine> ENGINES = new ConcurrentHashMap<>();

    private ModdedWorldEngines() {
    }

    public static Engine get(
            ServerLevel level,
            String pack,
            String dimensionKey,
            long seedOverride,
            ModdedGenerationMode generationMode
    ) {
        Engine existing = ENGINES.get(level);
        if (existing != null) {
            return existing;
        }
        return ENGINES.computeIfAbsent(level,
                (ServerLevel l) -> createAndApplyWorldBoundary(
                        l,
                        pack,
                        dimensionKey,
                        seedOverride,
                        generationMode
                ));
    }

    public static Collection<Engine> activeEngines() {
        return new ArrayList<>(ENGINES.values());
    }

    public static void evict(ServerLevel level) {
        try {
            evictOrThrow(level);
        } catch (Throwable e) {
            ModdedIrisLog.error("Iris engine evict close failed for {}", level.dimension().identifier(), e);
        }
    }

    static void evictOrThrow(ServerLevel level) {
        Engine[] removed = new Engine[1];
        ENGINES.computeIfPresent(level, (ServerLevel ignored, Engine current) -> {
            close(current);
            removed[0] = current;
            return null;
        });
        if (removed[0] == null) {
            return;
        }
        // The GUI host holds strong Engine/ServerLevel references with no other remove path.
        ModdedGuiHost.unbind(removed[0]);
        ModdedIrisLog.info("Iris engine evicted for {}", level.dimension().identifier());
    }

    static Engine prepareReplacement(
            ServerLevel level,
            String pack,
            String dimensionKey,
            long seedOverride,
            ModdedGenerationMode generationMode
    ) {
        return create(level, pack, dimensionKey, seedOverride, generationMode);
    }

    static void installReplacement(ServerLevel level, Engine replacement) {
        ServerLevel activeLevel = Objects.requireNonNull(level);
        Engine activeReplacement = Objects.requireNonNull(replacement);
        ENGINES.compute(activeLevel, (ServerLevel ignored, Engine current) -> {
            if (current != null && current != activeReplacement) {
                close(current);
                ModdedGuiHost.unbind(current);
            }
            return activeReplacement;
        });
    }

    static void closeUnregistered(Engine engine) {
        close(engine);
        ENGINES.entrySet().removeIf((Map.Entry<ServerLevel, Engine> entry) -> entry.getValue() == engine);
        ModdedGuiHost.unbind(engine);
    }

    private static Engine createAndApplyWorldBoundary(
            ServerLevel level,
            String pack,
            String dimensionKey,
            long seedOverride,
            ModdedGenerationMode generationMode
    ) {
        Engine engine = create(level, pack, dimensionKey, seedOverride, generationMode);
        try {
            engine.getPlatformHooks().applyWorldBoundary(engine);
            return engine;
        } catch (Throwable failure) {
            try {
                close(engine);
            } catch (Throwable cleanupError) {
                failure.addSuppressed(cleanupError);
            }
            if (failure instanceof Error fatal) {
                throw fatal;
            }
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to apply Iris world boundary for '"
                    + level.dimension().identifier() + "'.", failure);
        }
    }

    private static Engine create(
            ServerLevel level,
            String pack,
            String dimensionKey,
            long seedOverride,
            ModdedGenerationMode generationMode
    ) {
        ModdedEngineBootstrap.bind();
        long seed = seedOverride == Long.MIN_VALUE ? level.getSeed() : seedOverride;
        ModdedGenerationMode requiredMode = Objects.requireNonNull(generationMode, "generationMode");
        if (requiredMode == ModdedGenerationMode.TRANSIENT_STUDIO) {
            return createTransientStudioEngine(level, pack, dimensionKey, seed);
        }
        ModdedGenerationHistoryStorage.ActivePack active = ModdedGenerationHistoryStorage.openOrAdopt(
                level,
                pack,
                dimensionKey,
                seed
        );
        try {
            return createHistoryEngine(level, seed, active);
        } catch (IOException failure) {
            throw new IllegalStateException("Iris generation history could not initialize runtime routing for '"
                    + level.dimension().identifier() + "'.", failure);
        }
    }

    private static Engine createHistoryEngine(
            ServerLevel level,
            long seed,
            ModdedGenerationHistoryStorage.ActivePack opened
    ) throws IOException {
        GenerationHistory history = opened.history();
        try (GenerationAdmission.RuntimeLease startupAdmission = history.retainRuntime()) {
            history.prepareCurrentGenerator(IrisSettings.get().getGenerator().getGenerationTransitionWidthBlocks());
            opened = ModdedGenerationHistoryStorage.resolveActive(history);
            long openedActivationId = history.activeActivation().activationId();
            IrisEngine candidate = buildEngine(level, seed, opened);
            boolean ready = false;
            try {
                GenerationHistoryRuntimeRouter router = candidate.attachGenerationHistory(
                        history,
                        IrisBoundarySignatureSampler.INSTANCE,
                        IrisSettings.get().getGenerator().getGenerationTransitionWidthBlocks()
                );
                router.preloadActiveRuntimes();
                if (history.activeActivation().activationId() != openedActivationId) {
                    close(candidate);
                    ModdedGenerationHistoryStorage.ActivePack promoted =
                            ModdedGenerationHistoryStorage.resolveActive(history);
                    candidate = buildEngine(level, seed, promoted);
                    router = GenerationHistoryRuntimeRouter.attach(
                            candidate,
                            history,
                            IrisBoundarySignatureSampler.INSTANCE
                    );
                    router.preloadActiveRuntimes();
                }
                requireReady(candidate, level, ModdedGenerationMode.PERSISTENT_RESTORE);
                ready = true;
                ModdedIrisLog.info("Iris engine up for {}: pack={} dim={} seed={} height={}..{} activation={}",
                        level.dimension().identifier(),
                        candidate.getData().getDataFolder().getAbsolutePath(),
                        candidate.getDimension().getLoadKey(),
                        seed,
                        candidate.getDimension().getMinHeight(),
                        candidate.getDimension().getMaxHeight(),
                        history.activeActivation().activationId());
                return candidate;
            } finally {
                if (!ready) {
                    close(candidate);
                }
            }
        }
    }

    private static IrisEngine buildEngine(
            ServerLevel level,
            long seed,
            ModdedGenerationHistoryStorage.ActivePack active
    ) throws IOException {
        File packDir = active.packRoot().toFile();
        PackValidationRegistry.requireLoadable(active.packRoot());
        IrisData data = IrisData.openRuntime(packDir);
        boolean runtimeOwnsData = false;
        try {
            IrisDimension dimension = data.getDimensionLoader().load(active.dimensionKey());
            if (dimension == null) {
                ModdedIrisLog.error("Iris immutable generation pack at {} does not contain dimension '{}' (expected dimensions/{}.json).",
                        packDir.getAbsolutePath(), active.dimensionKey(), active.dimensionKey());
                throw new IllegalStateException("Iris dimension '" + active.dimensionKey()
                        + "' missing from immutable pack " + packDir.getAbsolutePath());
            }

            validateDimensionContract(active.dimensionContract(), dimension, level);
            File worldFolder = DimensionType.getStorageFolder(
                    level.dimension(),
                    level.getServer().getWorldPath(LevelResource.ROOT)
            ).toFile();
            IrisWorld world = buildWorld(level, seed, dimension, worldFolder);
            GenerationHistory history = active.history();
            GenerationActivation activation = history.activeActivation();
            GenerationEpoch epoch = history.activeEpoch();
            TransitionGenerationPlan transitionPlan = activation.isInitial()
                    ? null
                    : history.transitionPlan(activation.activationId());
            IrisEngine engine = new IrisEngine(
                    new EngineTarget(world, dimension, data),
                    IrisEngine.InitializationMode.RUNTIME,
                    history.paths().activationMantleRoot(activation.activationId()),
                    epoch.kernelVersion(),
                    transitionPlan);
            runtimeOwnsData = true;
            return engine;
        } finally {
            if (!runtimeOwnsData) {
                data.close();
            }
        }
    }

    private static IrisEngine createTransientStudioEngine(
            ServerLevel level,
            String pack,
            String dimensionKey,
            long seed
    ) {
        File packDir = resolvePack(pack, dimensionKey);
        PackValidationRegistry.requireLoadable(packDir.toPath());
        IrisData data = IrisData.openRuntime(packDir);
        boolean runtimeOwnsData = false;
        boolean ready = false;
        IrisEngine engine = null;
        try {
            IrisDimension dimension = data.getDimensionLoader().load(dimensionKey);
            if (dimension == null) {
                throw new IllegalStateException("Transient Iris Studio pack does not contain dimension '"
                        + dimensionKey + "'.");
            }
            File worldFolder = DimensionType.getStorageFolder(
                    level.dimension(),
                    level.getServer().getWorldPath(LevelResource.ROOT)
            ).toFile();
            IrisWorld world = buildWorld(level, seed, dimension, worldFolder);
            engine = new IrisEngine(
                    new EngineTarget(world, dimension, data),
                    IrisEngine.InitializationMode.STUDIO
            );
            runtimeOwnsData = true;
            requireReady(engine, level, ModdedGenerationMode.TRANSIENT_STUDIO);
            ready = true;
            ModdedIrisLog.info("Iris transient Studio engine up for {}: pack={} dim={} seed={} height={}..{}",
                    level.dimension().identifier(),
                    packDir.getAbsolutePath(),
                    dimension.getLoadKey(),
                    seed,
                    dimension.getMinHeight(),
                    dimension.getMaxHeight());
            return engine;
        } finally {
            if (!ready && engine != null) {
                close(engine);
            } else if (!runtimeOwnsData) {
                data.close();
            }
        }
    }

    private static IrisWorld buildWorld(
            ServerLevel level,
            long seed,
            IrisDimension dimension,
            File worldFolder
    ) {
        return IrisWorld.builder()
                .platformIdentity(level.dimension().identifier().toString())
                .name(level.dimension().identifier().toString().replace(':', '_'))
                .seed(seed)
                .worldFolder(worldFolder)
                .minHeight(dimension.getMinHeight())
                .maxHeight(dimension.getMaxHeight())
                .platformWorld(new ModdedPlatformWorld(level))
                .build();
    }

    private static void requireReady(
            IrisEngine engine,
            ServerLevel level,
            ModdedGenerationMode generationMode
    ) {
        if (engine.isClosed() || engine.getComplex() == null) {
            throw new IllegalStateException("Iris engine for " + level.dimension().identifier()
                    + " did not initialize a ready runtime");
        }
        if (generationMode.historyRequired()
                && engine.getGenerationHistoryRuntimeRouter().isEmpty()) {
            throw new IllegalStateException("Persistent Iris engine for " + level.dimension().identifier()
                    + " did not initialize a generation-history runtime router");
        }
        if (!generationMode.historyRequired()
                && (!engine.isStudio() || engine.getGenerationHistoryRuntimeRouter().isPresent())) {
            throw new IllegalStateException("Transient Iris Studio engine for "
                    + level.dimension().identifier() + " has an invalid history mode");
        }
    }

    private static void validateDimensionContract(
            GenerationEpoch.DimensionContract recorded,
            IrisDimension dimension,
            ServerLevel level
    ) {
        GenerationEpoch.DimensionContract loaded = GenerationEpochContractFactory.create(
                dimension,
                dimension.getLoadKey(),
                recorded.dimensionTypeKey()
        );
        if (!recorded.equals(loaded)) {
            throw new IllegalStateException("Immutable Iris dimension contract changed for '"
                    + level.dimension().identifier() + "'.");
        }
        DimensionType actualType = level.dimensionType();
        String actualTypeKey = level.dimensionTypeRegistration().unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse("<unregistered>");
        IrisDimensionRuntimeContract expected = new IrisDimensionRuntimeContract(
                recorded.dimensionTypeKey(),
                recorded.minHeight(),
                recorded.height(),
                recorded.logicalHeight());
        IrisDimensionRuntimeContract actual = new IrisDimensionRuntimeContract(
                actualTypeKey,
                actualType.minY(),
                actualType.height(),
                actualType.logicalHeight());
        String runtimeName = "Modded level '" + level.dimension().identifier() + "'";
        expected.requireExact(runtimeName, actual);
        expected.requireHeight(runtimeName, level.getMinY(), level.getHeight());
    }

    public static File packFolder(String pack) {
        return IrisPlatforms.get().packsFolderNoCreate(pack);
    }

    static File resolvePack(String pack, String dimensionKey) {
        File packDir = packFolder(pack);
        if (packDir.isDirectory()) {
            return packDir;
        }

        ModdedIrisLog.error("===============================================================");
        ModdedIrisLog.error("Iris pack '{}' is not installed.", pack);
        ModdedIrisLog.error("Expected a pack folder at: {}", packDir.getAbsolutePath());
        ModdedIrisLog.error("Install an Iris pack there (the folder must contain dimensions/{}.json) and restart the server.", dimensionKey);
        ModdedIrisLog.error("===============================================================");
        throw new IllegalStateException("Iris pack not installed: " + packDir.getAbsolutePath());
    }

    private static void close(Engine engine) {
        if (engine != null && !engine.isClosed()) {
            engine.close();
        }
    }

    public static void shutdown() {
        Throwable failure = null;
        for (Map.Entry<ServerLevel, Engine> entry : new ArrayList<>(ENGINES.entrySet())) {
            ServerLevel level = entry.getKey();
            Engine engine = entry.getValue();
            try {
                // Latch the generator's unloading flag BEFORE closing (unbindEngine sets it,
                // then evicts): chunk-system drain work running after this stage would
                // otherwise see a closed engine and silently rebuild a fresh engine + Mantle
                // that no teardown stage ever closes, writing plates after the final save.
                if (level.getChunkSource().getGenerator() instanceof IrisModdedChunkGenerator generator) {
                    generator.unbindEngine(level);
                } else {
                    close(engine);
                    if (!ENGINES.remove(level, engine) && ENGINES.containsKey(level)) {
                        throw new IllegalStateException("Iris engine mapping changed during shutdown for "
                                + level.dimension().identifier());
                    }
                }
                ModdedIrisLog.info("Iris engine closed for {}", level.dimension().identifier());
            } catch (Throwable e) {
                ModdedIrisLog.error("Iris engine close failed for {}", level.dimension().identifier(), e);
                if (failure == null) {
                    failure = e;
                } else if (e != failure) {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw new IllegalStateException("One or more Iris engines failed to close; failed mappings were retained", failure);
        }
    }
}
