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

package art.arcane.iris.platform.bukkit;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.data.registry.RegistryUtil;
import art.arcane.iris.util.common.scheduling.ConsoleCommands;
import art.arcane.iris.util.common.scheduling.J;
import com.volmit.entity.EntitySpec;
import com.volmit.service.AmbientEffects;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Plays the ambient effects a module decided on to a Bukkit player: the sound, the particles, the commands and the
 * potion effect of each, in that order. Everything random was decided by the module; what the platform lacks is skipped.
 */
public final class BukkitAmbientEffects {
    private static final double QUARTER_TURN_RADIANS = Math.toRadians(90);
    private static final int ABOVE_SURFACE_BLOCKS = 1;

    private BukkitAmbientEffects() {
    }

    public static void play(Player player, Engine engine, List<AmbientEffects.Effect> effects) {
        for (AmbientEffects.Effect effect : effects) {
            J.runEntity(player, () -> playOnEntityThread(player, engine, effect));
        }
    }

    private static void playOnEntityThread(Player player, Engine engine, AmbientEffects.Effect effect) {
        effect.sound().ifPresent(sound -> playSound(player, sound));
        effect.particles().ifPresent(particles -> showParticles(player, engine, particles));
        effect.commands().forEach(command -> runCommand(player, command));
        effect.potion().ifPresent(potion -> givePotion(player, potion));
    }

    private static void playSound(Player player, AmbientEffects.Sound sound) {
        Sound type = lookup(Sound.class, sound.key());
        if (type == null) {
            return;
        }
        Location at = player.getLocation().clone().add(sound.offsetX(), sound.offsetY(), sound.offsetZ());
        player.playSound(at, type, sound.volume(), sound.pitch());
    }

    private static void showParticles(Player player, Engine engine, AmbientEffects.Particles particles) {
        Particle type = lookup(Particle.class, particles.key());
        if (type == null) {
            return;
        }
        Location location = player.getLocation();
        Vector direction = location.getDirection();
        Location spot = location.clone()
                .add(direction.clone().multiply(particles.forward()))
                .add(direction.clone().rotateAroundY(QUARTER_TURN_RADIANS).multiply(particles.sideways()));
        spot.setY(Math.round(engine.getHeight(spot.getBlockX(), spot.getBlockZ())) + ABOVE_SURFACE_BLOCKS);
        spot.add(particles.jitterX(), 0, particles.jitterZ());
        double x = spot.getX();
        double y = spot.getY() + player.getWorld().getMinHeight() + particles.lift();
        double z = spot.getZ();
        if (particles.extra() != 0) {
            player.spawnParticle(type, x, y, z, particles.count(), particles.altX(), particles.altY(), particles.altZ(), particles.extra());
            return;
        }
        player.spawnParticle(type, x, y, z, particles.count(), particles.altX(), particles.altY(), particles.altZ());
    }

    private static void runCommand(Player player, AmbientEffects.Command command) {
        Location at = player.getLocation().clone().add(command.offsetX(), command.offsetY(), command.offsetZ());
        EntitySpec.Command run = command.command();
        ConsoleCommands.run(run.commands(), run.delay(), run.repeat(), run.repeatDelay(), at.getBlockX(), at.getBlockY(), at.getBlockZ());
    }

    private static void givePotion(Player player, AmbientEffects.Potion potion) {
        PotionEffectType type = potionType(potion.key());
        PotionEffect current = player.getPotionEffect(type);
        if (current != null) {
            if (current.getAmplifier() > potion.strength()) {
                return;
            }
            player.removePotionEffect(type);
        }

        player.addPotionEffect(new PotionEffect(type, potion.ticks(), potion.strength(), true, false, false));
    }

    /** The potion effect type a pack names, or luck for a name the platform does not know, with a warning once. */
    private static PotionEffectType potionType(String name) {
        if (name == null || name.isEmpty()) {
            return PotionEffectType.LUCK;
        }

        try {
            PotionEffectType resolved = PotionEffectTypes.resolve(name);
            if (resolved != null) {
                return resolved;
            }
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }

        if (PotionEffectTypes.shouldWarn(name)) {
            IrisLogging.warn("Unknown Potion Effect Type: \"" + name + "\". Valid types: " + PotionEffectTypes.knownTypesList());
        }
        return PotionEffectType.LUCK;
    }

    private static <T> T lookup(Class<T> type, String key) {
        NamespacedKey namespacedKey = NamespacedKey.fromString(key);
        return namespacedKey == null ? null : RegistryUtil.lookup(type).get(namespacedKey);
    }
}
