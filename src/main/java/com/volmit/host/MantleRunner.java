package com.volmit.host;

import com.volmit.generation.MantleGenerationContext;
import com.volmit.stage.Pipeline;
import com.volmit.stage.Stage;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

/** Reference executor of a resolved mantle graph, one stage at a time. Every mantle stage is its own pass:
 * the host runs a stage for every chunk within that stage's reach plus the reach of every later stage of
 * a chunk, each chunk once, before the next stage starts, and before that chunk generates. Each call gives
 * the stage the mantle around one chunk as far as the stage's own reach. It does not roll back mantle
 * writes; a failed invocation must stop the host from generating the chunks it reaches.
 */
public final class MantleRunner {
    private final ResolvedGraph graph;
    private final Map<Stage<?>, Integer> positions = new IdentityHashMap<>();
    private final List<List<Class<?>>> activationSettings;

    public MantleRunner(ResolvedGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
        if (graph.pipeline() != Pipeline.MANTLE) {
            throw new IllegalArgumentException("Expected a mantle graph, got " + graph.pipeline());
        }
        for (int position = 0; position < graph.size(); position++) {
            positions.put(graph.executable(position).stage(), position);
        }
        activationSettings = activationSettings(graph);
    }

    /** Runs {@code stage} for one chunk, if it is active there. A conditional stage is active when any of
     * the chunk's 16 by 16 columns has an entry of its settings, and receives every distinct entry of those
     * columns; it is inactive wherever a stage it requires is. The host derives {@code invocationSeed} from
     * the world seed and the chunk position; each stage draws from its own stream of it.
     */
    public void generate(Stage<?> stage, MantleGenerationContext inputs, long invocationSeed) {
        int position = position(stage);
        var settings = ChunkSettings.collect(activationSettings.get(position), inputs::settings, inputs.originX(), inputs.originZ());
        var mantle = new MantleInvocation(new StageInvocation(graph, invocationSeed, settings), inputs);
        mantle.stages().runInvocationStage(position, mantle);
    }

    private int position(Stage<?> stage) {
        Integer position = positions.get(Objects.requireNonNull(stage, "stage"));
        if (position == null) {
            throw new IllegalArgumentException("Stage " + stage + " is not a stage of this mantle graph");
        }
        return position;
    }

    /** Per position, the settings types that decide whether the stage is active: its own and those of every
     * stage it transitively requires, in the graph's order. Requirements come earlier in the order, so one
     * forward sweep completes each stage's set before a dependent merges it.
     */
    private static List<List<Class<?>>> activationSettings(ResolvedGraph graph) {
        var slots = new BitSet[graph.size()];
        for (int position = 0; position < slots.length; position++) {
            var executable = graph.executable(position);
            slots[position] = new BitSet();
            if (executable.settingsSlot() != ExecutableStage.NO_SETTINGS) {
                slots[position].set(executable.settingsSlot());
            }
            for (int requirement : executable.requirements()) {
                slots[position].or(slots[requirement]);
            }
        }
        var types = graph.settingsTypes();
        return IntStream.range(0, slots.length)
                .mapToObj(position -> slots[position].stream().<Class<?>>mapToObj(types::get).toList())
                .toList();
    }
}
