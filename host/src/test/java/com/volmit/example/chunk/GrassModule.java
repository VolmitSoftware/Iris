package com.volmit.example.chunk;

import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.block.BlockState;
import com.volmit.stage.ChunkStage;
import com.volmit.stage.ChunkStageContext;
import com.volmit.stage.Stage;

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
    public void setup(ModuleSetup setup) {
        setup.stages().define(TOP)
                .when(GrassSettings.class)
                .requires(TerrainModule.SURFACE)
                .run(GrassModule::cover);
    }

    private static void cover(ChunkStageContext context) {
        var heights = context.output(TerrainModule.SURFACE);
        var blocks = context.hunk(BlockState.class);
        for (int x = 0; x < blocks.width(); x++) {
            for (int z = 0; z < blocks.depth(); z++) {
                var here = context.settings().at(GrassSettings.class, context.originX() + x, context.originZ() + z).mostSpecific();
                int height = heights.at(x, z);
                if (here.isEmpty() || height <= 0) {
                    continue;
                }
                blocks.set(x, height - 1, z, here.get().block());
            }
        }
    }
}
