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

package art.arcane.iris.engine.object.structure;

import art.arcane.iris.builtin.anchor.PlaceTrait;
import art.arcane.iris.builtin.anchor.ObjectPlaceMode;
import art.arcane.iris.builtin.blocks.ObjectReplace;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawBranchFailurePolicy;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawCompatibility;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawMode;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawThemeSet;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawWorkcell;
import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import com.volmit.config.Ref;
import com.volmit.config.Reference;
import com.volmit.config.Required;
import com.volmit.config.ResourceRef;
import com.volmit.geometry.BlockPosition;
import art.arcane.volmlib.util.collection.KList;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("A jigsaw structure. Starting from a pool, the assembler places a start piece and recursively attaches more pieces through matching connectors until it reaches the max depth or size. Pieces are Iris objects, so they carry the full object placement and block-control feature set.")
@Data
public class IrisStructure {
    @Required
    @ResourceRef(IrisJigsawPool.class)
    @Desc("The pool the assembler draws the start piece from.")
    private String startPool = "";

    @MinNumber(1)
    @MaxNumber(30)
    @Desc("The maximum jigsaw recursion depth. Larger values allow larger structures (e.g. villages) at the cost of generation time.")
    private int maxDepth = 7;

    @MinNumber(1)
    @MaxNumber(32)
    @Desc("The maximum radius of the structure in chunks from its start piece. Acts as a hard bound on assembly size and collision checks.")
    private int maxSizeChunks = 8;

    @Desc("Assembly topology. SPATIAL_JIGSAW preserves the existing freeform connector graph; PLANAR_JIGSAW declares a cell-aligned planar graph.")
    private IrisJigsawMode mode = IrisJigsawMode.SPATIAL_JIGSAW;

    @Desc("Target compatibility contract for this structure. IRIS_EXTENDED permits Iris-only metadata; VANILLA_PORTABLE requires a graph that can be represented by vanilla jigsaw resources.")
    private IrisJigsawCompatibility compatibility = IrisJigsawCompatibility.IRIS_EXTENDED;

    @Desc("How unresolved optional connector branches affect assembly. FAIL_ASSEMBLY preserves strict Iris behavior; TERMINATE_BRANCH matches vanilla jigsaw branch termination.")
    private IrisJigsawBranchFailurePolicy branchFailurePolicy = IrisJigsawBranchFailurePolicy.FAIL_ASSEMBLY;

    @Desc("Default Studio cell dimensions. Legacy planar structures use this value for every workcell when planarWorkcells is empty.")
    private BlockPosition cellSize = new BlockPosition(15, 15, 15);

    @Desc("Optional author-facing name for the single spatial Jigsaw Studio workcell. Spatial is shown when this is blank.")
    private String spatialWorkcellDisplayName = "";

    @Desc("The six persistent planar Studio workcells. Leave empty only for the legacy cellSize fallback.")
    private List<IrisJigsawWorkcell> planarWorkcells = new KList<>();

    @Desc("Weighted themes available to an assembly. Leave empty for one implicit unthemed assembly path.")
    private List<IrisJigsawThemeSet> themeSets = new KList<>();

    @Desc("Whether every open connector must close with a real terminal piece through a mandatory direct fallback.")
    private boolean requireCaps = false;

    @Desc("The place mode used when stamping each piece object into the world.")
    private ObjectPlaceMode placeMode = ObjectPlaceMode.STRUCTURE_PIECE;

    @Desc("Find-and-replace block edits applied to every piece of this structure, using the exact same syntax as an object's 'edit'. For example, replace stone bricks with obsidian.")
    private List<ObjectReplace> edit = new KList<>();

    @Desc("Loot tables applied to containers placed by this structure's pieces.")
    private List<String> loot = new KList<>();

    @Ref(Reference.STRUCTURE)
    @Desc("If this structure was generated by importing a vanilla or datapack structure, this is that structure's key (provenance). Empty for hand-authored structures.")
    private String vanillaSource = "";

    /** Whether the structure's pieces are stamped where the assembler put them, as a vanilla jigsaw places them. */
    public boolean stampsPiecesWhereAssembled() {
        return placeMode != null && placeMode.placeMode().has(PlaceTrait.RAW_STAMP);
    }

    public IrisJigsawMode resolvedMode() {
        return mode == null ? IrisJigsawMode.SPATIAL_JIGSAW : mode;
    }

    public IrisJigsawCompatibility resolvedCompatibility() {
        return compatibility == null ? IrisJigsawCompatibility.IRIS_EXTENDED : compatibility;
    }

    public IrisJigsawBranchFailurePolicy resolvedBranchFailurePolicy() {
        return branchFailurePolicy == null
                ? IrisJigsawBranchFailurePolicy.FAIL_ASSEMBLY
                : branchFailurePolicy;
    }
}
