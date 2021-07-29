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

package com.volmit.iris.engine.framework;

import com.volmit.iris.core.IrisDataManager;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.engine.object.common.IrisWorld;
import com.volmit.iris.engine.parallax.ParallaxWorld;
import com.volmit.iris.engine.parallel.MultiBurst;
import lombok.Data;

import java.io.File;

@Data
public class EngineTarget {
    private final MultiBurst parallaxBurster;
    private final MultiBurst burster;
    private final IrisDimension dimension;
    private IrisWorld world;
    private final int height;
    private final IrisDataManager data;
    private final ParallaxWorld parallaxWorld;
    private final boolean inverted;

    public EngineTarget(IrisWorld world, IrisDimension dimension, IrisDataManager data, int height, boolean inverted, int threads) {
        this.world = world;
        this.height = height;
        this.dimension = dimension;
        this.data = data;
        this.inverted = inverted;
        this.burster = new MultiBurst("Iris Engine " + dimension.getName(), IrisSettings.get().getConcurrency().getEngineThreadPriority(), threads);
        this.parallaxBurster = new MultiBurst("Iris Parallax Engine " + dimension.getName(), 3, 4);
        this.parallaxWorld = new ParallaxWorld(parallaxBurster, 256, new File(world.worldFolder(), "iris/" + dimension.getLoadKey() + "/parallax"));
    }

    public EngineTarget(IrisWorld world, IrisDimension dimension, IrisDataManager data, int height, int threads) {
        this(world, dimension, data, height, false, threads);
    }

    public void close() {
        parallaxBurster.shutdownLater();
        burster.shutdownLater();
    }
}
