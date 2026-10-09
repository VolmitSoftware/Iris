/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.builtin.deposit;

import com.volmit.config.Desc;

@Desc("Selects the geometry used to build each deposit clump.")
public enum DepositShape {
    @Desc("Builds the traditional Iris fixed-block-count cube clump.")
    IRIS,
    @Desc("Builds a chain of overlapping ellipsoids matching Minecraft's ordinary ore-vein geometry.")
    VANILLA_ELLIPSOID,
    @Desc("Builds sparse candidate offsets matching Minecraft's scattered-ore geometry.")
    VANILLA_SCATTERED
}
