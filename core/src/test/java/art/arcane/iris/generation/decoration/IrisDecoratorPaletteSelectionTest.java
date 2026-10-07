package art.arcane.iris.generation.decoration;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class IrisDecoratorPaletteSelectionTest {
    @Test
    public void multipleTopEntriesVaryEvenWhenTheBodyPaletteIsASingleBlock() {
        IrisData data = mock(IrisData.class);
        IrisBiome biome = mock(IrisBiome.class);
        IrisDecorator decorator = spy(new IrisDecorator().setChance(1D));
        NativeBlockState body = mock(NativeBlockState.class);
        NativeBlockState first = mock(NativeBlockState.class);
        NativeBlockState second = mock(NativeBlockState.class);
        KList<NativeBlockState> tops = new KList<>(first, second);
        CNG chance = mock(CNG.class);
        CNG variance = mock(CNG.class);
        RNG rng = new RNG(37L);
        doReturn(new KList<>(body)).when(decorator).getBlockData(data);
        doReturn(tops).when(decorator).getBlockDataTops(data);
        doReturn(chance).when(decorator).getGenerator(rng, data);
        doReturn(variance).when(decorator).getVarianceGenerator(rng, data);
        doReturn(second).when(variance).fit(tops, 23D, 9D, 17D);
        doReturn(first).when(variance).fit(tops, 24D, 9D, 17D);

        assertSame(second, decorator.getBlockDataForTop(biome, rng, 17D, 9D, 23D, data));
        assertSame(first, decorator.getBlockDataForTop(biome, rng, 17D, 9D, 24D, data));
        verify(variance).fit(tops, 23D, 9D, 17D);
        verify(variance).fit(tops, 24D, 9D, 17D);
    }

    @Test
    public void aSingleTopEntryDoesNotBuildOrSampleTheVarianceGenerator() {
        IrisData data = mock(IrisData.class);
        IrisBiome biome = mock(IrisBiome.class);
        IrisDecorator decorator = spy(new IrisDecorator().setChance(1D));
        NativeBlockState firstBody = mock(NativeBlockState.class);
        NativeBlockState secondBody = mock(NativeBlockState.class);
        NativeBlockState top = mock(NativeBlockState.class);
        RNG rng = new RNG(37L);
        doReturn(new KList<>(firstBody, secondBody)).when(decorator).getBlockData(data);
        doReturn(new KList<>(top)).when(decorator).getBlockDataTops(data);
        doReturn(mock(CNG.class)).when(decorator).getGenerator(rng, data);

        assertSame(top, decorator.getBlockDataForTop(biome, rng, 17D, 9D, 23D, data));
        verify(decorator, never()).getVarianceGenerator(rng, data);
        verify(decorator, times(1)).getBlockDataTops(data);
    }

    @Test
    public void bodySamplingResolvesPaletteOnceAndPreservesCoordinatesAndRngConsumption() {
        IrisData data = mock(IrisData.class);
        IrisBiome biome = mock(IrisBiome.class);
        IrisDecorator decorator = spy(new IrisDecorator());
        NativeBlockState first = mock(NativeBlockState.class);
        NativeBlockState second = mock(NativeBlockState.class);
        KList<NativeBlockState> palette = new KList<>(first, second);
        CNG variance = new CNG(new RNG(841L));
        RNG rng = new RNG(37L);
        RNG control = new RNG(37L);
        doReturn(palette).when(decorator).getBlockData(data);
        doReturn(variance).when(decorator).getVarianceGenerator(rng, data);

        for (int x = -8; x < 8; x++) {
            for (int y = 0; y < 4; y++) {
                double z = x * 3D - 2D;
                NativeBlockState expected = variance.fit(palette, z, y, x);
                assertSame(expected, decorator.getBlockData100(biome, rng, x, y, z, data));
            }
        }
        verify(decorator, times(64)).getBlockData(data);
        assertEquals(control.nextLong(), rng.nextLong());
    }
}
