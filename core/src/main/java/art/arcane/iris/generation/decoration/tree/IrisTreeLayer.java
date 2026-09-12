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
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Snippet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("tree-layer")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("An explicit canopy disc at a given vertical offset and radius. Define a list of these to bypass the profile and sculpt the crown by hand.")
@Data
public class IrisTreeLayer {
    @Description("The vertical offset of this disc, measured in blocks above the trunk base.")
    private int yOffset = 0;

    @MinNumber(0.5)
    @Description("The radius of this leaf disc in blocks.")
    private double radius = 2;
}
