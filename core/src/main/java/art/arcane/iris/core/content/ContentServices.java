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

package art.arcane.iris.core.content;

import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.core.modules.ModuleServices;
import art.arcane.iris.core.modules.PlatformBlockStates;
import art.arcane.iris.engine.framework.Engine;
import com.volmit.block.BlockState;
import com.volmit.entity.EntitySpec;
import com.volmit.item.ItemAttribute;
import com.volmit.item.ItemDrop;
import com.volmit.item.ItemEnchantment;
import com.volmit.resource.ResourceId;
import com.volmit.service.AmbientEffects;
import com.volmit.service.BlockDrops;
import com.volmit.service.EntitySpawns;
import com.volmit.service.Loot;
import com.volmit.geometry.BlockPosition;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the pack's modules say about loot, block drops, spawns and ambient effects, for adapters that cannot name IrisApi
 * types: the questions take plain values and the answers are the host types of this package. The modded adapters, which
 * fill containers, drop blocks, spawn mobs and play effects themselves, ask here instead of reading the pack's classes.
 */
public final class ContentServices {
    private ContentServices() {
    }

    /**
     * The sources of the loot of the container at {@code x}, {@code worldY}, {@code z}, in the order their items are added.
     *
     * @param containerKey the platform's key for the block when it is a storage chest an object may have given loot, else null
     */
    public static List<ContentLoot.Part> containerLoot(Engine engine, int x, int worldY, int z, String containerKey, boolean nativeLootDefined) {
        Optional<BlockState> container = containerKey == null
                ? Optional.empty()
                : Optional.of(BlockState.parse(PlatformBlockStates.namespaced(containerKey)));
        Loot.Site site = new Loot.Site(position(engine, x, worldY, z), container, nativeLootDefined, false);
        List<ContentLoot.Part> parts = new ArrayList<>();
        for (Loot.Source source : ModuleServices.lootTables(engine, site)) {
            switch (source) {
                case Loot.Table table -> parts.add(new ContentLoot.Items(tableItems(engine, x, worldY, z, table.id())));
                case Loot.PlatformTable table -> parts.add(new ContentLoot.PlatformTable(table.key(), table.seed()));
            }
        }
        return parts;
    }

    /** The items the pack's loot table {@code tableKey} gives at the spot. */
    public static List<ContentItem> tableItems(Engine engine, int x, int worldY, int z, String tableKey) {
        return tableItems(engine, x, worldY, z, engine.getData().getPackResources().idOf(tableKey));
    }

    private static List<ContentItem> tableItems(Engine engine, int x, int worldY, int z, ResourceId table) {
        Loot.Site site = new Loot.Site(position(engine, x, worldY, z), Optional.empty(), false, false);
        return ModuleServices.lootItems(engine, site, table).stream().map(ContentServices::itemOf).toList();
    }

    /** What the modules drop for the block with the platform's key {@code blockKey} broken at the spot. */
    public static ContentLoot.BlockDrops blockDrops(Engine engine, int x, int worldY, int z, String blockKey) {
        BlockDrops.Result result = ModuleServices.blockDrops(engine, new BlockDrops.Broken(position(engine, x, worldY, z),
                BlockState.parse(PlatformBlockStates.namespaced(blockKey))));
        return new ContentLoot.BlockDrops(result.drops().stream().map(ContentServices::itemOf).toList(), result.replacesVanillaDrops());
    }

    /** What the first module with something to spawn in the chunk plans. */
    public static Optional<ContentSpawns.Plan> planChunkSpawn(Engine engine, int chunkX, int chunkZ, int livingEntities, boolean initial) {
        return planned(engine, new EntitySpawns.Chunk(chunkX, chunkZ, livingEntities, initial));
    }

    /** What the first module that knows the spawner plans at the marker block. */
    public static Optional<ContentSpawns.Plan> planMarkerSpawn(Engine engine, int x, int worldY, int z, String marker, String spawner, boolean initial) {
        ResourceId spawnerId = engine.getData().getPackResources().idOf(spawner);
        return planned(engine, new EntitySpawns.Marker(position(engine, x, worldY, z), marker, spawnerId, initial));
    }

    /** What the marker with the tag {@code tag} does; empty for a tag no module knows. */
    public static Optional<ContentSpawns.MarkerRule> markerRule(Engine engine, String tag) {
        IrisPackResources resources = engine.getData().getPackResources();
        return ModuleServices.markerRule(engine, tag)
                .map(rule -> new ContentSpawns.MarkerRule(rule.spawners().stream().map(resources::keyOf).toList(),
                        rule.emptyAbove(), rule.removeOnChange()));
    }

    /** The entity of the pack named {@code key} as a module would spawn it; empty for an unknown key. */
    public static Optional<ContentEntity> entity(Engine engine, String key) {
        return ModuleServices.entity(engine, key).map(ContentServices::entityOf);
    }

    /** The ambient effects due for a player standing at the spot. */
    public static List<ContentEffect> ambientEffects(Engine engine, int x, int worldY, int z) {
        return ModuleServices.ambientEffects(engine, position(engine, x, worldY, z)).stream().map(ContentServices::effectOf).toList();
    }

    private static Optional<ContentSpawns.Plan> planned(Engine engine, EntitySpawns.Site site) {
        return ModuleServices.planSpawn(engine, site).map(planned -> new ContentSpawns.Plan(planned, attemptsOf(engine, planned.plan())));
    }

    private static List<ContentSpawns.Attempt> attemptsOf(Engine engine, EntitySpawns.Plan plan) {
        List<ContentSpawns.Attempt> attempts = new ArrayList<>();
        for (EntitySpawns.Attempt attempt : plan.attempts()) {
            attempts.add(new ContentSpawns.Attempt(attempt.at().x(), attempt.at().y() + engine.getMinHeight(), attempt.at().z(),
                    entityOf(attempt.entity()), ContentSpawns.surfaceOf(attempt.surface()), attempt.minLight(), attempt.maxLight(),
                    attempt.checkSurface()));
        }
        return attempts;
    }

    private static BlockPosition position(Engine engine, int x, int worldY, int z) {
        return new BlockPosition(x, worldY - engine.getMinHeight(), z);
    }

    private static ContentItem itemOf(ItemDrop drop) {
        return new ContentItem(drop.item(), drop.amount(), drop.displayName().orElse(null), drop.lore(),
                drop.enchantments().stream().map(ContentServices::enchantmentOf).toList(),
                drop.attributes().stream().map(ContentServices::attributeOf).toList(), drop.unbreakable(), drop.flags(),
                drop.customModel().isPresent() ? drop.customModel().getAsInt() : null,
                drop.damage().isPresent() ? drop.damage().getAsDouble() : null,
                drop.leatherColor().orElse(null), drop.dyeColor().orElse(null), drop.customNbt());
    }

    private static ContentItem.Enchantment enchantmentOf(ItemEnchantment enchantment) {
        return new ContentItem.Enchantment(enchantment.key(), enchantment.level());
    }

    private static ContentItem.Attribute attributeOf(ItemAttribute attribute) {
        return new ContentItem.Attribute(attribute.attribute(), attribute.name(), attribute.operation(), attribute.amount());
    }

    /**
     * The adapters' entity keeps their conventions: a missing type, special type or name is empty text, anything else
     * missing is {@code null}. Loot tables are named by their ids' paths, which is how the adapters ask
     * {@link #tableItems} for them again.
     */
    private static ContentEntity entityOf(EntitySpec spec) {
        return new ContentEntity(spec.type().orElse(""), spec.specialType().orElse(""), spec.reason().orElse(null),
                spec.customName().orElse(""), flagsOf(spec.flags()),
                equipmentOf(spec.equipment()), spec.passengers().stream().map(ContentServices::entityOf).toList(),
                spec.attributes().stream().map(ContentServices::attributeOf).toList(),
                spec.lootTables().stream().map(ResourceId::path).toList(), spec.lootShuffleSeed(),
                spec.leashHolder().map(ContentServices::entityOf).orElse(null), spec.spawnEffect().map(ContentServices::spawnEffectOf).orElse(null),
                spec.riseOutOfGround(), spec.pandaMainGene().orElse(null), spec.pandaHiddenGene().orElse(null), spec.applySettingsToSpecial(),
                spec.commands().stream().map(ContentServices::commandOf).toList());
    }

    private static ContentEntity.Flags flagsOf(EntitySpec.Flags flags) {
        return new ContentEntity.Flags(flags.customNameVisible(), flags.aware(), flags.ai(), flags.glowing(), flags.gravity(),
                flags.invulnerable(), flags.silent(), flags.pickupItems(), flags.removable(), flags.keepEntity(), flags.baby());
    }

    private static ContentEntity.Equipment equipmentOf(EntitySpec.Equipment equipment) {
        return new ContentEntity.Equipment(optionalItem(equipment.helmet()), optionalItem(equipment.chestplate()),
                optionalItem(equipment.leggings()), optionalItem(equipment.boots()), optionalItem(equipment.mainHand()),
                optionalItem(equipment.offHand()));
    }

    private static ContentItem optionalItem(Optional<ItemDrop> drop) {
        return drop.map(ContentServices::itemOf).orElse(null);
    }

    private static ContentEntity.SpawnEffect spawnEffectOf(EntitySpec.SpawnEffect effect) {
        return new ContentEntity.SpawnEffect(effect.sound().orElse(null), effect.soundDistance(), effect.minPitch(), effect.maxPitch(),
                effect.volume(), effect.particle().orElse(null), effect.particleOffset(), effect.particleCount(), effect.altX(), effect.altY(),
                effect.altZ(), effect.randomAltX(), effect.randomAltY(), effect.randomAltZ(), effect.extra());
    }

    private static ContentEntity.Command commandOf(EntitySpec.Command command) {
        return new ContentEntity.Command(command.commands(), command.delay(), command.repeat(), command.repeatDelay());
    }

    private static ContentEffect effectOf(AmbientEffects.Effect effect) {
        return new ContentEffect(effect.sound().map(ContentServices::soundOf).orElse(null),
                effect.particles().map(ContentServices::particlesOf).orElse(null),
                effect.commands().stream().map(command -> new ContentEffect.Command(commandOf(command.command()),
                        command.offsetX(), command.offsetY(), command.offsetZ())).toList(),
                effect.potion().map(potion -> new ContentEffect.Potion(potion.key(), potion.strength(), potion.ticks())).orElse(null));
    }

    private static ContentEffect.Sound soundOf(AmbientEffects.Sound sound) {
        return new ContentEffect.Sound(sound.key(), sound.offsetX(), sound.offsetY(), sound.offsetZ(),
                sound.volume(), sound.pitch());
    }

    private static ContentEffect.Particles particlesOf(AmbientEffects.Particles particles) {
        return new ContentEffect.Particles(particles.key(), particles.forward(), particles.sideways(),
                particles.jitterX(), particles.jitterZ(), particles.lift(), particles.count(), particles.altX(), particles.altY(),
                particles.altZ(), particles.extra());
    }
}
