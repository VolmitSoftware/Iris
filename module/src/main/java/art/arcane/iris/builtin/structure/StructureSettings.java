/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.builtin.structure;

import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import com.volmit.config.Ref;
import com.volmit.config.Reference;

import java.util.Collections;
import java.util.List;

/**
 * How a dimension treats the platform's own structures (vanilla, data pack and mod structures), written as
 * {@code "importedStructures": {"disabled": [...], "adjustments": [...]}}: which ones it disables, how the ones that still
 * generate are moved and fitted, how often structure sets start, and whether installed data packs may replace the
 * platform's own definitions. IrisApi types only.
 */
@Desc("Controls native vanilla, mod, and ingested datapack structure generation for this dimension. Every registered structure generates through its native placement unless its key matches 'disabled', equals a key in 'disabledExact', or a viable dimension-level Iris placement explicitly replaces its source. Family matching uses namespace, slash, or underscore boundaries, while exact matching compares normalized complete keys only. Run '/iris module structure list' to dump every valid key. Only affects newly generated chunks and is separate from Iris structure placements.")
@Feature(id = "iris:structure", name = "Structures", key = "importedStructures", targets = Level.DIMENSION)
public final class StructureSettings implements IrisFeature {
    @Ref(Reference.STRUCTURE_PREFIX)
    @Desc("Structure keys to deny explicitly, e.g. 'minecraft:stronghold'. A namespace:path prefix also matches, so 'minecraft:village' disables every village variant and 'minecraft:ruined_portal' disables every ruined portal. Every key not matched here remains enabled.")
    private List<String> disabled = List.of();

    @Ref(Reference.STRUCTURE)
    @Desc("Exact structure keys to deny after trimming and case normalization. Unlike 'disabled', entries never match key families, so 'minecraft:ruined_portal' does not disable 'minecraft:ruined_portal_nether'.")
    private List<String> disabledExact = List.of();

    @MinNumber(-512)
    @MaxNumber(512)
    @Desc("Vertical block offset applied only to UNDERGROUND vanilla structures (the UNDERGROUND_STRUCTURES, UNDERGROUND_DECORATION, and STRONGHOLDS generation steps: strongholds, trial chambers, mineshafts, ancient cities, etc.). Surface structures (villages, outposts, etc.) are never shifted. Use a negative value to push deep structures lower when your dimension's sea/terrain level differs from vanilla's (e.g. -64 if you lowered the fluid height to 0). 0 = no shift.")
    private int undergroundYShift = 0;

    @Desc("Controls whether ingested datapacks may replace minecraft-namespaced structure definitions, sets, pools, and templates. When false, those overrides are stripped from installed datapack copies so vanilla definitions stay intact. Non-minecraft structures from datapacks and mods remain governed by disabled because namespace alone cannot identify their origin. Resolved globally across loaded packs: if any dimension sets this false, minecraft-namespaced overrides are stripped from every installed datapack copy.")
    private boolean datapackOverrides = true;

    @Desc("Exact registered structure-set frequency overrides for native generation. The last entry for a normalized structure-set key wins. These do not convert native structures into Iris placements and affect only newly generated chunks.")
    private List<StructureSetFrequency> frequencyOverrides = List.of();

    @Desc("Per-structure adjustments applied to vanilla, mod, and datapack structures that still generate natively. Vertical shifts from every matching entry stack. A matching preserveSourceY option disables Iris burial repositioning for that structure. The last matching entry with stilt settings controls foundation columns, and likewise for terrain and yBand settings. A structure suppressed by an Iris placement is unaffected.")
    private List<StructureAdjustment> adjustments = List.of();

    public StructureSettings() {
    }

    public StructureSettings(List<String> disabled, List<String> disabledExact, int undergroundYShift, boolean datapackOverrides,
                             List<StructureSetFrequency> frequencyOverrides, List<StructureAdjustment> adjustments) {
        this.disabled = List.copyOf(disabled);
        this.disabledExact = List.copyOf(disabledExact);
        this.undergroundYShift = undergroundYShift;
        this.datapackOverrides = datapackOverrides;
        this.frequencyOverrides = List.copyOf(frequencyOverrides);
        this.adjustments = List.copyOf(adjustments);
    }

    /** Family patterns of the structures the dimension disables. */
    public List<String> disabled() {
        return listOrEmpty(disabled);
    }

    /** Complete keys of the structures the dimension disables. */
    public List<String> disabledExact() {
        return listOrEmpty(disabledExact);
    }

    public int undergroundYShift() {
        return undergroundYShift;
    }

    public boolean datapackOverrides() {
        return datapackOverrides;
    }

    public List<StructureSetFrequency> frequencyOverrides() {
        return listOrEmpty(frequencyOverrides);
    }

    public List<StructureAdjustment> adjustments() {
        return listOrEmpty(adjustments);
    }

    static <T> List<T> listOrEmpty(List<T> list) {
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }
}
