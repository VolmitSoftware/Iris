/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

import com.volmit.iris.engine.object.annotations.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("jigsaw-structure-min-distance")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents the min distance between jigsaw structure placements")
@Data
public class IrisJigsawMinDistance {
    @Required
    @RegistryListResource(IrisJigsawStructure.class)
    @Desc("The structure to check against")
    private String structure;

    @Required
    @MinNumber(0)
    @Desc("The min distance in blocks to a placed structure\nWARNING: The performance impact scales exponentially!")
    private int distance;
}
