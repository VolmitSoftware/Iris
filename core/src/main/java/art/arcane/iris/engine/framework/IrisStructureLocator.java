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

package art.arcane.iris.engine.framework;

import art.arcane.iris.builtin.anchor.PlaceTrait;
import art.arcane.iris.engine.framework.structure.ColumnHeights;
import art.arcane.iris.engine.framework.structure.NativeStartPlanner;
import art.arcane.iris.engine.framework.structure.StructureWorld;
import art.arcane.iris.engine.framework.structure.assembly.AssembledPiece;
import art.arcane.iris.engine.framework.structure.assembly.Assembly;
import art.arcane.iris.engine.framework.structure.assembly.StructureAssembly;
import art.arcane.iris.engine.framework.structure.assembly.StructureStamp;
import art.arcane.iris.engine.framework.structure.assembly.Viability;
import art.arcane.iris.builtin.structure.NativeStructureSource;
import art.arcane.iris.builtin.structure.StructureAnchorMode;
import art.arcane.iris.builtin.structure.StructureCarveShape;
import art.arcane.iris.builtin.structure.StructurePlacement;
import art.arcane.iris.builtin.structure.StructurePlacementSettings;
import art.arcane.iris.builtin.structure.StructureTerrain;
import art.arcane.iris.builtin.structure.StructureTerrainMode;
import art.arcane.iris.builtin.structure.NativeStructureSuppression;
import art.arcane.iris.builtin.structure.StructureDistribution;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.config.Level;
import com.volmit.geometry.BlockPosition;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Finds where Iris assembly and native jigsaw placements generate. A structure key matches
 * an Iris structure load key, its {@code vanillaSource}, or a configured native registry key.
 *
 * Each world keeps an index of placed keys in its state, so {@code /locate} and {@code /iris goto} skip
 * the grid scan entirely for keys that are not placed (keeping them as fast as vanilla).
 *
 * For RANDOM_SPREAD placements (the common, vanilla-style spaced grid) the locator jumps
 * straight to the single candidate chunk in each spacing-sized grid cell rather than testing
 * every chunk -- roughly a {@code spacing^2} reduction in placement checks, matching vanilla
 * locate performance. Density placements use a bounded per-chunk ring scan, while concentric
 * placements enumerate their finite configured starts directly.
 */
public final class IrisStructureLocator {
    private static final int CANDIDATE_BUDGET = 4_096;
    private static final int MAX_BURIAL_COLUMNS = 2_000_000;
    private static final int UNDERGROUND_SURFACE_CLEARANCE = 1;
    private static final long CAVE_ANCHOR_DOMAIN = 0x43415645414E4348L;
    private static final Pattern NAMESPACED_RESOURCE_KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    private static final PlacementIndex EMPTY_INDEX = new PlacementIndex(
            Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
            Collections.emptySet(), Collections.emptyList());
    private static final LocatableIndex EMPTY_LOCATABLE_INDEX = new LocatableIndex(
            Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
            Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
    private static final LocateResult NOT_FOUND_RESULT = new LocateResult(LocateStatus.NOT_FOUND, 0, 0, 0);
    private static final LocateResult SEARCH_LIMIT_RESULT =
            new LocateResult(LocateStatus.SEARCH_LIMIT_REACHED, 0, 0, 0);

    private IrisStructureLocator() {
    }

    /** Structure keys referenced by any Iris assembly or native jigsaw placement. */
    public static Set<String> placedKeys(PackWorld world) {
        if (world == null) {
            return Collections.emptySet();
        }
        return index(world).loadKeys;
    }

    /**
     * Every key under which a structure the engine's placements place can be found: the placed keys and the platform
     * structures Iris structures stand in for ({@code vanillaSource}), the latter in lower case.
     */
    public static Set<String> placedKeysAndAliases(PackWorld world) {
        if (world == null) {
            return Collections.emptySet();
        }
        PlacementIndex placementIndex = index(world);
        Set<String> keys = new LinkedHashSet<>(placementIndex.loadKeys);
        keys.addAll(placementIndex.vanillaAliases);
        return Collections.unmodifiableSet(keys);
    }

    /** The platform structures the engine's placements start natively ({@code nativeStructures}), in lower case. */
    public static Set<String> nativeKeys(PackWorld world) {
        if (world == null) {
            return Collections.emptySet();
        }
        return index(world).nativeKeys;
    }

    public static boolean isPlaced(PackWorld world, String key) {
        if (world == null || key == null || key.isEmpty()) {
            return false;
        }
        PlacementIndex placementIndex = index(world);
        String normalizedKey = normalize(key);
        return placementIndex.normalizedLoadKeys.contains(normalizedKey)
                || placementIndex.vanillaAliases.contains(normalizedKey);
    }

    public static boolean hasLocatablePlacement(PackWorld world, String key) {
        if (world == null || key == null || key.isBlank()) {
            return false;
        }
        return locatableIndex(world).normalizedKeys().contains(normalize(key));
    }

    public static boolean hasLocatableEditablePlacement(PackWorld world, String key) {
        if (world == null || key == null || key.isBlank()) {
            return false;
        }
        return locatableIndex(world).normalizedEditableKeys().contains(normalize(key));
    }

    public static boolean hasLocatableNativePlacement(PackWorld world, String key) {
        if (world == null || key == null || key.isBlank()) {
            return false;
        }
        return locatableIndex(world).normalizedNativeKeys().contains(normalize(key));
    }

    public static Set<String> locatableKeys(PackWorld world) {
        return locatableIndex(world).keys();
    }

    public static Set<String> locatableEditableKeys(PackWorld world) {
        return locatableIndex(world).editableKeys();
    }

    public static Set<String> locatableNativeKeys(PackWorld world) {
        return locatableIndex(world).nativeKeys();
    }

    public static boolean hasNativePlacement(PackWorld world, String key) {
        if (world == null || key == null || key.isBlank()) {
            return false;
        }
        return index(world).nativeKeys.contains(normalize(key));
    }

    public static boolean hasEditablePlacement(PackWorld world, String key) {
        if (world == null || key == null || key.isBlank()) {
            return false;
        }
        for (StructurePlacement placement : index(world).placements) {
            if (placement.hasIrisStructures() && matches(placement, key, world)) {
                return true;
            }
        }
        return false;
    }

    public static boolean suppressesVanilla(PackWorld world, String vanillaKey) {
        if (world == null || vanillaKey == null || vanillaKey.isEmpty()) {
            return false;
        }
        return index(world).suppressedVanillaSources.contains(normalize(vanillaKey));
    }

    public static boolean startsInChunk(PackWorld world, String key, int cx, int cz) {
        if (!isPlaced(world, key)) {
            return false;
        }
        return resolveInChunk(world, key, cx, cz) != null;
    }

    public static LocateResult locate(PackWorld world, String key, int fromBlockX, int fromBlockZ, int maxRadiusChunks) {
        return locate(world, key, fromBlockX, fromBlockZ, maxRadiusChunks, (chunkX, chunkZ) -> true);
    }

    public static LocateResult locate(PackWorld world, String key, int fromBlockX, int fromBlockZ,
                                      int maxRadiusChunks, CandidateFilter candidateFilter) {
        try {
            return locateFiltered(world, key, fromBlockX, fromBlockZ, maxRadiusChunks, candidateFilter);
        } catch (CandidateSearchLimitException ignored) {
            return SEARCH_LIMIT_RESULT;
        }
    }

    public static LocateResult locateInPlacementRings(
            PackWorld world, String key, int fromBlockX, int fromBlockZ,
            int maxSearchRadius, CandidateFilter candidateFilter) {
        try {
            return locateInPlacementRingsFiltered(
                    world, key, fromBlockX, fromBlockZ, maxSearchRadius, candidateFilter);
        } catch (CandidateSearchLimitException ignored) {
            return SEARCH_LIMIT_RESULT;
        }
    }

    private static LocateResult locateInPlacementRingsFiltered(
            PackWorld world, String key, int fromBlockX, int fromBlockZ,
            int maxSearchRadius, CandidateFilter candidateFilter) {
        if (!isPlaced(world, key)) {
            return NOT_FOUND_RESULT;
        }
        CandidateFilter activeFilter = Objects.requireNonNull(
                candidateFilter, "Structure locate filter must not be null");
        int maximumRing = Math.max(0, Math.min(maxSearchRadius, 2048));
        int centerChunkX = fromBlockX >> 4;
        int centerChunkZ = fromBlockZ >> 4;
        PlacementCatalog catalog = collectPlacementCatalog(world, key);
        if (catalog.randomSpread().isEmpty()
                && catalog.concentricRings().isEmpty() && !catalog.hasDensity()) {
            return NOT_FOUND_RESULT;
        }
        long seed = StructureWorld.placementSeed(world);
        CandidateBudget candidateBudget = new CandidateBudget();
        LocatedCandidate best = nearestConcentricCandidate(
                world, key, fromBlockX, fromBlockZ, seed, catalog, activeFilter, candidateBudget);

        for (int ring = 0; ring <= maximumRing; ring++) {
            boolean foundInRing = false;
            for (RandomSpreadParameters parameters : catalog.randomSpread()) {
                LocatedCandidate candidate = firstRandomSpreadCandidateInRing(
                        world, key, fromBlockX, fromBlockZ,
                        centerChunkX, centerChunkZ, ring, seed,
                        parameters, activeFilter, candidateBudget);
                if (candidate == null) {
                    continue;
                }
                foundInRing = true;
                if (best == null || candidate.distanceSquared() < best.distanceSquared()) {
                    best = candidate;
                }
            }
            if (catalog.hasDensity()) {
                LocatedCandidate candidate = firstDensityCandidateInRing(
                        world, key, fromBlockX, fromBlockZ,
                        centerChunkX, centerChunkZ, ring,
                        activeFilter, candidateBudget);
                if (candidate != null) {
                    foundInRing = true;
                    if (best == null || candidate.distanceSquared() < best.distanceSquared()) {
                        best = candidate;
                    }
                }
            }
            if (foundInRing) {
                return found(best);
            }
        }
        return best == null ? NOT_FOUND_RESULT : found(best);
    }

    private static LocatedCandidate nearestConcentricCandidate(
            PackWorld world, String key, int fromBlockX, int fromBlockZ,
            long seed, PlacementCatalog catalog, CandidateFilter candidateFilter,
            CandidateBudget candidateBudget) {
        LocatedCandidate best = null;
        Set<Long> checkedChunks = new LinkedHashSet<>();
        for (StructurePlacement placement : catalog.concentricRings()) {
            int count = Math.max(1, placement.ringCount());
            for (int placementIndex = 0; placementIndex < count; placementIndex++) {
                candidateBudget.claim();
                int[] candidate = StructurePlacementGrid.concentricRingChunk(
                        placement, placementIndex, seed);
                if (candidate == null || !checkedChunks.add(chunkKey(candidate[0], candidate[1]))) {
                    continue;
                }
                ResolvedStart resolved = resolveInChunk(world, key, candidate[0], candidate[1]);
                if (resolved == null || !candidateFilter.accept(candidate[0], candidate[1])) {
                    continue;
                }
                long distanceSquared = blockDistanceSquared(
                        resolved, fromBlockX, fromBlockZ);
                if (best == null || distanceSquared < best.distanceSquared()) {
                    best = new LocatedCandidate(resolved, distanceSquared);
                }
            }
        }
        return best;
    }

    private static LocatedCandidate firstRandomSpreadCandidateInRing(
            PackWorld world, String key, int fromBlockX, int fromBlockZ,
            int centerChunkX, int centerChunkZ, int ring, long seed,
            RandomSpreadParameters parameters, CandidateFilter candidateFilter,
            CandidateBudget candidateBudget) {
        int spacing = Math.max(1, parameters.spacing());
        int centerCellX = Math.floorDiv(centerChunkX, spacing);
        int centerCellZ = Math.floorDiv(centerChunkZ, spacing);
        for (int offsetX = -ring; offsetX <= ring; offsetX++) {
            boolean edgeX = offsetX == -ring || offsetX == ring;
            for (int offsetZ = -ring; offsetZ <= ring; offsetZ++) {
                boolean edgeZ = offsetZ == -ring || offsetZ == ring;
                if (!edgeX && !edgeZ) {
                    continue;
                }
                candidateBudget.claim();
                int[] candidate = StructurePlacementGrid.randomSpreadCellChunk(
                        centerCellX + offsetX, centerCellZ + offsetZ,
                        spacing, parameters.separation(), parameters.salt(), seed);
                ResolvedStart resolved = resolveInChunk(world, key, candidate[0], candidate[1]);
                if (resolved != null && candidateFilter.accept(candidate[0], candidate[1])) {
                    return new LocatedCandidate(
                            resolved, blockDistanceSquared(resolved, fromBlockX, fromBlockZ));
                }
            }
        }
        return null;
    }

    private static LocatedCandidate firstDensityCandidateInRing(
            PackWorld world, String key, int fromBlockX, int fromBlockZ,
            int centerChunkX, int centerChunkZ, int ring,
            CandidateFilter candidateFilter, CandidateBudget candidateBudget) {
        for (int offsetX = -ring; offsetX <= ring; offsetX++) {
            boolean edgeX = offsetX == -ring || offsetX == ring;
            for (int offsetZ = -ring; offsetZ <= ring; offsetZ++) {
                boolean edgeZ = offsetZ == -ring || offsetZ == ring;
                if (!edgeX && !edgeZ) {
                    continue;
                }
                int chunkX = centerChunkX + offsetX;
                int chunkZ = centerChunkZ + offsetZ;
                candidateBudget.claim();
                ResolvedStart resolved = resolveInChunk(world, key, chunkX, chunkZ);
                if (resolved != null && candidateFilter.accept(chunkX, chunkZ)) {
                    return new LocatedCandidate(
                            resolved, blockDistanceSquared(resolved, fromBlockX, fromBlockZ));
                }
            }
        }
        return null;
    }

    private static LocateResult locateFiltered(PackWorld world, String key, int fromBlockX, int fromBlockZ,
                                               int maxRadiusChunks, CandidateFilter candidateFilter) {
        if (!isPlaced(world, key)) {
            return NOT_FOUND_RESULT;
        }
        CandidateFilter activeFilter = Objects.requireNonNull(candidateFilter, "Structure locate filter must not be null");
        int max = Math.max(0, Math.min(maxRadiusChunks, 2048));
        int pcx = fromBlockX >> 4;
        int pcz = fromBlockZ >> 4;
        LocatedCandidate best = null;
        PlacementCatalog catalog = collectPlacementCatalog(world, key);
        if (catalog.randomSpread().isEmpty() && catalog.concentricRings().isEmpty() && !catalog.hasDensity()) {
            return NOT_FOUND_RESULT;
        }
        long seed = StructureWorld.placementSeed(world);
        CandidateBudget candidateBudget = new CandidateBudget();

        for (RandomSpreadParameters parameters : catalog.randomSpread) {
            int spacing = Math.max(1, parameters.spacing());
            int centerCellX = Math.floorDiv(pcx, spacing);
            int centerCellZ = Math.floorDiv(pcz, spacing);
            int cellRadius = (max / spacing) + 2;

            for (int r = 0; r <= cellRadius; r++) {
                long chunkLowerBound = cellRingDistanceLowerBound(
                        r, spacing, pcx, pcz, centerCellX, centerCellZ);
                long blockLowerBound = chunkDeltaBlockDistanceLowerBound(chunkLowerBound);
                if (best != null && squareSaturated(blockLowerBound) >= best.distanceSquared()) {
                    break;
                }
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        candidateBudget.claim();
                        int[] candidate = StructurePlacementGrid.randomSpreadCellChunk(
                                centerCellX + dx, centerCellZ + dz, spacing, parameters.separation(), parameters.salt(), seed);
                        int cx = candidate[0];
                        int cz = candidate[1];
                        if (!withinRadius(cx, cz, pcx, pcz, max)
                                || best != null && chunkBlockDistanceSquaredLowerBound(
                                cx, cz, fromBlockX, fromBlockZ) >= best.distanceSquared()) {
                            continue;
                        }
                        ResolvedStart resolved = resolveInChunk(world, key, cx, cz);
                        if (resolved != null && activeFilter.accept(cx, cz)) {
                            long distanceSquared = blockDistanceSquared(
                                    resolved, fromBlockX, fromBlockZ);
                            if (best == null || distanceSquared < best.distanceSquared()) {
                                best = new LocatedCandidate(resolved, distanceSquared);
                            }
                        }
                    }
                }
            }
        }

        Set<Long> checkedRingChunks = new LinkedHashSet<>();
        for (StructurePlacement placement : catalog.concentricRings) {
            int count = Math.max(1, placement.ringCount());
            for (int placementIndex = 0; placementIndex < count; placementIndex++) {
                candidateBudget.claim();
                int[] candidate = StructurePlacementGrid.concentricRingChunk(placement, placementIndex, seed);
                if (candidate == null || !checkedRingChunks.add(chunkKey(candidate[0], candidate[1]))) {
                    continue;
                }
                if (!withinRadius(candidate[0], candidate[1], pcx, pcz, max)
                        || best != null && chunkBlockDistanceSquaredLowerBound(
                        candidate[0], candidate[1], fromBlockX, fromBlockZ) >= best.distanceSquared()) {
                    continue;
                }
                ResolvedStart resolved = resolveInChunk(world, key, candidate[0], candidate[1]);
                if (resolved != null && activeFilter.accept(candidate[0], candidate[1])) {
                    long distanceSquared = blockDistanceSquared(
                            resolved, fromBlockX, fromBlockZ);
                    if (best == null || distanceSquared < best.distanceSquared()) {
                        best = new LocatedCandidate(resolved, distanceSquared);
                    }
                }
            }
        }

        if (catalog.hasDensity) {
            for (int r = 0; r <= max; r++) {
                long ringBlockLowerBound = chunkDeltaBlockDistanceLowerBound(r);
                if (best != null && squareSaturated(ringBlockLowerBound) >= best.distanceSquared()) {
                    break;
                }
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        int cx = pcx + dx;
                        int cz = pcz + dz;
                        if (best != null && chunkBlockDistanceSquaredLowerBound(
                                cx, cz, fromBlockX, fromBlockZ) >= best.distanceSquared()) {
                            continue;
                        }
                        candidateBudget.claim();
                        ResolvedStart resolved = resolveInChunk(world, key, cx, cz);
                        if (resolved != null && activeFilter.accept(cx, cz)) {
                            long distanceSquared = blockDistanceSquared(
                                    resolved, fromBlockX, fromBlockZ);
                            if (best == null || distanceSquared < best.distanceSquared()) {
                                best = new LocatedCandidate(resolved, distanceSquared);
                            }
                        }
                    }
                }
            }
        }

        if (best == null) {
            return NOT_FOUND_RESULT;
        }
        return found(best);
    }

    static boolean withinRadius(int chunkX, int chunkZ,
                                int centerChunkX, int centerChunkZ, int radius) {
        return Math.abs((long) chunkX - centerChunkX) <= radius
                && Math.abs((long) chunkZ - centerChunkZ) <= radius;
    }

    public static ResolvedPlacement resolvePlacement(PackWorld world, StructurePlacement placement, int cx, int cz) {
        if (world == null) {
            throw new IllegalStateException("Iris structure placement requires a world");
        }
        NativeStartPlanner.validateBackend(placement);
        if (!placement.hasIrisStructures()) {
            throw new IllegalStateException("Iris structure placement is missing its structure list");
        }
        long seed = StructureWorld.placementSeed(world);
        if (!StructurePlacementGrid.startsInChunk(placement, cx, cz, seed)) {
            return null;
        }

        RNG rng = StructurePlacementGrid.placementRng(placement, cx, cz, seed);
        int originX = (cx << 4) + rng.nextInt(16);
        int originZ = (cz << 4) + rng.nextInt(16);
        PlacementAnchor anchor = resolveAnchor(world, placement, cx, cz, originX, originZ, rng);
        if (anchor == null) {
            return null;
        }
        originX = anchor.x();
        originZ = anchor.z();
        int baseY = anchor.y();

        String selectedKey = selectStructureKey(placement, rng);
        if (selectedKey == null || selectedKey.isBlank()) {
            throw new IllegalStateException("Iris structure placement selected a blank structure key");
        }
        PackResources pack = world.resources();
        ResourceId structureId = pack.idOf(selectedKey);
        StructureAssembly provider = providerOf(world, structureId);
        if (provider == null) {
            throw new IllegalStateException("Iris structure placement references missing structure '"
                    + selectedKey + "'");
        }
        if (!provider.viability(pack, structureId).isViable()) {
            throw new IllegalStateException("Iris structure '" + selectedKey
                    + "' has no runtime-viable assembly graph");
        }

        Assembly assembly = provider.assemble(pack, structureId, new BlockPosition(originX, baseY, originZ), rng)
                .orElseThrow();
        if (!requirePlacementOutput(placement, selectedKey, cx, cz,
                assembly.hasOutput(), "runtime assembly produced " + assembly.status() + ": " + assembly.detail())) {
            return null;
        }
        StructureStamp stamp = provider.stamp(pack, structureId).orElseThrow();
        KList<AssembledPiece> pieces = new KList<>(assembly.pieces());
        pieces = alignCavePieces(pieces, placement, baseY);
        pieces = alignSurfacePieces(pieces, placement, stamp, baseY);
        if (!requirePlacementOutput(placement, selectedKey, cx, cz,
                pieces != null && !pieces.isEmpty(), "surface alignment produced no pieces")) {
            return null;
        }
        boolean exactY = hasExactY(placement, stamp, pieces);

        int worldMin = world.minWorldY() + 1;
        int worldMax = world.minWorldY() + StructureWorld.height(world) - 1;
        if (exactY) {
            Integer verticalShift = resolveVerticalShift(pieces, placement, baseY, worldMin, worldMax);
            if (!requirePlacementOutput(placement, selectedKey, cx, cz, verticalShift != null,
                    "assembled pieces cannot fit the configured vertical and world bounds")) {
                return null;
            }
            if (verticalShift != 0) {
                pieces = shiftPieces(pieces, verticalShift);
                baseY += verticalShift;
            }
        }
        if (placement.isAnchoredUnderground() && !placement.resolvedAnchor().isCave()) {
            Integer burialShift = resolveUndergroundBurialShift(
                    world, pieces, placement, baseY, worldMin, worldMax);
            if (!requirePlacementOutput(placement, selectedKey, cx, cz, burialShift != null,
                    "assembled pieces and carving envelope cannot remain fully buried beneath terrain")) {
                return null;
            }
            if (burialShift != 0) {
                pieces = shiftPieces(pieces, burialShift);
                baseY += burialShift;
            }
        }

        long configuredRadius = (long) Math.max(1, provider.reachChunks(pack, structureId).orElse(1)) * 16L;
        int structureRadius = (int) Math.min(Integer.MAX_VALUE, configuredRadius);
        boolean withinBounds = fitsHorizontalBounds(pieces, originX, originZ, structureRadius)
                && (!exactY || fitsVerticalBounds(pieces, worldMin, worldMax));
        if (!requirePlacementOutput(placement, selectedKey, cx, cz, withinBounds,
                "assembled pieces exceed the configured structure or world bounds")) {
            return null;
        }
        return new ResolvedPlacement(placement, selectedKey, stamp, pieces, rng, originX, baseY, originZ, exactY);
    }

    /**
     * The first provider of {@link StructureAssembly} among the modules of the engine's pack that knows {@code structure};
     * null when none does.
     */
    public static StructureAssembly providerOf(PackWorld world, ResourceId structure) {
        PackResources pack = world.resources();
        for (StructureAssembly provider : world.services(StructureAssembly.class)) {
            if (provider.knows(pack, structure)) {
                return provider;
            }
        }
        return null;
    }

    /** How the pieces of the pack structure {@code key} are stamped; null when no module knows the structure. */
    public static StructureStamp stampOf(PackWorld world, String key) {
        PackResources pack = world.resources();
        ResourceId structure = pack.idOf(key);
        StructureAssembly provider = providerOf(world, structure);
        return provider == null ? null : provider.stamp(pack, structure).orElse(null);
    }

    public static boolean requirePlacementOutput(StructurePlacement placement, String structureKey,
                                                 int chunkX, int chunkZ, boolean outputPresent,
                                                 String failureReason) {
        if (outputPresent) {
            return true;
        }
        if (placement == null
                || NativeStructureSuppression.REPLACE_SOURCE != placement.nativeSuppression()) {
            return false;
        }
        String key = structureKey == null || structureKey.isBlank()
                ? String.valueOf(placement.structures()) : structureKey;
        String reason = failureReason == null || failureReason.isBlank()
                ? "placement produced no output" : failureReason;
        throw new IllegalStateException("REPLACE_SOURCE placement for Iris structure '" + key
                + "' failed in chunk " + chunkX + "," + chunkZ + ": " + reason
                + ". Native generation is suppressed and will not be used as a fallback");
    }

    static boolean fitsWorldBounds(KList<AssembledPiece> pieces, int worldMin, int worldMax,
                                  int originX, int originZ, int structureRadius) {
        int[] bounds = computeBounds(pieces);
        if (bounds == null) {
            return false;
        }
        return fitsVerticalBounds(bounds, worldMin, worldMax)
                && fitsHorizontalBounds(bounds, originX, originZ, structureRadius);
    }

    private static ResolvedStart resolveInChunk(PackWorld world, String key, int cx, int cz) {
        String normalizedKey = normalize(key);
        if (hasNativePlacement(world, key)) {
            for (NativeStartPlanner.Plan plan : NativeStartPlanner.plansAt(world, cx, cz)) {
                if (normalize(plan.source().structure()).equals(normalizedKey)) {
                    return new ResolvedStart(cx << 4, plan.baseY(), cz << 4);
                }
            }
        }
        List<StructurePlacement> placements = StructurePlacementScope.placementsAt(world, cx, cz);
        for (StructurePlacement placement : placements) {
            if (!matches(placement, key, world)) {
                continue;
            }
            if (placement.hasNativeStructures()) {
                continue;
            }
            ResolvedPlacement resolved = resolvePlacement(world, placement, cx, cz);
            if (resolved != null && matchesResolved(resolved, key)) {
                return new ResolvedStart(
                        resolved.originX(), resolved.baseY(), resolved.originZ());
            }
        }
        return null;
    }

    private static PlacementAnchor resolveAnchor(
            PackWorld world,
            StructurePlacement placement,
            int chunkX,
            int chunkZ,
            int originX,
            int originZ,
            RNG rng
    ) {
        if (placement.resolvedAnchor().isCave()) {
            StructureCaveAnchorResolver.Anchor caveAnchor = StructureCaveAnchorResolver.resolve(
                    world, placement, chunkX, chunkZ, rng.nextParallelRNG(CAVE_ANCHOR_DOMAIN));
            return caveAnchor == null ? null : new PlacementAnchor(caveAnchor.x(), caveAnchor.y(), caveAnchor.z());
        }
        Integer baseY = resolveBaseY(world, placement, originX, originZ, rng);
        return baseY == null ? null : new PlacementAnchor(originX, baseY, originZ);
    }

    private static Integer resolveBaseY(PackWorld world, StructurePlacement placement, int originX, int originZ, RNG rng) {
        int worldMin = world.minWorldY() + 1;
        int worldMax = world.minWorldY() + StructureWorld.height(world) - 1;
        if (worldMin > worldMax) {
            return null;
        }
        if (!placement.underwater()
                && NativeStartPlanner.isSubmerged(world, originX, originZ)) {
            return null;
        }
        if (placement.resolvedAnchor() == StructureAnchorMode.SURFACE) {
            int surfaceY = StructureWorld.highest(world, originX, originZ);
            return surfaceY < placement.minHeight() || surfaceY > placement.maxHeight() ? null : surfaceY;
        }
        int bandMin = Math.max(worldMin, Math.min(placement.minHeight(), placement.maxHeight()));
        int bandMax = Math.min(worldMax, Math.max(placement.minHeight(), placement.maxHeight()));
        if (bandMin > bandMax) {
            return null;
        }
        return randomInclusive(rng, bandMin, bandMax);
    }

    static Integer resolveVerticalShift(KList<AssembledPiece> pieces, StructurePlacement placement,
                                        int baseY, int worldMin, int worldMax) {
        int[] bounds = computeBounds(pieces);
        if (bounds == null) {
            return null;
        }
        if (!placement.isAnchoredUnderground() || placement.resolvedAnchor().isCave()) {
            return bounds[1] >= worldMin && bounds[4] <= worldMax ? 0 : null;
        }
        int bandMin = Math.max(worldMin, Math.min(placement.minHeight(), placement.maxHeight()));
        int bandMax = Math.min(worldMax, Math.max(placement.minHeight(), placement.maxHeight()));
        int minimumShift = Math.max(worldMin - bounds[1], bandMin - baseY);
        int maximumShift = Math.min(worldMax - bounds[4], bandMax - baseY);
        if (minimumShift > maximumShift) {
            return null;
        }
        if (minimumShift > 0) {
            return minimumShift;
        }
        if (maximumShift < 0) {
            return maximumShift;
        }
        return 0;
    }

    static Integer resolveUndergroundBurialShift(PackWorld world, KList<AssembledPiece> pieces,
                                                  StructurePlacement placement, int baseY,
                                                  int worldMin, int worldMax) {
        int[] bounds = computeBounds(pieces);
        if (world == null || bounds == null || placement == null || !placement.isAnchoredUnderground()
                || placement.resolvedAnchor().isCave()) {
            return null;
        }

        StructureTerrain terrain = placement.resolvedTerrain();
        StructureTerrainMode terrainMode = terrain.resolvedMode();
        boolean forceCarve = terrainMode == StructureTerrainMode.FORCE_CARVE;
        boolean bore = terrainMode == StructureTerrainMode.BORE;
        int sideExtension = forceCarve || bore ? Math.max(0, terrain.horizontalPadding()) : 0;
        StructureCarveShape carveShape = terrain.resolvedShape();
        int topExtension = forceCarve
                ? carveShape.maximumCeilingExtension(
                        terrain.ceilingPadding(), terrain.resolvedErosionStrength())
                : bore ? Math.max(0, terrain.ceilingPadding()) : 0;
        int bottomExtension = forceCarve || bore ? Math.max(0, terrain.floorPadding()) : 0;
        int bandMin = Math.max(worldMin, Math.min(placement.minHeight(), placement.maxHeight()));
        int bandMax = Math.min(worldMax, Math.max(placement.minHeight(), placement.maxHeight()));
        int minimumShift = Math.max(worldMin - (bounds[1] - bottomExtension), bandMin - baseY);
        int maximumShift = Math.min(0, Math.min(worldMax - (bounds[4] + topExtension), bandMax - baseY));
        if (minimumShift > maximumShift) {
            return null;
        }

        ColumnHeights surfaceHeights = new ColumnHeights(Integer.MIN_VALUE);
        if (bore || forceCarve && carveShape == StructureCarveShape.BOX) {
            maximumShift = resolveBurialEnvelopeShift(
                    world,
                    bounds[0] - sideExtension,
                    bounds[3] + sideExtension,
                    bounds[2] - sideExtension,
                    bounds[5] + sideExtension,
                    bounds[4] + topExtension,
                    maximumShift,
                    surfaceHeights
            );
            if (maximumShift == Integer.MIN_VALUE) {
                return null;
            }
        } else {
            for (AssembledPiece piece : pieces) {
                maximumShift = resolveBurialEnvelopeShift(
                        world,
                        piece.minX() - sideExtension,
                        piece.maxX() + sideExtension,
                        piece.minZ() - sideExtension,
                        piece.maxZ() + sideExtension,
                        piece.maxY() + topExtension,
                        maximumShift,
                        surfaceHeights
                );
                if (maximumShift == Integer.MIN_VALUE) {
                    return null;
                }
            }
        }
        return minimumShift > maximumShift ? null : maximumShift;
    }

    private static int resolveBurialEnvelopeShift(PackWorld world, int minX, int maxX, int minZ, int maxZ,
                                                   int envelopeTopY, int maximumShift,
                                                   ColumnHeights surfaceHeights) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                long columnKey = ((long) x << 32) ^ (z & 0xffffffffL);
                int surfaceY = surfaceHeights.get(columnKey);
                if (surfaceY == Integer.MIN_VALUE) {
                    if (surfaceHeights.size() >= MAX_BURIAL_COLUMNS) {
                        return Integer.MIN_VALUE;
                    }
                    surfaceY = StructureWorld.highest(world, x, z);
                    surfaceHeights.put(columnKey, surfaceY);
                }
                int allowedTopY = surfaceY - UNDERGROUND_SURFACE_CLEARANCE;
                maximumShift = Math.min(maximumShift, allowedTopY - envelopeTopY);
            }
        }
        return maximumShift;
    }

    static String selectStructureKey(StructurePlacement placement, RNG rng) {
        if (placement == null || placement.structures().isEmpty()) {
            return null;
        }
        return placement.structures().get(rng.nextInt(placement.structures().size()));
    }

    static KList<AssembledPiece> alignCavePieces(
            KList<AssembledPiece> pieces,
            StructurePlacement placement,
            int anchorY
    ) {
        StructureAnchorMode mode = placement.resolvedAnchor();
        if (!mode.isCave()) {
            return pieces;
        }
        int[] bounds = computeBounds(pieces);
        if (bounds == null) {
            return pieces;
        }
        int targetY = switch (mode) {
            case CAVE_FLOOR -> bounds[1];
            case CAVE_CEILING -> bounds[4];
            case CAVE_CENTER, CAVE_ANY -> bounds[1] + Math.floorDiv(bounds[4] - bounds[1], 2);
            default -> anchorY;
        };
        return shiftPieces(pieces, anchorY - targetY);
    }

    private static KList<AssembledPiece> alignSurfacePieces(KList<AssembledPiece> pieces,
                                                                  StructurePlacement placement,
                                                                  StructureStamp stamp, int baseY) {
        if (placement.isAnchoredUnderground() || pieces.size() <= 1
                || stamp.placeMode().placeMode().has(PlaceTrait.FREE_HEIGHT)) {
            return pieces;
        }
        int[] bounds = computeBounds(pieces);
        return bounds == null ? pieces : shiftPieces(pieces, baseY - bounds[1]);
    }

    static boolean hasExactY(StructurePlacement placement, StructureStamp stamp,
                             KList<AssembledPiece> pieces) {
        return placement.isAnchoredUnderground() || pieces.size() > 1
                || stamp.placeMode().placeMode().has(PlaceTrait.FREE_HEIGHT);
    }

    private static boolean fitsVerticalBounds(KList<AssembledPiece> pieces, int worldMin, int worldMax) {
        int[] bounds = computeBounds(pieces);
        return bounds != null && fitsVerticalBounds(bounds, worldMin, worldMax);
    }

    private static boolean fitsVerticalBounds(int[] bounds, int worldMin, int worldMax) {
        return bounds[1] >= worldMin && bounds[4] <= worldMax;
    }

    private static boolean fitsHorizontalBounds(KList<AssembledPiece> pieces, int originX, int originZ,
                                                int structureRadius) {
        int[] bounds = computeBounds(pieces);
        return bounds != null && fitsHorizontalBounds(bounds, originX, originZ, structureRadius);
    }

    private static boolean fitsHorizontalBounds(int[] bounds, int originX, int originZ, int structureRadius) {
        return bounds[0] >= originX - structureRadius && bounds[3] <= originX + structureRadius
                && bounds[2] >= originZ - structureRadius && bounds[5] <= originZ + structureRadius;
    }

    private static KList<AssembledPiece> shiftPieces(KList<AssembledPiece> pieces, int shiftY) {
        KList<AssembledPiece> shifted = new KList<>();
        for (AssembledPiece piece : pieces) {
            shifted.add(piece.shiftedUp(shiftY));
        }
        return shifted;
    }

    private static int[] computeBounds(KList<AssembledPiece> pieces) {
        if (pieces == null || pieces.isEmpty()) {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (AssembledPiece piece : pieces) {
            minX = Math.min(minX, piece.minX());
            minY = Math.min(minY, piece.minY());
            minZ = Math.min(minZ, piece.minZ());
            maxX = Math.max(maxX, piece.maxX());
            maxY = Math.max(maxY, piece.maxY());
            maxZ = Math.max(maxZ, piece.maxZ());
        }
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    private static int randomInclusive(RNG rng, int minimum, int maximum) {
        if (minimum == maximum) {
            return minimum;
        }
        return minimum + rng.nextInt((maximum - minimum) + 1);
    }

    private static PlacementCatalog collectPlacementCatalog(PackWorld world, String key) {
        Set<RandomSpreadParameters> randomSpread = new LinkedHashSet<>();
        List<StructurePlacement> concentricRings = new ArrayList<>();
        boolean hasDensity = false;
        for (StructurePlacement placement : index(world).placements) {
            if (!isSearchablePlacement(world, placement)
                    || !matches(placement, key, world)) {
                continue;
            }
            if (placement.distribution() == StructureDistribution.RANDOM_SPREAD) {
                randomSpread.add(new RandomSpreadParameters(
                        Math.max(1, placement.spacing()), placement.separation(),
                        StructurePlacementGrid.placementSalt(placement)));
            } else if (placement.distribution() == StructureDistribution.CONCENTRIC_RINGS) {
                concentricRings.add(placement);
            } else if (placement.distribution() == StructureDistribution.DENSITY) {
                hasDensity = true;
            }
        }
        return new PlacementCatalog(List.copyOf(randomSpread), List.copyOf(concentricRings), hasDensity);
    }

    static boolean isSearchableDensityPlacement(PackWorld world, StructurePlacement placement) {
        return placement != null
                && placement.distribution() == StructureDistribution.DENSITY
                && isSearchablePlacement(world, placement);
    }

    static boolean isSearchablePlacement(PackWorld world, StructurePlacement placement) {
        if (world == null || placement == null || StructureWorld.height(world) <= 0) {
            return false;
        }
        if (placement.distribution() == StructureDistribution.DENSITY
                && !(placement.density() > 0.0)) {
            return false;
        }
        long worldMin = (long) world.minWorldY() + (placement.isAnchoredUnderground() ? 1L : 0L);
        long worldMax = (long) world.minWorldY() + StructureWorld.height(world)
                - (placement.resolvedAnchor().isCave() ? 2L : 1L);
        if (worldMin > worldMax) {
            return false;
        }
        long configuredMin = placement.isAnchoredUnderground()
                ? Math.min(placement.minHeight(), placement.maxHeight())
                : placement.minHeight();
        long configuredMax = placement.isAnchoredUnderground()
                ? Math.max(placement.minHeight(), placement.maxHeight())
                : placement.maxHeight();
        return configuredMin <= configuredMax && configuredMin <= worldMax && configuredMax >= worldMin;
    }

    private static long blockDistanceSquared(
            ResolvedStart resolved, int fromBlockX, int fromBlockZ) {
        long dx = (long) resolved.originX() - fromBlockX;
        long dz = (long) resolved.originZ() - fromBlockZ;
        return distanceSquaredSaturated(dx, dz);
    }

    static long chunkBlockDistanceSquaredLowerBound(
            int chunkX, int chunkZ, int fromBlockX, int fromBlockZ) {
        long minimumX = (long) chunkX << 4;
        long minimumZ = (long) chunkZ << 4;
        long dx = axisDistance(fromBlockX, minimumX, minimumX + 15L);
        long dz = axisDistance(fromBlockZ, minimumZ, minimumZ + 15L);
        return distanceSquaredSaturated(dx, dz);
    }

    private static long axisDistance(long point, long minimum, long maximum) {
        if (point < minimum) {
            return minimum - point;
        }
        if (point > maximum) {
            return point - maximum;
        }
        return 0L;
    }

    private static long chunkDeltaBlockDistanceLowerBound(long chunkDelta) {
        return chunkDelta <= 0L ? 0L : chunkDelta * 16L - 15L;
    }

    private static long distanceSquaredSaturated(long dx, long dz) {
        long xSquared = squareSaturated(dx);
        long zSquared = squareSaturated(dz);
        if (Long.MAX_VALUE - xSquared < zSquared) {
            return Long.MAX_VALUE;
        }
        return xSquared + zSquared;
    }

    private static long squareSaturated(long value) {
        long absolute = Math.abs(value);
        if (absolute > 3_037_000_499L) {
            return Long.MAX_VALUE;
        }
        return absolute * absolute;
    }

    private static LocateResult found(LocatedCandidate candidate) {
        ResolvedStart resolved = candidate.resolved();
        return new LocateResult(LocateStatus.FOUND, resolved.originX(), resolved.baseY(), resolved.originZ());
    }

    static long cellRingDistanceLowerBound(int ring, int spacing, int pcx, int pcz,
                                           int centerCellX, int centerCellZ) {
        if (ring <= 0) {
            return 0L;
        }
        int localX = pcx - (centerCellX * spacing);
        int localZ = pcz - (centerCellZ * spacing);
        long ringOffset = (long) ring * spacing;
        long positiveX = ringOffset - localX;
        long negativeX = ringOffset + localX - (spacing - 1L);
        long positiveZ = ringOffset - localZ;
        long negativeZ = ringOffset + localZ - (spacing - 1L);
        return Math.min(Math.min(positiveX, negativeX), Math.min(positiveZ, negativeZ));
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    private record PlacementAnchor(int x, int y, int z) {
    }

    private static boolean matches(StructurePlacement placement, String key, PackWorld world) {
        if (placement == null || key == null || world == null) {
            return false;
        }
        String normalizedKey = normalize(key);
        for (NativeStructureSource source : placement.nativeStructures()) {
            if (source != null && normalize(source.structure()).equals(normalizedKey)) {
                return true;
            }
        }
        for (String structureKey : placement.structures()) {
            if (structureKey == null || structureKey.isBlank()) {
                continue;
            }
            StructureStamp stamp = stampOf(world, structureKey);
            if (stamp == null) {
                continue;
            }
            if (normalize(structureKey).equals(normalizedKey)
                    || normalize(stamp.vanillaSource()).equals(normalizedKey)) {
                return true;
            }
        }
        return false;
    }

    static boolean matchesResolved(ResolvedPlacement resolved, String key) {
        String normalizedKey = normalize(key);
        return normalize(resolved.structureKey()).equals(normalizedKey)
                || normalize(resolved.stamp().vanillaSource()).equals(normalizedKey);
    }

    private static String normalize(String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT);
    }

    private static PlacementIndex index(PackWorld world) {
        if (world == null) {
            return EMPTY_INDEX;
        }
        return world.state(PlacementIndex.class, IrisStructureLocator::build);
    }

    private static LocatableIndex locatableIndex(PackWorld world) {
        if (world == null) {
            return EMPTY_LOCATABLE_INDEX;
        }
        return world.state(LocatableIndex.class, IrisStructureLocator::buildLocatableIndex);
    }

    private static LocatableIndex buildLocatableIndex(PackWorld world) {
        Set<String> keys = new LinkedHashSet<>();
        Set<String> normalizedKeys = new LinkedHashSet<>();
        Set<String> editableKeys = new LinkedHashSet<>();
        Set<String> normalizedEditableKeys = new LinkedHashSet<>();
        Set<String> nativeKeys = new LinkedHashSet<>();
        Set<String> normalizedNativeKeys = new LinkedHashSet<>();
        for (StructurePlacement placement : index(world).placements) {
            if (!isSearchablePlacement(world, placement)) {
                continue;
            }
            if (placement.hasNativeStructures()) {
                for (NativeStructureSource source : placement.nativeStructures()) {
                    addLocatableKey(source.structure(), keys, normalizedKeys);
                    addLocatableKey(source.structure(), nativeKeys, normalizedNativeKeys);
                }
                continue;
            }
            for (String structureKey : placement.structures()) {
                StructureStamp stamp = stampOf(world, structureKey);
                if (stamp == null) {
                    throw new IllegalStateException(
                            "Iris structure placement references missing structure '" + structureKey + "'");
                }
                addLocatableKey(structureKey, keys, normalizedKeys);
                addLocatableKey(structureKey, editableKeys, normalizedEditableKeys);
                addLocatableKey(stamp.vanillaSource(), keys, normalizedKeys);
                addLocatableKey(stamp.vanillaSource(), editableKeys, normalizedEditableKeys);
            }
        }
        return new LocatableIndex(
                Collections.unmodifiableSet(keys),
                Collections.unmodifiableSet(normalizedKeys),
                Collections.unmodifiableSet(editableKeys),
                Collections.unmodifiableSet(normalizedEditableKeys),
                Collections.unmodifiableSet(nativeKeys),
                Collections.unmodifiableSet(normalizedNativeKeys));
    }

    private static void addLocatableKey(String key, Set<String> keys, Set<String> normalizedKeys) {
        if (key == null || key.isBlank()) {
            return;
        }
        String trimmedKey = key.trim();
        keys.add(trimmedKey);
        normalizedKeys.add(normalize(trimmedKey));
    }

    private static PlacementIndex build(PackWorld world) {
        Set<String> loadKeys = new LinkedHashSet<>();
        Set<String> normalizedLoadKeys = new LinkedHashSet<>();
        Set<String> vanillaAliases = new LinkedHashSet<>();
        Set<String> suppressedVanillaSources = new LinkedHashSet<>();
        List<StructurePlacement> placements = new ArrayList<>();
        for (var declared : world.settings().declared(StructurePlacementSettings.class)) {
            collect(declared.value().placements(), world, loadKeys, normalizedLoadKeys, vanillaAliases,
                    suppressedVanillaSources, placements, declared.level() == Level.DIMENSION);
        }
        return new PlacementIndex(
                Collections.unmodifiableSet(loadKeys),
                Collections.unmodifiableSet(normalizedLoadKeys),
                Collections.unmodifiableSet(vanillaAliases),
                Collections.unmodifiableSet(suppressedVanillaSources),
                nativeKeys(placements),
                List.copyOf(placements));
    }

    private static Set<String> nativeKeys(List<StructurePlacement> placements) {
        Set<String> nativeKeys = new LinkedHashSet<>();
        for (StructurePlacement placement : placements) {
            if (!placement.hasNativeStructures()) {
                continue;
            }
            for (NativeStructureSource source : placement.nativeStructures()) {
                if (source != null) {
                    nativeKeys.add(normalize(source.structure()));
                }
            }
        }
        return Collections.unmodifiableSet(nativeKeys);
    }

    private static void collect(List<StructurePlacement> source, PackWorld world, Set<String> loadKeys,
                                Set<String> normalizedLoadKeys, Set<String> vanillaAliases,
                                Set<String> suppressedVanillaSources,
                                List<StructurePlacement> placements, boolean allowNativeSuppression) {
        for (StructurePlacement placement : source) {
            if (placement == null) {
                throw new IllegalStateException("Iris structure placement list contains a null placement");
            }
            NativeStartPlanner.validateBackend(placement);
            boolean replacesNative = NativeStructureSuppression.REPLACE_SOURCE == placement.nativeSuppression();
            if (replacesNative && !allowNativeSuppression) {
                throw new IllegalStateException("REPLACE_SOURCE is only valid on dimension-level structure placements");
            }
            if (placement.hasNativeStructures()) {
                for (NativeStructureSource nativeStructure : placement.nativeStructures()) {
                    if (nativeStructure == null
                            || nativeStructure.structure() == null
                            || !NAMESPACED_RESOURCE_KEY.matcher(nativeStructure.structure()).matches()) {
                        throw new IllegalStateException("Native structure placement contains an invalid structure key");
                    }
                    String sourceKey = nativeStructure.structure();
                    loadKeys.add(sourceKey);
                    normalizedLoadKeys.add(normalize(sourceKey));
                    vanillaAliases.add(normalize(sourceKey));
                    if (replacesNative) {
                        suppressedVanillaSources.add(normalize(sourceKey));
                    }
                }
                placements.add(placement);
                continue;
            }
            for (String structureKey : placement.structures()) {
                if (structureKey == null || structureKey.isBlank()) {
                    throw new IllegalStateException(replacesNative
                            ? "REPLACE_SOURCE contains a blank Iris structure reference"
                            : "Iris structure placement contains a blank structure reference");
                }
                StructureStamp stamp = stampOf(world, structureKey);
                if (stamp == null) {
                    throw new IllegalStateException((replacesNative
                            ? "REPLACE_SOURCE references missing Iris structure '"
                            : "Iris structure placement references missing structure '")
                            + structureKey + "'");
                }
                loadKeys.add(structureKey);
                normalizedLoadKeys.add(normalize(structureKey));
                if (!stamp.vanillaSource().isBlank()) {
                    vanillaAliases.add(normalize(stamp.vanillaSource()));
                }
                if (replacesNative) {
                    String vanillaSource = stamp.vanillaSource();
                    if (!NAMESPACED_RESOURCE_KEY.matcher(vanillaSource).matches()) {
                        throw new IllegalStateException("REPLACE_SOURCE structure '" + structureKey
                                + "' must declare a valid namespaced vanillaSource");
                    }
                    PackResources pack = world.resources();
                    ResourceId structureId = pack.idOf(structureKey);
                    if (providerOf(world, structureId).viability(pack, structureId) != Viability.GUARANTEED) {
                        throw new IllegalStateException("REPLACE_SOURCE structure '" + structureKey
                                + "' is not runtime-viable; native generation will not be used as a fallback");
                    }
                    suppressedVanillaSources.add(normalize(vanillaSource));
                }
            }
            placements.add(placement);
        }
    }

    public record ResolvedPlacement(StructurePlacement placement, String structureKey, StructureStamp stamp,
                                    KList<AssembledPiece> pieces, RNG rng,
                                    int originX, int baseY, int originZ, boolean exactY) {
    }

    public enum LocateStatus {
        FOUND,
        NOT_FOUND,
        SEARCH_LIMIT_REACHED
    }

    public record LocateResult(LocateStatus status, int originX, int baseY, int originZ) {
        public boolean found() {
            return status == LocateStatus.FOUND;
        }
    }

    @FunctionalInterface
    public interface CandidateFilter {
        boolean accept(int chunkX, int chunkZ);
    }

    public static final class CandidateSearchLimitException extends RuntimeException {
        public CandidateSearchLimitException() {
            super(null, null, false, false);
        }
    }

    private record RandomSpreadParameters(int spacing, int separation, int salt) {
    }

    private record PlacementCatalog(List<RandomSpreadParameters> randomSpread,
                                    List<StructurePlacement> concentricRings, boolean hasDensity) {
    }

    private record LocatableIndex(Set<String> keys, Set<String> normalizedKeys,
                                  Set<String> editableKeys, Set<String> normalizedEditableKeys,
                                  Set<String> nativeKeys, Set<String> normalizedNativeKeys) {
    }

    private record ResolvedStart(int originX, int baseY, int originZ) {
    }

    private record LocatedCandidate(ResolvedStart resolved, long distanceSquared) {
    }

    private static final class CandidateBudget {
        private int checked;

        private void claim() {
            if (checked >= CANDIDATE_BUDGET) {
                throw new CandidateSearchLimitException();
            }
            checked++;
        }
    }

    private static final class PlacementIndex {
        private final Set<String> loadKeys;
        private final Set<String> normalizedLoadKeys;
        private final Set<String> vanillaAliases;
        private final Set<String> suppressedVanillaSources;
        private final Set<String> nativeKeys;
        private final List<StructurePlacement> placements;

        private PlacementIndex(Set<String> loadKeys, Set<String> normalizedLoadKeys,
                               Set<String> vanillaAliases, Set<String> suppressedVanillaSources,
                               Set<String> nativeKeys,
                               List<StructurePlacement> placements) {
            this.loadKeys = loadKeys;
            this.normalizedLoadKeys = normalizedLoadKeys;
            this.vanillaAliases = vanillaAliases;
            this.suppressedVanillaSources = suppressedVanillaSources;
            this.nativeKeys = nativeKeys;
            this.placements = placements;
        }
    }
}
