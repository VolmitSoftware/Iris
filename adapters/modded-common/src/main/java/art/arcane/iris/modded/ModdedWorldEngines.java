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

import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.pack.PackValidationRegistry;
import art.arcane.iris.engine.IrisEngine;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineTarget;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.dimension.IrisDimensionRuntimeContract;
import art.arcane.iris.engine.object.world.IrisWorld;
import art.arcane.iris.modded.command.ModdedGuiHost;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class ModdedWorldEngines {
    private static final ConcurrentHashMap<ServerLevel, Engine> ENGINES = new ConcurrentHashMap<>();

    private ModdedWorldEngines() {
    }

    public static Engine get(ServerLevel level, String pack, String dimensionKey, long seedOverride) {
        Engine existing = ENGINES.get(level);
        if (existing != null) {
            return existing;
        }
        return ENGINES.computeIfAbsent(level,
                (ServerLevel l) -> createAndApplyWorldBoundary(l, pack, dimensionKey, seedOverride));
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

    static Engine prepareReplacement(ServerLevel level, String pack, String dimensionKey, long seedOverride) {
        return create(level, pack, dimensionKey, seedOverride);
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

    private static Engine createAndApplyWorldBoundary(ServerLevel level, String pack,
                                                      String dimensionKey, long seedOverride) {
        Engine engine = create(level, pack, dimensionKey, seedOverride);
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

    private static Engine create(ServerLevel level, String pack, String dimensionKey, long seedOverride) {
        ModdedEngineBootstrap.bind();
        File packDir = resolvePack(pack, dimensionKey);
        PackValidationRegistry.requireLoadable(pack);
        IrisData data = IrisData.openRuntime(packDir);
        IrisDimension dimension = data.getDimensionLoader().load(dimensionKey);
        if (dimension == null) {
            ModdedIrisLog.error("Iris pack '{}' at {} does not contain dimension '{}' (expected dimensions/{}.json). Install a matching Iris pack and restart.",
                    pack, packDir.getAbsolutePath(), dimensionKey, dimensionKey);
            throw new IllegalStateException("Iris dimension '" + dimensionKey + "' missing from pack " + packDir.getAbsolutePath());
        }

        long seed = seedOverride == Long.MIN_VALUE ? level.getSeed() : seedOverride;
        validateDimensionContract(pack, dimensionKey, dimension, level);
        File worldFolder = DimensionType.getStorageFolder(level.dimension(), level.getServer().getWorldPath(LevelResource.ROOT)).toFile();
        IrisWorld world = IrisWorld.builder()
                .platformIdentity(level.dimension().identifier().toString())
                .name(level.dimension().identifier().toString().replace(':', '_'))
                .seed(seed)
                .worldFolder(worldFolder)
                .minHeight(dimension.getMinHeight())
                .maxHeight(dimension.getMaxHeight())
                .platformWorld(new ModdedPlatformWorld(level))
                .build();
        Engine engine = new IrisEngine(
                new EngineTarget(world, dimension, data),
                IrisEngine.InitializationMode.RUNTIME);
        if (engine.isClosed() || engine.getComplex() == null) {
            IllegalStateException failure = new IllegalStateException("Iris engine for "
                    + level.dimension().identifier() + " did not initialize a ready biome complex");
            try {
                close(engine);
            } catch (Throwable cleanupError) {
                failure.addSuppressed(cleanupError);
            }
            throw failure;
        }

        ModdedIrisLog.info("Iris engine up for {}: pack={} dim={} seed={} height={}..{}",
                level.dimension().identifier(), packDir.getAbsolutePath(), dimension.getLoadKey(), seed, dimension.getMinHeight(), dimension.getMaxHeight());
        return engine;
    }

    private static void validateDimensionContract(String pack, String dimensionKey,
                                                  IrisDimension dimension, ServerLevel level) {
        DimensionType actualType = level.dimensionType();
        String actualTypeKey = level.dimensionTypeRegistration().unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse("<unregistered>");
        String legacyTypeKey = "irisworldgen:" + dimension.getDimensionTypeKey();
        String expectedTypeKey = legacyTypeKey.equals(actualTypeKey)
                ? legacyTypeKey
                : ModdedWorldgenIds.dimensionTypeRef(pack, dimensionKey);
        IrisDimensionRuntimeContract expected = new IrisDimensionRuntimeContract(
                expectedTypeKey,
                dimension.getMinHeight(),
                dimension.getMaxHeight() - dimension.getMinHeight(),
                dimension.getLogicalHeight());
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
                // then evicts): late chunk work would otherwise see a closed engine and
                // silently rebuild a fresh engine + Mantle that no teardown stage ever
                // closes, writing plates after the final save.
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
