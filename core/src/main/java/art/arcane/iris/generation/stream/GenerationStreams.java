package art.arcane.iris.generation.stream;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.noise.IrisStyledRange;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.volmlib.util.function.Function3;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;

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

    public static <T> ProceduralStream<T> contextInjecting(ProceduralStream<T> stream, Function3<ChunkContext, Integer, Integer, T> contextAccessor) {
        //return stream;
        return new ContextInjectingStream<>(stream, contextAccessor);
    }

    public static <T> ProceduralStream<T> contextInjecting(ProceduralStream<T> stream, Engine engine, Function3<ChunkContext, Integer, Integer, T> contextAccessor) {
        return new ContextInjectingStream<>(stream, engine, contextAccessor);
    }

    public static <T> ProceduralStream<Double> style(ProceduralStream<T> stream, RNG rng, IrisStyledRange range, IrisData data) {
        return ProceduralStream.of((x, z) -> {
            double d = stream.getDouble(x, z);
            return range.get(rng, d, -d, data);
        }, Interpolated.DOUBLE);
    }
}
