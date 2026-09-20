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

package art.arcane.iris.modded.command;

import art.arcane.iris.modded.ModdedIrisLog;
import art.arcane.iris.world.WorldMaintenance;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.modded.IrisModdedChunkGenerator;
import art.arcane.iris.modded.ModdedBlockBuffer;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.math.ChunkSpiral;
import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandSource;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeChunkRegeneration;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolPlayer;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.modded.localization.ModdedCommandMessages;
public final class ModdedRegen {
    private static final int APPLY_AHEAD = 8;
    private static final long CHUNK_SLOT_TIMEOUT_MILLIS = 120000L;
    private static final long FINAL_APPLY_TIMEOUT_MILLIS = 300000L;
    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);

    private final NativeCommandSource source;
    private final NativeChunkRegeneration regeneration;
    private final IrisModdedChunkGenerator generator;
    private final Engine engine;
    private final int centerX;
    private final int centerZ;
    private final int radius;

    private ModdedRegen(NativeCommandSource source, NativeWorld level, IrisModdedChunkGenerator generator, Engine engine, int centerX, int centerZ, int radius) {
        this.source = source;
        this.regeneration = new NativeChunkRegeneration(level);
        this.generator = generator;
        this.engine = engine;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = Math.max(0, radius);
    }

    public static void start(NativeCommandSource source, NativeWorld level, IrisModdedChunkGenerator generator, Engine engine, NativeProtocolPlayer player, int radius) {
        if (!ACTIVE.compareAndSet(false, true)) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_REGEN_REGEN_IS_ALREADY_RUNNING));
            return;
        }
        int centerX = player.blockX() >> 4;
        int centerZ = player.blockZ() >> 4;
        ModdedRegen job = new ModdedRegen(source, level, generator, engine, centerX, centerZ, radius);
        int chunks = (job.radius * 2 + 1) * (job.radius * 2 + 1);
        job.ok("Regen started: " + chunks + " chunk(s) around " + centerX + "," + centerZ + ". Deleting and regenerating in place.");
        ModdedIrisLog.info("Iris regen start: dim={} center={},{} radius={} chunks={}",
                level.name(), centerX, centerZ, job.radius, chunks);
        Thread thread = new Thread(job::run, "Iris Regenerate");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        long startedAt = M.ms();
        String worldIdentity = engine.getWorld() == null ? null : engine.getWorld().identity();
        WorldMaintenance.beginWorldMaintenance(worldIdentity, "regen");
        try {
            resetMantleMargin();
            List<int[]> targets = ChunkSpiral.centerOut(centerX, centerZ, radius);
            int applied = regenerate(targets);
            ok("Regen finished: " + applied + "/" + targets.size() + " chunk(s) in " + Form.duration(M.ms() - startedAt, 2));
            ModdedIrisLog.info("Iris regen done: {}/{} chunks in {}ms", applied, targets.size(), M.ms() - startedAt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable e) {
            ModdedIrisLog.error("Iris regen failed", e);
            fail("Regen failed: " + e);
        } finally {
            WorldMaintenance.endWorldMaintenance(worldIdentity, "regen");
            ACTIVE.set(false);
        }
    }

    private void resetMantleMargin() {
        EngineMantle engineMantle = engine.getMantle();
        int margin = radius + Math.max(engineMantle.getRadius(), engineMantle.getRealRadius()) + 1;
        for (int dx = -margin; dx <= margin; dx++) {
            for (int dz = -margin; dz <= margin; dz++) {
                engineMantle.getMantle().deleteChunk(centerX + dx, centerZ + dz);
            }
        }
    }

    private int regenerate(List<int[]> targets) throws InterruptedException {
        Semaphore inFlight = new Semaphore(APPLY_AHEAD);
        CountDownLatch allApplied = new CountDownLatch(targets.size());
        AtomicBoolean aborted = new AtomicBoolean(false);
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger applied = new AtomicInteger();
        int total = targets.size();
        int stride = total <= 64 ? 1 : 32;
        int height = engine.getMaxHeight() - engine.getMinHeight();
        NativeBlockState air = IrisPlatforms.get().registries().air();

        for (int[] target : targets) {
            int chunkX = target[0];
            int chunkZ = target[1];
            if (!inFlight.tryAcquire(CHUNK_SLOT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                aborted.set(true);
                ModdedIrisLog.error("Iris regen aborted: chunk {},{} waited {}ms for an apply slot ({}/{} done)",
                        chunkX, chunkZ, CHUNK_SLOT_TIMEOUT_MILLIS, completed.get(), total);
                fail("Regen aborted: apply pipeline stalled at " + completed.get() + "/" + total + " chunk(s)");
                break;
            }
            MultiBurst.burst.lazy(() -> {
                long chunkStart = M.ms();
                if (aborted.get()) {
                    completed.incrementAndGet();
                    inFlight.release();
                    allApplied.countDown();
                    return;
                }
                ModdedBlockBuffer blocks = new ModdedBlockBuffer(height, air);
                Hunk<NativeBiome> biomes = Hunk.newArrayHunk(16, height, 16);
                try {
                    engine.generate(chunkX << 4, chunkZ << 4, blocks, biomes, false);
                } catch (Throwable e) {
                    ModdedIrisLog.error("Iris regen chunk {},{} generation failed", chunkX, chunkZ, e);
                    fail("Chunk " + chunkX + "," + chunkZ + " generation FAILED: " + e.getClass().getSimpleName());
                    completed.incrementAndGet();
                    inFlight.release();
                    allApplied.countDown();
                    return;
                }
                source.execute(() -> {
                    boolean success = false;
                    try {
                        if (aborted.get()) {
                            return;
                        }
                        apply(chunkX, chunkZ, blocks, biomes);
                        success = true;
                        applied.incrementAndGet();
                    } catch (Throwable e) {
                        ModdedIrisLog.error("Iris regen chunk {},{} apply failed", chunkX, chunkZ, e);
                        fail("Chunk " + chunkX + "," + chunkZ + " apply FAILED: " + e.getClass().getSimpleName());
                    } finally {
                        int done = completed.incrementAndGet();
                        if (success && (done % stride == 0 || done == total)) {
                            ok("Regen [" + done + "/" + total + "] chunk " + chunkX + "," + chunkZ + " in " + (M.ms() - chunkStart) + "ms");
                        }
                        inFlight.release();
                        allApplied.countDown();
                    }
                });
            });
        }

        if (aborted.get()) {
            // Targets were never submitted, so the latch can no longer reach zero; in-flight tasks
            // observe the abort flag and release themselves.
            return applied.get();
        }
        if (!allApplied.await(FINAL_APPLY_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            aborted.set(true);
            long outstanding = allApplied.getCount();
            ModdedIrisLog.error("Iris regen aborted: {} of {} chunk(s) did not finish within {}ms",
                    outstanding, total, FINAL_APPLY_TIMEOUT_MILLIS);
            fail("Regen aborted: " + outstanding + " of " + total + " chunk(s) never finished");
        }
        return applied.get();
    }

    private void apply(int chunkX, int chunkZ, ModdedBlockBuffer blocks, Hunk<NativeBiome> biomes) {
        regeneration.apply(new NativeChunkRegeneration.Replacement(chunkX, chunkZ,
                        engine.getMinHeight(), engine.getMaxHeight() - engine.getMinHeight(),
                        blocks::rawOrNull, generator.regenBiomeResolver()),
                () -> generator.updateRegeneratedChunk(chunkX, chunkZ));
    }

    private void ok(String message) {
        source.execute(() -> IrisModdedCommands.ok(source, message));
    }

    private void fail(String message) {
        source.execute(() -> IrisModdedCommands.fail(source, message));
    }
}
