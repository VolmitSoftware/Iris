package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.noise.OffsetNoiseGenerator;
import art.arcane.volmlib.util.noise.SimplexNoise;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.Test;

import java.util.SplittableRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

public class IrisComplexShoreBandTest {
    private static final double[] SHORE_EXTREMES = {0D, 1.2D, 3.2D, 2.5D, 7.75D, -1.5D, 4D, 0.0001D, 63.9D};

    @Test
    public void shoreHeightGeneratorIsAnIdentityPostSimplexSignature() {
        for (int sample = 0; sample < 8; sample++) {
            CNG generator = region(sample).getShoreHeightGenerator();
            assertTrue(generator.getGenerator() instanceof OffsetNoiseGenerator);
            assertTrue(((OffsetNoiseGenerator) generator.getGenerator()).getBase() instanceof SimplexNoise);
            assertEquals(1D, generator.getOpacity(), 0D);
            assertTrue(generator.getChildren() == null || generator.getChildren().isEmpty());
            assertEquals(1D, generator.getPower(), 0D);
            assertEquals(0D, generator.getUp(), 0D);
            assertEquals(0D, generator.getDown(), 0D);
            assertEquals(1D, generator.getPatch(), 0D);
            assertNull(generator.getCache());
            assertNull(generator.getCustomGenerator());
        }
    }

    @Test
    public void simplexAmplitudeBoundFitsInsideTheShoreNoiseRange() {
        double kernelPeak = 0D;
        for (int step = 0; step <= 2_000_000; step++) {
            double radius = Math.sqrt(0.5D) * step / 2_000_000D;
            double falloff = 0.5D - radius * radius;
            kernelPeak = Math.max(kernelPeak, falloff * falloff * falloff * falloff * Math.sqrt(2D) * radius);
        }
        assertEquals(256D / 19683D, kernelPeak, 1e-9D);
        double amplitude = 50D * 3D * 256D / 19683D;
        assertTrue(0.5D + amplitude / 2D < IrisComplex.SHORE_NOISE_HIGHEST);
        assertTrue(0.5D - amplitude / 2D > IrisComplex.SHORE_NOISE_LOWEST);
    }

    @Test
    public void shoreHeightNoiseStaysInsideTheProvenRange() {
        SplittableRandom random = new SplittableRandom(1337L);
        double lowest = Double.POSITIVE_INFINITY;
        double highest = Double.NEGATIVE_INFINITY;
        for (int sample = 0; sample < 4; sample++) {
            CNG generator = region(sample).getShoreHeightGenerator();
            for (int point = 0; point < 250_000; point++) {
                double scale = point % 2 == 0 ? 1D : 64D;
                double noise = generator.noise(random.nextDouble(-1e6D, 1e6D) / scale, random.nextDouble(-1e6D, 1e6D) / scale);
                lowest = Math.min(lowest, noise);
                highest = Math.max(highest, noise);
            }
        }
        assertTrue("lowest " + lowest, lowest >= IrisComplex.SHORE_NOISE_LOWEST);
        assertTrue("highest " + highest, highest <= IrisComplex.SHORE_NOISE_HIGHEST);
    }

    @Test
    public void lazyShoreBandMatchesTheEagerBandTop() {
        SplittableRandom random = new SplittableRandom(42L);
        ProceduralStream<Double> slopes = ProceduralStream.ofDouble((x, z) -> 2D);
        for (int sample = 0; sample < 400_000; sample++) {
            IrisRegion region = region(random.nextInt(64))
                    .setShoreHeightMin(SHORE_EXTREMES[random.nextInt(SHORE_EXTREMES.length)])
                    .setShoreHeightMax(SHORE_EXTREMES[random.nextInt(SHORE_EXTREMES.length)])
                    .setShoreHeightZoom(random.nextBoolean() ? 3.14D : 0.15D)
                    .setShoreMinimumWidth(random.nextInt(8) == 0 ? 2D : 0D);
            double fluid = random.nextInt(4) == 0 ? 63D : random.nextInt(-64, 200);
            double height = random.nextInt(3) == 0
                    ? fluid + random.nextDouble(-3D, 9D)
                    : random.nextInt(-64, 320) + (random.nextBoolean() ? 0D : random.nextDouble());
            double x = random.nextInt(-30_000_000, 30_000_000);
            double z = random.nextInt(-30_000_000, 30_000_000);
            boolean eager = height > IrisComplex.shoreBandTop(height, region, x, z, fluid, slopes);
            assertEquals(eager, IrisComplex.aboveShoreBand(height, region, x, z, fluid, slopes));
        }
    }

    @Test
    public void lazySurfaceBiomeMatchesTheEagerSelection() {
        SplittableRandom random = new SplittableRandom(7L);
        IrisBiome land = mock(IrisBiome.class, withSettings().stubOnly());
        IrisBiome sea = mock(IrisBiome.class, withSettings().stubOnly());
        IrisBiome shore = mock(IrisBiome.class, withSettings().stubOnly());
        IrisBiome[] bases = new IrisBiome[8];
        for (int flags = 0; flags < bases.length; flags++) {
            IrisBiome base = mock(IrisBiome.class, withSettings().stubOnly());
            doReturn((flags & 1) != 0).when(base).isShore();
            doReturn((flags & 2) != 0).when(base).isLand();
            doReturn((flags & 4) != 0).when(base).isAquatic();
            bases[flags] = base;
        }
        ProceduralStream<IrisBiome> lands = constant(land);
        ProceduralStream<IrisBiome> seas = constant(sea);
        ProceduralStream<IrisBiome> shores = constant(shore);
        ProceduralStream<Double> slopes = ProceduralStream.ofDouble((x, z) -> 4D);
        for (int sample = 0; sample < 200_000; sample++) {
            IrisRegion region = region(random.nextInt(16))
                    .setShoreHeightMin(SHORE_EXTREMES[random.nextInt(SHORE_EXTREMES.length)])
                    .setShoreHeightMax(SHORE_EXTREMES[random.nextInt(SHORE_EXTREMES.length)])
                    .setShoreMinimumWidth(random.nextInt(8) == 0 ? 3D : 0D);
            double fluid = 63D;
            double height = random.nextBoolean() ? fluid + random.nextInt(-3, 8) : random.nextInt(-64, 320);
            double x = random.nextInt(-100_000, 100_000);
            double z = random.nextInt(-100_000, 100_000);
            IrisBiome base = bases[random.nextInt(bases.length)];
            IrisBiome expected = eagerSurfaceBiome(height, base, region, x, z, fluid, lands, seas, shores, slopes);
            assertSame(expected, IrisComplex.resolveSurfaceBiome(height, base, region, x, z, fluid, lands, seas, shores, slopes));
        }
    }

    @Test
    public void columnsOutsideTheShoreWindowNeverSampleTheShoreNoise() {
        IrisRegion region = mock(IrisRegion.class);
        doReturn(1.2D).when(region).getShoreHeightMin();
        doReturn(3.2D).when(region).getShoreHeightMax();
        doReturn(0D).when(region).getShoreMinimumWidth();
        doThrow(new AssertionError("The shore noise was sampled for a column outside the shore window"))
                .when(region).getShoreHeight(anyDouble(), anyDouble());
        ProceduralStream<Double> slopes = ProceduralStream.ofDouble((x, z) -> 0D);

        assertTrue(IrisComplex.aboveShoreBand(68D, region, 3D, 4D, 63D, slopes));
        assertTrue(IrisComplex.aboveShoreBand(250D, region, 3D, 4D, 63D, slopes));
        assertFalse(IrisComplex.aboveShoreBand(63.2D, region, 3D, 4D, 63D, slopes));
        assertFalse(IrisComplex.aboveShoreBand(-20D, region, 3D, 4D, 63D, slopes));
    }

    @Test
    public void equalShoreExtremesNeverSampleTheShoreNoise() {
        IrisRegion region = mock(IrisRegion.class);
        doReturn(2D).when(region).getShoreHeightMin();
        doReturn(2D).when(region).getShoreHeightMax();
        doReturn(0D).when(region).getShoreMinimumWidth();
        doThrow(new AssertionError("Equal shore extremes need no shore noise"))
                .when(region).getShoreHeight(anyDouble(), anyDouble());
        ProceduralStream<Double> slopes = ProceduralStream.ofDouble((x, z) -> 0D);

        assertFalse(IrisComplex.aboveShoreBand(65D, region, 3D, 4D, 63D, slopes));
        assertTrue(IrisComplex.aboveShoreBand(65.0001D, region, 3D, 4D, 63D, slopes));
    }

    private static IrisRegion region(int variant) {
        IrisRegion region = new IrisRegion();
        region.setName("Shore Region " + variant);
        region.setLandBiomeZoom(1D + variant * 0.25D);
        KList<String> lands = new KList<>();
        for (int index = 0; index < variant % 5; index++) {
            lands.add("land-" + index);
        }
        region.setLandBiomes(lands);
        return region;
    }

    private static IrisBiome eagerSurfaceBiome(
            double height,
            IrisBiome biome,
            IrisRegion region,
            double x,
            double z,
            double fluidHeight,
            ProceduralStream<IrisBiome> landBiomes,
            ProceduralStream<IrisBiome> seaBiomes,
            ProceduralStream<IrisBiome> shoreBiomes,
            ProceduralStream<Double> shoreSlopes
    ) {
        double shoreTop = IrisComplex.shoreBandTop(height, region, x, z, fluidHeight, shoreSlopes);
        if (height >= fluidHeight - 1 && height <= shoreTop && !biome.isShore()) {
            return shoreBiomes.get(x, z);
        }
        if (height > shoreTop && !biome.isLand()) {
            return landBiomes.get(x, z);
        }
        if (height < fluidHeight && !biome.isAquatic()) {
            return seaBiomes.get(x, z);
        }
        if (height == fluidHeight && !biome.isShore()) {
            return shoreBiomes.get(x, z);
        }
        return biome;
    }

    private static ProceduralStream<IrisBiome> constant(IrisBiome biome) {
        @SuppressWarnings("unchecked")
        ProceduralStream<IrisBiome> stream = mock(ProceduralStream.class, withSettings().stubOnly());
        doReturn(biome).when(stream).get(anyDouble(), anyDouble());
        return stream;
    }
}
