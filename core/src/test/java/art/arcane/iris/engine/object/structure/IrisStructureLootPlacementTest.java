package art.arcane.iris.engine.object.structure;

import art.arcane.iris.engine.mantle.components.StructurePlacing;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.anchor.ObjectPlaceMode;
import art.arcane.iris.engine.framework.structure.assembly.StructureStamp;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisStructureLootPlacementTest {
    @Test
    public void createsPiecePlacementWithAuthoredLootOrderAndDefaultWeights() {
        StructureStamp stamp = new StructureStamp(ObjectPlaceMode.STRUCTURE_PIECE, List.of(),
                List.of("village_common", "village_rare"), "");

        ObjectPlacement placement = StructurePlacing.lootPlacement(stamp, "pieces/village_house");

        assertEquals(1, placement.place().size());
        assertEquals("pieces/village_house", placement.place().get(0));
        assertEquals(2, placement.loot().size());
        assertEquals("village_common", placement.loot().get(0).name());
        assertEquals(1, placement.loot().get(0).weight());
        assertTrue(placement.loot().get(0).filter().isEmpty());
        assertEquals("village_rare", placement.loot().get(1).name());
        assertEquals(1, placement.loot().get(1).weight());
        assertTrue(placement.loot().get(1).filter().isEmpty());
        assertFalse(placement.overrideGlobalLoot());
    }

    @Test
    public void createsEmptyLootPlacementWhenStructureHasNoLoot() {
        ObjectPlacement placement = StructurePlacing.lootPlacement(
                new StructureStamp(ObjectPlaceMode.STRUCTURE_PIECE, List.of(), List.of(), ""), "pieces/empty_house");

        assertEquals("pieces/empty_house", placement.place().get(0));
        assertTrue(placement.loot().isEmpty());
        assertFalse(placement.overrideGlobalLoot());
    }
}
