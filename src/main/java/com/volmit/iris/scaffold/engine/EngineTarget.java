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

package com.volmit.iris.scaffold.engine;

import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.scaffold.parallax.ParallaxWorld;
import com.volmit.iris.scaffold.parallel.MultiBurst;
import lombok.Data;
import org.bukkit.World;

import java.io.File;

@Data
public class EngineTarget {
    private final MultiBurst burster;
    private final IrisDimension dimension;
    private World world;
    private final int height;
    private final IrisDataManager data;
    private final ParallaxWorld parallaxWorld;
    private final boolean inverted;

    public EngineTarget(World world, IrisDimension dimension, IrisDataManager data, int height, boolean inverted, int threads) {
        this.world = world;
        this.height = height;
        this.dimension = dimension;
        this.data = data;
        // TODO: WARNING HEIGHT
        this.parallaxWorld = new ParallaxWorld(256, new File(world.getWorldFolder(), "iris/" + dimension.getLoadKey() + "/parallax"));
        this.inverted = inverted;
        this.burster = new MultiBurst(threads);
    }

    public void updateWorld(World world) {
        this.world = world;
    }

    public EngineTarget(World world, IrisDimension dimension, IrisDataManager data, int height, int threads) {
        this(world, dimension, data, height, false, threads);
    }
}
