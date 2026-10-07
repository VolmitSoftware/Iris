package art.arcane.iris.generation.stream;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.volmlib.util.function.Function3;
import art.arcane.volmlib.util.stream.ProceduralStream;

public final class GenerationStreams {
    private GenerationStreams() {
    }

    public static <T> CachedStream2D<T> cache2D(ProceduralStream<T> stream, String name, Engine engine, int size) {
        return new CachedStream2D<T>(name, engine, stream, size);
    }

    public static ProceduralStream<Double> cache2DDouble(ProceduralStream<Double> stream, String name, Engine engine, int size) {
        return new CachedDoubleStream2D(name, engine, stream, size);
    }

    public static <T> ProceduralStream<T> cache3D(ProceduralStream<T> stream, String name, Engine engine, int maxSize) {
        return new CachedStream3D<T>(name, engine, stream, maxSize);
    }

    public static <T> ProceduralStream<T> contextInjecting(ProceduralStream<T> stream, Engine engine, Function3<ChunkContext, Integer, Integer, T> contextAccessor) {
        return new ContextInjectingStream<>(stream, engine, contextAccessor);
    }
}
