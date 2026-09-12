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

import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.volmlib.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("tree-canopy")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("The leaf crown of a procedural tree. Built from stacked dome-shaped discs (driven by the profile or by explicit layers) and optionally a branch system.")
@Data
public class IrisTreeCanopy {
    @MinNumber(0)
    @MaxNumber(180)
    @Description("Elevation angle in degrees from vertical for each leaf dome. 90 is a flat disc, below 90 domes downward into a sphere, above 90 flares outward into an umbrella.")
    private double startAngle = 90;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Vertical scale of the canopy volume. Lower values flatten the crown.")
    private double squish = 1;

    @Description("How leaves fill each canopy disc.")
    private IrisTreeLeafMode mode = IrisTreeLeafMode.TRIMMED;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Fill probability used by the density and noise leaf modes.")
    private double leafDensity = 0.85;

    @MinNumber(0.1)
    @Description("Horizontal X stretch of the crown for elliptical or wind-blown shapes. 1 is circular.")
    private double crownStretchX = 1;

    @MinNumber(0.1)
    @Description("Horizontal Z stretch of the crown for elliptical or wind-blown shapes. 1 is circular.")
    private double crownStretchZ = 1;

    @ArrayType(min = 1, type = IrisTreeLayer.class)
    @Description("Optional explicit crown discs. When provided these replace the profile-driven layers and let you sculpt the silhouette by hand.")
    private KList<IrisTreeLayer> layers = new KList<>();

    @Description("Optional branch system. When present, branches build most of the canopy and only the topmost profile disc is still placed as a crown.")
    private IrisTreeBranches branches = null;
}
