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

import art.arcane.iris.builtin.LiveWorld.Sky;
import art.arcane.iris.builtin.loot.ItemAttributeModifier;
import art.arcane.iris.builtin.loot.LootEntry;
import art.arcane.iris.builtin.spawn.AmbientEffect;
import art.arcane.iris.builtin.spawn.EntityDefinition;
import art.arcane.iris.builtin.spawn.SpawnCommand;
import art.arcane.iris.builtin.loot.LootResolver;
import art.arcane.iris.engine.loot.LootItems;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.entity.EntitySpec;
import com.volmit.item.ItemAttribute;
import com.volmit.item.ItemDrop;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns an entity of the pack into the {@link EntitySpec} the host spawns, making every random choice in the order Iris
 * always made them: the passengers, the attribute modifiers, the leash holder and then the six pieces of equipment.
 */
final class EntityResolution {
    private static final long PASSENGER_STREAM_BASE = 234858L;
    private static final long LEASH_HOLDER_STREAM = 234548L;

    private final PackResources resources;
    private final LootItems items;
    private final Optional<Sky> sky;
    private final EffectTimers timers;

    /**
     * @param resources names the entity's loot tables by id
     * @param sky       the sky of the world the entity appears in, whose time and weather decide which of its commands run; none while the world has no platform world
     */
    EntityResolution(PackResources resources, LootItems items, Optional<Sky> sky, EffectTimers timers) {
        this.resources = resources;
        this.items = items;
        this.sky = sky;
        this.timers = timers;
    }

    EntitySpec resolve(EntityDefinition entity, RNG rng) {
        List<EntitySpec> passengers = new ArrayList<>();
        long passengerIndex = 0;
        for (EntityDefinition passenger : entity.passengers()) {
            passengers.add(resolve(passenger, rng.nextParallelRNG(PASSENGER_STREAM_BASE + passengerIndex++)));
        }

        List<ItemAttribute> attributes = drawAttributes(entity, rng);
        List<ResourceId> lootTables = entity.loot().tables().stream().map(resources::idOf).toList();
        long lootShuffleSeed = lootTables.isEmpty() ? 0L : rng.nextLong();
        Optional<EntitySpec> leashHolder = entity.leashHolder() == null
                ? Optional.empty()
                : Optional.of(resolve(entity.leashHolder(), rng.nextParallelRNG(LEASH_HOLDER_STREAM)));
        EntitySpec.Equipment equipment = new EntitySpec.Equipment(
                equip(entity.helmet(), rng), equip(entity.chestplate(), rng), equip(entity.leggings(), rng),
                equip(entity.boots(), rng), equip(entity.mainHand(), rng), equip(entity.offHand(), rng));

        return new EntitySpec(textOf(entity.type()), textOf(entity.specialType()), textOf(entity.reason()),
                textOf(entity.customName()), flagsOf(entity), equipment, passengers, attributes, lootTables, lootShuffleSeed,
                leashHolder, spawnEffectOf(entity), entity.spawnEffectRiseOutOfGround(), textOf(entity.pandaMainGene()),
                textOf(entity.pandaHiddenGene()), entity.applySettingsToCustomMobAnyways(), commandsOf(entity));
    }

    /** A text the pack may leave out or blank, as absent then. */
    private static Optional<String> textOf(String text) {
        return text == null || text.isBlank() ? Optional.empty() : Optional.of(text);
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

    private Optional<ItemDrop> equip(LootEntry piece, RNG rng) {
        if (piece == null || !LootResolver.oneIn(rng, piece.rarity())) {
            return Optional.empty();
        }
        return Optional.ofNullable(items.give(piece, rng));
    }

    /** The entity's spawn effect when it shows this time: an effect only shows now and then, as its interval and chance say. */
    private Optional<EntitySpec.SpawnEffect> spawnEffectOf(EntityDefinition entity) {
        AmbientEffect effect = entity.spawnEffect();
        if (effect == null || !timers.shouldPlayNow(effect)) {
            return Optional.empty();
        }
        return Optional.of(new EntitySpec.SpawnEffect(textOf(effect.sound()), effect.soundDistance(), effect.minPitch(),
                effect.maxPitch(), effect.volume(), textOf(effect.particleEffect()), effect.particleOffset(),
                effect.particleCount(), effect.particleAltX(), effect.particleAltY(), effect.particleAltZ(),
                effect.randomAltX(), effect.randomAltY(), effect.randomAltZ(), effect.extra()));
    }

    private List<EntitySpec.Command> commandsOf(EntityDefinition entity) {
        List<EntitySpec.Command> commands = new ArrayList<>();
        for (SpawnCommand command : entity.rawCommands()) {
            if (sky.isPresent() && SpawnConditions.allow(command.timeBlock(), command.weather(), sky.get())) {
                commands.add(new EntitySpec.Command(command.commands(), command.delay(), command.repeat(), command.repeatDelay()));
            }
        }
        return commands;
    }
}
