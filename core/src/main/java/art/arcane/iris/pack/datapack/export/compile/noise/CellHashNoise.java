package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleUnaryOperator;

public final class CellHashNoise {
    public static final String NOISE_ID = "cell_hash";
    public static final double LATTICE_SPACING = 2.625D;
    public static final int CDF_INTERVALS = 168;
    private static final String REFERENCE_NOISE = "iris_export:cell_hash";
    private static final long REFERENCE_SEED = 0L;
    private static final double REFERENCE_PLANE = 7.375D;
    private static final int REFERENCE_SEEDS = 8;
    private static final long REFERENCE_SEED_STRIDE = 7_919L;
    private static final int REFERENCE_ROWS = 16;
    private static final int REFERENCE_COLUMNS = 32;
    private static final int PLANE_SLOTS = 1 << 17;
    private static final double PLANE_STEP = 4.0D;
    private static final double PLANE_OFFSET = 0.375D;
    private static final int VOLUME_PLANE_SLOTS = 1 << 22;
    private static final double VOLUME_PLANE_RESOLUTION = 512.0D;
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final Map<DataVersion, double[]> QUANTILES = new ConcurrentHashMap<>();

    private CellHashNoise() {
    }

    public static String register(NoiseRegistry registry) {
        return registry.register(new NoiseDef(NOISE_ID, 0, List.of(1.0D)));
    }

    public static double plane(String key) {
        return PLANE_OFFSET + PLANE_STEP * Math.floorMod(stableHash(key), PLANE_SLOTS);
    }

    public static double volumePlane(String key) {
        return Math.floorMod(stableHash(key), VOLUME_PLANE_SLOTS) / VOLUME_PLANE_RESOLUTION;
    }

    public static Field sample(String noiseId, Field shiftX, Field shiftY, Field shiftZ) {
        return new Field.Noise(noiseId, 0.0D, 0.0D, shiftX, shiftY, shiftZ);
    }

    public static Field uniform(Field hash, DataVersion target) {
        double[] quantiles = quantiles(target);
        double[] values = new double[quantiles.length];
        for (int index = 0; index < values.length; index++) {
            values[index] = (double) index / CDF_INTERVALS;
        }
        return CurveKnots.monotone(hash, quantiles, values);
    }

    public static Field periodic(Field hash, DataVersion target, DoubleUnaryOperator shape) {
        double[] quantiles = quantiles(target);
        double[] values = new double[quantiles.length];
        for (int index = 0; index < values.length; index++) {
            values[index] = shape.applyAsDouble((double) index / CDF_INTERVALS);
        }
        return CurveKnots.smooth(hash, quantiles, values);
    }

    public static Field white(String key, NoiseRegistry registry, DataVersion target) {
        Field hash = new Field.Noise(register(registry), LATTICE_SPACING, 0.0D, null, new Field.Const(plane(key)), null);
        return uniform(hash, target);
    }

    static long stableHash(String value) {
        long hash = FNV_OFFSET;
        for (byte part : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= part & 0xFFL;
            hash *= FNV_PRIME;
        }
        hash = (hash ^ hash >>> 30) * 0xbf58476d1ce4e5b9L;
        hash = (hash ^ hash >>> 27) * 0x94d049bb133111ebL;
        return hash ^ hash >>> 31;
    }

    private static double[] quantiles(DataVersion target) {
        return QUANTILES.computeIfAbsent(target, CellHashNoise::bake).clone();
    }

    private static double[] bake(DataVersion target) {
        double[] samples = new double[REFERENCE_SEEDS * REFERENCE_ROWS * REFERENCE_COLUMNS];
        int index = 0;
        for (int seed = 0; seed < REFERENCE_SEEDS; seed++) {
            VanillaNoiseModel model = VanillaNoiseModel.create(REFERENCE_NOISE, 0, List.of(1.0D), REFERENCE_SEED + REFERENCE_SEED_STRIDE * seed, target);
            for (int row = 0; row < REFERENCE_ROWS; row++) {
                for (int column = 0; column < REFERENCE_COLUMNS; column++) {
                    samples[index++] = model.sample((column - REFERENCE_COLUMNS / 2) * LATTICE_SPACING, REFERENCE_PLANE, (row - REFERENCE_ROWS / 2) * LATTICE_SPACING);
                }
            }
        }
        Arrays.sort(samples);
        return IrisSampleFit.quantiles(samples, CDF_INTERVALS);
    }
}
