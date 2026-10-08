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

import art.arcane.iris.builtin.decorant.DecorantStages;
import art.arcane.iris.builtin.floating.FloatingStages;
import art.arcane.iris.builtin.post.PostStages;
import art.arcane.iris.core.modules.IrisChunk;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.stage.ChunkStageContext;
import com.volmit.stage.StageGraph;

/**
 * Module {@code iris:floating}: the islands of floating child biomes, their solids after smoothing and their
 * decoration after the chunk's own. A thin wrapper: both stages run {@link IrisFloatingChildBiomeModifier}
 * unchanged, one per engine runtime.
 */
public final class FloatingModule implements IrisModule {
    private static final String VERSION = "built-in";

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:floating", "Floating islands", VERSION);
    }

    @Override
    public void install(StageGraph graph) {
        graph.define(FloatingStages.SOLIDS).requires(PostStages.SMOOTH).run(FloatingModule::solids);
        graph.define(FloatingStages.DECORATE).requires(DecorantStages.DECORATE).run(FloatingModule::decorate);
    }

    private static void solids(ChunkStageContext context) {
        IrisChunk chunk = IrisChunk.current();
        islands(chunk).modify(chunk.originX(), chunk.originZ(), chunk.blocks(), chunk.multicore(), chunk.context());
    }

    private static void decorate(ChunkStageContext context) {
        IrisChunk chunk = IrisChunk.current();
        islands(chunk).decorateColumns(chunk.originX(), chunk.originZ(), chunk.blocks(), chunk.multicore(), chunk.context());
    }

    private static IrisFloatingChildBiomeModifier islands(IrisChunk chunk) {
        return chunk.state(IrisFloatingChildBiomeModifier.class, IrisFloatingChildBiomeModifier::new);
    }
}
