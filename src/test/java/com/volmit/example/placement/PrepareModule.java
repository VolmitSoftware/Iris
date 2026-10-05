package com.volmit.example.placement;

import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.object.ObjectBlock;
import com.volmit.stage.BlockStage;
import com.volmit.stage.BlockStageContext;
import com.volmit.stage.PlacementStage;
import com.volmit.stage.Stage;
import com.volmit.stage.StageGraph;

/** Port of PlacementModulePreparation: choose transforms once, rotate each block in the pass. */
public final class PrepareModule implements IrisModule {
    public static final PlacementStage<Transforms> TRANSFORMS = Stage.placement("iris:prepare/transforms", Transforms.class);
    public static final BlockStage<Void> ROTATE = Stage.placementBlock("iris:prepare/rotate");
    private static final int QUARTER_TURNS = 4;

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:prepare", "Prepare", "test");
    }

    @Override
    public void install(StageGraph graph) {
        graph.define(TRANSFORMS)
                .produce(context -> new Transforms(context.random().nextInt(QUARTER_TURNS)));
        graph.define(ROTATE)
                .requires(TRANSFORMS)
                .run(PrepareModule::rotate);
    }

    private static void rotate(BlockStageContext context) {
        var transforms = context.output(TRANSFORMS);
        var block = context.block();
        var pivot = context.request().object().anchor();
        context.replace(new ObjectBlock(transforms.rotate(block.position(), pivot), block.state(), block.blockEntity()));
    }
}
