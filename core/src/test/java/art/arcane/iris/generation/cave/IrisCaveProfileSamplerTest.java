package art.arcane.iris.generation.cave;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.generation.noise.IrisStyledRange;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisCaveProfileSamplerTest {
    @Test
    public void resolvesThresholdNoiseOnceWhilePreservingCoordinateDecisions() {
        IrisGeneratorStyle style = mock(IrisGeneratorStyle.class);
        CNG thresholdNoise = mock(CNG.class);
        when(style.create(any(), isNull())).thenReturn(thresholdNoise);
        when(thresholdNoise.fitDouble(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenAnswer(invocation -> (double) invocation.getArgument(2) * 0.003D
                        + (double) invocation.getArgument(3) * 0.005D);
        IrisCaveProfile profile = profile(new IrisStyledRange().setMin(-0.4D).setMax(0.7D).setStyle(style));
        IrisCaveProfileSampler sampler = new IrisCaveProfileSampler(engine(), profile);
        for (int x = -19; x <= 19; x++) {
            for (int y = -3; y <= 11; y++) {
                int z = -x;
                double density = density(x, y, z);
                double threshold = x * 0.003D + z * 0.005D - profile.getThresholdBias();
                threshold += (1D - 0.75D) * 0.2D;
                assertEquals(density <= threshold, sampler.shouldCarve(x, y, z, 0.75D));
            }
        }
        verify(style, times(1)).create(any(), isNull());
        verify(thresholdNoise, times(585)).fitDouble(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    public void realSeededThresholdMatchesStyledRangeAtSignedCoordinates() {
        IrisStyledRange range = new IrisStyledRange().setMin(-0.4D).setMax(0.7D)
                .setStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX));
        IrisCaveProfile profile = profile(range);
        IrisCaveProfileSampler sampler = new IrisCaveProfileSampler(engine(), profile);
        RNG thresholdRng = new RNG(47L).nextParallelRNG(489_112);
        int[] coordinates = {-1009, -33, -1, 0, 17, 513};
        double[] floatingThresholds = {-0.5D, 0D, 0.25D, 0.75D, 1D, 1.5D};
        for (int x : coordinates) {
            for (int z : coordinates) {
                for (int y = -60; y <= 60; y += 3) {
                    for (double floatingThreshold : floatingThresholds) {
                        double threshold = range.get(thresholdRng, x, z, null) - profile.getThresholdBias();
                        threshold += (1D - Math.max(0D, Math.min(1D, floatingThreshold))) * 0.2D;
                        assertEquals("x=" + x + " y=" + y + " z=" + z + " floating=" + floatingThreshold,
                                density(x, y, z) <= threshold, sampler.shouldCarve(x, y, z, floatingThreshold));
                    }
                }
            }
        }
    }

    @Test
    public void constantAndFlatThresholdsNeverResolveNoise() {
        for (boolean equalBounds : new boolean[]{false, true}) {
            IrisGeneratorStyle style = mock(IrisGeneratorStyle.class);
            when(style.isFlat()).thenReturn(true);
            IrisStyledRange range = new IrisStyledRange().setMin(-0.4D)
                    .setMax(equalBounds ? -0.4D : 0.7D).setStyle(style);
            IrisCaveProfile profile = profile(range);
            IrisCaveProfileSampler sampler = new IrisCaveProfileSampler(engine(), profile);
            double threshold = (equalBounds ? -0.4D : M.lerp(-0.4D, 0.7D, 0.5D)) - profile.getThresholdBias();
            for (int y = -60; y <= 60; y++) {
                assertEquals(density(-17, y, 31) <= threshold, sampler.shouldCarve(-17, y, 31, 1D));
            }
            verify(style, never()).create(any(), any());
        }
    }

    private Engine engine() {
        Engine engine = mock(Engine.class);
        SeedManager seeds = mock(SeedManager.class);
        when(seeds.getCarve()).thenReturn(47L);
        when(engine.getSeedManager()).thenReturn(seeds);
        return engine;
    }

    private IrisCaveProfile profile(IrisStyledRange threshold) {
        CNG densityNoise = mock(CNG.class);
        when(densityNoise.noiseFastSigned3D(anyDouble(), anyDouble(), anyDouble())).thenAnswer(invocation ->
                density(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        IrisGeneratorStyle densityStyle = mock(IrisGeneratorStyle.class);
        when(densityStyle.create(any(), isNull())).thenReturn(densityNoise);
        IrisCaveProfile profile = mock(IrisCaveProfile.class);
        when(profile.getBaseDensityStyle()).thenReturn(densityStyle);
        when(profile.getDetailDensityStyle()).thenReturn(densityStyle);
        when(profile.getWarpStyle()).thenReturn(densityStyle);
        when(profile.getDensityThreshold()).thenReturn(threshold);
        when(profile.getThresholdBias()).thenReturn(0.03D);
        when(profile.getBaseWeight()).thenReturn(1D);
        when(profile.getModules()).thenReturn(new KList<>());
        when(profile.isEnabled()).thenReturn(true);
        return profile;
    }

    private double density(double x, double y, double z) {
        return x * 0.003D + y * 0.01D + z * 0.005D;
    }
}
