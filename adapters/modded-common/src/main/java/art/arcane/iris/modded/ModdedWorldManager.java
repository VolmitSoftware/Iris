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
import art.arcane.iris.core.content.ContentServices;
import art.arcane.iris.core.content.ContentSpawns;
import art.arcane.iris.core.gui.PregeneratorJob;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineLifecycleTasks;
import art.arcane.iris.engine.framework.EngineWorldManager;
import art.arcane.iris.engine.object.value.IrisPosition;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterMarker;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
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
        if (!markerSystemEnabled()) {
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
        if (!markerSystemEnabled()) {
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

    private boolean initialSpawnChunk(ServerLevel level, int chunkX, int chunkZ) {
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
            chunk.raiseFlagUnchecked(INITIAL_SPAWN_COMPLETION_FLAG, () -> {
                spawnMarkerSpawners(level, chunkX, chunkZ, chunk, true);
                scheduleInitialFollowUp(level, chunkX, chunkZ);
            });
        } finally {
            chunk.release();
        }
        return true;
    }

    private void scheduleInitialFollowUp(ServerLevel level, int chunkX, int chunkZ) {
        ModdedScheduler scheduler = ModdedEngineBootstrap.schedulerOrNull();
        if (scheduler == null) {
            IrisLogging.error("Iris could not schedule the initial entity-spawn follow-up because the modded scheduler is unavailable.");
            return;
        }
        scheduler.laterGlobal(
                () -> EngineLifecycleTasks.run(
                        engine,
                        "modded_world_manager_initial_spawn_followup",
                        () -> runInitialFollowUp(level, chunkX, chunkZ)),
                RNG.r.i(5, 200));
    }

    private void runInitialFollowUp(ServerLevel level, int chunkX, int chunkZ) {
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

        MantleChunk<Matter> chunk = mantle.getChunk(chunkX, chunkZ).use();
        try {
            if (markerSystemEnabled()) {
                spawnMarkerSpawners(level, chunkX, chunkZ, chunk, false);
            }
            if (ambientSystemEnabled()) {
                spawnAmbient(level, chunkX, chunkZ, true);
            }
        } finally {
            chunk.release();
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
                spawnMarkerSpawners(level, chunkX, chunkZ, chunk, false);
            }
            if (ambientSystemEnabled()) {
                spawnAmbient(level, chunkX, chunkZ, false);
            }
        } finally {
            chunk.release();
        }
    }

    private void spawnMarkerSpawners(ServerLevel level, int chunkX, int chunkZ, MantleChunk<Matter> chunk, boolean initial) {
        int minHeight = engine.getWorld().minHeight();
        KList<IrisPosition> obstructed = new KList<>();
        chunk.iterate(MatterMarker.class, (Integer x, Integer yf, Integer z, MatterMarker marker) -> {
            String tag = marker.getTag();
            if (tag.equals("cave_floor") || tag.equals("cave_ceiling")) {
                return;
            }
            Optional<ContentSpawns.MarkerRule> rule = ContentServices.markerRule(engine, tag);
            if (rule.isEmpty()) {
                return;
            }

            int worldX = (chunkX << 4) + (x & 15);
            int worldZ = (chunkZ << 4) + (z & 15);
            int worldY = yf + minHeight;

            if (rule.get().emptyAbove() && aboveObstructed(level, worldX, worldY, worldZ)) {
                obstructed.add(new IrisPosition(worldX, yf, worldZ));
                return;
            }

            List<String> spawners = rule.get().spawners();
            if (spawners.isEmpty()) {
                return;
            }
            String chosen = spawners.get(RNG.r.nextInt(spawners.size()));
            spawnAtMarker(level, new IrisPosition(worldX, worldY, worldZ), tag, chosen, initial);
        });
        Mantle<Matter> mantle = engine.getMantle().getMantle();
        for (IrisPosition position : obstructed) {
            mantle.remove(position.getX(), position.getY(), position.getZ(), MatterMarker.class);
        }
    }

    private void spawnAtMarker(ServerLevel level, IrisPosition position, String marker, String spawner, boolean initial) {
        ContentServices.planMarkerSpawn(engine, position.getX(), position.getY(), position.getZ(), marker, spawner, initial)
                .ifPresent(plan -> {
                    if (plan.exhaustsMarker()) {
                        engine.getMantle().getMantle().remove(position.getX(), position.getY() - engine.getWorld().minHeight(), position.getZ(), MatterMarker.class);
                    }
                    spawn(level, plan);
                });
    }

    private void spawnAmbient(ServerLevel level, int chunkX, int chunkZ, boolean initial) {
        IrisComplex complex = engine.getComplex();
        if (complex == null) {
            return;
        }

        ContentServices.planChunkSpawn(engine, chunkX, chunkZ, countChunkLivingEntities(level, chunkX, chunkZ), initial)
                .ifPresent(plan -> spawn(level, plan));
    }

    /** Spawns the entities of the plan that the light, the surface below and the space around them allow, and tells the plan how many appeared. */
    private void spawn(ServerLevel level, ContentSpawns.Plan plan) {
        int spawned = 0;
        for (ContentSpawns.Attempt attempt : plan.attempts()) {
            if (spawnIfAllowed(level, attempt)) {
                spawned++;
            }
        }
        plan.spawned(spawned);
    }

    private boolean spawnIfAllowed(ServerLevel level, ContentSpawns.Attempt attempt) {
        if (attempt.worldY() <= level.getMinY() || attempt.worldY() >= level.getMaxY()) {
            return false;
        }
        if (attempt.restrictsLight() && !lightAllowed(attempt, level)) {
            return false;
        }
        if (attempt.checkSurface() && !surfaceAllows(attempt, level)) {
            return false;
        }
        return ModdedEntitySpawner.spawn(engine, attempt.entity(), level, attempt.x(), attempt.worldY(), attempt.z()) != null;
    }

    private static boolean lightAllowed(ContentSpawns.Attempt attempt, ServerLevel level) {
        int light = level.getMaxLocalRawBrightness(new BlockPos(attempt.x(), attempt.worldY(), attempt.z()));
        return light >= attempt.minLight() && light <= attempt.maxLight();
    }

    private static boolean surfaceAllows(ContentSpawns.Attempt attempt, ServerLevel level) {
        BlockState below = level.getBlockState(new BlockPos(attempt.x(), attempt.worldY() - 1, attempt.z()));
        return matchesSurface(attempt.surface(), below)
                && ModdedEntitySpawner.isAreaClearForSpawn(level, attempt.entity(), attempt.x(), attempt.worldY(), attempt.z());
    }

    private static boolean matchesSurface(ContentSpawns.Surface surface, BlockState below) {
        if (ModdedBlockResolution.isSolid(below)) {
            return surface == ContentSpawns.Surface.LAND || surface == ContentSpawns.Surface.OVERWORLD
                    || (surface == ContentSpawns.Surface.ANIMAL && isAnimalGround(below));
        }
        if (below.is(Blocks.LAVA)) {
            return surface == ContentSpawns.Surface.LAVA;
        }
        if (ModdedBlockResolution.isWater(below) || ModdedBlockResolution.isWaterLogged(below) || isAquaticFoliage(below)) {
            return surface == ContentSpawns.Surface.WATER || surface == ContentSpawns.Surface.OVERWORLD;
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
}
