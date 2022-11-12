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

package com.volmit.iris.engine.object;

import com.volmit.iris.engine.object.annotations.Desc;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;

@Desc("The type of surface entities should spawn on")
public enum IrisSurface {

    @Desc("Land surfaces")
    LAND,

    @Desc("Any surfaces animals can spawn on, such as dirt, grass and podzol")
    ANIMAL,

    @Desc("Within the water")
    WATER,

    @Desc("On land or on water")
    OVERWORLD,

    @Desc("Within lava")
    LAVA;

    /**
     * Check if this Iris surface matches the blockstate provided
     *
     * @param state The blockstate
     * @return True if it matches
     */
    public boolean matches(Block state) {
        Material type = state.getType();
        if (type.isSolid()) {
            return this == LAND || this == OVERWORLD || (this == ANIMAL
                    && (type == Material.GRASS_BLOCK || type == Material.DIRT
                    || type == Material.DIRT_PATH || type == Material.COARSE_DIRT
                    || type == Material.ROOTED_DIRT || type == Material.PODZOL
                    || type == Material.MYCELIUM || type == Material.SNOW_BLOCK));
        }
        if (type == Material.LAVA) return this == LAVA;
        if (type == Material.WATER || type == Material.SEAGRASS
                || type == Material.TALL_SEAGRASS || type == Material.KELP_PLANT
                || type == Material.KELP ||
                (state instanceof Waterlogged && ((Waterlogged) state).isWaterlogged()))
            return this == WATER || this == OVERWORLD;

        return false;
    }
}
