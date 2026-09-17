package art.arcane.iris.engine.object.placement;

import art.arcane.iris.engine.object.terrain.carving.CarvingMode;
/** Common caller-supplied and from-bottom anchors wrap each mode's sampled height. */
final class PlacementModuleAnchor implements PlacementModule {
    static final PlacementLifecycleDependency HEIGHT = new PlacementLifecycleDependency("anchor.height");
    static final PlacementLifecycleDependency POSITIONED = new PlacementLifecycleDependency("anchor.positioned");
    static final PlacementLifecycleDependency FINAL_HEIGHT = new PlacementLifecycleDependency("anchor.final");

    @Override
    public void install(PlacementLifecycleBuilder lifecycle) {
        lifecycle.add(POSITIONED, PlacementStage.HEIGHT, (context, state, ignored) -> {
            if (context.yv >= 0 && state.config.isBottom()
                    && state.config.getMode() != ObjectPlaceMode.STRUCTURE_PIECE) {
                state.y += Math.floorDiv(context.self.h, 2);
                if (!state.config.isForcePlace() && state.config.getCarvingSupport() != CarvingMode.CARVING_ONLY
                        && PlacementModuleSurfaceSupport.rejectsAnchor(context, state, context.x, state.y, context.z)) {
                    state.reject("carving-anchor");
                }
            }
        }, HEIGHT);
        lifecycle.add(FINAL_HEIGHT, PlacementStage.BEFORE_BLOCKS,
                (context, state, ignored) -> state.y += state.yrand, POSITIONED, PlacementModuleCollision.CHECK);
    }

    static void install(PlacementLifecycleBuilder lifecycle, PlacementFunction<Void> sampledHeight) {
        lifecycle.add(HEIGHT, PlacementStage.HEIGHT, (context, state, ignored) -> {
            if (state.config.isFromBottom()) {
                state.y = context.self.getH() + 1 + state.rty;
            } else if (context.yv < 0) {
                sampledHeight.execute(context, state, null);
                return;
            } else {
                state.y = context.yv;
            }
            if (!state.config.isForcePlace()
                    && state.config.getMode() != ObjectPlaceMode.STRUCTURE_PIECE
                    && PlacementModuleSurfaceSupport.rejectsAnchor(context, state, context.x, state.y, context.z)) {
                state.reject("carving-anchor");
            }
        }, PlacementModulePreparation.TRANSFORMS);
    }
}
