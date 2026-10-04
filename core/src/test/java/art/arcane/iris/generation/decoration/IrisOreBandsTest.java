package art.arcane.iris.generation.decoration;

import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.KeyedBlockState;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class IrisOreBandsTest {
    private static final RNG RNG_SOURCE = new RNG(77L);

    @Test
    public void bandsResolveTheSameOreAsScanningEveryGeneratorInOrder() {
        Random random = new Random(1337L);
        for (int trial = 0; trial < 200; trial++) {
            KList<IrisOreGenerator> ores = new KList<>();
            int count = 1 + random.nextInt(12);
            for (int index = 0; index < count; index++) {
                double min = random.nextInt(90) - 10 + (random.nextBoolean() ? 0D : random.nextDouble());
                double max = min + random.nextInt(40) + (random.nextBoolean() ? 0D : random.nextDouble());
                ores.add(generator(min, max, 0.2D + random.nextDouble() * 0.8D, 31L * trial + index));
            }
            IrisOreBands bands = IrisOreBands.of(ores);
            double lowest = Double.POSITIVE_INFINITY;
            double highest = Double.NEGATIVE_INFINITY;
            for (IrisOreGenerator ore : ores) {
                lowest = Math.min(lowest, ore.getRange().getMin());
                highest = Math.max(highest, ore.getRange().getMax());
            }
            for (int y = -20; y <= 140; y++) {
                assertEquals("trial " + trial + " y " + y, y >= lowest && y <= highest, bands.contains(y));
                if (!bands.contains(y)) {
                    continue;
                }
                for (int x = -3; x <= 3; x++) {
                    assertSame("trial " + trial + " y " + y + " x " + x,
                            scan(ores, x, y, 5 - x), bands.generate(x, y, 5 - x, RNG_SOURCE, null));
                }
            }
        }
    }

    @Test
    public void emptyAndUnboundedListsHaveNoBands() {
        assertFalse(IrisOreBands.of(null).hasOres());
        assertFalse(IrisOreBands.of(new KList<>()).contains(0));
        KList<IrisOreGenerator> inverted = new KList<>();
        inverted.add(generator(40D, 10D, 1D, 3L));
        assertFalse(IrisOreBands.of(inverted).contains(20));
    }

    @Test
    public void constantChanceMatchesSamplingTheFlatField() {
        IrisOreGenerator flat = new IrisOreGenerator();
        NativeBlockState ore = new KeyedBlockState("minecraft:iron_ore");
        IrisMaterialPalette palette = new FixedPalette(ore);
        IrisGeneratorStyle style = new IrisGeneratorStyle(NoiseStyle.FLAT).setZoom(2.5D).setExponent(1.3D)
                .setFracture(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).setMultiplier(9D));
        flat.setPalette(palette).setChanceStyle(style).setRange(new IrisRange(0, 100));
        CNG reference = style.create(RNG_SOURCE, null);
        for (double threshold : new double[]{0.1D, 0.5D, 0.99D, 1D}) {
            flat.setThreshold(threshold);
            for (int index = 0; index < 100; index++) {
                int x = index * 37 - 900;
                int y = index % 101;
                int z = 400 - index * 11;
                NativeBlockState expected = reference.noise(x, y, z) > threshold ? null : ore;
                assertSame(expected, flat.generate(x, y, z, RNG_SOURCE, null));
            }
        }
    }

    private static NativeBlockState scan(KList<IrisOreGenerator> ores, int x, int y, int z) {
        for (IrisOreGenerator ore : ores) {
            NativeBlockState state = ore.generate(x, y, z, RNG_SOURCE, null);
            if (state != null) {
                return state;
            }
        }
        return null;
    }

    private static IrisOreGenerator generator(double min, double max, double threshold, long seed) {
        IrisOreGenerator generator = new IrisOreGenerator();
        NativeBlockState state = new KeyedBlockState("minecraft:iron_ore");
        IrisMaterialPalette palette = new FixedPalette(state);
        IrisGeneratorStyle style = new FixedChanceStyle(new CoordinateChance(seed));
        return generator.setPalette(palette).setChanceStyle(style).setThreshold(threshold)
                .setRange(new IrisRange(min, max));
    }

    @Test
    public void generatorsWithDisjointRangesDoNotShadowEachOther() {
        KList<IrisOreGenerator> ores = new KList<>();
        ores.add(generator(0D, 0D, 1D, 1L));
        ores.add(generator(1D, 57D, 1D, 2L));
        ores.add(generator(58D, 58D, 1D, 3L));
        IrisOreBands bands = IrisOreBands.of(ores);
        assertSame(scan(ores, 0, 0, 0), bands.generate(0, 0, 0, RNG_SOURCE, null));
        assertSame(scan(ores, 0, 30, 0), bands.generate(0, 30, 0, RNG_SOURCE, null));
        assertSame(scan(ores, 0, 58, 0), bands.generate(0, 58, 0, RNG_SOURCE, null));
        assertNull(bands.contains(59) ? bands.generate(0, 59, 0, RNG_SOURCE, null) : null);
    }
    private static final class FixedPalette extends IrisMaterialPalette {
        private final NativeBlockState state;

        private FixedPalette(NativeBlockState state) {
            this.state = state;
            setPalette(new KList<>(new IrisBlockData()));
        }

        @Override
        public NativeBlockState get(RNG rng, double x, double y, double z, IrisData data) {
            return state;
        }
    }

    private static final class FixedChanceStyle extends IrisGeneratorStyle {
        private final CNG chance;

        private FixedChanceStyle(CNG chance) {
            this.chance = chance;
        }

        @Override
        public CNG create(RNG rng, IrisData data) {
            return chance;
        }
    }

    private static final class CoordinateChance extends CNG {
        private final long seed;

        private CoordinateChance(long seed) {
            super(new RNG(seed));
            this.seed = seed;
        }

        @Override
        public double noise(double x, double y, double z) {
            long hash = seed;
            hash = hash * 31 + Double.doubleToLongBits(x);
            hash = hash * 31 + Double.doubleToLongBits(y);
            hash = hash * 31 + Double.doubleToLongBits(z);
            return new Random(hash).nextDouble();
        }
    }

}
