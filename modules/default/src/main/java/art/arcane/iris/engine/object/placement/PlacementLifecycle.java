package art.arcane.iris.engine.object.placement;

import java.util.List;

/** Immutable plan. Placement performs no registration, graph traversal, or sorting. */
public final class PlacementLifecycle {
    private final List<List<PlacementLifecycleBuilder.Step<?>>> stages;

    PlacementLifecycle(List<PlacementLifecycleBuilder.Step<?>> ordered) {
        stages = PlacementStage.ALL.stream()
                .map(stage -> ordered.stream().filter(step -> step.stage() == stage).toList()).toList();
    }

    public PlacementState execute(PlacementContext context) {
        PlacementState state = new PlacementState(context);
        for (PlacementStage<Void> stage : PlacementStage.BEFORE_PASS) {
            run(stage, context, state, null);
        }
        run(PlacementStage.BLOCKS, context, state, this);
        run(PlacementStage.AFTER_BLOCKS, context, state, null);
        return state;
    }

    public <C> void run(PlacementStage<C> stage, PlacementContext context, PlacementState state, C input) {
        for (PlacementLifecycleBuilder.Step<?> step : stages.get(stage.order)) {
            if (state.isRejected()) {
                return;
            }
            // Registration pairs each function with its typed stage.
            @SuppressWarnings("unchecked")
            PlacementFunction<C> function = (PlacementFunction<C>) step.function();
            function.execute(context, state, input);
        }
    }
}
