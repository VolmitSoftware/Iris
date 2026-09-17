package art.arcane.iris.engine.object;

public final class ObjectModuleCeilingHang implements PlacementModule {
    static final PlacementLifecycleDependency FLIP = new PlacementLifecycleDependency("ceiling.flip");

    @Override
    public void install(PlacementLifecycleBuilder lifecycle) {
        lifecycle.add(FLIP, PlacementStage.TRANSFORM_BLOCK,
                (context, state, block) -> block.vector().setY(-block.vector().getBlockY()));
    }
}
