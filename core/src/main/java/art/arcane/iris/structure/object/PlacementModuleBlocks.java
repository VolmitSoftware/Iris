package art.arcane.iris.structure.object;

import art.arcane.iris.generation.decoration.IrisStiltSettings;

import java.util.Set;

/** Resolves the baked object layout for this placement's transform, then runs the block pass. */
final class PlacementModuleBlocks implements PlacementModule {
    static final PlacementLifecycleDependency COMPLETE = new PlacementLifecycleDependency("blocks.complete");

    @Override
    public void install(PlacementLifecycleBuilder lifecycle) {
        lifecycle.add(COMPLETE, PlacementStage.BLOCKS, (context, state, plan) -> {
            bake(context, state);
            new ObjectBlockPass(context, state, plan, context.listener).process();
        }, PlacementModuleBore.BORE);
    }

    private static void bake(PlacementContext context, PlacementState state) {
        state.bake = ObjectPlacementBake.of(context.self, state.spin, state.ceilingHang, state.translateOffset);
        IrisStiltSettings stiltSettings = state.config.getStiltSettings();
        Set<String> excludedStiltMaterials = stiltSettings == null ? Set.of() : stiltSettings.excludedMaterials();
        state.palette = new PlacementPalette(state.bake.palette(), state.config.getRotation(),
                state.spinX, state.spinY, state.spinZ, state.placer.isPreventingDecay(),
                state.placer.isDebugSmartBore(), excludedStiltMaterials);
    }
}
