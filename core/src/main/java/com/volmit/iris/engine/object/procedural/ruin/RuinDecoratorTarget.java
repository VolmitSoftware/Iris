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

package com.volmit.iris.engine.object.procedural.ruin;

import com.volmit.iris.engine.object.annotations.Desc;

@Desc("Where a ruin accent block is placed after the ruin has been built and eroded.")
public enum RuinDecoratorTarget {
    @Desc("On top of the highest surviving block in each column (moss carpets, snow, sculk, lanterns on broken tops).")
    TOP,
    @Desc("On air-facing vertical sides of any surviving block (vines, glow lichen, climbing growth on the ruin faces).")
    SURFACE,
    @Desc("Scattered across the ground footprint around the y=0 base ring, wider than the structure itself (loose rubble, mushrooms, grass tufts ringing the ruin).")
    BASE_SCATTER
}
