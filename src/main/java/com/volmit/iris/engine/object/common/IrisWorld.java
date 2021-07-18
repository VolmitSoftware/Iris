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

import com.volmit.iris.util.collection.KList;
import lombok.Builder;
import lombok.Data;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;

@Builder
@Data
public class IrisWorld {
    private static final KList<Player> NO_PLAYERS = new KList<>();
    private String name;
    private File worldFolder;
    private long seed;
    private World.Environment environment;
    private World realWorld;

    public static IrisWorld fromWorld(World world)
    {
        return IrisWorld.builder()
                .name(world.getName())
                .worldFolder(world.getWorldFolder())
                .seed(world.getSeed())
                .environment(world.getEnvironment())
                .build();
    }

    public boolean hasRealWorld()
    {
        return realWorld != null;
    }

    public Iterable<? extends Player> getPlayers() {

        if(hasRealWorld())
        {
            return getRealWorld().getPlayers();
        }

        return NO_PLAYERS;
    }
}
