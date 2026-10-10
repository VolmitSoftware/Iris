package art.arcane.iris.engine.modifier;

import art.arcane.iris.builtin.deposit.DepositVariant;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import com.google.gson.Gson;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class ResolvedDepositVariantTest {
    private static final String DEEP_IRON = """
            {"minHeight": -64, "maxHeight": 0, "remap": {"minecraft:iron_ore": "minecraft:deepslate_iron_ore", "unknown_block": "minecraft:stone"}}
            """;

    private static void knowEveryBlockButTheUnknownOne() {
        UsableBlockPlatform.bind();
        when(IrisPlatforms.get().registries().blockOrNull(anyString(), anyBoolean())).thenAnswer(call -> {
            String name = call.getArgument(0);
            return name.equals("unknown_block") ? null : UsableBlockPlatform.block(name);
        });
    }

    @After
    public void unbindPlatform() {
        UsableBlockPlatform.unbind();
    }

    @Test
    public void aVariantAppliesWithinItsInclusiveBand() {
        ResolvedDepositVariant variant = new ResolvedDepositVariant(new Gson().fromJson(DEEP_IRON, DepositVariant.class));

        assertTrue(variant.appliesAt(-64));
        assertTrue(variant.appliesAt(0));
        assertFalse(variant.appliesAt(-65));
        assertFalse(variant.appliesAt(1));
    }

    @Test
    public void anOreIsRemappedByItsMaterialAndOtherBlocksAreNot() {
        knowEveryBlockButTheUnknownOne();
        ResolvedDepositVariant variant = new ResolvedDepositVariant(new Gson().fromJson(DEEP_IRON, DepositVariant.class));
        PlatformBlockState iron = UsableBlockPlatform.block("iron_ore");

        PlatformBlockState remapped = variant.remapOrNull(iron);

        assertEquals("minecraft:deepslate_iron_ore", remapped.key());
        assertNull(variant.remapOrNull(UsableBlockPlatform.block("coal_ore")));
        assertNull(variant.remapOrNull(null));
    }

    @Test
    public void aRuleWhoseSourceTheServerDoesNotKnowRemapsNothing() {
        knowEveryBlockButTheUnknownOne();
        DepositVariant unknown = new Gson().fromJson("{\"remap\": {\"unknown_block\": \"minecraft:stone\"}}", DepositVariant.class);

        assertNull(new ResolvedDepositVariant(unknown).remapOrNull(UsableBlockPlatform.block("unknown_block")));
    }

    @Test
    public void aVariantWithoutRemapsChangesNothing() {
        UsableBlockPlatform.bind();
        ResolvedDepositVariant variant = new ResolvedDepositVariant(new Gson().fromJson("{\"maxHeight\": 5}", DepositVariant.class));

        assertNull(variant.remapOrNull(UsableBlockPlatform.block("iron_ore")));
    }
}
