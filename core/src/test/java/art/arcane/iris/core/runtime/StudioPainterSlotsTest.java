package art.arcane.iris.core.runtime;

import com.volmit.tooling.world.ChunkPainter;
import org.junit.After;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class StudioPainterSlotsTest {
    private static final String WORLD = "iris-00000000-0000-0000-0000-000000000001";
    private static final String OTHER_WORLD = "iris-00000000-0000-0000-0000-000000000002";

    private final ChunkPainter painter = (chunkX, chunkZ, canvas) -> { };

    @After
    public void removeSlots() {
        StudioPainterSlots.remove(WORLD);
        StudioPainterSlots.remove(OTHER_WORLD);
    }

    @Test
    public void aWorldIsPaintedFromTheMomentItsSlotIsInstalledUntilItIsRemoved() {
        StudioPainterSlots.Slot slot = new StudioPainterSlots.Slot(painter, painted -> { });
        assertFalse(StudioPainterSlots.isInstalled(WORLD));

        StudioPainterSlots.install(WORLD, slot);

        assertTrue(StudioPainterSlots.isInstalled(WORLD));
        assertSame(slot, StudioPainterSlots.slotOf(WORLD).orElseThrow());
        assertFalse("other worlds keep their pack", StudioPainterSlots.isInstalled(OTHER_WORLD));

        StudioPainterSlots.remove(WORLD);

        assertFalse(StudioPainterSlots.isInstalled(WORLD));
    }

    @Test
    public void aWorldHasOneSlotAtATime() {
        StudioPainterSlots.install(WORLD, new StudioPainterSlots.Slot(painter, painted -> { }));

        assertThrows(IllegalStateException.class,
                () -> StudioPainterSlots.install(WORLD, new StudioPainterSlots.Slot(painter, painted -> { })));
    }

    @Test
    public void removingASlotThatIsNotThereOrAskingForANullNameIsHarmless() {
        StudioPainterSlots.remove(WORLD);
        StudioPainterSlots.remove(null);

        assertTrue(StudioPainterSlots.slotOf(null).isEmpty());
        assertTrue(StudioPainterSlots.slotOf(WORLD).isEmpty());
    }

    @Test
    public void aPaintedChunkKeepsItsOwnCopyOfTheBlockEntities() {
        List<StudioPainterSlots.PaintedBlockEntity> entities = new java.util.ArrayList<>();
        StudioPainterSlots.PaintedChunk chunk = new StudioPainterSlots.PaintedChunk(1, 2, entities);
        entities.add(null);

        assertTrue(chunk.blockEntities().isEmpty());
    }
}
