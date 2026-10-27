package art.arcane.iris.engine.object.structure;

import art.arcane.iris.engine.mantle.components.StructurePlacing;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.engine.object.structure.IrisStructure;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisStructureLootPlacementTest {
    @Test
    public void createsPiecePlacementWithAuthoredLootOrderAndDefaultWeights() {
        IrisStructure structure = new IrisStructure();
        structure.getLoot().add("village_common");
        structure.getLoot().add("village_rare");

        ObjectPlacement placement = StructurePlacing.lootPlacement(structure, "pieces/village_house");

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
        ObjectPlacement placement = StructurePlacing.lootPlacement(new IrisStructure(), "pieces/empty_house");

        assertEquals("pieces/empty_house", placement.place().get(0));
        assertTrue(placement.loot().isEmpty());
        assertFalse(placement.overrideGlobalLoot());
    }
}
