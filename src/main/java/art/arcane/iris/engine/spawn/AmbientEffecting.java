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
import art.arcane.iris.engine.loot.BlockBiomes;
import art.arcane.iris.engine.loot.PlatformKeys;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.ModuleLog;
import com.volmit.config.Level;
import com.volmit.entity.EntitySpec;
import com.volmit.resource.ResourceId;
import com.volmit.service.AmbientEffects;
import com.volmit.geometry.BlockPosition;
import com.volmit.world.PackWorld;
import com.volmit.world.RunningWorld;
import com.volmit.world.RunningWorld.Sky;

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
        RunningWorld running = world.running().orElseThrow();
        EffectTimers timers = world.state(EffectTimers.class, ignored -> new EffectTimers());
        Optional<Sky> sky = running.sky();

        List<Effect> due = new ArrayList<>();
        addDue(due, effectsOf(running.settings().at(EffectSettings.class, at.x(), at.z()).level(Level.REGION)), timers, sky, world.log());
        addDue(due, effectsOf(BlockBiomes.declaredAt(running, EffectSettings.class, at)), timers, sky, world.log());
        return due;
    }

    private static List<AmbientEffect> effectsOf(Optional<EffectSettings> declared) {
        return declared.map(EffectSettings::effects).orElse(List.of());
    }

    private static void addDue(List<Effect> due, List<AmbientEffect> effects, EffectTimers timers, Optional<Sky> sky, ModuleLog log) {
        for (AmbientEffect effect : effects) {
            if (timers.shouldPlayNow(effect)) {
                due.add(play(effect, sky, log));
            }
        }
    }

    private static Effect play(AmbientEffect effect, Optional<Sky> sky, ModuleLog log) {
        Optional<Sound> sound = PlatformKeys.id(effect.sound(), log).map(key -> soundOf(key, effect));
        Optional<Particles> particles = PlatformKeys.id(effect.particleEffect(), log).map(key -> particlesOf(key, effect));
        return new Effect(sound, particles, commandsOf(effect.commandRegistry(), sky), potionOf(effect, log));
    }

    private static Sound soundOf(ResourceId key, AmbientEffect effect) {
        int distance = effect.soundDistance();
        int offsetX = RNG.r.i(-distance, distance);
        int offsetY = RNG.r.i(-distance, distance);
        int offsetZ = RNG.r.i(-distance, distance);
        float pitch = (float) RNG.r.d(effect.minPitch(), effect.maxPitch());
        return new Sound(key, offsetX, offsetY, offsetZ, (float) effect.volume(), pitch);
    }

    private static Particles particlesOf(ResourceId key, AmbientEffect effect) {
        double forward = RNG.r.i(effect.particleDistance()) + effect.particleAway();
        double sideways = RNG.r.d(-effect.particleDistanceWidth(), effect.particleDistanceWidth());
        Spot spot = new Spot(forward, sideways, RNG.r.d(), RNG.r.d(), RNG.r.i(effect.particleOffset()));
        Spread spread = new Spread(randomized(effect.particleAltX(), effect.randomAltX()),
                randomized(effect.particleAltY(), effect.randomAltY()), randomized(effect.particleAltZ(), effect.randomAltZ()));
        return new Particles(key, spot, effect.particleCount(), spread, effect.extra());
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

    private static Optional<Potion> potionOf(AmbientEffect effect, ModuleLog log) {
        if (effect.potionStrength() <= -1) {
            return Optional.empty();
        }

        int shortest = Math.min(effect.potionTicksMax(), effect.potionTicksMin());
        int longest = Math.max(effect.potionTicksMax(), effect.potionTicksMin());
        int ticks = RNG.r.i(shortest, longest);
        return PlatformKeys.id(effect.potionEffect(), log).map(key -> new Potion(key, effect.potionStrength(), ticks));
    }

    private static double commandOffset(double offset, boolean random) {
        return randomized(offset, random);
    }

    private static double randomized(double value, boolean random) {
        return random ? RNG.r.d(-value, value) : value;
    }
}
