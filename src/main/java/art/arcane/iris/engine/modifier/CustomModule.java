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

package art.arcane.iris.engine.modifier;

import art.arcane.iris.builtin.custom.CustomStages;
import art.arcane.iris.builtin.perfection.PerfectionStages;
import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.core.modules.IrisChunk;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.parallel.BurstExecutor;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.stage.ChunkStageContext;
import com.volmit.stage.StageGraph;

/**
 * Module {@code iris:custom}: records the chunk's content-plugin blocks for the server to place later: each
 * custom block's placement key goes into the mantle and the block itself is replaced by its base state. Skipped
 * while maintenance regeneration bypasses the mantle.
 */
public final class CustomModule implements IrisModule {
    private static final String VERSION = "built-in";
    private static final int CHUNK_SHIFT = 4;
    private static final int SECTION_MASK = 15;

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:custom", "Custom blocks", VERSION);
    }

    @Override
    public void install(StageGraph graph) {
        graph.define(CustomStages.BLOCKS).requires(PerfectionStages.SURFACE).run(CustomModule::record);
    }

    private static void record(ChunkStageContext context) {
        IrisChunk chunk = IrisChunk.current();
        if (chunk.bypassesMantleStages()) {
            return;
        }
        chunk.state(CustomBlockRecording.class, CustomBlockRecording::new).record(chunk.originX(), chunk.originZ(), chunk.blocks(), chunk.multicore());
    }

    /** Moves the custom blocks of a chunk into the mantle for one engine runtime. */
    static final class CustomBlockRecording {
        private final Engine engine;

        CustomBlockRecording(Engine engine) {
            this.engine = engine;
        }

        /** Records the custom blocks of the chunk whose first block is {@code x}, {@code z}, when its mantle chunk is flagged as having some. */
        void record(int x, int z, Hunk<PlatformBlockState> output, boolean multicore) {
            MantleChunk<Matter> mc = engine.getMantle().getMantle().getChunk(x >> CHUNK_SHIFT, z >> CHUNK_SHIFT);
            if (!mc.isFlagged(MantleFlag.CUSTOM_ACTIVE)) {
                return;
            }
            mc.use();

            BurstExecutor burst = MultiBurst.burst.burst(output.getHeight());
            burst.setMulticore(multicore);
            // complete() must run before release() even when queueing throws — detached burst
            // tasks must never write into a released chunk, and a lost permit wedges close().
            try {
                for (int y = 0; y < output.getHeight(); y++) {
                    int finalY = y;
                    burst.queue(() -> {
                        for (int rX = 0; rX < output.getWidth(); rX++) {
                            for (int rZ = 0; rZ < output.getDepth(); rZ++) {
                                PlatformBlockState b = output.get(rX, finalY, rZ);
                                if (b == null || !b.isCustom()) {
                                    continue;
                                }
                                String placementKey = b.deferredPlacementKey();
                                PlatformBlockState baseState = b.placementBaseState();
                                if (placementKey == null || baseState == null) {
                                    continue;
                                }

                                mc.getOrCreate(finalY >> CHUNK_SHIFT)
                                        .slice(Identifier.class)
                                        .set(rX, finalY & SECTION_MASK, rZ, Identifier.fromString(placementKey));
                                output.set(rX, finalY, rZ, baseState);
                            }
                        }
                    });
                }
            } finally {
                try {
                    burst.complete();
                } finally {
                    mc.release();
                }
            }
        }
    }
}
