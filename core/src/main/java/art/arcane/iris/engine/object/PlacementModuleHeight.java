package art.arcane.iris.engine.object;

public class PlacementModuleHeight extends PlacementModule {

    int minX;
    int maxX;
    int minZ;
    int maxZ;

   private boolean fast;
   private Mode mode;

   public enum Mode {
        MIN,
        MAX
    }

    public PlacementModuleHeight(Mode mode, boolean fast) {
        this.fast = fast;
        this.mode = mode;
    }

    @Override
    public void setHeight() {
        minX = context.x + state.bounds.minX();
        maxX = context.x + state.bounds.maxX();
        minZ = context.z + state.bounds.minZ();
        maxZ = context.z + state.bounds.maxZ();
        if (Mode.MIN == mode) {
            state.y = context.placer.getEngine().getHeight() + 1;
        }
        if (fast) {
            computeFastHeight(context);
        } else {
            computeHeight(context);
        }
    }

    private void computeFastHeight(PlacementContext context) {
        int xRadius = Math.max(0, (maxX - minX) / 2);
        int zRadius = Math.max(0, (maxZ - minZ) / 2);

        for (int i = minX; i <= maxX; i += Math.abs(xRadius) + 1) {
            for (int ii = minZ; ii <= maxZ; ii += Math.abs(zRadius) + 1) {
                columnHeight(context, i, ii);
            }
        }
    }

    private void computeHeight(PlacementContext context) {
        for (int i = minX; i <= maxX; i++) {
            for (int ii = minZ; ii <= maxZ; ii++) {
                columnHeight(context, i, ii);
            }
        }
    }

    private void columnHeight(PlacementContext context, int i, int ii) {
        int h = context.placer.getHighest(i, ii, context.self.getLoader(), context.config.isUnderwater()) + context.rty;
        if (!context.config.isForcePlace()) {
            if (context.shouldBailForCarvingAnchor(i, h, ii)) {
                state.bail = true;
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
