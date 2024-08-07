/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine.object.matter;

import com.volmit.iris.engine.IrisEngine;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.util.function.Function3;

@Desc("WHERE THINGS PLACE")
public enum IrisMatterPlacementLocation {
    SURFACE((e, x, z) -> e.getHeight(x, z, true)),
    SURFACE_ON_FLUID((e, x, z) -> e.getHeight(x, z, false)),
    BEDROCK((e, x, z) -> 0),
    SKY((e, x, z) -> e.getHeight());

    private final Function3<IrisEngine, Integer, Integer, Integer> computer;

    IrisMatterPlacementLocation(Function3<IrisEngine, Integer, Integer, Integer> computer) {
        this.computer = computer;
    }

    public int at(IrisEngine engine, int x, int z) {
        return computer.apply(engine, x, z);
    }
}
