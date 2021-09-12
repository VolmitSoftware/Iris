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

import com.volmit.iris.engine.object.annotations.ArrayType;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MaxNumber;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.engine.object.annotations.Snippet;
import com.volmit.iris.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("object-marker")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Find blocks to mark")
@Data
public class IrisObjectMarker {
    @ArrayType(min = 1, type = IrisBlockData.class)
    @Required
    @Desc("Find block types to mark")
    private KList<IrisBlockData> mark = new KList<>();

    @MinNumber(1)
    @MaxNumber(16)
    @Desc("The maximum amount of markers to place. Use these sparingly!")
    private int maximumMarkers = 8;

    @MinNumber(0.01)
    @MaxNumber(1)
    @Desc("The percentage of blocks in this object to check.")
    private double checkRatio = 0.33;

    @Desc("If true, markers will only be placed here if there is 2 air blocks above it.")
    private boolean emptyAbove = true;
}
