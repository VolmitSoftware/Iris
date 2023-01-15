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

package com.volmit.iris.engine.object;

import com.volmit.iris.Iris;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.util.collection.KList;
import lombok.*;
import lombok.experimental.Accessors;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.generator.WorldInfo;

import java.io.File;
import java.util.Collection;
import java.util.List;

@Builder
@Data
@Accessors(chain = true, fluent = true)
public class IrisWorld {
    private static final KList<Player> NO_PLAYERS = new KList<>();
    private static final KList<? extends Entity> NO_ENTITIES = new KList<>();
    private String name;
    private File worldFolder;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private long seed;
    private World.Environment environment;
    private World realWorld;
    private int minHeight;
    private int maxHeight;

    public static IrisWorld fromWorld(World world) {
        return bindWorld(IrisWorld.builder().build(), world);
    }

    private static IrisWorld bindWorld(IrisWorld iw, World world) {
        return iw.name(world.getName())
                .worldFolder(world.getWorldFolder())
                .minHeight(world.getMinHeight())
                .maxHeight(world.getMaxHeight())
                .realWorld(world)
                .environment(world.getEnvironment());
    }

    public long getRawWorldSeed() {
        return seed;
    }

    public void setRawWorldSeed(long seed) {
        this.seed = seed;
    }

    public boolean tryGetRealWorld() {
        if (hasRealWorld()) {
            return true;
        }

        World w = Bukkit.getWorld(name);

        if (w != null) {
            realWorld = w;
            return true;
        }

        return false;
    }

    public boolean hasRealWorld() {
        return realWorld != null;
    }

    public List<Player> getPlayers() {

        if (hasRealWorld()) {
            return realWorld().getPlayers();
        }

        return NO_PLAYERS;
    }

    public void evacuate() {
        if (hasRealWorld()) {
            IrisToolbelt.evacuate(realWorld());
        }
    }

    public void bind(WorldInfo worldInfo) {
        name(worldInfo.getName())
                .worldFolder(new File(Bukkit.getWorldContainer(), worldInfo.getName()))
                .minHeight(worldInfo.getMinHeight())
                .maxHeight(worldInfo.getMaxHeight())
                .environment(worldInfo.getEnvironment());
    }

    public void bind(World world) {
        if (hasRealWorld()) {
            return;
        }

        bindWorld(this, world);
    }

    public Location spawnLocation() {
        if (hasRealWorld()) {
            return realWorld().getSpawnLocation();
        }

        Iris.error("This world is not real yet, cannot get spawn location! HEADLESS!");
        return null;
    }

    public <T extends Entity> Collection<? extends T> getEntitiesByClass(Class<T> t) {
        if (hasRealWorld()) {
            return realWorld().getEntitiesByClass(t);
        }

        return (KList<? extends T>) NO_ENTITIES;
    }

    public int getHeight() {
        return maxHeight - minHeight;
    }
}
