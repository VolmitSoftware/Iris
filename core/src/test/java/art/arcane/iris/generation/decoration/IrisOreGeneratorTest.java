package art.arcane.iris.generation.decoration;

import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class IrisOreGeneratorTest {
    @Test
    public void zeroThresholdDisablesEvenZeroNoiseSamples() {
        Fixture fixture = new Fixture(0.0);
        fixture.generator.setThreshold(0.0);

        assertNull(fixture.generator.generate(2, 40, 3, fixture.rng, null));
        verifyNoInteractions(fixture.noise, fixture.style);
    }

    @Test
    public void positiveThresholdStillIncludesExactCutoffAndVerticalBounds() {
        Fixture fixture = new Fixture(0.5);
        fixture.generator.setThreshold(0.5);

        assertSame(fixture.ore, fixture.generator.generate(2, 30, 3, fixture.rng, null));
        assertSame(fixture.ore, fixture.generator.generate(2, 80, 3, fixture.rng, null));
        assertNull(fixture.generator.generate(2, 29, 3, fixture.rng, null));
        assertNull(fixture.generator.generate(2, 81, 3, fixture.rng, null));
        fixture.generator.setThreshold(0.49);
        assertNull(fixture.generator.generate(2, 40, 3, fixture.rng, null));
    }

    @Test
    public void fullThresholdIncludesMaximumNoise() {
        Fixture fixture = new Fixture(1.0);
        fixture.generator.setThreshold(1.0);

        assertSame(fixture.ore, fixture.generator.generate(2, 40, 3, fixture.rng, null));
    }

    private static final class Fixture {
        private final RNG rng = new RNG(34L);
        private final PlatformBlockState ore = mock(PlatformBlockState.class);
        private final CNG noise = mock(CNG.class);
        private final IrisGeneratorStyle style = mock(IrisGeneratorStyle.class);
        private final IrisOreGenerator generator = new IrisOreGenerator();

        private Fixture(double sample) {
            IrisMaterialPalette palette = mock(IrisMaterialPalette.class);
            when(palette.getPalette()).thenReturn(new KList<>(mock(IrisBlockData.class)));
            when(palette.get(any(), anyDouble(), anyDouble(), anyDouble(), isNull())).thenReturn(ore);
            when(noise.noise(anyDouble(), anyDouble(), anyDouble())).thenReturn(sample);
            when(style.create(any(), isNull())).thenReturn(noise);
            generator.setPalette(palette);
            generator.setChanceStyle(style);
        }
    }
}
