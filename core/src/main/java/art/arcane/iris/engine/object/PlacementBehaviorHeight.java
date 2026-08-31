package art.arcane.iris.engine.object;

public class PlacementBehaviorHeight implements PlacementBehavior {

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

    public PlacementBehaviorHeight(Mode mode, boolean fast) {
        this.fast = fast;
        this.mode = mode;
    }

    @Override
    public void setHeight(PlacementContext context) {
        minX = context.x + context.bounds.minX();
        maxX = context.x + context.bounds.maxX();
        minZ = context.z + context.bounds.minZ();
        maxZ = context.z + context.bounds.maxZ();
        if (Mode.MIN == mode) {
            context.y = context.placer.getEngine().getHeight() + 1;
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
                context.bail = true;
            }
        }

        if (Mode.MIN == mode && h < context.y) {
            context.y = h;
        }
        if (Mode.MAX == mode && h > context.y) {
            context.y = h;
        }
    }
}
