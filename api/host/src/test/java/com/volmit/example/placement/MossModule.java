package com.volmit.example.placement;

import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.block.BlockState;
import com.volmit.stage.PlacementStage;
import com.volmit.stage.PlacementStageContext;
import com.volmit.stage.Stage;
import com.volmit.stage.StageRef;

/** Third-party module: turns the ground under the anchor into moss. It requires the core
 * anchor height (typed output) and block completion, and runs after snow only if snow exists.
 */
public final class MossModule implements IrisModule {
    public static final PlacementStage<Void> SPREAD = Stage.placement("example:moss/spread");
    public static final BlockState MOSS = new BlockState("minecraft:moss_block");

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("example:moss", "Moss", "1.0.0");
    }

    @Override
    public void setup(ModuleSetup setup) {
        setup.stages().define(SPREAD)
                .when(MossSettings.class)
                .requires(AnchorModule.HEIGHT, BlocksModule.COMPLETE)
                .softAfter(StageRef.of("iris:snow/finish"))
                .run(MossModule::spread);
    }

    private static void spread(PlacementStageContext context) {
        var moss = context.configured(MossSettings.class).mostSpecific().orElseThrow();    // a placement declares its settings once
        var anchor = context.output(AnchorModule.HEIGHT).anchor();
        var access = context.access();
        int groundY = anchor.y() - 1;
        for (int dx = -moss.radius(); dx <= moss.radius(); dx++) {
            for (int dz = -moss.radius(); dz <= moss.radius(); dz++) {
                int x = anchor.x() + dx;
                int z = anchor.z() + dz;
                if (!ExampleBlocks.isAir(access.get(x, groundY, z))) {
                    access.set(x, groundY, z, MOSS);
                }
            }
        }
    }
}
