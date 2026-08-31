package art.arcane.iris.engine.object;

public class PlacementBehaviorCenterHeight implements PlacementBehavior{

    @Override
    public void setHeight(PlacementContext context) {
        context.y = (context.caveResult != null ? context.caveResult.getSurface() : context.placer.getHighest(context.x, context.z, context.self.getLoader(), context.config.isUnderwater())) + context.rty;
        if (!context.config.isForcePlace()) {
            if (context.shouldBailForCarvingAnchor()) {
               context.bail = true;
            }
        }
    }
}
