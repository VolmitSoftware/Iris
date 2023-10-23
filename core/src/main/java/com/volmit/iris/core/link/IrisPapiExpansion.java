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

package com.volmit.iris.core.link;

import com.volmit.iris.Iris;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.platform.PlatformChunkGenerator;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

// See/update https://app.gitbook.com/@volmitsoftware/s/iris/compatability/papi/
public class IrisPapiExpansion extends PlaceholderExpansion {
    @Override
    public @NotNull String getIdentifier() {
        return "iris";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Volmit Software";
    }

    @Override
    public @NotNull String getVersion() {
        return Iris.instance.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return false;
    }

    @Override
    public String onRequest(OfflinePlayer player, String p) {
        Location l = null;
        PlatformChunkGenerator a = null;

        if (player.isOnline() && player.getPlayer() != null) {
            l = player.getPlayer().getLocation().add(0, 2, 0);
            a = IrisToolbelt.access(l.getWorld());
        }

        if (p.equalsIgnoreCase("biome_name")) {
            if (a != null) {
                return getBiome(a, l).getName();
            }
        } else if (p.equalsIgnoreCase("biome_id")) {
            if (a != null) {
                return getBiome(a, l).getLoadKey();
            }
        } else if (p.equalsIgnoreCase("biome_file")) {
            if (a != null) {
                return getBiome(a, l).getLoadFile().getPath();
            }
        } else if (p.equalsIgnoreCase("region_name")) {
            if (a != null) {
                return a.getEngine().getRegion(l).getName();
            }
        } else if (p.equalsIgnoreCase("region_id")) {
            if (a != null) {
                return a.getEngine().getRegion(l).getLoadKey();
            }
        } else if (p.equalsIgnoreCase("region_file")) {
            if (a != null) {
                return a.getEngine().getRegion(l).getLoadFile().getPath();
            }
        } else if (p.equalsIgnoreCase("terrain_slope")) {
            if (a != null) {
                return (a.getEngine())
                        .getComplex().getSlopeStream()
                        .get(l.getX(), l.getZ()) + "";
            }
        } else if (p.equalsIgnoreCase("terrain_height")) {
            if (a != null) {
                return Math.round(a.getEngine().getHeight(l.getBlockX(), l.getBlockZ())) + "";
            }
        } else if (p.equalsIgnoreCase("world_mode")) {
            if (a != null) {
                return a.isStudio() ? "Studio" : "Production";
            }
        } else if (p.equalsIgnoreCase("world_seed")) {
            if (a != null) {
                return a.getEngine().getSeedManager().getSeed() + "";
            }
        } else if (p.equalsIgnoreCase("world_speed")) {
            if (a != null) {
                return a.getEngine().getGeneratedPerSecond() + "/s";
            }
        }

        return null;
    }

    private IrisBiome getBiome(PlatformChunkGenerator a, Location l) {
        return a.getEngine().getBiome(l.getBlockX(), l.getBlockY() - l.getWorld().getMinHeight(), l.getBlockZ());
    }
}
