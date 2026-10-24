package com.volmit.example.placement;

import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.block.BlockState;
import com.volmit.stage.BlockStage;
import com.volmit.stage.PlacementStage;
import com.volmit.stage.PlacementStageContext;
import com.volmit.stage.Stage;
import com.volmit.stage.StageRef;

/** Port of PlacementModuleSnow: collect written tops, then cover them after the finishing work
 * of whichever stilt or vacuum module is installed (soft, so neither is required).
 */
public final class SnowModule implements IrisModule {
    public static final BlockStage<SnowHeights> COLLECT = Stage.placementBlock("iris:snow/collect", SnowHeights.class);
    public static final PlacementStage<Void> FINISH = Stage.placement("iris:snow/finish");
    public static final BlockState SNOW = new BlockState("minecraft:snow");

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:snow", "Snow", "test");
    }

    @Override
    public void setup(ModuleSetup setup) {
        var stages = setup.stages();
        stages.define(COLLECT)
                .when(SnowSettings.class)
                .requires(BlocksModule.WRITE)
                .accumulate(SnowHeights::new, (context, heights) -> heights.include(
                        context.output(AnchorModule.HEIGHT).worldPosition(context.request(), context.block().position())));
        stages.define(FINISH)
                .when(SnowSettings.class)
                .requires(COLLECT, BlocksModule.COMPLETE)
                .softAfter(StageRef.of("iris:stilt/fill"), StageRef.of("iris:vacuum/finish"))
                .run(SnowModule::cover);
    }

    private static void cover(PlacementStageContext context) {
        var snow = context.configured(SnowSettings.class).mostSpecific().orElseThrow();    // a placement declares its settings once
        var layer = SNOW.withProperty("layers", Integer.toString(snow.layers()));
        var access = context.access();
        for (var top : context.output(COLLECT).tops()) {
            int aboveY = top.y() + 1;
            if (ExampleBlocks.isAir(access.get(top.x(), aboveY, top.z()))) {
                access.set(top.x(), aboveY, top.z(), layer);
            }
        }
    }
}
