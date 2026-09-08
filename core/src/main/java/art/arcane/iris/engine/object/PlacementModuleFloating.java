package art.arcane.iris.engine.object;

public class PlacementModuleFloating extends PlacementModule {

    @Override
    public void setHeight() {
        state.y = context.rty;
    }

}
