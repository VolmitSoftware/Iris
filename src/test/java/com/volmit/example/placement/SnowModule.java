package com.volmit.example.placement;

import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.block.BlockState;
import com.volmit.placement.PlacedBlock;
import com.volmit.stage.BlockStage;
import com.volmit.stage.PlacementStage;
import com.volmit.stage.PlacementStageContext;
import com.volmit.stage.Stage;
import com.volmit.stage.StageGraph;
import com.volmit.stage.StageRef;
import com.volmit.world.BlockPosition;
import java.util.Optional;

/** Port of PlacementModuleSnow: collect written tops, then cover them after the finishing work
 * of whichever stilt or vacuum module is installed (soft, so neither is required).
 */
public final class SnowModule implements IrisModule {
    public static final BlockStage<SnowHeights> COLLECT = Stage.placementBlock("iris:snow/collect", SnowHeights.class);
    public static final PlacementStage<Void> FINISH = Stage.placement("iris:snow/finish");
    public static final BlockState SNOW = new BlockState("minecraft:snow");

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:snow", "test");
    }

    @Override
    public void install(StageGraph graph) {
        graph.define(COLLECT)
                .when(SnowSettings.class)
                .requires(BlocksModule.WRITE)
                .accumulate(SnowHeights::new, (context, snow, heights) -> heights.include(
                        context.output(AnchorModule.HEIGHT).worldPosition(context.request(), context.block().position())));
        graph.define(FINISH)
                .when(SnowSettings.class)
                .requires(COLLECT, BlocksModule.COMPLETE)
                .softAfter(StageRef.of("iris:stilt/fill"), StageRef.of("iris:vacuum/finish"))
                .run(SnowModule::cover);
    }

    private static void cover(PlacementStageContext context, SnowSettings snow) {
        var layer = SNOW.withProperty("layers", Integer.toString(snow.layers()));
        var access = context.access();
        for (var top : context.output(COLLECT).tops()) {
            var above = new BlockPosition(top.x(), top.y() + 1, top.z());
            if (ExampleBlocks.isAir(access.get(above.x(), above.y(), above.z()))) {
                access.set(new PlacedBlock(above, layer, Optional.empty()));
            }
        }
    }
}
