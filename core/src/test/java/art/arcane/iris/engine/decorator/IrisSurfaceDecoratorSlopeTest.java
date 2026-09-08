package art.arcane.iris.engine.decorator;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.object.IrisDecorator;
import art.arcane.iris.engine.object.IrisSlopeClip;
import art.arcane.iris.util.project.stream.ProceduralStream;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisSurfaceDecoratorSlopeTest {
    @Test
    public void lowerLedgeUsesItsOwnSlopeForVegetation() {
        IrisComplex complex = mock(IrisComplex.class);
        IrisSurfaceDecorator surface = mock(IrisSurfaceDecorator.class, CALLS_REAL_METHODS);
        doReturn(complex).when(surface).getComplex();
        IrisDecorator vegetation = new IrisDecorator().setSlopeCondition(new IrisSlopeClip(0D, 2.6D));
        when(complex.hasTerrain3D()).thenReturn(true);
        when(complex.terrainSurfaceSlope(12, 32, -8)).thenReturn(0D);
        when(complex.terrainSurfaceSlope(12, 96, -8)).thenReturn(5D);

        assertTrue(surface.isSlopeValid(vegetation, 12, 32, -8));
        assertFalse(surface.isSlopeValid(vegetation, 12, 96, -8));
    }

    @Test
    public void disabledTerrainKeepsItsExistingSlopeGate() {
        IrisComplex complex = mock(IrisComplex.class);
        IrisSurfaceDecorator surface = mock(IrisSurfaceDecorator.class, CALLS_REAL_METHODS);
        doReturn(complex).when(surface).getComplex();
        IrisDecorator vegetation = new IrisDecorator().setSlopeCondition(new IrisSlopeClip(0D, 2.6D));
        when(complex.getSlopeStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> 5D));

        assertFalse(surface.isSlopeValid(vegetation, 12, 32, -8));
        vegetation.setForcePlace(true);
        assertTrue(surface.isSlopeValid(vegetation, 12, 32, -8));
    }
}
