package art.arcane.iris.structure.object;

/** Water, cave clearance and height-clamp eligibility. */
final class PlacementModuleLimits implements PlacementModule {
    static final PlacementLifecycleDependency CHECK = new PlacementLifecycleDependency("validate.limits");

    @Override
    public void install(PlacementLifecycleBuilder lifecycle) {
        lifecycle.add(CHECK, PlacementStage.VALIDATE, (context, state, ignored) -> {
            IrisObjectPlacement config = state.config;
            if (config.isForcePlace() || config.getMode() == ObjectPlaceMode.STRUCTURE_PIECE) {
                return;
            }
            if (context.yv < 0 && config.getMode() != ObjectPlaceMode.FLOATING
                    && !config.isUnderwater() && !config.isOnwater()
                    && state.placer.isUnderwater(context.x, context.z)) {
                state.reject("underwater-anchor");
                return;
            }
            if (context.yv < 0 && config.getMode() != ObjectPlaceMode.FLOATING
                    && config.isOnwater() && !state.placer.isUnderwater(context.x, context.z)) {
                state.reject("onwater-anchor");
                return;
            }
            if (context.caveResult != null
                    && Math.max(0, context.self.h + state.yrand + state.ty) + 1 >= context.caveResult.getHeight()) {
                state.reject("cave-clearance");
                return;
            }
            if (config.isUnderwater() && state.y + state.rty + state.ty >= state.placer.getFluidHeight(context.x, context.z)) {
                state.reject("fluid-height");
                return;
            }
            if (!config.getClamp().canPlace(state.y + state.rty + state.ty, state.y - state.rty + state.ty)) {
                state.reject("height-clamp");
            }
        }, PlacementModuleSurfaceSupport.CHECK);
    }
}
