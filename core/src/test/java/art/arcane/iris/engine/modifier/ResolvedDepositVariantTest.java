package art.arcane.iris.engine.modifier;

import art.arcane.iris.builtin.deposit.DepositVariant;
import com.google.gson.Gson;
import com.volmit.ModuleLog;
import com.volmit.block.BlockState;
import com.volmit.host.ModuleLogs;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ResolvedDepositVariantTest {
    private static final String DEEP_IRON = """
            {"minHeight": -64, "maxHeight": 0, "remap": {"minecraft:iron_ore": "minecraft:deepslate_iron_ore", "unknown_block": "minecraft:stone"}}
            """;
    private static final ModuleLog LOG = ModuleLogs.discarding();

    private static ResolvedDepositVariant variant(String json) {
        return new ResolvedDepositVariant(new Gson().fromJson(json, DepositVariant.class), ApiChunk.blocks(), LOG);
    }

    @Test
    public void aVariantAppliesWithinItsInclusiveBand() {
        ResolvedDepositVariant variant = variant(DEEP_IRON);

        assertTrue(variant.appliesAt(-64));
        assertTrue(variant.appliesAt(0));
        assertFalse(variant.appliesAt(-65));
        assertFalse(variant.appliesAt(1));
    }

    @Test
    public void anOreIsRemappedByItsMaterialAndOtherBlocksAreNot() {
        ResolvedDepositVariant variant = variant(DEEP_IRON);

        BlockState remapped = variant.remapOrNull(new BlockState("minecraft:iron_ore"));

        assertEquals("minecraft:deepslate_iron_ore", remapped.key());
        assertNull(variant.remapOrNull(new BlockState("minecraft:coal_ore")));
        assertNull(variant.remapOrNull(null));
    }

    @Test
    public void aRuleWhoseSourceTheServerDoesNotKnowRemapsNothing() {
        ResolvedDepositVariant unknown = variant("{\"remap\": {\"unknown_block\": \"minecraft:stone\"}}");

        assertNull(unknown.remapOrNull(new BlockState("minecraft:unknown_block")));
    }

    @Test
    public void aRuleWhoseReplacementTheServerDoesNotKnowRemapsNothing() {
        ResolvedDepositVariant unknown = variant("{\"remap\": {\"minecraft:iron_ore\": \"unknown_block\"}}");

        assertNull(unknown.remapOrNull(new BlockState("minecraft:iron_ore")));
    }

    @Test
    public void aVariantWithoutRemapsChangesNothing() {
        ResolvedDepositVariant variant = variant("{\"maxHeight\": 5}");

        assertNull(variant.remapOrNull(new BlockState("minecraft:iron_ore")));
    }
}
