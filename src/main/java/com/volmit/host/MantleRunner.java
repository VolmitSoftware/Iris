package com.volmit.host;

import com.volmit.generation.MantleGenerationContext;
import com.volmit.stage.Pipeline;
import java.util.Objects;

/** Reference executor of a resolved mantle graph: active stages run once per chunk, in resolved order.
 * The host runs it for every chunk within the pipeline's reach of a chunk before that chunk generates,
 * each chunk once, and gives it the mantle around the chunk as far as that reach. It does not roll back
 * mantle writes; a failed invocation must stop the host from generating the chunks it reaches.
 */
public final class MantleRunner {
    private final ResolvedGraph graph;

    public MantleRunner(ResolvedGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
        if (graph.pipeline() != Pipeline.MANTLE) {
            throw new IllegalArgumentException("Expected a mantle graph, got " + graph.pipeline());
        }
    }

    /** Runs the mantle stages of one chunk. A conditional stage is active when any of the chunk's
     * 16 by 16 columns has an entry of its settings, and receives every distinct entry of those columns.
     * The host derives {@code invocationSeed} from the world seed and the chunk position.
     */
    public void generate(MantleGenerationContext inputs, long invocationSeed) {
        if (graph.size() == 0) {
            return;
        }
        var settings = ChunkSettings.collect(graph, inputs::settings, inputs.originX(), inputs.originZ());
        var mantle = new MantleInvocation(new StageInvocation(graph, invocationSeed, settings), inputs);
        for (int position = 0; position < graph.size(); position++) {
            mantle.stages().runInvocationStage(position, mantle);
        }
    }
}
