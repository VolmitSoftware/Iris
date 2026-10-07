package art.arcane.iris.generation.stream;

import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.volmlib.util.function.Function3;
import art.arcane.volmlib.util.stream.BasicStream;
import art.arcane.volmlib.util.stream.ProceduralStream;

import java.util.Objects;
public class ContextInjectingStream<T> extends BasicStream<T> {
    private final Engine engine;
    private final Function3<ChunkContext, Integer, Integer, T> contextAccessor;

    public ContextInjectingStream(ProceduralStream<T> stream, Engine engine, Function3<ChunkContext, Integer, Integer, T> contextAccessor) {
        super(stream);
        this.engine = Objects.requireNonNull(engine);
        this.contextAccessor = contextAccessor;
    }

    @Override
    public T get(double x, double z) {
        IrisContext context = IrisContext.get();

        if (context != null) {
            ChunkContext chunkContext = context.getChunkContext();
            int blockX = (int) x;
            int blockZ = (int) z;

            if (chunkContext != null
                    && context.getEngine() == engine
                    && context.getGenerationSessionId() == chunkContext.getGenerationSessionId()
                    && context.getGenerationSessionId() == engine.getGenerationSessionId()
                    && blockX >> 4 == chunkContext.getX() >> 4
                    && blockZ >> 4 == chunkContext.getZ() >> 4) {
                T t = contextAccessor.apply(chunkContext, blockX & 15, blockZ & 15);

                if (t != null) {
                    return t;
                }
            }
        }

        return getTypedSource().get(x, z);
    }

    @Override
    public T get(double x, double y, double z) {
        return getTypedSource().get(x, y, z);
    }

    @Override
    public double toDouble(T t) {
        return getTypedSource().toDouble(t);
    }

    @Override
    public T fromDouble(double d) {
        return getTypedSource().fromDouble(d);
    }
}
