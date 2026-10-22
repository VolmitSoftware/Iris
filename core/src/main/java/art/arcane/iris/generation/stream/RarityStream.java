package art.arcane.iris.generation.stream;

import art.arcane.volmlib.util.math.Rarity;
import art.arcane.volmlib.util.stream.BasicStream;
import art.arcane.volmlib.util.stream.ProceduralStream;

import java.util.List;
import java.util.Objects;

public final class RarityStream<T extends Rarity> extends BasicStream<T> {
    private final ProceduralStream<?> noise;
    private final Object[] options;
    private final double[] cumulative;

    public RarityStream(ProceduralStream<?> noise, List<T> options) {
        super();
        this.noise = Objects.requireNonNull(noise, "Rarity noise");
        this.options = options.toArray();
        this.cumulative = new double[this.options.length];
        double buffer = 0;
        for (int index = 0; index < this.options.length; index++) {
            buffer += 1d / Rarity.get(this.options[index]);
            cumulative[index] = buffer;
        }
    }

    @Override
    public T get(double x, double z) {
        return pick(noise.getDouble(x, z));
    }

    @Override
    public T get(double x, double y, double z) {
        return pick(noise.getDouble(x, y, z));
    }

    @Override
    public double toDouble(T t) {
        return 0;
    }

    @Override
    public T fromDouble(double d) {
        return null;
    }

    @SuppressWarnings("unchecked")
    public T pick(double noiseValue) {
        int count = options.length;
        if (count == 0) {
            return null;
        }
        if (count == 1) {
            return (T) options[0];
        }
        double threshold = cumulative[count - 1] * noiseValue;
        for (int index = 0; index < count; index++) {
            if (cumulative[index] >= threshold) {
                return (T) options[index];
            }
        }
        return (T) options[count - 1];
    }
}
