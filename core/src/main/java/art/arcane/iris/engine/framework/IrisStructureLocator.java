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
import art.arcane.iris.engine.framework.structure.NativeStartPlanner;
import art.arcane.iris.engine.framework.structure.StructureResources;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.structure.StructureGraphCatalog;
import art.arcane.iris.engine.framework.structure.StructureGraphCompilation;
import art.arcane.iris.engine.framework.structure.StructureAssemblyResult;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.builtin.structure.NativeStructureSource;
import art.arcane.iris.engine.object.value.IrisPosition;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.iris.builtin.structure.StructureAnchorMode;
import art.arcane.iris.builtin.structure.StructureCarveShape;
import art.arcane.iris.builtin.structure.StructurePlacement;
import art.arcane.iris.builtin.structure.StructureTerrain;
import art.arcane.iris.builtin.structure.StructureTerrainMode;
import art.arcane.iris.builtin.structure.NativeStructureSuppression;
import art.arcane.iris.builtin.structure.StructureDistribution;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.core.modules.IrisModuleHost;
import com.volmit.ModuleLog;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

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
    public static Set<String> placedKeys(Engine engine) {
        if (engine == null) {
            return Collections.emptySet();
        }
        return index(engine).loadKeys;
    }

    /**
     * Every key under which a structure the engine's placements place can be found: the placed keys and the platform
     * structures Iris structures stand in for ({@code vanillaSource}), the latter in lower case.
     */
    public static Set<String> placedKeysAndAliases(Engine engine) {
        if (engine == null) {
            return Collections.emptySet();
        }
        PlacementIndex placementIndex = index(engine);
        Set<String> keys = new LinkedHashSet<>(placementIndex.loadKeys);
        keys.addAll(placementIndex.vanillaAliases);
        return Collections.unmodifiableSet(keys);
    }

    /** The platform structures the engine's placements start natively ({@code nativeStructures}), in lower case. */
    public static Set<String> nativeKeys(Engine engine) {
        if (engine == null) {
            return Collections.emptySet();
        }
        return index(engine).nativeKeys;
    }

    public static boolean isPlaced(Engine engine, String key) {
        if (engine == null || key == null || key.isEmpty()) {
            return false;
        }
        PlacementIndex placementIndex = index(engine);
        String normalizedKey = normalize(key);
        return placementIndex.normalizedLoadKeys.contains(normalizedKey)
                || placementIndex.vanillaAliases.contains(normalizedKey);
    }

    public static boolean hasLocatablePlacement(Engine engine, String key) {
        if (engine == null || key == null || key.isBlank()) {
            return false;
        }
        return locatableIndex(engine).normalizedKeys().contains(normalize(key));
    }

    public static boolean hasLocatableEditablePlacement(Engine engine, String key) {
        if (engine == null || key == null || key.isBlank()) {
            return false;
        }
        return locatableIndex(engine).normalizedEditableKeys().contains(normalize(key));
    }

    public static boolean hasLocatableNativePlacement(Engine engine, String key) {
        if (engine == null || key == null || key.isBlank()) {
            return false;
        }
        return locatableIndex(engine).normalizedNativeKeys().contains(normalize(key));
    }

    public static Set<String> locatableKeys(Engine engine) {
        return locatableIndex(engine).keys();
    }

    public static Set<String> locatableEditableKeys(Engine engine) {
        return locatableIndex(engine).editableKeys();
    }

    public static Set<String> locatableNativeKeys(Engine engine) {
        return locatableIndex(engine).nativeKeys();
    }

    public static boolean hasNativePlacement(Engine engine, String key) {
        if (engine == null || key == null || key.isBlank()) {
            return false;
        }
        return index(engine).nativeKeys.contains(normalize(key));
    }

    public static boolean hasEditablePlacement(Engine engine, String key) {
        if (engine == null || key == null || key.isBlank()) {
            return false;
        }
        for (StructurePlacement placement : index(engine).placements) {
            if (placement.hasIrisStructures() && matches(placement, key, engine.getData())) {
                return true;
            }
        }
        return false;
    }

    public static boolean suppressesVanilla(Engine engine, String vanillaKey) {
        if (engine == null || vanillaKey == null || vanillaKey.isEmpty()) {
            return false;
        }
        return index(engine).suppressedVanillaSources.contains(normalize(vanillaKey));
    }

    public static boolean startsInChunk(Engine engine, String key, int cx, int cz) {
        if (!isPlaced(engine, key)) {
            return false;
        }
        return resolveInChunk(engine, key, cx, cz) != null;
    }

    public static LocateResult locate(Engine engine, String key, int fromBlockX, int fromBlockZ, int maxRadiusChunks) {
        return locate(engine, key, fromBlockX, fromBlockZ, maxRadiusChunks, (chunkX, chunkZ) -> true);
    }

    public static LocateResult locate(Engine engine, String key, int fromBlockX, int fromBlockZ,
                                      int maxRadiusChunks, CandidateFilter candidateFilter) {
        try {
            return locateFiltered(engine, key, fromBlockX, fromBlockZ, maxRadiusChunks, candidateFilter);
        } catch (CandidateSearchLimitException ignored) {
            return SEARCH_LIMIT_RESULT;
        }
    }

    public static LocateResult locateInPlacementRings(
            Engine engine, String key, int fromBlockX, int fromBlockZ,
            int maxSearchRadius, CandidateFilter candidateFilter) {
        try {
            return locateInPlacementRingsFiltered(
                    engine, key, fromBlockX, fromBlockZ, maxSearchRadius, candidateFilter);
        } catch (CandidateSearchLimitException ignored) {
            return SEARCH_LIMIT_RESULT;
        }
    }

    private static LocateResult locateInPlacementRingsFiltered(
            Engine engine, String key, int fromBlockX, int fromBlockZ,
            int maxSearchRadius, CandidateFilter candidateFilter) {
        if (!isPlaced(engine, key)) {
            return NOT_FOUND_RESULT;
        }
        CandidateFilter activeFilter = Objects.requireNonNull(
                candidateFilter, "Structure locate filter must not be null");
        int maximumRing = Math.max(0, Math.min(maxSearchRadius, 2048));
        int centerChunkX = fromBlockX >> 4;
        int centerChunkZ = fromBlockZ >> 4;
        PlacementCatalog catalog = collectPlacementCatalog(engine, key);
        if (catalog.randomSpread().isEmpty()
                && catalog.concentricRings().isEmpty() && !catalog.hasDensity()) {
            return NOT_FOUND_RESULT;
        }
        SeedManager seedManager = engine.getSeedManager();
        if (seedManager == null) {
            throw new IllegalStateException(
                    "Iris structure locate requires a bound seed manager for '" + key + "'");
        }
        long seed = seedManager.getMantle();
        CandidateBudget candidateBudget = new CandidateBudget();
        LocatedCandidate best = nearestConcentricCandidate(
                engine, key, fromBlockX, fromBlockZ, seed, catalog, activeFilter, candidateBudget);

        for (int ring = 0; ring <= maximumRing; ring++) {
            boolean foundInRing = false;
            for (RandomSpreadParameters parameters : catalog.randomSpread()) {
                LocatedCandidate candidate = firstRandomSpreadCandidateInRing(
                        engine, key, fromBlockX, fromBlockZ,
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
                        engine, key, fromBlockX, fromBlockZ,
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
            Engine engine, String key, int fromBlockX, int fromBlockZ,
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
                ResolvedStart resolved = resolveInChunk(engine, key, candidate[0], candidate[1]);
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
            Engine engine, String key, int fromBlockX, int fromBlockZ,
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
                ResolvedStart resolved = resolveInChunk(engine, key, candidate[0], candidate[1]);
                if (resolved != null && candidateFilter.accept(candidate[0], candidate[1])) {
                    return new LocatedCandidate(
                            resolved, blockDistanceSquared(resolved, fromBlockX, fromBlockZ));
                }
            }
        }
        return null;
    }

    private static LocatedCandidate firstDensityCandidateInRing(
            Engine engine, String key, int fromBlockX, int fromBlockZ,
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
                ResolvedStart resolved = resolveInChunk(engine, key, chunkX, chunkZ);
                if (resolved != null && candidateFilter.accept(chunkX, chunkZ)) {
                    return new LocatedCandidate(
                            resolved, blockDistanceSquared(resolved, fromBlockX, fromBlockZ));
                }
            }
        }
        return null;
    }

    private static LocateResult locateFiltered(Engine engine, String key, int fromBlockX, int fromBlockZ,
                                               int maxRadiusChunks, CandidateFilter candidateFilter) {
        if (!isPlaced(engine, key)) {
            return NOT_FOUND_RESULT;
        }
        CandidateFilter activeFilter = Objects.requireNonNull(candidateFilter, "Structure locate filter must not be null");
        int max = Math.max(0, Math.min(maxRadiusChunks, 2048));
        int pcx = fromBlockX >> 4;
        int pcz = fromBlockZ >> 4;
        LocatedCandidate best = null;
        PlacementCatalog catalog = collectPlacementCatalog(engine, key);
        if (catalog.randomSpread().isEmpty() && catalog.concentricRings().isEmpty() && !catalog.hasDensity()) {
            return NOT_FOUND_RESULT;
        }
        SeedManager seedManager = engine.getSeedManager();
        if (seedManager == null) {
            throw new IllegalStateException("Iris structure locate requires a bound seed manager for '" + key + "'");
        }
        long seed = seedManager.getMantle();
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
                        ResolvedStart resolved = resolveInChunk(engine, key, cx, cz);
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
                ResolvedStart resolved = resolveInChunk(engine, key, candidate[0], candidate[1]);
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
                        ResolvedStart resolved = resolveInChunk(engine, key, cx, cz);
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

    public static ResolvedPlacement resolvePlacement(Engine engine, StructurePlacement placement, int cx, int cz) {
        if (engine == null || engine.getData() == null || engine.getSeedManager() == null) {
            throw new IllegalStateException("Iris structure placement requires a fully bound engine");
        }
        NativeStartPlanner.validateBackend(placement);
        if (!placement.hasIrisStructures()) {
            throw new IllegalStateException("Iris structure placement is missing its structure list");
        }
        long seed = engine.getSeedManager().getMantle();
        if (!StructurePlacementGrid.startsInChunk(placement, cx, cz, seed)) {
            return null;
        }

        RNG rng = StructurePlacementGrid.placementRng(placement, cx, cz, seed);
        int originX = (cx << 4) + rng.nextInt(16);
        int originZ = (cz << 4) + rng.nextInt(16);
        PlacementAnchor anchor = resolveAnchor(engine, placement, cx, cz, originX, originZ, rng);
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
        IrisStructure structure = StructureResources.structure(engine.getData().getPackResources(), selectedKey);
        if (structure == null) {
            throw new IllegalStateException("Iris structure placement references missing structure '"
                    + selectedKey + "'");
        }
        StructureGraphCompilation compilation = StructureGraphCatalog.compile(engine.getData(), structure, IrisModuleHost.hostLog());
        if (!compilation.isAssemblyViable()) {
            throw new IllegalStateException("Iris structure '" + selectedKey
                    + "' has no runtime-viable assembly graph");
        }

        StructureAssembler assembler = StructureAssembler.forData(
                engine.getData(), structure, new IrisPosition(originX, baseY, originZ), IrisModuleHost.hostLog());
        StructureAssemblyResult assembly = assembler.assemble(rng);
        if (!requirePlacementOutput(placement, selectedKey, cx, cz,
                assembly.hasOutput(), "runtime assembly produced " + assembly.status() + ": " + assembly.detail())) {
            return null;
        }
        KList<PlacedStructurePiece> pieces = new KList<>(assembly.pieces());
        pieces = alignCavePieces(pieces, placement, baseY);
        pieces = alignSurfacePieces(pieces, placement, structure, baseY);
        if (!requirePlacementOutput(placement, selectedKey, cx, cz,
                pieces != null && !pieces.isEmpty(), "surface alignment produced no pieces")) {
            return null;
        }
        boolean exactY = hasExactY(placement, structure, pieces);

        int worldMin = engine.getMinHeight() + 1;
        int worldMax = engine.getMinHeight() + engine.getHeight() - 1;
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
                    engine, pieces, placement, baseY, worldMin, worldMax);
            if (!requirePlacementOutput(placement, selectedKey, cx, cz, burialShift != null,
                    "assembled pieces and carving envelope cannot remain fully buried beneath terrain")) {
                return null;
            }
            if (burialShift != 0) {
                pieces = shiftPieces(pieces, burialShift);
                baseY += burialShift;
            }
        }

        long configuredRadius = (long) Math.max(1, structure.getMaxSizeChunks()) * 16L;
        int structureRadius = (int) Math.min(Integer.MAX_VALUE, configuredRadius);
        boolean withinBounds = fitsHorizontalBounds(pieces, originX, originZ, structureRadius)
                && (!exactY || fitsVerticalBounds(pieces, worldMin, worldMax));
        if (!requirePlacementOutput(placement, selectedKey, cx, cz, withinBounds,
                "assembled pieces exceed the configured structure or world bounds")) {
            return null;
        }
        return new ResolvedPlacement(placement, selectedKey, structure, pieces, rng, originX, baseY, originZ, exactY);
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

    static boolean fitsWorldBounds(KList<PlacedStructurePiece> pieces, int worldMin, int worldMax,
                                  int originX, int originZ, int structureRadius) {
        int[] bounds = computeBounds(pieces);
        if (bounds == null) {
            return false;
        }
        return fitsVerticalBounds(bounds, worldMin, worldMax)
                && fitsHorizontalBounds(bounds, originX, originZ, structureRadius);
    }

    private static ResolvedStart resolveInChunk(Engine engine, String key, int cx, int cz) {
        IrisData data = engine.getData();
        String normalizedKey = normalize(key);
        if (hasNativePlacement(engine, key)) {
            for (NativeStartPlanner.Plan plan : NativeStartPlanner.plansAt(engine, cx, cz)) {
                if (normalize(plan.source().structure()).equals(normalizedKey)) {
                    return new ResolvedStart(cx << 4, plan.baseY(), cz << 4);
                }
            }
        }
        KList<StructurePlacement> placements = StructurePlacementScope.placementsAt(engine, cx, cz);
        for (StructurePlacement placement : placements) {
            if (!matches(placement, key, data)) {
                continue;
            }
            if (placement.hasNativeStructures()) {
                continue;
            }
            ResolvedPlacement resolved = resolvePlacement(engine, placement, cx, cz);
            if (resolved != null && matchesResolved(resolved, key)) {
                return new ResolvedStart(
                        resolved.originX(), resolved.baseY(), resolved.originZ());
            }
        }
        return null;
    }

    private static PlacementAnchor resolveAnchor(
            Engine engine,
            StructurePlacement placement,
            int chunkX,
            int chunkZ,
            int originX,
            int originZ,
            RNG rng
    ) {
        if (placement.resolvedAnchor().isCave()) {
            StructureCaveAnchorResolver.Anchor caveAnchor = StructureCaveAnchorResolver.resolve(
                    engine, placement, chunkX, chunkZ, rng.nextParallelRNG(CAVE_ANCHOR_DOMAIN));
            return caveAnchor == null ? null : new PlacementAnchor(caveAnchor.x(), caveAnchor.y(), caveAnchor.z());
        }
        Integer baseY = resolveBaseY(engine, placement, originX, originZ, rng);
        return baseY == null ? null : new PlacementAnchor(originX, baseY, originZ);
    }

    private static Integer resolveBaseY(Engine engine, StructurePlacement placement, int originX, int originZ, RNG rng) {
        int worldMin = engine.getMinHeight() + 1;
        int worldMax = engine.getMinHeight() + engine.getHeight() - 1;
        if (worldMin > worldMax) {
            return null;
        }
        if (!placement.underwater()
                && NativeStartPlanner.isSubmerged(engine, originX, originZ)) {
            return null;
        }
        if (placement.resolvedAnchor() == StructureAnchorMode.SURFACE) {
            int surfaceY = engine.getHeight(originX, originZ, true) + engine.getMinHeight();
            return surfaceY < placement.minHeight() || surfaceY > placement.maxHeight() ? null : surfaceY;
        }
        int bandMin = Math.max(worldMin, Math.min(placement.minHeight(), placement.maxHeight()));
        int bandMax = Math.min(worldMax, Math.max(placement.minHeight(), placement.maxHeight()));
        if (bandMin > bandMax) {
            return null;
        }
        return randomInclusive(rng, bandMin, bandMax);
    }

    static Integer resolveVerticalShift(KList<PlacedStructurePiece> pieces, StructurePlacement placement,
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

    static Integer resolveUndergroundBurialShift(Engine engine, KList<PlacedStructurePiece> pieces,
                                                  StructurePlacement placement, int baseY,
                                                  int worldMin, int worldMax) {
        int[] bounds = computeBounds(pieces);
        if (engine == null || bounds == null || placement == null || !placement.isAnchoredUnderground()
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

        Long2IntOpenHashMap surfaceHeights = new Long2IntOpenHashMap();
        surfaceHeights.defaultReturnValue(Integer.MIN_VALUE);
        if (bore || forceCarve && carveShape == StructureCarveShape.BOX) {
            maximumShift = resolveBurialEnvelopeShift(
                    engine,
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
            for (PlacedStructurePiece piece : pieces) {
                maximumShift = resolveBurialEnvelopeShift(
                        engine,
                        piece.getMinX() - sideExtension,
                        piece.getMaxX() + sideExtension,
                        piece.getMinZ() - sideExtension,
                        piece.getMaxZ() + sideExtension,
                        piece.getMaxY() + topExtension,
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

    private static int resolveBurialEnvelopeShift(Engine engine, int minX, int maxX, int minZ, int maxZ,
                                                   int envelopeTopY, int maximumShift,
                                                   Long2IntOpenHashMap surfaceHeights) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                long columnKey = ((long) x << 32) ^ (z & 0xffffffffL);
                int surfaceY = surfaceHeights.get(columnKey);
                if (surfaceY == Integer.MIN_VALUE) {
                    if (surfaceHeights.size() >= MAX_BURIAL_COLUMNS) {
                        return Integer.MIN_VALUE;
                    }
                    surfaceY = engine.getHeight(x, z, true) + engine.getMinHeight();
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

    static KList<PlacedStructurePiece> alignCavePieces(
            KList<PlacedStructurePiece> pieces,
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

    private static KList<PlacedStructurePiece> alignSurfacePieces(KList<PlacedStructurePiece> pieces,
                                                                  StructurePlacement placement,
                                                                  IrisStructure structure, int baseY) {
        if (placement.isAnchoredUnderground() || pieces.size() <= 1
                || structure.getPlaceMode().placeMode().has(PlaceTrait.FREE_HEIGHT)) {
            return pieces;
        }
        int[] bounds = computeBounds(pieces);
        return bounds == null ? pieces : shiftPieces(pieces, baseY - bounds[1]);
    }

    static boolean hasExactY(StructurePlacement placement, IrisStructure structure,
                             KList<PlacedStructurePiece> pieces) {
        return placement.isAnchoredUnderground() || pieces.size() > 1
                || structure.getPlaceMode().placeMode().has(PlaceTrait.FREE_HEIGHT);
    }

    private static boolean fitsVerticalBounds(KList<PlacedStructurePiece> pieces, int worldMin, int worldMax) {
        int[] bounds = computeBounds(pieces);
        return bounds != null && fitsVerticalBounds(bounds, worldMin, worldMax);
    }

    private static boolean fitsVerticalBounds(int[] bounds, int worldMin, int worldMax) {
        return bounds[1] >= worldMin && bounds[4] <= worldMax;
    }

    private static boolean fitsHorizontalBounds(KList<PlacedStructurePiece> pieces, int originX, int originZ,
                                                int structureRadius) {
        int[] bounds = computeBounds(pieces);
        return bounds != null && fitsHorizontalBounds(bounds, originX, originZ, structureRadius);
    }

    private static boolean fitsHorizontalBounds(int[] bounds, int originX, int originZ, int structureRadius) {
        return bounds[0] >= originX - structureRadius && bounds[3] <= originX + structureRadius
                && bounds[2] >= originZ - structureRadius && bounds[5] <= originZ + structureRadius;
    }

    private static KList<PlacedStructurePiece> shiftPieces(KList<PlacedStructurePiece> pieces, int shiftY) {
        KList<PlacedStructurePiece> shifted = new KList<>();
        for (PlacedStructurePiece piece : pieces) {
            shifted.add(new PlacedStructurePiece(
                    piece.getPiece(), piece.getObject(), piece.getX(), piece.getY() + shiftY, piece.getZ(), piece.getRotation(),
                    piece.getMinX(), piece.getMinY() + shiftY, piece.getMinZ(),
                    piece.getMaxX(), piece.getMaxY() + shiftY, piece.getMaxZ()));
        }
        return shifted;
    }

    private static int[] computeBounds(KList<PlacedStructurePiece> pieces) {
        if (pieces == null || pieces.isEmpty()) {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (PlacedStructurePiece piece : pieces) {
            minX = Math.min(minX, piece.getMinX());
            minY = Math.min(minY, piece.getMinY());
            minZ = Math.min(minZ, piece.getMinZ());
            maxX = Math.max(maxX, piece.getMaxX());
            maxY = Math.max(maxY, piece.getMaxY());
            maxZ = Math.max(maxZ, piece.getMaxZ());
        }
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    private static int randomInclusive(RNG rng, int minimum, int maximum) {
        if (minimum == maximum) {
            return minimum;
        }
        return minimum + rng.nextInt((maximum - minimum) + 1);
    }

    private static PlacementCatalog collectPlacementCatalog(Engine engine, String key) {
        Set<RandomSpreadParameters> randomSpread = new LinkedHashSet<>();
        List<StructurePlacement> concentricRings = new ArrayList<>();
        boolean hasDensity = false;
        for (StructurePlacement placement : index(engine).placements) {
            if (!isSearchablePlacement(engine, placement)
                    || !matches(placement, key, engine.getData())) {
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

    static boolean isSearchableDensityPlacement(Engine engine, StructurePlacement placement) {
        return placement != null
                && placement.distribution() == StructureDistribution.DENSITY
                && isSearchablePlacement(engine, placement);
    }

    static boolean isSearchablePlacement(Engine engine, StructurePlacement placement) {
        if (engine == null || placement == null || engine.getHeight() <= 0) {
            return false;
        }
        if (placement.distribution() == StructureDistribution.DENSITY
                && !(placement.density() > 0.0)) {
            return false;
        }
        long worldMin = (long) engine.getMinHeight() + (placement.isAnchoredUnderground() ? 1L : 0L);
        long worldMax = (long) engine.getMinHeight() + engine.getHeight()
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

    private static boolean matches(StructurePlacement placement, String key, IrisData data) {
        if (placement == null || key == null || data == null) {
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
            IrisStructure structure = StructureResources.structure(data.getPackResources(), structureKey);
            if (structure == null) {
                continue;
            }
            if (normalize(structureKey).equals(normalizedKey)
                    || normalize(structure.getLoadKey()).equals(normalizedKey)
                    || normalize(structure.getVanillaSource()).equals(normalizedKey)) {
                return true;
            }
        }
        return false;
    }

    static boolean matchesResolved(ResolvedPlacement resolved, String key) {
        String normalizedKey = normalize(key);
        return normalize(resolved.structureKey()).equals(normalizedKey)
                || normalize(resolved.structure().getLoadKey()).equals(normalizedKey)
                || normalize(resolved.structure().getVanillaSource()).equals(normalizedKey);
    }

    private static String normalize(String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT);
    }

    private static PlacementIndex index(Engine engine) {
        if (engine == null) {
            return EMPTY_INDEX;
        }
        if (engine.getData() == null || engine.getDimension() == null) {
            throw new IllegalStateException("Iris structure index requires a fully bound engine and dimension");
        }
        return engine.getGeneration().state(PlacementIndex.class, IrisStructureLocator::build);
    }

    private static LocatableIndex locatableIndex(Engine engine) {
        if (engine == null) {
            return EMPTY_LOCATABLE_INDEX;
        }
        return engine.getGeneration().state(LocatableIndex.class, IrisStructureLocator::buildLocatableIndex);
    }

    private static LocatableIndex buildLocatableIndex(Engine engine) {
        IrisData data = engine.getData();
        Set<String> keys = new LinkedHashSet<>();
        Set<String> normalizedKeys = new LinkedHashSet<>();
        Set<String> editableKeys = new LinkedHashSet<>();
        Set<String> normalizedEditableKeys = new LinkedHashSet<>();
        Set<String> nativeKeys = new LinkedHashSet<>();
        Set<String> normalizedNativeKeys = new LinkedHashSet<>();
        for (StructurePlacement placement : index(engine).placements) {
            if (!isSearchablePlacement(engine, placement)) {
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
                IrisStructure structure = StructureResources.structure(data.getPackResources(), structureKey);
                if (structure == null) {
                    throw new IllegalStateException(
                            "Iris structure placement references missing structure '" + structureKey + "'");
                }
                addLocatableKey(structureKey, keys, normalizedKeys);
                addLocatableKey(structureKey, editableKeys, normalizedEditableKeys);
                addLocatableKey(structure.getLoadKey(), keys, normalizedKeys);
                addLocatableKey(structure.getLoadKey(), editableKeys, normalizedEditableKeys);
                addLocatableKey(structure.getVanillaSource(), keys, normalizedKeys);
                addLocatableKey(structure.getVanillaSource(), editableKeys, normalizedEditableKeys);
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

    private static PlacementIndex build(Engine engine) {
        IrisData data = engine.getData();
        ModuleLog log = IrisModuleHost.hostLog();
        Set<String> loadKeys = new LinkedHashSet<>();
        Set<String> normalizedLoadKeys = new LinkedHashSet<>();
        Set<String> vanillaAliases = new LinkedHashSet<>();
        Set<String> suppressedVanillaSources = new LinkedHashSet<>();
        List<StructurePlacement> placements = new ArrayList<>();
        collect(StructurePlacementScope.placementsOf(engine.getDimension()), data, log, loadKeys, normalizedLoadKeys, vanillaAliases,
                suppressedVanillaSources, placements, true);
        KList<IrisRegion> regions = engine.getDimension().getAllRegions(engine);
        if (regions != null) {
            for (IrisRegion region : regions) {
                collect(StructurePlacementScope.placementsOf(region), data, log,
                        loadKeys, normalizedLoadKeys, vanillaAliases,
                        suppressedVanillaSources, placements, false);
            }
        }
        KList<IrisBiome> biomes = engine.getDimension().getReachableBiomes(engine);
        if (biomes != null) {
            for (IrisBiome biome : biomes) {
                collect(StructurePlacementScope.placementsOf(biome), data, log,
                        loadKeys, normalizedLoadKeys, vanillaAliases,
                        suppressedVanillaSources, placements, false);
            }
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

    private static void collect(List<StructurePlacement> source, IrisData data, ModuleLog log, Set<String> loadKeys,
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
                IrisStructure structure = StructureResources.structure(data.getPackResources(), structureKey);
                if (structure == null) {
                    throw new IllegalStateException((replacesNative
                            ? "REPLACE_SOURCE references missing Iris structure '"
                            : "Iris structure placement references missing structure '")
                            + structureKey + "'");
                }
                loadKeys.add(structureKey);
                normalizedLoadKeys.add(normalize(structureKey));
                if (structure.getLoadKey() != null && !structure.getLoadKey().isBlank()) {
                    loadKeys.add(structure.getLoadKey());
                    normalizedLoadKeys.add(normalize(structure.getLoadKey()));
                }
                if (structure.getVanillaSource() != null && !structure.getVanillaSource().isBlank()) {
                    vanillaAliases.add(normalize(structure.getVanillaSource()));
                }
                if (replacesNative) {
                    String vanillaSource = structure.getVanillaSource();
                    if (vanillaSource == null || !NAMESPACED_RESOURCE_KEY.matcher(vanillaSource).matches()) {
                        throw new IllegalStateException("REPLACE_SOURCE structure '" + structureKey
                                + "' must declare a valid namespaced vanillaSource");
                    }
                    if (!StructureGraphCatalog.guaranteesRuntimeOutput(data, structure, log)) {
                        throw new IllegalStateException("REPLACE_SOURCE structure '" + structureKey
                                + "' is not runtime-viable; native generation will not be used as a fallback");
                    }
                    suppressedVanillaSources.add(normalize(vanillaSource));
                }
            }
            placements.add(placement);
        }
    }

    public record ResolvedPlacement(StructurePlacement placement, String structureKey, IrisStructure structure,
                                    KList<PlacedStructurePiece> pieces, RNG rng,
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
