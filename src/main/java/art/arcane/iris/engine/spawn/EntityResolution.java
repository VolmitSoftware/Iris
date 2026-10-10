/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.engine.spawn;

import art.arcane.iris.builtin.loot.ItemAttributeModifier;
import art.arcane.iris.builtin.loot.LootEntry;
import art.arcane.iris.builtin.spawn.AmbientEffect;
import art.arcane.iris.builtin.spawn.EntityDefinition;
import art.arcane.iris.builtin.spawn.SpawnCommand;
import art.arcane.iris.engine.framework.LootResolver;
import art.arcane.iris.engine.loot.LootItems;
import art.arcane.iris.spi.PlatformWorld;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.entity.EntitySpec;
import com.volmit.item.ItemAttribute;
import com.volmit.item.ItemDrop;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns an entity of the pack into the {@link EntitySpec} the host spawns, making every random choice in the order Iris
 * always made them: the passengers, the attribute modifiers, the leash holder and then the six pieces of equipment.
 */
final class EntityResolution {
    private static final long PASSENGER_STREAM_BASE = 234858L;
    private static final long LEASH_HOLDER_STREAM = 234548L;

    private final LootItems items;
    private final PlatformWorld world;
    private final EffectTimers timers;

    /** @param world the world the entity appears in, whose time and weather decide which of its commands run */
    EntityResolution(LootItems items, PlatformWorld world, EffectTimers timers) {
        this.items = items;
        this.world = world;
        this.timers = timers;
    }

    EntitySpec resolve(EntityDefinition entity, RNG rng) {
        List<EntitySpec> passengers = new ArrayList<>();
        long passengerIndex = 0;
        for (EntityDefinition passenger : entity.passengers()) {
            passengers.add(resolve(passenger, rng.nextParallelRNG(PASSENGER_STREAM_BASE + passengerIndex++)));
        }

        List<ItemAttribute> attributes = drawAttributes(entity, rng);
        List<String> lootTables = List.copyOf(entity.loot().tables());
        long lootShuffleSeed = lootTables.isEmpty() ? 0L : rng.nextLong();
        EntitySpec leashHolder = entity.leashHolder() == null ? null : resolve(entity.leashHolder(), rng.nextParallelRNG(LEASH_HOLDER_STREAM));
        EntitySpec.Equipment equipment = new EntitySpec.Equipment(
                equip(entity.helmet(), rng), equip(entity.chestplate(), rng), equip(entity.leggings(), rng),
                equip(entity.boots(), rng), equip(entity.mainHand(), rng), equip(entity.offHand(), rng));

        return new EntitySpec(
                entity.type() == null ? "" : entity.type(),
                entity.specialType() == null ? "" : entity.specialType(),
                entity.reason(),
                entity.customName() == null ? "" : entity.customName(),
                flagsOf(entity), equipment, passengers, attributes, lootTables, lootShuffleSeed, leashHolder,
                spawnEffectOf(entity), entity.spawnEffectRiseOutOfGround(), entity.pandaMainGene(),
                entity.pandaHiddenGene(), entity.applySettingsToCustomMobAnyways(), commandsOf(entity));
    }

    private static EntitySpec.Flags flagsOf(EntityDefinition entity) {
        return new EntitySpec.Flags(entity.customNameVisible(), entity.aware(), entity.ai(), entity.glowing(),
                entity.gravity(), entity.invulnerable(), entity.silent(), entity.pickupItems(), entity.removable(),
                entity.keepEntity(), entity.baby());
    }

    private static List<ItemAttribute> drawAttributes(EntityDefinition entity, RNG rng) {
        List<ItemAttribute> attributes = new ArrayList<>();
        for (ItemAttributeModifier modifier : entity.attributes()) {
            if (rng.nextDouble() >= modifier.chance()) {
                continue;
            }
            String attribute = modifier.attribute();
            if (attribute == null || attribute.isBlank()) {
                continue;
            }
            attributes.add(new ItemAttribute(attribute, modifier.name(), modifier.operation(), rng.d(modifier.minAmount(), modifier.maxAmount())));
        }
        return attributes;
    }

    private ItemDrop equip(LootEntry piece, RNG rng) {
        if (piece == null || !LootResolver.oneIn(rng, piece.rarity())) {
            return null;
        }
        return items.give(piece, rng);
    }

    /** The entity's spawn effect when it shows this time: an effect only shows now and then, as its interval and chance say. */
    private EntitySpec.SpawnEffect spawnEffectOf(EntityDefinition entity) {
        AmbientEffect effect = entity.spawnEffect();
        if (effect == null || !timers.shouldPlayNow(effect)) {
            return null;
        }
        return new EntitySpec.SpawnEffect(effect.sound(), effect.soundDistance(), effect.minPitch(),
                effect.maxPitch(), effect.volume(), effect.particleEffect(), effect.particleOffset(),
                effect.particleCount(), effect.particleAltX(), effect.particleAltY(), effect.particleAltZ(),
                effect.randomAltX(), effect.randomAltY(), effect.randomAltZ(), effect.extra());
    }

    private List<EntitySpec.Command> commandsOf(EntityDefinition entity) {
        List<EntitySpec.Command> commands = new ArrayList<>();
        for (SpawnCommand command : entity.rawCommands()) {
            if (SpawnConditions.allow(command.timeBlock(), command.weather(), world)) {
                commands.add(new EntitySpec.Command(command.commands(), command.delay(), command.repeat(), command.repeatDelay()));
            }
        }
        return commands;
    }
}
