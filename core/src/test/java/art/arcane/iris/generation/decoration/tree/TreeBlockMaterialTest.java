package art.arcane.iris.generation.decoration.tree;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TreeBlockMaterialTest {
    @Test
    public void materialComparisonIgnoresBlockStateProperties() {
        TreeBlockMaterial expected = TreeBlockMaterial.of("minecraft:oak_log[axis=y]");

        assertTrue(expected.matches("minecraft:oak_log[axis=x]"));
        assertFalse(expected.matches("minecraft:spruce_log[axis=y]"));
    }
}
