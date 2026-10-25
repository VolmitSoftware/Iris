package art.arcane.iris.structure.object;

import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.noise.CNG;

public final class PlacementModulePaint implements PlacementModule {

    static final PlacementLifecycleDependency PROJECT = new PlacementLifecycleDependency("paint.project");

    @Override
    public void install(PlacementLifecycleBuilder lifecycle) {
        PlacementModuleAnchor.install(lifecycle, PlacementModulePaint::sample);
    }

    private static void sample(PlacementContext context, PlacementState state, Void ignored) {
        int anchorX = context.x + (state.translateOffset == null ? 0 : (int) Math.round(state.translateOffset.getX()));
        int anchorZ = context.z + (state.translateOffset == null ? 0 : (int) Math.round(state.translateOffset.getZ()));
        state.y = (context.caveResult != null ? context.caveResult.getSurface()
                : state.placer.getHighest(anchorX, anchorZ, context.self.getLoader(), state.config.isUnderwater())) + state.rty;
        if (!state.config.isForcePlace()) {
            if (PlacementModuleSurfaceSupport.rejectsAnchor(context, state, context.x, state.y, context.z)) {
                state.reject("carving-anchor");
            }
        }
    }
    static void installProjection(PlacementLifecycleBuilder lifecycle) {
        lifecycle.add(PROJECT, PlacementStage.VALIDATE, (context, state, ignored) -> {
            if (context.yv >= 0 || state.config.getMode() != ObjectPlaceMode.PAINT) {
                return;
            }
            int buffer = state.config.isRequireSurfaceSupport() ? Math.max(0, state.config.getSurfaceSupportBuffer()) : 0;
            int anchorX = context.x + (state.translateOffset == null ? 0 : (int) Math.round(state.translateOffset.getX()));
            int anchorZ = context.z + (state.translateOffset == null ? 0 : (int) Math.round(state.translateOffset.getZ()));
            state.paintProjection = IrisPaintSurfaceProjection.create(state.placer, new IrisPaintSurfaceProjection.Request(
                    context.self.getLoader(), context.x + state.bounds.minX() - buffer, context.x + state.bounds.maxX() + buffer,
                    context.z + state.bounds.minZ() - buffer, context.z + state.bounds.maxZ() + buffer,
                    anchorX, state.y - state.rty, anchorZ, state.config.isUnderwater(), state.config.getHeightmap() != null));
            if (state.paintProjection == null) {
                state.reject("paint-projection");
                return;
            }
            var warp = state.config.getWarp().isFlat() ? null : state.config.getSurfaceWarp(context.rng, context.self.getLoader());
            double half = state.config.getWarp().isFlat() ? 0 : state.config.getWarp().getMultiplier() / 2D;
            state.paintSupport = transformedPaintSupport(context.self, state.spin, state.translateOffset, warp, half,
                    context.x, state.y + state.yrand, context.z);
        }, PlacementModuleAnchor.POSITIONED);
    }

    private static KList<IrisBlockVector> transformedPaintSupport(IrisObject self, SpinKernel spin, IrisBlockVector translateOffset,
                                                          CNG warp, double half, int x, int y, int z) {
        KList<IrisBlockVector> offsets = new KList<>();
        for (IrisBlockVector source : self.getSurfaceSupportOffsets()) {
            IrisBlockVector offset = source.clone();
            spin.rotate(offset);
            if (translateOffset != null) {
                offset.add(translateOffset);
            }
            int worldX = x + (int) Math.round(offset.getX());
            int worldZ = z + (int) Math.round(offset.getZ());
            if (warp != null) {
                worldX += warp.fitDouble(-half, half, offset.getX() + x, offset.getY() + y, offset.getZ() + z);
                worldZ += warp.fitDouble(-half, half, offset.getZ() + z, offset.getY() + y, offset.getX() + x);
            }
            offsets.add(new IrisBlockVector(worldX - x, 0, worldZ - z));
        }
        return offsets;
    }

}
