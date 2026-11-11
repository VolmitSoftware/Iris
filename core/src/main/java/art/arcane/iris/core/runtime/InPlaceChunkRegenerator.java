/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.runtime;

import art.arcane.iris.core.localization.IrisLanguage;
import art.arcane.iris.core.localization.RuntimeProgressMessages;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.engine.data.chunk.TerrainChunk;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.engine.platform.BukkitChunkGenerator;
import art.arcane.iris.engine.platform.BukkitChunkGenerator.GenerationStagePermit;
import art.arcane.iris.engine.platform.EngineBukkitOps;
import io.papermc.lib.PaperLib;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.WrongEngineBroException;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class InPlaceChunkRegenerator {
    private static final int BIOME_STEP = 4;
    private static final int APPLY_AHEAD = 8;
    private static final int PROBE_BATCH_CHUNKS = 256;
    private static final String GENERATION_STAGE = "regen";

    private final World world;
    private final Engine engine;
    private final int centerChunkX;
    private final int centerChunkZ;
    private final int radius;
    private final ChunkJobReporter reporter;

    public InPlaceChunkRegenerator(World world, Engine engine, VolmitSender sender, int centerChunkX, int centerChunkZ, int radius) {
        this.world = world;
        this.engine = engine;
        this.centerChunkX = centerChunkX;
        this.centerChunkZ = centerChunkZ;
        this.radius = Math.max(0, radius);
        this.reporter = new ChunkJobReporter(sender, IrisLanguage.text(RuntimeProgressMessages.CHUNK_TITLE_REGEN), world);
    }

    public void start() {
        reporter.start();
        Thread thread = new Thread(this::run, "Iris Regenerate");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        boolean error = false;
        try {
            reporter.setStage(IrisLanguage.text(RuntimeProgressMessages.CHUNK_STAGE_RESETTING_MANTLE));
            resetMantleMargin();

            List<int[]> targets = ChunkJobReporter.orderedTargets(centerChunkX, centerChunkZ, radius);
            reporter.setTotal(targets.size());
            reporter.setStage(IrisLanguage.text(RuntimeProgressMessages.CHUNK_STAGE_REGENERATING));
            regenerate(targets);
            logSkippedChunks();
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            error = true;
        } finally {
            reporter.finish(error);
        }
    }

    private void logSkippedChunks() {
        if (reporter.skipped() == 0) {
            return;
        }
        IrisLogging.info("Regen skipped " + reporter.skipped() + " chunks in " + world.getName()
                + " that were never generated; they generate fresh when visited.");
    }

    private void resetMantleMargin() {
        EngineMantle engineMantle = engine.getMantle();
        Mantle mantle = engineMantle.getMantle();
        int margin = radius + Math.max(engineMantle.getRadius(), engineMantle.getRealRadius()) + 1;
        for (int dx = -margin; dx <= margin; dx++) {
            for (int dz = -margin; dz <= margin; dz++) {
                mantle.deleteChunk(centerChunkX + dx, centerChunkZ + dz);
            }
        }
    }

    private void regenerate(List<int[]> targets) throws InterruptedException {
        Semaphore inFlight = new Semaphore(APPLY_AHEAD);

        for (int from = 0; from < targets.size(); from += PROBE_BATCH_CHUNKS) {
            List<int[]> batch = targets.subList(from, Math.min(targets.size(), from + PROBE_BATCH_CHUNKS));
            for (int[] generated : generatedChunksOf(batch)) {
                inFlight.acquire();
                MultiBurst.burst.lazy(() -> regenerateChunk(generated[0], generated[1], inFlight::release));
            }
        }

        inFlight.acquire(APPLY_AHEAD);
    }

    /**
     * Chunks that were never generated are skipped: their mantle is reset, so they generate fresh when first visited.
     * Regenerating them here would generate each one twice, and loading them on the server thread stalls it.
     */
    private List<int[]> generatedChunksOf(List<int[]> batch) {
        List<CompletableFuture<Chunk>> loads = new ArrayList<>(batch.size());
        for (int[] target : batch) {
            loads.add(loadIfGenerated(target[0], target[1]));
        }

        List<int[]> generated = new ArrayList<>(batch.size());
        for (int index = 0; index < batch.size(); index++) {
            try {
                if (loads.get(index).join() == null) {
                    reporter.countSkipped();
                    continue;
                }
                generated.add(batch.get(index));
            } catch (CompletionException | CancellationException e) {
                IrisLogging.reportError(e);
                reporter.countApplied(false);
            }
        }
        return generated;
    }

    private CompletableFuture<Chunk> loadIfGenerated(int chunkX, int chunkZ) {
        return PaperLib.getChunkAtAsync(world, chunkX, chunkZ, false);
    }

    private void regenerateChunk(int chunkX, int chunkZ, Runnable release) {
        // Settle exactly once no matter which path fails: an unguarded throw stranded the regen thread and the
        // player's boss bar. CAS is required because the apply can run inline, before the scheduling call returns.
        AtomicBoolean settled = new AtomicBoolean();
        Consumer<Boolean> settle = ok -> {
            if (settled.compareAndSet(false, true)) {
                reporter.countApplied(ok);
                release.run();
            }
        };
        try {
            TerrainChunk buffer = generate(chunkX, chunkZ);
            loadIfGenerated(chunkX, chunkZ).whenComplete((chunk, loadFailure) -> {
                if (!scheduledApply(chunk, buffer, settle)) {
                    settle.accept(false);
                }
            });
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            settle.accept(false);
        }
    }

    /**
     * @return false when the apply could not be handed to the thread owning the chunk, so the caller settles the chunk
     * as failed; true leaves settling to the apply.
     */
    private boolean scheduledApply(Chunk chunk, TerrainChunk buffer, Consumer<Boolean> settle) {
        if (chunk == null) {
            IrisLogging.warn("Regen could not load a chunk it found generated in " + world.getName() + ".");
            return false;
        }
        try {
            return runOnThreadOwning(chunk, () -> settle.accept(applied(chunk, buffer)));
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            return false;
        }
    }

    /** Holds a generation stage like any chunk the server generates, so a hotload waits for it instead of rejecting it. */
    private TerrainChunk generate(int chunkX, int chunkZ) throws WrongEngineBroException {
        TerrainChunk buffer = TerrainChunk.create(world);
        try (GenerationStagePermit ignored = acquireGenerationStage()) {
            engine.generate(chunkX << 4, chunkZ << 4, buffer, false);
        }
        return buffer;
    }

    private GenerationStagePermit acquireGenerationStage() {
        if (IrisToolbelt.access(world) instanceof BukkitChunkGenerator generator) {
            return generator.acquireGenerationStage(GENERATION_STAGE);
        }
        return GenerationStagePermit.noop();
    }

    private boolean runOnThreadOwning(Chunk chunk, Runnable task) {
        if (!J.isFolia() && J.isPrimaryThread()) {
            task.run();
            return true;
        }
        return J.runRegion(world, chunk.getX(), chunk.getZ(), task);
    }

    private boolean applied(Chunk chunk, TerrainChunk buffer) {
        try {
            applyToLiveChunk(chunk, buffer);
            return true;
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            return false;
        }
    }

    private void applyToLiveChunk(Chunk chunk, TerrainChunk buffer) {
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        for (Entity entity : chunk.getEntities()) {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        }

        int minHeight = world.getMinHeight();
        int maxHeight = world.getMaxHeight();
        if (!INMS.get().applyChunkBlocks(chunk, buffer)) {
            applyBlockDiffs(chunk, chunk.getChunkSnapshot(false, false, false), buffer.getChunkData(), minHeight, maxHeight);
        }

        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        for (int x = 0; x < 16; x += BIOME_STEP) {
            for (int z = 0; z < 16; z += BIOME_STEP) {
                for (int y = minHeight; y < maxHeight; y += BIOME_STEP) {
                    PlatformBiome biome = buffer.getBiome(x, y, z);
                    if (biome != null) {
                        Biome bukkitBiome = (Biome) biome.nativeHandle();
                        if (world.getBiome(baseX + x, y, baseZ + z) != bukkitBiome) {
                            world.setBiome(baseX + x, y, baseZ + z, bukkitBiome);
                        }
                    }
                }
            }
        }

        EngineBukkitOps.updateChunk(engine, chunk);
        world.refreshChunk(chunkX, chunkZ);
    }

    public static void applyBlockDiffs(
            Chunk chunk,
            ChunkData generated,
            int minHeight,
            int maxHeight
    ) {
        applyBlockDiffs(
                chunk,
                chunk.getChunkSnapshot(false, false, false),
                generated,
                minHeight,
                maxHeight);
    }

    public static void applyBlockDiffs(
            Chunk chunk,
            ChunkSnapshot snapshot,
            ChunkData generated,
            int minHeight,
            int maxHeight
    ) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minHeight; y < maxHeight; y++) {
                    Material target = generated.getType(x, y, z);
                    if (snapshot.getBlockType(x, y, z) != target) {
                        chunk.getBlock(x, y, z).setBlockData(generated.getBlockData(x, y, z), false);
                        continue;
                    }

                    if (target == Material.AIR) {
                        continue;
                    }

                    BlockData data = generated.getBlockData(x, y, z);
                    if (!data.equals(snapshot.getBlockData(x, y, z))) {
                        chunk.getBlock(x, y, z).setBlockData(data, false);
                    }
                }
            }
        }
    }
}
