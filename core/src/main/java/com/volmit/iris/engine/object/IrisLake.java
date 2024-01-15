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
import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("lake")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents an Iris Lake")
@Data
public class IrisLake implements IRare {
    @Required
    @Desc("Typically a 1 in RARITY on a per chunk/fork basis")
    @MinNumber(1)
    private int rarity = 15;

    @Desc("The width style of this lake")
    private IrisShapedGeneratorStyle widthStyle = new IrisShapedGeneratorStyle(NoiseStyle.PERLIN.style(), 5, 9);

    @Desc("The depth style of this lake")
    private IrisShapedGeneratorStyle depthStyle = new IrisShapedGeneratorStyle(NoiseStyle.PERLIN.style(), 4, 7);

    @Desc("Define the shape of this lake")
    private IrisWorm worm = new IrisWorm();

    @RegistryListResource(IrisBiome.class)
    @Desc("Force this lake to only generate the specified custom biome")
    private String customBiome = "";

    public int getSize(IrisData data) {
        return worm.getMaxDistance();
    }

    public void generate(MantleWriter writer, RNG rng, Engine engine, int x, int y, int z) {

    }
}
