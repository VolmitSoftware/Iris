package art.arcane.iris.engine.object;

public class PlacementBehaviorFloating extends PlacementBehavior {

    @Override
    public void setHeight() {
        context.y = context.rty;
    }

}
