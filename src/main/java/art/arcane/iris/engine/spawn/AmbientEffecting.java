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

import art.arcane.iris.builtin.spawn.AmbientEffect;
import art.arcane.iris.builtin.spawn.EffectCommands;
import art.arcane.iris.builtin.spawn.EffectSettings;
import art.arcane.iris.builtin.spawn.SpawnCommand;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.core.modules.IrisPackWorld;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.spi.PlatformWorld;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.entity.EntitySpec;
import com.volmit.service.AmbientEffects;
import com.volmit.world.BlockPosition;
import com.volmit.world.PackWorld;

import java.util.ArrayList;
import java.util.List;

/**
 * How {@code iris:spawn} plays the ambient effects of regions and biomes: the region's effects, then the biome's, each
 * when its interval and chance say, with every random choice made here so the host only carries them out.
 */
public final class AmbientEffecting implements AmbientEffects {
    @Override
    public List<Effect> due(PackWorld world, BlockPosition at) {
        Engine engine = IrisPackWorld.of(world).engine();
        EffectTimers timers = world.state(EffectTimers.class, ignored -> new EffectTimers());
        PlatformWorld platformWorld = engine.getWorld().platformWorld();

        List<Effect> due = new ArrayList<>();
        addDue(due, effectsOf(engine.getRegion(at.x(), at.z())), timers, platformWorld);
        addDue(due, effectsOf(engine.getBiome(at.x(), at.y(), at.z())), timers, platformWorld);
        return due;
    }

    private static List<AmbientEffect> effectsOf(IrisRegistrant owner) {
        return owner.getModuleSettings().find(EffectSettings.class).map(EffectSettings::effects).orElse(List.of());
    }

    private static void addDue(List<Effect> due, List<AmbientEffect> effects, EffectTimers timers, PlatformWorld world) {
        for (AmbientEffect effect : effects) {
            if (timers.shouldPlayNow(effect)) {
                due.add(play(effect, world));
            }
        }
    }

    private static Effect play(AmbientEffect effect, PlatformWorld world) {
        Sound sound = effect.sound() == null ? null : soundOf(effect);
        Particles particles = effect.particleEffect() == null ? null : particlesOf(effect);
        return new Effect(sound, particles, commandsOf(effect.commandRegistry(), world), potionOf(effect));
    }

    private static Sound soundOf(AmbientEffect effect) {
        int distance = effect.soundDistance();
        int offsetX = RNG.r.i(-distance, distance);
        int offsetY = RNG.r.i(-distance, distance);
        int offsetZ = RNG.r.i(-distance, distance);
        float pitch = (float) RNG.r.d(effect.minPitch(), effect.maxPitch());
        return new Sound(effect.sound(), offsetX, offsetY, offsetZ, (float) effect.volume(), pitch);
    }

    private static Particles particlesOf(AmbientEffect effect) {
        double forward = RNG.r.i(effect.particleDistance()) + effect.particleAway();
        double sideways = RNG.r.d(-effect.particleDistanceWidth(), effect.particleDistanceWidth());
        double jitterX = RNG.r.d();
        double jitterZ = RNG.r.d();
        int lift = RNG.r.i(effect.particleOffset());
        double altX = randomized(effect.particleAltX(), effect.randomAltX());
        double altY = randomized(effect.particleAltY(), effect.randomAltY());
        double altZ = randomized(effect.particleAltZ(), effect.randomAltZ());
        return new Particles(effect.particleEffect(), forward, sideways, jitterX, jitterZ, lift, effect.particleCount(),
                altX, altY, altZ, effect.extra());
    }

    private static List<Command> commandsOf(EffectCommands registry, PlatformWorld world) {
        List<Command> commands = new ArrayList<>();
        if (registry == null) {
            return commands;
        }

        double offsetX = commandOffset(registry.commandOffsetX(), registry.commandRandomAltX());
        double offsetY = commandOffset(registry.commandOffsetY(), registry.commandRandomAltY());
        double offsetZ = commandOffset(registry.commandOffsetZ(), registry.commandRandomAltZ());
        for (SpawnCommand command : registry.rawCommands()) {
            if (SpawnConditions.allow(command.timeBlock(), command.weather(), world)) {
                commands.add(new Command(new EntitySpec.Command(command.commands(), command.delay(), command.repeat(),
                        command.repeatDelay()), offsetX, offsetY, offsetZ));
            }
            if (registry.commandAllRandomLocations()) {
                offsetX = commandOffset(registry.commandOffsetX(), registry.commandRandomAltX());
                offsetY = commandOffset(registry.commandOffsetY(), registry.commandRandomAltY());
                offsetZ = commandOffset(registry.commandOffsetZ(), registry.commandRandomAltZ());
            }
        }
        return commands;
    }

    private static Potion potionOf(AmbientEffect effect) {
        if (effect.potionStrength() <= -1) {
            return null;
        }

        int shortest = Math.min(effect.potionTicksMax(), effect.potionTicksMin());
        int longest = Math.max(effect.potionTicksMax(), effect.potionTicksMin());
        return new Potion(effect.potionEffect(), effect.potionStrength(), RNG.r.i(shortest, longest));
    }

    private static double commandOffset(double offset, boolean random) {
        return randomized(offset, random);
    }

    private static double randomized(double value, boolean random) {
        return random ? RNG.r.d(-value, value) : value;
    }
}
