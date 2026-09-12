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

@Snippet("tree-branches")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("A branch system that throws limbs out of the trunk and places a leaf ball at each tip. When present, branches drive the canopy and only the topmost crown disc of the profile is still placed.")
@Data
public class IrisTreeBranches {
    @Description("How branch spawn chance varies over trunk height.")
    private IrisTreeBranchProbability probabilityFunction = IrisTreeBranchProbability.TOP_HEAVY;

    @MinNumber(0)
    @Description("Branch chance for the CONSTANT probability function.")
    private double probabilityConstant = 0.5;

    @MinNumber(0)
    @Description("Branch chance at the base for the LINEAR probability function.")
    private double probabilityBase = 0;

    @MinNumber(0)
    @Description("Branch chance at the crown for the LINEAR probability function.")
    private double probabilityCrown = 1;

    @Description("Steepness for the SIGMOID probability function.")
    private double probabilitySteepness = 10;

    @Description("Midpoint height (0-1) for the SIGMOID probability function.")
    private double probabilityMidpoint = 0.7;

    @Description("Exponent for the TOP_HEAVY probability function.")
    private double probabilityExponent = 2;

    @Description("Mean height (0-1) for the GAUSSIAN probability function.")
    private double probabilityMean = 0.7;

    @Description("Standard deviation for the GAUSSIAN probability function.")
    private double probabilityStd = 0.15;

    @Description("Noise scale for the NOISE probability function.")
    private double probabilityScale = 1;

    @MinNumber(0)
    @Description("Number of whorl rings over the trunk height for the PERIODIC probability function.")
    private double probabilityPeriods = 5;

    @Description("How branch length varies over trunk height.")
    private IrisTreeFunction lengthFunction = IrisTreeFunction.LINEAR;

    @MinNumber(0)
    @Description("Branch length at the base for the LINEAR length function.")
    private double lengthBase = 1;

    @MinNumber(0)
    @Description("Branch length at the crown for the LINEAR length function.")
    private double lengthCrown = 4;

    @MinNumber(0)
    @Description("Branch length for the CONSTANT length function.")
    private double lengthConstant = 3;

    @MinNumber(0)
    @Description("Maximum branch length for the SIGMOID, LOG and PARABOLIC length functions.")
    private double lengthMax = 4;

    @Description("Steepness for the SIGMOID length function.")
    private double lengthSteepness = 5;

    @Description("How the compass direction of each branch is chosen.")
    private IrisTreeAzimuthMode azimuthMode = IrisTreeAzimuthMode.RANDOM;

    @Description("Fixed azimuth in degrees when azimuthMode is CONSTANT.")
    private double azimuth = 0;

    @Description("Elevation angle in degrees from horizontal. 0 is flat, positive points up, negative droops down.")
    private double elevation = 0;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("How much each branch sags/droops along its length (catenary). 0 is a straight branch; higher values arc the branch and its tip downward.")
    private double sag = 0;

    @MinNumber(0)
    @MaxNumber(6)
    @Description("How many recursive levels of sub-branches to grow. 0 is none, 1 is a single level, 2+ produces fractal branching.")
    private int branchDepth = 1;

    @Description("If true, branches are clamped to never droop below horizontal (useful for upright species).")
    private boolean leafStartUp = false;

    @MinNumber(0)
    @Description("Leaf ball radius placed at each branch tip.")
    private int clusterRadius = 2;

    @Description("How the branch tip leaf cluster fills.")
    private IrisTreeLeafMode clusterMode = IrisTreeLeafMode.TRIMMED;

    @MinNumber(0)
    @Description("Fill density for the branch tip leaf cluster when clusterMode is density or noise.")
    private double clusterDensity = 0.85;

    @Description("Optional one level of recursive sub-branches sprouting from each branch tip.")
    private IrisTreeSubBranches subBranches = null;
}
