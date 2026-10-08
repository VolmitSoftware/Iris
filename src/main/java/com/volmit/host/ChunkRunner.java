package com.volmit.host;

import com.volmit.generation.GenerationContext;
import com.volmit.stage.Pipeline;
import java.util.Objects;

/** Reference executor of a resolved chunk graph: active stages run once, in resolved order.
 * It does not schedule neighbors or roll back hunks; a failed invocation must prevent the host
 * from publishing the chunk. Stages see the mantle read-only.
 */
public final class ChunkRunner {
    private final ResolvedGraph graph;

    public ChunkRunner(ResolvedGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
        if (graph.pipeline() != Pipeline.CHUNK) {
            throw new IllegalArgumentException("Expected a chunk graph, got " + graph.pipeline());
        }
    }

    /** Runs one chunk. A conditional stage is active when any of the chunk's 16 by 16 columns has an
     * entry of its settings in {@code inputs.settings()}, and receives every distinct entry of those
     * columns. The host derives {@code invocationSeed} from the world seed and the chunk position; each
     * stage draws from its own stream of that seed ({@link StageStreams}).
     */
    public void generate(GenerationContext inputs, long invocationSeed) {
        if (graph.size() == 0) {
            return;
        }
        var settings = ChunkSettings.collect(graph.settingsTypes(), inputs::settings, inputs.originX(), inputs.originZ());
        var chunk = new ChunkInvocation(new StageInvocation(graph, invocationSeed, settings), inputs);
        for (int position = 0; position < graph.size(); position++) {
            chunk.stages().runInvocationStage(position, chunk);
        }
    }
}
