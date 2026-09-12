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

package art.arcane.iris.modded.service;

import art.arcane.iris.modded.ModdedIrisLog;
import art.arcane.iris.studio.view.PregeneratorJob;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.world.WorldMaintenance;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.world.history.SavedTerrainChunk;
import art.arcane.iris.modded.ModdedSavedTerrainCapture;

import java.util.concurrent.CompletableFuture;
import art.arcane.iris.generation.runtime.EnginePlatformHooks;
import art.arcane.iris.structure.nativegen.NativeStructureVolume;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionRuntimeContract;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.world.IrisWorldBoundary;
import art.arcane.iris.modded.IrisModdedChunkGenerator;
import art.arcane.iris.modded.ModdedDimensionManager;
import art.arcane.iris.modded.ModdedForcedDatapack;
import art.arcane.iris.modded.ModdedScheduler;
import art.arcane.iris.modded.ModdedWorkspaceGenerator;
import art.arcane.iris.nativegen.NativeStructureVolumeIndex;
import art.arcane.iris.modded.command.ModdedPregenJob;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.io.ReactiveFolder;
import art.arcane.volmlib.util.scheduling.ChronoLatch;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.border.WorldBorder;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModdedStudioHotloadService implements ModdedTickableService, EnginePlatformHooks {
    private static final String STUDIO_DIMENSION_PREFIX = "irisworldgen:studio_";
    private static final long POLL_MILLIS = 250L;
    private static final long CHECK_LATCH_MILLIS = 1_000L;
    private static final long RECENT_GENERATION_HOLDOFF_MILLIS = 2_000L;

    private final ConcurrentHashMap<String, Watch> watches = new ConcurrentHashMap<>();
    private volatile ExecutorService executor;
    private long lastPollAt;

    @Override
    public CompletableFuture<Void> flushSavedTerrainCapture(Engine engine) {
        return ModdedSavedTerrainCapture.flush(engine);
    }

    @Override
    public CompletableFuture<SavedTerrainChunk> captureSavedTerrainChunk(Engine engine, int chunkX, int chunkZ) {
        return ModdedSavedTerrainCapture.capture(engine, chunkX, chunkZ);
    }

    @Override
    public void onEnable() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadExecutor((Runnable task) -> {
            Thread thread = new Thread(task, "Iris Studio Hotload");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
        lastPollAt = 0L;
    }

    @Override
    public void onDisable() {
        ExecutorService active = executor;
        executor = null;
        if (active != null) {
            active.shutdownNow();
        }
        for (Watch watch : watches.values()) {
            watch.close();
        }
        watches.clear();
    }

    @Override
    public void refreshWorkspace(Engine engine) {
        writeWorkspace(engine, "workspace refresh");
    }

    @Override
    public KList<NativeStructureVolume> nativeStructureVolumes(Engine engine, int minX, int minZ, int maxX, int maxZ) {
        return NativeStructureVolumeIndex.volumes(engine, minX, minZ, maxX, maxZ);
    }

    @Override
    public boolean isMainThread() {
        return ModdedScheduler.isMainThread();
    }

    @Override
    public void refreshDatapackWorkspace(Engine engine) {
        IrisData data = engine.getData();
        KList<IrisDimension> dimensions = data.getDimensionLoader().loadAll(data.getDimensionLoader().getPossibleKeys());
        if (hasDatapackImports(dimensions)) {
            writeWorkspace(engine, "datapack workspace refresh");
        }
    }

    @Override
    public void reloadDatapacks(Engine engine) {
        ModdedForcedDatapack.regenerate();
    }

    @Override
    public void validateDimensionHotload(Engine engine, IrisDimension replacement) {
        IrisDimensionRuntimeContract.requireHotloadCompatible(
                "Modded Studio level '" + engine.getWorld().identity() + "'",
                engine.getDimension(),
                replacement,
                "irisworldgen");
        IrisModdedChunkGenerator.requireStructureBiomeUniverseCompatible(
                engine.getDimension(), replacement);
    }

    @Override
    public void prepareRuntimeHotload(Engine engine) {
        IrisWorld world = engine.getWorld();
        if (world == null || !world.hasPlatformWorld()
                || !(world.platformWorld().nativeHandle() instanceof ServerLevel level)
                || !(level.getChunkSource().getGenerator() instanceof IrisModdedChunkGenerator generator)) {
            NativeStructureVolumeIndex.invalidate(engine);
            return;
        }
        generator.prepareRuntimeHotload(level, engine);
    }

    @Override
    public void applyWorldBoundary(Engine engine) {
        IrisDimension expectedDimension = engine.getDimension();
        IrisWorldBoundary configuredBoundary = expectedDimension.getWorldBoundary();
        if (configuredBoundary == null) {
            return;
        }
        IrisWorld expectedIrisWorld = engine.getWorld();
        if (expectedIrisWorld == null || !expectedIrisWorld.hasPlatformWorld()
                || !(expectedIrisWorld.platformWorld().nativeHandle() instanceof ServerLevel expectedLevel)) {
            return;
        }
        IrisWorldBoundary boundary;
        try {
            boundary = IrisWorldBoundary.snapshot(configuredBoundary);
        } catch (Throwable error) {
            ModdedIrisLog.error("Invalid Iris world boundary for "
                    + expectedLevel.dimension().identifier() + ".", error);
            throw propagateBoundaryFailure("Invalid Iris world boundary for '"
                    + expectedLevel.dimension().identifier() + "'.", error);
        }
        MinecraftServer server = expectedLevel.getServer();
        if (server.isSameThread()) {
            applyWorldBoundary(engine, expectedDimension, expectedIrisWorld, expectedLevel, boundary);
            return;
        }
        try {
            server.execute(() -> applyWorldBoundary(engine, expectedDimension, expectedIrisWorld, expectedLevel, boundary));
        } catch (Throwable error) {
            ModdedIrisLog.error("Failed to schedule Iris world-boundary application for "
                    + expectedLevel.dimension().identifier() + ".", error);
            throw propagateBoundaryFailure("Failed to schedule Iris world-boundary application for '"
                    + expectedLevel.dimension().identifier() + "'.", error);
        }
    }

    @Override
    public void onServerTick(MinecraftServer server) {
        ExecutorService active = executor;
        if (active == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastPollAt < POLL_MILLIS) {
            return;
        }
        lastPollAt = now;
        List<ModdedDimensionManager.Handle> handles = ModdedDimensionManager.handles();
        Set<String> seen = null;
        for (ModdedDimensionManager.Handle handle : handles) {
            String dimensionId = handle.dimensionId();
            if (!dimensionId.startsWith(STUDIO_DIMENSION_PREFIX)) {
                continue;
            }
            IrisModdedChunkGenerator generator = handle.generator();
            if (generator == null) {
                continue;
            }
            Engine engine = generator.engineIfBound();
            if (engine == null || engine.isClosed()) {
                continue;
            }
            if (seen == null) {
                seen = new HashSet<>();
            }
            seen.add(dimensionId);
            Watch watch = watches.computeIfAbsent(dimensionId, (String key) -> new Watch());
            if (throttled(generator, engine, now)) {
                continue;
            }
            if (!watch.latch.flip()) {
                continue;
            }
            if (!watch.busy.compareAndSet(false, true)) {
                continue;
            }
            try {
                active.execute(() -> {
                    try {
                        poll(dimensionId, watch, generator, engine);
                    } finally {
                        watch.busy.set(false);
                    }
                });
            } catch (RejectedExecutionException rejected) {
                watch.busy.set(false);
            }
        }
        if (!watches.isEmpty()) {
            Set<String> keep = seen == null ? Set.of() : seen;
            watches.entrySet().removeIf((Map.Entry<String, Watch> entry) -> {
                if (keep.contains(entry.getKey())) {
                    return false;
                }
                entry.getValue().close();
                return true;
            });
        }
    }

    @Override
    public boolean isPregeneratorActive(Engine engine) {
        IrisWorld world = engine.getWorld();
        if (world == null) {
            return false;
        }
        PregeneratorJob pregeneratorJob = PregeneratorJob.getInstance();
        return pregeneratorJob != null && pregeneratorJob.targetsWorldIdentity(world.identity());
    }

    @Override
    public void shutdownPregenerator(Engine engine) {
        IrisWorld world = engine.getWorld();
        if (world != null) {
            ModdedPregenJob.shutdownForWorld(world.identity());
        }
    }

    @Override
    public boolean shouldSkipMantleCleanup(Engine engine) {
        IrisWorld world = engine.getWorld();
        return world != null
                && WorldMaintenance.isWorldMaintenanceActive(world.identity())
                && !isPregeneratorActive(engine);
    }

    private boolean throttled(IrisModdedChunkGenerator generator, Engine engine, long now) {
        if (now - generator.lastChunkGenAt() < RECENT_GENERATION_HOLDOFF_MILLIS) {
            return true;
        }
        return isPregeneratorActive(engine);
    }

    private void poll(String dimensionId, Watch watch, IrisModdedChunkGenerator generator, Engine engine) {
        try {
            if (watch.engine != engine) {
                watch.close();
                watch.engine = engine;
                watch.folder = new ReactiveFolder(
                        engine.getData().getDataFolder(),
                        (KList<File> created, KList<File> changed, KList<File> deleted) -> hotload(dimensionId, generator, engine),
                        new KList<>(".iob", ".json"),
                        new KList<>(".iris"),
                        new KList<>());
                return;
            }
            ReactiveFolder folder = watch.folder;
            if (folder != null) {
                folder.check();
            }
        } catch (Throwable e) {
            ModdedIrisLog.error("Iris studio hotload check failed for {}", dimensionId, e);
        }
    }

    private void hotload(String dimensionId, IrisModdedChunkGenerator generator, Engine engine) {
        if (engine.isClosed()) {
            return;
        }
        long start = System.currentTimeMillis();
        try {
            engine.hotloadSilently();
            ModdedIrisLog.info("Iris studio hotload {} pack={} {}ms", dimensionId, engine.getDimension().getLoadKey(), System.currentTimeMillis() - start);
        } catch (Throwable e) {
            ModdedIrisLog.error("Iris studio hotload failed for {}", dimensionId, e);
            throw new IllegalStateException("Iris studio hotload failed for " + dimensionId, e);
        }
    }

    static boolean hasDatapackImports(Iterable<IrisDimension> dimensions) {
        if (dimensions == null) {
            return false;
        }
        for (IrisDimension dimension : dimensions) {
            if (dimension != null
                    && dimension.getDatapackImports() != null
                    && !dimension.getDatapackImports().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    static void applyWorldBoundary(WorldBorder worldBorder, IrisWorldBoundary boundary) {
        if (boundary == null) {
            return;
        }
        worldBorder.setCenter(boundary.getCenter().getX(), boundary.getCenter().getZ());
        worldBorder.setSize(boundary.getSize());
        worldBorder.setWarningBlocks(boundary.getWarningDistance());
        worldBorder.setSafeZone(boundary.getDamageBuffer());
        worldBorder.setDamagePerBlock(boundary.getDamageAmount());
    }

    private static void applyWorldBoundary(Engine engine, IrisDimension expectedDimension,
                                           IrisWorld expectedIrisWorld, ServerLevel expectedLevel,
                                           IrisWorldBoundary boundary) {
        if (engine.isClosed() || engine.isClosing() || engine.getDimension() != expectedDimension
                || engine.getWorld() != expectedIrisWorld
                || expectedIrisWorld.platformWorld().nativeHandle() != expectedLevel) {
            return;
        }
        try {
            applyWorldBoundary(expectedLevel.getWorldBorder(), boundary);
        } catch (Throwable error) {
            ModdedIrisLog.error("Failed to apply Iris world boundary to "
                    + expectedLevel.dimension().identifier() + ".", error);
            throw propagateBoundaryFailure("Failed to apply Iris world boundary to '"
                    + expectedLevel.dimension().identifier() + "'.", error);
        }
    }

    private static RuntimeException propagateBoundaryFailure(String message, Throwable error) {
        if (error instanceof Error fatal) {
            throw fatal;
        }
        if (error instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(message, error);
    }

    private void writeWorkspace(Engine engine, String operation) {
        try {
            IrisData data = engine.getData();
            ModdedWorkspaceGenerator.writeWorkspace(data, data.getDataFolder());
        } catch (Throwable e) {
            ModdedIrisLog.error("Iris {} failed for {}", operation, engine.getDimension().getLoadKey(), e);
        }
    }

    private static final class Watch {
        private final ChronoLatch latch = new ChronoLatch(CHECK_LATCH_MILLIS, false);
        private final AtomicBoolean busy = new AtomicBoolean(false);
        private volatile Engine engine;
        private volatile ReactiveFolder folder;

        private void close() {
            ReactiveFolder active = folder;
            folder = null;
            if (active != null) {
                active.clear();
            }
            engine = null;
        }
    }
}
