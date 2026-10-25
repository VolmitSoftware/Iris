package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.VectorMap;
import art.arcane.iris.generation.cave.CarvingMode;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

/** Floor, caller-supplied and from-bottom anchors wrap each mode's sampled height. */
final class PlacementModuleAnchor implements PlacementModule {
    static final PlacementLifecycleDependency HEIGHT = new PlacementLifecycleDependency("anchor.height");
    static final PlacementLifecycleDependency POSITIONED = new PlacementLifecycleDependency("anchor.positioned");
    static final PlacementLifecycleDependency FINAL_HEIGHT = new PlacementLifecycleDependency("anchor.final");

    @Override
    public void install(PlacementLifecycleBuilder lifecycle) {
        lifecycle.add(POSITIONED, PlacementStage.HEIGHT, (context, state, ignored) -> {
            if (context.yv >= 0 && state.config.isBottom()
                    && state.config.getMode() != ObjectPlaceMode.STRUCTURE_PIECE
                    && context.alignment != IrisObjectPlacementAlignment.FLOOR) {
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
            if (context.alignment == IrisObjectPlacementAlignment.FLOOR) {
                alignToFloor(context, state);
                return;
            }
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

    /** Seats the lowest occupied block on the caller's first air Y. */
    private static void alignToFloor(PlacementContext context, PlacementState state) {
        if (context.yv < 0 || state.ceilingHang || state.config.getMode() == ObjectPlaceMode.STRUCTURE_PIECE) {
            throw new IllegalArgumentException("Floor alignment requires an explicit floor Y and a floor placement mode.");
        }
        int minimumY = minimumOccupiedY(context.self, state.spin);
        if (minimumY == Integer.MAX_VALUE) {
            state.reject("empty-object");
            return;
        }
        state.y = context.yv - minimumY;
        if (!state.config.isForcePlace()
                && PlacementModuleSurfaceSupport.rejectsAnchor(context, state, context.x, context.yv, context.z)) {
            state.reject("carving-anchor");
        }
    }

    private static int minimumOccupiedY(IrisObject self, SpinKernel spin) {
        int minimumY = Integer.MAX_VALUE;
        self.readLock.lock();
        try {
            VectorMap<NativeBlockState>.Cursor cursor = self.blocks.cursor();
            while (cursor.next()) {
                NativeBlockState state = cursor.value();
                if (state == null || state.isAir()) {
                    continue;
                }
                IrisBlockVector position = cursor.key().clone();
                spin.rotate(position);
                minimumY = Math.min(minimumY, (int) Math.round(position.getY()));
            }
        } finally {
            self.readLock.unlock();
        }
        return minimumY;
    }
}
