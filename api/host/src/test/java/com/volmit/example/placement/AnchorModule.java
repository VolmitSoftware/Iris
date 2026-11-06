package com.volmit.example.placement;

import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.stage.PlacementStage;
import com.volmit.stage.PlacementStageContext;
import com.volmit.stage.Stage;
import com.volmit.geometry.BlockPosition;

/** Port of PlacementModuleAnchor with center-height sampling: rest the anchor on the surface. */
public final class AnchorModule implements IrisModule {
    public static final PlacementStage<AnchorResult> HEIGHT = Stage.placement("iris:anchor/height", AnchorResult.class);

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:anchor", "Anchor", "test");
    }

    @Override
    public void setup(ModuleSetup setup) {
        setup.stages().define(HEIGHT)
                .requires(PrepareModule.TRANSFORMS)
                .produce(AnchorModule::restOnSurface);
    }

    private static AnchorResult restOnSurface(PlacementStageContext context) {
        var requested = context.request().anchor();
        int firstAirAboveGround = context.placementTerrain().highest(requested.x(), requested.z(), false) + 1;
        return new AnchorResult(new BlockPosition(requested.x(), firstAirAboveGround, requested.z()));
    }
}
