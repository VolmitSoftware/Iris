package art.arcane.iris.structure.object;

import art.arcane.iris.generation.decoration.tree.TreeGrounding;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.math.BlockPosition;

/** Procedural trees extend their roots into supporting terrain instead of stilting or floating. */
final class PlacementModuleTreeGrounding implements PlacementModule {
    static final PlacementLifecycleDependency PLAN = new PlacementLifecycleDependency("grounding.plan");
    static final PlacementLifecycleDependency ROOTS = new PlacementLifecycleDependency("grounding.roots");

    @Override
    public void install(PlacementLifecycleBuilder lifecycle) {
        lifecycle.add(PLAN, PlacementStage.VALIDATE, PlacementModuleTreeGrounding::plan, PlacementModuleAnchor.POSITIONED);
        lifecycle.add(ROOTS, PlacementStage.AFTER_BLOCKS, PlacementModuleTreeGrounding::extendRoots,
                PLAN, PlacementModuleBlocks.COMPLETE);
    }

    private static void plan(PlacementContext context, PlacementState state, Void ignored) {
        TreeGrounding grounding = context.self.getTreeGrounding();
        if (grounding == null || !isSurfaceTreePlacement(context, state)) {
            return;
        }
        state.grounding = grounding.plan(state.placer, new TreeGrounding.Placement(context.rdata, state.config,
                new BlockPosition(context.x, state.y + state.yrand, context.z),
                new BlockPosition(state.spinX, state.spinY, state.spinZ)));
        if (!state.grounding.supported()) {
            state.reject("tree-grounding");
        }
    }

    private static boolean isSurfaceTreePlacement(PlacementContext context, PlacementState state) {
        IrisObjectPlacement config = state.config;
        ObjectPlaceMode mode = config.getMode();
        return context.yv < 0 && config.getCarvingSupport().supportsSurface()
                && !config.isForcePlace() && !config.isFromBottom() && !state.ceilingHang
                && mode != ObjectPlaceMode.PAINT && mode != ObjectPlaceMode.FLOATING
                && mode != ObjectPlaceMode.STRUCTURE_PIECE;
    }

    private static void extendRoots(PlacementContext context, PlacementState state, Void ignored) {
        if (state.grounding == null) {
            return;
        }
        boolean waterlogCandidate = state.config.isWaterloggable() || state.config.isUnderwater();
        for (TreeGrounding.Extension extension : state.grounding.extensions()) {
            for (int rootY = extension.topY(); rootY >= extension.bottomY(); rootY--) {
                NativeBlockState root = ObjectBlockPass.normalizePlacedWaterlogging(extension.state(), state.placer,
                        extension.x(), rootY, extension.z(), waterlogCandidate);
                state.placer.set(extension.x(), rootY, extension.z(), root);
                if (context.listener != null) {
                    context.listener.accept(new BlockPosition(extension.x(), rootY, extension.z()), root);
                }
            }
        }
    }
}
