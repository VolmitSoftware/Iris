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

package art.arcane.iris.world.entity;

import art.arcane.iris.command.IrisCommandRegistry;

import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.schema.annotation.DependsOn;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListPotionEffect;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.scheduling.ChronoLatch;
import art.arcane.iris.platform.bukkit.registry.RegistryUtil;
import art.arcane.iris.world.task.J;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Location;
import org.bukkit.HeightMap;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

@Snippet("effect")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("An iris effect")
@Data
public class IrisEffect {
    private final transient AtomicCache<PotionEffectType> pt = new AtomicCache<>();
    private final transient AtomicCache<ChronoLatch> latch = new AtomicCache<>();
    private final transient AtomicCache<Particle> particleEffectResolved = new AtomicCache<>();
    private final transient AtomicCache<Sound> soundResolved = new AtomicCache<>();
    @RegistryListPotionEffect
    @Description("The potion effect to apply in this area")
    private String potionEffect = "";
    @Description("The particle effect to apply in the area")
    private String particleEffect = null;
    @DependsOn({"particleEffect"})
    @MinNumber(-32)
    @MaxNumber(32)
    @Description("Randomly offset from the surface to this surface+value")
    private int particleOffset = 0;
    @DependsOn({"particleEffect"})
    @MinNumber(-8)
    @MaxNumber(8)
    @Description("The alt x, usually represents motion if the particle count is zero. Otherwise an offset.")
    private double particleAltX = 0;
    @DependsOn({"particleEffect"})
    @MinNumber(-8)
    @MaxNumber(8)
    @Description("The alt y, usually represents motion if the particle count is zero. Otherwise an offset.")
    private double particleAltY = 0;
    @DependsOn({"particleEffect"})
    @MinNumber(-8)
    @MaxNumber(8)
    @Description("The alt z, usually represents motion if the particle count is zero. Otherwise an offset.")
    private double particleAltZ = 0;
    @DependsOn({"particleEffect"})
    @Description("Randomize the altX by -altX to altX")
    private boolean randomAltX = true;
    @DependsOn({"particleEffect"})
    @Description("Randomize the altY by -altY to altY")
    private boolean randomAltY = false;
    @DependsOn({"particleEffect"})
    @Description("Randomize the altZ by -altZ to altZ")
    private boolean randomAltZ = true;
    @Description("The sound to play")
    private String sound = null;
    @DependsOn({"sound"})
    @MinNumber(0)
    @MaxNumber(512)
    @Description("The max distance from the player the sound will play")
    private int soundDistance = 12;
    @DependsOn({"sound", "maxPitch"})
    @MinNumber(0.01)
    @MaxNumber(1.99)
    @Description("The minimum sound pitch")
    private double minPitch = 0.5D;
    @DependsOn({"sound", "minVolume"})
    @MinNumber(0.01)
    @MaxNumber(1.99)
    @Description("The max sound pitch")
    private double maxPitch = 1.5D;
    @DependsOn({"sound"})
    @MinNumber(0.001)
    @MaxNumber(512)
    @Description("The sound volume.")
    private double volume = 1.5D;
    @DependsOn({"particleEffect"})
    @MinNumber(0)
    @MaxNumber(512)
    @Description("The particle count. Try setting to zero for using the alt xyz to a motion value instead of an offset")
    private int particleCount = 0;
    @DependsOn({"particleEffect"})
    @MinNumber(0)
    @MaxNumber(64)
    @Description("How far away from the player particles can play")
    private int particleDistance = 20;
    @DependsOn({"particleEffect"})
    @MinNumber(0)
    @MaxNumber(128)
    @Description("How wide the particles can play (player's view left and right) RADIUS")
    private int particleDistanceWidth = 24;
    @DependsOn({"particleEffect"})
    @Description("An extra value for some particles... Which bukkit doesn't even document.")
    private double extra = 0;
    @DependsOn({"potionEffect"})
    @MinNumber(-1)
    @MaxNumber(1024)
    @Description("The Potion Strength or -1 to disable")
    private int potionStrength = -1;
    @DependsOn({"potionEffect", "potionTicksMin"})
    @MinNumber(1)
    @Description("The max time the potion will last for")
    private int potionTicksMax = 155;
    @DependsOn({"potionEffect", "potionTicksMax"})
    @MinNumber(1)
    @Description("The min time the potion will last for")
    private int potionTicksMin = 75;
    @Required
    @MinNumber(0)
    @Description("The effect interval in milliseconds")
    private int interval = 150;
    @DependsOn({"particleEffect"})
    @MinNumber(0)
    @MaxNumber(16)
    @Description("The effect distance start away")
    private int particleAway = 5;
    @Required
    @MinNumber(1)
    @Description("The chance is 1 in CHANCE per interval")
    private int chance = 50;
    @Description("Run commands, with configurable location parameters")
    private IrisCommandRegistry commandRegistry = null;


    public boolean canTick() {
        return latch.aquire(() -> new ChronoLatch(interval)).flip();
    }

    public boolean shouldApplyNow() {
        return canTick() && RNG.r.nextInt(chance) == 0;
    }

    public String getParticleEffectKey() {
        return particleEffect;
    }

    public String getSoundKey() {
        return sound;
    }

    public Particle getParticleEffect() {
        if (particleEffect == null) {
            return null;
        }
        return particleEffectResolved.aquire(() -> resolveKeyed(Particle.class, particleEffect));
    }

    public Sound getSound() {
        if (sound == null) {
            return null;
        }
        return soundResolved.aquire(() -> resolveKeyed(Sound.class, sound));
    }

    private static <T> T resolveKeyed(Class<T> type, String key) {
        NamespacedKey namespacedKey = NamespacedKey.fromString(key);
        return namespacedKey == null ? null : RegistryUtil.lookup(type).get(namespacedKey);
    }

    public PotionEffectType getRealType() {
        return pt.aquire(() ->
        {
            if (getPotionEffect() == null || getPotionEffect().isEmpty()) {
                return PotionEffectType.LUCK;
            }

            try {
                PotionEffectType resolved = PotionEffectTypes.resolve(getPotionEffect());
                if (resolved != null) {
                    return resolved;
                }
            } catch (Throwable e) {
                IrisLogging.reportError(e);
            }

            if (PotionEffectTypes.shouldWarn(getPotionEffect())) {
                IrisLogging.warn("Unknown Potion Effect Type: \"" + getPotionEffect() + "\". Valid types: " + PotionEffectTypes.knownTypesList());
            }

            return PotionEffectType.LUCK;
        });
    }

    private static final class BukkitFx {
        private static void run(Player p, Runnable r) {
            J.runEntity(p, r);
        }
    }

    public void apply(Player p, Engine g) {
        if (!shouldApplyNow()) {
            return;
        }

        BukkitFx.run(p, () -> {
            Sound soundType = getSound();
            Particle particleType = getParticleEffect();
            if (soundType != null) {
                Location part = p.getLocation().clone().add(RNG.r.i(-soundDistance, soundDistance), RNG.r.i(-soundDistance, soundDistance), RNG.r.i(-soundDistance, soundDistance));
                p.playSound(part, soundType, (float) volume, (float) RNG.r.d(minPitch, maxPitch));
            }

            if (particleType != null) {
                Location part = p.getLocation().clone().add(p.getLocation().getDirection().clone().multiply(RNG.r.i(particleDistance) + particleAway)).clone().add(p.getLocation().getDirection().clone().rotateAroundY(Math.toRadians(90)).multiply(RNG.r.d(-particleDistanceWidth, particleDistanceWidth)));

                applyParticles(p, particleType, part);
            }

            if (commandRegistry != null) {
                commandRegistry.run(p);
            }

            if (potionStrength > -1) {
                if (p.hasPotionEffect(getRealType())) {
                    PotionEffect e = p.getPotionEffect(getRealType());
                    if (e != null && e.getAmplifier() > getPotionStrength()) {
                        return;
                    }

                    p.removePotionEffect(getRealType());
                }

                p.addPotionEffect(new PotionEffect(getRealType(),
                        RNG.r.i(Math.min(potionTicksMax, potionTicksMin),
                                Math.max(potionTicksMax, potionTicksMin)),
                        getPotionStrength(),
                        true, false, false));
            }
        });
    }

    void applyParticles(Player player, Particle particle, Location location) {
        World world = location.getWorld();
        if (J.isOwnedByCurrentRegion(world, location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            sampleParticleSurface(player, particle, location);
        } else {
            J.runAt(location, () -> sampleParticleSurface(player, particle, location));
        }
    }

    private void sampleParticleSurface(Player player, Particle particle, Location location) {
        World world = location.getWorld();
        if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return;
        }
        location.setY(world.getHighestBlockYAt(location.getBlockX(), location.getBlockZ(), HeightMap.OCEAN_FLOOR) + 1);
        location.add(RNG.r.d(), 0, RNG.r.d());
        BukkitFx.run(player, () -> emitParticles(player, particle, location));
    }

    private void emitParticles(Player player, Particle particle, Location location) {
        if (player.getWorld() != location.getWorld()) {
            return;
        }
        double xOffset = randomAltX ? RNG.r.d(-particleAltX, particleAltX) : particleAltX;
        double yOffset = randomAltY ? RNG.r.d(-particleAltY, particleAltY) : particleAltY;
        double zOffset = randomAltZ ? RNG.r.d(-particleAltZ, particleAltZ) : particleAltZ;
        if (extra != 0) {
            player.spawnParticle(particle, location.getX(), location.getY() + RNG.r.i(particleOffset),
                    location.getZ(), particleCount, xOffset, yOffset, zOffset, extra);
        } else {
            player.spawnParticle(particle, location.getX(), location.getY() + RNG.r.i(particleOffset),
                    location.getZ(), particleCount, xOffset, yOffset, zOffset);
        }
    }

    public void apply(Entity p) {
        if (!shouldApplyNow()) {
            return;
        }

        J.runEntity(p, () -> {
            Sound soundType = getSound();
            Particle particleType = getParticleEffect();
            if (soundType != null) {
                Location part = p.getLocation().clone().add(RNG.r.i(-soundDistance, soundDistance), RNG.r.i(-soundDistance, soundDistance), RNG.r.i(-soundDistance, soundDistance));
                p.getWorld().playSound(part, soundType, (float) volume, (float) RNG.r.d(minPitch, maxPitch));
            }

            if (particleType != null) {
                Location part = p.getLocation().clone().add(0, 0.25, 0).add(new Vector(1, 1, 1).multiply(RNG.r.d())).subtract(new Vector(1, 1, 1).multiply(RNG.r.d()));
                part.add(RNG.r.d(), 0, RNG.r.d());
                if (extra != 0) {
                    p.getWorld().spawnParticle(particleType, part.getX(), part.getY() + RNG.r.i(particleOffset),
                            part.getZ(),
                            particleCount,
                            randomAltX ? RNG.r.d(-particleAltX, particleAltX) : particleAltX,
                            randomAltY ? RNG.r.d(-particleAltY, particleAltY) : particleAltY,
                            randomAltZ ? RNG.r.d(-particleAltZ, particleAltZ) : particleAltZ,
                            extra);
                } else {
                    p.getWorld().spawnParticle(particleType, part.getX(), part.getY() + RNG.r.i(particleOffset), part.getZ(),
                            particleCount,
                            randomAltX ? RNG.r.d(-particleAltX, particleAltX) : particleAltX,
                            randomAltY ? RNG.r.d(-particleAltY, particleAltY) : particleAltY,
                            randomAltZ ? RNG.r.d(-particleAltZ, particleAltZ) : particleAltZ);
                }
            }
        });
    }
}
