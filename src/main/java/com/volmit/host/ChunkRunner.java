package com.volmit.host;

import com.volmit.generation.GenerationContext;
import com.volmit.stage.Pipeline;
import java.util.Objects;

/** Reference executor of a resolved chunk graph: active stages run once, in resolved order.
 * It does not schedule neighbors or roll back hunks; a failed invocation must prevent the host
 * from publishing the chunk.
 */
public final class ChunkRunner {
    private final ResolvedGraph graph;

    public ChunkRunner(ResolvedGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
        if (graph.pipeline() != Pipeline.CHUNK) {
            throw new IllegalArgumentException("Expected a chunk graph, got " + graph.pipeline());
        }
    }

    public void generate(GenerationContext inputs, ConfiguredSettings settings) {
        var chunk = new ChunkInvocation(new StageInvocation(graph, settings), inputs);
        for (int position = 0; position < graph.size(); position++) {
            chunk.stages().runInvocationStage(position, chunk);
        }
    }
}
