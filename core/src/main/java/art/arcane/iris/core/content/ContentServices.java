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
import com.volmit.resource.ContentKey;
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
    private static final String LEATHER_COLOR_FORMAT = "#%06x";

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
                case Loot.PlatformTable table -> parts.add(new ContentLoot.PlatformTable(table.table().toString(), table.seed()));
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
        IrisPackResources resources = engine.getData().getPackResources();
        return planned(engine, new EntitySpawns.Marker(position(engine, x, worldY, z), resources.idOf(marker), resources.idOf(spawner), initial));
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

    /** The adapters' item names everything by text: keys as written by their ids, the leather colour as {@code #rrggbb}. */
    private static ContentItem itemOf(ItemDrop drop) {
        ItemDrop.Look look = drop.look();
        return new ContentItem(drop.item().toString(), drop.amount(), look.displayName().orElse(null), look.lore(),
                drop.enchantments().stream().map(ContentServices::enchantmentOf).toList(),
                drop.attributes().stream().map(ContentServices::attributeOf).toList(), drop.wear().unbreakable(), drop.extras().flags(),
                look.customModel().isPresent() ? look.customModel().getAsInt() : null,
                drop.wear().damage().isPresent() ? drop.wear().damage().getAsDouble() : null,
                look.leatherColor().isPresent() ? String.format(LEATHER_COLOR_FORMAT, look.leatherColor().getAsInt()) : null,
                look.dyeColor().map(Enum::name).orElse(null), drop.extras().customNbt());
    }

    private static ContentItem.Enchantment enchantmentOf(ItemEnchantment enchantment) {
        return new ContentItem.Enchantment(enchantment.key().toString(), enchantment.level());
    }

    private static ContentItem.Attribute attributeOf(ItemAttribute attribute) {
        return new ContentItem.Attribute(attribute.attribute().toString(), attribute.name(), attribute.operation().name(), attribute.amount());
    }

    /**
     * The adapters' entity keeps their conventions: a missing type, special type or name is empty text, anything else
     * missing is {@code null}. Loot tables are named by their ids' paths, which is how the adapters ask
     * {@link #tableItems} for them again.
     */
    private static ContentEntity entityOf(EntitySpec spec) {
        String type = spec.type() instanceof ContentKey.Registered registered ? registered.id().toString() : "";
        String provided = spec.type() instanceof ContentKey.Provided mob ? mob.toString() : "";
        return new ContentEntity(type, provided, spec.arrival().reason().orElse(null),
                spec.appearance().customName().orElse(""), flagsOf(spec),
                equipmentOf(spec.equipment()), spec.company().passengers().stream().map(ContentServices::entityOf).toList(),
                spec.attributes().stream().map(ContentServices::attributeOf).toList(),
                spec.drops().lootTables().stream().map(ResourceId::path).toList(), spec.drops().shuffleSeed(),
                spec.company().leashHolder().map(ContentServices::entityOf).orElse(null),
                spec.arrival().effect().map(ContentServices::spawnEffectOf).orElse(null),
                spec.arrival().risesFromGround(), spec.appearance().pandaMainGene().orElse(null),
                spec.appearance().pandaHiddenGene().orElse(null),
                spec.adjustsProvidedMob(), spec.arrival().commands().stream().map(ContentServices::commandOf).toList());
    }

    private static ContentEntity.Flags flagsOf(EntitySpec spec) {
        EntitySpec.Appearance appearance = spec.appearance();
        EntitySpec.Behaviour behaviour = spec.behaviour();
        return new ContentEntity.Flags(appearance.nameVisible(), behaviour.aware(), behaviour.ai(), appearance.glowing(), behaviour.gravity(),
                behaviour.invulnerable(), behaviour.silent(), behaviour.pickupItems(), behaviour.removable(), behaviour.keepEntity(),
                appearance.baby());
    }

    private static ContentEntity.Equipment equipmentOf(EntitySpec.Equipment equipment) {
        return new ContentEntity.Equipment(optionalItem(equipment.helmet()), optionalItem(equipment.chestplate()),
                optionalItem(equipment.leggings()), optionalItem(equipment.boots()), optionalItem(equipment.mainHand()),
                optionalItem(equipment.offHand()));
    }

    private static ContentItem optionalItem(Optional<ItemDrop> drop) {
        return drop.map(ContentServices::itemOf).orElse(null);
    }

    /** The adapters' spawn effect holds both parts in one record; a part that is absent has no key and zeros. */
    private static ContentEntity.SpawnEffect spawnEffectOf(EntitySpec.SpawnEffect effect) {
        Optional<EntitySpec.SpawnEffect.Sound> sound = effect.sound();
        Optional<EntitySpec.SpawnEffect.Burst> burst = effect.particles();
        Optional<EntitySpec.SpawnEffect.Motion> motion = burst.map(EntitySpec.SpawnEffect.Burst::motion);
        return new ContentEntity.SpawnEffect(sound.map(present -> present.key().toString()).orElse(null),
                sound.map(EntitySpec.SpawnEffect.Sound::distance).orElse(0), sound.map(EntitySpec.SpawnEffect.Sound::minPitch).orElse(0D),
                sound.map(EntitySpec.SpawnEffect.Sound::maxPitch).orElse(0D), sound.map(EntitySpec.SpawnEffect.Sound::volume).orElse(0D),
                burst.map(present -> present.key().toString()).orElse(null), burst.map(EntitySpec.SpawnEffect.Burst::offset).orElse(0),
                burst.map(EntitySpec.SpawnEffect.Burst::count).orElse(0), motion.map(EntitySpec.SpawnEffect.Motion::x).orElse(0D),
                motion.map(EntitySpec.SpawnEffect.Motion::y).orElse(0D), motion.map(EntitySpec.SpawnEffect.Motion::z).orElse(0D),
                motion.map(EntitySpec.SpawnEffect.Motion::randomX).orElse(false), motion.map(EntitySpec.SpawnEffect.Motion::randomY).orElse(false),
                motion.map(EntitySpec.SpawnEffect.Motion::randomZ).orElse(false), burst.map(EntitySpec.SpawnEffect.Burst::extra).orElse(0D));
    }

    private static ContentEntity.Command commandOf(EntitySpec.Command command) {
        return new ContentEntity.Command(command.commands(), command.delay(), command.repeat(), command.repeatDelay());
    }

    private static ContentEffect effectOf(AmbientEffects.Effect effect) {
        return new ContentEffect(effect.sound().map(ContentServices::soundOf).orElse(null),
                effect.particles().map(ContentServices::particlesOf).orElse(null),
                effect.commands().stream().map(command -> new ContentEffect.Command(commandOf(command.command()),
                        command.offsetX(), command.offsetY(), command.offsetZ())).toList(),
                effect.potion().map(potion -> new ContentEffect.Potion(potion.key().toString(), potion.strength(), potion.ticks())).orElse(null));
    }

    private static ContentEffect.Sound soundOf(AmbientEffects.Sound sound) {
        return new ContentEffect.Sound(sound.key().toString(), sound.offsetX(), sound.offsetY(), sound.offsetZ(),
                sound.volume(), sound.pitch());
    }

    private static ContentEffect.Particles particlesOf(AmbientEffects.Particles particles) {
        AmbientEffects.Spot spot = particles.spot();
        AmbientEffects.Spread spread = particles.spread();
        return new ContentEffect.Particles(particles.key().toString(), spot.forward(), spot.sideways(),
                spot.jitterX(), spot.jitterZ(), spot.lift(), particles.count(), spread.x(), spread.y(), spread.z(), particles.extra());
    }
}
