package art.arcane.iris.structure.object;

/** The first world-writing step; all placement guards must have completed. */
final class PlacementModuleBore implements PlacementModule {
    static final PlacementLifecycleDependency BORE = new PlacementLifecycleDependency("bore");

    @Override
    public void install(PlacementLifecycleBuilder lifecycle) {
        lifecycle.add(BORE, PlacementStage.BEFORE_BLOCKS, (context, state, ignored) -> {
            if (!state.config.isBore()) {
                return;
            }
            var bounds = state.worldBounds;
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    boolean paint = state.paintProjection != null;
                    int surface = paint ? state.paintProjection.surfaceY(x, z) : 0;
                    if (surface == IrisPaintSurfaceProjection.MISSING) {
                        continue;
                    }
                    int offset = Math.floorDiv(context.self.h, 2) + state.yrand;
                    int minimumY = paint ? surface + state.bounds.minY() + offset : bounds.minY();
                    int maximumY = paint ? surface + state.bounds.maxY() + offset : bounds.maxY();
                    for (int y = minimumY - state.config.getBoreExtendMinY(); y <= maximumY + state.config.getBoreExtendMaxY(); y++) {
                        state.placer.set(x, y, z, IrisObject.States.air());
                    }
                }
            }
        }, PlacementModuleBounds.WORLD, PlacementModuleCollision.CHECK, PlacementModuleAnchor.FINAL_HEIGHT);
    }
}
