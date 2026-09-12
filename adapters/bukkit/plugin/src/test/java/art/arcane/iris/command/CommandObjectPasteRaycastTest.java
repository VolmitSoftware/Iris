package art.arcane.iris.command;

import org.bukkit.Material;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CommandObjectPasteRaycastTest {
    @Test
    public void rejectsEveryAirVariantAndConfiguredFoliageAsPasteTargets() {
        assertFalse(CommandObject.isPasteTarget(Material.AIR));
        assertFalse(CommandObject.isPasteTarget(Material.CAVE_AIR));
        assertFalse(CommandObject.isPasteTarget(Material.VOID_AIR));
        assertFalse(CommandObject.isPasteTarget(Material.SHORT_GRASS));
        assertFalse(CommandObject.isPasteTarget(Material.SNOW));
        assertFalse(CommandObject.isPasteTarget(Material.VINE));
        assertFalse(CommandObject.isPasteTarget(Material.TORCH));
        assertFalse(CommandObject.isPasteTarget(Material.DEAD_BUSH));
        assertFalse(CommandObject.isPasteTarget(Material.POPPY));
        assertFalse(CommandObject.isPasteTarget(Material.DANDELION));
    }

    @Test
    public void acceptsSolidBlocksAsPasteTargets() {
        assertTrue(CommandObject.isPasteTarget(Material.STONE));
    }
}
