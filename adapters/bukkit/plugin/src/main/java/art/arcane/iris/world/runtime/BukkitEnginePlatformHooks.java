/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.world.runtime;

import art.arcane.iris.api.world.IrisWorldPhase;
import art.arcane.iris.pack.datapack.ServerConfigurator;
import art.arcane.iris.pack.datapack.DatapackIngestService;
import art.arcane.iris.world.event.IrisEngineHotloadEvent;
import art.arcane.iris.studio.view.PregeneratorJob;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.studio.workspace.IrisProject;
import art.arcane.iris.studio.workspace.IrisCodeWorkspace;
import art.arcane.iris.platform.bukkit.api.IrisApiEventSVC;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.world.WorldMaintenance;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.world.history.SavedTerrainChunk;

import java.util.concurrent.CompletableFuture;
import art.arcane.iris.generation.runtime.EngineMode;
import art.arcane.iris.generation.runtime.EnginePlatformHooks;
import art.arcane.iris.structure.nativegen.NativeStructureVolume;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionRuntimeContract;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.world.IrisWorldBoundary;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.collection.KList;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;

public final class BukkitEnginePlatformHooks implements EnginePlatformHooks {
    @Override
    public CompletableFuture<Void> flushSavedTerrainCapture(Engine engine) {
        return INMS.get().flushSavedTerrainCapture(BukkitWorldBinding.world(engine.getWorld()));
    }

    @Override
    public CompletableFuture<SavedTerrainChunk> captureSavedTerrainChunk(Engine engine, int chunkX, int chunkZ) {
        return BukkitSavedTerrainCapture.capture(engine, chunkX, chunkZ);
    }

    @Override
    public KList<NativeStructureVolume> nativeStructureVolumes(Engine engine, int minX, int minZ, int maxX, int maxZ) {
        return INMS.get().nativeStructureVolumes(engine, minX, minZ, maxX, maxZ);
    }

    @Override
    public void refreshWorkspace(Engine engine) {
        new IrisCodeWorkspace(new IrisProject(engine.getPackSource().toFile())).updateWorkspace();
    }

    @Override
    public void refreshDatapackWorkspace(Engine engine) {
        IrisData data = IrisData.openRuntime(engine.getPackSource().toFile());
        try {
            DatapackIngestService.refreshWorkspace(data);
        } finally {
            data.close();
        }
    }

    @Override
    public void reloadDatapacks(Engine engine) {
        synchronized (ServerConfigurator.class) {
            ServerConfigurator.installDataPacks(false);
        }
    }

    @Override
    public void fireHotloadEvent(Engine engine) {
        IrisPlatforms.get().callEvent(new IrisEngineHotloadEvent(engine));
        IrisApiEventSVC.fireWorldPhase(BukkitWorldBinding.world(engine.getWorld()), IrisWorldPhase.ENGINE_HOTLOADED);
    }

    @Override
    public void validateDimensionHotload(Engine engine, IrisDimension replacement) {
        IrisDimensionRuntimeContract.requireHotloadCompatible(
                "Bukkit Studio world '" + engine.getWorld().name() + "'",
                engine.getDimension(),
                replacement,
                "iris");
    }

    @Override
    public void prepareRuntimeHotload(Engine engine) {
        INMS.get().invalidateNativeStructureVolumeIndex(engine);
    }

    @Override
    public void applyWorldBoundary(Engine engine) {
        IrisDimension expectedDimension = engine.getDimension();
        IrisWorldBoundary configuredBoundary = expectedDimension.getWorldBoundary();
        if (configuredBoundary == null) {
            return;
        }
        IrisWorld expectedIrisWorld = engine.getWorld();
        World expectedWorld = BukkitWorldBinding.world(expectedIrisWorld);
        if (expectedWorld == null) {
            return;
        }
        IrisWorldBoundary boundary;
        try {
            boundary = IrisWorldBoundary.snapshot(configuredBoundary);
        } catch (Throwable error) {
            IrisLogging.error("Invalid Iris world boundary for " + expectedWorld.getName() + ".");
            IrisLogging.reportError(error);
            throw propagateBoundaryFailure("Invalid Iris world boundary for '" + expectedWorld.getName() + "'.", error);
        }
        if (!J.runGlobal(() -> applyWorldBoundary(engine, expectedDimension, expectedIrisWorld, expectedWorld, boundary))) {
            IllegalStateException failure = new IllegalStateException(
                    "Bukkit global scheduler rejected world-boundary application for '" + expectedWorld.getName() + "'.");
            IrisLogging.error(failure.getMessage());
            IrisLogging.reportError(failure);
            throw failure;
        }
    }

    @Override
    public boolean isMainThread() {
        return Bukkit.isPrimaryThread();
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
            PregeneratorJob.shutdownInstanceForWorld(world.identity());
        }
    }

    @Override
    public boolean shouldDisableChunkContextCache(Engine engine) {
        IrisWorld world = engine.getWorld();
        if (!J.isFolia() || world == null || !world.hasPlatformWorld()) {
            return false;
        }
        boolean maintenanceActive = WorldMaintenance.isWorldMaintenanceActive(world.identity());
        return EngineMode.shouldDisableContextCacheForMaintenance(maintenanceActive, isPregeneratorActive(engine));
    }

    @Override
    public boolean shouldSkipMantleCleanup(Engine engine) {
        IrisWorld world = engine.getWorld();
        return world != null
                && WorldMaintenance.isWorldMaintenanceActive(world.identity())
                && !isPregeneratorActive(engine);
    }

    @Override
    public boolean shouldSkipMantleMarkerRead(Engine engine, int chunkX, int chunkZ) {
        IrisWorld irisWorld = engine.getWorld();
        if (!J.isFolia() || irisWorld == null || !irisWorld.hasPlatformWorld()) {
            return false;
        }
        World world = BukkitWorldBinding.world(irisWorld);
        return world != null && J.isOwnedByCurrentRegion(world, chunkX, chunkZ);
    }

    @Override
    public boolean shouldBypassMantleStages(Engine engine) {
        if (!J.isFolia() || !engine.getWorld().hasPlatformWorld()) {
            return false;
        }
        World world = BukkitWorldBinding.world(engine.getWorld());
        return world != null && IrisToolbelt.isWorldMaintenanceBypassingMantleStages(world);
    }

    static void applyWorldBoundary(WorldBorder worldBorder, IrisWorldBoundary boundary) {
        if (boundary == null) {
            return;
        }
        worldBorder.setCenter(boundary.getCenter().getX(), boundary.getCenter().getZ());
        worldBorder.setSize(boundary.getSize());
        worldBorder.setWarningDistance(boundary.getWarningDistance());
        worldBorder.setDamageBuffer(boundary.getDamageBuffer());
        worldBorder.setDamageAmount(boundary.getDamageAmount());
    }

    private static void applyWorldBoundary(Engine engine, IrisDimension expectedDimension,
                                           IrisWorld expectedIrisWorld, World expectedWorld,
                                           IrisWorldBoundary boundary) {
        if (engine.isClosed() || engine.isClosing() || engine.getDimension() != expectedDimension
                || engine.getWorld() != expectedIrisWorld
                || BukkitWorldBinding.world(expectedIrisWorld) != expectedWorld) {
            return;
        }
        try {
            applyWorldBoundary(expectedWorld.getWorldBorder(), boundary);
        } catch (Throwable error) {
            IrisLogging.error("Failed to apply Iris world boundary to " + expectedWorld.getName() + ".");
            IrisLogging.reportError(error);
            throw propagateBoundaryFailure("Failed to apply Iris world boundary to '"
                    + expectedWorld.getName() + "'.", error);
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
}
