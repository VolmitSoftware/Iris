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
import com.volmit.iris.core.loader.IrisRegistrant;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.mantle.MantleWriter;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.RegistryListResource;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.matter.MatterCavern;
import com.volmit.iris.util.noise.CNG;
import com.volmit.iris.util.plugin.VolmitSender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Translate objects")
@Data
public class IrisCave extends IrisRegistrant {
    @Desc("Define the shape of this cave")
    private IrisWorm worm = new IrisWorm();

    @Desc("Define potential forking features")
    private IrisCarving fork = new IrisCarving();

    @RegistryListResource(IrisBiome.class)
    @Desc("Force this cave to only generate the specified custom biome")
    private String customBiome = "";

    @Desc("Limit the worm from ever getting higher or lower than this range")
    private IrisRange verticalRange = new IrisRange(3, 255);

    @Desc("Shape of the caves")
    private IrisCaveShape shape = new IrisCaveShape();

    @Override
    public String getFolderName() {
        return "caves";
    }

    @Override
    public String getTypeName() {
        return "Cave";
    }

    public void generate(MantleWriter writer, RNG rng, Engine engine, int x, int y, int z) {
        generate(writer, rng, new RNG(engine.getSeedManager().getCarve()), engine, x, y, z, 0, -1, true);
    }

    public void generate(MantleWriter writer, RNG rng, RNG base, Engine engine, int x, int y, int z, int recursion, int waterHint, boolean breakSurface) {
        double girth = getWorm().getGirth().get(base.nextParallelRNG(465156), x, z, engine.getData());
        KList<IrisPosition> points = getWorm().generate(base.nextParallelRNG(784684), engine.getData(), writer, verticalRange, x, y, z, breakSurface, girth + 9);
        int highestWater = Math.max(waterHint, -1);

        if (highestWater == -1) {
            for (IrisPosition i : points) {
                double yy = i.getY() + girth;
                int th = engine.getHeight(x, z, true);

                if (yy > th && th < engine.getDimension().getFluidHeight()) {
                    highestWater = Math.max(highestWater, (int) yy);
                    break;
                }
            }
        }


        int h = Math.min(highestWater, engine.getDimension().getFluidHeight());

        for (IrisPosition i : points) {
            fork.doCarving(writer, rng, base, engine, i.getX(), i.getY(), i.getZ(), recursion, h);
        }

        MatterCavern c = new MatterCavern(true, customBiome, (byte) 0);
        MatterCavern w = new MatterCavern(true, customBiome, (byte) 1);

        CNG cng = shape.getNoise(base.nextParallelRNG(8131545), engine);
        KSet<IrisPosition> mask = shape.getMasked(rng, engine);
        writer.setNoiseMasked(points,
                girth, shape.getNoiseThreshold() < 0 ? cng.noise(x, y, z) : shape.getNoiseThreshold(), cng, mask, true,
                (xf, yf, zf) -> yf <= h ? w : c);
    }

    @Override
    public void scanForErrors(JSONObject p, VolmitSender sender) {

    }

    public int getMaxSize(IrisData data, int depth) {
        return (int) (Math.ceil(getWorm().getGirth().getMax() * 2) + getWorm().getMaxDistance() + fork.getMaxRange(data, depth));
    }
}
