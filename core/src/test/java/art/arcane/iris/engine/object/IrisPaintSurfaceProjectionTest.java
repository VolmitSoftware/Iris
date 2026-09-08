package art.arcane.iris.engine.object;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisPaintSurfaceProjectionTest {
    @Test
    public void positiveAndNegativeChunkSeamsHaveIdenticalRelativeSurfaces() {
        IrisPaintSurfaceProjection positive = projection(15);
        IrisPaintSurfaceProjection negative = projection(-17);
        assertNotNull(positive);
        assertNotNull(negative);

        for (int x = -5; x <= 5; x++) {
            for (int z = -1; z <= 1; z++) {
                assertEquals(80 + x * 2, positive.surfaceY(15 + x, z));
                assertEquals(positive.surfaceY(15 + x, z), negative.surfaceY(-17 + x, z));
            }
        }
    }

    @Test
    public void exposedFloorsMustExistAtTheRequestedAnchor() {
        IObjectPlacer placer = mock(IObjectPlacer.class);
        when(placer.getHighest(anyInt(), anyInt(), any(), anyBoolean())).thenReturn(140);
        when(placer.isSurfaceSolid(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            int y = invocation.getArgument(1);
            return y >= 0 && y <= 100;
        });

        assertNull(IrisPaintSurfaceProjection.create(placer, request(80, false)));
        assertNotNull(IrisPaintSurfaceProjection.create(placer, request(100, false)));
        assertNull(IrisPaintSurfaceProjection.create(placer, request(140, false)));
    }

    @Test
    public void aVirtualHeightmapDoesNotFallBackToPhysicalLedges() {
        IObjectPlacer placer = mock(IObjectPlacer.class);
        when(placer.getHighest(anyInt(), anyInt(), any(), anyBoolean()))
                .thenAnswer(invocation -> (int) invocation.getArgument(0) == 0 ? 80 : 140);
        when(placer.isSurfaceSolid(anyInt(), anyInt(), anyInt()))
                .thenAnswer(invocation -> (int) invocation.getArgument(1) <= 80);
        IrisPaintSurfaceProjection projection = IrisPaintSurfaceProjection.create(placer, request(80, true));

        assertNotNull(projection);
        assertEquals(80, projection.surfaceY(0, 0));
        assertEquals(IrisPaintSurfaceProjection.MISSING, projection.surfaceY(1, 0));
    }

    @Test
    public void surfaceChoicesStayCompatibleWhenTwoTraversalBranchesMeet() {
        IObjectPlacer placer = mock(IObjectPlacer.class);
        when(placer.getHighest(anyInt(), anyInt(), any(), anyBoolean())).thenAnswer(invocation -> {
            int x = invocation.getArgument(0);
            int z = invocation.getArgument(1);
            return 80 + x * z;
        });
        when(placer.isSurfaceSolid(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            int x = invocation.getArgument(0);
            int y = invocation.getArgument(1);
            int z = invocation.getArgument(2);
            return y <= 80 + x * z;
        });
        IrisPaintSurfaceProjection projection = IrisPaintSurfaceProjection.create(placer,
                new IrisPaintSurfaceProjection.Request(null, -6, 6, -6, 6, 0, 80, 0, true, false));
        assertNotNull(projection);

        for (int x = -6; x < 6; x++) {
            for (int z = -6; z < 6; z++) {
                int surface = projection.surfaceY(x, z);
                if (surface == IrisPaintSurfaceProjection.MISSING) {
                    continue;
                }
                int east = projection.surfaceY(x + 1, z);
                int south = projection.surfaceY(x, z + 1);
                assertTrue(east == IrisPaintSurfaceProjection.MISSING || Math.abs(surface - east) <= 4);
                assertTrue(south == IrisPaintSurfaceProjection.MISSING || Math.abs(surface - south) <= 4);
            }
        }
    }

    private IrisPaintSurfaceProjection projection(int anchorX) {
        IObjectPlacer placer = mock(IObjectPlacer.class);
        when(placer.getHighest(anyInt(), anyInt(), any(), anyBoolean())).thenAnswer(invocation -> {
            int x = invocation.getArgument(0);
            return x > anchorX ? 200 : 80 + (x - anchorX) * 2;
        });
        when(placer.isSurfaceSolid(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            int x = invocation.getArgument(0);
            int y = invocation.getArgument(1);
            return y <= 80 + (x - anchorX) * 2 || x > anchorX && y >= 190 && y <= 200;
        });
        return IrisPaintSurfaceProjection.create(placer, new IrisPaintSurfaceProjection.Request(null,
                anchorX - 5, anchorX + 5, -1, 1, anchorX, 80, 0, true, false));
    }

    private IrisPaintSurfaceProjection.Request request(int anchorY, boolean virtual) {
        return new IrisPaintSurfaceProjection.Request(null, 0, 1, 0, 0, 0, anchorY, 0, true, virtual);
    }
}
