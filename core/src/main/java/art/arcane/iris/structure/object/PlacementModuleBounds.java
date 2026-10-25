package art.arcane.iris.structure.object;

/** Computes the shared world volume for collision checks and boring, only when needed. */
final class PlacementModuleBounds implements PlacementModule {
    static final PlacementLifecycleDependency WORLD = new PlacementLifecycleDependency("bounds.world");

    @Override
    public void install(PlacementLifecycleBuilder lifecycle) {
        lifecycle.add(WORLD, PlacementStage.VALIDATE, (context, state, ignored) -> {
            IrisObjectPlacement config = state.config;
            if (config.isBore() || (!config.isForcePlace() && config.getMode() != ObjectPlaceMode.STRUCTURE_PIECE
                    && (!config.getAllowedCollisions().isEmpty() || !config.getForbiddenCollisions().isEmpty()))) {
                state.worldBounds = resolve(context, state, context.yv < 0 && config.getMode() == ObjectPlaceMode.PAINT);
            }
        }, PlacementModuleNativeStructureProtection.CHECK);
    }

    private static PlacementContext.WorldBounds resolve(PlacementContext context, PlacementState state, boolean paint) {
        int x = context.x;
        int z = context.z;
        int y = state.y + state.yrand;
        int minX = x + state.bounds.minX();
        int maxX = x + state.bounds.maxX();
        int minZ = z + state.bounds.minZ();
        int maxZ = z + state.bounds.maxZ();
        if (!paint) {
            return new PlacementContext.WorldBounds(minX, maxX, y + state.bounds.minY(), y + state.bounds.maxY(), minZ, maxZ);
        }

        int minimumSurface = Integer.MAX_VALUE;
        int maximumSurface = Integer.MIN_VALUE;
        for (int worldX = minX; worldX <= maxX; worldX++) {
            for (int worldZ = minZ; worldZ <= maxZ; worldZ++) {
                int surface = state.paintProjection.surfaceY(worldX, worldZ);
                if (surface == IrisPaintSurfaceProjection.MISSING) {
                    continue;
                }
                minimumSurface = Math.min(minimumSurface, surface);
                maximumSurface = Math.max(maximumSurface, surface);
            }
        }
        int paintOffset = Math.floorDiv(context.self.h, 2) + state.yrand;
        return new PlacementContext.WorldBounds(minX, maxX, minimumSurface + state.bounds.minY() + paintOffset,
                maximumSurface + state.bounds.maxY() + paintOffset, minZ, maxZ);
    }
}
