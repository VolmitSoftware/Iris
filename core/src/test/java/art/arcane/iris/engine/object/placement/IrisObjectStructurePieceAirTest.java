package art.arcane.iris.engine.object.placement;

import com.volmit.block.BlockState;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisObjectStructurePieceAirTest {
    @Test
    public void explicitAirPlacesOnlyForRawStructurePieces() {
        assertTrue(BlocksModule.shouldPlaceObjectBlock(true, true, false));
        assertFalse(BlocksModule.shouldPlaceObjectBlock(false, true, false));
    }

    @Test
    public void ordinaryObjectAirRemainsNondestructive() {
        assertFalse(BlocksModule.shouldPlaceObjectBlock(false, true, false));
        assertTrue(BlocksModule.shouldPlaceObjectBlock(false, false, false));
    }

    @Test
    public void aBlockTurnedIntoAirHadNoValidOrientationAndIsDropped() {
        BlockState torch = new BlockState("minecraft:wall_torch");
        BlockState air = new BlockState("minecraft:air");

        assertTrue(BlocksModule.isDroppedByRotation(torch, air));
        assertFalse(BlocksModule.isDroppedByRotation(torch, torch));
        assertFalse(BlocksModule.isDroppedByRotation(air, air));
    }

    @Test
    public void vineReplacementRemainsRejectedForEveryPlacementMode() {
        assertFalse(BlocksModule.shouldPlaceObjectBlock(false, false, true));
        assertFalse(BlocksModule.shouldPlaceObjectBlock(true, false, true));
        assertFalse(BlocksModule.shouldPlaceObjectBlock(true, true, true));
    }
}
