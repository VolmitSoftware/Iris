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

package art.arcane.iris.structure.nativegen;

import art.arcane.iris.structure.placement.IrisStructureStiltSettings;
import art.arcane.iris.structure.placement.IrisStructureTerrain;
import art.arcane.iris.structure.placement.IrisStructureYBand;

import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListVanillaStructure;
import art.arcane.volmlib.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("A per-structure adjustment applied to vanilla, mod, and datapack structures that still generate natively (those NOT suppressed by an Iris 'structures' placement). Vertical shifts move the structure start, pieces, bounds, and jigsaw metadata together before references and placement. Intersecting trees are cleared automatically inside structure piece envelopes; optional postprocessing can build palette-driven foundation columns.")
@Data
public class IrisVanillaStructureAdjustment {
    @ArrayType(type = String.class, min = 1)
    @RegistryListVanillaStructure(prefixes = true)
    @Description("Structure keys this adjustment applies to, e.g. 'minecraft:stronghold'. A namespace:path prefix also matches, so 'minecraft:village' adjusts every village variant and 'minecraft:ruined_portal' adjusts every ruined portal. Empty matches nothing.")
    private KList<String> match = new KList<>();

    @MinNumber(-512)
    @MaxNumber(512)
    @Description("Vertical block offset. Negative pushes the structure down, positive lifts it. The resolved shift is clamped so the structure remains inside the world's vertical build bounds.")
    private int yShift = 0;

    @Description("Optional absolute world Y band. The structure is relocated so its vertical midpoint lands inside the band, deterministically from its start chunk and clamped to the world's build bounds. Precedence: preserveSourceY wins over yBand, and yBand wins over both yShift and Iris burial repositioning. Structures with a fixed vanilla alignment (ocean monument sea level, desert and jungle pyramid surface fit) ignore it.")
    private IrisStructureYBand yBand = null;

    @Description("When true, skip Iris burial repositioning so the structure keeps the Y its own vanilla placement chose. Underground structures are otherwise pushed below the lowest solid column across their whole footprint, which hides terrain-aware structures such as mineshafts that intentionally breach cliffs and surfaces. Vertical shifts still apply on top of the preserved Y: this dimension's undergroundYShift plus every matching adjustment's yShift.")
    private boolean preserveSourceY = false;

    @Description("Optional foundation columns placed beneath the native structure piece bases after placement.")
    private IrisStructureStiltSettings stilt = null;

    @Description("Optional terrain integration override. FLATTEN cuts and fills exposed surface foundations within flattenRange, blending across horizontalPadding blocks while preserving buried and submerged structures. VACUUM raises processed rigid-template foundations with a fixed 12-block falloff without lowering ground. BORE and FORCE_CARVE clear every intersecting chunk before native placement, while ENCASE fills it. Left unset, SOURCE replays the registered structure's authored terrain adaptation.")
    private IrisStructureTerrain terrain = null;

    public boolean matches(String key) {
        for (String entry : match) {
            if (IrisImportedStructureControl.matchesKey(entry, key)) {
                return true;
            }
        }
        return false;
    }
}
