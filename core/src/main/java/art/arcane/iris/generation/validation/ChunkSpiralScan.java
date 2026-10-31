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

import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.spi.IrisLogging;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public final class ChunkSpiralScan {
    private ChunkSpiralScan() {
    }

    public static int scan(List<int[]> targets, int threads, ChunkVisitor visitor, Listener listener) throws InterruptedException {
        Semaphore inFlight = new Semaphore(Math.max(1, threads));
        CountDownLatch done = new CountDownLatch(targets.size());
        AtomicInteger failures = new AtomicInteger();

        for (int[] target : targets) {
            int chunkX = target[0];
            int chunkZ = target[1];

            inFlight.acquire();
            MultiBurst.burst.lazy(() -> {
                boolean ok = false;
                try {
                    visitor.visit(chunkX, chunkZ);
                    ok = true;
                } catch (Throwable e) {
                    failures.incrementAndGet();
                    IrisLogging.reportError(e);
                    listener.chunkFailed(chunkX, chunkZ, e);
                } finally {
                    listener.chunkDone(chunkX, chunkZ, ok);
                    inFlight.release();
                    done.countDown();
                }
            });
        }

        done.await();
        return failures.get();
    }

    @FunctionalInterface
    public interface ChunkVisitor {
        void visit(int chunkX, int chunkZ) throws Exception;
    }

    public interface Listener {
        void chunkDone(int chunkX, int chunkZ, boolean ok);

        void chunkFailed(int chunkX, int chunkZ, Throwable error);
    }
}
