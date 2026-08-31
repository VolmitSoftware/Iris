package art.arcane.iris.engine.object.placer;

public class PlacementBehaviorCenterHeight implements PlacementBehavior{

    @Override
    public int getHeight(PlacementContext context) {
        context.y = (context.caveResult != null ? context.caveResult.getSurface() : context.placer.getHighest(context.x, context.z, context.self.getLoader(), context.config.isUnderwater())) + context.rty;
        if (!context.config.isForcePlace()) {
            if (context.shouldBailForCarvingAnchor()) {
               return -1;
            }
        }
        return context.y;
    }
}
