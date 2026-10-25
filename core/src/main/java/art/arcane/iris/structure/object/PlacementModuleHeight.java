package art.arcane.iris.structure.object;


public final class PlacementModuleHeight implements PlacementModule {
    private final boolean fast;
    private final Mode mode;

    public enum Mode {
        MIN,
        MAX
    }

    public PlacementModuleHeight(Mode mode, boolean fast) {
        this.fast = fast;
        this.mode = mode;
    }

    @Override
    public void install(PlacementLifecycleBuilder lifecycle) {
        PlacementModuleAnchor.install(lifecycle, this::sample);
    }

    private void sample(PlacementContext context, PlacementState state, Void ignored) {
        int minX = context.x + state.bounds.minX();
        int maxX = context.x + state.bounds.maxX();
        int minZ = context.z + state.bounds.minZ();
        int maxZ = context.z + state.bounds.maxZ();
        if (Mode.MIN == mode) {
            state.y = Integer.MAX_VALUE;
        }
        int xStep = fast ? Math.max(0, (maxX - minX) / 2) + 1 : 1;
        int zStep = fast ? Math.max(0, (maxZ - minZ) / 2) + 1 : 1;
        for (int i = minX; i <= maxX; i += xStep) {
            for (int ii = minZ; ii <= maxZ; ii += zStep) {
                columnHeight(context, state, i, ii);
                if (state.isRejected()) {
                    return;
                }
            }
        }
    }

    private void columnHeight(PlacementContext context, PlacementState state, int i, int ii) {
        int h = state.placer.getHighest(i, ii, context.self.getLoader(), state.config.isUnderwater()) + state.rty;
        if (!state.config.isForcePlace()) {
            if (PlacementModuleSurfaceSupport.rejectsAnchor(context, state, i, h, ii)) {
                state.reject("carving-anchor");
            }
        }

        if (Mode.MIN == mode && h < state.y) {
            state.y = h;
        }
        if (Mode.MAX == mode && h > state.y) {
            state.y = h;
        }
    }
}
