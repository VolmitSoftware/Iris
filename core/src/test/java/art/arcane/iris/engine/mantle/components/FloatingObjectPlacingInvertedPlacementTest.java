package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.modules.floating.IslandFootprint;
import art.arcane.iris.engine.object.terrain.floating.FloatingIslandSample;
import art.arcane.iris.engine.object.terrain.floating.FloatingIslandSamples;
import art.arcane.iris.engine.object.terrain.floating.ResolvedFloatingChild;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class FloatingObjectPlacingInvertedPlacementTest {
    private static final UndersideTurn UNTURNED = new UndersideTurn(0);

    private IslandFootprint footprint(int lowestSolidY, int tallestX, int tallestZ) {
        return footprint(lowestSolidY, tallestX, tallestZ, new long[0]);
    }

    private IslandFootprint footprint(int lowestSolidY, int tallestX, int tallestZ, long[] columns) {
        return new IslandFootprint(tallestX, tallestZ, lowestSolidY, columns);
    }

    private FloatingIslandSample sample(ResolvedFloatingChild entry) {
        boolean[] solidMask = new boolean[10];
        Arrays.fill(solidMask, true);
        return FloatingIslandSamples.of(entry, 100, 10, 9, 10, solidMask, null);
    }

    @Test
    public void invertedBaseY_anchorsOriginalLowestSolidBelowBottomFace() {
        IslandFootprint footprint = footprint(5, 2, 3);

        assertEquals(104, FloatingObjectPlacing.invertedBaseY(100, footprint, UNTURNED));
    }

    @Test
    public void invertedBaseX_usesTopFootprintAnchor() {
        IslandFootprint footprint = footprint(5, 2, 3);

        assertEquals(106, FloatingObjectPlacing.invertedBaseX(100, 8, footprint, UNTURNED));
    }

    @Test
    public void invertedBaseZ_mirrorsTopFootprintAnchor() {
        IslandFootprint footprint = footprint(5, 2, 3);

        assertEquals(111, FloatingObjectPlacing.invertedBaseZ(100, 8, footprint, UNTURNED));
    }

    @Test
    public void invertedBaseX_usesFixedYRotationAnchor() {
        IslandFootprint footprint = footprint(5, 2, 3);
        UndersideTurn turn = new UndersideTurn(90);

        assertEquals(111, FloatingObjectPlacing.invertedBaseX(100, 8, footprint, turn));
    }

    @Test
    public void invertedBaseZ_usesFixedYRotationAnchor() {
        IslandFootprint footprint = footprint(5, 2, 3);
        UndersideTurn turn = new UndersideTurn(90);

        assertEquals(110, FloatingObjectPlacing.invertedBaseZ(100, 8, footprint, turn));
    }

    @Test
    public void invertedBaseY_isStableAcrossFixedYRotation() {
        IslandFootprint footprint = footprint(5, 2, 3);
        UndersideTurn turn = new UndersideTurn(270);

        assertEquals(104, FloatingObjectPlacing.invertedBaseY(100, footprint, turn));
    }

    @Test
    public void topFootprintChecksNeighborChunkTerrain() {
        ResolvedFloatingChild entry = mock(ResolvedFloatingChild.class);
        FloatingIslandSample sample = sample(entry);
        long[] cells = {0L, 1L << 32};
        IslandFootprint footprint = footprint(0, 0, 0, cells);

        assertTrue(FloatingObjectPlacing.isFootprintFlat(
                footprint, 15, 0, 109,
                (x, z) -> z == 0 && (x == 15 || x == 16) ? sample : null,
                entry, 2));
        assertFalse(FloatingObjectPlacing.isFootprintFlat(
                footprint, 15, 0, 109,
                (x, z) -> x == 15 && z == 0 ? sample : null,
                entry, 2));
    }
}
