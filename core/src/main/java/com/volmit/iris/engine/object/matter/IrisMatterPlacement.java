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

package com.volmit.iris.engine.object.matter;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.IrisEngine;
import com.volmit.iris.engine.object.IRare;
import com.volmit.iris.engine.object.IrisStyledRange;
import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.matter.MatterSlice;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("matter-placer")
@EqualsAndHashCode()
@Accessors(chain = true)
@NoArgsConstructor
@Desc("Represents an iris object placer. It places matter objects.")
@Data
public class IrisMatterPlacement implements IRare {
    @RegistryListResource(IrisMatterObject.class)
    @Required
    @ArrayType(min = 1, type = String.class)
    @Desc("List of objects to place")
    private KList<String> place = new KList<>();

    @MinNumber(0)
    @Desc("The rarity of this object placing")
    private int rarity = 0;

    @MinNumber(0)
    @Desc("The styled density of this object")
    private IrisStyledRange densityRange;

    @Desc("The absolute density for this object")
    private double density = 1;

    @Desc("Translate this matter object before placement")
    private IrisMatterTranslate translate;

    @Desc("Place this object on the surface height, bedrock or the sky, then use translate if need be.")
    private IrisMatterPlacementLocation location = IrisMatterPlacementLocation.SURFACE;

    public void place(IrisEngine engine, IrisData data, RNG rng, int ax, int az) {
        IrisMatterObject object = data.getMatterLoader().load(place.getRandom(rng));
        int x = ax;
        int z = az;
        int yoff = 0;

        if (translate != null) {
            x += translate.xOffset(data, rng, x, z);
            yoff += translate.yOffset(data, rng, x, z);
            z += translate.zOffset(data, rng, x, z);
        }

        int y = yoff + location.at(engine, x, z);
        Mantle mantle = engine.getMantle().getMantle();

        int xx = x;
        int yy = y;
        int zz = z;

        for (MatterSlice<?> slice : object.getMatter().getSliceMap().values()) {
            slice.iterate((mx, my, mz, v) -> {
                mantle.set(xx + mx, yy + my, zz + mz, v);
            });
        }
    }
}
