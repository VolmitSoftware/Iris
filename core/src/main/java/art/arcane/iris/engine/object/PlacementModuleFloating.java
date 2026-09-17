package art.arcane.iris.engine.object;

public final class PlacementModuleFloating implements PlacementModule {

    @Override
    public void install(PlacementLifecycleBuilder lifecycle) {
        PlacementModuleAnchor.install(lifecycle, (context, state, ignored) -> state.y = state.rty);
    }

}
