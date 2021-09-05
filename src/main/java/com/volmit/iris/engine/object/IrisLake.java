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

package com.volmit.iris.engine.object;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.mantle.MantleWriter;
import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.matter.MatterFluidBody;
import com.volmit.iris.util.matter.slices.FluidBodyMatter;
import com.volmit.iris.util.noise.CNG;
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
        KList<IrisPosition> pos = getWorm().generate(rng, engine.getData(), writer, null, x, y, z, (at) -> {
        });
        CNG dg = depthStyle.getGenerator().createNoCache(rng, engine.getData());
        CNG bw = widthStyle.getGenerator().createNoCache(rng, engine.getData());
        IrisPosition avg;
        double ax = 0;
        double ay = 0;
        double az = 0;
        double[] surfaces = new double[pos.size()];
        int i = 0;

        for (IrisPosition p : pos) {
            surfaces[i] = engine.getComplex().getHeightStream().get(x, z);
            ax += p.getX();
            ay += surfaces[i];
            az += p.getZ();
            i++;
        }

        avg = new IrisPosition(ax / pos.size(), ay / pos.size(), az / pos.size());
        MatterFluidBody body = FluidBodyMatter.get(customBiome, false);
        i = 0;

        for (IrisPosition p : pos) {

            double surface = surfaces[i];
            double depth = dg.fitDouble(depthStyle.getMin(), depthStyle.getMax(), p.getX(), p.getZ()) + (surface - avg.getY());
            double width = bw.fitDouble(widthStyle.getMin(), widthStyle.getMax(), p.getX(), p.getZ());

            if (depth > 1) {
                writer.setElipsoidFunction(p.getX(), avg.getY(), p.getZ(), width, depth, width, true, (xx, yy, zz) -> {
                    if (yy > avg.getY()) {
                        return null;
                    }

                    return body;
                });
            }

            i++;
        }
    }
}
