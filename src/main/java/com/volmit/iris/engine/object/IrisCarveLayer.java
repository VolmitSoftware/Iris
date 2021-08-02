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

import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.engine.cache.AtomicCache;
import com.volmit.iris.engine.interpolation.IrisInterpolation;
import com.volmit.iris.engine.noise.CNG;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MaxNumber;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.engine.stream.ProceduralStream;
import com.volmit.iris.engine.stream.interpolation.Interpolated;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Translate objects")
@Data
public class IrisCarveLayer {
    @Required
    @Desc("The 4d slope this carve layer follows")
    private IrisGeneratorStyle style = new IrisGeneratorStyle();

    @MaxNumber(512)
    @MinNumber(-128)
    @Desc("The max height")
    private int maxHeight = 220;

    @MinNumber(0.0)
    @MaxNumber(1.0)
    @Desc("The full percentage means the 4D opacity of this carver will decay from 100% to 0% at the min & max vertical ranges. Setting the percent to 1.0 will make a very drastic & charp change at the edge of the vertical min & max. Where as 0.15 means only 15% of the vertical range will actually be 100% opacity.")
    private double fullPercent = 0.5;

    @MaxNumber(512)
    @MinNumber(-128)
    @Desc("The min height")
    private int minHeight = 147;

    @MaxNumber(1)
    @MinNumber(0)
    @Desc("The threshold used as: \n\ncarved = noise(x,y,z) > threshold")
    private double threshold = 0.5;

    private final transient AtomicCache<ProceduralStream<Boolean>> streamCache = new AtomicCache<>();
    private final transient AtomicCache<ProceduralStream<Double>> rawStreamCache = new AtomicCache<>();
    private final transient AtomicCache<CNG> cng = new AtomicCache<>();

    public boolean isCarved(RNG rng, IrisData data, double x, double y, double z) {
        if (y > getMaxHeight() || y < getMinHeight()) {
            return false;
        }

        double opacity = Math.pow(IrisInterpolation.sinCenter(M.lerpInverse(getMinHeight(), getMaxHeight(), y)), 4);
        return getCng(rng, data).fitDouble(0D, 1D, x, y, z) * opacity > getThreshold();
    }

    public ProceduralStream<Boolean> stream(RNG rng, IrisData data) {
        return streamCache.aquire(() -> ProceduralStream.of((x, y, z) -> isCarved(rng, data, x, y, z), Interpolated.BOOLEAN));
    }

    public ProceduralStream<Double> rawStream(RNG rng, IrisData data) {
        return rawStreamCache.aquire(() -> ProceduralStream.of((x, y, z) -> {
            return getCng(rng, data).fitDouble(0D, 1D, x, y, z) * Math.pow(IrisInterpolation.sinCenter(M.lerpInverse(getMinHeight(), getMaxHeight(), y)), 4);
        }, Interpolated.DOUBLE));
    }

    public CNG getCng(RNG rng, IrisData data) {
        return cng.aquire(() -> getStyle().create(rng.nextParallelRNG(-2340 * getMaxHeight() * getMinHeight()), data));
    }

    public boolean isCarved2(RNG rng, IrisData data, double x, double y, double z) {
        if (y > getMaxHeight() || y < getMinHeight()) {
            return false;
        }

        double innerRange = fullPercent * (maxHeight - minHeight);
        double opacity = 1D;

        if (y <= minHeight + innerRange) {
            opacity = IrisInterpolation.bezier(M.lerpInverse(getMinHeight(), minHeight + innerRange, y));
        } else if (y >= maxHeight - innerRange) {
            opacity = IrisInterpolation.bezier(1D - M.lerpInverse(maxHeight - innerRange, getMaxHeight(), y));
        }

        return cng.aquire(() -> getStyle().create(rng.nextParallelRNG(-2340 * getMaxHeight() * getMinHeight()), data)).fitDouble(0D, 1D, x, y, z) * opacity > getThreshold();
    }
}
