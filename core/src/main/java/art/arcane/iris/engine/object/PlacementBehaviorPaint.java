package art.arcane.iris.engine.object;

public class PlacementBehaviorPaint extends PlacementBehavior {

    private PlacementContext context;
    protected ObjectPlacementConditions util;

    @Override
    public void setHeight() {
        context.y = context.placer.getHighest(context.x, context.z, context.self.getLoader(), context.config.isUnderwater()) + context.rty;
        if (!context.config.isForcePlace()) {
            if (util.shouldBailForCarvingAnchor(context.x, context.y, context.z)) {
                context.bail = true;
            }
        }
    }
}
