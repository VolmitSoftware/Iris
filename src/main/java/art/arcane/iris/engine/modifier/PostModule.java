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
import art.arcane.iris.builtin.post.PostProcessingSettings;
import art.arcane.iris.builtin.post.PostProcessingSlabsSettings;
import art.arcane.iris.builtin.post.PostProcessingWallsSettings;
import art.arcane.iris.core.modules.IrisChunk;
import com.volmit.FeatureRegistry;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.stage.ChunkStageContext;
import com.volmit.stage.StageGraph;

/**
 * Module {@code iris:post}: smooths the carved surface with {@link SurfaceSmoothing}, except while maintenance
 * regeneration bypasses the mantle.
 */
public final class PostModule implements IrisModule {
    private static final String VERSION = "built-in";

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:post", "Post-processing", VERSION);
    }

    @Override
    public void register(FeatureRegistry registry) {
        registry.registerSettings(PostProcessingSettings.class);
        registry.registerSettings(PostProcessingSlabsSettings.class);
        registry.registerSettings(PostProcessingWallsSettings.class);
    }

    @Override
    public void install(StageGraph graph) {
        graph.define(IrisStages.Post.SMOOTH).requires(IrisStages.Carve.APPLY).run(PostModule::smooth);
    }

    private static void smooth(ChunkStageContext context) {
        IrisChunk chunk = IrisChunk.current();
        if (chunk.bypassesMantleStages()) {
            return;
        }
        chunk.state(SurfaceSmoothing.class, SurfaceSmoothing::new)
                .smooth(chunk.originX(), chunk.originZ(), chunk.blocks(), chunk.multicore(), chunk.context());
    }
}
