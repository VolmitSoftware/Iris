package art.arcane.iris.structure.object;


@FunctionalInterface
public interface PlacementFunction<C> {
    void execute(PlacementContext context, PlacementState state, C input);
}
