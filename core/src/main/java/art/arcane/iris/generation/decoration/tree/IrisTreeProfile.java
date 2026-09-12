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

package art.arcane.iris.generation.decoration.tree;

import art.arcane.volmlib.util.documentation.Description;

@Description("Named canopy presets that drive default layer radii and shape, scaled to the tree height.")
public enum IrisTreeProfile {
    @Description("Rounded broadleaf crown.")
    OAK,
    @Description("Slim upright broadleaf crown.")
    BIRCH,
    @Description("Narrow conifer cone.")
    SPRUCE,
    @Description("Tall thin crown concentrated near the very top.")
    JUNGLE,
    @Description("Flat shallow umbrella crown.")
    ACACIA,
    @Description("Dense rounded broadleaf crown.")
    DARK_OAK,
    @Description("Flat wide umbrella crown compressed into the top quarter (vanilla dark oak look).")
    DARK_OAK_FLAT,
    @Description("Extra wide flat umbrella that overlaps into a continuous roofed canopy.")
    DARK_OAK_FLAT_WIDE,
    @Description("Broad rounded blossom crown.")
    CHERRY,
    @Description("Bare trunk with a small tuft of fronds only at the very top.")
    PALM,
    @Description("Broad crown with wide drooping radii, ideal with a low startAngle to make a weeping skirt.")
    WILLOW,
    @Description("Very narrow tall crown hugging the trunk (cypress / lombardy poplar).")
    COLUMNAR,
    @Description("Squat, wide, low crown for shrubs and saplings.")
    BUSH,
    @Description("Giant conifer cone for 2x2 mega spruce trunks.")
    MEGA_SPRUCE
}
