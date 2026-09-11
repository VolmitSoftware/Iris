package art.arcane.iris.modded;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ModdedWorldgenIdsTest {
    @Test
    public void scopedIdsRetainExactUtf8HexEncoding() {
        assertEquals("irisworldgen:packs/5061636b2f412d5f/dimensions/c389e5b1b1/preset",
                ModdedWorldgenIds.presetRef("Pack/A-_", "É山"));
        assertEquals("irisworldgen:packs//dimensions//preset", ModdedWorldgenIds.presetRef("", ""));
        assertEquals("irisworldgen:packs/3f/dimensions/f0a08080/dimension_type",
                ModdedWorldgenIds.dimensionTypeRef("\uD800", "\uD840\uDC00"));
        assertEquals(new ModdedWorldgenIds.ScopedDimension("Pack/A-_", "É山"),
                ModdedWorldgenIds.scopedDimensionType(
                        ModdedWorldgenIds.dimensionTypeRef("Pack/A-_", "É山")).orElseThrow());
    }

    @Test
    public void malformedIdentifiersRetainRejectionBehavior() {
        assertTrue(ModdedWorldgenIds.scopedDimensionType(
                "irisworldgen:packs/7/dimensions/61/dimension_type").isEmpty());
        assertTrue(ModdedWorldgenIds.scopedDimensionType(
                "irisworldgen:packs/gg/dimensions/61/dimension_type").isEmpty());
        assertThrows(NullPointerException.class, () -> ModdedWorldgenIds.presetRef(null, "dimension"));
    }
}
