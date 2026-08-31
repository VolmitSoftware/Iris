package art.arcane.iris.engine.object.placer;

public class PlacementBehaviorFloating implements PlacementBehavior {

    @Override
    public int getHeight(PlacementContext context) {
        return context.y = context.rty;
    }

}
