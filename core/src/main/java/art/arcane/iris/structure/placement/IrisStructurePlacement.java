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

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.pack.validation.CompatPools;
import art.arcane.iris.structure.nativegen.IrisNativeStructure;
import art.arcane.iris.structure.nativegen.NativeStructureSuppression;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListResource;
import art.arcane.iris.pack.schema.annotation.RegistryListStructure;
import art.arcane.volmlib.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Attaches structures to a biome, region or dimension and controls where and how often they generate. This is independent of a structure's own native generation, so you can add custom placements in tandem with native generation or instead of it.")
@Data
public class IrisStructurePlacement {
    @ArrayType(type = String.class, min = 1)
    @RegistryListStructure
    @Description("Editable Iris assembly resources offered by this placement. Leave empty when nativeStructures supplies the source.")
    private KList<String> structures = new KList<>();

    @ArrayType(type = IrisNativeStructure.class, min = 1)
    @Description("Live registered vanilla, datapack, and modded structures offered by this placement. Minecraft runs their native generator directly, independent of normal biome or dimension eligibility.")
    private KList<IrisNativeStructure> nativeStructures = new KList<>();

    @Description("Stable authored identity for this placement. Set this when multiple placements use the same structure and settings. When empty, Iris derives identity from the placement content so list reordering does not move generated structures.")
    private String placementId = "";

    @Description("Controls native replacement for this placement. REPLACE_SOURCE suppresses each selected Iris structure's vanillaSource or registered native source, and is honored only for dimension-level placements. NONE leaves native generation untouched.")
    private NativeStructureSuppression nativeSuppression = NativeStructureSuppression.NONE;

    @Description("How start positions are scattered.")
    private StructureDistribution distribution = StructureDistribution.RANDOM_SPREAD;

    @MinNumber(1)
    @MaxNumber(4096)
    @Description("RANDOM_SPREAD only: the grid cell size in chunks. One placement attempt per cell. Larger = rarer.")
    private int spacing = 32;

    @MinNumber(0)
    @Description("RANDOM_SPREAD only: the minimum chunk separation between placements within the grid. Must be smaller than spacing.")
    private int separation = 8;

    @Description("RANDOM_SPREAD only: a salt mixed into the placement RNG so different structures using the same spacing do not stack.")
    private int salt = 165745296;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("DENSITY only: the per-chunk probability (0-1) of a placement starting in a chunk.")
    private double density = 0.02;

    @MinNumber(1)
    @Description("CONCENTRIC_RINGS only: the number of placements distributed across the rings.")
    private int ringCount = 128;

    @MinNumber(1)
    @Description("CONCENTRIC_RINGS only: the ring spacing in chunks.")
    private int ringDistance = 32;

    @MinNumber(1)
    @Description("CONCENTRIC_RINGS only: how many placements share each ring before moving outward.")
    private int ringSpread = 3;

    @Description("Minimum world Y. SURFACE uses it as a terrain-height gate; HEIGHT_BAND and cave anchors use it as the lower candidate bound. LEGACY follows underground.")
    private int minHeight = -2032;

    @Description("Maximum world Y. SURFACE uses it as a terrain-height gate; HEIGHT_BAND and cave anchors use it as the upper candidate bound. LEGACY follows underground.")
    private int maxHeight = 2032;

    @Description("LEGACY anchor only: if true, choose a deterministic world Y inside [minHeight, maxHeight]; otherwise gate the terrain surface. Explicit anchor modes override this field.")
    private boolean underground = false;

    @Description("Vertical anchor policy. LEGACY preserves the underground boolean. Cave modes search existing carved-space data and implicitly behave as underground placements.")
    private IrisStructureAnchorMode anchor = IrisStructureAnchorMode.LEGACY;

    @RegistryListResource(IrisBiome.class)
    @ArrayType(type = String.class, min = 1)
    @Description("Optional cave-biome allowlist for cave anchor modes. Empty accepts the cave biome resolved at the selected anchor. Entries match by load key, including prefix matches.")
    private KList<String> caveBiomes = new KList<>();

    @MinNumber(1)
    @MaxNumber(64)
    @Description("Cave anchor modes only: deterministic X/Z columns tested inside the start chunk before the placement is skipped.")
    private int caveAnchorAttempts = 8;

    @MinNumber(1)
    @MaxNumber(16)
    @Description("Cave anchor modes only: vertical scan step in blocks. One is exact; larger values trade precision for speed.")
    private int caveAnchorScanStep = 1;

    @MinNumber(1)
    @MaxNumber(64)
    @Description("Cave anchor modes only: minimum contiguous carved-space height required at the selected anchor.")
    private int caveMinimumClearance = 3;

    @Description("Terrain integration for this placement. The editable structures backend supports SOURCE, PRESERVE, BORE, and FORCE_CARVE. The nativeStructures backend supports every terrain mode.")
    private IrisStructureTerrain terrain = new IrisStructureTerrain();

    @Description("Optional foundation columns placed beneath the assembled structure's occupied bottom cells. Columns pass through air and fluids until they reach solid ground, up to maxDepth.")
    private IrisStructureStiltSettings stilt = null;

    @Description("If false, the placement is skipped when its selected surface or cave column is submerged.")
    private boolean underwater = false;

    public IrisStructureTerrain resolvedTerrain() {
        return terrain == null ? new IrisStructureTerrain() : terrain;
    }

    public boolean hasIrisStructures() {
        return structures != null && !structures.isEmpty();
    }

    public boolean hasNativeStructures() {
        return nativeStructures != null && !nativeStructures.isEmpty();
    }

    public IrisStructureAnchorMode resolvedAnchor() {
        if (anchor == null || anchor == IrisStructureAnchorMode.LEGACY) {
            return underground ? IrisStructureAnchorMode.HEIGHT_BAND : IrisStructureAnchorMode.SURFACE;
        }
        return anchor;
    }

    public boolean isAnchoredUnderground() {
        IrisStructureAnchorMode activeAnchor = resolvedAnchor();
        return activeAnchor == IrisStructureAnchorMode.HEIGHT_BAND || activeAnchor.isCave();
    }

    /**
     * Reports cave biome references the version-content gate excluded. The list is deliberately not filtered: it is an
     * allowlist whose emptiness means "any cave biome", and it feeds the placement determinism signature. An excluded
     * biome never generates, so the entry is inert - this only makes it visible in the pack compat report.
     */
    public void reportExcludedCaveBiomes(IrisData data, String subjectType, String subjectKey, String field) {
        if (data == null || data.getBiomeLoader() == null || caveBiomes == null) {
            return;
        }

        for (int index = 0; index < caveBiomes.size(); index++) {
            String key = caveBiomes.get(index);
            IrisBiome biome = data.getBiomeLoader().load(key);

            if (biome != null && biome.isCompatExcluded()) {
                CompatPools.drop(data, biome, subjectType, subjectKey, field + "[" + index + "] " + key, null);
            }
        }
    }
}
