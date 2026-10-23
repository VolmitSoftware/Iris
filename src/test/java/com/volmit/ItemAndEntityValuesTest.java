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
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

/** The values services hand the host are immutable and refuse what the host could not create. */
class ItemAndEntityValuesTest {
    @Test
    void anItemDropRefusesNothingToDropAndKeepsItsListsApart() {
        assertThrows(IllegalArgumentException.class, () -> ItemDrop.of("stick", 0));

        List<ItemEnchantment> enchantments = new ArrayList<>(List.of(new ItemEnchantment("sharpness", 3)));
        ItemDrop drop = new ItemDrop("diamond_sword", 1, Optional.empty(), List.of(), enchantments, List.of(), false, List.of(),
                OptionalInt.empty(), OptionalDouble.of(0.25), Optional.empty(), Optional.empty(), Map.of(), Optional.empty());
        enchantments.clear();

        assertEquals(1, drop.enchantments().size());
        assertThrows(UnsupportedOperationException.class, () -> drop.enchantments().clear());
        assertThrows(IllegalArgumentException.class, () -> new ItemEnchantment("sharpness", 0));
    }

    @Test
    void absentValuesAreEmptyNeverNull() {
        ItemDrop stick = ItemDrop.of("stick", 1);

        assertTrue(stick.displayName().isEmpty());
        assertTrue(stick.damage().isEmpty());
        assertTrue(stick.source().isEmpty());
        assertThrows(NullPointerException.class, () -> new ItemDrop("stick", 1, null, List.of(), List.of(), List.of(), false,
                List.of(), OptionalInt.empty(), OptionalDouble.empty(), Optional.empty(), Optional.empty(), Map.of(), Optional.empty()));
        assertThrows(NullPointerException.class, () -> new AmbientEffects.Effect(null, Optional.empty(), List.of(), Optional.empty()));
        assertThrows(NullPointerException.class, () -> new EntitySpawns.MarkerRule(null, false, false));
    }

    @Test
    void anEntityKnowsWhetherItIsAnotherPluginsMob() {
        EntitySpec zombie = entity(Optional.of("minecraft:zombie"), Optional.empty());
        EntitySpec special = entity(Optional.empty(), Optional.of("mythic:dragon"));

        assertFalse(zombie.isSpecial());
        assertTrue(special.isSpecial());
    }

    @Test
    void anAttemptSaysWhetherLightMatters() {
        BlockPosition at = new BlockPosition(1, 2, 3);
        EntitySpec zombie = entity(Optional.of("minecraft:zombie"), Optional.empty());

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
        AmbientEffects.Effect effect = new AmbientEffects.Effect(Optional.empty(), Optional.empty(), commands,
                Optional.of(new AmbientEffects.Potion("luck", 0, 100)));
        commands.clear();

        assertEquals(1, effect.commands().size());
        assertThrows(UnsupportedOperationException.class, () -> effect.commands().clear());
        assertThrows(NullPointerException.class, () -> new AmbientEffects.Sound(null, 0, 0, 0, 1, 1));
        assertThrows(NullPointerException.class, () -> new AmbientEffects.Command(null, 0, 0, 0));
    }

    private static EntitySpec entity(Optional<String> type, Optional<String> specialType) {
        return new EntitySpec(type, specialType, Optional.empty(), Optional.empty(), new EntitySpec.Flags(false, true, true, false,
                true, false, false, false, false, false, false), EntitySpec.Equipment.NONE, List.of(), List.of(), List.of(), 0L,
                Optional.empty(), Optional.empty(), false, Optional.empty(), Optional.empty(), false, List.of());
    }
}
