package art.arcane.iris.engine.object;

@FunctionalInterface
public interface PlacementFunction<C> {
    void execute(PlacementContext context, PlacementState state, C input);
}
