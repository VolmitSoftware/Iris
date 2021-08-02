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
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MaxNumber;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.common.IRare;
import com.volmit.iris.engine.stream.ProceduralStream;
import com.volmit.iris.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a cavern zone")
@Data
public class IrisCavernZone implements IRare {
    @Desc("Use carving in this zone if defined")
    private IrisCarveLayer carver = null;

    @Desc("Use worley styled caves if defined")
    private IrisGeneratorStyle worley = null;

    @MinNumber(1)
    @MaxNumber(100)
    @Desc("The rarity of this zone")
    private int rarity = 1;

    private transient AtomicCache<ProceduralStream<Boolean>> carveCache = new AtomicCache<>();

    public boolean isCarved(RNG rng, IrisData data, double xx, double yy, double zz) {
        if (carver != null) {
            return carver.isCarved(rng, data, xx, yy, zz);
        }

        return false;
    }

    public double getCarved(RNG rng, IrisData data, double xx, double yy, double zz) {
        if (carver != null) {
            return carver.rawStream(rng, data).get(xx, yy, zz) / (carver.getThreshold() * 2);
        }

        return -1;
    }
}
