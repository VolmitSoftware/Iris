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

import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.engine.object.noise.IrisShapedGeneratorStyle;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Translate objects")
@Data
public class IrisCaveLayer {
    @Required
    @Desc("The vertical slope this cave layer follows")
    private IrisShapedGeneratorStyle verticalSlope = new IrisShapedGeneratorStyle();

    @Required
    @Desc("The horizontal slope this cave layer follows")
    private IrisShapedGeneratorStyle horizontalSlope = new IrisShapedGeneratorStyle();

    @Desc("If defined, a cave fluid will fill this cave below (or above) the specified fluidHeight in this object.")
    private IrisCaveFluid fluid = new IrisCaveFluid();

    @MinNumber(0.001)
    @Desc("The cave zoom. Higher values makes caves spread out further and branch less often, but are thicker.")
    private double caveZoom = 1D;

    @MinNumber(0.001)
    @Desc("The cave thickness.")
    private double caveThickness = 1D;

    @Desc("If set to true, this cave layer can break the surface")
    private boolean canBreakSurface = false;

}
