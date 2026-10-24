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

import art.arcane.iris.builtin.LiveWorld;
import art.arcane.iris.builtin.LiveWorld.Level;
import art.arcane.iris.builtin.LiveWorld.Sky;
import art.arcane.iris.builtin.spawn.AmbientEffect;
import art.arcane.iris.builtin.spawn.EffectCommands;
import art.arcane.iris.builtin.spawn.EffectSettings;
import art.arcane.iris.builtin.spawn.SpawnCommand;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.entity.EntitySpec;
import com.volmit.service.AmbientEffects;
import com.volmit.geometry.BlockPosition;
import com.volmit.world.PackWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * How {@code iris:spawn} plays the ambient effects of regions and biomes: the region's effects, then the biome's, each
 * when its interval and chance say, with every random choice made here so the host only carries them out.
 */
public final class AmbientEffecting implements AmbientEffects {
    @Override
    public List<Effect> due(PackWorld world, BlockPosition at) {
        LiveWorld live = world.extension(LiveWorld.class).orElseThrow();
        EffectTimers timers = world.state(EffectTimers.class, ignored -> new EffectTimers());
        Optional<Sky> sky = live.sky();

        List<Effect> due = new ArrayList<>();
        addDue(due, effectsOf(live, Level.REGION, at), timers, sky);
        addDue(due, effectsOf(live, Level.BIOME, at), timers, sky);
        return due;
    }

    private static List<AmbientEffect> effectsOf(LiveWorld live, Level level, BlockPosition at) {
        return live.settings(EffectSettings.class, level, at.x(), at.y(), at.z()).map(EffectSettings::effects).orElse(List.of());
    }

    private static void addDue(List<Effect> due, List<AmbientEffect> effects, EffectTimers timers, Optional<Sky> sky) {
        for (AmbientEffect effect : effects) {
            if (timers.shouldPlayNow(effect)) {
                due.add(play(effect, sky));
            }
        }
    }

    private static Effect play(AmbientEffect effect, Optional<Sky> sky) {
        Optional<Sound> sound = effect.sound() == null ? Optional.empty() : Optional.of(soundOf(effect));
        Optional<Particles> particles = effect.particleEffect() == null ? Optional.empty() : Optional.of(particlesOf(effect));
        return new Effect(sound, particles, commandsOf(effect.commandRegistry(), sky), potionOf(effect));
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

    private static List<Command> commandsOf(EffectCommands registry, Optional<Sky> sky) {
        List<Command> commands = new ArrayList<>();
        if (registry == null) {
            return commands;
        }

        double offsetX = commandOffset(registry.commandOffsetX(), registry.commandRandomAltX());
        double offsetY = commandOffset(registry.commandOffsetY(), registry.commandRandomAltY());
        double offsetZ = commandOffset(registry.commandOffsetZ(), registry.commandRandomAltZ());
        for (SpawnCommand command : registry.rawCommands()) {
            if (sky.isPresent() && SpawnConditions.allow(command.timeBlock(), command.weather(), sky.get())) {
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

    private static Optional<Potion> potionOf(AmbientEffect effect) {
        if (effect.potionStrength() <= -1) {
            return Optional.empty();
        }

        int shortest = Math.min(effect.potionTicksMax(), effect.potionTicksMin());
        int longest = Math.max(effect.potionTicksMax(), effect.potionTicksMin());
        return Optional.of(new Potion(effect.potionEffect(), effect.potionStrength(), RNG.r.i(shortest, longest)));
    }

    private static double commandOffset(double offset, boolean random) {
        return randomized(offset, random);
    }

    private static double randomized(double value, boolean random) {
        return random ? RNG.r.d(-value, value) : value;
    }
}
