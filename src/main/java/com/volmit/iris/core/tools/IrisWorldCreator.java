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

package com.volmit.iris.core.tools;

import com.sun.jna.Platform;
import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.engine.object.common.IrisWorld;
import com.volmit.iris.engine.object.dimensional.IrisDimension;
import com.volmit.iris.engine.platform.BukkitChunkGenerator;
import com.volmit.iris.engine.platform.PlatformChunkGenerator;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;

public class IrisWorldCreator {
    private String name;
    private boolean studio = false;
    private String dimensionName = null;
    private long seed = 1337;
    private int maxHeight = 256;
    private int minHeight = 0;

    public IrisWorldCreator() {

    }

    public IrisWorldCreator dimension(String loadKey) {
        this.dimensionName = loadKey;
        return this;
    }

    public IrisWorldCreator height(int maxHeight) {
        this.maxHeight = maxHeight;
        this.minHeight = 0;
        return this;
    }

    public IrisWorldCreator height(int minHeight, int maxHeight) {
        this.maxHeight = maxHeight;
        this.minHeight = minHeight;
        return this;
    }

    public IrisWorldCreator name(String name) {
        this.name = name;
        return this;
    }

    public IrisWorldCreator seed(long seed) {
        this.seed = seed;
        return this;
    }

    public IrisWorldCreator studioMode() {
        this.studio = true;
        return this;
    }

    public IrisWorldCreator productionMode() {
        this.studio = false;
        return this;
    }

    public WorldCreator create() {
        IrisWorld w = IrisWorld.builder()
                .name(name)
                .minHeight(minHeight)
                .maxHeight(maxHeight)
                .seed(seed)
                .worldFolder(new File(name))
                .environment(findEnvironment())
                .build();
        ChunkGenerator g = new BukkitChunkGenerator(w, studio, studio
                ? IrisData.loadAnyDimension(dimensionName).getLoader().getDataFolder():
                new File(w.worldFolder(), "iris/pack"), dimensionName);

        return new WorldCreator(name)
                .environment(findEnvironment())
                .generateStructures(true)
                .generator(g).seed(seed);
    }

    private World.Environment findEnvironment() {
        IrisDimension dim = IrisData.loadAnyDimension(dimensionName);
        if (dim == null || dim.getEnvironment() == null) {
            return World.Environment.NORMAL;
        } else {
            return dim.getEnvironment();
        }
    }

    public IrisWorldCreator studio(boolean studio) {
        this.studio = studio;
        return this;
    }
}
