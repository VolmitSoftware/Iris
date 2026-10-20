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
import com.volmit.iris.engine.object.annotations.Snippet;
import com.volmit.iris.engine.object.procedural.ProceduralObjectCatalog;
import com.volmit.iris.engine.object.annotations.MaxNumber;
import com.volmit.iris.engine.object.annotations.MinNumber;

import java.util.Optional;

@Snippet(ProceduralObjectCatalog.PART_FOLDER + "/tree-branches")
@Desc("A branch system that throws limbs out of the trunk and places a leaf ball at each tip. When present, branches drive the canopy and only the topmost crown disc of the profile is still placed.")
public final class TreeBranches {
    @Desc("How branch spawn chance varies over trunk height.")
    private TreeBranchProbability probabilityFunction = TreeBranchProbability.TOP_HEAVY;

    @MinNumber(0)
    @Desc("Branch chance for the CONSTANT probability function.")
    private double probabilityConstant = 0.5;

    @MinNumber(0)
    @Desc("Branch chance at the base for the LINEAR probability function.")
    private double probabilityBase = 0;

    @MinNumber(0)
    @Desc("Branch chance at the crown for the LINEAR probability function.")
    private double probabilityCrown = 1;

    @Desc("Steepness for the SIGMOID probability function.")
    private double probabilitySteepness = 10;

    @Desc("Midpoint height (0-1) for the SIGMOID probability function.")
    private double probabilityMidpoint = 0.7;

    @Desc("Exponent for the TOP_HEAVY probability function.")
    private double probabilityExponent = 2;

    @Desc("Mean height (0-1) for the GAUSSIAN probability function.")
    private double probabilityMean = 0.7;

    @Desc("Standard deviation for the GAUSSIAN probability function.")
    private double probabilityStd = 0.15;

    @Desc("Noise scale for the NOISE probability function.")
    private double probabilityScale = 1;

    @MinNumber(0)
    @Desc("Number of whorl rings over the trunk height for the PERIODIC probability function.")
    private double probabilityPeriods = 5;

    @Desc("How branch length varies over trunk height.")
    private TreeFunction lengthFunction = TreeFunction.LINEAR;

    @MinNumber(0)
    @Desc("Branch length at the base for the LINEAR length function.")
    private double lengthBase = 1;

    @MinNumber(0)
    @Desc("Branch length at the crown for the LINEAR length function.")
    private double lengthCrown = 4;

    @MinNumber(0)
    @Desc("Branch length for the CONSTANT length function.")
    private double lengthConstant = 3;

    @MinNumber(0)
    @Desc("Maximum branch length for the SIGMOID, LOG and PARABOLIC length functions.")
    private double lengthMax = 4;

    @Desc("Steepness for the SIGMOID length function.")
    private double lengthSteepness = 5;

    @Desc("How the compass direction of each branch is chosen.")
    private TreeAzimuthMode azimuthMode = TreeAzimuthMode.RANDOM;

    @Desc("Fixed azimuth in degrees when azimuthMode is CONSTANT.")
    private double azimuth = 0;

    @Desc("Elevation angle in degrees from horizontal. 0 is flat, positive points up, negative droops down.")
    private double elevation = 0;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("How much each branch sags/droops along its length (catenary). 0 is a straight branch; higher values arc the branch and its tip downward.")
    private double sag = 0;

    @MinNumber(0)
    @MaxNumber(6)
    @Desc("How many recursive levels of sub-branches to grow. 0 is none, 1 is a single level, 2+ produces fractal branching.")
    private int branchDepth = 1;

    @Desc("If true, branches are clamped to never droop below horizontal (useful for upright species).")
    private boolean leafStartUp = false;

    @MinNumber(0)
    @Desc("Leaf ball radius placed at each branch tip.")
    private int clusterRadius = 2;

    @Desc("How the branch tip leaf cluster fills.")
    private TreeLeafMode clusterMode = TreeLeafMode.TRIMMED;

    @MinNumber(0)
    @Desc("Fill density for the branch tip leaf cluster when clusterMode is density or noise.")
    private double clusterDensity = 0.85;

    @Desc("Optional one level of recursive sub-branches sprouting from each branch tip.")
    private TreeSubBranches subBranches = null;

    public TreeBranches() {
    }

    public TreeBranchProbability probabilityFunction() {
        return probabilityFunction == null ? TreeBranchProbability.TOP_HEAVY : probabilityFunction;
    }

    public double probabilityConstant() {
        return probabilityConstant;
    }

    public double probabilityBase() {
        return probabilityBase;
    }

    public double probabilityCrown() {
        return probabilityCrown;
    }

    public double probabilitySteepness() {
        return probabilitySteepness;
    }

    public double probabilityMidpoint() {
        return probabilityMidpoint;
    }

    public double probabilityExponent() {
        return probabilityExponent;
    }

    public double probabilityMean() {
        return probabilityMean;
    }

    public double probabilityStd() {
        return probabilityStd;
    }

    public double probabilityScale() {
        return probabilityScale;
    }

    public double probabilityPeriods() {
        return probabilityPeriods;
    }

    public TreeFunction lengthFunction() {
        return lengthFunction == null ? TreeFunction.LINEAR : lengthFunction;
    }

    public double lengthBase() {
        return lengthBase;
    }

    public double lengthCrown() {
        return lengthCrown;
    }

    public double lengthConstant() {
        return lengthConstant;
    }

    public double lengthMax() {
        return lengthMax;
    }

    public double lengthSteepness() {
        return lengthSteepness;
    }

    public TreeAzimuthMode azimuthMode() {
        return azimuthMode == null ? TreeAzimuthMode.RANDOM : azimuthMode;
    }

    public double azimuth() {
        return azimuth;
    }

    public double elevation() {
        return elevation;
    }

    public double sag() {
        return sag;
    }

    public int branchDepth() {
        return branchDepth;
    }

    public boolean leafStartUp() {
        return leafStartUp;
    }

    public int clusterRadius() {
        return clusterRadius;
    }

    public TreeLeafMode clusterMode() {
        return clusterMode == null ? TreeLeafMode.TRIMMED : clusterMode;
    }

    public double clusterDensity() {
        return clusterDensity;
    }

    /** Empty where the pack sets none. */
    public Optional<TreeSubBranches> subBranches() {
        return Optional.ofNullable(subBranches);
    }
}
