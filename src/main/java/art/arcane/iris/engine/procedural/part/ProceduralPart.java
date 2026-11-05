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
package art.arcane.iris.engine.procedural.part;

import com.volmit.config.MinItems;
import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import com.volmit.config.Snippet;

import java.util.Collections;
import java.util.List;

@Snippet("iris/objects/procedural-part")
@Desc("One piece of a procedural object. Set exactly one of stem, blob, branches or cluster; children grow from this part.")
public final class ProceduralPart {
    @Desc("The role of the blocks this part places, matching a material's role, such as wood or leaves. Unset, it takes its parent's role.")
    private String role;

    @Desc("Where on its parent this part sits, from the base (0) to the tip (1). Unset, it sits at the tip of a stem or the top of a blob.")
    private ProceduralValue at;

    @Desc("Moves this part from where it sits, in blocks.")
    private PartOffset offset;

    @Desc("How this part's blocks combine with the blocks earlier parts placed.")
    private PartMode mode = PartMode.ADD;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The fraction of this part's blocks that are placed, for sparse leaves or eroded carving.")
    private double density = 1;

    @Desc("How the density is spread over the blocks.")
    private PartPattern pattern = PartPattern.RANDOM;

    @MinNumber(1)
    @Desc("The size of the patches in blocks for the PATCHES pattern.")
    private double scale = 3;

    @MinNumber(0)
    @Desc("Thins the blocks toward the edge of the shape: 1 thins evenly, 2 keeps a dense core.")
    private double falloff = 0;

    @Desc("A line that grows from here: a trunk, branch, spire, stalk or shard.")
    private PartStem stem;

    @Desc("A rounded volume: a leaf crown, boulder, cap or coral head.")
    private PartBlob blob;

    @Desc("Copies of a part grown out of this part's parent along its length.")
    private PartBranches branches;

    @Desc("Copies of a part placed around this spot.")
    private PartCluster cluster;

    @MinItems(1)
    @Desc("The parts that grow from this one.")
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
