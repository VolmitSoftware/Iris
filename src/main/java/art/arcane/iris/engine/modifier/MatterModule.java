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
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.stage.ChunkStageContext;
import com.volmit.stage.StageGraph;

/**
 * Module {@code iris:matter}: copies the blocks the mantle planned for the chunk, its objects and structures,
 * into it. A thin wrapper around the engine mantle's own insertion. It orders itself after deposits without
 * requiring them, since deposits run only where a level declares some.
 */
public final class MatterModule implements IrisModule {
    private static final String VERSION = "built-in";
    private static final int CHUNK_SHIFT = 4;

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
        chunk.engine().getMantle().insertMatter(chunk.originX() >> CHUNK_SHIFT, chunk.originZ() >> CHUNK_SHIFT, chunk.blocks(), chunk.multicore());
    }
}
