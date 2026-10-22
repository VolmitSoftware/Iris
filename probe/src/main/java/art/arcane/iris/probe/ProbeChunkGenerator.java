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

package art.arcane.iris.probe;

import art.arcane.iris.generation.chunk.SectionBuffer;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

final class ProbeChunkGenerator {
    private final Engine engine;
    private final GenerationHistoryRuntimeRouter router;
    private final boolean productionBuffers;
    private final HistoryDigest historyDigest;
    private final AtomicInteger expandedBiomeHunks = new AtomicInteger();

    ProbeChunkGenerator(Engine engine, GenerationHistoryRuntimeRouter router, boolean productionBuffers,
                        boolean historyDigest) {
        this.engine = engine;
        this.router = router;
        this.productionBuffers = productionBuffers;
        this.historyDigest = historyDigest && router != null ? new HistoryDigest(router) : null;
    }

    String historySummary() {
        return historyDigest == null ? null : historyDigest.summary();
    }

    Engine engine() {
        return engine;
    }

    int expandedBiomeHunks() {
        return expandedBiomeHunks.get();
    }

    GenerationProbe.ChunkOutcome generate(int index, GenerationProbe.ChunkCoordinate coordinate, boolean multicore) {
        int height = engine.getTarget().getHeight();
        List<Throwable> failures = new ArrayList<>();
        Hunk<NativeBlockState> blocks = newBlocks(height);
        Hunk<NativeBiome> biomes = newBiomes(height);
        long started = System.nanoTime();
        try {
            generateInto(coordinate.x() << 4, coordinate.z() << 4, blocks, biomes, multicore);
        } catch (Throwable e) {
            failures.add(e);
        }
        long elapsed = System.nanoTime() - started;
        if (historyDigest != null && failures.isEmpty()) {
            try {
                historyDigest.record(coordinate.x(), coordinate.z());
            } catch (Throwable e) {
                failures.add(e);
            }
        }
        if (biomes instanceof ProductionBiomeHunk production && production.expanded()) {
            expandedBiomeHunks.incrementAndGet();
        }
        return new GenerationProbe.ChunkOutcome(index, coordinate, elapsed, blocks, biomes, failures);
    }

    private Hunk<NativeBlockState> newBlocks(int height) {
        return productionBuffers ? new SectionBuffer(height) : Hunk.newArrayHunk(16, height, 16);
    }

    private Hunk<NativeBiome> newBiomes(int height) {
        return productionBuffers ? new ProductionBiomeHunk(height, ProbeChunkGenerator::plainsBiome)
                : Hunk.newArrayHunk(16, height, 16);
    }

    private void generateInto(int blockX, int blockZ, Hunk<NativeBlockState> blocks, Hunk<NativeBiome> biomes,
                              boolean multicore) throws Exception {
        if (router == null) {
            engine.generate(blockX, blockZ, blocks, biomes, multicore);
            return;
        }
        try (GenerationHistoryRuntimeRouter.CoordinateScope historyScope = router.openCoordinateScope(blockX, blockZ);
             GenerationSessionLease lease = engine.acquireGenerationLease("probe_terrain_stage");
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            engine.generate(blockX, blockZ, blocks, biomes, multicore);
            historyScope.claimGeneratedSemantics();
        }
    }

    private static NativeBiome plainsBiome() {
        return IrisPlatforms.get().registries().biome("minecraft:plains");
    }
}
