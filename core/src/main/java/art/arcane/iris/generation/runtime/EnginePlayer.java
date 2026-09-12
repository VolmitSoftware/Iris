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

package art.arcane.iris.generation.runtime;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.world.entity.IrisEffect;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.world.history.SavedBiomeUnavailableException;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.math.M;
import lombok.Data;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@Data
public class EnginePlayer {
    private final Engine engine;
    private final Player player;
    private BiomeEnvironment environment;
    private IrisBiome biome;
    private IrisRegion region;
    private Location lastLocation;
    private long lastSample;

    public EnginePlayer(Engine engine, Player player) {
        this.engine = engine;
        this.player = player;
        lastLocation = null;
        lastSample = -1;
    }

    public void tick() {
        J.runEntity(player, this::tickOnEntity);
    }

    private void tickOnEntity() {
        if (sample() || !IrisSettings.get().getWorld().isEffectSystem()) {
            return;
        }

        try (BiomeEnvironment.Scope ignored = engine.openBiomeEnvironmentScope(environment)) {
            applyEffects();
        }
    }

    private void applyEffects() {
        if (region != null) {
            for (IrisEffect effect : region.getEffects()) {
                try {
                    effect.apply(player, getEngine());
                } catch (Throwable e) {
                    IrisLogging.reportError(e);

                }
            }
        }

        if (biome != null) {
            for (IrisEffect effect : biome.getEffects()) {
                try {
                    effect.apply(player, getEngine());
                } catch (Throwable e) {
                    IrisLogging.reportError(e);

                }
            }
        }
    }

    public long ticksSinceLastSample() {
        return M.ms() - lastSample;
    }

    public boolean sample() {
        Location current = player.getLocation().clone();
        if (current.getWorld() != BukkitWorldBinding.world(engine.getWorld())) {
            return true;
        }
        try {
            boolean sampled = lastLocation != null;
            double distanceSquared = sampled ? current.distanceSquared(lastLocation) : 0D;
            if (needsSample(sampled, ticksSinceLastSample(), distanceSquared)) {
                BiomeEnvironment sampledEnvironment = engine.getBiomeEnvironment(current.getBlockX(),
                        current.getBlockY() - engine.getWorld().minHeight(), current.getBlockZ());
                environment = sampledEnvironment;
                biome = sampledEnvironment.biome();
                region = sampledEnvironment.region();
                lastLocation = current;
                lastSample = M.ms();
            }
            return false;
        } catch (SavedBiomeUnavailableException e) {
            environment = null;
            biome = null;
            region = null;
            lastLocation = null;
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }
        return true;
    }

    static boolean needsSample(boolean sampled, long elapsedMillis, double distanceSquared) {
        return !sampled || elapsedMillis > 55L && distanceSquared > 81D;
    }
}
