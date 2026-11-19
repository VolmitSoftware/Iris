package art.arcane.iris.generation.stream;

import art.arcane.iris.pack.value.Weighted;
import art.arcane.volmlib.util.stream.BasicStream;
import art.arcane.volmlib.util.stream.ProceduralStream;

import java.util.List;
import java.util.Objects;

public final class WeightedStream<T> extends BasicStream<T> {
    private final ProceduralStream<?> noise;
    private final List<Weighted<T>> entries;

    public WeightedStream(ProceduralStream<?> noise, List<Weighted<T>> entries) {
        super();
        this.noise = Objects.requireNonNull(noise, "Weighted noise");
        this.entries = List.copyOf(entries);
    }

    @Override
    public T get(double x, double z) {
        return Weighted.pick(entries, noise.getDouble(x, z), Weighted.Edge.EARLIER);
    }

    @Override
    public T get(double x, double y, double z) {
        return Weighted.pick(entries, noise.getDouble(x, y, z), Weighted.Edge.EARLIER);
    }

    @Override
    public double toDouble(T t) {
        return 0;
    }

    @Override
    public T fromDouble(double d) {
        return null;
    }
}
