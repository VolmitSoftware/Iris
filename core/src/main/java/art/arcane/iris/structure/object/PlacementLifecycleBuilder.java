package art.arcane.iris.structure.object;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Registers steps and resolves their graph before an engine runtime is published.
 * A dependency names a required step, not a Java class: two instances of the same module can
 * register distinct work. Declaring a dependency does not install its prerequisite.
 * The module composition must register it, in any order, before calling {@link #build()}.
 *
 * <pre>{@code
 * lifecycle.add(FILL, PlacementStage.AFTER_BLOCKS, this::fill, HEIGHT, BOUNDS);
 * }</pre>
 *
 * Independent steps retain registration order within their stage. Invalid graphs fail construction;
 * the builder never executes functions. Functions must not capture mutable placement state.
 */
public final class PlacementLifecycleBuilder {
    private final Map<PlacementLifecycleDependency, Step<?>> steps = new LinkedHashMap<>();

    public <C> void add(PlacementLifecycleDependency id, PlacementStage<C> stage,
                        PlacementFunction<C> function, PlacementLifecycleDependency... after) {
        Objects.requireNonNull(id, "Step id");
        Objects.requireNonNull(stage, "Stage");
        Objects.requireNonNull(function, "Function");
        Step<C> step = new Step<>(id, stage, function, List.of(after), steps.size());
        if (steps.putIfAbsent(id, step) != null) {
            throw new IllegalArgumentException("Duplicate placement step '" + id.name() + "'");
        }
    }

    /** Stable topological sort; stage barriers do not need all-to-all edges. */
    public PlacementLifecycle build() {
        Map<PlacementLifecycleDependency, Integer> remaining = new HashMap<>();
        Map<PlacementLifecycleDependency, List<Step<?>>> dependents = new HashMap<>();
        PriorityQueue<Step<?>> ready = new PriorityQueue<>(Comparator.<Step<?>>comparingInt(step -> step.stage.order).thenComparingInt(Step::index));
        for (Step<?> step : steps.values()) {
            var prerequisites = step.after.stream().distinct().toList();
            remaining.put(step.id, prerequisites.size());
            for (PlacementLifecycleDependency id : prerequisites) {
                Step<?> prerequisite = steps.get(id);
                if (prerequisite == null) {
                    throw new IllegalArgumentException("Placement step '" + step.id.name()
                            + "' requires missing step '" + id.name() + "'");
                }
                if (prerequisite.stage.order > step.stage.order) {
                    throw new IllegalArgumentException("Placement step '" + step.id.name() + "' in " + step.stage
                            + " cannot depend on later step '" + id.name() + "' in " + prerequisite.stage);
                }
                dependents.computeIfAbsent(id, ignored -> new ArrayList<>()).add(step);
            }
            if (prerequisites.isEmpty()) {
                ready.add(step);
            }
        }
        List<Step<?>> ordered = new ArrayList<>(steps.size());
        while (!ready.isEmpty()) {
            Step<?> step = ready.remove();
            ordered.add(step);
            for (Step<?> dependent : dependents.getOrDefault(step.id, List.of())) {
                if (remaining.compute(dependent.id, (ignored, count) -> count - 1) == 0) {
                    ready.add(dependent);
                }
            }
        }
        if (ordered.size() != steps.size()) {
            throw new IllegalArgumentException("Placement dependency cycle; unresolved steps: "
                    + steps.keySet().stream().filter(id -> remaining.get(id) > 0)
                    .map(PlacementLifecycleDependency::name).toList());
        }
        return new PlacementLifecycle(ordered);
    }

    record Step<C>(PlacementLifecycleDependency id, PlacementStage<C> stage, PlacementFunction<C> function,
                   List<PlacementLifecycleDependency> after, int index) {
    }
}
