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

import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MaxNumber;
import com.volmit.iris.engine.object.annotations.MinNumber;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Translate objects")
@Data
public class IrisTerrainIsland {
    @Desc("The height range")
    private IrisStyledRange height = new IrisStyledRange().setMin(60).setMax(160);

    @Desc("How deep the island can get")
    private IrisStyledRange islandDepth = new IrisStyledRange().setMin(60).setMax(160);

    @MinNumber(1)
    @MaxNumber(10000)
    @Desc("How often are regions islands instead of nothing?")
    private double islandChance = 0.5;

    @Desc("Interpolate the edges of islands")
    private IrisInterpolator islandEdgeInterpolator = new IrisInterpolator();
}
