package art.arcane.iris.pack.datapack.export.compile.cave;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.datapack.export.compile.noise.NoiseRegistry;
import art.arcane.iris.pack.datapack.export.compile.noise.StyleCompiler;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class FakeCaveStyles implements StyleCompiler {
    static final String CELLULAR_PREFIX = "cave_test/cellular/";
    private static final String SMOOTH_PREFIX = "cave_test/smooth/";
    private static final String WARP_PREFIX = "cave_test/warp/";
    private static final double BLOCKS_PER_ZOOM = 64.0;
    private static final List<Double> SINGLE_OCTAVE = List.of(1.0);

    private final Mode mode;
    private final List<NoiseStyle> requests = new ArrayList<>();

    private FakeCaveStyles(Mode mode) {
        this.mode = mode;
    }

    static FakeCaveStyles volumetric() {
        return new FakeCaveStyles(Mode.VOLUMETRIC);
    }

    static FakeCaveStyles planar() {
        return new FakeCaveStyles(Mode.PLANAR);
    }

    static FakeCaveStyles expensiveCellular() {
        return new FakeCaveStyles(Mode.EXPENSIVE_CELLULAR);
    }

    static FakeCaveStyles expensive() {
        return new FakeCaveStyles(Mode.EXPENSIVE);
    }

    static boolean cellular(NoiseStyle style) {
        String name = style.name();
        return name.contains("CELL") || name.contains("VASCULAR") || name.contains("GLOB") || name.contains("HEX");
    }

    static Field unit(Field signed) {
        return new Field.Curve(signed, List.of(
                new Field.Knot(-1.0, new Field.Const(0.0), 0.5),
                new Field.Knot(1.0, new Field.Const(1.0), 0.5)));
    }

    List<NoiseStyle> requests() {
        return List.copyOf(requests);
    }

    @Override
    public Field compile(IrisGeneratorStyle style, long seedSalt, NoiseRegistry registry, ExportCapabilities capabilities) {
        NoiseStyle noiseStyle = style.getStyle();
        requests.add(noiseStyle);
        boolean cellular = cellular(noiseStyle);
        if (cellular && mode == Mode.PLANAR) {
            return new Field.Coord(Axis.X, Field.Tiling.REPEAT, 0, 64, 0, 1);
        }
        String key = noiseStyle.name().toLowerCase(Locale.ROOT) + "_" + seedSalt;
        double scale = 1.0 / (BLOCKS_PER_ZOOM * style.getZoom());
        double yScale = mode == Mode.PLANAR ? 0.0 : scale;
        boolean warped = mode == Mode.EXPENSIVE || cellular && mode == Mode.EXPENSIVE_CELLULAR;
        Field shift = warped ? new Field.Noise(registry.register(new NoiseDef(WARP_PREFIX + key, 0, SINGLE_OCTAVE)), scale, yScale, null, null, null) : null;
        String id = registry.register(new NoiseDef((cellular ? CELLULAR_PREFIX : SMOOTH_PREFIX) + key, 0, SINGLE_OCTAVE));
        return unit(new Field.Noise(id, scale, yScale, shift, null, shift));
    }

    private enum Mode {
        VOLUMETRIC,
        PLANAR,
        EXPENSIVE_CELLULAR,
        EXPENSIVE
    }
}
