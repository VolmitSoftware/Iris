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
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MaxNumber;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.engine.object.block.IrisBlockData;
import com.volmit.iris.util.data.B;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.block.data.BlockData;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Translate objects")
@Data
public class IrisCaveFluid {
    @Required
    @MaxNumber(255)
    @MinNumber(0)
    @Desc("The fluid height of the cave")
    private int fluidHeight = 35;

    @Desc("Insead of fluidHeight & below being fluid, turning inverse height on will simply spawn fluid in this cave layer from min(max_height, cave_height) to the fluid height. Basically, fluid will spawn above the fluidHeight value instead of below the fluidHeight.")
    private boolean inverseHeight = false;

    @Required
    @Desc("The fluid type that should spawn here")
    private IrisBlockData fluidType = new IrisBlockData("CAVE_AIR");

    private final transient AtomicCache<BlockData> fluidData = new AtomicCache<>();

    public boolean hasFluid(IrisData rdata) {
        return !B.isAir(getFluid(rdata));
    }

    public BlockData getFluid(IrisData rdata) {
        return fluidData.aquire(() ->
        {
            BlockData b = getFluidType().getBlockData(rdata);

            if (b != null) {
                return b;
            }

            return B.get("CAVE_AIR");
        });
    }
}
