package art.arcane.iris.structure.object;

import art.arcane.iris.generation.stream.ProvisionalAnswerException;
import art.arcane.iris.generation.stream.ProvisionalSampling;

import java.util.List;

/** Immutable plan. Placement performs no registration, graph traversal, or sorting. */
public final class PlacementLifecycle {
    private final List<List<PlacementLifecycleBuilder.Step<?>>> stages;

    PlacementLifecycle(List<PlacementLifecycleBuilder.Step<?>> ordered) {
        stages = PlacementStage.ALL.stream()
                .map(stage -> ordered.stream().filter(step -> step.stage() == stage).toList()).toList();
    }

    /** The block pass and everything after it read one consistent object snapshot. */
    public PlacementState execute(PlacementContext context) {
        PlacementState state = new PlacementState(context);
        long revision = ProvisionalSampling.revision();
        for (PlacementStage<Void> stage : PlacementStage.DECISION) {
            run(stage, context, state, null);
        }
        requireFinalAnswers(context, revision);
        run(PlacementStage.BEFORE_BLOCKS, context, state, null);
        if (state.isRejected()) {
            return state;
        }
        context.self.readLock.lock();
        try {
            run(PlacementStage.BLOCKS, context, state, this);
            run(PlacementStage.AFTER_BLOCKS, context, state, null);
        } finally {
            context.self.readLock.unlock();
        }
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

    private static void requireFinalAnswers(PlacementContext context, long revision) {
        if (context.yv >= 0 || ProvisionalSampling.revision() == revision) {
            return;
        }
        String key = context.self.getLoadKey();
        throw new ProvisionalAnswerException("Object " + (key == null ? "<no loadKey>" : key) + " at " + context.x + "," + context.z
                + " read a provisional terrain or water answer; automatic placements are only made from final terrain.");
    }
}
