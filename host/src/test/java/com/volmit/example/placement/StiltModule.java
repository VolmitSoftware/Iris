package com.volmit.example.placement;

import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.object.ObjectBlock;
import com.volmit.stage.BlockStage;
import com.volmit.stage.PlacementStage;
import com.volmit.stage.PlacementStageContext;
import com.volmit.stage.Stage;
import com.volmit.world.BlockPosition;

/** Port of PlacementModuleStilt: collect the lowest layer per block, then fill down after writes. */
public final class StiltModule implements IrisModule {
    public static final BlockStage<StiltBounds> BOUNDS = Stage.placementBlock("iris:stilt/bounds", StiltBounds.class);
    public static final PlacementStage<Void> FILL = Stage.placement("iris:stilt/fill");

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:stilt", "Stilts", "test");
    }

    @Override
    public void setup(ModuleSetup setup) {
        var stages = setup.stages();
        stages.define(BOUNDS)
                .when(StiltSettings.class)
                .requires(PrepareModule.ROTATE, AnchorModule.HEIGHT)
                .accumulate(StiltBounds::new, (context, bounds) -> bounds.include(context.block()));
        stages.define(FILL)
                .when(StiltSettings.class)
                .requires(BOUNDS, BlocksModule.COMPLETE)
                .run(StiltModule::fill);
    }

    private static void fill(PlacementStageContext context) {
        var stilt = context.configured(StiltSettings.class).mostSpecific().orElseThrow();    // a placement declares its settings once
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
            access.set(bottom.x(), y, bottom.z(), source.state());
        }
    }
}
