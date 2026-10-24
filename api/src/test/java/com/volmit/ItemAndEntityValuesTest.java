package com.volmit;

import com.volmit.entity.EntitySpec;
import com.volmit.geometry.BlockPosition;
import com.volmit.item.ItemDrop;
import com.volmit.item.ItemEnchantment;
import com.volmit.resource.ContentKey;
import com.volmit.resource.ResourceId;
import com.volmit.service.AmbientEffects;
import com.volmit.service.BlockDrops;
import com.volmit.service.EntitySpawns;
import com.volmit.service.SaplingGrowth;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

/** The values services hand the host are immutable and refuse what the host could not create. */
class ItemAndEntityValuesTest {
    private static final ContentKey STICK = new ContentKey.Registered(ResourceId.parse("minecraft:stick"));
    private static final ResourceId SHARPNESS = ResourceId.parse("minecraft:sharpness");

    @Test
    void anItemDropRefusesNothingToDropAndKeepsItsListsApart() {
        assertThrows(IllegalArgumentException.class, () -> ItemDrop.of(STICK, 0));

        List<ItemEnchantment> enchantments = new ArrayList<>(List.of(new ItemEnchantment(SHARPNESS, 3)));
        ItemDrop drop = new ItemDrop(STICK, 1, ItemDrop.Look.PLAIN, enchantments, List.of(),
                new ItemDrop.Wear(false, OptionalDouble.of(0.25)), ItemDrop.PlatformExtras.NONE, Optional.empty());
        enchantments.clear();

        assertEquals(1, drop.enchantments().size());
        assertThrows(UnsupportedOperationException.class, () -> drop.enchantments().clear());
        assertThrows(IllegalArgumentException.class, () -> new ItemEnchantment(SHARPNESS, 0));
    }

    @Test
    void absentValuesAreEmptyNeverNull() {
        ItemDrop stick = ItemDrop.of(STICK, 1);

        assertTrue(stick.look().displayName().isEmpty());
        assertTrue(stick.wear().damage().isEmpty());
        assertTrue(stick.source().isEmpty());
        assertThrows(NullPointerException.class, () -> new ItemDrop(STICK, 1, null, List.of(), List.of(), ItemDrop.Wear.NEW,
                ItemDrop.PlatformExtras.NONE, Optional.empty()));
        assertThrows(NullPointerException.class, () -> new AmbientEffects.Effect(null, Optional.empty(), List.of(), Optional.empty()));
        assertThrows(NullPointerException.class, () -> new EntitySpawns.MarkerRule(null, false, false));
    }

    @Test
    void aProvidedKeyKeepsTheProvidersNameAsWrittenAndRefusesABlankOne() {
        ContentKey mob = new ContentKey.Provided("mmoitems_SWORD", "EXCALIBUR:Gold");

        assertEquals("mmoitems_SWORD:EXCALIBUR:Gold", mob.toString());
        assertThrows(IllegalArgumentException.class, () -> new ContentKey.Provided("mythicmobs", " "));
    }

    @Test
    void aPlatformKeyAsPacksWriteItBecomesAMinecraftIdUnlessItNamesANamespace() {
        assertEquals(ResourceId.parse("minecraft:diamond_sword"), ResourceId.ofPlatformKey(" DIAMOND_SWORD "));
        assertEquals(ResourceId.parse("minecraft:fire_protection"), ResourceId.ofPlatformKey("Fire Protection"));
        assertEquals(ResourceId.parse("mymod:vorpal"), ResourceId.ofPlatformKey("mymod:vorpal"));
        assertThrows(IllegalArgumentException.class, () -> ResourceId.ofPlatformKey("a:b:c"));
    }

    @Test
    void anEntityHandsAHostTheExtensionItReadsAndNothingForAnotherType() {
        EntitySpec zombie = entity(List.of("iris extras"));

        assertEquals(Optional.of("iris extras"), zombie.extension(String.class));
        assertEquals(Optional.empty(), zombie.extension(Integer.class));
    }

    @Test
    void anAttemptSaysWhetherLightMatters() {
        BlockPosition at = new BlockPosition(1, 2, 3);
        EntitySpec zombie = entity(List.of());

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
                Optional.of(new AmbientEffects.Potion(ResourceId.parse("minecraft:luck"), 0, 100)));
        commands.clear();

        assertEquals(1, effect.commands().size());
        assertThrows(UnsupportedOperationException.class, () -> effect.commands().clear());
        assertThrows(NullPointerException.class, () -> new AmbientEffects.Sound(null, 0, 0, 0, 1, 1));
        assertThrows(NullPointerException.class, () -> new AmbientEffects.Command(null, 0, 0, 0));
    }

    private static EntitySpec entity(List<Object> extensions) {
        return new EntitySpec(new ContentKey.Registered(ResourceId.parse("minecraft:zombie")), EntitySpec.Appearance.PLAIN,
                EntitySpec.Behaviour.NATURAL, EntitySpec.Equipment.NONE, List.of(), EntitySpec.Company.NONE,
                EntitySpec.Drops.PLATFORM, EntitySpec.Arrival.QUIET, extensions);
    }
}
