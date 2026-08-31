package art.arcane.iris.engine.object;

public class PlacementBehaviorFloating implements PlacementBehavior {

    @Override
    public void setHeight(PlacementContext context) {
        context.y = context.rty;
    }

}
