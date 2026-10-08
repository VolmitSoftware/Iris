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

import art.arcane.iris.builtin.deposit.DepositStages;
import art.arcane.iris.builtin.floating.FloatingStages;
import art.arcane.iris.builtin.matter.MatterStages;
import art.arcane.iris.core.modules.IrisChunk;
import art.arcane.iris.engine.UpperDimensionContext;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.stage.ChunkStageContext;
import com.volmit.stage.StageGraph;

/**
 * Module {@code iris:matter}: copies the blocks the mantle planned for the chunk, its objects and structures,
 * into it, unless the dimension turns the mantle off ({@code useMantle}). Where an upper dimension sits above,
 * blocks at or above its surface are left out. It orders itself after deposits without requiring them, since
 * deposits run only where a level declares some.
 */
public final class MatterModule implements IrisModule {
    private static final String VERSION = "built-in";
    private static final int CHUNK_SHIFT = 4;
    private static final int CHUNK_COLUMNS = 16 * 16;

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:matter", "Mantle blocks", VERSION);
    }

    @Override
    public void install(StageGraph graph) {
        graph.define(MatterStages.INSERT)
                .requires(FloatingStages.SOLIDS)
                .softAfter(DepositStages.ORES.ref())
                .run(MatterModule::insert);
    }

    private static void insert(ChunkStageContext context) {
        IrisChunk chunk = IrisChunk.current();
        if (chunk.bypassesMantleStages()) {
            return;
        }
        chunk.state(MatterInsertion.class, MatterInsertion::new).insert(chunk.originX() >> CHUNK_SHIFT, chunk.originZ() >> CHUNK_SHIFT, chunk.blocks());
    }

    /** Copies the mantle's blocks into chunks for one engine runtime. */
    static final class MatterInsertion {
        private final Engine engine;

        MatterInsertion(Engine engine) {
            this.engine = engine;
        }

        /** Copies the blocks the mantle holds for chunk {@code x}, {@code z} into {@code blocks}. */
        @ChunkCoordinates
        void insert(int x, int z, Hunk<PlatformBlockState> blocks) {
            if (!engine.getDimension().isUseMantle()) {
                return;
            }

            UpperDimensionContext upperCtx = engine.getUpperContext();
            boolean protectUpper = upperCtx != null;

            MantleChunk<Matter> chunk = engine.getMantle().getMantle().getChunk(x, z).use();
            try {
                if (protectUpper) {
                    int chunkBlockX = x << CHUNK_SHIFT;
                    int chunkBlockZ = z << CHUNK_SHIFT;
                    int gap = engine.getDimension().getUpperDimensionGap();
                    int[] upperYs = new int[CHUNK_COLUMNS];
                    for (int i = 0; i < CHUNK_COLUMNS; i++) {
                        int lx = i >> 4;
                        int lz = i & 15;
                        int worldX = chunkBlockX + lx;
                        int worldZ = chunkBlockZ + lz;
                        int he = (int) Math.round(engine.getComplex().getHeightStream().get((double) worldX, (double) worldZ));
                        int rawUpper = upperCtx.getUpperSurfaceY(worldX, worldZ);
                        upperYs[i] = Math.max(rawUpper, he + gap);
                    }
                    chunk.iterate(PlatformBlockState.class, (lx, y, lz, value) -> {
                        int colIdx = (lx << 4) | (lz & 15);
                        if (y < upperYs[colIdx]) {
                            blocks.set(lx, y, lz, value);
                        }
                    });
                } else {
                    chunk.iterate(PlatformBlockState.class, (lx, y, lz, value) -> blocks.set(lx, y, lz, value));
                }
            } finally {
                chunk.release();
            }
        }
    }
}
