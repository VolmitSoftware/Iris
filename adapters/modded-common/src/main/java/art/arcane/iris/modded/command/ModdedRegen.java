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
import art.arcane.iris.modded.ModdedEngineBootstrap;
import art.arcane.iris.modded.service.ModdedChunkUpdateService;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.math.ChunkSpiral;
import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
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

    private final CommandSourceStack source;
    private final MinecraftServer server;
    private final ServerLevel level;
    private final IrisModdedChunkGenerator generator;
    private final Engine engine;
    private final int centerX;
    private final int centerZ;
    private final int radius;

    private ModdedRegen(CommandSourceStack source, ServerLevel level, IrisModdedChunkGenerator generator, Engine engine, int centerX, int centerZ, int radius) {
        this.source = source;
        this.server = source.getServer();
        this.level = level;
        this.generator = generator;
        this.engine = engine;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = Math.max(0, radius);
    }

    public static void start(CommandSourceStack source, ServerLevel level, IrisModdedChunkGenerator generator, Engine engine, ServerPlayer player, int radius) {
        if (!ACTIVE.compareAndSet(false, true)) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_REGEN_REGEN_IS_ALREADY_RUNNING));
            return;
        }
        int centerX = player.blockPosition().getX() >> 4;
        int centerZ = player.blockPosition().getZ() >> 4;
        ModdedRegen job = new ModdedRegen(source, level, generator, engine, centerX, centerZ, radius);
        int chunks = (job.radius * 2 + 1) * (job.radius * 2 + 1);
        job.ok("Regen started: " + chunks + " chunk(s) around " + centerX + "," + centerZ + ". Deleting and regenerating in place.");
        ModdedIrisLog.info("Iris regen start: dim={} center={},{} radius={} chunks={}",
                level.dimension().identifier(), centerX, centerZ, job.radius, chunks);
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
        PlatformBlockState air = IrisPlatforms.get().registries().air();

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
                Hunk<PlatformBiome> biomes = Hunk.newArrayHunk(16, height, 16);
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
                server.execute(() -> {
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

    private void apply(int chunkX, int chunkZ, ModdedBlockBuffer blocks, Hunk<PlatformBiome> biomes) {
        LevelChunk chunk = level.getChunk(chunkX, chunkZ);
        ChunkPos pos = chunk.getPos();
        discardEntities(pos);
        for (BlockPos blockEntityPos : new ArrayList<>(chunk.getBlockEntities().keySet())) {
            chunk.removeBlockEntity(blockEntityPos);
        }

        int dimMinY = engine.getMinHeight();
        int height = engine.getMaxHeight() - dimMinY;
        int baseX = pos.getMinBlockX();
        int baseZ = pos.getMinBlockZ();
        BlockState airState = Blocks.AIR.defaultBlockState();
        ThreadedLevelLightEngine lightEngine = (ThreadedLevelLightEngine) level.getChunkSource().getLightEngine();
        List<BlockPos> lightChecks = new ArrayList<>();

        for (int i = 0; i < chunk.getSectionsCount(); i++) {
            LevelChunkSection section = chunk.getSection(i);
            int sectionBaseY = chunk.getSectionYFromSectionIndex(i) << 4;
            section.acquire();
            try {
                for (int y = 0; y < 16; y++) {
                    int worldY = sectionBaseY + y;
                    int bufferY = worldY - dimMinY;
                    boolean inRange = bufferY >= 0 && bufferY < height;
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            BlockState target = airState;
                            if (inRange && !blocks.isAir(x, bufferY, z)) {
                                target = (BlockState) blocks.getRaw(x, bufferY, z).nativeHandle();
                            }
                            BlockState previous = section.setBlockState(x, y, z, target, false);
                            if (previous != target && (previous.getLightEmission() != target.getLightEmission()
                                    || previous.getLightDampening() != target.getLightDampening()
                                    || previous.propagatesSkylightDown() != target.propagatesSkylightDown())) {
                                lightChecks.add(new BlockPos(baseX + x, worldY, baseZ + z));
                            }
                            if (target.hasBlockEntity() && target.getBlock() instanceof EntityBlock entityBlock) {
                                BlockEntity entity = entityBlock.newBlockEntity(new BlockPos(baseX + x, worldY, baseZ + z), target);
                                if (entity != null) {
                                    chunk.setBlockEntity(entity);
                                }
                            }
                        }
                    }
                }
            } finally {
                section.release();
            }
            lightEngine.updateSectionStatus(SectionPos.of(pos, chunk.getSectionYFromSectionIndex(i)), section.hasOnlyAir());
        }

        ModdedChunkUpdateService updateService = ModdedEngineBootstrap.services().service(ModdedChunkUpdateService.class);
        if (updateService == null) {
            throw new IllegalStateException("Iris chunk update service is unavailable during regeneration");
        }
        updateService.updateRegeneratedChunk(engine, level, chunkX, chunkZ);
        Heightmap.primeHeightmaps(chunk, ChunkStatus.FULL.heightmapsAfter());
        chunk.fillBiomesFromNoise(generator.regenBiomeResolver(), level.getChunkSource().randomState().sampler());
        chunk.markUnsaved();

        for (BlockPos check : lightChecks) {
            lightEngine.checkBlock(check);
        }

        for (ServerPlayer tracking : level.getChunkSource().chunkMap.getPlayers(pos, false)) {
            tracking.connection.chunkSender.dropChunk(tracking, pos);
            tracking.connection.chunkSender.markChunkPendingToSend(chunk);
        }
    }

    private void discardEntities(ChunkPos pos) {
        AABB box = new AABB(pos.getMinBlockX(), level.getMinY(), pos.getMinBlockZ(),
                pos.getMaxBlockX() + 1, level.getMaxY() + 1, pos.getMaxBlockZ() + 1);
        for (Entity entity : level.getEntities((Entity) null, box, (Entity e) -> !(e instanceof ServerPlayer))) {
            entity.discard();
        }
    }

    private void ok(String message) {
        server.execute(() -> IrisModdedCommands.ok(source, message));
    }

    private void fail(String message) {
        server.execute(() -> IrisModdedCommands.fail(source, message));
    }
}
