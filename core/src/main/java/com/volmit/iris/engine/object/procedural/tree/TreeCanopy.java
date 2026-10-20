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
import com.volmit.iris.engine.object.annotations.ArrayType;
import com.volmit.iris.engine.object.annotations.MinNumber;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Snippet(ProceduralObjectCatalog.PART_FOLDER + "/tree-canopies")
@Desc("The leaf crown of a procedural tree. Built from stacked dome-shaped discs (driven by the profile or by explicit layers) and optionally a branch system.")
public final class TreeCanopy {
    @MinNumber(0)
    @MaxNumber(180)
    @Desc("Elevation angle in degrees from vertical for each leaf dome. 90 is a flat disc, below 90 domes downward into a sphere, above 90 flares outward into an umbrella.")
    private double startAngle = 90;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Vertical scale of the canopy volume. Lower values flatten the crown.")
    private double squish = 1;

    @Desc("How leaves fill each canopy disc.")
    private TreeLeafMode mode = TreeLeafMode.TRIMMED;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Fill probability used by the density and noise leaf modes.")
    private double leafDensity = 0.85;

    @MinNumber(0.1)
    @Desc("Horizontal X stretch of the crown for elliptical or wind-blown shapes. 1 is circular.")
    private double crownStretchX = 1;

    @MinNumber(0.1)
    @Desc("Horizontal Z stretch of the crown for elliptical or wind-blown shapes. 1 is circular.")
    private double crownStretchZ = 1;

    @ArrayType(min = 1, type = TreeLayer.class)
    @Desc("Optional explicit crown discs. When provided these replace the profile-driven layers and let you sculpt the silhouette by hand.")
    private List<TreeLayer> layers = List.of();

    @Desc("Optional branch system. When present, branches build most of the canopy and only the topmost profile disc is still placed as a crown.")
    private TreeBranches branches = null;

    public TreeCanopy() {
    }

    public double startAngle() {
        return startAngle;
    }

    public double squish() {
        return squish;
    }

    public TreeLeafMode mode() {
        return mode == null ? TreeLeafMode.TRIMMED : mode;
    }

    public double leafDensity() {
        return leafDensity;
    }

    public double crownStretchX() {
        return crownStretchX;
    }

    public double crownStretchZ() {
        return crownStretchZ;
    }

    public List<TreeLayer> layers() {
        return layers == null ? List.of() : Collections.unmodifiableList(layers);
    }

    /** Empty where the pack sets none. */
    public Optional<TreeBranches> branches() {
        return Optional.ofNullable(branches);
    }
}
