package art.arcane.iris.engine.object;

public class PlacementBehaviorCenterHeight extends PlacementBehavior{

    @Override
    public void setHeight() {
        context.y = (context.caveResult != null ? context.caveResult.getSurface() : context.placer.getHighest(context.x, context.z, context.self.getLoader(), context.config.isUnderwater())) + context.rty;
        if (!context.config.isForcePlace()) {
            if (util.shouldBailForCarvingAnchor(context.x, context.y, context.z)) {
               context.bail = true;
            }
        }
    }
}
