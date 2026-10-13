package art.arcane.iris.generation.noise;

import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class IrisGeneratorBatchTest {
    private static final double[] XS = {-0D, 0D, -12D, -5.75D, 18D, 18D, 8191.25D, -29999999.5D, 2147483647D};
    private static final double[] ZS = {0D, -0D, 6D, 2.5D, -6D, -6D, -4096.75D, 29999998D, -2147483648D};

    @Test
    public void batchPreservesRawBitsAcrossStylesSeedsAndSurfaceDetails() {
        NoiseStyle[] styles = {NoiseStyle.SIMPLEX, NoiseStyle.IRIS, NoiseStyle.FRACTAL_SMOKE,
                NoiseStyle.CLOVER, NoiseStyle.CELLULAR, NoiseStyle.FLAT};
        long[] seeds = {0L, -1337L, Long.MAX_VALUE - 37L, Long.MIN_VALUE + 23L};
        for (NoiseStyle style : styles) {
            for (double detail : new double[]{0D, 0.5D, 1D}) {
                for (boolean multiply : new boolean[]{false, true}) {
                    IrisGenerator scalar = generator(style, detail, multiply);
                    IrisGenerator batch = generator(style, detail, multiply);
                    assertTrue(batch.supportsBatch());
                    double[] sampled = new double[XS.length];
                    for (long seed : seeds) {
                        batch.getHeights(XS, ZS, seed, sampled, sampled.length);
                        for (int index = 0; index < sampled.length; index++) {
                            assertEquals(style + " detail=" + detail + " seed=" + seed + " index=" + index,
                                    Double.doubleToRawLongBits(scalar.getHeight(XS[index], ZS[index], seed)),
                                    Double.doubleToRawLongBits(sampled[index]));
                        }
                    }
                }
            }
        }
    }

    @Test
    public void unroundedCompositeBatchRetainsEveryNoiseBit() {
        for (NoiseStyle style : new NoiseStyle[]{NoiseStyle.SIMPLEX, NoiseStyle.IRIS, NoiseStyle.CLOVER}) {
            for (double detail : new double[]{0D, 0.5D, 1D}) {
                IrisGenerator scalar = generator(style, detail, false).setCliffHeightMax(0D).setCellFractureHeight(0D);
                IrisGenerator batch = generator(style, detail, false).setCliffHeightMax(0D).setCellFractureHeight(0D);
                double[] values = new double[XS.length];
                for (long seed : new long[]{-1337L, Long.MAX_VALUE - 37L}) {
                    batch.getHeights(XS, ZS, seed, values, XS.length);
                    for (int index = 0; index < XS.length; index++) {
                        assertEquals(Double.doubleToRawLongBits(scalar.getHeight(XS[index], ZS[index], seed)),
                                Double.doubleToRawLongBits(values[index]));
                    }
                }
            }
        }
    }

    @Test
    public void resolvesLayerContextOncePerBatch() {
        IrisNoiseGenerator layer = spy(new IrisNoiseGenerator().setStyle(NoiseStyle.SIMPLEX.style()));
        IrisGenerator generator = new IrisGenerator().setComposite(new KList<>(layer));
        double[] sampled = new double[XS.length];
        generator.getHeights(XS, ZS, 99L, sampled, XS.length);
        verify(layer, times(1)).getGenerator(anyLong(), isNull(), isNull());
        clearInvocations(layer);
        for (int index = 0; index < XS.length; index++) {
            generator.getHeight(XS[index], ZS[index], 99L);
        }
        verify(layer, times(XS.length)).getGenerator(anyLong(), isNull(), isNull());
    }

    @Test
    public void customGeneratorFallbackKeepsCoordinateOrder() {
        List<Double> sampled = new ArrayList<>();
        IrisGenerator generator = new IrisGenerator() {
            @Override
            public double getHeight(double x, double z, long seed) {
                sampled.add(x);
                return sampled.size();
            }
        };
        assertFalse(generator.supportsBatch());
        double[] values = new double[XS.length];
        generator.getHeights(XS, ZS, 0L, values, values.length);
        for (int index = 0; index < values.length; index++) {
            assertEquals(Double.valueOf(XS[index]), sampled.get(index));
            assertEquals(index + 1D, values[index], 0D);
        }
        assertThrows(IllegalArgumentException.class,
                () -> generator.getHeights(new double[4097], new double[4097], 0L, new double[4097], 4097));
    }

    @Test
    public void expressionsAndCustomStylesKeepScalarSampling() {
        assertFalse(new IrisGenerator().setComposite(new KList<>(new IrisNoiseGenerator()
                .setStyle(new IrisGeneratorStyle().setExpression("height")))).supportsBatch());
        assertFalse(new IrisGenerator().setComposite(new KList<>(new IrisNoiseGenerator()
                .setStyle(new IrisGeneratorStyle() {}))).supportsBatch());
    }

    private static IrisGenerator generator(NoiseStyle style, double detail, boolean multiply) {
        IrisNoiseGenerator layer = new IrisNoiseGenerator().setStyle(style.style()).setSeed(-831L)
                .setZoom(0.73D).setOffsetX(3.25D).setOffsetZ(-1.75D).setOpacity(0.7D)
                .setNegative(true).setExponent(1.37D).setOffsetY(0.03D).setParametric(true);
        IrisNoiseGenerator node = layer;
        for (int depth = 0; depth < 5; depth++) {
            IrisNoiseGenerator child = new IrisNoiseGenerator().setStyle(NoiseStyle.SIMPLEX.style())
                    .setSeed(depth * 7L).setOpacity(3.1D).setZoom(1.7D);
            node.setFracture(new KList<>(child));
            node = child;
        }
        return new IrisGenerator().setComposite(new KList<>(layer,
                        new IrisNoiseGenerator().setStyle(NoiseStyle.SIMPLEX.style()).setOpacity(0.2D)))
                .setSurfaceDetail(detail).setMultiplicitive(multiply).setSeed(-93L).setZoom(1.3D)
                .setOffsetX(7.25D).setOffsetZ(-9.125D).setOpacity(0.93D)
                .setCliffHeightMin(3.5D).setCliffHeightMax(12.25D)
                .setCliffHeightGenerator(new IrisNoiseGenerator().setStyle(NoiseStyle.SIMPLEX.style()))
                .setCellFractureHeight(0.25D);
    }
}
