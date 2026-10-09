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

import art.arcane.iris.engine.object.marker.IrisCommandRegistry;
import art.arcane.iris.engine.data.cache.AtomicCache;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.annotations.DependsOn;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import art.arcane.iris.engine.object.annotations.RegistryListPotionEffect;
import art.arcane.iris.engine.object.annotations.Required;
import art.arcane.iris.engine.object.annotations.Snippet;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.scheduling.ChronoLatch;
import art.arcane.iris.util.common.data.registry.RegistryUtil;
import art.arcane.iris.util.common.scheduling.J;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Location;
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
@Desc("An iris effect")
@Data
public class IrisEffect {
    private final transient AtomicCache<PotionEffectType> pt = new AtomicCache<>();
    private final transient AtomicCache<ChronoLatch> latch = new AtomicCache<>();
    private final transient AtomicCache<Particle> particleEffectResolved = new AtomicCache<>();
    private final transient AtomicCache<Sound> soundResolved = new AtomicCache<>();
    @RegistryListPotionEffect
    @Desc("The potion effect to apply in this area")
    private String potionEffect = "";
    @Desc("The particle effect to apply in the area")
    private String particleEffect = null;
    @DependsOn({"particleEffect"})
    @MinNumber(-32)
    @MaxNumber(32)
    @Desc("Randomly offset from the surface to this surface+value")
    private int particleOffset = 0;
    @DependsOn({"particleEffect"})
    @MinNumber(-8)
    @MaxNumber(8)
    @Desc("The alt x, usually represents motion if the particle count is zero. Otherwise an offset.")
    private double particleAltX = 0;
    @DependsOn({"particleEffect"})
    @MinNumber(-8)
    @MaxNumber(8)
    @Desc("The alt y, usually represents motion if the particle count is zero. Otherwise an offset.")
    private double particleAltY = 0;
    @DependsOn({"particleEffect"})
    @MinNumber(-8)
    @MaxNumber(8)
    @Desc("The alt z, usually represents motion if the particle count is zero. Otherwise an offset.")
    private double particleAltZ = 0;
    @DependsOn({"particleEffect"})
    @Desc("Randomize the altX by -altX to altX")
    private boolean randomAltX = true;
    @DependsOn({"particleEffect"})
    @Desc("Randomize the altY by -altY to altY")
    private boolean randomAltY = false;
    @DependsOn({"particleEffect"})
    @Desc("Randomize the altZ by -altZ to altZ")
    private boolean randomAltZ = true;
    @Desc("The sound to play")
    private String sound = null;
    @DependsOn({"sound"})
    @MinNumber(0)
    @MaxNumber(512)
    @Desc("The max distance from the player the sound will play")
    private int soundDistance = 12;
    @DependsOn({"sound", "maxPitch"})
    @MinNumber(0.01)
    @MaxNumber(1.99)
    @Desc("The minimum sound pitch")
    private double minPitch = 0.5D;
    @DependsOn({"sound", "minVolume"})
    @MinNumber(0.01)
    @MaxNumber(1.99)
    @Desc("The max sound pitch")
    private double maxPitch = 1.5D;
    @DependsOn({"sound"})
    @MinNumber(0.001)
    @MaxNumber(512)
    @Desc("The sound volume.")
    private double volume = 1.5D;
    @DependsOn({"particleEffect"})
    @MinNumber(0)
    @MaxNumber(512)
    @Desc("The particle count. Try setting to zero for using the alt xyz to a motion value instead of an offset")
    private int particleCount = 0;
    @DependsOn({"particleEffect"})
    @MinNumber(0)
    @MaxNumber(64)
    @Desc("How far away from the player particles can play")
    private int particleDistance = 20;
    @DependsOn({"particleEffect"})
    @MinNumber(0)
    @MaxNumber(128)
    @Desc("How wide the particles can play (player's view left and right) RADIUS")
    private int particleDistanceWidth = 24;
    @DependsOn({"particleEffect"})
    @Desc("An extra value for some particles... Which bukkit doesn't even document.")
    private double extra = 0;
    @DependsOn({"potionEffect"})
    @MinNumber(-1)
    @MaxNumber(1024)
    @Desc("The Potion Strength or -1 to disable")
    private int potionStrength = -1;
    @DependsOn({"potionEffect", "potionTicksMin"})
    @MinNumber(1)
    @Desc("The max time the potion will last for")
    private int potionTicksMax = 155;
    @DependsOn({"potionEffect", "potionTicksMax"})
    @MinNumber(1)
    @Desc("The min time the potion will last for")
    private int potionTicksMin = 75;
    @Required
    @MinNumber(0)
    @Desc("The effect interval in milliseconds")
    private int interval = 150;
    @DependsOn({"particleEffect"})
    @MinNumber(0)
    @MaxNumber(16)
    @Desc("The effect distance start away")
    private int particleAway = 5;
    @Required
    @MinNumber(1)
    @Desc("The chance is 1 in CHANCE per interval")
    private int chance = 50;
    @Desc("Run commands, with configurable location parameters")
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

                part.setY(Math.round(g.getHeight(part.getBlockX(), part.getBlockZ())) + 1);
                part.add(RNG.r.d(), 0, RNG.r.d());
                int offset = p.getWorld().getMinHeight();
                if (extra != 0) {
                    p.spawnParticle(particleType, part.getX(), part.getY() + offset + RNG.r.i(particleOffset),
                            part.getZ(),
                            particleCount,
                            randomAltX ? RNG.r.d(-particleAltX, particleAltX) : particleAltX,
                            randomAltY ? RNG.r.d(-particleAltY, particleAltY) : particleAltY,
                            randomAltZ ? RNG.r.d(-particleAltZ, particleAltZ) : particleAltZ,
                            extra);
                } else {
                    p.spawnParticle(particleType, part.getX(), part.getY() + offset + RNG.r.i(particleOffset), part.getZ(),
                            particleCount,
                            randomAltX ? RNG.r.d(-particleAltX, particleAltX) : particleAltX,
                            randomAltY ? RNG.r.d(-particleAltY, particleAltY) : particleAltY,
                            randomAltZ ? RNG.r.d(-particleAltZ, particleAltZ) : particleAltZ);
                }
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
}
