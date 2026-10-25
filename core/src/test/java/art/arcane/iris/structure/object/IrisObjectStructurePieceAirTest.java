package art.arcane.iris.structure.object;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisObjectStructurePieceAirTest {
    @Test
    public void explicitAirPlacesOnlyForRawStructurePieces() {
        assertTrue(ObjectBlockPass.shouldPlaceObjectBlock(true, true, false));
        assertFalse(ObjectBlockPass.shouldPlaceObjectBlock(false, true, false));
    }

    @Test
    public void ordinaryObjectAirRemainsNondestructive() {
        assertFalse(ObjectBlockPass.shouldPlaceObjectBlock(false, true, false));
        assertTrue(ObjectBlockPass.shouldPlaceObjectBlock(false, false, false));
    }

    @Test
    public void vineReplacementRemainsRejectedForEveryPlacementMode() {
        assertFalse(ObjectBlockPass.shouldPlaceObjectBlock(false, false, true));
        assertFalse(ObjectBlockPass.shouldPlaceObjectBlock(true, false, true));
        assertFalse(ObjectBlockPass.shouldPlaceObjectBlock(true, true, true));
    }
}
