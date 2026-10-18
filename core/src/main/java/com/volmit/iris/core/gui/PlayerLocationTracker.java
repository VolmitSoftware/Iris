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

package com.volmit.iris.core.gui;

import com.volmit.iris.Iris;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisRegion;
import com.volmit.iris.engine.platform.PlatformChunkGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.awt.EventQueue;
import java.util.UUID;

final class PlayerLocationTracker {
    private static final long CHECK_INTERVAL_TICKS = 10L;

    private final UUID playerId;
    private final Listener listener;
    private BukkitTask task;
    private PlayerLocation lastLocation;

    private PlayerLocationTracker(UUID playerId, Listener listener) {
        this.playerId = playerId;
        this.listener = listener;
    }

    static PlayerLocationTracker follow(UUID playerId, Listener listener) {
        PlayerLocationTracker tracker = new PlayerLocationTracker(playerId, listener);
        tracker.task = Bukkit.getScheduler().runTaskTimer(Iris.instance, tracker::checkLocation, 0L, CHECK_INTERVAL_TICKS);
        return tracker;
    }

    void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    private void checkLocation() {
        PlayerLocation location = locate();
        if (location.equals(lastLocation)) {
            return;
        }
        lastLocation = location;
        EventQueue.invokeLater(() -> listener.onLocationChanged(location));
    }

    private PlayerLocation locate() {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return PlayerLocation.OFFLINE;
        }
        Engine engine = engineAt(player.getLocation());
        if (engine == null) {
            return PlayerLocation.OUTSIDE_IRIS_WORLD;
        }
        Location position = player.getLocation();
        IrisRegion region = engine.getRegion(position);
        String surfaceBiome = keyOf(engine.getSurfaceBiome(position.getBlockX(), position.getBlockZ()));
        String biomeAtPosition = keyOf(engine.getBiomeOrMantle(position));
        if (region == null || region.getLoadKey() == null || surfaceBiome == null) {
            return PlayerLocation.OUTSIDE_IRIS_WORLD;
        }
        return new PlayerLocation(Status.IN_REGION, region.getLoadKey(), surfaceBiome, biomeAtPosition);
    }

    private static String keyOf(IrisBiome biome) {
        return biome == null ? null : biome.getLoadKey();
    }

    private static Engine engineAt(Location location) {
        if (location.getWorld() == null || !IrisToolbelt.isIrisWorld(location.getWorld())) {
            return null;
        }
        PlatformChunkGenerator generator = IrisToolbelt.access(location.getWorld());
        return generator == null ? null : generator.getEngine();
    }

    enum Status {
        OFFLINE,
        OUTSIDE_IRIS_WORLD,
        IN_REGION
    }

    // Biomes are 3D: underground, the biome at the player's position can differ from the surface biome above it.
    record PlayerLocation(Status status, String regionKey, String surfaceBiomeKey, String biomeAtPositionKey) {
        static final PlayerLocation OFFLINE = new PlayerLocation(Status.OFFLINE, null, null, null);
        static final PlayerLocation OUTSIDE_IRIS_WORLD = new PlayerLocation(Status.OUTSIDE_IRIS_WORLD, null, null, null);

        boolean isBelowSurfaceBiome() {
            return biomeAtPositionKey != null && !biomeAtPositionKey.equals(surfaceBiomeKey);
        }
    }

    interface Listener {
        void onLocationChanged(PlayerLocation location);
    }
}
