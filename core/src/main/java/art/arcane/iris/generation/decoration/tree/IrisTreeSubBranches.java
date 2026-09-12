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
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Snippet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("tree-sub-branches")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("One recursive level of sub-branches sprouting from each primary branch tip.")
@Data
public class IrisTreeSubBranches {
    @MinNumber(1)
    @Description("How many sub-branches sprout from each primary branch tip.")
    private int count = 1;

    @Description("Pitch deflection in degrees applied to each sub-branch relative to its parent. Positive bends up, negative droops down. Cumulative pitch past 90 makes sub-branches point downward.")
    private double pitchDelta = 0;

    @Description("Yaw (horizontal) spread in degrees fanned between sub-branches.")
    private double yawDelta = 45;

    @MinNumber(0)
    @Description("Sub-branch length as a fraction of its parent branch length.")
    private double lengthScale = 0.5;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("How much each sub-branch sags/droops along its length (catenary).")
    private double sag = 0;

    @MinNumber(0)
    @Description("Leaf ball radius placed at each sub-branch tip.")
    private int clusterRadius = 1;

    @Description("How the sub-branch tip leaf cluster fills.")
    private IrisTreeLeafMode clusterMode = IrisTreeLeafMode.TRIMMED;

    @MinNumber(0)
    @Description("Fill density for the sub-branch leaf cluster when clusterMode is density or noise.")
    private double clusterDensity = 0.85;
}
