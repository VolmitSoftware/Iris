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

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.mantle.MantleWriter;
import com.volmit.iris.engine.object.annotations.ArrayType;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.Snippet;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("fluid-bodies")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a fluid body configuration")
@Data
public class IrisFluidBodies {
    @ArrayType(type = IrisRiver.class, min = 1)
    @Desc("Define rivers")
    private KList<IrisRiver> rivers = new KList<>();

    @ArrayType(type = IrisLake.class, min = 1)
    @Desc("Define lakes")
    private KList<IrisLake> lakes = new KList<>();

    @BlockCoordinates
    public void generate(MantleWriter writer, RNG rng, Engine engine, int x, int y, int z) {
        if (rivers.isNotEmpty()) {
            for (IrisRiver i : rivers) {
                i.generate(writer, rng, engine, x, y, z);
            }
        }

        if (lakes.isNotEmpty()) {
            for (IrisLake i : lakes) {
                i.generate(writer, rng, engine, x, y, z);
            }
        }
    }

    public int getMaxRange(IrisData data) {
        int max = 0;

        for (IrisRiver i : rivers) {
            max = Math.max(max, i.getSize(data));
        }

        for (IrisLake i : lakes) {
            max = Math.max(max, i.getSize(data));
        }


        return max;
    }
}
