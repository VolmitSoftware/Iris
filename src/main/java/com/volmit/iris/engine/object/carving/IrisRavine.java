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

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.loader.IrisRegistrant;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.mantle.MantleWriter;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MaxNumber;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.RegistryListResource;
import com.volmit.iris.engine.object.basic.IrisPosition;
import com.volmit.iris.engine.object.basic.IrisRange;
import com.volmit.iris.engine.object.biome.IrisBiome;
import com.volmit.iris.engine.object.noise.IrisShapedGeneratorStyle;
import com.volmit.iris.engine.object.noise.IrisWorm;
import com.volmit.iris.engine.object.noise.NoiseStyle;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.matter.MatterCavern;
import com.volmit.iris.util.matter.slices.CavernMatter;
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
public class IrisRavine extends IrisRegistrant {
    @Desc("Define the shape of this ravine (2d, ignores Y)")
    private IrisWorm worm;

    @RegistryListResource(IrisBiome.class)
    @Desc("Force this cave to only generate the specified custom biome")
    private String customBiome = "";

    @Desc("Define potential forking features")
    private IrisCarving fork = new IrisCarving();

    @Desc("The style used to determine the curvature of this worm's y")
    private IrisShapedGeneratorStyle depthStyle = new IrisShapedGeneratorStyle(NoiseStyle.PERLIN, 5, 18);

    @Desc("The style used to determine the curvature of this worm's y")
    private IrisShapedGeneratorStyle baseWidthStyle = new IrisShapedGeneratorStyle(NoiseStyle.PERLIN, 3, 6);

    @MinNumber(1)
    @MaxNumber(70)
    @Desc("The angle at which the ravine widens as it gets closer to the surface")
    private double angle = 18;

    @MinNumber(1)
    @MaxNumber(70)
    @Desc("The angle at which the ravine widens as it gets closer to the surface")
    private double topAngle = 38;

    @Desc("How many worm nodes must be placed to actually generate a ravine? Higher reduces the chances but also reduces ravine 'holes'")
    private int nodeThreshold = 5;

    @MinNumber(1)
    @MaxNumber(8)
    @Desc("The thickness of the ravine ribs")
    private double ribThickness = 3;

    private transient final AtomicCache<MatterCavern> matterNodeCache = new AtomicCache<>();
    @Override
    public String getFolderName() {
        return "ravines";
    }

    @Override
    public String getTypeName() {
        return "Ravine";
    }

    public void generate(MantleWriter writer, RNG rng, Engine engine, int x, int y, int z) {

        KList<IrisPosition> pos = getWorm().generate(rng, engine.getData(), writer, null, x, y, z, (at) -> {});
        CNG dg = depthStyle.getGenerator().createNoCache(rng, engine.getData());
        CNG bw = baseWidthStyle.getGenerator().createNoCache(rng, engine.getData());

        if(pos.size() < nodeThreshold)
        {
            return;
        }

        for(IrisPosition p : pos)
        {
            int rsurface = y == -1 ? engine.getComplex().getHeightStream().get(x, z).intValue() : y;
            int depth = (int) Math.round(dg.fitDouble(depthStyle.getMin(), depthStyle.getMax(), p.getX(), p.getZ()));
            int width = (int) Math.round(bw.fitDouble(baseWidthStyle.getMin(), baseWidthStyle.getMax(), p.getX(), p.getZ()));
            int surface = (int) Math.round(rsurface - depth * 0.45);

            fork.doCarving(writer, rng, engine, p.getX(), rng.i(surface-depth, surface), p.getZ());

            for(int i = surface + depth; i >= surface; i--)
            {
                if(i % ribThickness == 0) {
                    double v = width + ((((surface + depth) - i) * (angle / 360D)));

                    if(v <= 0.25)
                    {
                        break;
                    }

                    if(i <= ribThickness+2)
                    {
                        break;
                    }

                    writer.setElipsoid(p.getX(), i, p.getZ(), v, ribThickness, v, true, matterNodeCache.aquire(() -> CavernMatter.get(getCustomBiome())));
                }
            }

            for(int i = surface - depth; i <= surface; i++)
            {
                if(i % ribThickness == 0) {
                    double v = width - ((((surface - depth) - i) * (angle / 360D)));

                    if(v <= 0.25)
                    {
                        break;
                    }

                    if(i <= ribThickness+2)
                    {
                        break;
                    }

                    writer.setElipsoid(p.getX(), i, p.getZ(), v, ribThickness, v, true, matterNodeCache.aquire(() -> CavernMatter.get(getCustomBiome())));
                }
            }
        }
    }

    @Override
    public void scanForErrors(JSONObject p, VolmitSender sender) {

    }

    public int getMaxSize(IrisData data) {
        return getWorm().getMaxDistance() + fork.getMaxRange(data);
    }
}
