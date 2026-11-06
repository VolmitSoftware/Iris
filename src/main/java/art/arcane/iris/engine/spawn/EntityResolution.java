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

import art.arcane.iris.builtin.loot.LootEntry;
import art.arcane.iris.builtin.spawn.AmbientEffect;
import art.arcane.iris.builtin.spawn.EntityDefinition;
import art.arcane.iris.builtin.spawn.SpawnCommand;
import art.arcane.iris.builtin.loot.LootResolver;
import art.arcane.iris.engine.loot.LootItems;
import art.arcane.iris.engine.loot.PlatformKeys;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.ModuleLog;
import com.volmit.entity.EntitySpec;
import com.volmit.item.ItemAttribute;
import com.volmit.item.ItemDrop;
import com.volmit.resource.ContentKey;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.world.RunningWorld.Sky;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns an entity of the pack into the {@link EntitySpec} the host spawns, making every random choice in the order Iris
 * always made them: the passengers, the attribute modifiers, the leash holder and then the six pieces of equipment. An
 * entity that names no type the host could spawn resolves to nothing, after its random choices, so that the ones after
 * it stay what they were.
 */
final class EntityResolution {
    private static final long PASSENGER_STREAM_BASE = 234858L;
    private static final long LEASH_HOLDER_STREAM = 234548L;

    private final PackResources resources;
    private final LootItems items;
    private final Optional<Sky> sky;
    private final EffectTimers timers;
    private final ModuleLog log;

    /**
     * @param resources names the entity's loot tables by id
     * @param sky       the sky of the world the entity appears in, whose time and weather decide which of its commands run; none while the world has no platform world
     * @param log       hears of keys that name nothing the platform could have
     */
    EntityResolution(PackResources resources, LootItems items, Optional<Sky> sky, EffectTimers timers, ModuleLog log) {
        this.resources = resources;
        this.items = items;
        this.sky = sky;
        this.timers = timers;
        this.log = log;
    }

    Optional<EntitySpec> resolve(EntityDefinition entity, RNG rng) {
        List<EntitySpec> passengers = new ArrayList<>();
        long passengerIndex = 0;
        for (EntityDefinition passenger : entity.passengers()) {
            resolve(passenger, rng.nextParallelRNG(PASSENGER_STREAM_BASE + passengerIndex++)).ifPresent(passengers::add);
        }

        List<ItemAttribute> attributes = items.attributes(entity.attributes(), rng);
        List<ResourceId> lootTables = entity.loot().tables().stream().map(resources::idOf).toList();
        long lootShuffleSeed = lootTables.isEmpty() ? 0L : rng.nextLong();
        Optional<EntitySpec> leashHolder = entity.leashHolder() == null
                ? Optional.empty()
                : resolve(entity.leashHolder(), rng.nextParallelRNG(LEASH_HOLDER_STREAM));
        EntitySpec.Equipment equipment = new EntitySpec.Equipment(
                equip(entity.helmet(), rng), equip(entity.chestplate(), rng), equip(entity.leggings(), rng),
                equip(entity.boots(), rng), equip(entity.mainHand(), rng), equip(entity.offHand(), rng));

        return typeOf(entity).map(type -> new EntitySpec(type, appearanceOf(entity), behaviourOf(entity), equipment, attributes,
                new EntitySpec.Company(passengers, leashHolder), new EntitySpec.Drops(lootTables, lootShuffleSeed),
                arrivalOf(entity), entity.applySettingsToCustomMobAnyways()));
    }

    /** The provider's mob when the pack names one, otherwise the entity type. */
    private Optional<ContentKey> typeOf(EntityDefinition entity) {
        Optional<String> provided = textOf(entity.specialType());
        if (provided.isPresent()) {
            return PlatformKeys.provided(provided.get(), log);
        }
        return PlatformKeys.id(entity.type(), log).map(ContentKey.Registered::new);
    }

    /** A text the pack may leave out or blank, as absent then. */
    private static Optional<String> textOf(String text) {
        return text == null || text.isBlank() ? Optional.empty() : Optional.of(text);
    }

    private static EntitySpec.Appearance appearanceOf(EntityDefinition entity) {
        return new EntitySpec.Appearance(textOf(entity.customName()), entity.customNameVisible(), entity.glowing(), entity.baby(),
                textOf(entity.pandaMainGene()), textOf(entity.pandaHiddenGene()));
    }

    private EntitySpec.Arrival arrivalOf(EntityDefinition entity) {
        return new EntitySpec.Arrival(spawnEffectOf(entity), commandsOf(entity), textOf(entity.reason()),
                entity.spawnEffectRiseOutOfGround());
    }

    private static EntitySpec.Behaviour behaviourOf(EntityDefinition entity) {
        return new EntitySpec.Behaviour(entity.aware(), entity.ai(), entity.gravity(), entity.invulnerable(), entity.silent(),
                entity.pickupItems(), entity.removable(), entity.keepEntity());
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
        Optional<EntitySpec.SpawnEffect.Sound> sound = PlatformKeys.id(effect.sound(), log).map(key ->
                new EntitySpec.SpawnEffect.Sound(key, effect.soundDistance(), effect.minPitch(), effect.maxPitch(), effect.volume()));
        Optional<EntitySpec.SpawnEffect.Burst> particles = PlatformKeys.id(effect.particleEffect(), log).map(key ->
                new EntitySpec.SpawnEffect.Burst(key, effect.particleOffset(), effect.particleCount(), motionOf(effect), effect.extra()));
        return Optional.of(new EntitySpec.SpawnEffect(sound, particles));
    }

    private static EntitySpec.SpawnEffect.Motion motionOf(AmbientEffect effect) {
        return new EntitySpec.SpawnEffect.Motion(effect.particleAltX(), effect.particleAltY(), effect.particleAltZ(),
                effect.randomAltX(), effect.randomAltY(), effect.randomAltZ());
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
