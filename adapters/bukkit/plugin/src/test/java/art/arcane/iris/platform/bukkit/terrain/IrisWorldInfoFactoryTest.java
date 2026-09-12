package art.arcane.iris.platform.bukkit.terrain;

import art.arcane.iris.api.terrain.IrisWorldInfo;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class IrisWorldInfoFactoryTest {
    @Test
    public void aDescribableWorldReportsFluidHeightInAbsoluteWorldY() {
        IrisWorldInfo info = IrisWorldInfoFactory.build(
                "overworld", "minecraft:world", 42L, -64, 320, 63, false);

        assertNotNull(info);
        assertEquals("overworld", info.dimensionKey());
        assertEquals("minecraft:world", info.worldIdentity());
        assertEquals(42L, info.seed());
        assertEquals(-64, info.minHeight());
        assertEquals(320, info.maxHeight());
        assertEquals(-1, info.fluidHeight());
        assertEquals(384, info.height());
        assertFalse(info.studio());
    }

    @Test
    public void aStudioWorldIsReportedAsStudio() {
        IrisWorldInfo info = IrisWorldInfoFactory.build(
                "overworld", "minecraft:studio", 1L, 0, 256, 63, true);

        assertNotNull(info);
        assertTrue(info.studio());
        assertEquals(63, info.fluidHeight());
    }

    @Test
    public void anIndescribableWorldIsAbsentRatherThanHalfBuilt() {
        assertNull(IrisWorldInfoFactory.build(null, "minecraft:world", 1L, 0, 256, 63, false));
        assertNull(IrisWorldInfoFactory.build("overworld", null, 1L, 0, 256, 63, false));
        assertNull(IrisWorldInfoFactory.build("overworld", "minecraft:world", 1L, 256, 256, 63, false));
        assertNull(IrisWorldInfoFactory.build("overworld", "minecraft:world", 1L, 320, 0, 63, false));
    }

    @Test
    public void anAbsentGeneratorOrWorldIsDescribedAsNothing() {
        assertNull(IrisWorldInfoFactory.from(null));
        assertNull(IrisWorldInfoFactory.forWorld(null));
    }
}
