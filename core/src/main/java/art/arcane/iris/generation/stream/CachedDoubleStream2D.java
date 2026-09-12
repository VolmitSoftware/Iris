package art.arcane.iris.generation.stream;

import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.MeteredCache;
import art.arcane.volmlib.util.stream.BasicStream;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.cache.WorldCache2DDouble;
import art.arcane.volmlib.util.data.KCache;

public class CachedDoubleStream2D extends BasicStream<Double> implements ProceduralStream<Double>, MeteredCache, ChunkFillableStream2D, ChunkFillableDoubleStream2D {
    private final ProceduralStream<Double> stream;
    private final WorldCache2DDouble cache;
    private final Engine engine;

    public CachedDoubleStream2D(String name, Engine engine, ProceduralStream<Double> stream, int size) {
        super();
        this.stream = stream;
        this.engine = engine;
        this.cache = new WorldCache2DDouble((x, z) -> stream.getDouble(x, z), size);
        IrisServices.get(PreservationRegistry.class).registerCache(this);
    }

    @Override
    public double toDouble(Double t) {
        return t;
    }

    @Override
    public Double fromDouble(double d) {
        return d;
    }

    @Override
    public Double get(double x, double z) {
        return cache.get((int) x, (int) z);
    }

    @Override
    public Double get(double x, double y, double z) {
        return stream.get(x, y, z);
    }

    @Override
    public double getDouble(double x, double z) {
        return cache.get((int) x, (int) z);
    }

    @Override
    public long getSize() {
        return cache.getSize();
    }

    @Override
    public KCache<?, ?> getRawCache() {
        return null;
    }

    @Override
    public long getMaxSize() {
        return cache.getMaxSize();
    }

    public void setMaximumChunks(int maximumChunks) {
        cache.setMaximumChunks(maximumChunks);
    }

    @Override
    public boolean isClosed() {
        return engine.isClosed();
    }

    @Override
    public void fillChunkRaw(int worldX, int worldZ, Object[] target) {
        int chunkX = worldX >> 4;
        int chunkZ = worldZ >> 4;
        cache.fillChunk(chunkX, chunkZ, target);
    }

    @Override
    public void fillChunkDoubles(int worldX, int worldZ, double[] target) {
        int chunkX = worldX >> 4;
        int chunkZ = worldZ >> 4;
        cache.fillChunk(chunkX, chunkZ, target);
    }
}
