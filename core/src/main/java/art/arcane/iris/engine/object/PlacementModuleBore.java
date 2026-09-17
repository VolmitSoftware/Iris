package art.arcane.iris.engine.object;

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
                for (int y = bounds.minY() - state.config.getBoreExtendMinY();
                     y <= bounds.maxY() + state.config.getBoreExtendMaxY(); y++) {
                    for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                        state.placer.set(x, y, z, IrisObject.States.AIR);
                    }
                }
            }
        }, PlacementModuleBounds.WORLD, PlacementModuleCollision.CHECK, PlacementModuleAnchor.FINAL_HEIGHT);
    }
}
