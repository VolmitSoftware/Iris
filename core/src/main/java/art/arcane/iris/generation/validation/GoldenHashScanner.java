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

package art.arcane.iris.generation.validation;

import art.arcane.iris.world.runtime.ChunkJobReporter;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.RuntimeProgressMessages;
import art.arcane.iris.generation.chunk.TerrainChunk;
import art.arcane.iris.generation.chunk.ChunkDataHunkView;
import art.arcane.iris.generation.chunk.TerrainChunkBiomeHunkView;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.localization.C;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import org.bukkit.Bukkit;
import org.bukkit.World;

public final class GoldenHashScanner {
    private final World world;
    private final Engine engine;
    private final VolmitSender sender;
    private final ChunkJobReporter reporter;
    private final GoldenHashEngine hashEngine;

    public GoldenHashScanner(World world, Engine engine, VolmitSender sender, int centerChunkX, int centerChunkZ, int radius, boolean resetMantle, int threads, boolean deep) {
        this.world = world;
        this.engine = engine;
        this.sender = sender;
        this.reporter = new ChunkJobReporter(sender, IrisLanguage.text(RuntimeProgressMessages.CHUNK_TITLE_GOLDEN_HASH), world);
        GoldenHashEngine.Request request = new GoldenHashEngine.Request(
                world.getName(),
                world.getSeed(),
                Bukkit.getBukkitVersion(),
                world.getMinHeight(),
                world.getMaxHeight(),
                centerChunkX,
                centerChunkZ,
                radius,
                threads,
                GoldenHashEngine.Mode.AUTO,
                resetMantle,
                deep,
                GoldenHashEngine.FALLBACK_BIOME_KEY);
        this.hashEngine = new GoldenHashEngine(engine, request, IrisPlatforms.get().dataFolder("golden"), this::snapshot, feedback(), progress());
    }

    public static boolean isScanActive() {
        return GoldenHashEngine.isActive();
    }

    public void start() {
        reporter.start();
        Thread thread = new Thread(this::run, "Iris GoldenHash");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        boolean ok = false;
        try {
            ok = hashEngine.run();
        } finally {
            reporter.finish(!ok);
        }
    }

    private GoldenHashEngine.ChunkSnapshot snapshot(int chunkX, int chunkZ) throws Exception {
        TerrainChunk buffer = TerrainChunk.create(world);
        engine.generate(chunkX << 4, chunkZ << 4, new ChunkDataHunkView(buffer.getChunkData()),
                new TerrainChunkBiomeHunkView(buffer), false);
        return new GoldenHashEngine.ChunkSnapshot() {
            @Override
            public int minY() {
                return buffer.getMinHeight();
            }

            @Override
            public int maxY() {
                return buffer.getMaxHeight();
            }

            @Override
            public PlatformBlockState block(int x, int y, int z) {
                return buffer.getBlockData(x, y, z);
            }

            @Override
            public PlatformBiome biome(int x, int y, int z) {
                return buffer.getBiome(x, y, z);
            }
        };
    }

    private GoldenHashEngine.Feedback feedback() {
        return new GoldenHashEngine.Feedback() {
            @Override
            public void ok(String message) {
                sender.sendMessage(C.GREEN + message);
            }

            @Override
            public void warn(String message) {
                sender.sendMessage(C.YELLOW + message);
            }

            @Override
            public void fail(String message) {
                sender.sendMessage(C.RED + message);
            }
        };
    }

    private GoldenHashEngine.Progress progress() {
        return new GoldenHashEngine.Progress() {
            @Override
            public void stage(String stage) {
                reporter.setStage(stage);
            }

            @Override
            public void total(int total) {
                reporter.setTotal(total);
            }

            @Override
            public void chunkDone(int chunkX, int chunkZ, boolean ok, int done, int total) {
                reporter.countApplied(ok);
            }
        };
    }
}
