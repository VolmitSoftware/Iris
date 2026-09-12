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
import art.arcane.iris.generation.validation.GoldenHashEngine;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.modded.ModdedBlockBuffer;
import art.arcane.iris.modded.ModdedEngineBootstrap;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.modded.localization.ModdedCommandMessages;
import art.arcane.iris.localization.RuntimeProgressMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
public final class ModdedGoldenHash {
    public enum Mode {
        AUTO,
        CAPTURE,
        VERIFY
    }

    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);

    private final CommandSourceStack source;
    private final MinecraftServer server;
    private final Engine engine;
    private final GoldenHashEngine hashEngine;

    private ModdedGoldenHash(CommandSourceStack source, ServerLevel level, Engine engine, int radius, int threads, Mode mode) {
        this.source = source;
        this.server = source.getServer();
        this.engine = engine;
        GoldenHashEngine.Mode engineMode = switch (mode) {
            case AUTO -> GoldenHashEngine.Mode.AUTO;
            case CAPTURE -> GoldenHashEngine.Mode.CAPTURE;
            case VERIFY -> GoldenHashEngine.Mode.VERIFY;
        };
        File goldenDir = ModdedEngineBootstrap.loader().configDir().resolve("irisworldgen").resolve("golden").toFile();
        GoldenHashEngine.Request request = new GoldenHashEngine.Request(
                engine.getWorld().name(),
                engine.getSeedManager().getSeed(),
                ModdedEngineBootstrap.loader().minecraftVersion(),
                engine.getMinHeight(),
                engine.getMaxHeight(),
                0,
                0,
                radius,
                threads,
                engineMode,
                true,
                false,
                GoldenHashEngine.FALLBACK_BIOME_KEY);
        this.hashEngine = new GoldenHashEngine(engine, request, goldenDir, this::snapshot, feedback(), progress());
    }

    public static void start(CommandSourceStack source, ServerLevel level, Engine engine, int radius, int threads, Mode mode) {
        if (!ACTIVE.compareAndSet(false, true)) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_GOLDEN_HASH_GOLDENHASH_SCAN_IS_ALREADY_RUNNING));
            return;
        }
        ModdedGoldenHash scan = new ModdedGoldenHash(source, level, engine, radius, threads, mode);
        int boundedRadius = Math.max(0, radius);
        int chunks = (boundedRadius * 2 + 1) * (boundedRadius * 2 + 1);
        scan.ok(IrisLanguage.plain(
                RuntimeProgressMessages.GOLDEN_STARTED,
                MessageArgument.trusted("chunks", chunks),
                MessageArgument.trusted("threads", Math.max(1, threads)),
                MessageArgument.untrusted("mode", mode)
        ));
        ModdedIrisLog.info("goldenhash start: dim={} seed={} radius={} threads={} mode={} file={}",
                engine.getDimension().getLoadKey(), engine.getSeedManager().getSeed(), boundedRadius, Math.max(1, threads), mode, scan.hashEngine.getGoldenFile().getName());
        Thread thread = new Thread(() -> {
            try {
                scan.hashEngine.run();
            } finally {
                ACTIVE.set(false);
            }
        }, "Iris GoldenHash");
        thread.setDaemon(true);
        thread.start();
    }

    private GoldenHashEngine.ChunkSnapshot snapshot(int chunkX, int chunkZ) throws Exception {
        int minY = engine.getMinHeight();
        int height = engine.getMaxHeight() - minY;
        PlatformBlockState air = IrisPlatforms.get().registries().air();
        ModdedBlockBuffer blocks = new ModdedBlockBuffer(height, air);
        Hunk<PlatformBiome> biomes = Hunk.newArrayHunk(16, height, 16);
        engine.generate(chunkX << 4, chunkZ << 4, blocks, biomes, false);
        return new GoldenHashEngine.ChunkSnapshot() {
            @Override
            public int minY() {
                return minY;
            }

            @Override
            public int maxY() {
                return minY + height;
            }

            @Override
            public PlatformBlockState block(int x, int y, int z) {
                return blocks.get(x, y - minY, z);
            }

            @Override
            public PlatformBiome biome(int x, int y, int z) {
                return biomes.get(x, y - minY, z);
            }
        };
    }

    private GoldenHashEngine.Feedback feedback() {
        return new GoldenHashEngine.Feedback() {
            @Override
            public void ok(String message) {
                ModdedGoldenHash.this.ok(message);
            }

            @Override
            public void warn(String message) {
                ModdedGoldenHash.this.ok(message);
            }

            @Override
            public void fail(String message) {
                ModdedGoldenHash.this.fail(message);
            }
        };
    }

    private GoldenHashEngine.Progress progress() {
        return new GoldenHashEngine.Progress() {
            private final AtomicInteger hashed = new AtomicInteger();

            @Override
            public void chunkDone(int chunkX, int chunkZ, boolean ok, int done, int total) {
                if (!ok) {
                    return;
                }
                int doneCount = hashed.incrementAndGet();
                int stride = total <= 64 ? 1 : 32;
                if (doneCount % stride == 0 || doneCount == total) {
                    ModdedGoldenHash.this.ok(IrisLanguage.plain(
                            RuntimeProgressMessages.GOLDEN_CHUNK_HASHED,
                            MessageArgument.trusted("done", doneCount),
                            MessageArgument.trusted("total", total),
                            MessageArgument.trusted("x", chunkX),
                            MessageArgument.trusted("z", chunkZ)
                    ));
                }
            }

            @Override
            public void chunkFailed(int chunkX, int chunkZ, Throwable error) {
                ModdedIrisLog.error("goldenhash chunk {},{} failed", chunkX, chunkZ, error);
                ModdedGoldenHash.this.fail(IrisLanguage.plain(
                        RuntimeProgressMessages.GOLDEN_CHUNK_FAILED,
                        MessageArgument.trusted("x", chunkX),
                        MessageArgument.trusted("z", chunkZ),
                        MessageArgument.untrusted("type", error.getClass().getSimpleName())
                ));
            }
        };
    }

    private void ok(String message) {
        server.execute(() -> IrisModdedCommands.ok(source, message));
    }

    private void fail(String message) {
        server.execute(() -> IrisModdedCommands.fail(source, message));
    }
}
