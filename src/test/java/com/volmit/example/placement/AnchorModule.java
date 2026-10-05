package com.volmit.example.placement;

import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.stage.PlacementStage;
import com.volmit.stage.PlacementStageContext;
import com.volmit.stage.Stage;
import com.volmit.stage.StageGraph;
import com.volmit.world.BlockPosition;
import com.volmit.world.BlockView;

/** Port of PlacementModuleAnchor with center-height sampling: rest the anchor on the surface. */
public final class AnchorModule implements IrisModule {
    public static final PlacementStage<AnchorResult> HEIGHT = Stage.placement("iris:anchor/height", AnchorResult.class);

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:anchor", "test");
    }

    @Override
    public void install(StageGraph graph) {
        graph.define(HEIGHT)
                .requires(PrepareModule.TRANSFORMS)
                .produce(AnchorModule::restOnSurface);
    }

    private static AnchorResult restOnSurface(PlacementStageContext context) {
        var requested = context.request().anchor();
        int surfaceY = firstAirAboveGround(context.blocks(), requested.x(), requested.z());
        return new AnchorResult(new BlockPosition(requested.x(), surfaceY, requested.z()));
    }

    private static int firstAirAboveGround(BlockView blocks, int x, int z) {
        var bounds = blocks.bounds();
        for (int y = bounds.maxY() - 1; y >= bounds.minY(); y--) {
            if (!ExampleBlocks.isAir(blocks.get(x, y, z))) {
                return y + 1;
            }
        }
        return bounds.minY();
    }
}
