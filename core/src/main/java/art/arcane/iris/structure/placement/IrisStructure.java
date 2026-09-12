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

package art.arcane.iris.structure.placement;

import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.structure.jigsaw.IrisJigsawBranchFailurePolicy;
import art.arcane.iris.structure.jigsaw.IrisJigsawCompatibility;
import art.arcane.iris.structure.jigsaw.IrisJigsawMode;
import art.arcane.iris.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.structure.jigsaw.IrisJigsawThemeSet;
import art.arcane.iris.structure.jigsaw.IrisJigsawWorkcell;
import art.arcane.iris.structure.object.IrisObjectLoot;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.structure.object.IrisObjectReplace;
import art.arcane.iris.structure.object.ObjectPlaceMode;
import art.arcane.iris.world.loot.IrisLootTable;

import art.arcane.iris.pack.validation.CompatAction;
import art.arcane.iris.pack.validation.CompatFinding;
import art.arcane.iris.pack.validation.CompatRegistry;
import art.arcane.iris.pack.validation.CompatStatus;
import art.arcane.iris.pack.validation.ContentGate;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListResource;
import art.arcane.iris.pack.schema.annotation.RegistryListVanillaStructure;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.volmlib.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("A jigsaw structure. Starting from a pool, the assembler places a start piece and recursively attaches more pieces through matching connectors until it reaches the max depth or size. Pieces are Iris objects, so they carry the full object placement and block-control feature set.")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisStructure extends IrisRegistrant {
    @Required
    @RegistryListResource(IrisJigsawPool.class)
    @Description("The pool the assembler draws the start piece from.")
    private String startPool = "";

    @MinNumber(1)
    @MaxNumber(30)
    @Description("The maximum jigsaw recursion depth. Larger values allow larger structures (e.g. villages) at the cost of generation time.")
    private int maxDepth = 7;

    @MinNumber(1)
    @MaxNumber(32)
    @Description("The maximum radius of the structure in chunks from its start piece. Acts as a hard bound on assembly size and collision checks.")
    private int maxSizeChunks = 8;

    @Description("Assembly topology. SPATIAL_JIGSAW preserves the existing freeform connector graph; PLANAR_JIGSAW declares a cell-aligned planar graph.")
    private IrisJigsawMode mode = IrisJigsawMode.SPATIAL_JIGSAW;

    @Description("Target compatibility contract for this structure. IRIS_EXTENDED permits Iris-only metadata; VANILLA_PORTABLE requires a graph that can be represented by vanilla jigsaw resources.")
    private IrisJigsawCompatibility compatibility = IrisJigsawCompatibility.IRIS_EXTENDED;

    @Description("How unresolved optional connector branches affect assembly. FAIL_ASSEMBLY preserves strict Iris behavior; TERMINATE_BRANCH matches vanilla jigsaw branch termination.")
    private IrisJigsawBranchFailurePolicy branchFailurePolicy = IrisJigsawBranchFailurePolicy.FAIL_ASSEMBLY;

    @Description("Default Studio cell dimensions. Legacy planar structures use this value for every workcell when planarWorkcells is empty.")
    private IrisPosition cellSize = new IrisPosition(15, 15, 15);

    @Description("Optional author-facing name for the single spatial Jigsaw Studio workcell. Spatial is shown when this is blank.")
    private String spatialWorkcellDisplayName = "";

    @ArrayType(type = IrisJigsawWorkcell.class)
    @Description("The six persistent planar Studio workcells. Leave empty only for the legacy cellSize fallback.")
    private KList<IrisJigsawWorkcell> planarWorkcells = new KList<>();

    @ArrayType(type = IrisJigsawThemeSet.class, min = 1)
    @Description("Weighted themes available to an assembly. Leave empty for one implicit unthemed assembly path.")
    private KList<IrisJigsawThemeSet> themeSets = new KList<>();

    @Description("Whether every open connector must close with a real terminal piece through a mandatory direct fallback.")
    private boolean requireCaps = false;

    @Description("The place mode used when stamping each piece object into the world.")
    private ObjectPlaceMode placeMode = ObjectPlaceMode.STRUCTURE_PIECE;

    @ArrayType(min = 1, type = IrisObjectReplace.class)
    @Description("Find-and-replace block edits applied to every piece of this structure, using the exact same syntax as an object's 'edit'. For example, replace stone bricks with obsidian.")
    private KList<IrisObjectReplace> edit = new KList<>();

    @ArrayType(type = String.class, min = 1)
    @RegistryListResource(IrisLootTable.class)
    @Description("Loot tables applied to containers placed by this structure's pieces.")
    private KList<String> loot = new KList<>();

    @RegistryListVanillaStructure
    @Description("If this structure was generated by importing a vanilla or datapack structure, this is that structure's key (provenance). Empty for hand-authored structures.")
    private String vanillaSource = "";

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

    public IrisObjectPlacement createLootPlacement(String objectKey) {
        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.getPlace().add(objectKey);
        placement.setOverrideGlobalLoot(false);
        if (loot == null) {
            return placement;
        }
        for (String lootTable : loot) {
            if (lootTable != null && !lootTable.isBlank()) {
                placement.getLoot().add(new IrisObjectLoot().setName(lootTable).setWeight(1));
            }
        }
        return placement;
    }

    /**
     * The start pool is the only mandatory entry point into an assembly: when it has nothing left to place, the
     * structure cannot generate on this server and every placement that lists it skips it.
     */
    @Override
    public CompatStatus evaluateCompat(ContentGate gate) {
        CompatStatus base = super.evaluateCompat(gate);

        if (base.excluded() || gate == null || !gate.ready()) {
            return base;
        }

        IrisData data = getLoader();

        if (data == null || startPool == null || startPool.isBlank()) {
            return base;
        }

        IrisJigsawPool pool = data.load(IrisJigsawPool.class, startPool.trim(), false);

        if (pool == null || !pool.isCompatExcluded()) {
            return base;
        }

        CompatFinding reason = pool.getCompat().reasons().isEmpty() ? null : pool.getCompat().reasons().getLast();
        CompatFinding excluded = new CompatFinding(
                reason == null ? CompatRegistry.BLOCK : reason.registry(),
                reason == null ? startPool.trim() : reason.key(),
                CompatAction.EXCLUDED, "structure", getLoadKey(),
                "start pool " + startPool.trim() + " is unavailable");
        gate.report().record(excluded);
        KList<CompatFinding> reasons = new KList<>(base.reasons());
        reasons.add(excluded);
        return CompatStatus.excludedBy(reasons);
    }

    @Override
    public String getFolderName() {
        return "structures";
    }

    @Override
    public String getTypeName() {
        return "Structure";
    }
}
