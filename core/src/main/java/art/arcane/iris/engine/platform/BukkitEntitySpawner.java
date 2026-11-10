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

package art.arcane.iris.engine.platform;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.core.modules.ModuleServices;
import art.arcane.iris.core.service.EntityRiseSVC;
import art.arcane.iris.core.service.ExternalDataSVC;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.platform.bukkit.BukkitAttributes;
import art.arcane.iris.platform.bukkit.BukkitItemStacks;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.util.common.data.registry.RegistryUtil;
import art.arcane.iris.util.common.format.C;
import art.arcane.iris.util.common.plugin.Chunks;
import art.arcane.iris.util.common.scheduling.ConsoleCommands;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import com.volmit.entity.EntitySpec;
import com.volmit.item.ItemAttribute;
import com.volmit.resource.ContentKey;
import com.volmit.resource.ResourceId;
import com.volmit.service.Loot;
import com.volmit.geometry.BlockPosition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attributable;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Panda.Gene;
import org.bukkit.entity.Villager;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static art.arcane.iris.util.common.data.registry.Particles.ITEM;

/**
 * Creates the entities a module planned ({@link EntitySpec}) on a Bukkit server: the entity itself, then everything the
 * spec sets on it. Every random choice was made by the module; this class only acts on the platform.
 */
public final class BukkitEntitySpawner {
    private static final long RISE_START_DEPTH_BLOCKS = 5;
    private static final int CHUNK_SHIFT = 4;
    private static final int RISE_MAX_TICKS = 100;
    private static final int RISE_NO_DAMAGE_TICKS = 100000;
    private static final double RISE_STEP_BLOCKS = 0.1;
    private static final double RISE_SOUND_CHANCE = 0.2;
    private static final int SPAWN_WAIT_SLEEP_MS = 25;
    private static final int SPAWN_WAIT_LIMIT_MS = 500;
    private static final String LOOT_TABLE_NAMESPACE = "iris";

    private BukkitEntitySpawner() {
    }

    /** Spawns the pack's entity {@code key} at {@code at}, as a command asks for; null when the pack has no such entity or the spawn fails. */
    public static Entity spawnByKey(Engine gen, String key, Location at) {
        return ModuleServices.entity(gen, key).map(spec -> spawn(gen, spec, at)).orElse(null);
    }

    public static Entity spawn(Engine gen, EntitySpec spec, Location at) {
        if (!Chunks.isSafe(at)) {
            return null;
        }
        boolean risesFromGround = spec.arrival().risesFromGround();
        if (risesFromGround) {
            at = startBelowGroundWhenWatched(at);
        }

        Entity ee = doSpawn(spec, at);

        if (ee == null && !Chunks.isSafe(at)) {
            return null;
        }

        if (spec.type() instanceof ContentKey.Provided && !spec.adjustsProvidedMob()) {
            return ee;
        }

        if (ee == null) {
            return null;
        }

        Entity e = ee;
        applyFlags(e, spec);
        addPassengers(gen, e, spec, at);

        if (e instanceof Attributable attributable) {
            for (ItemAttribute attribute : spec.attributes()) {
                BukkitAttributes.apply(attribute, attributable);
            }
        }

        if (e instanceof Lootable lootable && !spec.drops().lootTables().isEmpty()) {
            bindLoot(spec, gen, lootable, at);
        }

        if (e instanceof LivingEntity living) {
            applyLivingState(gen, living, spec, at);
        }

        if (e instanceof Ageable ageable && spec.appearance().baby()) {
            ageable.setBaby();
        }

        if (e instanceof Panda panda) {
            panda.setMainGene(resolveGene(spec.appearance().pandaMainGene().orElse(null)));
            panda.setHiddenGene(resolveGene(spec.appearance().pandaHiddenGene().orElse(null)));
        }

        if (e instanceof Villager villager) {
            villager.setRemoveWhenFarAway(false);
            villager.setPersistent(true);
        }

        if (e instanceof Mob mob) {
            mob.setAware(spec.behaviour().aware());
        }

        spec.arrival().effect().ifPresent(effect -> showSpawnEffect(e, effect));

        runCommands(spec, at);

        Location riseFrom = at;
        J.runEntity(e, () -> {
            if (risesFromGround && e instanceof LivingEntity living && Chunks.hasPlayersNearby(riseFrom)) {
                riseOutOfGround(e, living, riseFrom);
            }
        });

        return e;
    }

    /** Players watching an entity that rises out of the ground see it start five blocks below the spot. */
    static Location startBelowGroundWhenWatched(Location at) {
        AtomicReference<Location> start = new AtomicReference<>(at);
        try {
            J.runRegionFuture(at.getWorld(), at.getBlockX() >> CHUNK_SHIFT, at.getBlockZ() >> CHUNK_SHIFT, () -> {
                if (Chunks.hasPlayersNearby(start.get())) {
                    Location b = start.get().clone();
                    start.set(new Location(b.getWorld(), b.getX(), b.getY() - RISE_START_DEPTH_BLOCKS, b.getZ()));
                }
            }).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            IrisLogging.reportError("Iris entity spawn preparation was interrupted.", e);
        } catch (ExecutionException e) {
            IrisLogging.reportError("Iris entity spawn preparation failed.", e);
        }
        return start.get();
    }

    private static void applyFlags(Entity e, EntitySpec spec) {
        EntitySpec.Appearance appearance = spec.appearance();
        EntitySpec.Behaviour behaviour = spec.behaviour();
        e.setCustomName(appearance.customName().map(name -> C.translateAlternateColorCodes('&', name)).orElse(null));
        e.setCustomNameVisible(appearance.nameVisible());
        e.setGlowing(appearance.glowing());
        e.setGravity(behaviour.gravity());
        e.setInvulnerable(behaviour.invulnerable());
        e.setSilent(behaviour.silent());
        e.setPersistent(behaviour.keepEntity() || IrisSettings.get().getWorld().isForcePersistEntities());
    }

    private static void addPassengers(Engine gen, Entity e, EntitySpec spec, Location at) {
        for (EntitySpec passengerSpec : spec.company().passengers()) {
            Entity passenger = spawn(gen, passengerSpec, at);
            if (passenger == null) {
                continue;
            }

            if (Bukkit.isPrimaryThread()) {
                e.addPassenger(passenger);
            } else {
                J.runEntity(e, () -> e.addPassenger(passenger));
            }
        }
    }

    private static void applyLivingState(Engine gen, LivingEntity l, EntitySpec spec, Location at) {
        l.setAI(spec.behaviour().ai());
        l.setCanPickupItems(spec.behaviour().pickupItems());

        spec.company().leashHolder().ifPresent(holder -> l.setLeashHolder(spawn(gen, holder, at)));

        l.setRemoveWhenFarAway(spec.behaviour().removable());

        EntitySpec.Equipment equipment = spec.equipment();
        equipment.helmet().ifPresent(item -> l.getEquipment().setHelmet(BukkitItemStacks.create(item)));
        equipment.chestplate().ifPresent(item -> l.getEquipment().setChestplate(BukkitItemStacks.create(item)));
        equipment.leggings().ifPresent(item -> l.getEquipment().setLeggings(BukkitItemStacks.create(item)));
        equipment.boots().ifPresent(item -> l.getEquipment().setBoots(BukkitItemStacks.create(item)));
        equipment.mainHand().ifPresent(item -> l.getEquipment().setItemInMainHand(BukkitItemStacks.create(item)));
        equipment.offHand().ifPresent(item -> l.getEquipment().setItemInOffHand(BukkitItemStacks.create(item)));
    }

    private static void runCommands(EntitySpec spec, Location at) {
        for (EntitySpec.Command command : spec.arrival().commands()) {
            ConsoleCommands.run(command.commands(), command.delay(), command.repeat(), command.repeatDelay(),
                    at.getBlockX(), at.getBlockY(), at.getBlockZ());
        }
    }

    private static void riseOutOfGround(Entity e, LivingEntity living, Location from) {
        Location start = from.clone();
        boolean originalInvulnerable = e.isInvulnerable();
        boolean originalAi = living.hasAI();
        boolean originalCollidable = living.isCollidable();
        int originalNoDamageTicks = living.getNoDamageTicks();
        EntityRiseSVC.stamp(e, originalInvulnerable, originalAi, originalCollidable);
        e.setInvulnerable(true);
        living.setAI(false);
        living.setCollidable(false);
        living.setNoDamageTicks(RISE_NO_DAMAGE_TICKS);
        Runnable restore = () -> restoreRiseState(e, living, originalInvulnerable, originalAi,
                originalCollidable, originalNoDamageTicks);
        AtomicInteger t = new AtomicInteger(0);
        Runnable[] loop = new Runnable[1];
        loop[0] = () -> {
            if (t.get() > RISE_MAX_TICKS || e.isDead()) {
                restore.run();
                return;
            }

            t.incrementAndGet();
            if (e.getLocation().getBlock().getType().isSolid() || living.getEyeLocation().getBlock().getType().isSolid()) {
                e.teleport(start.add(new Vector(0, RISE_STEP_BLOCKS, 0)));
                ItemStack itemCrackData = new ItemStack(living.getEyeLocation().clone().subtract(0, 2, 0).getBlock().getBlockData().getMaterial());
                e.getWorld().spawnParticle(ITEM, living.getEyeLocation(), 6, 0.2, 0.4, 0.2, 0.06f, itemCrackData);
                if (M.r(RISE_SOUND_CHANCE)) {
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

    private static void restoreRiseState(Entity entity, LivingEntity living, boolean invulnerable,
                                         boolean ai, boolean collidable, int noDamageTicks) {
        living.setNoDamageTicks(noDamageTicks);
        living.setCollidable(collidable);
        living.setAI(ai);
        entity.setInvulnerable(invulnerable);
        EntityRiseSVC.clearStamp(entity);
    }

    private static Entity doSpawn(EntitySpec spec, Location at) {
        if (!Chunks.isSafe(at)) {
            return null;
        }

        if (EntityType.UNKNOWN.equals(bukkitType(spec)) && !(spec.type() instanceof ContentKey.Provided)) {
            return null;
        }

        if (!Bukkit.isPrimaryThread()) {
            // Someone called spawn (worldedit maybe?) on a non server thread
            // Due to the structure of iris, we will call it sync and busy wait until it's done.
            AtomicReference<Entity> ae = new AtomicReference<>();

            try {
                if (!J.runAt(at, () -> ae.set(doSpawn(spec, at)))) {
                    return null;
                }
            } catch (Throwable e) {
                return null;
            }
            PrecisionStopwatch p = PrecisionStopwatch.start();

            while (ae.get() == null) {
                J.sleep(SPAWN_WAIT_SLEEP_MS);

                if (p.getMilliseconds() > SPAWN_WAIT_LIMIT_MS) {
                    return null;
                }
            }

            return ae.get();
        }

        if (spec.type() instanceof ContentKey.Provided mob) {
            return IrisServices.get(ExternalDataSVC.class).spawnMob(at, new Identifier(mob.provider(), mob.name()));
        }

        return BukkitPlatform.spawnEntity(at, bukkitType(spec), bukkitReason(spec));
    }

    /** The platform's entity type of the spec, unknown for a provider's mob or a type the platform lacks. */
    public static EntityType bukkitType(EntitySpec spec) {
        if (!(spec.type() instanceof ContentKey.Registered registered)) {
            return EntityType.UNKNOWN;
        }
        EntityType entityType = RegistryUtil.lookup(EntityType.class).get(new NamespacedKey(registered.id().namespace(), registered.id().path()));
        return entityType == null ? EntityType.UNKNOWN : entityType;
    }

    private static CreatureSpawnEvent.SpawnReason bukkitReason(EntitySpec spec) {
        String reason = spec.arrival().reason().orElse("");
        if (reason.isBlank()) {
            return CreatureSpawnEvent.SpawnReason.NATURAL;
        }

        try {
            return CreatureSpawnEvent.SpawnReason.valueOf(reason.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return CreatureSpawnEvent.SpawnReason.NATURAL;
        }
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

    private static void showSpawnEffect(Entity p, EntitySpec.SpawnEffect fx) {
        J.runEntity(p, () -> {
            fx.sound().ifPresent(sound -> playSpawnSound(p, sound));
            fx.particles().ifPresent(burst -> showSpawnParticles(p, burst));
        });
    }

    private static void playSpawnSound(Entity p, EntitySpec.SpawnEffect.Sound sound) {
        Sound type = resolveKeyed(Sound.class, sound.key());
        if (type == null) {
            return;
        }
        int distance = sound.distance();
        Location part = p.getLocation().clone().add(RNG.r.i(-distance, distance), RNG.r.i(-distance, distance), RNG.r.i(-distance, distance));
        p.getWorld().playSound(part, type, (float) sound.volume(), (float) RNG.r.d(sound.minPitch(), sound.maxPitch()));
    }

    private static void showSpawnParticles(Entity p, EntitySpec.SpawnEffect.Burst burst) {
        Particle type = resolveKeyed(Particle.class, burst.key());
        if (type == null) {
            return;
        }
        Location part = p.getLocation().clone().add(0, 0.25, 0).add(new Vector(1, 1, 1).multiply(RNG.r.d())).subtract(new Vector(1, 1, 1).multiply(RNG.r.d()));
        part.add(RNG.r.d(), 0, RNG.r.d());
        EntitySpec.SpawnEffect.Motion motion = burst.motion();
        double motionX = motion.randomX() ? RNG.r.d(-motion.x(), motion.x()) : motion.x();
        double motionY = motion.randomY() ? RNG.r.d(-motion.y(), motion.y()) : motion.y();
        double motionZ = motion.randomZ() ? RNG.r.d(-motion.z(), motion.z()) : motion.z();
        double y = part.getY() + RNG.r.i(burst.offset());
        if (burst.extra() != 0) {
            p.getWorld().spawnParticle(type, part.getX(), y, part.getZ(), burst.count(), motionX, motionY, motionZ, burst.extra());
        } else {
            p.getWorld().spawnParticle(type, part.getX(), y, part.getZ(), burst.count(), motionX, motionY, motionZ);
        }
    }

    private static <T> T resolveKeyed(Class<T> type, ResourceId key) {
        return RegistryUtil.lookup(type).get(new NamespacedKey(key.namespace(), key.path()));
    }

    private static void bindLoot(EntitySpec spec, Engine gen, Lootable lootable, Location finalAt) {
        lootable.setLootTable(new LootTable() {
            @Override
            public NamespacedKey getKey() {
                return new NamespacedKey(LOOT_TABLE_NAMESPACE, "loot-" + spec.hashCode());
            }

            @Override
            public Collection<ItemStack> populateLoot(Random random, LootContext context) {
                BlockPosition position = new BlockPosition(finalAt.getBlockX(), finalAt.getBlockY() - gen.getMinHeight(), finalAt.getBlockZ());
                Loot.Site site = new Loot.Site(position, Optional.empty(), false, gen.isStudio());
                KList<ItemStack> items = new KList<>();

                for (ResourceId table : spec.drops().lootTables()) {
                    items.addAll(BukkitItemStacks.createAll(ModuleServices.lootItems(gen, site, table)));
                }

                return items;
            }

            @Override
            public void fillInventory(Inventory inventory, Random random, LootContext context) {
                for (ItemStack i : populateLoot(random, context)) {
                    inventory.addItem(i);
                }

                EngineBukkitOps.scramble(gen, inventory, new RNG(spec.drops().shuffleSeed()));
            }
        });
    }
}
