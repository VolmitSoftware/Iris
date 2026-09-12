package art.arcane.iris.generation.stream;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ContextInjectingStreamTest {
    @Test
    public void matchingOwnerSessionAndChunkUsesContextCache() {
        Engine engine = mock(Engine.class);
        when(engine.getGenerationSessionId()).thenReturn(41L);
        ChunkContext chunkContext = chunkContext(32, 48, 41L);
        ProceduralStream<String> source = sourceStream();
        ContextInjectingStream<String> stream = new ContextInjectingStream<>(source, engine,
                (context, x, z) -> "cache-" + x + "-" + z);

        try (IrisContext.Scope scope = IrisContext.open(engine, 41L, chunkContext)) {
            assertEquals("cache-3-3", stream.get(35D, 51D));
        }
    }

    @Test
    public void mismatchedOwnerSessionChunkOrCoordinatesFallsBackToSource() {
        Engine engine = mock(Engine.class);
        Engine otherEngine = mock(Engine.class);
        when(engine.getGenerationSessionId()).thenReturn(41L);
        when(otherEngine.getGenerationSessionId()).thenReturn(41L);
        ProceduralStream<String> source = sourceStream();
        ContextInjectingStream<String> stream = new ContextInjectingStream<>(source, engine,
                (context, x, z) -> "cache");

        try (IrisContext.Scope scope = IrisContext.open(otherEngine, 41L, chunkContext(32, 48, 41L))) {
            assertEquals("source", stream.get(35D, 51D));
        }
        try (IrisContext.Scope scope = IrisContext.open(engine, 40L, chunkContext(32, 48, 40L))) {
            assertEquals("source", stream.get(35D, 51D));
        }
        try (IrisContext.Scope scope = IrisContext.open(engine, 41L, chunkContext(32, 48, 41L))) {
            assertEquals("source", stream.get(64D, 80D));
        }
        try (IrisContext.Scope scope = IrisContext.open(engine, 41L, null)) {
            assertEquals("source", stream.get(35D, 51D));
        }
    }

    private ChunkContext chunkContext(int x, int z, long generationSessionId) {
        ChunkContext context = mock(ChunkContext.class);
        when(context.getX()).thenReturn(x);
        when(context.getZ()).thenReturn(z);
        when(context.getGenerationSessionId()).thenReturn(generationSessionId);
        return context;
    }

    @SuppressWarnings("unchecked")
    private ProceduralStream<String> sourceStream() {
        ProceduralStream<String> source = mock(ProceduralStream.class);
        when(source.get(anyDouble(), anyDouble())).thenReturn("source");
        return source;
    }
}
