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

import art.arcane.iris.generation.terrain.IrisMaterialPalette;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListBlockType;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("tree-decorator")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("An accent block applied to a generated tree such as fruit on branch tips, vines on the trunk, snow on the crown, or a glowing underside.")
@Data
public class IrisTreeDecorator {
    @Description("Where this decorator is placed on the tree.")
    private IrisTreeDecoratorTarget target = IrisTreeDecoratorTarget.BRANCH_TIP;

    @Required
    @Description("The block id to place, e.g. minecraft:magma_block. Ignored when palette is set.")
    @RegistryListBlockType
    private String block = "";

    @Description("A noise-driven palette for this decorator. When set this overrides the single block.")
    private IrisMaterialPalette palette = null;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("The chance per candidate position for this decorator to place.")
    private double chance = 0.5;

    @MinNumber(1)
    @Description("Maximum strand length downward for the CANOPY_HANG target (random 1..length per column).")
    private int length = 1;

    @Description("If true the block's facing is oriented away from the trunk (for trunk-mounted fences/gates/banners).")
    private boolean axisAware = false;
}
