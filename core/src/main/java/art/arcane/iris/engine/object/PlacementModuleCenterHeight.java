package art.arcane.iris.engine.object;

public class PlacementModuleCenterHeight extends PlacementModule {

    @Override
    public void setHeight() {
        state.y = (context.caveResult != null ? context.caveResult.getSurface() : context.placer.getHighest(context.x, context.z, context.self.getLoader(), context.config.isUnderwater())) + context.rty;
        if (!context.config.isForcePlace()) {
            if (context.shouldBailForCarvingAnchor(context.x, state.y, context.z)) {
               state.bail = true;
            }
        }
    }
}
