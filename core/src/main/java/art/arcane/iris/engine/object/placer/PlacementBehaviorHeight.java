package art.arcane.iris.engine.object.placer;

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
    public int getHeight(PlacementContext context) {
        minX = context.x + context.bounds.minX();
        maxX = context.x + context.bounds.maxX();
        minZ = context.z + context.bounds.minZ();
        maxZ = context.z + context.bounds.maxZ();

        if (fast) {
            return computeFastHeight(context);
        } else {
            return computeHeight(context);
        }
    }

    private int computeFastHeight(PlacementContext context) {
        int xRadius = Math.max(0, (maxX - minX) / 2);
        int zRadius = Math.max(0, (maxZ - minZ) / 2);

        for (int i = minX; i <= maxX; i += Math.abs(xRadius) + 1) {
            for (int ii = minZ; ii <= maxZ; ii += Math.abs(zRadius) + 1) {
                if (columnHeight(context, i, ii)) return -1;
            }
        }
        return context.y;
    }

    private int computeHeight(PlacementContext context) {
        for (int i = minX; i <= maxX; i++) {
            for (int ii = minZ; ii <= maxZ; ii++) {
                if (columnHeight(context, i, ii)) return -1;
            }
        }
        return context.y;
    }

    private boolean columnHeight(PlacementContext context, int i, int ii) {
        int h = context.placer.getHighest(i, ii, context.self.getLoader(), context.config.isUnderwater()) + context.rty;
        if (!context.config.isForcePlace()) {
            if (context.shouldBailForCarvingAnchor(i, h, ii)) {
                return true;
            }
        }

        if (Mode.MAX == mode && h < context.y) {
            context.y = h;
        }
        if (Mode.MIN == mode && h > context.y) {
            context.y = h;
        }
        return false;
    }
}
