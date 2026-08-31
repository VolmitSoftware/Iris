package art.arcane.iris.engine.object;

public class PlacementBehaviorPaint implements PlacementBehavior {


    @Override
    public void setHeight(PlacementContext context) {
        context.y = context.placer.getHighest(context.x, context.z, context.self.getLoader(), context.config.isUnderwater()) + context.rty;
        if (!context.config.isForcePlace()) {
            if (context.shouldBailForCarvingAnchor(context.x, context.y, context.z)) {
                context.bail = true;
            }
        }
    }
}
