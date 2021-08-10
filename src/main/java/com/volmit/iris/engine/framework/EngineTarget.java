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

import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.engine.object.common.IrisWorld;
import com.volmit.iris.engine.object.dimensional.IrisDimension;
import com.volmit.iris.util.parallel.MultiBurst;
import lombok.Data;

@Data
public class EngineTarget {
    private final MultiBurst burster;
    private final IrisDimension dimension;
    private IrisWorld world;
    private final IrisData data;

    public EngineTarget(IrisWorld world, IrisDimension dimension, IrisData data) {
        this.world = world;
        this.dimension = dimension;
        this.data = data;
        this.burster = new MultiBurst("Iris Engine " + dimension.getName(),
                IrisSettings.get().getConcurrency().getEngineThreadPriority(),
                IrisSettings.getThreadCount(IrisSettings.get().getConcurrency().getEngineThreadCount()));
    }

    public int getHeight() {
        return world.maxHeight() - world.minHeight();
    }

    public void close() {
        burster.shutdownLater();
    }
}
