package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.builtin.anchor.ObjectPlaceMode;
import art.arcane.iris.builtin.carve.CaveProfile;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.modules.objects.PlacementChanges;
import com.volmit.object.PlaceableObject;
import art.arcane.iris.engine.object.TestSettings;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A dimension and a cave profile change the placement an object is placed with, and only ever towards safer placing. */
public class EffectivePlacementsTest {
    @Test
    public void aPlacementTheDimensionAgreesWithIsPlacedAsWritten() {
        ObjectPlacement placement = TestSettings.of(ObjectPlacement.class, "{\"surfaceSupportBuffer\":3}");

        PlacementChanges changes = new EffectivePlacements(true, 2).forSurface(placement, object("trees/oak"));

        assertTrue(changes.isNone());
    }

    @Test
    public void theDimensionWidensTheBufferAndCanSwitchSupportOffButNeverNarrowsTheBuffer() {
        ObjectPlacement relaxed = TestSettings.of(ObjectPlacement.class, "{\"requireSurfaceSupport\":false,\"surfaceSupportBuffer\":1}");
        ObjectPlacement strict = TestSettings.of(ObjectPlacement.class, "{\"requireSurfaceSupport\":true,\"surfaceSupportBuffer\":5}");

        PlacementChanges widened = new EffectivePlacements(true, 2).forSurface(relaxed, object("a"));
        PlacementChanges switchedOff = new EffectivePlacements(false, 2).forSurface(strict, object("a"));

        assertEquals("a placement that does not require support cannot be made to", false, widened.requireSurfaceSupport());
        assertEquals(2, (int) widened.surfaceSupportBuffer());
        assertEquals("a dimension that does not require support switches the requirement off", false, switchedOff.requireSurfaceSupport());
        assertEquals("the buffer is never narrowed", 5, (int) switchedOff.surfaceSupportBuffer());
    }

    @Test
    public void importedObjectsAreStiltedFastUnlessTheirModeFindsItsOwnHeight() {
        ObjectPlacement centered = TestSettings.of(ObjectPlacement.class, "{\"mode\":\"CENTER_HEIGHT\"}");
        ObjectPlacement stilted = TestSettings.of(ObjectPlacement.class, "{\"mode\":\"FAST_MIN_STILT\"}");

        PlacementChanges imported = new EffectivePlacements(true, 0).forSurface(centered, object("imports/castle"));

        assertEquals(ObjectPlaceMode.FAST_MIN_STILT, imported.mode());
        assertEquals(List.of("imports/castle"), imported.place());
        assertTrue(new EffectivePlacements(true, 0).forSurface(stilted, object("imports/castle")).isNone());
        assertTrue(new EffectivePlacements(true, 0).forSurface(centered, object("castle")).isNone());
    }

    @Test
    public void aCaveProfileGivesCenteredPlacementsItsPlaceMode() {
        ObjectPlacement centered = TestSettings.of(ObjectPlacement.class, "{\"mode\":\"CENTER_HEIGHT\"}");
        CaveProfile profile = TestSettings.of(CaveProfile.class, "{\"enabled\":true,\"defaultObjectPlaceMode\":\"STILT\"}");

        PlacementChanges changes = new EffectivePlacements(true, 0).forCave(centered, object("crystal"), profile);

        assertEquals(ObjectPlaceMode.STILT, changes.mode());
        assertEquals(List.of("crystal"), changes.place());
        assertNull(new EffectivePlacements(true, 0).forCave(centered, object("crystal"), null).mode());
    }

    private static PlaceableObject object(String key) {
        PlaceableObject object = mock(PlaceableObject.class);
        when(object.name()).thenReturn(key);
        return object;
    }
}
