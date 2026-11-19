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
package art.arcane.iris.generation.procedural.part;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Snippet;

import java.util.Collections;
import java.util.List;

@Snippet("procedural-part")
@Description("One piece of a procedural object. Set exactly one of stem, blob, branches or cluster; children grow from this part.")
public final class ProceduralPart {
    @Description("The role of the blocks this part places, matching a material's role, such as wood or leaves. Unset, it takes its parent's role.")
    private String role;

    @Description("Where on its parent this part sits, from the base (0) to the tip (1). Unset, it sits at the tip of a stem or the top of a blob.")
    private ProceduralValue at;

    @Description("Moves this part from where it sits, in blocks.")
    private PartOffset offset;

    @Description("How this part's blocks combine with the blocks earlier parts placed.")
    private PartMode mode = PartMode.ADD;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("The fraction of this part's blocks that are placed, for sparse leaves or eroded carving.")
    private double density = 1;

    @Description("How the density is spread over the blocks.")
    private PartPattern pattern = PartPattern.RANDOM;

    @MinNumber(1)
    @Description("The size of the patches in blocks for the PATCHES pattern.")
    private double scale = 3;

    @MinNumber(0)
    @Description("Thins the blocks toward the edge of the shape: 1 thins evenly, 2 keeps a dense core.")
    private double falloff = 0;

    @Description("A line that grows from here: a trunk, branch, spire, stalk or shard.")
    private PartStem stem;

    @Description("A rounded volume: a leaf crown, boulder, cap or coral head.")
    private PartBlob blob;

    @Description("Copies of a part grown out of this part's parent along its length.")
    private PartBranches branches;

    @Description("Copies of a part placed around this spot.")
    private PartCluster cluster;

    @Description("The parts that grow from this one.")
    private List<ProceduralPart> children = List.of();

    public String role() {
        return role;
    }

    public ProceduralValue at() {
        return at;
    }

    public PartOffset offset() {
        return offset;
    }

    public PartMode mode() {
        return mode == null ? PartMode.ADD : mode;
    }

    public double density() {
        return density;
    }

    public PartPattern pattern() {
        return pattern == null ? PartPattern.RANDOM : pattern;
    }

    public double scale() {
        return scale;
    }

    public double falloff() {
        return falloff;
    }

    public PartStem stem() {
        return stem;
    }

    public PartBlob blob() {
        return blob;
    }

    public PartBranches branches() {
        return branches;
    }

    public PartCluster cluster() {
        return cluster;
    }

    public List<ProceduralPart> children() {
        return children == null ? List.of() : Collections.unmodifiableList(children);
    }
}
