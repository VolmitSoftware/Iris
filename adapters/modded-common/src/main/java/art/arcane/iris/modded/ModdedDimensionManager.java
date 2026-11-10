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
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.modded.toolworld.ModdedToolPainting;
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
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

    /** Makes the runtime dimension {@code dimensionId} generate {@code pack}, with chunks that come only from {@code painting} when it is not null. */
    public static Handle create(MinecraftServer server, String dimensionId, String pack, String packDimensionKey, long seed,
                                ModdedToolPainting painting) {
        ModdedStartup.requirePackForWorldCreation(pack);
        ModdedServerAccess serverAccess = requireAccess();
        synchronized (LOCK) {
            ResourceKey<Level> key = levelKey(dimensionId);
            Handle existing = HANDLES.get(dimensionId);
            if (existing != null && serverAccess.hasLevel(server, key)) {
                existing.generator().repointAndBind(existing.level(), pack, packDimensionKey, seed);
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
                generator.repointAndBind(present, pack, packDimensionKey, seed);
                Handle handle = new Handle(dimensionId, pack, packDimensionKey, seed, present, generator);
                HANDLES.put(dimensionId, handle);
                return handle;
            }

            try {
                Handle handle = inject(server, serverAccess, dimensionId, key, pack, packDimensionKey, seed, painting);
                HANDLES.put(dimensionId, handle);
                ModdedIrisLog.info("Iris injected runtime dimension '{}' (pack={} dim={} seed={})", dimensionId, pack, packDimensionKey, seed);
                return handle;
            } catch (Throwable e) {
                ModdedIrisLog.error("Iris failed to inject runtime dimension '{}' (pack={} dim={} seed={})", dimensionId, pack, packDimensionKey, seed, e);
                throw new IllegalStateException("Iris runtime dimension injection failed for " + dimensionId, e);
            }
        }
    }

    public static Handle create(MinecraftServer server, String dimensionId, String pack, String packDimensionKey, long seed) {
        return create(server, dimensionId, pack, packDimensionKey, seed, null);
    }

    public static Handle createPersistent(MinecraftServer server, String dimensionId, String pack, String packDimensionKey, long seed) {
        ModdedDimensionRegistryStore.PersistentDimension previous =
                ModdedDimensionRegistryStore.get(server, dimensionId);
        ModdedDimensionRegistryStore.put(server, new ModdedDimensionRegistryStore.PersistentDimension(dimensionId, pack, packDimensionKey, seed));
        try {
            return create(server, dimensionId, pack, packDimensionKey, seed);
        } catch (Throwable e) {
            try {
                if (previous == null) {
                    ModdedDimensionRegistryStore.remove(server, dimensionId);
                } else {
                    ModdedDimensionRegistryStore.put(server, previous);
                }
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
                + " did not finish before its deadline.");
    }

    private static void runOnServer(MinecraftServer server, Runnable task) {
        if (server.isSameThread()) {
            task.run();
            return;
        }
        server.execute(task);
    }

    private static Holder<DimensionType> resolveDimensionType(RegistryAccess registryAccess, String pack, String packDimensionKey) {
        Registry<DimensionType> registry = registryAccess.lookupOrThrow(Registries.DIMENSION_TYPE);
        IrisDimension dimension = loadPackDimension(pack, packDimensionKey);
        String typeRef = ModdedWorldgenIds.dimensionTypeRef(pack, packDimensionKey);
        ResourceKey<DimensionType> typeKey = ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.parse(typeRef));
        ModdedRuntimeRegistry.ensureCustomBiomes(registryAccess, dimension, pack);
        ModdedRuntimeRegistry.ensureDimensionType(registry, typeKey, typeRef);
        return ModdedForcedDatapack.requireRegisteredDimensionType(
                typeRef, registry.get(typeKey), pack, packDimensionKey);
    }

    private static IrisDimension loadPackDimension(String pack, String packDimensionKey) {
        try {
            File packFolder = ModdedWorldEngines.packFolder(pack);
            if (!packFolder.isDirectory()) {
                throw new IllegalStateException("Iris pack directory is missing: " + packFolder.getAbsolutePath());
            }
            IrisDimension dimension = IrisData.get(packFolder).getDimensionLoader().load(packDimensionKey);
            if (dimension == null) {
                throw new IllegalStateException("Iris pack '" + pack
                        + "' does not contain dimension '" + packDimensionKey + "'");
            }
            return dimension;
        } catch (Throwable e) {
            ModdedIrisLog.error("Iris could not load pack '{}' dimension '{}' for dimension type resolution",
                    pack, packDimensionKey, e);
            if (e instanceof Error fatalError) {
                throw fatalError;
            }
            throw new IllegalStateException("Iris could not load pack '" + pack + "' dimension '"
                    + packDimensionKey + "' for dimension type resolution", e);
        }
    }

    private static Handle inject(MinecraftServer server, ModdedServerAccess serverAccess, String dimensionId, ResourceKey<Level> key, String pack, String packDimensionKey, long seed,
                                 ModdedToolPainting painting) {
        RegistryAccess registryAccess = server.registryAccess();
        Holder<DimensionType> dimensionType = resolveDimensionType(registryAccess, pack, packDimensionKey);
        Holder<Biome> plains = registryAccess.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        FixedBiomeSource biomeSource = new FixedBiomeSource(plains);
        String generatorRef = pack.equals(packDimensionKey) ? pack : pack + ":" + packDimensionKey;
        IrisModdedChunkGenerator generator = new IrisModdedChunkGenerator(biomeSource, generatorRef);
        generator.paintWith(painting);
        generator.repoint(pack, packDimensionKey, seed);
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

    public record Handle(String dimensionId, String pack, String packDimensionKey, long seed, ServerLevel level, IrisModdedChunkGenerator generator) {
    }
}
