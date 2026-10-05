package com.volmit.example.placement;

import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.object.ObjectBlock;
import com.volmit.placement.PlacedBlock;
import com.volmit.stage.BlockStage;
import com.volmit.stage.PlacementStage;
import com.volmit.stage.PlacementStageContext;
import com.volmit.stage.Stage;
import com.volmit.stage.StageGraph;
import com.volmit.world.BlockPosition;
import java.util.Optional;

/** Port of PlacementModuleStilt: collect the lowest layer per block, then fill down after writes. */
public final class StiltModule implements IrisModule {
    public static final BlockStage<StiltBounds> BOUNDS = Stage.placementBlock("iris:stilt/bounds", StiltBounds.class);
    public static final PlacementStage<Void> FILL = Stage.placement("iris:stilt/fill");

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:stilt", "Stilts", "test");
    }

    @Override
    public void install(StageGraph graph) {
        graph.define(BOUNDS)
                .when(StiltSettings.class)
                .requires(PrepareModule.ROTATE, AnchorModule.HEIGHT)
                .accumulate(StiltBounds::new, (context, stilt, bounds) -> bounds.include(context.block()));
        graph.define(FILL)
                .when(StiltSettings.class)
                .requires(BOUNDS, BlocksModule.COMPLETE)
                .run(StiltModule::fill);
    }

    private static void fill(PlacementStageContext context, StiltSettings stilt) {
        var anchor = context.output(AnchorModule.HEIGHT);
        for (var block : context.output(BOUNDS).lowestLayer()) {
            var bottom = anchor.worldPosition(context.request(), block.position());
            fillColumnBelow(context, bottom, block, stilt.maxDepth());
        }
    }

    private static void fillColumnBelow(PlacementStageContext context, BlockPosition bottom,
                                        ObjectBlock source, int maxDepth) {
        var access = context.access();
        for (int depth = 1; depth <= maxDepth; depth++) {
            int y = bottom.y() - depth;
            if (y < access.bounds().minY() || !ExampleBlocks.isAir(access.get(bottom.x(), y, bottom.z()))) {
                return;
            }
            access.set(new PlacedBlock(new BlockPosition(bottom.x(), y, bottom.z()), source.state(), Optional.empty()));
        }
    }
}
