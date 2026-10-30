package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;

import java.util.List;

final class LeafNoise {
    static final String NOISE_ID = "cell_leaf";
    private static final int MARGINAL_INTERVALS = 32;

    private LeafNoise() {
    }

    static String register(NoiseRegistry registry) {
        return registry.register(new NoiseDef(NOISE_ID, 0, List.of(1.0D)));
    }

    static Field sample(String noiseId, double xzScale, String planeKey, Field shiftXBlocks, Field shiftZBlocks) {
        return new Field.Noise(noiseId, xzScale, 0.0D, scaled(shiftXBlocks, xzScale), new Field.Const(CellHashNoise.plane(planeKey)), scaled(shiftZBlocks, xzScale));
    }

    static double xzScale(IrisSampleFit.Profile vanilla, double irisCorrelationBlocks) {
        return vanilla.correlationLength() / irisCorrelationBlocks;
    }

    static Field toMarginal(Field noise, IrisSampleFit.Profile vanilla, IrisSampleFit.Profile iris) {
        return CurveKnots.monotone(noise, vanilla.quantiles(MARGINAL_INTERVALS), iris.quantiles(MARGINAL_INTERVALS));
    }

    private static Field scaled(Field shiftBlocks, double xzScale) {
        if (shiftBlocks == null) {
            return null;
        }
        return new Field.Binary(Field.BinaryOp.MUL, shiftBlocks, new Field.Const(xzScale));
    }
}
