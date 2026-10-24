package com.volmit.example.placement;

import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.placement.PlacedBlock;
import com.volmit.stage.BlockStage;
import com.volmit.stage.BlockStageContext;
import com.volmit.stage.PlacementStage;
import com.volmit.stage.Stage;

/** Port of PlacementModuleBlocks: write each transformed block; COMPLETE marks the pass done. */
public final class BlocksModule implements IrisModule {
    public static final BlockStage<Void> WRITE = Stage.placementBlock("iris:blocks/write");
    /** Milestone: stages requiring it run after every object block has been written. */
    public static final PlacementStage<Void> COMPLETE = Stage.placement("iris:blocks/complete");

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:blocks", "Blocks", "test");
    }

    @Override
    public void setup(ModuleSetup setup) {
        var stages = setup.stages();
        stages.define(WRITE)
                .requires(PrepareModule.ROTATE, AnchorModule.HEIGHT)
                .run(BlocksModule::write);
        stages.define(COMPLETE)
                .requires(WRITE)
                .run(context -> {});
    }

    private static void write(BlockStageContext context) {
        var block = context.block();
        var position = context.output(AnchorModule.HEIGHT).worldPosition(context.request(), block.position());
        context.access().set(new PlacedBlock(position, block.state(), block.blockEntity()));
    }
}
