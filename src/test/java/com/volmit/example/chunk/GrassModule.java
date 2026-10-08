package com.volmit.example.chunk;

import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.block.BlockState;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.stage.ChunkStage;
import com.volmit.stage.ChunkStageContext;
import com.volmit.stage.Stage;
import com.volmit.stage.StageGraph;
import java.util.List;

/** Third-party decoration: tops terrain columns using the terrain module's typed output. It runs where any
 * column of the chunk has grass and reads the settings per column, so a biome border inside the chunk
 * keeps grass on its own side.
 */
public final class GrassModule implements IrisModule {
    public static final ChunkStage<Void> TOP = Stage.chunk("example:grass/top");

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("example:grass", "Grass", "1.0.0");
    }

    @Override
    public void install(StageGraph graph) {
        graph.define(TOP)
                .when(GrassSettings.class)
                .requires(TerrainModule.SURFACE)
                .run(GrassModule::cover);
    }

    private static void cover(ChunkStageContext context, List<Entry<GrassSettings>> inChunk) {
        var heights = context.output(TerrainModule.SURFACE);
        var blocks = context.hunk(BlockState.class);
        for (int x = 0; x < blocks.getWidth(); x++) {
            for (int z = 0; z < blocks.getDepth(); z++) {
                var here = context.settings().at(GrassSettings.class, context.originX() + x, context.originZ() + z);
                int height = heights.at(x, z);
                if (here.isEmpty() || height <= 0) {
                    continue;
                }
                blocks.set(x, height - 1, z, here.getLast().value().block());   // the most specific level wins here
            }
        }
    }
}
