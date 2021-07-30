/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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
import com.volmit.iris.core.tools.IrisWorlds;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.IrisAccess;
import com.volmit.iris.engine.object.common.IrisWorld;
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
        IrisAccess a = null;

        if(player.isOnline())
        {
            l = player.getPlayer().getLocation();
            a = IrisWorlds.access(l.getWorld());
        }

        if (p.equalsIgnoreCase("biome_name"))
        {
            if(a != null)
            {
                return a.getBiome(l).getName();
            }
        }

        else if (p.equalsIgnoreCase("biome_id"))
        {
            if(a != null)
            {
                return a.getBiome(l).getLoadKey();
            }
        }

        else if (p.equalsIgnoreCase("biome_file"))
        {
            if(a != null)
            {
                return a.getBiome(l).getLoadFile().getPath();
            }
        }

        else if (p.equalsIgnoreCase("region_name"))
        {
            if(a != null)
            {
                return a.getRegion(l).getName();
            }
        }

        else if (p.equalsIgnoreCase("region_id"))
        {
            if(a != null)
            {
                return a.getRegion(l).getLoadKey();
            }
        }

        else if (p.equalsIgnoreCase("region_file"))
        {
            if(a != null)
            {
                return a.getRegion(l).getLoadFile().getPath();
            }
        }

        else if (p.equalsIgnoreCase("terrain_slope"))
        {
            if(a != null)
            {
                return ((Engine)a.getEngineAccess(l.getBlockY()))
                        .getFramework().getComplex().getSlopeStream()
                        .get(l.getX(), l.getZ()) + "";
            }
        }

        else if (p.equalsIgnoreCase("terrain_height"))
        {
            if(a != null)
            {
                return (int)Math.round(a.getHeight(l)) + "";
            }
        }

        else if (p.equalsIgnoreCase("world_mode"))
        {
            if(a != null)
            {
                return a.isStudio() ? "Studio" : "Production";
            }
        }

        else if (p.equalsIgnoreCase("world_seed"))
        {
            if(a != null)
            {
                return a.getTarget().getWorld().seed() + "";
            }
        }

        else if (p.equalsIgnoreCase("world_speed"))
        {
            if(a != null)
            {
                return a.getGeneratedPerSecond() + "/s";
            }
        }

        return null;
    }
}
