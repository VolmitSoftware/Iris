package art.arcane.iris.structure.object;


import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.placement.PlacedObject;

/** Owns object collision policy and its data-engine requirement. */
final class PlacementModuleCollision implements PlacementModule {
    static final PlacementLifecycleDependency CHECK = new PlacementLifecycleDependency("validate.collisions");

    @Override
    public void install(PlacementLifecycleBuilder lifecycle) {
        lifecycle.add(CHECK, PlacementStage.VALIDATE, (context, state, ignored) -> {
            IrisObjectPlacement config = state.config;
            if (config.isForcePlace() || config.getMode() == ObjectPlaceMode.STRUCTURE_PIECE
                    || (config.getAllowedCollisions().isEmpty() && config.getForbiddenCollisions().isEmpty())) {
                return;
            }
            Engine engine = context.rdata.getEngine();
            if (engine == null) {
                throw new IllegalStateException("Object placement requires active Iris data for collision settings.");
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
                    for (int y = minimumY; y <= maximumY; y++) {
                        String marker = state.placer.getData(x, y, z, String.class);
                        PlacedObject placed = engine.resolveObjectPlacementMarker(x, z, marker);
                        if (placed == null || placed.getObject() == null) {
                            continue;
                        }
                        String key = placed.getObject().getLoadKey();
                        if (key != null && config.getForbiddenCollisions().contains(key)
                                && !config.getAllowedCollisions().contains(key)) {
                            state.reject("object-collision");
                            return;
                        }
                    }
                }
            }
        }, PlacementModuleBounds.WORLD);
    }
}
