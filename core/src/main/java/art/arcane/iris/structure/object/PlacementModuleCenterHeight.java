package art.arcane.iris.structure.object;

public final class PlacementModuleCenterHeight implements PlacementModule {

    @Override
    public void install(PlacementLifecycleBuilder lifecycle) {
        PlacementModuleAnchor.install(lifecycle, PlacementModuleCenterHeight::sample);
    }

    private static void sample(PlacementContext context, PlacementState state, Void ignored) {
        state.y = (context.caveResult != null ? context.caveResult.getSurface() : state.placer.getHighest(context.x, context.z, context.self.getLoader(), state.config.isUnderwater())) + state.rty;
        if (!state.config.isForcePlace()) {
            if (PlacementModuleSurfaceSupport.rejectsAnchor(context, state, context.x, state.y, context.z)) {
                state.reject("carving-anchor");
            }
        }
    }
}
