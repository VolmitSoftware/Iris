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

package com.volmit.iris.engine.framework;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisEffect;
import com.volmit.iris.engine.object.IrisRegion;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.scheduling.J;
import lombok.Data;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@Data
public class EnginePlayer {
    private final Engine engine;
    private final Player player;
    private IrisBiome biome;
    private IrisRegion region;
    private Location lastLocation;
    private long lastSample;

    public EnginePlayer(Engine engine, Player player) {
        this.engine = engine;
        this.player = player;
        lastLocation = player.getLocation().clone();
        lastSample = -1;
        sample();
    }

    public void tick() {
        if (sample() || !IrisSettings.get().getWorld().isEffectSystem())
            return;

        J.a(() -> {
            if (region != null) {
                for (IrisEffect j : region.getEffects()) {
                    try {
                        j.apply(player, getEngine());
                    } catch (Throwable e) {
                        Iris.reportError(e);

                    }
                }
            }

            if (biome != null) {
                for (IrisEffect j : biome.getEffects()) {
                    try {
                        j.apply(player, getEngine());
                    } catch (Throwable e) {
                        Iris.reportError(e);

                    }
                }
            }
        });
    }

    public long ticksSinceLastSample() {
        return M.ms() - lastSample;
    }

    public boolean sample() {
        Location current = player.getLocation().clone();
        if (current.getWorld() != engine.getWorld().realWorld())
            return true;
        try {
            if (ticksSinceLastSample() > 55 && current.distanceSquared(lastLocation) > 9 * 9) {
                lastLocation = current;
                lastSample = M.ms();
                biome = engine.getBiome(current);
                region = engine.getRegion(current);
            }
            return false;
        } catch (Throwable e) {
            Iris.reportError(e);

        }
        return true;
    }
}
