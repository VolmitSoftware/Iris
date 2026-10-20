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

package com.volmit.iris.engine.object.procedural.tree;

import com.volmit.iris.engine.object.annotations.Desc;

@Desc("Where a tree decorator block is placed.")
public enum TreeDecoratorTarget {
    @Desc("At the tip of each generated branch (vines, fruit, lanterns).")
    BRANCH_TIP,
    @Desc("On air-facing sides of trunk and branch blocks (vines, moss).")
    TRUNK_SURFACE,
    @Desc("On top of the highest block in each column (snow, sculk).")
    CANOPY_TOP,
    @Desc("One block below the lowest leaf in each column (glowing underside).")
    CANOPY_BOTTOM,
    @Desc("Around the y=0 ring at the base of the trunk (roots, mushrooms).")
    TRUNK_BASE,
    @Desc("On air-facing sides of leaf blocks (berries, lights nestled in the foliage).")
    LEAF_SURFACE,
    @Desc("Hanging strands of up to 'length' blocks below the lowest leaf in each column (vines, icicles).")
    CANOPY_HANG,
    @Desc("On the top face of trunk and branch blocks (moss, snow on limbs).")
    BRANCH_SURFACE,
    @Desc("One block above the very top of the trunk (nest, beehive, beacon).")
    TRUNK_TOP,
    @Desc("Scattered across the ground footprint around the base, wider than the trunk-base ring.")
    GROUND_SCATTER
}
