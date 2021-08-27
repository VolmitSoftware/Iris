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

package com.volmit.iris.engine.object.carving;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.mantle.MantleWriter;
import com.volmit.iris.engine.object.annotations.ArrayType;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a carving configuration")
@Data
public class IrisCarving {
    @ArrayType(type = IrisCavePlacer.class, min = 1)
    @Desc("Define cave placers")
    private KList<IrisCavePlacer> caves = new KList<>();

    @BlockCoordinates
    public void doCarving(MantleWriter writer, RNG rng, Engine engine, int x, int z) {
        if(caves.isNotEmpty())
        {
            for(IrisCavePlacer i : caves)
            {
                i.generateCave(writer, rng, engine, x, z);
            }
        }
    }

    public int getMaxRange(IrisData data) {
        int max = 0;

        for(IrisCavePlacer i : caves)
        {
            max = Math.max(max, i.getSize(data));
        }

        return max;
    }
}
