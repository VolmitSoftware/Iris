package art.arcane.iris.studio.render;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.Test;

import java.util.concurrent.CancellationException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class IrisRendererPerformanceContractTest {
    @Test
    public void cancelledRenderStopsBeforeSamplingTheEngine() {
        Engine engine = mock(Engine.class);
        IrisRenderer renderer = new IrisRenderer(engine);

        assertThrows(
                CancellationException.class,
                () -> renderer.render(0D, 0D, 128D, 128, RenderType.BIOME, () -> true)
        );
        verifyNoInteractions(engine);
    }

    @Test
    public void invalidResolutionIsRejectedBeforeSamplingTheEngine() {
        Engine engine = mock(Engine.class);
        IrisRenderer renderer = new IrisRenderer(engine);

        assertThrows(IllegalArgumentException.class, () -> renderer.render(0D, 0D, 128D, 0, RenderType.BIOME));
        verifyNoInteractions(engine);
    }

    @Test
    public void heightPaletteIsNormalizedClampedAndReadable() {
        int deepWater = IrisRenderer.heightColor(0D, 320D, 64D);
        int shallowWater = IrisRenderer.heightColor(64D, 320D, 64D);
        int lowland = IrisRenderer.heightColor(80D, 320D, 64D);
        int highland = IrisRenderer.heightColor(190D, 320D, 64D);
        int snow = IrisRenderer.heightColor(320D, 320D, 64D);

        assertNotEquals(deepWater, shallowWater);
        assertNotEquals(shallowWater, lowland);
        assertNotEquals(lowland, highland);
        assertNotEquals(highland, snow);
        assertEquals(deepWater, IrisRenderer.heightColor(-500D, 320D, 64D));
        assertEquals(snow, IrisRenderer.heightColor(900D, 320D, 64D));
    }

    @Test
    public void samplingGroupsStayWithinAStreamCacheCell() {
        assertEquals(16, IrisRenderer.sampleGroup(1D, 128));
        assertEquals(4, IrisRenderer.sampleGroup(4D, 128));
        assertEquals(1, IrisRenderer.sampleGroup(32D, 128));
        assertEquals(1, IrisRenderer.sampleGroup(0D, 128));
    }

    @Test
    public void heightRenderSamplesTheExactHeightStream() {
        Engine engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        @SuppressWarnings("unchecked")
        ProceduralStream<Double> exact = mock(ProceduralStream.class);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getHeight()).thenReturn(320);
        when(complex.getHeightStream()).thenReturn(exact);
        when(exact.getDouble(0D, 0D)).thenReturn(96D);

        new IrisRenderer(engine).render(0D, 0D, 1D, 1, RenderType.HEIGHT);

        verify(exact).getDouble(0D, 0D);
        verify(complex, never()).getNaturalHeightStream();
    }
}
