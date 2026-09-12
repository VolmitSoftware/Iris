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

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.studio.view.PregeneratorJob;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.BiomeEnvironment;
import art.arcane.iris.world.history.SavedBiomeUnavailableException;
import art.arcane.iris.generation.runtime.EngineLifecycleTasks;
import art.arcane.iris.generation.runtime.EngineWorldManager;
import art.arcane.iris.structure.placement.LootResolver;
import art.arcane.volmlib.util.math.Rarity;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.world.entity.IrisEntity;
import art.arcane.iris.world.entity.IrisEntitySpawn;
import art.arcane.iris.world.entity.IrisMarker;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.iris.world.entity.IrisSpawnGroup;
import art.arcane.iris.world.entity.IrisSpawner;
import art.arcane.iris.generation.decoration.IrisSurface;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterMarker;
import art.arcane.volmlib.util.matter.slices.MarkerMatter;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class ModdedWorldManager implements EngineWorldManager {
    static final MantleFlag INITIAL_SPAWN_COMPLETION_FLAG = MantleFlag.INITIAL_SPAWNED_MARKER;
    private static final int MAX_INITIAL_QUEUE = 8192;
    private static final int MAX_INITIAL_DRAIN_PER_TICK = 8;
    private static final int MAX_INITIAL_RECOVERY_PER_PASS = 128;
    private static final int MANTLE_WARMUP_QUEUE_CAPACITY = 256;
    private static final int AMBIENT_CHUNK_SAMPLE = 64;
    private static final long INITIAL_RECOVERY_INTERVAL_MS = 1_000L;

    private final Engine engine;
    private final InitialSpawnQueue initialSpawnQueue;
    private final Set<Long> mantleWarmups;
    private final ThreadPoolExecutor mantleWarmupExecutor;
    private final long[] ambientChunkSample = new long[AMBIENT_CHUNK_SAMPLE];
    private int ambientChunkSampleSeen;
    private long lastAmbientAt;
    private long lastInitialRecoveryAt;
    private boolean initialSpawnQueueClosed;
    private boolean mantleWarmupExecutorStopped;
    private boolean mantleWarmupsCleared;
    private boolean spawnStateMissingLogged;
    private volatile boolean closed;
    private volatile boolean entityCountAvailable;
    private volatile int cachedEntityCount;
    private volatile int cachedConsideredChunks;
    private volatile double cachedSaturation;

    public ModdedWorldManager(Engine engine) {
        this.engine = engine;
        this.initialSpawnQueue = new InitialSpawnQueue(MAX_INITIAL_QUEUE);
        this.mantleWarmups = ConcurrentHashMap.newKeySet();
        BlockingQueue<Runnable> warmupQueue = new ArrayBlockingQueue<>(MANTLE_WARMUP_QUEUE_CAPACITY);
        this.mantleWarmupExecutor = new ThreadPoolExecutor(
                1,
                1,
                30L,
                TimeUnit.SECONDS,
                warmupQueue,
                runnable -> {
                    Thread thread = new Thread(runnable, "Iris Initial Spawn Mantle Warmup");
                    thread.setDaemon(true);
                    thread.setPriority(Thread.MIN_PRIORITY);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
        this.mantleWarmupExecutor.allowCoreThreadTimeOut(true);
    }

    public static void enqueueGenerated(Engine engine, int chunkX, int chunkZ) {
        if (engine == null || engine.isClosed()) {
            return;
        }
        EngineWorldManager worldManager = engine.getWorldManager();
        if (!(worldManager instanceof ModdedWorldManager moddedWorldManager)) {
            return;
        }
        if (moddedWorldManager.closed || moddedWorldManager.isPregenActive()) {
            // runServerTick skips the drain while a pregen targets this world, so every offer from the
            // generation threads can only expire or overflow while contending for the queue monitor.
            // recoverLoadedInitialSpawns re-offers the chunks that matter once the job ends.
            return;
        }
        moddedWorldManager.initialSpawnQueue.offer(pack(chunkX, chunkZ));
    }

    public void serverTick(ServerLevel level) {
        EngineLifecycleTasks.run(engine, "modded_world_manager_tick", () -> runServerTick(level));
    }

    private void runServerTick(ServerLevel level) {
        if (closed || engine.isClosed() || engine.getMantle().getMantle().isClosed()) {
            return;
        }
        if (!isEntitySpawningEnabledForCurrentWorld()) {
            return;
        }
        if (isPregenActive()) {
            return;
        }
        recoverLoadedInitialSpawns(level);
        drainInitialSpawns(level);
        ambientTick(level);
    }

    private void recoverLoadedInitialSpawns(ServerLevel level) {
        if (!markerSystemEnabled() && !ambientSystemEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastInitialRecoveryAt < INITIAL_RECOVERY_INTERVAL_MS) {
            return;
        }
        lastInitialRecoveryAt = now;

        Set<Long> candidates = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            int centerX = player.blockPosition().getX() >> 4;
            int centerZ = player.blockPosition().getZ() >> 4;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    candidates.add(pack(centerX + dx, centerZ + dz));
                }
            }
        }
        candidates.addAll(level.getForceLoadedChunks());

        Mantle<Matter> mantle = engine.getMantle().getMantle();
        int recovered = 0;
        for (long key : candidates) {
            int chunkX = unpackX(key);
            int chunkZ = unpackZ(key);
            if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
                continue;
            }
            if (mantle.isChunkLoaded(chunkX, chunkZ) && mantle.hasFlag(chunkX, chunkZ, INITIAL_SPAWN_COMPLETION_FLAG)) {
                continue;
            }
            if (initialSpawnQueue.offer(key) && ++recovered >= MAX_INITIAL_RECOVERY_PER_PASS) {
                return;
            }
        }
    }

    private void drainInitialSpawns(ServerLevel level) {
        if (initialSpawnQueue.isEmpty()) {
            return;
        }
        if (!markerSystemEnabled() && !ambientSystemEnabled()) {
            initialSpawnQueue.clear();
            return;
        }

        int budget = initialSpawnQueue.batchSize(MAX_INITIAL_DRAIN_PER_TICK);
        while (budget-- > 0) {
            Long key = initialSpawnQueue.poll();
            if (key == null) {
                return;
            }
            int chunkX = unpackX(key);
            int chunkZ = unpackZ(key);
            boolean retry = false;
            try {
                if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
                    retry = true;
                    continue;
                }
                if (!initialSpawnChunk(level, chunkX, chunkZ)) {
                    retry = true;
                    warmupMantleChunkAsync(key, chunkX, chunkZ);
                }
            } catch (SavedBiomeUnavailableException unavailable) {
                retry = unavailable.isLoading();
            } catch (Throwable e) {
                IrisLogging.reportError(e);
                retry = true;
            } finally {
                if (retry) {
                    initialSpawnQueue.retry(key);
                } else {
                    initialSpawnQueue.complete(key);
                }
            }
        }
    }

    boolean initialSpawnChunk(ServerLevel level, int chunkX, int chunkZ) {
        if (!ModdedEntitySpawner.chunksSafe(level, chunkX, chunkZ)) {
            return false;
        }
        Mantle<Matter> mantle = engine.getMantle().getMantle();
        if (!mantle.isChunkLoaded(chunkX, chunkZ)) {
            return false;
        }
        if (mantle.hasFlag(chunkX, chunkZ, INITIAL_SPAWN_COMPLETION_FLAG)) {
            return true;
        }

        MantleChunk<Matter> chunk = mantle.getChunk(chunkX, chunkZ).use();
        try {
            List<PreparedMarkerSpawn> markers = markerSystemEnabled()
                    ? prepareMarkerSpawns(level, chunkX, chunkZ, chunk) : List.of();
            Optional<BiomeEnvironment> environment = ambientSystemEnabled()
                    ? resolveSurfaceEnvironment(chunkX, chunkZ) : Optional.empty();
            chunk.raiseFlagUnchecked(INITIAL_SPAWN_COMPLETION_FLAG, () -> {
                spawnPreparedMarkers(level, markers, true);
                if (environment.isPresent()) {
                    scheduleInitialFollowUp(level, chunkX, chunkZ, environment.get());
                }
            });
        } finally {
            chunk.release();
        }
        return true;
    }

    private void scheduleInitialFollowUp(ServerLevel level, int chunkX, int chunkZ, BiomeEnvironment environment) {
        ModdedScheduler scheduler = ModdedEngineBootstrap.schedulerOrNull();
        if (scheduler == null) {
            IrisLogging.error("Iris could not schedule the initial entity-spawn follow-up because the modded scheduler is unavailable.");
            return;
        }
        scheduler.laterGlobal(
                () -> EngineLifecycleTasks.run(
                        engine,
                        "modded_world_manager_initial_spawn_followup",
                        () -> runInitialFollowUp(level, chunkX, chunkZ, environment)),
                RNG.r.i(5, 200));
    }

    private void runInitialFollowUp(ServerLevel level, int chunkX, int chunkZ, BiomeEnvironment environment) {
        Mantle<Matter> mantle = engine.getMantle().getMantle();
        if (closed || engine.isClosed() || mantle.isClosed() || !isEntitySpawningEnabledForCurrentWorld()) {
            return;
        }
        if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null || !mantle.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }
        if (engine.getComplex() == null) {
            return;
        }

        if (ambientSystemEnabled()) {
            try (BiomeEnvironment.Scope ignored = engine.openBiomeEnvironmentScope(environment)) {
                spawnAmbient(level, chunkX, chunkZ, true, environment);
            }
        }
    }

    private void warmupMantleChunkAsync(long key, int chunkX, int chunkZ) {
        if (closed || !mantleWarmups.add(key)) {
            return;
        }
        try {
            mantleWarmupExecutor.execute(() -> {
                if (!EngineLifecycleTasks.run(
                        engine,
                        "modded_world_manager_mantle_warmup",
                        () -> warmupMantleChunk(key, chunkX, chunkZ))) {
                    mantleWarmups.remove(key);
                }
            });
        } catch (RejectedExecutionException e) {
            mantleWarmups.remove(key);
        }
    }

    private void warmupMantleChunk(long key, int chunkX, int chunkZ) {
        try {
            Mantle<Matter> mantle = engine.getMantle().getMantle();
            if (!closed && !engine.isClosed() && !mantle.isClosed() && !mantle.isChunkLoaded(chunkX, chunkZ)) {
                mantle.getChunk(chunkX, chunkZ);
            }
        } catch (Throwable e) {
            if (!closed && !engine.isClosed()) {
                IrisLogging.reportError(e);
            }
        } finally {
            mantleWarmups.remove(key);
        }
    }

    private void ambientTick(ServerLevel level) {
        long now = System.currentTimeMillis();
        long interval = IrisSettings.get().getWorld().getAsyncTickIntervalMS();
        if (now - lastAmbientAt < interval) {
            return;
        }
        lastAmbientAt = now;

        if (!markerSystemEnabled() && !ambientSystemEnabled()) {
            return;
        }
        if (level.players().isEmpty()) {
            return;
        }

        int loadedChunks = sampleLoadedChunks(level);
        refreshEntityCount(level, loadedChunks);
        if (!entityCountAvailable) {
            return;
        }
        if (cachedSaturation > IrisSettings.get().getWorld().getTargetSpawnEntitiesPerChunk()) {
            return;
        }

        int sampled = Math.min(loadedChunks, ambientChunkSample.length);
        if (sampled == 0) {
            return;
        }

        int spawnBuffer = RNG.r.i(2, 12);
        while (spawnBuffer-- > 0) {
            long key = ambientChunkSample[RNG.r.nextInt(sampled)];
            try {
                ambientSpawnChunk(level, unpackX(key), unpackZ(key));
            } catch (SavedBiomeUnavailableException unavailable) {
                continue;
            } catch (Throwable e) {
                IrisLogging.reportError(e);
            }
        }
    }

    private void ambientSpawnChunk(ServerLevel level, int chunkX, int chunkZ) {
        if (!ModdedEntitySpawner.chunksSafe(level, chunkX, chunkZ)) {
            return;
        }
        Mantle<Matter> mantle = engine.getMantle().getMantle();
        if (!mantle.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }

        MantleChunk<Matter> chunk = mantle.getChunk(chunkX, chunkZ).use();
        try {
            if (markerSystemEnabled()) {
                spawnPreparedMarkers(level, prepareMarkerSpawns(level, chunkX, chunkZ, chunk), false);
            }
            if (ambientSystemEnabled()) {
                spawnAmbient(level, chunkX, chunkZ, false);
            }
        } finally {
            chunk.release();
        }
    }

    private List<PreparedMarkerSpawn> prepareMarkerSpawns(ServerLevel level, int chunkX, int chunkZ, MantleChunk<Matter> chunk) {
        int minHeight = engine.getWorld().minHeight();
        KList<IrisPosition> obstructed = new KList<>();
        List<PreparedMarkerSpawn> prepared = new ArrayList<>();
        chunk.iterate(MatterMarker.class, (Integer x, Integer yf, Integer z, MatterMarker marker) -> {
            String tag = marker.getTag();
            if (tag.equals("cave_floor") || tag.equals("cave_ceiling")) {
                return;
            }
            int worldX = (chunkX << 4) + (x & 15);
            int worldZ = (chunkZ << 4) + (z & 15);
            int worldY = yf + minHeight;
            BiomeEnvironment environment;
            try {
                environment = engine.getBiomeEnvironment(worldX, yf, worldZ);
            } catch (SavedBiomeUnavailableException unavailable) {
                if (unavailable.isLoading()) {
                    throw unavailable;
                }
                return;
            }
            IrisMarker resolved = environment.data().getMarkerLoader().load(tag);
            if (resolved == null) {
                return;
            }

            if (resolved.isEmptyAbove() && aboveObstructed(level, worldX, worldY, worldZ)) {
                obstructed.add(new IrisPosition(worldX, yf, worldZ));
                return;
            }

            KList<IrisSpawner> spawners = resolveMarkerSpawners(resolved, environment);
            if (spawners.isEmpty()) {
                return;
            }
            IrisSpawner chosen = spawners.getRandom();
            if (chosen == null) {
                return;
            }
            prepared.add(new PreparedMarkerSpawn(new IrisPosition(worldX, worldY, worldZ), chosen, environment));
        });
        Mantle<Matter> mantle = engine.getMantle().getMantle();
        for (IrisPosition position : obstructed) {
            mantle.remove(position.getX(), position.getY(), position.getZ(), MatterMarker.class);
        }
        return List.copyOf(prepared);
    }

    private KList<IrisSpawner> resolveMarkerSpawners(IrisMarker marker, BiomeEnvironment environment) {
        KList<IrisSpawner> spawners = new KList<>();
        for (String key : marker.getSpawners()) {
            IrisSpawner spawner = environment.data().getSpawnerLoader().load(key);
            if (spawner == null) {
                IrisLogging.error("Cannot load spawner: " + key + " for marker on " + engine.getName());
                continue;
            }
            spawner.setReferenceMarker(marker);
            spawners.add(spawner);
        }
        return spawners;
    }

    private void spawnPreparedMarkers(ServerLevel level, List<PreparedMarkerSpawn> markers, boolean initial) {
        for (PreparedMarkerSpawn marker : markers) {
            try (BiomeEnvironment.Scope ignored = engine.openBiomeEnvironmentScope(marker.environment())) {
                spawnFromSpawner(level, marker.position(), marker.spawner(), initial);
            }
        }
    }

    private void spawnFromSpawner(ServerLevel level, IrisPosition position, IrisSpawner spawner, boolean initial) {
        KList<IrisEntitySpawn> spawns = initial ? spawner.getInitialSpawns() : spawner.getSpawns();
        if (spawns.isEmpty()) {
            return;
        }
        for (IrisEntitySpawn entry : spawns) {
            entry.setReferenceSpawner(spawner);
            entry.setReferenceMarker(spawner.getReferenceMarker());
        }
        IrisEntitySpawn chosen = rarityPick(spawns);
        if (chosen == null) {
            return;
        }

        int chunkX = position.getX() >> 4;
        int chunkZ = position.getZ() >> 4;
        if (!canSpawn(spawner, chunkX, chunkZ)) {
            return;
        }
        int spawned = spawnEntryAt(level, chosen, spawner, position);
        if (spawned > 0) {
            spawner.spawn(engine, chunkX, chunkZ);
        }
    }

    private Optional<BiomeEnvironment> resolveSurfaceEnvironment(int chunkX, int chunkZ) {
        try {
            return Optional.of(engine.getSurfaceBiomeEnvironment((chunkX << 4) + 8, (chunkZ << 4) + 8));
        } catch (SavedBiomeUnavailableException unavailable) {
            if (unavailable.isLoading()) {
                throw unavailable;
            }
            return Optional.empty();
        }
    }

    private void spawnAmbient(ServerLevel level, int chunkX, int chunkZ, boolean initial) {
        Optional<BiomeEnvironment> resolved = resolveSurfaceEnvironment(chunkX, chunkZ);
        if (resolved.isEmpty()) {
            return;
        }
        BiomeEnvironment environment = resolved.get();
        try (BiomeEnvironment.Scope ignored = engine.openBiomeEnvironmentScope(environment)) {
            spawnAmbient(level, chunkX, chunkZ, initial, environment);
        }
    }

    private void spawnAmbient(ServerLevel level, int chunkX, int chunkZ, boolean initial, BiomeEnvironment environment) {
        IrisComplex complex = engine.getComplex();
        if (complex == null) {
            return;
        }

        IrisBiome biome = environment.biome();
        int chunkMobs = countChunkLivingEntities(level, chunkX, chunkZ);

        KList<IrisEntitySpawn> pool = new KList<>();
        collectSpawns(pool, environment.data().getSpawnerLoader().loadAll(environment.dimension().getEntitySpawners()), biome, chunkX, chunkZ, chunkMobs, initial);
        collectSpawns(pool, environment.data().getSpawnerLoader().loadAll(environment.region().getEntitySpawners()), null, chunkX, chunkZ, chunkMobs, initial);
        collectSpawns(pool, environment.data().getSpawnerLoader().loadAll(biome.getEntitySpawners()), null, chunkX, chunkZ, chunkMobs, initial);
        if (pool.isEmpty()) {
            return;
        }

        IrisEntitySpawn chosen = rarityPick(pool);
        if (chosen == null || chosen.getReferenceSpawner() == null) {
            return;
        }
        IrisSpawner spawner = chosen.getReferenceSpawner();
        if (!canSpawn(spawner, chunkX, chunkZ)) {
            return;
        }
        int spawned = spawnEntry(level, chosen, spawner, chunkX, chunkZ);
        if (spawned > 0) {
            spawner.spawn(engine, chunkX, chunkZ);
        }
    }

    private void collectSpawns(KList<IrisEntitySpawn> pool, KList<IrisSpawner> spawners, IrisBiome biomeFilter, int chunkX, int chunkZ, int chunkMobs, boolean initial) {
        for (IrisSpawner spawner : spawners) {
            if (spawner == null) {
                continue;
            }
            if (spawner.getMaxEntitiesPerChunk() <= chunkMobs) {
                continue;
            }
            if (biomeFilter != null && !spawner.isValid(biomeFilter)) {
                continue;
            }
            if (!canSpawn(spawner, chunkX, chunkZ)) {
                continue;
            }
            KList<IrisEntitySpawn> spawns = initial ? spawner.getInitialSpawns() : spawner.getSpawns();
            for (IrisEntitySpawn entry : spawns) {
                entry.setReferenceSpawner(spawner);
                entry.setReferenceMarker(spawner.getReferenceMarker());
                pool.add(entry);
            }
        }
    }

    private int spawnEntry(ServerLevel level, IrisEntitySpawn entry, IrisSpawner spawner, int chunkX, int chunkZ) {
        IrisEntity irisEntity = entry.getRealEntity(engine);
        if (irisEntity == null) {
            return 0;
        }

        int min = entry.getMinSpawns();
        int max = entry.getMaxSpawns();
        int count = LootResolver.inclusive(RNG.r, min, max);
        if (count <= 0) {
            return 0;
        }

        RNG entityRng = entry.getRng().aquire(() -> new RNG(engine.getSeedManager().getEntity()));
        IrisSpawnGroup group = spawner.getGroup();
        KList<IrisPosition> caveFloors = group == IrisSpawnGroup.CAVE
                ? engine.getMantle().findMarkers(chunkX, chunkZ, MarkerMatter.CAVE_FLOOR)
                : new KList<>();
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            int worldX;
            int worldY;
            int worldZ;
            if (group == IrisSpawnGroup.CAVE) {
                if (caveFloors.isEmpty()) {
                    continue;
                }
                IrisPosition caveFloor = caveFloors.getRandom(RNG.r);
                worldX = caveFloor.getX();
                worldY = caveFloor.getY() + 1;
                worldZ = caveFloor.getZ();
            } else {
                worldX = (chunkX << 4) + RNG.r.i(16);
                worldZ = (chunkZ << 4) + RNG.r.i(16);
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ) - 1;
                int solidY = level.getHeight(Heightmap.Types.OCEAN_FLOOR, worldX, worldZ) - 1;
                Integer selectedY = IrisEntitySpawn.selectSurfaceSpawnY(group, irisEntity.getSurface(), solidY, surfaceY, RNG.r);
                if (selectedY == null) {
                    continue;
                }
                worldY = selectedY;
            }
            if (worldY <= level.getMinY() || worldY >= level.getMaxY()) {
                continue;
            }
            // Rarity is applied exactly once, as pool weighting in rarityPick - never re-rolled per position (Bukkit parity).
            if (!lightAllowed(spawner, level, worldX, worldY, worldZ)) {
                continue;
            }
            if (!surfaceMatches(irisEntity.getSurface(), level, worldX, worldY, worldZ)) {
                continue;
            }
            if (!ModdedEntitySpawner.isAreaClearForSpawn(level, irisEntity, worldX, worldY, worldZ)) {
                continue;
            }
            if (ModdedEntitySpawner.spawn(engine, irisEntity, level, worldX, worldY, worldZ, entityRng) != null) {
                spawned++;
            }
        }
        return spawned;
    }

    private int spawnEntryAt(ServerLevel level, IrisEntitySpawn entry, IrisSpawner spawner, IrisPosition position) {
        IrisEntity irisEntity = entry.getRealEntity(engine);
        if (irisEntity == null) {
            return 0;
        }

        int min = entry.getMinSpawns();
        int max = entry.getMaxSpawns();
        int count = LootResolver.inclusive(RNG.r, min, max);
        if (count <= 0) {
            return 0;
        }

        exhaustMarker(spawner, position);

        RNG entityRng = entry.getRng().aquire(() -> new RNG(engine.getSeedManager().getEntity()));
        int worldX = position.getX();
        int worldY = position.getY() + 1;
        int worldZ = position.getZ();
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            // Rarity is applied exactly once, as pool weighting in rarityPick - never re-rolled per position (Bukkit parity).
            if (!lightAllowed(spawner, level, worldX, worldY, worldZ)) {
                continue;
            }
            if (irisEntity.getSurface().isFluid()
                    && (!surfaceMatches(irisEntity.getSurface(), level, worldX, worldY, worldZ)
                    || !ModdedEntitySpawner.isAreaClearForSpawn(level, irisEntity, worldX, worldY, worldZ))) {
                continue;
            }
            if (ModdedEntitySpawner.spawn(engine, irisEntity, level, worldX, worldY, worldZ, entityRng) != null) {
                spawned++;
            }
        }
        return spawned;
    }

    private void exhaustMarker(IrisSpawner spawner, IrisPosition position) {
        IrisMarker marker = spawner.getReferenceMarker();
        if (marker == null || !marker.shouldExhaust()) {
            return;
        }
        engine.getMantle().getMantle().remove(position.getX(), position.getY() - engine.getWorld().minHeight(), position.getZ(), MatterMarker.class);
    }

    private boolean canSpawn(IrisSpawner spawner, int chunkX, int chunkZ) {
        return spawner.canSpawn(engine, chunkX, chunkZ);
    }

    private boolean lightAllowed(IrisSpawner spawner, ServerLevel level, int worldX, int worldY, int worldZ) {
        IrisRange range = spawner.getAllowedLightLevels();
        if (range.getMin() > 0 || range.getMax() < 15) {
            return range.contains(level.getMaxLocalRawBrightness(new BlockPos(worldX, worldY, worldZ)));
        }
        return true;
    }

    private boolean surfaceMatches(IrisSurface surface, ServerLevel level, int worldX, int worldY, int worldZ) {
        BlockState below = level.getBlockState(new BlockPos(worldX, worldY - (surface.isFluid() ? 0 : 1), worldZ));
        return matchesSurface(surface, below);
    }

    static boolean matchesSurface(IrisSurface surface, BlockState below) {
        if (ModdedBlockResolution.isSolid(below)) {
            return surface == IrisSurface.LAND || surface == IrisSurface.OVERWORLD
                    || (surface == IrisSurface.ANIMAL && isAnimalGround(below));
        }
        if (below.is(Blocks.LAVA)) {
            return surface == IrisSurface.LAVA;
        }
        if (ModdedBlockResolution.isWater(below) || ModdedBlockResolution.isWaterLogged(below) || isAquaticFoliage(below)) {
            return surface == IrisSurface.WATER || surface == IrisSurface.OVERWORLD;
        }
        return false;
    }

    private static boolean isAnimalGround(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM) || state.is(Blocks.SNOW_BLOCK);
    }

    private static boolean isAquaticFoliage(BlockState state) {
        return state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS)
                || state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT);
    }

    private boolean aboveObstructed(ServerLevel level, int worldX, int worldY, int worldZ) {
        return worldY + 2 >= level.getMaxY()
                || ModdedBlockResolution.isSolid(level.getBlockState(new BlockPos(worldX, worldY + 1, worldZ)))
                || ModdedBlockResolution.isSolid(level.getBlockState(new BlockPos(worldX, worldY + 2, worldZ)));
    }

    private int countChunkLivingEntities(ServerLevel level, int chunkX, int chunkZ) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        AABB box = new AABB(baseX, level.getMinY(), baseZ, baseX + 16, level.getMaxY(), baseZ + 16);
        return level.getEntities((Entity) null, box,
                (Entity entity) -> isLivingEntityInChunk(entity, chunkX, chunkZ)).size();
    }

    private static boolean isLivingEntityInChunk(Entity entity, int chunkX, int chunkZ) {
        if (!(entity instanceof LivingEntity)) {
            return false;
        }
        BlockPos position = entity.blockPosition();
        return (position.getX() >> 4) == chunkX && (position.getZ() >> 4) == chunkZ;
    }

    /**
     * ServerChunkCache.tickChunks rebuilds NaturalSpawner.SpawnState every tick from every entity in the
     * level, so read that instead of walking all entities again on an Iris timer. The count is the natural
     * spawn cap population (non persistent mobs in loaded chunks, MISC excluded), which is exactly the
     * population the ambient spawn gate throttles against. No state means the level has not ticked chunks
     * yet, so hold spawning rather than guess.
     */
    private void refreshEntityCount(ServerLevel level, int loadedChunks) {
        cachedConsideredChunks = loadedChunks;
        NaturalSpawner.SpawnState spawnState = level.getChunkSource().getLastSpawnState();
        if (spawnState == null) {
            entityCountAvailable = false;
            if (!spawnStateMissingLogged) {
                spawnStateMissingLogged = true;
                IrisLogging.warn("No spawn state for " + engine.getName() + " yet; ambient spawning held");
            }
            return;
        }

        Object2IntMap<MobCategory> counts = spawnState.getMobCategoryCounts();
        int mobs = 0;
        for (MobCategory category : counts.keySet()) {
            mobs += counts.getInt(category);
        }

        entityCountAvailable = true;
        cachedEntityCount = mobs;
        // Metric = natural-spawn-cap population over natural-spawn chunk count: numerator and denominator both
        // come from MC's own spawn state, so the ratio is not diluted by chunks the spawner never counts.
        cachedSaturation = mobs / (spawnState.getSpawnableChunkCount() + 1.0) * 1.28;
    }

    /**
     * Reservoir sample (algorithm R) of the ready to send chunks into a fixed buffer. ambientTick only picks
     * up to 12 random chunks per pass, so materializing every loaded chunk position once per interval was
     * pure garbage. Returns how many chunks the walk saw, which is the same considered-chunk count the old
     * snapshot length reported. Server thread only, so the reservoir and its counter are plain fields.
     */
    private int sampleLoadedChunks(ServerLevel level) {
        ambientChunkSampleSeen = 0;
        level.getChunkSource().chunkMap.forEachReadyToSendChunk((LevelChunk chunk) -> {
            int index = ambientChunkSampleSeen++;
            int capacity = ambientChunkSample.length;
            int slot = reservoirSlot(index, capacity, index < capacity ? 0 : RNG.r.nextInt(index + 1));
            if (slot >= 0) {
                ambientChunkSample[slot] = chunk.getPos().pack();
            }
        });
        return ambientChunkSampleSeen;
    }

    /**
     * Algorithm R slot for the item at {@code index}: fill the reservoir first, then keep the item only when
     * {@code roll} (uniform over 0..index) lands inside the reservoir. Negative means drop the item.
     */
    static int reservoirSlot(int index, int capacity, int roll) {
        if (index < capacity) {
            return index;
        }
        return roll < capacity ? roll : -1;
    }

    private boolean isPregenActive() {
        PregeneratorJob job = PregeneratorJob.getInstance();
        return job != null && job.targetsWorldIdentity(engine.getWorld().identity());
    }

    private static boolean markerSystemEnabled() {
        return IrisSettings.get().getWorld().isMarkerEntitySpawningSystem();
    }

    private static boolean ambientSystemEnabled() {
        return IrisSettings.get().getWorld().isAmbientEntitySpawningSystem();
    }

    private boolean isEntitySpawningEnabledForCurrentWorld() {
        return entitySpawningEnabled(engine.isStudio(), IrisSettings.get().getStudio().isEntitySpawning());
    }

    static boolean entitySpawningEnabled(boolean studio, boolean studioSetting) {
        return !studio || studioSetting;
    }

    private IrisEntitySpawn rarityPick(KList<IrisEntitySpawn> entries) {
        KList<IrisEntitySpawn> weighted = Rarity.expandWeighted(entries);
        return weighted.isEmpty() ? entries.getRandom() : weighted.getRandom();
    }

    private static long pack(int x, int z) {
        return (((long) x) & 0xFFFFFFFFL) | ((((long) z) & 0xFFFFFFFFL) << 32);
    }

    private static int unpackX(long key) {
        return (int) (key & 0xFFFFFFFFL);
    }

    private static int unpackZ(long key) {
        return (int) ((key >> 32) & 0xFFFFFFFFL);
    }

    @Override
    public synchronized void close() {
        closed = true;
        Throwable failure = null;
        if (!initialSpawnQueueClosed) {
            try {
                initialSpawnQueue.close();
                initialSpawnQueueClosed = true;
            } catch (Throwable e) {
                failure = e;
            }
        }
        if (!mantleWarmupExecutorStopped) {
            try {
                // Drain, don't interrupt: an interrupt inside a FileChannel plate read closes
                // the channel and the read-failure fallback installs an empty plate that a
                // later flush persists over real data. Queued tasks no-op on the closed flag,
                // so the await only covers a single in-flight load; escalate on timeout only.
                mantleWarmupExecutor.shutdown();
                if (!mantleWarmupExecutor.awaitTermination(5L, java.util.concurrent.TimeUnit.SECONDS)) {
                    IrisLogging.warn("Iris mantle warm-up did not stop before world manager close; forcing interrupt");
                    mantleWarmupExecutor.shutdownNow();
                }
                mantleWarmupExecutorStopped = true;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                mantleWarmupExecutor.shutdownNow();
                mantleWarmupExecutorStopped = true;
            } catch (Throwable e) {
                failure = appendCloseFailure(failure, e);
            }
        }
        if (!mantleWarmupsCleared) {
            try {
                mantleWarmups.clear();
                mantleWarmupsCleared = true;
            } catch (Throwable e) {
                failure = appendCloseFailure(failure, e);
            }
        }
        if (failure != null) {
            throw new IllegalStateException("Failed to completely stop the modded Iris world manager.", failure);
        }
    }

    @Override
    public int getEntityCount() {
        return cachedEntityCount;
    }

    @Override
    public int getChunkCount() {
        return cachedConsideredChunks;
    }

    @Override
    public double getEntitySaturation() {
        return cachedSaturation;
    }

    @Override
    public void onTick() {
    }

    @Override
    public void onSave() {
        engine.getMantle().save();
    }

    private static Throwable appendCloseFailure(Throwable failure, Throwable next) {
        if (failure == null) {
            return next;
        }
        if (failure != next) {
            failure.addSuppressed(next);
        }
        return failure;
    }

    private record PreparedMarkerSpawn(IrisPosition position, IrisSpawner spawner, BiomeEnvironment environment) {
    }

}
