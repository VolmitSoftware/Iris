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

package art.arcane.iris.world.runtime;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.RuntimeProgressMessages;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

public final class ChunkClearer {
    private static final int APPLY_AHEAD = 8;

    private final World world;
    private final Engine engine;
    private final int centerChunkX;
    private final int centerChunkZ;
    private final int radius;
    private final ChunkJobReporter reporter;

    public ChunkClearer(World world, Engine engine, VolmitSender sender, int centerChunkX, int centerChunkZ, int radius) {
        this.world = world;
        this.engine = engine;
        this.centerChunkX = centerChunkX;
        this.centerChunkZ = centerChunkZ;
        this.radius = Math.max(0, radius);
        this.reporter = new ChunkJobReporter(sender, IrisLanguage.text(RuntimeProgressMessages.CHUNK_TITLE_DELETE), world);
    }

    public void start() {
        reporter.start();
        Thread thread = new Thread(this::run, "Iris Delete Chunks");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        boolean error = false;
        try {
            List<int[]> targets = ChunkJobReporter.orderedTargets(centerChunkX, centerChunkZ, radius);
            reporter.setTotal(targets.size());
            reporter.setStage(IrisLanguage.text(RuntimeProgressMessages.CHUNK_STAGE_CLEARING));
            clear(targets);
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            error = true;
        } finally {
            reporter.finish(error);
        }
    }

    private void clear(List<int[]> targets) throws InterruptedException {
        Mantle mantle = engine.getMantle().getMantle();
        Semaphore inFlight = new Semaphore(APPLY_AHEAD);
        CountDownLatch allCleared = new CountDownLatch(targets.size());

        for (int[] target : targets) {
            int chunkX = target[0];
            int chunkZ = target[1];

            inFlight.acquire();
            boolean scheduled = J.runRegion(world, chunkX, chunkZ, () -> {
                boolean ok = false;
                try {
                    clearChunk(chunkX, chunkZ, mantle);
                    ok = true;
                } catch (Throwable e) {
                    IrisLogging.reportError(e);
                } finally {
                    reporter.countApplied(ok);
                    inFlight.release();
                    allCleared.countDown();
                }
            });

            if (!scheduled) {
                IrisLogging.warn("Delete could not schedule chunk clear at " + chunkX + "," + chunkZ + " in " + world.getName() + ".");
                reporter.countApplied(false);
                inFlight.release();
                allCleared.countDown();
            }
        }

        allCleared.await();
    }

    private void clearChunk(int chunkX, int chunkZ, Mantle mantle) {
        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        for (Entity entity : chunk.getEntities()) {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        }

        if (!INMS.get().clearChunkBlocks(chunk)) {
            clearBlocks(chunk, chunk.getChunkSnapshot(true, false, false), world.getMinHeight(), world.getMaxHeight());
        }

        mantle.deleteChunk(chunkX, chunkZ);
        world.refreshChunk(chunkX, chunkZ);
    }

    static void clearBlocks(Chunk chunk, ChunkSnapshot snapshot, int minHeight, int maxHeight) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int top = Math.min(maxHeight - 1, snapshot.getHighestBlockYAt(x, z) + 1);
                for (int y = minHeight; y <= top; y++) {
                    if (snapshot.getBlockType(x, y, z) != Material.AIR) {
                        chunk.getBlock(x, y, z).setType(Material.AIR, false);
                    }
                }
            }
        }
    }
}
