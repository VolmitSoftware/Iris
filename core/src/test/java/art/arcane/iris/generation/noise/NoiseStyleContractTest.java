package art.arcane.iris.generation.noise;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.noise.NoiseGenerator;
import art.arcane.volmlib.util.noise.NoiseType;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class NoiseStyleContractTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void builtInStylesAreFiniteAndRepeatableAcrossWorldCoordinates() {
        double[] coordinates = {-29_999_983.25D, -513D, -64D, -1D, 0D, 0.125D, 17D, 64D, 8192D, 29_999_983.25D};
        for (NoiseStyle style : NoiseStyle.values()) {
            CNG first = style.create(new RNG(813753L));
            CNG second = style.create(new RNG(813753L));
            for (double coordinate : coordinates) {
                double x = coordinate;
                double z = coordinate * -0.713D;
                double[] samples = {first.noise(x), first.noise(x, z), first.noise(x, 81.25D, z)};
                for (double sample : samples) {
                    assertTrue(style + " is non-finite at " + coordinate, Double.isFinite(sample));
                }
                assertEquals(style.name(), samples[0], second.noise(x), 0D);
                assertEquals(style.name(), samples[1], second.noise(x, z), 0D);
                assertEquals(style.name(), samples[2], second.noise(x, 81.25D, z), 0D);
            }
        }
    }

    @Test
    public void classicPresetsShareOneBaseScale() {
        NoiseStyle[] styles = {NoiseStyle.SIMPLEX, NoiseStyle.PERLIN, NoiseStyle.CUBIC,
                NoiseStyle.FRACTAL_CUBIC, NoiseStyle.BIOCTAVE_SIMPLEX, NoiseStyle.VIGOCTAVE_SIMPLEX,
                NoiseStyle.BIOCTAVE_FRACTAL_CUBIC, NoiseStyle.QUADOCTAVE_FRACTAL_CUBIC};
        for (NoiseStyle style : styles) {
            CNG generator = style.create(new RNG(7751L));
            NoiseGenerator source = generator.getGenerator();
            double scale = 100D / 64D;
            assertEquals(style.name(), source.noise(27.5D * scale, -83.25D * scale),
                    generator.noise(27.5D, -83.25D), 0D);
        }
        assertEquals(2D / 64D, NoiseType.HEXAGON.getCoordinateScale(), 0D);
        assertEquals(8D / 64D, NoiseType.SIERPINSKI_TRIANGLE.getCoordinateScale(), 0D);
    }

    @Test
    public void explicitNativeGeneratorFrequenciesKeepTheirUnits() {
        CNG generator = new CNG(new RNG(7231L), NoiseType.SIMPLEX, 1D, 1).scale(0.375D);
        assertEquals(generator.getGenerator().noise(28D * 0.375D, -74D * 0.375D),
                generator.noise(28D, -74D), 0D);
    }

    @Test
    public void explicitZoomDoublesFeatureSize() {
        for (NoiseStyle style : NoiseStyle.values()) {
            if (style == NoiseStyle.STATIC) {
                continue;
            }
            CNG normal = style.style().createNoCache(new RNG(45231L), null);
            CNG enlarged = style.style().zoomed(2D).createNoCache(new RNG(45231L), null);
            assertEquals(style.name(), normal.noise(13.25D, -17.75D),
                    enlarged.noise(26.5D, -35.5D), 0.000000001D);
            assertEquals(style.name(), normal.noise(13.25D), enlarged.noise(26.5D), 0.000000001D);
            assertEquals(style.name(), normal.noise(13.25D, 41.125D, -17.75D),
                    enlarged.noise(26.5D, 82.25D, -35.5D), 0.000000001D);
        }
    }

    @Test
    public void defaultSettingsPreserveEveryPreset() {
        for (NoiseStyle style : NoiseStyle.values()) {
            CNG direct = style.create(new RNG(7253L));
            CNG configured = style.style().createNoCache(new RNG(7253L), null);
            for (int index = 0; index < 32; index++) {
                double x = index * 19.713D - 400D;
                double z = index * -31.579D + 100D;
                assertEquals(style.name(), direct.noise(x, z), configured.noise(x, z), 0D);
                assertEquals(style.name(), direct.noise(x, 32.75D, z), configured.noise(x, 32.75D, z), 0D);
            }
        }
    }

    @Test
    public void builtInStylesStayInsideTheirUnitRange() {
        for (NoiseStyle style : NoiseStyle.values()) {
            for (long seed : new long[]{0L, 813753L, Long.MAX_VALUE}) {
                CNG generator = style.create(new RNG(seed));
                for (int index = 0; index < 256; index++) {
                    double x = index * 17.12345D - 2000D;
                    double z = index * -27.98765D + 3000D;
                    double[] values = {generator.noise(x), generator.noise(x, z), generator.noise(x, 53.75D, z)};
                    for (double value : values) {
                        assertTrue(style + " produced " + value, value >= 0D && value <= 1D);
                    }
                }
            }
        }
    }

    @Test
    public void explicitFractureAndCellularizationZoomWithTheirSource() {
        IrisGeneratorStyle[] styles = {
                NoiseStyle.SIMPLEX.style().setFracture(NoiseStyle.CLOVER.style().setMultiplier(50D).setZoom(1.5D)),
                NoiseStyle.IRIS.style().setCellularFrequency(0.7D).setCellularZoom(1.25D)
        };
        for (IrisGeneratorStyle style : styles) {
            CNG normal = style.createNoCache(new RNG(217L), null);
            CNG enlarged = style.setZoom(2D).createNoCache(new RNG(217L), null);
            assertEquals(normal.noise(19.25D, -71.75D), enlarged.noise(38.5D, -143.5D), 0.000000001D);
        }
    }

    @Test
    public void configuredExponentCompoundsThePresetCurve() {
        CNG base = NoiseStyle.VASCULAR_THIN.style().createNoCache(new RNG(8831L), null);
        CNG squared = NoiseStyle.VASCULAR_THIN.style().setExponent(2D).createNoCache(new RNG(8831L), null);
        double value = base.noise(29.5D, -33.25D);
        assertEquals(value * value, squared.noise(29.5D, -33.25D), 0.000000000001D);
    }

    @Test
    public void octavePresetsKeepTheirBaseSeed() {
        NoiseStyle[][] families = {
                {NoiseStyle.SIMPLEX, NoiseStyle.BIOCTAVE_SIMPLEX},
                {NoiseStyle.FRACTAL_FBM_SIMPLEX, NoiseStyle.BIOCTAVE_FRACTAL_FBM_SIMPLEX},
                {NoiseStyle.FRACTAL_BILLOW_SIMPLEX, NoiseStyle.BIOCTAVE_FRACTAL_BILLOW_SIMPLEX},
                {NoiseStyle.FRACTAL_RM_SIMPLEX, NoiseStyle.BIOCTAVE_FRACTAL_RM_SIMPLEX},
                {NoiseStyle.FRACTAL_BILLOW_PERLIN, NoiseStyle.BIOCTAVE_FRACTAL_BILLOW_PERLIN},
                {NoiseStyle.FRACTAL_CUBIC, NoiseStyle.BIOCTAVE_FRACTAL_CUBIC}
        };
        for (NoiseStyle[] family : families) {
            CNG configured = family[0].create(new RNG(82731L)).oct(2);
            CNG preset = family[1].create(new RNG(82731L));
            for (int index = 0; index < 32; index++) {
                double x = index * 7.125D;
                double z = index * -11.875D;
                assertEquals(family[1].name(), configured.noise(x, z), preset.noise(x, z), 0D);
                assertEquals(family[1].name(), configured.noise(x, 31.25D, z), preset.noise(x, 31.25D, z), 0D);
            }
        }
    }

    @Test
    public void layerOctavesPreserveAndMultiplyPresetDetail() {
        IrisGeneratorStyle style = NoiseStyle.QUADOCTAVE_SIMPLEX.style();
        CNG unchanged = style.createForLayer(new RNG(71L), null, 1);
        CNG detailed = style.createForLayer(new RNG(71L), null, 2);
        assertEquals(4, unchanged.getOct());
        assertEquals(8, detailed.getOct());
        assertEquals(16, style.createForLayer(new RNG(71L), null, Integer.MAX_VALUE).getOct());
        assertNotEquals(unchanged.noise(21D, -31D), detailed.noise(21D, -31D), 0D);
    }

    @Test
    public void cachedLayersRetainTheirOctavesAcrossReload() throws Exception {
        File dataFolder = folder.newFolder("pack");
        IrisData data = mock(IrisData.class);
        when(data.getDataFolder()).thenReturn(dataFolder);
        IrisGeneratorStyle cached = NoiseStyle.BIOCTAVE_SIMPLEX.style().setCacheSize(32);
        CNG first = cached.createForLayer(new RNG(921L), data, 3);
        CNG reloaded = cached.createForLayer(new RNG(921L), data, 3);
        CNG uncached = NoiseStyle.BIOCTAVE_SIMPLEX.style().createForLayer(new RNG(921L), data, 3);
        CNG different = cached.createForLayer(new RNG(921L), data, 1);
        assertEquals(6, first.getOct());
        assertEquals(6, reloaded.getOct());
        assertEquals(uncached.noise(4D, 7D), first.noise(4D, 7D), 0.000001D);
        assertEquals(first.noise(4D, 7D), reloaded.noise(4D, 7D), 0D);
        assertNotEquals(first.noise(4D, 7D), different.noise(4D, 7D), 0D);
        File[] caches = new File(dataFolder, ".cache").listFiles((parent, name) -> name.endsWith(".cnm"));
        assertEquals(2, caches.length);
    }

    @Test
    public void invalidTransformsFailBeforeSampling() {
        double[] invalid = {0D, -1D, Double.NaN, Double.POSITIVE_INFINITY, Double.MIN_VALUE};
        for (double value : invalid) {
            assertThrows(IllegalArgumentException.class,
                    () -> NoiseStyle.SIMPLEX.style().setZoom(value).createNoCache(new RNG(1L), null));
            assertThrows(IllegalArgumentException.class,
                    () -> NoiseStyle.SIMPLEX.style().setExponent(value).createNoCache(new RNG(1L), null));
            assertThrows(IllegalArgumentException.class,
                    () -> NoiseStyle.SIMPLEX.style().setCellularFrequency(1D).setCellularZoom(value)
                            .createNoCache(new RNG(1L), null));
        }
    }
}
