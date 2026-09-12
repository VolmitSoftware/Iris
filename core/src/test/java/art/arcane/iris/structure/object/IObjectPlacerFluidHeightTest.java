package art.arcane.iris.structure.object;

import art.arcane.iris.generation.terrain.IrisDimension;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IObjectPlacerFluidHeightTest {
    @Test
    public void missingEngineOrComplexFallsBackToThePlacersScalarHead() {
        IObjectPlacer withoutEngine = mock(IObjectPlacer.class, CALLS_REAL_METHODS);
        when(withoutEngine.getFluidHeight()).thenReturn(63);
        when(withoutEngine.getEngine()).thenReturn(null);

        assertEquals(63, withoutEngine.getFluidHeight(12, -7));

        IObjectPlacer withoutComplex = mock(IObjectPlacer.class, CALLS_REAL_METHODS);
        Engine engine = mock(Engine.class);
        when(withoutComplex.getFluidHeight()).thenReturn(63);
        when(withoutComplex.getEngine()).thenReturn(engine);
        when(engine.getComplex()).thenReturn(null);

        assertEquals(63, withoutComplex.getFluidHeight(12, -7));
    }

    @Test
    public void engineLocalPlacersUseTheRoundedPerColumnRiverHead() {
        IObjectPlacer placer = placer(127, 127, 130.6D);

        assertEquals(131, placer.getFluidHeight(12, -7));

        verify(riverHead(placer)).get(12, -7);
    }

    @Test
    public void worldCoordinatePlacersShiftTheLocalRiverHeadExactlyOnce() {
        IObjectPlacer placer = placer(63, 127, 130.6D);

        assertEquals(67, placer.getFluidHeight(12, -7));

        verify(riverHead(placer)).get(12, -7);
    }

    private static IObjectPlacer placer(int placerFluidHeight, int dimensionFluidHeight, double riverFluidHeight) {
        IObjectPlacer placer = mock(IObjectPlacer.class, CALLS_REAL_METHODS);
        Engine engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        IrisDimension dimension = mock(IrisDimension.class);
        @SuppressWarnings("unchecked")
        ProceduralStream<Double> riverHead = mock(ProceduralStream.class);

        when(placer.getFluidHeight()).thenReturn(placerFluidHeight);
        when(placer.getEngine()).thenReturn(engine);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getDimension()).thenReturn(dimension);
        when(dimension.getFluidHeight()).thenReturn(dimensionFluidHeight);
        when(complex.getRiverWaterSurfaceStream()).thenReturn(riverHead);
        when(riverHead.get(12, -7)).thenReturn(riverFluidHeight);
        return placer;
    }

    private static ProceduralStream<Double> riverHead(IObjectPlacer placer) {
        return placer.getEngine().getComplex().getRiverWaterSurfaceStream();
    }
}
