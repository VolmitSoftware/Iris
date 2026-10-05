package com.volmit.example.chunk;

import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.block.BlockState;
import com.volmit.stage.ChunkStage;
import com.volmit.stage.ChunkStageContext;
import com.volmit.stage.Stage;
import com.volmit.stage.StageGraph;

/** Third-party decoration: tops terrain columns using the terrain module's typed output. */
public final class GrassModule implements IrisModule {
    public static final ChunkStage<Void> TOP = Stage.chunk("example:grass/top");

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("example:grass", "1.0.0");
    }

    @Override
    public void install(StageGraph graph) {
        graph.define(TOP)
                .when(GrassSettings.class)
                .requires(TerrainModule.SURFACE)
                .run(GrassModule::cover);
    }

    private static void cover(ChunkStageContext context, GrassSettings grass) {
        var top = new BlockState(grass.block());
        var heights = context.output(TerrainModule.SURFACE);
        var blocks = context.hunk(BlockState.class);
        for (int x = 0; x < blocks.getWidth(); x++) {
            for (int z = 0; z < blocks.getDepth(); z++) {
                int height = heights.at(x, z);
                if (height > 0) {
                    blocks.set(x, height - 1, z, top);
                }
            }
        }
    }
}
