package art.arcane.iris.structure.object;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisObjectStructurePieceAirTest {
    @Test
    public void explicitAirPlacesOnlyForRawStructurePieces() {
        assertTrue(IrisObjectPlacementRunner.shouldPlaceObjectBlock(true, true, false));
        assertFalse(IrisObjectPlacementRunner.shouldPlaceObjectBlock(false, true, false));
    }

    @Test
    public void ordinaryObjectAirRemainsNondestructive() {
        assertFalse(IrisObjectPlacementRunner.shouldPlaceObjectBlock(false, true, false));
        assertTrue(IrisObjectPlacementRunner.shouldPlaceObjectBlock(false, false, false));
    }

    @Test
    public void vineReplacementRemainsRejectedForEveryPlacementMode() {
        assertFalse(IrisObjectPlacementRunner.shouldPlaceObjectBlock(false, false, true));
        assertFalse(IrisObjectPlacementRunner.shouldPlaceObjectBlock(true, false, true));
        assertFalse(IrisObjectPlacementRunner.shouldPlaceObjectBlock(true, true, true));
    }
}
