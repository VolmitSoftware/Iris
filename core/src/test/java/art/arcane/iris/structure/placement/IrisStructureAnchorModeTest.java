package art.arcane.iris.structure.placement;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisStructureAnchorModeTest {
    @Test
    public void legacyPreservesUndergroundBehavior() {
        IrisStructurePlacement surface = new IrisStructurePlacement();
        IrisStructurePlacement underground = new IrisStructurePlacement().setUnderground(true);

        assertEquals(IrisStructureAnchorMode.SURFACE, surface.resolvedAnchor());
        assertEquals(IrisStructureAnchorMode.HEIGHT_BAND, underground.resolvedAnchor());
        assertFalse(surface.isAnchoredUnderground());
        assertTrue(underground.isAnchoredUnderground());
    }

    @Test
    public void explicitAnchorOverridesLegacyBoolean() {
        IrisStructurePlacement floor = new IrisStructurePlacement()
                .setUnderground(false)
                .setAnchor(IrisStructureAnchorMode.CAVE_FLOOR);
        IrisStructurePlacement surface = new IrisStructurePlacement()
                .setUnderground(true)
                .setAnchor(IrisStructureAnchorMode.SURFACE);

        assertTrue(floor.isAnchoredUnderground());
        assertFalse(surface.isAnchoredUnderground());
    }
}
