package art.arcane.iris.generation.noise;

import art.arcane.volmlib.util.collection.KList;
import com.google.gson.Gson;
import org.junit.Test;

import java.util.SplittableRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IrisGeneratorExponentTest {
    private static final long SUPER_SEED = 4242L;
    private static final double OPACITY = 0.8D;
    private static final double TOLERANCE = 1e-12D;
    private static final int POINT_COUNT = 400;

    private final double[] xs = new double[POINT_COUNT];
    private final double[] zs = new double[POINT_COUNT];

    public IrisGeneratorExponentTest() {
        SplittableRandom random = new SplittableRandom(0xE590L);
        for (int index = 0; index < POINT_COUNT; index++) {
            xs[index] = random.nextDouble(-3000D, 3000D);
            zs[index] = random.nextDouble(-3000D, 3000D);
        }
    }

    @Test
    public void exponentDefaultsToOne() {
        assertEquals(1D, new IrisGenerator().getExponent(), 0D);
    }

    @Test
    public void exponentOfOneKeepsOriginalValuesBitForBit() {
        for (boolean multiplicitive : new boolean[]{false, true}) {
            IrisGenerator unset = generator(multiplicitive, OPACITY);
            IrisGenerator explicit = generator(multiplicitive, OPACITY).setExponent(1D);
            IrisGenerator unitOpacity = generator(multiplicitive, 1D);
            double[] fromUnset = batch(unset);
            double[] fromExplicit = batch(explicit);
            for (int index = 0; index < POINT_COUNT; index++) {
                double original = unitOpacity.getHeight(xs[index], zs[index], SUPER_SEED) * OPACITY;
                assertBits(original, unset.getHeight(xs[index], zs[index], SUPER_SEED));
                assertBits(original, explicit.getHeight(xs[index], zs[index], SUPER_SEED));
                assertBits(original, fromUnset[index]);
                assertBits(original, fromExplicit[index]);
            }
        }
    }

    @Test
    public void scalarProgramAndBatchPathsApplySignedPowerBeforeOpacity() {
        for (double exponent : new double[]{3D, 0.5D}) {
            for (boolean multiplicitive : new boolean[]{false, true}) {
                IrisGenerator combined = generator(multiplicitive, 1D);
                IrisGenerator curved = generator(multiplicitive, OPACITY).setExponent(exponent);
                GeneratorProgram program = curved.compileProgram(SUPER_SEED);
                TerrainBatchScratch scratch = new TerrainBatchScratch();
                double[] batched = batch(curved);
                for (int index = 0; index < POINT_COUNT; index++) {
                    double expected = signedPow(combined.getHeight(xs[index], zs[index], SUPER_SEED), exponent) * OPACITY;
                    String point = "exponent " + exponent + " multiplicitive " + multiplicitive + " point " + index;
                    assertEquals(point + " scalar", expected, curved.getHeight(xs[index], zs[index], SUPER_SEED), TOLERANCE);
                    assertEquals(point + " program", expected, program.height(scratch, xs[index], zs[index]), TOLERANCE);
                    assertEquals(point + " batch", expected, batched[index], TOLERANCE);
                }
            }
        }
    }

    @Test
    public void exponentChangesTheOutput() {
        IrisGenerator flat = generator(false, 1D);
        IrisGenerator curved = generator(false, 1D).setExponent(3D);
        boolean anyDifference = false;
        for (int index = 0; index < POINT_COUNT; index++) {
            anyDifference |= flat.getHeight(xs[index], zs[index], SUPER_SEED) != curved.getHeight(xs[index], zs[index], SUPER_SEED);
        }
        assertTrue(anyDifference);
    }

    @Test
    public void applyExponentKeepsTheSignOfNegativeValues() {
        assertEquals(-8D, IrisGenerator.applyExponent(-2D, 3D), TOLERANCE);
        assertEquals(-Math.sqrt(2D), IrisGenerator.applyExponent(-2D, 0.5D), TOLERANCE);
        assertEquals(-0.3D, IrisGenerator.applyExponent(-0.3D, 1D), 0D);
    }

    @Test
    public void jsonExponentDeserializesIntoTheField() {
        IrisGenerator generator = new Gson().fromJson("{\"exponent\":3}", IrisGenerator.class);
        assertEquals(3D, generator.getExponent(), 0D);
        assertEquals(1D, new Gson().fromJson("{}", IrisGenerator.class).getExponent(), 0D);
    }

    private double[] batch(IrisGenerator generator) {
        double[] out = new double[POINT_COUNT];
        generator.compileProgram(SUPER_SEED).heights(new TerrainBatchScratch(), xs, zs, POINT_COUNT, out);
        return out;
    }

    private static IrisGenerator generator(boolean multiplicitive, double opacity) {
        KList<IrisNoiseGenerator> composite = new KList<>();
        composite.add(new IrisNoiseGenerator().setStyle(NoiseStyle.SIMPLEX.style()).setSeed(11L).setZoom(1.3D).setOpacity(0.7D));
        composite.add(new IrisNoiseGenerator().setStyle(NoiseStyle.PERLIN.style()).setSeed(23L).setZoom(2.1D).setOpacity(1.4D));
        return new IrisGenerator().setComposite(composite)
                .setMultiplicitive(multiplicitive)
                .setSeed(5L)
                .setZoom(37D)
                .setOpacity(opacity);
    }

    private static double signedPow(double value, double exponent) {
        return value < 0 ? -Math.pow(-value, exponent) : Math.pow(value, exponent);
    }

    private static void assertBits(double expected, double actual) {
        assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual));
    }
}
