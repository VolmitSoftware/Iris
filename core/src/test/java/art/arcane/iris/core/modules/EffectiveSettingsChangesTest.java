package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.anchor.ObjectPlaceMode;
import art.arcane.iris.builtin.carve.CarvingMode;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.modules.objects.PlacementChanges;
import art.arcane.iris.modules.objects.EffectiveSettings;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The changes iris:objects asks for are made to a copy of the placement, and the placement it came from is left alone. */
public class EffectiveSettingsChangesTest {
    @Test
    public void withoutChangesThePlacementIsUsedAsItIs() {
        ObjectPlacement base = placement("a", "b");

        assertSame(base, EffectiveSettings.withChanges(base, PlacementChanges.NONE));
    }

    @Test
    public void changesAreMadeToACopyThatKeepsTheObjectsUnlessTheyAreReplaced() {
        ObjectPlacement base = placement("a", "b");

        ObjectPlacement changed = EffectiveSettings.withChanges(base, PlacementChanges.NONE
                .withSurfaceSupport(false, 7).withForcePlace(true));

        assertNotSame(base, changed);
        assertEquals(List.of("a", "b"), changed.place());
        assertEquals(false, changed.requireSurfaceSupport());
        assertEquals(7, changed.surfaceSupportBuffer());
        assertTrue(changed.forcePlace());
        assertEquals(true, base.requireSurfaceSupport());
        assertEquals(false, base.forcePlace());
    }

    @Test
    public void objectsModeCarvingAndRotationAreReplaced() {
        ObjectPlacement base = placement("a");

        ObjectPlacement changed = EffectiveSettings.withChanges(base, PlacementChanges.NONE
                .withPlace(List.of("hut")).withMode(ObjectPlaceMode.FAST_MIN_STILT).withCarvingSupport(CarvingMode.ANYWHERE).withUpsideDown(true));

        assertEquals(List.of("hut"), changed.place());
        assertEquals(ObjectPlaceMode.FAST_MIN_STILT, changed.mode());
        assertEquals(CarvingMode.ANYWHERE, changed.carvingSupport());
        assertTrue(changed.rotation().enabled());
        assertEquals(180D, changed.rotation().xAxis().min(), 0);
        assertEquals(ObjectPlaceMode.CENTER_HEIGHT, base.mode());
    }

    private static ObjectPlacement placement(String... place) {
        return ObjectPlacement.builder().place(List.of(place)).build();
    }
}
