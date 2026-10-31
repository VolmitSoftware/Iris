package art.arcane.iris.probe.fidelity;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CaveWallsTest {
    private static final int MIN_Y = -64;

    @Test
    public void solidBesideCaveAirIsAWall() {
        SyntheticFidelityChunk chunk = new SyntheticFidelityChunk(MIN_Y).surface(64).caves(-20, 0, 4);

        assertTrue(CaveWalls.besideCave(chunk, 4, -10, 7));
        assertFalse(CaveWalls.besideCave(chunk, 5, -10, 7));
        assertFalse(CaveWalls.besideCave(chunk, 4, 5, 7));
        assertFalse(CaveWalls.besideCave(chunk, 4, -21, 7));
    }

    @Test
    public void fluidFilledCavesHaveWalls() {
        SyntheticFidelityChunk chunk = new SyntheticFidelityChunk(MIN_Y).surface(64).lavaPool(-64, -56, 4);

        assertTrue(CaveWalls.besideCave(chunk, 4, -60, 7));
        assertFalse(CaveWalls.besideCave(chunk, 4, -50, 7));
    }

    @Test
    public void chunkCornersOnlyReadNeighboursInsideTheChunk() {
        assertTrue(CaveWalls.besideCave(new SyntheticFidelityChunk(MIN_Y).surface(64).caves(-20, 0, 15), 15, -10, 0));
        assertFalse(CaveWalls.besideCave(new SyntheticFidelityChunk(MIN_Y).surface(64), 15, -10, 15));
        assertFalse(CaveWalls.besideCave(new SyntheticFidelityChunk(MIN_Y).surface(64), 0, -10, 0));
    }
}
