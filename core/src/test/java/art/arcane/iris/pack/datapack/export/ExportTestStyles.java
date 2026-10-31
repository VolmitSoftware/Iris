package art.arcane.iris.pack.datapack.export;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.datapack.export.compile.noise.NoiseRegistry;
import art.arcane.iris.pack.datapack.export.compile.noise.StyleCompiler;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;

import java.util.List;
import java.util.Locale;

public final class ExportTestStyles implements StyleCompiler {
    private static final double COHERENT_BLOCKS = 64.0;
    private static final double WARP_FREQUENCY = 2.0;
    private static final double WARP_AMPLITUDE = 0.35;
    private static final List<Double> SINGLE_OCTAVE = List.of(1.0);

    @Override
    public Field compile(IrisGeneratorStyle style, long seedSalt, NoiseRegistry registry, ExportCapabilities capabilities) {
        if (style.isFlat()) {
            return new Field.Const(0.5);
        }
        String name = "test/" + Long.toUnsignedString(seedSalt, 36) + "_" + (style.getStyle() == null ? NoiseStyle.FLAT : style.getStyle()).name().toLowerCase(Locale.ROOT);
        String base = registry.register(new NoiseDef(name, 0, SINGLE_OCTAVE));
        double scale = 1.0 / (COHERENT_BLOCKS * style.getZoom());
        if (style.getFracture() == null) {
            return new Field.Noise(base, scale, 0.0, null, null, null);
        }
        String warp = registry.register(new NoiseDef(name + "_warp", 0, SINGLE_OCTAVE));
        Field shiftX = new Field.Binary(Field.BinaryOp.MUL, new Field.Const(WARP_AMPLITUDE), new Field.Noise(warp, scale * WARP_FREQUENCY, 0.0, null, null, null));
        Field shiftZ = new Field.Binary(Field.BinaryOp.MUL, new Field.Const(-WARP_AMPLITUDE), new Field.Noise(warp, scale * WARP_FREQUENCY * 1.5, 0.0, null, null, null));
        return new Field.Noise(base, scale, 0.0, shiftX, null, shiftZ);
    }
}
