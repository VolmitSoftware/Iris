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
import art.arcane.iris.core.modules.IrisChunk;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.stage.ChunkStageContext;
import com.volmit.stage.StageGraph;

/**
 * Module {@code iris:custom}: records the chunk's content-plugin blocks for the server to place later. A thin
 * wrapper: the stage runs {@link IrisCustomModifier} unchanged, one per engine runtime, except while
 * maintenance regeneration bypasses the mantle.
 */
public final class CustomModule implements IrisModule {
    private static final String VERSION = "built-in";

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
        chunk.state(IrisCustomModifier.class, IrisCustomModifier::new)
                .modify(chunk.originX(), chunk.originZ(), chunk.blocks(), chunk.multicore(), chunk.context());
    }
}
