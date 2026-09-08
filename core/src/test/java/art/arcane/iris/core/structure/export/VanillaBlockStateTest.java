package art.arcane.iris.core.structure.export;

import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class VanillaBlockStateTest {
    @Test
    public void aBareBlockKeepsItsNameAndNoProperties() {
        VanillaBlockState state = VanillaBlockState.parse("  minecraft:stone  ");

        assertEquals("minecraft:stone", state.name());
        assertTrue(state.properties().isEmpty());
        assertEquals("minecraft:stone", state.canonical());
    }

    @Test
    public void propertiesAreCanonicalizedIntoAlphabeticalOrder() {
        VanillaBlockState state = VanillaBlockState.parse("minecraft:oak_stairs[waterlogged=false,facing=north,half=top]");

        assertEquals("minecraft:oak_stairs[facing=north,half=top,waterlogged=false]", state.canonical());
        assertEquals("north", state.properties().get("facing"));
    }

    @Test
    public void theCanonicalFormRoundTripsThroughTheParser() {
        String canonical = VanillaBlockState.parse("minecraft:oak_log[axis=z]").canonical();

        assertEquals(canonical, VanillaBlockState.parse(canonical).canonical());
    }

    @Test
    public void theNbtFormCarriesTheNameAndOnlyRealProperties() {
        CompoundTag bare = VanillaBlockState.parse("minecraft:air").toNbt();
        CompoundTag stairs = VanillaBlockState.parse("minecraft:oak_stairs[facing=north]").toNbt();

        assertEquals("minecraft:air", bare.getString("Name"));
        assertNull(bare.get("Properties"));
        assertEquals("minecraft:oak_stairs", stairs.getString("Name"));
        assertEquals("north", stairs.getCompoundTag("Properties").getString("facing"));
    }

    @Test
    public void nonMinecraftBlocksAreRejectedBeforeAnythingIsWritten() {
        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, () -> VanillaBlockState.parse("iris:custom_block"));

        assertTrue(failure.getMessage(), failure.getMessage().contains("non-Minecraft block"));
    }

    @Test
    public void malformedPropertyBlocksAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> VanillaBlockState.parse(null));
        assertThrows(IllegalArgumentException.class, () -> VanillaBlockState.parse("minecraft:stone[facing=north"));
        assertThrows(IllegalArgumentException.class, () -> VanillaBlockState.parse("minecraft:stone[]"));
        assertThrows(IllegalArgumentException.class, () -> VanillaBlockState.parse("minecraft:stone[facing]"));
        assertThrows(IllegalArgumentException.class, () -> VanillaBlockState.parse("minecraft:stone[facing=]"));
        assertThrows(IllegalArgumentException.class, () -> VanillaBlockState.parse("minecraft:stone[=north]"));
        assertThrows(IllegalArgumentException.class, () -> VanillaBlockState.parse("minecraft:stone[a=1][b=2]"));
    }

    @Test
    public void aRepeatedPropertyIsRejectedInsteadOfSilentlyWinning() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> VanillaBlockState.parse("minecraft:stone[facing=north,facing=south]"));

        assertTrue(failure.getMessage(), failure.getMessage().contains("Duplicate block property"));
    }
}
