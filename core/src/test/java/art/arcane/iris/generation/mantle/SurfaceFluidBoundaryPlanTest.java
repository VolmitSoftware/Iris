package art.arcane.iris.generation.mantle;

import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SurfaceFluidBoundaryPlanTest {
    private static final int CHUNK_SIZE = 16;
    private static final int FIELD_SIZE = 22;
    private static final int PADDING = 3;
    private static final int FLUID_HEIGHT = 64;

    @Test
    public void submergedColumnProtectsItsSeabedWithoutBlockingTheCaveBelow() {
        Fixture fixture = new Fixture();
        fixture.setChunkSurface(8, 8, 60);

        fixture.resolve();

        int boundaryY = fixture.boundary(8, 8);
        assertEquals(60, boundaryY);
        assertFalse(SurfaceFluidBoundaryPlan.protects(fixture.boundaries, fixture.index(8, 8), 59));
        assertTrue(SurfaceFluidBoundaryPlan.protects(fixture.boundaries, fixture.index(8, 8), 60));
    }

    @Test
    public void dryCoastProtectsOnlyTheAdjacentFluidBand() {
        Fixture fixture = new Fixture();
        fixture.setFieldSurface(7, 8, 60D);

        fixture.resolve();

        int columnIndex = fixture.index(8, 8);
        assertEquals(61, fixture.boundary(8, 8));
        assertFalse(SurfaceFluidBoundaryPlan.protects(fixture.boundaries, columnIndex, 60));
        assertTrue(SurfaceFluidBoundaryPlan.protects(fixture.boundaries, columnIndex, 61));
        assertTrue(SurfaceFluidBoundaryPlan.protects(fixture.boundaries, columnIndex, 64));
        assertFalse(SurfaceFluidBoundaryPlan.protects(fixture.boundaries, columnIndex, 65));
    }

    @Test
    public void lowestCardinalReservoirBoundaryWins() {
        Fixture fixture = new Fixture();
        fixture.setFieldSurface(7, 8, 62D);
        fixture.setFieldSurface(9, 8, 58D);

        fixture.resolve();

        assertEquals(59, fixture.boundary(8, 8));
    }

    @Test
    public void roundedWaterlineAndDiagonalReservoirDoNotOverprotect() {
        Fixture wetRoundedDown = new Fixture();
        wetRoundedDown.setFieldSurface(7, 8, 63.49D);
        wetRoundedDown.resolve();
        assertEquals(64, wetRoundedDown.boundary(8, 8));

        Fixture dryRoundedUp = new Fixture();
        dryRoundedUp.setFieldSurface(7, 8, 63.5D);
        dryRoundedUp.setFieldSurface(7, 7, 40D);
        dryRoundedUp.resolve();
        assertEquals(SurfaceFluidBoundaryPlan.NO_BOUNDARY, dryRoundedUp.boundary(8, 8));
    }

    @Test
    public void nonFluidPaletteDoesNotCreateAReservoirBoundary() {
        Fixture fixture = new Fixture();
        fixture.setFieldSurface(7, 8, 40D);
        fixture.setFieldHasFluid(7, 8, false);

        fixture.resolve();

        assertEquals(SurfaceFluidBoundaryPlan.NO_BOUNDARY, fixture.boundary(8, 8));
    }

    @Test
    public void paddedHaloProtectsEveryChunkEdge() {
        assertEdgeBoundary(0, 8, -1, 8);
        assertEdgeBoundary(15, 8, 16, 8);
        assertEdgeBoundary(8, 0, 8, -1);
        assertEdgeBoundary(8, 15, 8, 16);
    }

    @Test
    public void invalidFieldShapeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SurfaceFluidBoundaryPlan.fill(
                new int[CHUNK_SIZE * CHUNK_SIZE],
                new double[FIELD_SIZE * FIELD_SIZE],
                new boolean[FIELD_SIZE * FIELD_SIZE],
                new double[FIELD_SIZE * FIELD_SIZE],
                FIELD_SIZE,
                0,
                new long[CHUNK_SIZE * CHUNK_SIZE]
        ));
    }

    @Test
    public void terracedReservoirProtectsOnlyToItsLocalHead() {
        Fixture fixture = new Fixture();
        fixture.setFieldSurface(7, 8, 60D);
        fixture.setFieldFluidHeight(7, 8, 67D);

        fixture.resolve();

        int columnIndex = fixture.index(8, 8);
        assertEquals(61, fixture.boundary(8, 8));
        assertEquals(67, fixture.boundaryEnd(8, 8));
        assertTrue(SurfaceFluidBoundaryPlan.protects(fixture.boundaries, columnIndex, 67));
        assertFalse(SurfaceFluidBoundaryPlan.protects(fixture.boundaries, columnIndex, 68));
    }

    private void assertEdgeBoundary(int localX, int localZ, int neighborLocalX, int neighborLocalZ) {
        Fixture fixture = new Fixture();
        fixture.setFieldSurface(neighborLocalX, neighborLocalZ, 60D);
        fixture.resolve();
        assertEquals(61, fixture.boundary(localX, localZ));
    }

    private static final class Fixture {
        private final int[] chunkSurfaceHeights = new int[CHUNK_SIZE * CHUNK_SIZE];
        private final double[] fieldSurfaceHeights = new double[FIELD_SIZE * FIELD_SIZE];
        private final double[] fieldFluidHeights = new double[FIELD_SIZE * FIELD_SIZE];
        private final boolean[] fieldHasFluid = new boolean[FIELD_SIZE * FIELD_SIZE];
        private final long[] boundaries = new long[CHUNK_SIZE * CHUNK_SIZE];

        private Fixture() {
            Arrays.fill(chunkSurfaceHeights, 70);
            Arrays.fill(fieldSurfaceHeights, 70D);
            Arrays.fill(fieldFluidHeights, FLUID_HEIGHT);
            Arrays.fill(fieldHasFluid, true);
        }

        private void setChunkSurface(int localX, int localZ, int surfaceY) {
            chunkSurfaceHeights[index(localX, localZ)] = surfaceY;
            setFieldSurface(localX, localZ, surfaceY);
        }

        private void setFieldSurface(int localX, int localZ, double surfaceY) {
            int fieldX = localX + PADDING;
            int fieldZ = localZ + PADDING;
            fieldSurfaceHeights[(fieldX * FIELD_SIZE) + fieldZ] = surfaceY;
        }

        private void setFieldHasFluid(int localX, int localZ, boolean hasFluid) {
            int fieldX = localX + PADDING;
            int fieldZ = localZ + PADDING;
            fieldHasFluid[(fieldX * FIELD_SIZE) + fieldZ] = hasFluid;
        }

        private void setFieldFluidHeight(int localX, int localZ, double fluidHeight) {
            int fieldX = localX + PADDING;
            int fieldZ = localZ + PADDING;
            fieldFluidHeights[(fieldX * FIELD_SIZE) + fieldZ] = fluidHeight;
        }

        private void resolve() {
            SurfaceFluidBoundaryPlan.fill(
                    chunkSurfaceHeights,
                    fieldSurfaceHeights,
                    fieldHasFluid,
                    fieldFluidHeights,
                    FIELD_SIZE,
                    PADDING,
                    boundaries
            );
        }

        private int boundary(int localX, int localZ) {
            return SurfaceFluidBoundaryPlan.startY(boundaries[index(localX, localZ)]);
        }

        private int boundaryEnd(int localX, int localZ) {
            return SurfaceFluidBoundaryPlan.endY(boundaries[index(localX, localZ)]);
        }

        private int index(int localX, int localZ) {
            return PowerOfTwoCoordinates.packLocal16(localX, localZ);
        }
    }
}
