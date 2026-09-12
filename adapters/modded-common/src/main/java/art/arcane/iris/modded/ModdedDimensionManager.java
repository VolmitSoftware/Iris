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

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.PackValidationRegistry;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.world.history.GenerationActivation;
import art.arcane.iris.generation.terrain.IrisDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModdedDimensionManager {
    private static final int TELEPORT_WARM_RADIUS = 0;
    private static final Object LOCK = new Object();
    private static final ConcurrentHashMap<String, Handle> HANDLES = new ConcurrentHashMap<>();
    private static final TicketType TELEPORT_WARM_TICKET = new TicketType(TicketType.NO_TIMEOUT,
            TicketType.FLAG_LOADING | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);
    private static final long TELEPORT_TIMEOUT_SECONDS = 10L;
    private static volatile ModdedServerAccess access;

    private ModdedDimensionManager() {
    }

    public static synchronized ModdedServerAccess bindAccess(ModdedServerAccess serverAccess) {
        ModdedServerAccess previous = access;
        access = serverAccess;
        return previous;
    }

    public static synchronized void restoreAccess(ModdedServerAccess serverAccess) {
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

    public static ServerLevel level(MinecraftServer server, String dimensionId) {
        Handle handle = HANDLES.get(dimensionId);
        if (handle != null && handle.level().getServer() == server) {
            return handle.level();
        }
        ResourceKey<Level> key = levelKey(dimensionId);
        // Server thread only (create/remove hold LOCK, teleport and the primary-world router tick, command
        // handlers). Off-thread callers must use ModdedServerLevels.level instead of the live map.
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().equals(key)) {
                return level;
            }
        }
        return null;
    }

    public static Engine engine(MinecraftServer server, String dimensionId) {
        ServerLevel level = level(server, dimensionId);
        if (level == null) {
            return null;
        }
        if (!(level.getChunkSource().getGenerator() instanceof IrisModdedChunkGenerator generator)) {
            return null;
        }
        return generator.commandEngine();
    }

    public static Handle createTransientStudio(
            MinecraftServer server,
            String dimensionId,
            String pack,
            String packDimensionKey,
            long seed
    ) {
        return create(
                server,
                dimensionId,
                pack,
                packDimensionKey,
                seed,
                ModdedGenerationMode.TRANSIENT_STUDIO
        );
    }

    static Handle restorePersistent(
            MinecraftServer server,
            String dimensionId,
            String pack,
            String packDimensionKey,
            long seed
    ) {
        return create(
                server,
                dimensionId,
                pack,
                packDimensionKey,
                seed,
                ModdedGenerationMode.PERSISTENT_RESTORE
        );
    }

    private static Handle create(
            MinecraftServer server,
            String dimensionId,
            String pack,
            String packDimensionKey,
            long seed,
            ModdedGenerationMode generationMode
    ) {
        ModdedServerAccess serverAccess = requireAccess();
        synchronized (LOCK) {
            ResourceKey<Level> key = levelKey(dimensionId);
            RuntimePack runtimePack = switch (generationMode) {
                case TRANSIENT_STUDIO -> transientStudioPack(pack, packDimensionKey);
                case PERSISTENT_CREATE -> persistentRuntimePack(
                        ModdedGenerationHistoryStorage.createOrStage(
                                server,
                                key,
                                pack,
                                packDimensionKey,
                                seed
                        ),
                        generationMode
                );
                case PERSISTENT_RESTORE -> persistentRuntimePack(
                        ModdedGenerationHistoryStorage.restoreOrAdopt(
                                server,
                                key,
                                pack,
                                packDimensionKey,
                                seed
                        ),
                        generationMode
                );
            };
            Handle existing = HANDLES.get(dimensionId);
            if (existing != null && serverAccess.hasLevel(server, key)) {
                Handle refreshed = new Handle(dimensionId, pack, packDimensionKey, seed, existing.level(), existing.generator());
                HANDLES.put(dimensionId, refreshed);
                return refreshed;
            }
            if (serverAccess.hasLevel(server, key)) {
                ServerLevel present = level(server, dimensionId);
                if (present == null || !(present.getChunkSource().getGenerator() instanceof IrisModdedChunkGenerator generator)) {
                    throw new IllegalStateException("Iris cannot inject dimension '" + dimensionId + "': a non-Iris level with that id is already loaded");
                }
                ModdedIrisLog.warn("Iris dimension '{}' is already present in the running server; reusing it", dimensionId);
                Handle handle = new Handle(dimensionId, pack, packDimensionKey, seed, present, generator);
                HANDLES.put(dimensionId, handle);
                return handle;
            }

            try {
                Handle handle = inject(
                        server,
                        serverAccess,
                        dimensionId,
                        key,
                        pack,
                        packDimensionKey,
                        seed,
                        runtimePack
                );
                HANDLES.put(dimensionId, handle);
                ModdedIrisLog.info("Iris injected runtime dimension '{}' (pack={} dim={} seed={})", dimensionId, pack, packDimensionKey, seed);
                return handle;
            } catch (Throwable e) {
                ModdedIrisLog.error("Iris failed to inject runtime dimension '{}' (pack={} dim={} seed={})", dimensionId, pack, packDimensionKey, seed, e);
                throw new IllegalStateException("Iris runtime dimension injection failed for " + dimensionId, e);
            }
        }
    }

    public static Handle createPersistent(MinecraftServer server, String dimensionId, String pack, String packDimensionKey, long seed) {
        synchronized (LOCK) {
            ModdedDimensionRegistryStore.PersistentDimension previous =
                    ModdedDimensionRegistryStore.get(server, dimensionId);
            if (previous != null) {
                throw new IllegalStateException("Iris dimension '" + dimensionId
                        + "' already exists. Use the Iris world update command to stage a pack update.");
            }
            ModdedDimensionRegistryStore.put(server, new ModdedDimensionRegistryStore.PersistentDimension(
                    dimensionId,
                    pack,
                    packDimensionKey,
                    seed
            ));
            try {
                return create(
                        server,
                        dimensionId,
                        pack,
                        packDimensionKey,
                        seed,
                        ModdedGenerationMode.PERSISTENT_CREATE
                );
            } catch (Throwable e) {
                try {
                    ModdedDimensionRegistryStore.remove(server, dimensionId);
                } catch (Throwable rollbackFailure) {
                    e.addSuppressed(rollbackFailure);
                }
                if (e instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (e instanceof Error fatalError) {
                    throw fatalError;
                }
                throw new IllegalStateException("Iris runtime dimension injection failed for " + dimensionId, e);
            }
        }
    }

    public static UpdateResult stagePersistentUpdate(
            MinecraftServer server,
            String dimensionId,
            String pack,
            String packDimensionKey
    ) {
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
                                levelKey(dimensionId),
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

    public static boolean removePersistent(MinecraftServer server, String dimensionId, boolean wipeStorage) {
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

    public static boolean remove(MinecraftServer server, String dimensionId, boolean wipeStorage) {
        ModdedServerAccess serverAccess = requireAccess();
        synchronized (LOCK) {
            ResourceKey<Level> key = levelKey(dimensionId);
            ServerLevel level = level(server, dimensionId);
            if (level == null) {
                HANDLES.remove(dimensionId);
                if (wipeStorage) {
                    ModdedDimensionStorage.wipe(server, key);
                }
                return false;
            }
            IrisModdedChunkGenerator generator = level.getChunkSource().getGenerator()
                    instanceof IrisModdedChunkGenerator irisGenerator
                    ? irisGenerator
                    : null;
            boolean unloadEventStarted = false;
            try {
                evacuate(server, level);
                level.save(null, true, false);
                unloadEventStarted = true;
                ModdedEngineBootstrap.loader().fireDynamicLevelUnload(server, level);
                if (generator != null) {
                    generator.unbindEngine(level);
                }
                ModdedWorldEngines.evictOrThrow(level);
                serverAccess.removeLevel(server, key);
                // Undo snapshots pin the ServerLevel and could replay into the dead level.
                art.arcane.iris.modded.command.ModdedObjectUndo.forget(level);
                level.close();
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

    private static void rollbackRemoval(MinecraftServer server, ModdedServerAccess serverAccess,
                                        ResourceKey<Level> key, ServerLevel level,
                                        IrisModdedChunkGenerator generator, boolean unloadEventStarted,
        Throwable failure) {
        try {
            if (!unloadEventStarted || !serverAccess.hasLevel(server, key)) {
                return;
            }
            if (generator != null) {
                generator.bindLevel(level);
            }
            ModdedEngineBootstrap.loader().fireDynamicLevelLoad(server, level);
        } catch (Throwable rollbackFailure) {
            if (rollbackFailure != failure) {
                failure.addSuppressed(rollbackFailure);
            }
            ModdedIrisLog.error("Iris failed to restore the engine for retained runtime dimension '{}'",
                    key.identifier(), rollbackFailure);
        }
    }

    public static CompletableFuture<Boolean> teleportAsync(
            ServerPlayer player,
            MinecraftServer server,
            String dimensionId,
            double x,
            double y,
            double z
    ) {
        return teleportAsync(player, server, dimensionId, x, y, z,
                System.nanoTime() + TimeUnit.SECONDS.toNanos(TELEPORT_TIMEOUT_SECONDS));
    }

    public static CompletableFuture<Boolean> teleportAsync(
            ServerPlayer player,
            MinecraftServer server,
            String dimensionId,
            double x,
            double y,
            double z,
            long deadlineNanos
    ) {
        ServerLevel level = level(server, dimensionId);
        if (level == null) {
            return CompletableFuture.completedFuture(false);
        }
        return teleportAsync(player, server, level, x, y, z, deadlineNanos);
    }

    public static CompletableFuture<Boolean> teleportAsync(
            ServerPlayer player,
            MinecraftServer server,
            ServerLevel level,
            double x,
            double y,
            double z
    ) {
        return teleportAsync(player, server, level, x, y, z, 0L);
    }

    public static CompletableFuture<Boolean> teleportAsync(
            ServerPlayer player,
            MinecraftServer server,
            ServerLevel level,
            double x,
            double y,
            double z,
            long deadlineNanos
    ) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (player == null || server == null || level == null || level.getServer() != server) {
            result.complete(false);
            return result;
        }
        if (!Double.isFinite(x) || !Double.isFinite(z)
                || (y != Double.MIN_VALUE && !Double.isFinite(y))) {
            result.completeExceptionally(new IllegalArgumentException("Teleport coordinates must be finite."));
            return result;
        }
        if (deadlineNanos != 0L) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0L) {
                result.completeExceptionally(teleportTimeout(level, x, z));
                return result;
            }
            result.orTimeout(remainingNanos, TimeUnit.NANOSECONDS);
        }
        UUID playerId = player.getUUID();
        runOnServer(server, () -> beginTeleport(
                result,
                playerId,
                server,
                level,
                x,
                y,
                z,
                deadlineNanos));
        return result;
    }

    private static void beginTeleport(
            CompletableFuture<Boolean> result,
            UUID playerId,
            MinecraftServer server,
            ServerLevel level,
            double x,
            double y,
            double z,
            long deadlineNanos
    ) {
        if (result.isDone()) {
            return;
        }
        if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) {
            result.completeExceptionally(teleportTimeout(level, x, z));
            return;
        }
        ServerLevel active = level(server, level.dimension().identifier().toString());
        if (active != level) {
            result.complete(false);
            return;
        }
        int blockX = ModdedTeleportBounds.blockCoordinate(x);
        int blockZ = ModdedTeleportBounds.blockCoordinate(z);
        ChunkPos chunkPos = new ChunkPos(blockX >> 4, blockZ >> 4);
        if (level.getChunkSource().hasChunk(chunkPos.x(), chunkPos.z())) {
            completeTeleport(result, playerId, server, level, x, y, z, blockX, blockZ, deadlineNanos);
            return;
        }
        warmAndTeleport(result, playerId, server, level, x, y, z, blockX, blockZ, chunkPos, deadlineNanos);
    }

    private static void warmAndTeleport(
            CompletableFuture<Boolean> result,
            UUID playerId,
            MinecraftServer server,
            ServerLevel level,
            double x,
            double y,
            double z,
            int blockX,
            int blockZ,
            ChunkPos chunkPos,
            long deadlineNanos
    ) {
        AtomicBoolean ticketReleased = new AtomicBoolean();
        CompletableFuture<?> chunkLoad;
        try {
            chunkLoad = level.getChunkSource().addTicketAndLoadWithRadius(
                    TELEPORT_WARM_TICKET,
                    chunkPos,
                    TELEPORT_WARM_RADIUS);
        } catch (Throwable failure) {
            result.completeExceptionally(failure);
            return;
        }
        if (chunkLoad == null) {
            releaseTeleportTicket(level, chunkPos, ticketReleased);
            result.completeExceptionally(new IllegalStateException(
                    "Chunk warm returned no completion future for " + level.dimension().identifier()
                            + " at " + chunkPos.x() + "," + chunkPos.z() + "."));
            return;
        }
        result.whenComplete((success, failure) -> runOnServer(server,
                () -> releaseTeleportTicket(level, chunkPos, ticketReleased)));
        chunkLoad.whenComplete((ignored, failure) -> runOnServer(server, () -> {
            releaseTeleportTicket(level, chunkPos, ticketReleased);
            if (result.isDone()) {
                return;
            }
            if (failure != null) {
                result.completeExceptionally(failure);
                return;
            }
            completeTeleport(result, playerId, server, level, x, y, z, blockX, blockZ, deadlineNanos);
        }));
    }

    private static void releaseTeleportTicket(
            ServerLevel level,
            ChunkPos chunkPos,
            AtomicBoolean ticketReleased
    ) {
        if (ticketReleased.compareAndSet(false, true)) {
            level.getChunkSource().removeTicketWithRadius(
                    TELEPORT_WARM_TICKET,
                    chunkPos,
                    TELEPORT_WARM_RADIUS);
        }
    }

    private static void completeTeleport(
            CompletableFuture<Boolean> result,
            UUID playerId,
            MinecraftServer server,
            ServerLevel level,
            double x,
            double y,
            double z,
            int blockX,
            int blockZ,
            long deadlineNanos
    ) {
        if (result.isDone()) {
            return;
        }
        if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) {
            result.completeExceptionally(teleportTimeout(level, x, z));
            return;
        }
        ServerLevel active = level(server, level.dimension().identifier().toString());
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (active != level || player == null) {
            result.complete(false);
            return;
        }
        try {
            int targetY = resolveSafeTeleportY(level, blockX, blockZ, y);
            boolean teleported = player.teleportTo(
                    level,
                    x,
                    targetY,
                    z,
                    Set.<Relative>of(),
                    player.getYRot(),
                    player.getXRot(),
                    false);
            result.complete(teleported);
        } catch (Throwable failure) {
            result.completeExceptionally(failure);
        }
    }

    private static int resolveSafeTeleportY(ServerLevel level, int blockX, int blockZ, double requestedY) {
        int initialY = requestedY == Double.MIN_VALUE
                ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ)
                : (int) Math.floor(requestedY);
        int startY = ModdedTeleportBounds.clampY(level.getMinY(), level.getMaxY(), initialY);
        int maximumY = ModdedTeleportBounds.maximumY(level.getMinY(), level.getMaxY());
        int minimumY = ModdedTeleportBounds.minimumY(level.getMinY(), level.getMaxY());
        for (int candidateY = startY; candidateY <= maximumY; candidateY++) {
            if (isSafeStandingPosition(level, blockX, candidateY, blockZ)) {
                return candidateY;
            }
        }
        for (int candidateY = startY - 1; candidateY >= minimumY; candidateY--) {
            if (isSafeStandingPosition(level, blockX, candidateY, blockZ)) {
                return candidateY;
            }
        }
        throw new IllegalStateException("No safe teleport position exists in "
                + level.dimension().identifier() + " at " + blockX + "," + blockZ + ".");
    }

    private static boolean isSafeStandingPosition(ServerLevel level, int blockX, int blockY, int blockZ) {
        BlockPos feet = new BlockPos(blockX, blockY, blockZ);
        BlockPos head = feet.above();
        BlockPos support = feet.below();
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet).getFluidState().isEmpty()
                && level.getBlockState(head).getCollisionShape(level, head).isEmpty()
                && level.getBlockState(head).getFluidState().isEmpty()
                && !level.getBlockState(support).getCollisionShape(level, support).isEmpty();
    }

    private static TimeoutException teleportTimeout(ServerLevel level, double x, double z) {
        return new TimeoutException("Teleport into " + level.dimension().identifier()
                + " at " + ModdedTeleportBounds.blockCoordinate(x) + ","
                + ModdedTeleportBounds.blockCoordinate(z)
                + " exceeded " + TELEPORT_TIMEOUT_SECONDS + " seconds.");
    }

    private static void runOnServer(MinecraftServer server, Runnable task) {
        if (server.isSameThread()) {
            task.run();
            return;
        }
        server.execute(task);
    }

    private static Holder<DimensionType> resolveDimensionType(
            RegistryAccess registryAccess,
            String pack,
            String packDimensionKey,
            RuntimePack runtimePack
    ) {
        Registry<DimensionType> registry = registryAccess.lookupOrThrow(Registries.DIMENSION_TYPE);
        String typeRef = runtimePack.dimensionTypeKey();
        ResourceKey<DimensionType> typeKey = ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.parse(typeRef));
        return ModdedForcedDatapack.requireRegisteredDimensionType(
                typeRef, registry.get(typeKey), pack, packDimensionKey);
    }

    private static Handle inject(
            MinecraftServer server,
            ModdedServerAccess serverAccess,
            String dimensionId,
            ResourceKey<Level> key,
            String pack,
            String packDimensionKey,
            long seed,
            RuntimePack runtimePack
    ) {
        RegistryAccess registryAccess = server.registryAccess();
        Holder<DimensionType> dimensionType = resolveDimensionType(
                registryAccess,
                pack,
                packDimensionKey,
                runtimePack
        );
        Holder<Biome> plains = registryAccess.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        FixedBiomeSource biomeSource = new FixedBiomeSource(plains);
        String generatorRef = pack.equals(packDimensionKey) ? pack : pack + ":" + packDimensionKey;
        IrisModdedChunkGenerator generator = new IrisModdedChunkGenerator(biomeSource, generatorRef);
        generator.repoint(
                pack,
                runtimePack.dimensionKey(),
                seed,
                runtimePack.packRoot(),
                runtimePack.generationMode()
        );
        LevelStem stem = new LevelStem(dimensionType, generator);

        WorldData worldData = server.getWorldData();
        ServerLevelData overworldData = worldData.overworldData();
        DerivedLevelData derivedLevelData = new DerivedLevelData(worldData, overworldData);

        Executor executor = serverAccess.levelExecutor(server);
        LevelStorageSource.LevelStorageAccess storage = serverAccess.levelStorage(server);
        long obfuscatedSeed = BiomeManager.obfuscateSeed(seed);

        ServerLevel level = new ServerLevel(
                server,
                executor,
                storage,
                derivedLevelData,
                key,
                stem,
                false,
                obfuscatedSeed,
                List.of(),
                false);

        boolean loadEventStarted = false;
        try {
            serverAccess.initializeLevelData(server, level);
            generator.bindLevel(level);
            Handle handle = new Handle(dimensionId, pack, packDimensionKey, seed, level, generator);
            ServerLevel previous = serverAccess.putLevelIfAbsent(server, key, level);
            if (previous != null) {
                throw new IllegalStateException("Iris cannot inject dimension '" + dimensionId
                        + "': the level was registered concurrently");
            }
            server.getPlayerList().addWorldborderListener(level);
            loadEventStarted = true;
            ModdedEngineBootstrap.loader().fireDynamicLevelLoad(server, level);
            return handle;
        } catch (Throwable error) {
            rollbackInjection(server, serverAccess, key, level, generator, loadEventStarted, error);
            if (error instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (error instanceof Error fatalError) {
                throw fatalError;
            }
            throw new IllegalStateException("Iris runtime dimension injection failed for " + dimensionId, error);
        }
    }

    private static void rollbackInjection(MinecraftServer server, ModdedServerAccess serverAccess,
                                          ResourceKey<Level> key, ServerLevel level,
                                          IrisModdedChunkGenerator generator, boolean loadEventStarted,
                                          Throwable failure) {
        if (loadEventStarted) {
            try {
                ModdedEngineBootstrap.loader().fireDynamicLevelUnload(server, level);
            } catch (Throwable cleanupError) {
                failure.addSuppressed(cleanupError);
                ModdedIrisLog.error("Iris failed to publish rollback unload for {}",
                        key.identifier(), cleanupError);
            }
        }
        try {
            if (serverAccess.hasLevel(server, key)) {
                ServerLevel removed = serverAccess.removeLevel(server, key);
                if (removed != null && removed != level) {
                    serverAccess.putLevel(server, key, removed);
                    throw new IllegalStateException("Iris injection rollback encountered another registered level for "
                            + key.identifier());
                }
            }
        } catch (Throwable cleanupError) {
            failure.addSuppressed(cleanupError);
            ModdedIrisLog.error("Iris failed to remove a partially injected level for {}", key.identifier(), cleanupError);
        }
        try {
            generator.unbindEngine(level);
        } catch (Throwable cleanupError) {
            failure.addSuppressed(cleanupError);
            ModdedIrisLog.error("Iris failed to close a partially bound engine for {}", key.identifier(), cleanupError);
        }
        try {
            level.close();
        } catch (Throwable cleanupError) {
            failure.addSuppressed(cleanupError);
            ModdedIrisLog.error("Iris failed to close a partially injected level for {}", key.identifier(), cleanupError);
        }
    }

    public static int evacuate(MinecraftServer server, ServerLevel from) {
        ServerLevel fallback = server.overworld();
        if (fallback == from) {
            return 0;
        }
        BlockPos spawn = fallback.getRespawnData().pos();
        int spawnY = fallback.getHeight(Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
        List<ServerPlayer> players = new ArrayList<>(from.players());
        for (ServerPlayer player : players) {
            player.teleportTo(fallback, spawn.getX() + 0.5D, spawnY, spawn.getZ() + 0.5D, Set.<Relative>of(), player.getYRot(), player.getXRot(), false);
        }
        return players.size();
    }

    private static ResourceKey<Level> levelKey(String dimensionId) {
        Identifier identifier = Identifier.parse(dimensionId);
        return ResourceKey.create(Registries.DIMENSION, identifier);
    }

    private static ModdedServerAccess requireAccess() {
        ModdedServerAccess bound = access;
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
        try {
            IrisDimension dimension = data.getDimensionLoader().load(dimensionKey, false);
            if (dimension == null) {
                throw new IllegalStateException("Transient Iris Studio pack does not contain dimension '"
                        + dimensionKey + "'.");
            }
        } finally {
            data.close();
        }
        return new RuntimePack(
                packRoot,
                dimensionKey,
                ModdedWorldgenIds.dimensionTypeRef(pack, dimensionKey),
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

    public record Handle(String dimensionId, String pack, String packDimensionKey, long seed, ServerLevel level, IrisModdedChunkGenerator generator) {
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
