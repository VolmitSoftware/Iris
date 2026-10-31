package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.WorldgenExportTargets;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleBinaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IrisSampleFitTest {
    private static final int ROWS = 48;
    private static final int LENGTH = 512;
    private static final int LAGS = 96;

    @Test
    public void fittedLeafMatchesIrisCorrelationLength() {
        List<DoubleBinaryOperator> irisSamplers = new ArrayList<>();
        for (long seed = 0L; seed < 4L; seed++) {
            CNG simplex = NoiseStyle.SIMPLEX.create(new RNG(77L + seed));
            irisSamplers.add(simplex::noise);
        }
        double iris = correlationLength(irisSamplers);
        for (DataVersion target : WorldgenExportTargets.SUPPORTED) {
            IrisSampleFit.Profile vanilla = IrisSampleFit.vanilla(target);
            double xzScale = LeafNoise.xzScale(vanilla, IrisSampleFit.style(NoiseStyle.SIMPLEX).correlationLength());
            List<DoubleBinaryOperator> vanillaSamplers = new ArrayList<>();
            for (long seed = 0L; seed < 16L; seed++) {
                VanillaNoiseModel model = VanillaNoiseModel.create("iris_export:fit_check", 0, List.of(1.0D), 5L + 101L * seed, target);
                vanillaSamplers.add((x, z) -> model.sample(x * xzScale, 3.375D, z * xzScale));
            }
            double fitted = correlationLength(vanillaSamplers);
            assertEquals(target + " fitted correlation length", iris, fitted, 0.1D * iris);
        }
    }

    @Test
    public void profilesAreDeterministic() {
        IrisSampleFit.Profile first = IrisSampleFit.style(NoiseStyle.VASCULAR);
        assertTrue(first.correlationLength() > 0.0D);
        assertEquals(first.quantile(0.5D), IrisSampleFit.style(NoiseStyle.VASCULAR).quantile(0.5D), 0.0D);
        double[] quantiles = first.quantiles(32);
        for (int index = 1; index < quantiles.length; index++) {
            assertTrue(quantiles[index] >= quantiles[index - 1]);
        }
    }

    private static double correlationLength(List<DoubleBinaryOperator> samplers) {
        double[][] rows = new double[ROWS][LENGTH + LAGS];
        double sum = 0.0D;
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < LENGTH + LAGS; column++) {
                rows[row][column] = samplers.get(row % samplers.size()).applyAsDouble(row * 281.0D + column, row * 173.0D);
            }
            for (int column = 0; column < LENGTH; column++) {
                sum += rows[row][column];
            }
        }
        double mean = sum / (ROWS * LENGTH);
        double variance = covariance(rows, mean, 0);
        double previous = 1.0D;
        for (int lag = 1; lag <= LAGS; lag++) {
            double correlation = covariance(rows, mean, lag) / variance;
            if (correlation < 0.5D) {
                return lag - 1 + (previous - 0.5D) / (previous - correlation);
            }
            previous = correlation;
        }
        return LAGS;
    }

    private static double covariance(double[][] rows, double mean, int lag) {
        double sum = 0.0D;
        for (double[] row : rows) {
            for (int column = 0; column < LENGTH; column++) {
                sum += (row[column] - mean) * (row[column + lag] - mean);
            }
        }
        return sum / (ROWS * LENGTH);
    }
}
