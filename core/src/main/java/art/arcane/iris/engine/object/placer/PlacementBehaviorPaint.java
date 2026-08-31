package art.arcane.iris.engine.object.placer;

public class PlacementBehaviorPaint implements PlacementBehavior {


    @Override
    public int getHeight(PlacementContext context) {
        context.y = context.placer.getHighest(context.x, context.z, context.self.getLoader(), context.config.isUnderwater()) + context.rty;
        if (!context.config.isForcePlace()) {
            if (context.shouldBailForCarvingAnchor(context.x, context.y, context.z)) {
               return -1;
            }
        }
        return context.y;
    }
}
