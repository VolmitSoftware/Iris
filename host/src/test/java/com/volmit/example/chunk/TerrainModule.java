package com.volmit.example.chunk;

import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.block.BlockState;
import com.volmit.stage.ChunkStage;
import com.volmit.stage.ChunkStageContext;
import com.volmit.stage.Stage;

/** Core terrain shaping as an ordinary module: fills stone and reports surface heights. */
public final class TerrainModule implements IrisModule {
    public static final ChunkStage<SurfaceHeights> SURFACE = Stage.chunk("iris:terrain/surface", SurfaceHeights.class);
    public static final BlockState STONE = new BlockState("minecraft:stone");
    private static final int BASE_HEIGHT = 3;
    private static final int RIPPLE = 3;

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:terrain", "Terrain", "test");
    }

    @Override
    public void setup(ModuleSetup setup) {
        setup.stages().define(SURFACE).produce(TerrainModule::shape);
    }

    public static int surfaceHeight(int worldX, int worldZ) {
        return BASE_HEIGHT + Math.floorMod(worldX + worldZ, RIPPLE);
    }

    private static SurfaceHeights shape(ChunkStageContext context) {
        var blocks = context.hunk(BlockState.class);
        var heights = new SurfaceHeights(blocks.width(), blocks.depth());
        for (int x = 0; x < blocks.width(); x++) {
            for (int z = 0; z < blocks.depth(); z++) {
                int height = Math.min(surfaceHeight(context.originX() + x, context.originZ() + z), blocks.height());
                for (int y = 0; y < height; y++) {
                    blocks.set(x, y, z, STONE);
                }
                heights.set(x, z, height);
            }
        }
        return heights;
    }
}
