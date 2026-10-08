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

import art.arcane.iris.builtin.IrisStages;
import art.arcane.iris.core.modules.IrisChunk;
import art.arcane.iris.core.modules.IrisMantleChunk;
import art.arcane.iris.engine.mantle.components.CaveCarving;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.stage.ChunkStageContext;
import com.volmit.stage.MantleStageContext;
import com.volmit.stage.StageGraph;

/**
 * Module {@code iris:carve}: caves. A mantle stage plans them into the mantle, reaching one block into each
 * neighboring chunk where blended cave walls cross the border; a chunk stage later cuts them out of the chunk's
 * terrain. The mantle stage is a port of what was Iris's carving mantle component, its work done by
 * {@link CaveCarving}, one per engine runtime. The chunk stage's work is done by {@link CaveApplying}.
 */
public final class CarveModule implements IrisModule {
    private static final String VERSION = "built-in";
    /** Blended cave walls reach one block past the chunk. */
    private static final int CAVE_REACH_BLOCKS = 1;
    private static final int CHUNK_SHIFT = 4;

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:carve", "Caves", VERSION);
    }

    @Override
    public void install(StageGraph graph) {
        graph.define(IrisStages.Carve.CAVES).reach(CAVE_REACH_BLOCKS).run(CarveModule::planCaves);
        graph.define(IrisStages.Carve.APPLY).requires(IrisStages.Terrain.SHAPE).run(CarveModule::applyCaves);
    }

    private static void planCaves(MantleStageContext context) {
        IrisMantleChunk chunk = IrisMantleChunk.current();
        if (!carves(chunk.engine().getDimension())) {
            return;
        }
        chunk.state(CaveCarving.class, CaveCarving::new).carve(chunk.writer(), chunk.chunkX(), chunk.chunkZ(), chunk.context());
    }

    private static void applyCaves(ChunkStageContext context) {
        IrisChunk chunk = IrisChunk.current();
        if (chunk.bypassesMantleStages()) {
            return;
        }
        chunk.state(CaveApplying.class, CaveApplying::new).apply(chunk.originX() >> CHUNK_SHIFT, chunk.originZ() >> CHUNK_SHIFT,
                chunk.blocks(), chunk.multicore(), chunk.context());
    }

    /**
     * A dimension turns carving off with {@code carvingEnabled}. Listing {@code CARVED} in {@code disabledComponents}
     * also does: Iris then skips this stage's mantle pass like any disabled component.
     */
    private static boolean carves(IrisDimension dimension) {
        return dimension.isCarvingEnabled();
    }
}
