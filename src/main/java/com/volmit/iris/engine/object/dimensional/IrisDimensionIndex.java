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

package com.volmit.iris.engine.object.dimensional;

import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.RegistryListResource;
import com.volmit.iris.engine.object.annotations.Required;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@SuppressWarnings("DefaultAnnotationParam")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents an index for dimensions to take up vertical slots in the same world")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisDimensionIndex {
    @Required
    @Desc("The weight of this dimension. If there are 2 dimensions, if the weight is the same on both, both dimensions will take up 128 blocks of height.")
    private double weight = 1D;

    @Desc("If inverted is set to true, the dimension will be updide down in the world")
    private boolean inverted = false;

    @Desc("Only one dimension layer should be set to primary. The primary dimension layer is where players spawn, and the biomes that the vanilla structure system uses to figure out what structures to place.")
    private boolean primary = false;

    @Required
    @RegistryListResource(IrisDimension.class)
    @MinNumber(1)
    @Desc("Name of dimension")
    private String dimension = "";
}
