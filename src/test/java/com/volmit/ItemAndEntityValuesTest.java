package com.volmit;

import com.volmit.entity.EntitySpec;
import com.volmit.item.ItemDrop;
import com.volmit.item.ItemEnchantment;
import com.volmit.service.AmbientEffects;
import com.volmit.service.BlockDrops;
import com.volmit.service.EntitySpawns;
import com.volmit.service.SaplingGrowth;
import com.volmit.world.BlockPosition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The values services hand the host are immutable and refuse what the host could not create. */
class ItemAndEntityValuesTest {
    @Test
    void anItemDropRefusesNothingToDropAndKeepsItsListsApart() {
        assertThrows(IllegalArgumentException.class, () -> ItemDrop.of("stick", 0));

        List<ItemEnchantment> enchantments = new ArrayList<>(List.of(new ItemEnchantment("sharpness", 3)));
        ItemDrop drop = new ItemDrop("diamond_sword", 1, null, List.of(), enchantments, List.of(), false, List.of(), null,
                0.25, null, null, Map.of(), null);
        enchantments.clear();

        assertEquals(1, drop.enchantments().size());
        assertThrows(UnsupportedOperationException.class, () -> drop.enchantments().clear());
        assertThrows(IllegalArgumentException.class, () -> new ItemEnchantment("sharpness", 0));
    }

    @Test
    void anEntityKnowsWhetherItIsAnotherPluginsMob() {
        EntitySpec zombie = entity("minecraft:zombie", "");
        EntitySpec special = entity("", "mythic:dragon");

        assertFalse(zombie.isSpecial());
        assertTrue(special.isSpecial());
    }

    @Test
    void anAttemptSaysWhetherLightMatters() {
        BlockPosition at = new BlockPosition(1, 2, 3);
        EntitySpec zombie = entity("minecraft:zombie", "");

        assertFalse(new EntitySpawns.Attempt(at, zombie, EntitySpawns.Surface.LAND, 0, 15, true).restrictsLight());
        assertTrue(new EntitySpawns.Attempt(at, zombie, EntitySpawns.Surface.LAND, 0, 7, true).restrictsLight());
        assertTrue(new EntitySpawns.Attempt(at, zombie, EntitySpawns.Surface.LAND, 8, 15, true).restrictsLight());
    }

    @Test
    void answersWithNothingAreEmpty() {
        assertTrue(BlockDrops.Result.NONE.drops().isEmpty());
        assertFalse(BlockDrops.Result.NONE.replacesVanillaDrops());
        assertTrue(SaplingGrowth.TreeObjects.NONE.objects().isEmpty());
    }

    @Test
    void anAmbientEffectKeepsItsCommandsApartAndRefusesPartsWithoutKeys() {
        List<AmbientEffects.Command> commands = new ArrayList<>(List.of(
                new AmbientEffects.Command(new EntitySpec.Command(List.of("say hi"), 0, false, 100), 1, 0, -1)));
        AmbientEffects.Effect effect = new AmbientEffects.Effect(null, null, commands, new AmbientEffects.Potion("luck", 0, 100));
        commands.clear();

        assertEquals(1, effect.commands().size());
        assertThrows(UnsupportedOperationException.class, () -> effect.commands().clear());
        assertThrows(NullPointerException.class, () -> new AmbientEffects.Sound(null, 0, 0, 0, 1, 1));
        assertThrows(NullPointerException.class, () -> new AmbientEffects.Command(null, 0, 0, 0));
    }

    private static EntitySpec entity(String type, String specialType) {
        return new EntitySpec(type, specialType, null, "", new EntitySpec.Flags(false, true, true, false, true, false, false,
                false, false, false, false), EntitySpec.Equipment.NONE, List.of(), List.of(), List.of(), 0L, null, null, false,
                null, null, false, List.of());
    }
}
