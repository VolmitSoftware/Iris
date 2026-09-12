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

package art.arcane.iris.generation.biome;

import art.arcane.volmlib.util.documentation.Description;

@Description("Controls how floating-child island underside blocks choose their palette.")
public enum FloatingBottomPaletteMode {
    @Description("Use the normal top-down biome layer depth for the whole island column.")
    DEPTH,

    @Description("Use the target biome's top palette from both the island top and underside, meeting in the middle.")
    MIRROR_TOP,

    @Description("Use bottomPalette near the underside and the target biome's normal palette near the top.")
    CUSTOM
}
