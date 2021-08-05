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

package com.volmit.iris.engine.object.carve;

import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.object.annotations.ArrayType;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.noise.IrisGeneratorStyle;
import com.volmit.iris.engine.object.noise.IrisInterpolator3D;
import com.volmit.iris.engine.object.noise.NoiseStyle;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.stream.ProceduralStream;
import com.volmit.iris.util.stream.interpolation.Interpolated;
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

    @Desc("If set to true (default) iris will interpolate the noise before checking if it meets the threshold.")
    private boolean preThresholdInterpolation = true;

    private transient AtomicCache<ProceduralStream<IrisCavernZone>> zonesRarity = new AtomicCache<>();
    private transient AtomicCache<ProceduralStream<Double>> streamCache = new AtomicCache<>();

    public IrisCavernZone getZone(double x, double y, double z, RNG rng, IrisData data) {
        return zonesRarity.aquire(() -> zoneStyle.create(rng, data)
                .stream().selectRarity(getZones())).get(x, y, z);
    }

    private double threshold(double y) {
        return 0.5;
    }

    public ProceduralStream<Double> stream(RNG rng, IrisData data) {
        if (preThresholdInterpolation) {
            return streamCache.aquire(() -> ProceduralStream.of((xx, yy, zz)
                            -> (getZone(xx, yy, zz, rng, data)
                            .getCarved(rng, data, xx, yy, zz)), Interpolated.DOUBLE)
                    .cache3D(65535));
        }

        return streamCache.aquire(() -> ProceduralStream.of((xx, yy, zz)
                        -> (getZone(xx, yy, zz, rng, data)
                        .isCarved(rng, data, xx, yy, zz) ? 1D : 0D), Interpolated.DOUBLE)
                .cache3D(65535));
    }

    public boolean isCavern(RNG rng, double x, double y, double z, double height, IrisData data) {
        if (zones.isEmpty()) {
            return false;
        }

        return getInterpolator().interpolate(x, y, z, (xx, yy, zz)
                -> stream(rng, data).get(xx, yy, zz)) > threshold(height);
    }
}
