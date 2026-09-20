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
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandSource;

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

    private final NativeCommandSource source;
    private final Engine engine;
    private final GoldenHashEngine hashEngine;

    private ModdedGoldenHash(NativeCommandSource source, Engine engine, ScanOptions options) {
        this.source = source;
        this.engine = engine;
        GoldenHashEngine.Mode engineMode = switch (options.mode()) {
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
                options.radius(),
                options.threads(),
                engineMode,
                true,
                false,
                GoldenHashEngine.FALLBACK_BIOME_KEY);
        this.hashEngine = new GoldenHashEngine(engine, request, goldenDir, this::snapshot, feedback(), progress());
    }

    public static void start(NativeCommandSource source, Engine engine, ScanOptions options) {
        if (!ACTIVE.compareAndSet(false, true)) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_GOLDEN_HASH_GOLDENHASH_SCAN_IS_ALREADY_RUNNING));
            return;
        }
        ModdedGoldenHash scan = new ModdedGoldenHash(source, engine, options);
        int boundedRadius = Math.max(0, options.radius());
        int chunks = (boundedRadius * 2 + 1) * (boundedRadius * 2 + 1);
        scan.ok(IrisLanguage.plain(
                RuntimeProgressMessages.GOLDEN_STARTED,
                MessageArgument.trusted("chunks", chunks),
                MessageArgument.trusted("threads", Math.max(1, options.threads())),
                MessageArgument.untrusted("mode", options.mode())
        ));
        ModdedIrisLog.info("goldenhash start: dim={} seed={} radius={} threads={} mode={} file={}",
                engine.getDimension().getLoadKey(), engine.getSeedManager().getSeed(), boundedRadius, Math.max(1, options.threads()), options.mode(), scan.hashEngine.getGoldenFile().getName());
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
        NativeBlockState air = IrisPlatforms.get().registries().air();
        ModdedBlockBuffer blocks = new ModdedBlockBuffer(height, air);
        Hunk<NativeBiome> biomes = Hunk.newArrayHunk(16, height, 16);
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
            public NativeBlockState block(int x, int y, int z) {
                return blocks.get(x, y - minY, z);
            }

            @Override
            public NativeBiome biome(int x, int y, int z) {
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

    public record ScanOptions(int radius, int threads, Mode mode) {
    }

    private void ok(String message) {
        source.execute(() -> IrisModdedCommands.ok(source, message));
    }

    private void fail(String message) {
        source.execute(() -> IrisModdedCommands.fail(source, message));
    }
}
