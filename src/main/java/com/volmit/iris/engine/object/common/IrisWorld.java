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

package com.volmit.iris.engine.object.common;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.IrisWorlds;
import com.volmit.iris.util.collection.KList;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;

@Builder
@Data
@Accessors(chain = true, fluent = true)
public class IrisWorld {
    private static final KList<Player> NO_PLAYERS = new KList<>();
    private String name;
    private File worldFolder;
    private long seed;
    private World.Environment environment;
    private World realWorld;
    private int minHeight;
    private int maxHeight;

    public static IrisWorld fromWorld(World world)
    {
        return bindWorld(IrisWorld.builder().build(), world);
    }

    private static IrisWorld bindWorld(IrisWorld iw, World world)
    {
        return iw.name(world.getName())
            .worldFolder(world.getWorldFolder())
            .seed(world.getSeed())
            .minHeight(world.getMinHeight())
            .maxHeight(world.getMaxHeight())
            .realWorld(world)
            .environment(world.getEnvironment());
    }

    public boolean hasRealWorld()
    {
        return realWorld != null;
    }

    public Iterable<? extends Player> getPlayers() {

        if(hasRealWorld())
        {
            return realWorld().getPlayers();
        }

        return NO_PLAYERS;
    }

    public void evacuate() {
        if(hasRealWorld())
        {
            IrisWorlds.evacuate(realWorld());
        }
    }

    public void bind(World world) {
        bindWorld(this, world);
    }

    public Location spawnLocation() {
        if(hasRealWorld())
        {
            return realWorld().getSpawnLocation();
        }

        Iris.error("This world is not real yet, cannot get spawn location! HEADLESS!");
        return null;
    }
}
