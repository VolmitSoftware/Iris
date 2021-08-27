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

import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.mantle.MantleWriter;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.engine.object.block.IrisBlockData;
import com.volmit.iris.engine.object.common.IRare;
import com.volmit.iris.engine.object.noise.IrisGeneratorStyle;
import com.volmit.iris.engine.object.noise.IrisStyledRange;
import com.volmit.iris.engine.object.noise.NoiseStyle;
import com.volmit.iris.util.math.RNG;
import lombok.Data;

@Desc("Represents an procedural eliptical shape")
@Data
public class IrisPyramid implements IRare
{
    @Required
    @Desc("Typically a 1 in RARITY on a per fork basis")
    @MinNumber(1)
    private int rarity = 1;

    @Desc("Change the air block to fill elipsoids with as caves")
    private IrisBlockData fill = new IrisBlockData("cave_air");

    @Desc("The styled random radius for x")
    private IrisStyledRange baseWidth = new IrisStyledRange(1, 5, new IrisGeneratorStyle(NoiseStyle.STATIC));

    public void generate(RNG rng, Engine engine, MantleWriter writer, int x, int y, int z)
    {
        writer.setPyramid(x, y, z, fill.getBlockData(engine.getData()),
                (int)baseWidth.get(rng, z, y, engine.getData()), true);
    }

    public double maxSize() {
        return baseWidth.getMax();
    }
}
