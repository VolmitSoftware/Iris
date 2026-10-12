package art.arcane.iris.builtin.structure;

import org.junit.Test;

import static art.arcane.iris.engine.framework.StructurePlacementFixtures.placement;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StructurePlacementAnchorTest {
    @Test
    public void legacyPreservesUndergroundBehavior() {
        StructurePlacement surface = new StructurePlacement();
        StructurePlacement underground = placement("underground", true);

        assertEquals(StructureAnchorMode.SURFACE, surface.resolvedAnchor());
        assertEquals(StructureAnchorMode.HEIGHT_BAND, underground.resolvedAnchor());
        assertFalse(surface.isAnchoredUnderground());
        assertTrue(underground.isAnchoredUnderground());
    }

    @Test
    public void explicitAnchorOverridesLegacyBoolean() {
        StructurePlacement floor = placement("underground", false, "anchor", StructureAnchorMode.CAVE_FLOOR);
        StructurePlacement surface = placement("underground", true, "anchor", StructureAnchorMode.SURFACE);

        assertTrue(floor.isAnchoredUnderground());
        assertFalse(surface.isAnchoredUnderground());
    }

    @Test
    public void aMissingAnchorIsLegacy() {
        StructurePlacement placement = placement("anchor", null, "underground", true);

        assertEquals(StructureAnchorMode.LEGACY, placement.anchor());
        assertEquals(StructureAnchorMode.HEIGHT_BAND, placement.resolvedAnchor());
    }
}
