package art.arcane.iris.modded.command;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandText;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeItemStack;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeTaggedItems;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Items;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class NativeTaggedItemsTest {
    @BeforeClass
    public static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        if (!Items.BLAZE_ROD.builtInRegistryHolder().areComponentsBound()) {
            Items.BLAZE_ROD.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                    .set(DataComponents.MAX_STACK_SIZE, 64).build());
        }
    }

    @Test
    public void toolRetainsNameLoreFlagsAndHiddenUnbreakableMarker() {
        NativeItemStack item = NativeTaggedItems.create(new NativeTaggedItems.Options("minecraft:blaze_rod",
                NativeCommandText.literal("Selection"), List.of(NativeCommandText.literal("First corner")),
                "selection_tool", true, true));

        assertSame(Items.BLAZE_ROD, item.stack().getItem());
        assertEquals("Selection", item.stack().get(DataComponents.CUSTOM_NAME).getString());
        assertEquals("First corner", item.stack().get(DataComponents.LORE).lines().getFirst().getString());
        assertTrue(item.stack().get(DataComponents.CUSTOM_DATA).copyTag().getBooleanOr("selection_tool", false));
        assertTrue(item.stack().get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
        assertTrue(item.stack().has(DataComponents.UNBREAKABLE));
        assertTrue(item.stack().get(DataComponents.TOOLTIP_DISPLAY).hiddenComponents().contains(DataComponents.UNBREAKABLE));
    }

    @Test
    public void editingSoundKeysResolveToTheOriginalEvents() {
        assertSame(SoundEvents.END_PORTAL_FRAME_FILL, BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse("minecraft:block.end_portal_frame.fill")));
        assertSame(SoundEvents.LODESTONE_COMPASS_LOCK, BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse("minecraft:item.lodestone_compass.lock")));
        assertSame(SoundEvents.AMETHYST_BLOCK_CHIME, BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse("minecraft:block.amethyst_block.chime")));
    }
}
