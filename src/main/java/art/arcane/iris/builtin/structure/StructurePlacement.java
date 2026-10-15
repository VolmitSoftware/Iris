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

import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinItems;
import com.volmit.config.MinNumber;
import com.volmit.config.Ref;
import com.volmit.config.Reference;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Where and how often the structures of a dimension, region or biome start, written as one entry of its
 * {@code "structures": [...]}: the pack's own structures to assemble or the platform's to start natively, the
 * distribution of the starts, the height they anchor at, and how they meet the terrain. IrisApi types only.
 */
@Desc("Attaches structures to a biome, region or dimension and controls where and how often they generate. This is independent of a structure's own native generation, so you can add custom placements in tandem with native generation or instead of it.")
public final class StructurePlacement {
    @MinItems(1)
    @Ref(Reference.PACK_STRUCTURE)
    @Desc("Editable Iris assembly resources offered by this placement. Leave empty when nativeStructures supplies the source.")
    private List<String> structures = List.of();

    @MinItems(1)
    @Desc("Live registered vanilla, datapack, and modded structures offered by this placement. Minecraft runs their native generator directly, independent of normal biome or dimension eligibility.")
    private List<NativeStructureSource> nativeStructures = List.of();

    @Desc("Stable authored identity for this placement. Set this when multiple placements use the same structure and settings. When empty, Iris derives identity from the placement content so list reordering does not move generated structures.")
    private String placementId = "";

    @Desc("Controls native replacement for this placement. REPLACE_SOURCE suppresses each selected Iris structure's vanillaSource or registered native source, and is honored only for dimension-level placements. NONE leaves native generation untouched.")
    private NativeStructureSuppression nativeSuppression = NativeStructureSuppression.NONE;

    @Desc("How start positions are scattered.")
    private StructureDistribution distribution = StructureDistribution.RANDOM_SPREAD;

    @MinNumber(1)
    @MaxNumber(4096)
    @Desc("RANDOM_SPREAD only: the grid cell size in chunks. One placement attempt per cell. Larger = rarer.")
    private int spacing = 32;

    @MinNumber(0)
    @Desc("RANDOM_SPREAD only: the minimum chunk separation between placements within the grid. Must be smaller than spacing.")
    private int separation = 8;

    @Desc("RANDOM_SPREAD only: a salt mixed into the placement RNG so different structures using the same spacing do not stack.")
    private int salt = 165745296;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("DENSITY only: the per-chunk probability (0-1) of a placement starting in a chunk.")
    private double density = 0.02;

    @MinNumber(1)
    @Desc("CONCENTRIC_RINGS only: the number of placements distributed across the rings.")
    private int ringCount = 128;

    @MinNumber(1)
    @Desc("CONCENTRIC_RINGS only: the ring spacing in chunks.")
    private int ringDistance = 32;

    @MinNumber(1)
    @Desc("CONCENTRIC_RINGS only: how many placements share each ring before moving outward.")
    private int ringSpread = 3;

    @Desc("Minimum world Y. SURFACE uses it as a terrain-height gate; HEIGHT_BAND and cave anchors use it as the lower candidate bound. LEGACY follows underground.")
    private int minHeight = -2032;

    @Desc("Maximum world Y. SURFACE uses it as a terrain-height gate; HEIGHT_BAND and cave anchors use it as the upper candidate bound. LEGACY follows underground.")
    private int maxHeight = 2032;

    @Desc("LEGACY anchor only: if true, choose a deterministic world Y inside [minHeight, maxHeight]; otherwise gate the terrain surface. Explicit anchor modes override this field.")
    private boolean underground = false;

    @Desc("Vertical anchor policy. LEGACY preserves the underground boolean. Cave modes search existing carved-space data and implicitly behave as underground placements.")
    private StructureAnchorMode anchor = StructureAnchorMode.LEGACY;

    @MinItems(1)
    @Ref(Reference.PACK_BIOME)
    @Desc("Optional cave-biome allowlist for cave anchor modes. Empty accepts the cave biome resolved at the selected anchor. Entries match by load key, including prefix matches.")
    private List<String> caveBiomes = List.of();

    @MinNumber(1)
    @MaxNumber(64)
    @Desc("Cave anchor modes only: deterministic X/Z columns tested inside the start chunk before the placement is skipped.")
    private int caveAnchorAttempts = 8;

    @MinNumber(1)
    @MaxNumber(16)
    @Desc("Cave anchor modes only: vertical scan step in blocks. One is exact; larger values trade precision for speed.")
    private int caveAnchorScanStep = 1;

    @MinNumber(1)
    @MaxNumber(64)
    @Desc("Cave anchor modes only: minimum contiguous carved-space height required at the selected anchor.")
    private int caveMinimumClearance = 3;

    @Desc("Terrain integration for this placement. The editable structures backend supports SOURCE, PRESERVE, BORE, and FORCE_CARVE. The nativeStructures backend supports every terrain mode.")
    private StructureTerrain terrain = new StructureTerrain();

    @Desc("Optional foundation columns placed beneath the assembled structure's occupied bottom cells. Columns pass through air and fluids until they reach solid ground, up to maxDepth.")
    private StructureStilts stilt = null;

    @Desc("If false, the placement is skipped when its selected surface or cave column is submerged.")
    private boolean underwater = false;

    public StructurePlacement() {
    }

    /** Keys of the pack's structures the placement assembles, one picked per start. */
    public List<String> structures() {
        return listOrEmpty(structures);
    }

    /** The platform structures the placement starts natively, one picked per start by weight. */
    public List<NativeStructureSource> nativeStructures() {
        return listOrEmpty(nativeStructures);
    }

    public String placementId() {
        return placementId;
    }

    public NativeStructureSuppression nativeSuppression() {
        return nativeSuppression == null ? NativeStructureSuppression.NONE : nativeSuppression;
    }

    public StructureDistribution distribution() {
        return distribution == null ? StructureDistribution.RANDOM_SPREAD : distribution;
    }

    public int spacing() {
        return spacing;
    }

    public int separation() {
        return separation;
    }

    public int salt() {
        return salt;
    }

    public double density() {
        return density;
    }

    public int ringCount() {
        return ringCount;
    }

    public int ringDistance() {
        return ringDistance;
    }

    public int ringSpread() {
        return ringSpread;
    }

    public int minHeight() {
        return minHeight;
    }

    public int maxHeight() {
        return maxHeight;
    }

    public boolean underground() {
        return underground;
    }

    /** The anchor as written; {@link #resolvedAnchor} says what {@code LEGACY} means for this placement. */
    public StructureAnchorMode anchor() {
        return anchor == null ? StructureAnchorMode.LEGACY : anchor;
    }

    /** Cave biome keys or key prefixes a cave anchor accepts; empty for any. */
    public List<String> caveBiomes() {
        return listOrEmpty(caveBiomes);
    }

    public int caveAnchorAttempts() {
        return caveAnchorAttempts;
    }

    public int caveAnchorScanStep() {
        return caveAnchorScanStep;
    }

    public int caveMinimumClearance() {
        return caveMinimumClearance;
    }

    /** How the placement's structures meet the terrain; the defaults when the pack wrote none. */
    public StructureTerrain resolvedTerrain() {
        return terrain == null ? new StructureTerrain() : terrain;
    }

    /** The foundation columns under the structures; empty for none. */
    public Optional<StructureStilts> stilt() {
        return Optional.ofNullable(stilt);
    }

    public boolean underwater() {
        return underwater;
    }

    public boolean hasIrisStructures() {
        return !structures().isEmpty();
    }

    public boolean hasNativeStructures() {
        return !nativeStructures().isEmpty();
    }

    /** The anchor this placement uses: {@code LEGACY} is the band when {@link #underground}, else the surface. */
    public StructureAnchorMode resolvedAnchor() {
        if (anchor() == StructureAnchorMode.LEGACY) {
            return underground ? StructureAnchorMode.HEIGHT_BAND : StructureAnchorMode.SURFACE;
        }
        return anchor();
    }

    public boolean isAnchoredUnderground() {
        StructureAnchorMode activeAnchor = resolvedAnchor();
        return activeAnchor == StructureAnchorMode.HEIGHT_BAND || activeAnchor.isCave();
    }

    private static <T> List<T> listOrEmpty(List<T> list) {
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }
}
