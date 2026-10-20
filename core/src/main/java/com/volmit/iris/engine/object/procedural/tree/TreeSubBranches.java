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

@Snippet(ProceduralObjectCatalog.PART_FOLDER + "/tree-sub-branches")
@Desc("One recursive level of sub-branches sprouting from each primary branch tip.")
public final class TreeSubBranches {
    @MinNumber(1)
    @Desc("How many sub-branches sprout from each primary branch tip.")
    private int count = 1;

    @Desc("Pitch deflection in degrees applied to each sub-branch relative to its parent. Positive bends up, negative droops down. Cumulative pitch past 90 makes sub-branches point downward.")
    private double pitchDelta = 0;

    @Desc("Yaw (horizontal) spread in degrees fanned between sub-branches.")
    private double yawDelta = 45;

    @MinNumber(0)
    @Desc("Sub-branch length as a fraction of its parent branch length.")
    private double lengthScale = 0.5;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("How much each sub-branch sags/droops along its length (catenary).")
    private double sag = 0;

    @MinNumber(0)
    @Desc("Leaf ball radius placed at each sub-branch tip.")
    private int clusterRadius = 1;

    @Desc("How the sub-branch tip leaf cluster fills.")
    private TreeLeafMode clusterMode = TreeLeafMode.TRIMMED;

    @MinNumber(0)
    @Desc("Fill density for the sub-branch leaf cluster when clusterMode is density or noise.")
    private double clusterDensity = 0.85;

    public TreeSubBranches() {
    }

    public int count() {
        return count;
    }

    public double pitchDelta() {
        return pitchDelta;
    }

    public double yawDelta() {
        return yawDelta;
    }

    public double lengthScale() {
        return lengthScale;
    }

    public double sag() {
        return sag;
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
}
