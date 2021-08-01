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

import com.volmit.iris.engine.cache.AtomicCache;
import com.volmit.iris.engine.hunk.Hunk;
import com.volmit.iris.engine.interpolation.IrisInterpolation;
import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.engine.object.common.IRare;
import com.volmit.iris.engine.stream.ProceduralStream;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a cavern system")
@Data
public class IrisCaverns {
    @ArrayType(type = IrisCavernZone.class, min = 1)
    @Desc("Define different cavern zones")
    private KList<IrisCavernZone> zones = new KList<>();

    @Desc("The 3D interpolator to connect caverns")
    private IrisInterpolator3D interpolator = new IrisInterpolator3D();

    @Desc("Defines how cavern zones are placed in the world")
    private IrisGeneratorStyle zoneStyle = new IrisGeneratorStyle(NoiseStyle.CELLULAR);

    @Desc("Threshold defined")
    private double bottomBleed = 16;

    @Desc("Threshold defined")
    private double topBleed = 16;

    private transient AtomicCache<ProceduralStream<IrisCavernZone>> zonesRarity = new AtomicCache<>();

    public IrisCavernZone getZone(double x, double y, double z, RNG rng)
    {
        return zonesRarity.aquire(() -> zoneStyle.create(rng)
                .stream().selectRarity(getZones())).get(x,y,z);
    }

    private double threshold(double y, double th)
    {
        double m = 0;
        double a = 0;
        double top = th - topBleed;
        double bot = 0 + bottomBleed;

        if(y >= top && y <= th)
        {
            m++;
            a+= IrisInterpolation.lerpBezier(1, 0, M.lerpInverse(top, th, y));
        }

        if(y <= bottomBleed && y >= 0)
        {
            m++;
            a+= IrisInterpolation.lerpBezier(1, 0, M.lerpInverse(top, th, y));
        }

        return m==0 ? 1 : (a/m);
    }

    public <T> void apply(double ox, double oy, double oz, Hunk<T> hunk, T cave, RNG rng, ProceduralStream<Double> height)
    {
        hunk.iterateSync((x,y,z) -> {
            if(getInterpolator().interpolate(ox+x,oy+y,oz+z,(xx,yy,zz)
                    -> getZone(xx,yy,zz, rng).isCarved(rng, xx,yy,zz) ? 1 : 0) > threshold(y + oy, height.get(ox + x, oz + z)))
            {
                hunk.set(x,y,z,cave);
            }
        });
    }
}
