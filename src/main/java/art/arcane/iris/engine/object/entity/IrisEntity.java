/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.engine.object.entity;

import art.arcane.iris.engine.object.entity.spawn.IrisSurface;
import art.arcane.iris.engine.object.loot.InventorySlotType;
import art.arcane.iris.engine.object.loot.IrisLoot;
import art.arcane.iris.engine.object.loot.IrisLootReference;
import art.arcane.iris.engine.object.loot.IrisLootTable;
import art.arcane.iris.engine.object.marker.IrisCommand;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.core.service.EntityRiseSVC;
import art.arcane.iris.core.service.ExternalDataSVC;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.LootResolver;
import art.arcane.iris.engine.platform.EngineBukkitOps;
import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.RegistryListEntityType;
import art.arcane.iris.engine.object.annotations.RegistryListSpecialEntity;
import art.arcane.iris.engine.object.annotations.Required;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.util.common.data.registry.RegistryUtil;
import art.arcane.iris.platform.bukkit.BukkitWorld;
import art.arcane.iris.spi.PlatformWorld;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.util.common.format.C;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.util.common.plugin.Chunks;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attributable;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Panda.Gene;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static art.arcane.iris.util.common.data.registry.Particles.ITEM;

@SuppressWarnings("ALL")
@Accessors(chain = true)
@NoArgsConstructor

@Desc("Represents an iris entity.")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisEntity extends IrisRegistrant {
    @Required
    @RegistryListEntityType
    @Desc("The namespaced key of the entity type to spawn, such as minecraft:zombie or alexsmobs:grizzly_bear. To spawn a mob from another plugin or provider, set this type to unknown and define the special type.")
    private String type = null;

    @Desc("The SpawnReason name to spawn the entity with, such as NATURAL or COMMAND.")
    private String reason = null;

    @Desc("The custom name of this entity")
    private String customName = "";

    @Desc("Should the name on this entity be visible even if you arent looking at it.")
    private boolean customNameVisible = false;

    @Desc("If this entity type is a mob, should it be aware of it's surroundings & interact with the world.")
    private boolean aware = true;

    @Desc("If this entity type is a creature, should it have ai goals.")
    private boolean ai = true;

    @Desc("Should this entity be glowing")
    private boolean glowing = false;

    @Desc("Should gravity apply to this entity")
    private boolean gravity = true;

    @Desc("When an entity is invulnerable it can only be damaged by players increative mode.")
    private boolean invulnerable = false;

    @Desc("When an entity is silent it will not produce any sound.")
    private boolean silent = false;

    @Desc("Should this entity be allowed to pickup items")
    private boolean pickupItems = false;

    @Desc("Should this entity be removed when far away")
    private boolean removable = false;

    @Desc("Entity helmet equipment")
    private IrisLoot helmet = null;

    @Desc("Entity chestplate equipment")
    private IrisLoot chestplate = null;

    @Desc("Entity boots equipment")
    private IrisLoot boots = null;

    @Desc("Entity leggings equipment")
    private IrisLoot leggings = null;

    @Desc("Entity main hand equipment")
    private IrisLoot mainHand = null;

    @Desc("Entity off hand equipment")
    private IrisLoot offHand = null;

    @Desc("Make other entities ride this entity")
    @ArrayType(min = 1, type = IrisEntity.class)
    private KList<IrisEntity> passengers = new KList<>();

    @Desc("Attribute modifiers for this entity")
    @ArrayType(min = 1, type = IrisAttributeModifier.class)
    private KList<IrisAttributeModifier> attributes = new KList<>();

    @Desc("Loot tables for drops. Only the tables list is honored for entities; mode and multiplier are ignored, and the tables replace vanilla drops entirely.")
    private IrisLootReference loot = new IrisLootReference();

    @Desc("If specified, this entity will be leashed by this entity. I.e. THIS ENTITY Leashed by SPECIFIED. This has no effect on EnderDragons, Withers, Players, or Bats.Non-living entities excluding leashes will not persist as leashholders.")
    private IrisEntity leashHolder = null;

    @Desc("If specified, this entity will spawn with an effect")
    private IrisEffect spawnEffect = null;

    @Desc("Simply moves the entity from below the surface slowly out of the ground as a spawn-in effect")
    private boolean spawnEffectRiseOutOfGround = false;

    @Desc("The main gene for a panda if the entity type is a panda. One of NORMAL, LAZY, WORRIED, PLAYFUL, BROWN, WEAK or AGGRESSIVE.")
    private String pandaMainGene = null;

    @Desc("The hidden gene for a panda if the entity type is a panda. One of NORMAL, LAZY, WORRIED, PLAYFUL, BROWN, WEAK or AGGRESSIVE.")
    private String pandaHiddenGene = null;

    @Desc("The this entity is ageable, set it's baby status")
    private boolean baby = false;

    @Desc("If the entity should never be culled.")
    private boolean keepEntity = false;

    @Desc("The surface type to spawn this mob on")
    private IrisSurface surface = IrisSurface.LAND;

    @RegistryListSpecialEntity
    @Desc("Create a mob from another plugin, such as Mythic Mobs. Should be in the format of a namespace of PluginName:MobName")
    private String specialType = "";

    @Desc("Set to true if you want to apply all of the settings here to the mob, even though an external plugin has already done so.")
    private boolean applySettingsToCustomMobAnyways = false;

    @ArrayType(min = 1, type = IrisCommand.class)
    @Desc("Run raw commands when this entity is spawned. Use {x}, {y}, and {z} for location. /summon pig {x} {y} {z}")
    private KList<IrisCommand> rawCommands = new KList<>();

    public EntityType getBukkitType() {
        if (type == null || type.isBlank()) {
            return EntityType.UNKNOWN;
        }

        NamespacedKey key = NamespacedKey.fromString(type.trim().toLowerCase(Locale.ROOT));

        if (key == null) {
            return EntityType.UNKNOWN;
        }

        EntityType entityType = RegistryUtil.lookup(EntityType.class).get(key);
        return entityType == null ? EntityType.UNKNOWN : entityType;
    }

    public CreatureSpawnEvent.SpawnReason getBukkitReason() {
        if (reason == null || reason.isBlank()) {
            return CreatureSpawnEvent.SpawnReason.NATURAL;
        }

        try {
            return CreatureSpawnEvent.SpawnReason.valueOf(reason.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return CreatureSpawnEvent.SpawnReason.NATURAL;
        }
    }

    public Gene getBukkitPandaMainGene() {
        return resolveGene(pandaMainGene);
    }

    public Gene getBukkitPandaHiddenGene() {
        return resolveGene(pandaHiddenGene);
    }

    private static Gene resolveGene(String gene) {
        if (gene == null || gene.isBlank()) {
            return Gene.NORMAL;
        }

        try {
            return Gene.valueOf(gene.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Gene.NORMAL;
        }
    }

    public Entity spawn(Engine gen, Location at) {
        return spawn(gen, at, new RNG(at.hashCode()));
    }

    public Entity spawn(Engine gen, Location at, RNG rng) {
        if (!Chunks.isSafe(at)) {
            return null;
        }
        if (isSpawnEffectRiseOutOfGround()) {
            AtomicReference<Location> f = new AtomicReference<>(at);
            try {
                J.sfut(() -> {
                    if (Chunks.hasPlayersNearby(f.get())) {
                        Location b = f.get().clone();
                        Location start = new Location(b.getWorld(), b.getX(), b.getY() - 5, b.getZ());
                        f.set(start);
                    }
                }).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                IrisLogging.reportError("Iris entity spawn preparation was interrupted.", e);
            } catch (ExecutionException e) {
                IrisLogging.reportError("Iris entity spawn preparation failed.", e);
            }
            at = f.get();
        }

        Entity ee = doSpawn(at);

        if (ee == null && !Chunks.isSafe(at)) {
            return null;
        }

        if (isSpecialType() && !applySettingsToCustomMobAnyways) {
            return ee;
        }

        if (ee == null) {
            return null;
        }

        Entity e = ee;
        e.setCustomName(getCustomName() != null ? C.translateAlternateColorCodes('&', getCustomName()) : null);
        e.setCustomNameVisible(isCustomNameVisible());
        e.setGlowing(isGlowing());
        e.setGravity(isGravity());
        e.setInvulnerable(isInvulnerable());
        e.setSilent(isSilent());
        e.setPersistent(isKeepEntity() || IrisSettings.get().getWorld().isForcePersistEntities());

        int gg = 0;
        for (IrisEntity i : passengers) {
            Entity passenger = i.spawn(gen, at, rng.nextParallelRNG(234858 + gg++));
            if (passenger == null) {
                continue;
            }

            if (Bukkit.isPrimaryThread()) {
                e.addPassenger(passenger);
            } else {
                J.runEntity(e, () -> e.addPassenger(passenger));
            }
        }

        if (e instanceof Attributable) {
            Attributable a = (Attributable) e;

            for (IrisAttributeModifier i : getAttributes()) {
                i.apply(rng, a);
            }
        }

        if (e instanceof Lootable) {
            Lootable l = (Lootable) e;

            if (getLoot().getTables().isNotEmpty()) {
                BukkitOps.bindLoot(this, gen, l, at, rng);
            }
        }

        if (e instanceof LivingEntity) {
            LivingEntity l = (LivingEntity) e;
            l.setAI(isAi());
            l.setCanPickupItems(isPickupItems());

            if (getLeashHolder() != null) {
                l.setLeashHolder(getLeashHolder().spawn(gen, at, rng.nextParallelRNG(234548)));
            }

            l.setRemoveWhenFarAway(isRemovable());

            if (getHelmet() != null && LootResolver.oneIn(rng, getHelmet().getRarity())) {
                l.getEquipment().setHelmet(getHelmet().get(gen.isStudio(), rng));
            }

            if (getChestplate() != null && LootResolver.oneIn(rng, getChestplate().getRarity())) {
                l.getEquipment().setChestplate(getChestplate().get(gen.isStudio(), rng));
            }

            if (getLeggings() != null && LootResolver.oneIn(rng, getLeggings().getRarity())) {
                l.getEquipment().setLeggings(getLeggings().get(gen.isStudio(), rng));
            }

            if (getBoots() != null && LootResolver.oneIn(rng, getBoots().getRarity())) {
                l.getEquipment().setBoots(getBoots().get(gen.isStudio(), rng));
            }

            if (getMainHand() != null && LootResolver.oneIn(rng, getMainHand().getRarity())) {
                l.getEquipment().setItemInMainHand(getMainHand().get(gen.isStudio(), rng));
            }

            if (getOffHand() != null && LootResolver.oneIn(rng, getOffHand().getRarity())) {
                l.getEquipment().setItemInOffHand(getOffHand().get(gen.isStudio(), rng));
            }
        }

        if (e instanceof Ageable && isBaby()) {
            ((Ageable) e).setBaby();
        }

        if (e instanceof Panda) {
            ((Panda) e).setMainGene(getBukkitPandaMainGene());
            ((Panda) e).setHiddenGene(getBukkitPandaHiddenGene());
        }

        if (e instanceof Villager) {
            Villager villager = (Villager) e;
            villager.setRemoveWhenFarAway(false);
            BukkitOps.persistVillager(villager);
        }

        if (e instanceof Mob) {
            Mob m = (Mob) e;
            m.setAware(isAware());
        }

        if (spawnEffect != null) {
            spawnEffect.apply(e);
        }

        if (rawCommands.isNotEmpty()) {
            final Location fat = at;
            final PlatformWorld commandWorld = new BukkitWorld(fat.getWorld());
            rawCommands.forEach(r -> r.run(commandWorld, fat.getBlockX(), fat.getBlockY(), fat.getBlockZ()));
        }

        Location finalAt1 = at;

        J.runEntity(e, () -> {
            if (isSpawnEffectRiseOutOfGround() && e instanceof LivingEntity && Chunks.hasPlayersNearby(finalAt1)) {
                LivingEntity living = (LivingEntity) e;
                Location start = finalAt1.clone();
                boolean originalInvulnerable = e.isInvulnerable();
                boolean originalAi = living.hasAI();
                boolean originalCollidable = living.isCollidable();
                int originalNoDamageTicks = living.getNoDamageTicks();
                EntityRiseSVC.stamp(e, originalInvulnerable, originalAi, originalCollidable);
                e.setInvulnerable(true);
                living.setAI(false);
                living.setCollidable(false);
                living.setNoDamageTicks(100000);
                Runnable restore = () -> restoreRiseState(e, living, originalInvulnerable, originalAi,
                        originalCollidable, originalNoDamageTicks);
                AtomicInteger t = new AtomicInteger(0);
                Runnable[] loop = new Runnable[1];
                loop[0] = () -> {
                    if (t.get() > 100 || e.isDead()) {
                        restore.run();
                        return;
                    }

                    t.incrementAndGet();
                    if (e.getLocation().getBlock().getType().isSolid() || living.getEyeLocation().getBlock().getType().isSolid()) {
                        e.teleport(start.add(new Vector(0, 0.1, 0)));
                        ItemStack itemCrackData = new ItemStack(living.getEyeLocation().clone().subtract(0, 2, 0).getBlock().getBlockData().getMaterial());
                        e.getWorld().spawnParticle(ITEM, living.getEyeLocation(), 6, 0.2, 0.4, 0.2, 0.06f, itemCrackData);
                        if (M.r(0.2)) {
                            e.getWorld().playSound(e.getLocation(), Sound.BLOCK_CHORUS_FLOWER_GROW, 0.8f, 0.1f);
                        }
                        if (!J.runEntity(e, loop[0], 1, restore)) {
                            restore.run();
                        }
                    } else {
                        restore.run();
                    }
                };
                if (!J.runEntity(e, loop[0], 0, restore)) {
                    restore.run();
                }
            }
        });


        return e;
    }

    private static void restoreRiseState(Entity entity, LivingEntity living, boolean invulnerable,
                                         boolean ai, boolean collidable, int noDamageTicks) {
        living.setNoDamageTicks(noDamageTicks);
        living.setCollidable(collidable);
        living.setAI(ai);
        entity.setInvulnerable(invulnerable);
        EntityRiseSVC.clearStamp(entity);
    }

    private int surfaceY(Location l) {
        int m = l.getBlockY();

        while (m-- > 0) {
            Location ll = l.clone();
            ll.setY(m);

            if (ll.getBlock().getType().isSolid()) {
                return m;
            }
        }

        return 0;
    }

    private Entity doSpawn(Location at) {
        if (!Chunks.isSafe(at)) {
            return null;
        }

        if (EntityType.UNKNOWN.equals(getBukkitType()) && !isSpecialType()) {
            return null;
        }

        if (!Bukkit.isPrimaryThread()) {
            // Someone called spawn (worldedit maybe?) on a non server thread
            // Due to the structure of iris, we will call it sync and busy wait until it's done.
            AtomicReference<Entity> ae = new AtomicReference<>();

            try {
                if (!J.runAt(at, () -> ae.set(doSpawn(at)))) {
                    return null;
                }
            } catch (Throwable e) {
                return null;
            }
            PrecisionStopwatch p = PrecisionStopwatch.start();

            while (ae.get() == null) {
                J.sleep(25);

                if (p.getMilliseconds() > 500) {
                    return null;
                }
            }

            return ae.get();
        }

        if (isSpecialType()) {
            return IrisServices.get(ExternalDataSVC.class).spawnMob(at, Identifier.fromString(specialType));
        }


        return BukkitPlatform.spawnEntity(at, getBukkitType(), getBukkitReason());
    }

    public boolean isSpecialType() {
        return specialType != null && !specialType.equals("");
    }

    private static final class BukkitOps {
        private static void persistVillager(Villager villager) {
            villager.setPersistent(true);
        }

        private static void bindLoot(IrisEntity entity, Engine gen, Lootable l, Location finalAt, RNG rng) {
            l.setLootTable(new LootTable() {
                @Override
                public NamespacedKey getKey() {
                    return new NamespacedKey("iris", "loot-" + entity.hashCode());
                }

                @Override
                public Collection<ItemStack> populateLoot(Random random, LootContext context) {
                    KList<ItemStack> items = new KList<>();

                    for (String fi : entity.getLoot().getTables()) {
                        IrisLootTable i = gen.getData().getLootLoader().load(fi);
                        items.addAll(i.getLoot(gen.isStudio(), gen.getSeedManager().getLoot(), InventorySlotType.STORAGE, finalAt.getWorld(), finalAt.getBlockX(), finalAt.getBlockY(), finalAt.getBlockZ()));
                    }

                    return items;
                }

                @Override
                public void fillInventory(Inventory inventory, Random random, LootContext context) {
                    for (ItemStack i : populateLoot(random, context)) {
                        inventory.addItem(i);
                    }

                    EngineBukkitOps.scramble(gen, inventory, rng);
                }
            });
        }
    }

    @Override
    public String getFolderName() {
        return "entities";
    }

    @Override
    public String getTypeName() {
        return "Entity";
    }
}
