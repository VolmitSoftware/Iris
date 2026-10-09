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

import art.arcane.iris.engine.framework.LootResolver;
import art.arcane.iris.engine.loot.LootItems;
import art.arcane.iris.engine.object.entity.IrisAttributeModifier;
import art.arcane.iris.engine.object.entity.IrisEffect;
import art.arcane.iris.engine.object.entity.IrisEntity;
import art.arcane.iris.engine.object.loot.IrisLoot;
import art.arcane.iris.engine.object.marker.IrisCommand;
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

    /** @param world the world the entity appears in, whose time and weather decide which of its commands run */
    EntityResolution(LootItems items, PlatformWorld world) {
        this.items = items;
        this.world = world;
    }

    EntitySpec resolve(IrisEntity entity, RNG rng) {
        List<EntitySpec> passengers = new ArrayList<>();
        long passengerIndex = 0;
        for (IrisEntity passenger : entity.getPassengers()) {
            passengers.add(resolve(passenger, rng.nextParallelRNG(PASSENGER_STREAM_BASE + passengerIndex++)));
        }

        List<ItemAttribute> attributes = drawAttributes(entity, rng);
        List<String> lootTables = List.copyOf(entity.getLoot().getTables());
        long lootShuffleSeed = lootTables.isEmpty() ? 0L : rng.nextLong();
        EntitySpec leashHolder = entity.getLeashHolder() == null ? null : resolve(entity.getLeashHolder(), rng.nextParallelRNG(LEASH_HOLDER_STREAM));
        EntitySpec.Equipment equipment = new EntitySpec.Equipment(
                equip(entity.getHelmet(), rng), equip(entity.getChestplate(), rng), equip(entity.getLeggings(), rng),
                equip(entity.getBoots(), rng), equip(entity.getMainHand(), rng), equip(entity.getOffHand(), rng));

        return new EntitySpec(
                entity.getType() == null ? "" : entity.getType(),
                entity.getSpecialType() == null ? "" : entity.getSpecialType(),
                entity.getReason(),
                entity.getCustomName() == null ? "" : entity.getCustomName(),
                flagsOf(entity), equipment, passengers, attributes, lootTables, lootShuffleSeed, leashHolder,
                spawnEffectOf(entity), entity.isSpawnEffectRiseOutOfGround(), entity.getPandaMainGene(),
                entity.getPandaHiddenGene(), entity.isApplySettingsToCustomMobAnyways(), commandsOf(entity));
    }

    private static EntitySpec.Flags flagsOf(IrisEntity entity) {
        return new EntitySpec.Flags(entity.isCustomNameVisible(), entity.isAware(), entity.isAi(), entity.isGlowing(),
                entity.isGravity(), entity.isInvulnerable(), entity.isSilent(), entity.isPickupItems(), entity.isRemovable(),
                entity.isKeepEntity(), entity.isBaby());
    }

    private static List<ItemAttribute> drawAttributes(IrisEntity entity, RNG rng) {
        List<ItemAttribute> attributes = new ArrayList<>();
        for (IrisAttributeModifier modifier : entity.getAttributes()) {
            if (rng.nextDouble() >= modifier.getChance()) {
                continue;
            }
            String attribute = modifier.getAttribute();
            if (attribute == null || attribute.isBlank()) {
                continue;
            }
            attributes.add(new ItemAttribute(attribute, modifier.getName(), modifier.getOperation(), modifier.getAmount(rng)));
        }
        return attributes;
    }

    private ItemDrop equip(IrisLoot piece, RNG rng) {
        if (piece == null || !LootResolver.oneIn(rng, piece.getRarity())) {
            return null;
        }
        return items.give(piece, rng);
    }

    /** The entity's spawn effect when it shows this time: an effect only shows now and then, as its interval and chance say. */
    private static EntitySpec.SpawnEffect spawnEffectOf(IrisEntity entity) {
        IrisEffect effect = entity.getSpawnEffect();
        if (effect == null || !effect.shouldApplyNow()) {
            return null;
        }
        return new EntitySpec.SpawnEffect(effect.getSoundKey(), effect.getSoundDistance(), effect.getMinPitch(),
                effect.getMaxPitch(), effect.getVolume(), effect.getParticleEffectKey(), effect.getParticleOffset(),
                effect.getParticleCount(), effect.getParticleAltX(), effect.getParticleAltY(), effect.getParticleAltZ(),
                effect.isRandomAltX(), effect.isRandomAltY(), effect.isRandomAltZ(), effect.getExtra());
    }

    private List<EntitySpec.Command> commandsOf(IrisEntity entity) {
        List<EntitySpec.Command> commands = new ArrayList<>();
        for (IrisCommand command : entity.getRawCommands()) {
            if (command.isValid(world)) {
                commands.add(new EntitySpec.Command(command.getCommands(), command.getDelay(), command.isRepeat(), command.getRepeatDelay()));
            }
        }
        return commands;
    }
}
