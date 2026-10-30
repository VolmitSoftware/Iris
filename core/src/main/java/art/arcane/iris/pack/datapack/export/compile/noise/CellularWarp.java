package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ir.Field;

import java.util.List;

final class CellularWarp {
    static final Offsets NONE = new Offsets(null, null);
    private static final double IRIS_FREQUENCY = 0.01D;
    private static final double STYLE_FREQUENCY = 1.0D / 64.0D;

    private CellularWarp() {
    }

    static Offsets signature(String prefix, Signature signature, double zoom, NoiseRegistry registry, DataVersion target) {
        String leafId = LeafNoise.register(registry);
        IrisSampleFit.Profile vanilla = IrisSampleFit.vanilla(target);
        Offsets inner = NONE;
        List<Level> levels = signature.levels();
        for (int index = levels.size() - 1; index >= 0; index--) {
            inner = level(prefix + "warp" + index, levels.get(index), inner, zoom, leafId, vanilla);
        }
        return inner;
    }

    static Offsets fracture(String prefix, Field fractureX, Field fractureZ, double amplitude) {
        return new Offsets(centered(prefix + "fracture_x", fractureX, amplitude), centered(prefix + "fracture_z", fractureZ, amplitude));
    }

    private static Offsets level(String name, Level level, Offsets inner, double zoom, String leafId, IrisSampleFit.Profile vanilla) {
        IrisSampleFit.Profile iris = IrisSampleFit.style(level.leaf());
        double frequency = level.scale() * IRIS_FREQUENCY / zoom;
        double xzScale = LeafNoise.xzScale(vanilla, iris.correlationLength() * STYLE_FREQUENCY / frequency);
        Field amplitude = new Field.Const(level.fracture() * zoom * iris.deviation() / vanilla.deviation());
        Field x = new Field.Binary(Field.BinaryOp.MUL, LeafNoise.sample(leafId, xzScale, name + "_x", inner.x(), inner.z()), amplitude);
        Field z = new Field.Binary(Field.BinaryOp.MUL, LeafNoise.sample(leafId, xzScale, name + "_z", inner.x(), inner.z()), amplitude);
        return new Offsets(new Field.Shared(name + "_x", x), new Field.Shared(name + "_z", z));
    }

    private static Field centered(String name, Field value, double amplitude) {
        Field offset = new Field.Binary(Field.BinaryOp.ADD, value, new Field.Const(-0.5D));
        return new Field.Shared(name, new Field.Binary(Field.BinaryOp.MUL, offset, new Field.Const(amplitude)));
    }

    enum Signature {
        IRIS(List.of(new Level(NoiseStyle.SIMPLEX, 0.9D, 44.0D), new Level(NoiseStyle.SIMPLEX, 0.21D, 145.0D), new Level(NoiseStyle.SIMPLEX, 0.9D, 620.0D))),
        THICK(List.of(new Level(NoiseStyle.SIMPLEX, 0.5D, 44.0D), new Level(NoiseStyle.SIMPLEX, 0.11D, 145.0D), new Level(NoiseStyle.SIMPLEX, 0.4D, 620.0D))),
        HALF(List.of(new Level(NoiseStyle.SIMPLEX, 0.9D, 22.0D), new Level(NoiseStyle.SIMPLEX, 0.21D, 99.0D), new Level(NoiseStyle.SIMPLEX, 0.9D, 420.0D))),
        DOUBLE(List.of(new Level(NoiseStyle.SIMPLEX, 1.0D, 93.0D), new Level(NoiseStyle.SIMPLEX, 0.9D, 44.0D), new Level(NoiseStyle.SIMPLEX, 0.21D, 145.0D),
                new Level(NoiseStyle.SIMPLEX, 0.9D, 620.0D))),
        PERLIN(List.of(new Level(NoiseStyle.PERLIN, 1.25D, 250.0D)));

        private final List<Level> levels;

        Signature(List<Level> levels) {
            this.levels = levels;
        }

        List<Level> levels() {
            return levels;
        }
    }

    record Offsets(Field x, Field z) {
    }

    record Level(NoiseStyle leaf, double scale, double fracture) {
    }
}
