package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.anchor.ObjectPlaceMode;
import art.arcane.iris.builtin.carve.CarvingMode;
import art.arcane.iris.builtin.objects.PlacementChanges;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The changes iris:objects asks for are made to a copy of the placement, and the placement it came from is left alone. */
public class IrisObjectChunkChangesTest {
    @Test
    public void withoutChangesThePlacementIsUsedAsItIs() {
        IrisObjectPlacement base = placement("a", "b");

        assertSame(base, IrisObjectChunk.withChanges(base, PlacementChanges.NONE));
    }

    @Test
    public void changesAreMadeToACopyThatKeepsTheObjectsUnlessTheyAreReplaced() {
        IrisObjectPlacement base = placement("a", "b");

        IrisObjectPlacement changed = IrisObjectChunk.withChanges(base, PlacementChanges.NONE
                .withSurfaceSupport(false, 7).withForcePlace(true));

        assertNotSame(base, changed);
        assertEquals(List.of("a", "b"), changed.getPlace());
        assertEquals(false, changed.isRequireSurfaceSupport());
        assertEquals(7, changed.getSurfaceSupportBuffer());
        assertTrue(changed.isForcePlace());
        assertEquals(true, base.isRequireSurfaceSupport());
        assertEquals(false, base.isForcePlace());
    }

    @Test
    public void objectsModeCarvingAndRotationAreReplaced() {
        IrisObjectPlacement base = placement("a");

        IrisObjectPlacement changed = IrisObjectChunk.withChanges(base, PlacementChanges.NONE
                .withPlace(List.of("hut")).withMode(ObjectPlaceMode.FAST_MIN_STILT).withCarvingSupport(CarvingMode.ANYWHERE).withUpsideDown(true));

        assertEquals(List.of("hut"), changed.getPlace());
        assertEquals(ObjectPlaceMode.FAST_MIN_STILT, changed.getMode());
        assertEquals(CarvingMode.ANYWHERE, changed.getCarvingSupport());
        assertTrue(changed.getRotation().isEnabled());
        assertEquals(180D, changed.getRotation().getXAxis().min(), 0);
        assertEquals(ObjectPlaceMode.CENTER_HEIGHT, base.getMode());
    }

    private static IrisObjectPlacement placement(String... place) {
        return new IrisObjectPlacement().setPlace(new KList<>(place));
    }
}
