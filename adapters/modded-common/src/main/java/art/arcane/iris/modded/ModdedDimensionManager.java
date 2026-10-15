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

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldTeleport;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldGenerators;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeDimensionRuntime;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;


import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedServerAccess;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.PackValidationRegistry;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.world.history.GenerationActivation;
import art.arcane.iris.generation.terrain.IrisDimension;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ModdedDimensionManager {
    private static final Object LOCK = new Object();
    private static final ConcurrentHashMap<String, Handle> HANDLES = new ConcurrentHashMap<>();
    private static volatile NativeDimensionRuntime access;

    private ModdedDimensionManager() {
    }

    public static synchronized NativeDimensionRuntime bindAccess(ModdedServerAccess serverAccess) {
        NativeDimensionRuntime previous = access;
        access = new NativeDimensionRuntime(serverAccess);
        return previous;
    }

    public static synchronized void restoreAccess(NativeDimensionRuntime serverAccess) {
        access = serverAccess;
    }

    public static void clear() {
        HANDLES.clear();
    }

    public static Handle handle(String dimensionId) {
        return HANDLES.get(dimensionId);
    }

    public static List<Handle> handles() {
        return new ArrayList<>(HANDLES.values());
    }

    public static NativeWorld level(NativeModdedServer server, String dimensionId) {
        Handle handle = HANDLES.get(dimensionId);
        if (handle != null && server.owns(handle.level())) {
            return handle.level();
        }
        // Server thread only (create/remove hold LOCK, teleport and the primary-world router tick, command
        // handlers). Off-thread callers must use the published world snapshot instead of the live map.
        return server.liveWorld(dimensionId);
    }

    public static Engine engine(NativeModdedServer server, String dimensionId) {
        NativeWorld level = level(server, dimensionId);
        if (level == null) {
            return null;
        }
        IrisModdedChunkGenerator generator = NativeWorldGenerators.find(level, IrisModdedChunkGenerator.class);
        if (generator == null) {
            return null;
        }
        return generator.commandEngine();
    }

    public static Handle createTransientStudio(
            NativeModdedServer server,
            String dimensionId,
            String pack,
            String packDimensionKey,
            long seed
    ) {
        return create(
                server,
                new DimensionRequest(dimensionId, pack, packDimensionKey, seed),
                () -> transientStudioPack(pack, packDimensionKey)
        );
    }

    static PendingInjection beginInjection(
            NativeModdedServer server,
            ModdedDimensionRegistryStore.PersistentDimension dimension,
            ModdedGenerationHistoryStorage.ActivePack active,
            ModdedGenerationMode generationMode
    ) {
        NativeDimensionRuntime serverAccess = requireAccess();
        DimensionRequest requested = new DimensionRequest(dimension.id(), dimension.pack(), dimension.dimension(), dimension.seed());
        synchronized (LOCK) {
            Handle present = presentHandle(server, serverAccess, requested);
            if (present != null) {
                return new PendingInjection(present, true);
            }
            try {
                RuntimePack runtimePack = persistentRuntimePack(active, generationMode);
                return new PendingInjection(createLevel(server, serverAccess, requested, runtimePack), false);
            } catch (Throwable e) {
                throw injectionFailure(requested, e);
            }
        }
    }

    static Handle completeInjection(NativeModdedServer server, PendingInjection pending, Engine engine) {
        if (pending.present()) {
            return pending.handle();
        }
        NativeDimensionRuntime serverAccess = requireAccess();
        synchronized (LOCK) {
            try {
                return register(publishLevel(server, serverAccess, pending.handle(), engine));
            } catch (Throwable e) {
                throw injectionFailure(DimensionRequest.of(pending.handle()), e);
            }
        }
    }

    static void abandonInjection(NativeModdedServer server, PendingInjection pending, Engine engine, Throwable failure) {
        if (engine != null) {
            try {
                ModdedWorldEngines.closeUnregistered(engine);
            } catch (Throwable cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
                ModdedIrisLog.error("Iris failed to close the abandoned engine for '{}'",
                        pending.handle().dimensionId(), cleanupFailure);
            }
        }
        if (pending.present()) {
            return;
        }
        NativeDimensionRuntime serverAccess = requireAccess();
        Handle handle = pending.handle();
        synchronized (LOCK) {
            rollbackInjection(server, serverAccess, handle.dimensionId(), handle.level(), handle.generator(), false, failure);
        }
    }

    static PendingInjection beginCreation(
            NativeModdedServer server,
            ModdedDimensionRegistryStore.PersistentDimension dimension,
            ModdedGenerationHistoryStorage.ActivePack active
    ) {
        synchronized (LOCK) {
            if (ModdedDimensionRegistryStore.get(server, dimension.id()) != null) {
                throw new IllegalStateException(alreadyExists(dimension.id()));
            }
            ModdedDimensionRegistryStore.put(server, dimension);
            try {
                return beginInjection(server, dimension, active, ModdedGenerationMode.PERSISTENT_CREATE);
            } catch (RuntimeException | Error failure) {
                forgetCreation(server, dimension.id(), failure);
                throw failure;
            }
        }
    }

    static Handle completeCreation(NativeModdedServer server, PendingInjection pending, Engine engine) {
        try {
            return completeInjection(server, pending, engine);
        } catch (RuntimeException | Error failure) {
            forgetCreation(server, pending.handle().dimensionId(), failure);
            throw failure;
        }
    }

    static void abandonCreation(NativeModdedServer server, PendingInjection pending, Engine engine, Throwable failure) {
        abandonInjection(server, pending, engine, failure);
        forgetCreation(server, pending.handle().dimensionId(), failure);
    }

    static String alreadyExists(String dimensionId) {
        return "Iris dimension '" + dimensionId
                + "' already exists. Use the Iris world update command to stage a pack update.";
    }

    private static void forgetCreation(NativeModdedServer server, String dimensionId, Throwable failure) {
        try {
            ModdedDimensionRegistryStore.remove(server, dimensionId);
        } catch (Throwable rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private static Handle create(NativeModdedServer server, DimensionRequest requested, RuntimePackSource source) {
        NativeDimensionRuntime serverAccess = requireAccess();
        synchronized (LOCK) {
            RuntimePack runtimePack = source.open();
            Handle present = presentHandle(server, serverAccess, requested);
            if (present != null) {
                return present;
            }
            try {
                return register(publishLevel(server, serverAccess,
                        createLevel(server, serverAccess, requested, runtimePack), null));
            } catch (Throwable e) {
                throw injectionFailure(requested, e);
            }
        }
    }

    private static Handle presentHandle(NativeModdedServer server, NativeDimensionRuntime serverAccess, DimensionRequest requested) {
        String dimensionId = requested.dimensionId();
        Handle existing = HANDLES.get(dimensionId);
        if (existing != null && serverAccess.hasLevel(server, dimensionId)) {
            Handle refreshed = requested.bind(existing.level(), existing.generator());
            HANDLES.put(dimensionId, refreshed);
            return refreshed;
        }
        if (!serverAccess.hasLevel(server, dimensionId)) {
            return null;
        }
        NativeWorld present = level(server, dimensionId);
        IrisModdedChunkGenerator generator = NativeWorldGenerators.find(present, IrisModdedChunkGenerator.class);
        if (generator == null) {
            throw new IllegalStateException("Iris cannot inject dimension '" + dimensionId + "': a non-Iris level with that id is already loaded");
        }
        ModdedIrisLog.warn("Iris dimension '{}' is already present in the running server; reusing it", dimensionId);
        Handle handle = requested.bind(present, generator);
        HANDLES.put(dimensionId, handle);
        return handle;
    }

    private static Handle register(Handle handle) {
        HANDLES.put(handle.dimensionId(), handle);
        ModdedIrisLog.info("Iris injected runtime dimension '{}' (pack={} dim={} seed={})",
                handle.dimensionId(), handle.pack(), handle.packDimensionKey(), handle.seed());
        return handle;
    }

    private static IllegalStateException injectionFailure(DimensionRequest requested, Throwable failure) {
        ModdedIrisLog.error("Iris failed to inject runtime dimension '{}' (pack={} dim={} seed={})",
                requested.dimensionId(), requested.pack(), requested.packDimensionKey(), requested.seed(), failure);
        return new IllegalStateException("Iris runtime dimension injection failed for " + requested.dimensionId(), failure);
    }

    public static UpdateResult stagePersistentUpdate(
            NativeModdedServer server,
            String dimensionId,
            String pack,
            String packDimensionKey
    ) {
        ModdedDimensionCreations.requireNotCreating(dimensionId);
        synchronized (LOCK) {
            ModdedDimensionRegistryStore.PersistentDimension previous =
                    ModdedDimensionRegistryStore.get(server, dimensionId);
            if (previous == null) {
                throw new IllegalStateException("Iris dimension '" + dimensionId
                        + "' is not registered. Create it before staging an update.");
            }
            ModdedDimensionRegistryStore.PersistentDimension updated =
                    new ModdedDimensionRegistryStore.PersistentDimension(
                            dimensionId,
                            pack,
                            packDimensionKey,
                            previous.seed()
                    );
            ModdedDimensionRegistryStore.put(server, updated);
            try {
                ModdedGenerationHistoryStorage.ActivePack active =
                        ModdedGenerationHistoryStorage.createOrStage(
                                server,
                                dimensionId,
                                pack,
                                packDimensionKey,
                                previous.seed()
                        );
                Optional<GenerationActivation> pending = active.history().pendingActivation();
                long activeActivationId = active.history().activeActivation().activationId();
                OptionalLong pendingActivationId = pending.isPresent()
                        ? OptionalLong.of(pending.orElseThrow().activationId())
                        : OptionalLong.empty();
                UpdateResult result = new UpdateResult(
                        pending.isPresent(),
                        pending.isPresent(),
                        activeActivationId,
                        pendingActivationId
                );
                if (result.restartRequired()) {
                    ModdedIrisLog.warn("Iris dimension '{}' staged generation activation {} from pack={}:{}. Restart the server to activate it.",
                            dimensionId, pendingActivationId.orElseThrow(), pack, packDimensionKey);
                } else {
                    ModdedIrisLog.info("Iris dimension '{}' already uses pack={}:{}; no generation update was staged.",
                            dimensionId, pack, packDimensionKey);
                }
                return result;
            } catch (Throwable failure) {
                try {
                    ModdedDimensionRegistryStore.put(server, previous);
                } catch (Throwable rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                if (failure instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (failure instanceof Error fatalError) {
                    throw fatalError;
                }
                throw new IllegalStateException("Iris generation update staging failed for "
                        + dimensionId + ".", failure);
            }
        }
    }

    public static boolean removePersistent(NativeModdedServer server, String dimensionId, boolean wipeStorage) {
        ModdedDimensionCreations.requireNotCreating(dimensionId);
        ModdedDimensionRegistryStore.PersistentDimension previous =
                ModdedDimensionRegistryStore.get(server, dimensionId);
        ModdedDimensionRegistryStore.remove(server, dimensionId);
        try {
            return remove(server, dimensionId, wipeStorage);
        } catch (Throwable e) {
            if (previous != null) {
                try {
                    ModdedDimensionRegistryStore.put(server, previous);
                } catch (Throwable rollbackFailure) {
                    e.addSuppressed(rollbackFailure);
                }
            }
            throw e;
        }
    }

    public static boolean remove(NativeModdedServer server, String dimensionId, boolean wipeStorage) {
        NativeDimensionRuntime serverAccess = requireAccess();
        synchronized (LOCK) {
            String key = dimensionId;
            NativeWorld level = level(server, dimensionId);
            if (level == null) {
                HANDLES.remove(dimensionId);
                if (wipeStorage) {
                    ModdedDimensionStorage.wipe(server, key);
                }
                return false;
            }
            IrisModdedChunkGenerator generator = NativeWorldGenerators.find(level, IrisModdedChunkGenerator.class);
            boolean unloadEventStarted = false;
            try {
                NativeWorldTeleport.evacuate(server, level);
                serverAccess.save(level);
                unloadEventStarted = true;
                ModdedEngineBootstrap.loader().fireDynamicLevelUnload(level);
                if (generator != null) {
                    generator.unbindEngine(level);
                }
                ModdedWorldEngines.evictOrThrow(level);
                serverAccess.remove(server, key);
                // Undo snapshots pin the NativeWorld and could replay into the dead level.
                art.arcane.iris.modded.command.ModdedObjectUndo.forget(level);
                serverAccess.close(level);
                HANDLES.remove(dimensionId);
                if (wipeStorage) {
                    ModdedDimensionStorage.wipe(server, key);
                }
                ModdedIrisLog.info("Iris removed runtime dimension '{}'", dimensionId);
                return true;
            } catch (Throwable e) {
                rollbackRemoval(server, serverAccess, key, level, generator, unloadEventStarted, e);
                ModdedIrisLog.error("Iris failed to remove runtime dimension '{}'", dimensionId, e);
                throw new IllegalStateException("Iris runtime dimension removal failed for " + dimensionId, e);
            }
        }
    }

    private static void rollbackRemoval(NativeModdedServer server, NativeDimensionRuntime serverAccess,
                                        String key, NativeWorld level,
                                        IrisModdedChunkGenerator generator, boolean unloadEventStarted,
        Throwable failure) {
        try {
            if (!unloadEventStarted || !serverAccess.hasLevel(server, key)) {
                return;
            }
            if (generator != null) {
                generator.bindLevel(level);
            }
            ModdedEngineBootstrap.loader().fireDynamicLevelLoad(level);
        } catch (Throwable rollbackFailure) {
            if (rollbackFailure != failure) {
                failure.addSuppressed(rollbackFailure);
            }
            ModdedIrisLog.error("Iris failed to restore the engine for retained runtime dimension '{}'",
                    key, rollbackFailure);
        }
    }

    private static Handle createLevel(
            NativeModdedServer server,
            NativeDimensionRuntime serverAccess,
            DimensionRequest requested,
            RuntimePack runtimePack
    ) {
        String dimensionId = requested.dimensionId();
        String pack = requested.pack();
        String packDimensionKey = requested.packDimensionKey();
        long seed = requested.seed();
        String typeRef = runtimePack.dimensionTypeKey();
        ModdedForcedDatapack.requireRegisteredDimensionType(typeRef,
                serverAccess.hasDimensionType(server, typeRef) ? Optional.of(Boolean.TRUE) : Optional.empty(),
                pack, packDimensionKey);
        String generatorRef = pack.equals(packDimensionKey) ? pack : pack + ":" + packDimensionKey;
        NativeDimensionRuntime.Created<IrisModdedChunkGenerator> created = serverAccess.create(server,
                new NativeDimensionRuntime.Creation<>(dimensionId, typeRef, seed, generatorRef, context -> {
                    IrisModdedChunkGenerator generator = new IrisModdedChunkGenerator(context);
                    generator.repoint(pack, runtimePack.dimensionKey(), seed, runtimePack.packRoot(),
                            runtimePack.generationMode());
                    return generator;
                }));
        try {
            serverAccess.initialize(server, created.world());
            return requested.bind(created.world(), created.generator());
        } catch (Throwable error) {
            rollbackInjection(server, serverAccess, dimensionId, created.world(), created.generator(), false, error);
            throw rethrow(dimensionId, error);
        }
    }

    private static Handle publishLevel(
            NativeModdedServer server,
            NativeDimensionRuntime serverAccess,
            Handle created,
            Engine prepared
    ) {
        NativeWorld level = created.level();
        IrisModdedChunkGenerator generator = created.generator();
        boolean loadEventStarted = false;
        try {
            if (prepared != null) {
                ModdedWorldEngines.installPrepared(level, prepared);
            }
            generator.bindLevel(level);
            NativeWorld previous = serverAccess.publish(server, level);
            if (previous != null) {
                throw new IllegalStateException("Iris cannot inject dimension '" + created.dimensionId()
                        + "': the level was registered concurrently");
            }
            serverAccess.addWorldBorderListener(server, level);
            loadEventStarted = true;
            ModdedEngineBootstrap.loader().fireDynamicLevelLoad(level);
            return created;
        } catch (Throwable error) {
            rollbackInjection(server, serverAccess, created.dimensionId(), level, generator, loadEventStarted, error);
            throw rethrow(created.dimensionId(), error);
        }
    }

    private static RuntimeException rethrow(String dimensionId, Throwable error) {
        if (error instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (error instanceof Error fatalError) {
            throw fatalError;
        }
        return new IllegalStateException("Iris runtime dimension injection failed for " + dimensionId, error);
    }

    private static void rollbackInjection(NativeModdedServer server, NativeDimensionRuntime serverAccess,
                                          String key, NativeWorld level,
                                          IrisModdedChunkGenerator generator, boolean loadEventStarted,
                                          Throwable failure) {
        if (loadEventStarted) {
            try {
                ModdedEngineBootstrap.loader().fireDynamicLevelUnload(level);
            } catch (Throwable cleanupError) {
                failure.addSuppressed(cleanupError);
                ModdedIrisLog.error("Iris failed to publish rollback unload for {}",
                        key, cleanupError);
            }
        }
        try {
            if (serverAccess.hasLevel(server, key)) {
                NativeWorld removed = serverAccess.remove(server, key);
                if (removed != null && !NativeDimensionRuntime.sameWorld(removed, level)) {
                    serverAccess.restore(server, removed);
                    throw new IllegalStateException("Iris injection rollback encountered another registered level for "
                            + key);
                }
            }
        } catch (Throwable cleanupError) {
            failure.addSuppressed(cleanupError);
            ModdedIrisLog.error("Iris failed to remove a partially injected level for {}", key, cleanupError);
        }
        try {
            generator.unbindEngine(level);
        } catch (Throwable cleanupError) {
            failure.addSuppressed(cleanupError);
            ModdedIrisLog.error("Iris failed to close a partially bound engine for {}", key, cleanupError);
        }
        try {
            serverAccess.close(level);
        } catch (Throwable cleanupError) {
            failure.addSuppressed(cleanupError);
            ModdedIrisLog.error("Iris failed to close a partially injected level for {}", key, cleanupError);
        }
    }

    private static NativeDimensionRuntime requireAccess() {
        NativeDimensionRuntime bound = access;
        if (bound == null) {
            throw new IllegalStateException("Iris modded server access is not bound; the loader bootstrap must bind ModdedServerAccess before runtime dimension injection");
        }
        return bound;
    }

    private static RuntimePack persistentRuntimePack(
            ModdedGenerationHistoryStorage.ActivePack active,
            ModdedGenerationMode generationMode
    ) {
        return new RuntimePack(
                active.packRoot(),
                active.dimensionKey(),
                active.dimensionContract().dimensionTypeKey(),
                generationMode
        );
    }

    private static RuntimePack transientStudioPack(String pack, String dimensionKey) {
        ModdedStartup.requirePackForWorldCreation(pack);
        Path packRoot = ModdedWorldEngines.resolvePack(pack, dimensionKey)
                .toPath()
                .toAbsolutePath()
                .normalize();
        PackValidationRegistry.requireLoadable(packRoot);
        IrisData data = IrisData.openDatapackCompiler(packRoot.toFile());
        String dimensionTypeRef;
        try {
            IrisDimension dimension = data.getDimensionLoader().load(dimensionKey, false);
            if (dimension == null) {
                throw new IllegalStateException("Transient Iris Studio pack does not contain dimension '"
                        + dimensionKey + "'.");
            }
            dimensionTypeRef = ModdedWorldgenIds.dimensionTypeRef(pack, dimension);
        } finally {
            data.close();
        }
        return new RuntimePack(
                packRoot,
                dimensionKey,
                dimensionTypeRef,
                ModdedGenerationMode.TRANSIENT_STUDIO
        );
    }

    private record RuntimePack(
            Path packRoot,
            String dimensionKey,
            String dimensionTypeKey,
            ModdedGenerationMode generationMode
    ) {
        private RuntimePack {
            packRoot = Objects.requireNonNull(packRoot, "packRoot").toAbsolutePath().normalize();
            dimensionKey = Objects.requireNonNull(dimensionKey, "dimensionKey");
            dimensionTypeKey = Objects.requireNonNull(dimensionTypeKey, "dimensionTypeKey");
            generationMode = Objects.requireNonNull(generationMode, "generationMode");
        }
    }

    @FunctionalInterface
    private interface RuntimePackSource {
        RuntimePack open();
    }

    private record DimensionRequest(String dimensionId, String pack, String packDimensionKey, long seed) {
        private static DimensionRequest of(Handle handle) {
            return new DimensionRequest(handle.dimensionId(), handle.pack(), handle.packDimensionKey(), handle.seed());
        }

        private Handle bind(NativeWorld level, IrisModdedChunkGenerator generator) {
            return new Handle(dimensionId, pack, packDimensionKey, seed, level, generator);
        }
    }

    public record Handle(String dimensionId, String pack, String packDimensionKey, long seed, NativeWorld level, IrisModdedChunkGenerator generator) {
    }

    record PendingInjection(Handle handle, boolean present) {
        PendingInjection {
            handle = Objects.requireNonNull(handle, "handle");
        }
    }

    public record UpdateResult(
            boolean pending,
            boolean restartRequired,
            long activeActivationId,
            OptionalLong pendingActivationId
    ) {
        public UpdateResult {
            pendingActivationId = Objects.requireNonNull(
                    pendingActivationId,
                    "pendingActivationId"
            );
            if (pending != pendingActivationId.isPresent()) {
                throw new IllegalArgumentException("Pending update status must match the pending activation ID.");
            }
            if (restartRequired != pending) {
                throw new IllegalArgumentException("A pending generation activation requires a restart.");
            }
        }
    }
}
