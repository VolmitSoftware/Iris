package art.arcane.iris.engine.terrain;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.util.project.stream.ProceduralStream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class Terrain3DSurfaceSlopeTest {
    @Test
    public void flatLowerLedgeIgnoresTheVaryingUpperCap() {
        IrisComplex complex = complex();
        when(complex.terrainColumn(0, 0)).thenReturn(column(60, 100, 120));
        when(complex.terrainColumn(3, 0)).thenReturn(column(60, 150, 200));
        when(complex.terrainColumn(0, 3)).thenReturn(column(60, 90, 100));

        assertEquals(0D, complex.terrainSurfaceSlope(0, 60, 0), 0D);
    }

    @Test
    public void lowerLedgeUsesTheExistingThreeBlockSlopeMetric() {
        IrisComplex complex = complex();
        when(complex.terrainColumn(0, 0)).thenReturn(column(60, 100, 120));
        when(complex.terrainColumn(3, 0)).thenReturn(column(63, 100, 120));
        when(complex.terrainColumn(0, 3)).thenReturn(column(64, 100, 120));

        assertEquals(5D, complex.terrainSurfaceSlope(0, 60, 0), 0D);
    }

    @Test
    public void neighboringContinuousRiverTerrainUsesItsPlacementHeight() {
        IrisComplex complex = complex();
        when(complex.terrainColumn(0, 0)).thenReturn(column(60, 100, 120));
        when(complex.getPlacementHeightStream()).thenReturn(
                ProceduralStream.ofDouble((x, z) -> x == 3D ? 63D : 64D));

        assertEquals(5D, complex.terrainSurfaceSlope(0, 60, 0), 0D);
    }

    @Test
    public void topSurfaceAndCaveInteriorsKeepTheExistingSlope() {
        IrisComplex complex = complex();
        when(complex.terrainColumn(0, 0)).thenReturn(column(60, 100, 120));
        when(complex.getSlopeStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> 7.25D));

        assertEquals(7.25D, complex.terrainSurfaceSlope(0, 120, 0), 0D);
        assertEquals(7.25D, complex.terrainSurfaceSlope(0, 50, 0), 0D);
        verify(complex, never()).terrainColumn(3, 0);
        verify(complex, never()).terrainColumn(0, 3);
    }

    @Test
    public void unshapedTerrainKeepsTheExistingSlope() {
        IrisComplex complex = complex();
        when(complex.getSlopeStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> 3.5D));

        assertEquals(3.5D, complex.terrainSurfaceSlope(0, 60, 0), 0D);
        verify(complex, never()).terrainColumn(3, 0);
        verify(complex, never()).terrainColumn(0, 3);
    }

    @Test
    public void nearestSurfaceUsesTheLowerFloorOnAnEqualDistance() {
        Terrain3DColumn column = column(60, 80, 100);

        assertEquals(60, column.nearestSurfaceY(80));
        assertEquals(100, column.nearestSurfaceY(81));
        assertEquals(60, column.nearestSurfaceY(Integer.MIN_VALUE));
        assertEquals(100, column.nearestSurfaceY(Integer.MAX_VALUE));
    }

    private static IrisComplex complex() {
        IrisComplex complex = mock(IrisComplex.class);
        doCallRealMethod().when(complex).terrainSurfaceSlope(anyInt(), anyInt(), anyInt());
        return complex;
    }

    private static Terrain3DColumn column(int floor, int ceiling, int top) {
        return new Terrain3DColumn(floor, floor, true, new int[]{0, floor, ceiling, top});
    }
}
