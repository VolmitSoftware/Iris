package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.cave.CavePosition;
import art.arcane.iris.engine.hydrology.cave.CaveVoxelView;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveCandidate;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveContainmentPlanner;
import art.arcane.iris.engine.hydrology.cave.HydrologyCavePlan;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class HydrologyCrossTileResolver {
    private final HydrologyPlanner planner;

    HydrologyCrossTileResolver(HydrologyPlanner planner) {
        this.planner = planner;
    }

    HydrologyOwnerDraft compileOwnerDraft(
            HydrologyTileKey key,
            CrossTileDraftAdmission crossTileAdmission,
            boolean retainFootprintCompiler
    ) {
        HydrologyPlanner.PlanningSamples previous = planner.planningSamples.get();
        if (previous == null) {
            planner.planningSamples.set(new HydrologyPlanner.PlanningSamples());
        }
        DraftProfile previousProfile = planner.draftProfiles.get();
        DraftProfile profile = new DraftProfile();
        planner.draftProfiles.set(profile);
        long started = System.nanoTime();
        try {
            HydrologyOwnerDraft draft = compileScopedOwnerDraft(
                    key,
                    crossTileAdmission,
                    retainFootprintCompiler
            );
            profile.log(key, ownerColorRank(key), System.nanoTime() - started);
            return draft;
        } finally {
            if (previous == null) {
                planner.planningSamples.remove();
            }
            if (previousProfile == null) {
                planner.draftProfiles.remove();
            } else {
                planner.draftProfiles.set(previousProfile);
            }
        }
    }

    HydrologyOwnerDraft compileScopedOwnerDraft(
            HydrologyTileKey key,
            CrossTileDraftAdmission crossTileAdmission,
            boolean retainFootprintCompiler
    ) {
        Objects.requireNonNull(key, "key");
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();
        HydrologySampledGrid grid = null;
        HydrologyRoutingPlan surfaceRouting = null;
        HydrologyRoutingPlan undergroundRouting = null;
        SourceSelection surfaceSelection = SourceSelection.empty(true);
        SourceSelection undergroundSelection = SourceSelection.empty(false);
        DraftProfile profile = planner.currentDraftProfile();
        long phaseStarted = System.nanoTime();
        if (planner.sourcePlanner.hasRoutedSourceSearch()) {
            SourceRoutingContext primaryContext = planner.sourceRoutingContext(key);
            phaseStarted = profile.record(DraftPhase.CONTEXT, phaseStarted);
            grid = primaryContext.grid();
            surfaceRouting = primaryContext.surfaceRouting();
            undergroundRouting = primaryContext.undergroundRouting();
            diagnostics.addAll(primaryContext.diagnostics());
            HashMap<HydrologyTileKey, SourceRoutingContext> sourceRoutingContexts = new HashMap<>();
            sourceRoutingContexts.put(key, primaryContext);
            surfaceSelection = planner.sourcePlanner.selectSources(
                    key,
                    grid,
                    surfaceRouting,
                    true,
                    true,
                    diagnostics,
                    sourceRoutingContexts
            );
            undergroundSelection = planner.sourcePlanner.selectSources(
                    key,
                    grid,
                    undergroundRouting,
                    false,
                    true,
                    diagnostics,
                    sourceRoutingContexts
            );
            phaseStarted = profile.record(DraftPhase.SELECT, phaseStarted);
            if (crossTileAdmission != null && (!surfaceSelection.selectedCandidateIndices.isEmpty()
                    || !undergroundSelection.selectedCandidateIndices.isEmpty())) {
                crossTileAdmission.prepare();
            }
        }
        HydrologyFootprintCompiler footprintCompiler = new HydrologyFootprintCompiler(
                planner.settings,
                new HydrologyFootprintCompiler.Sampling(
                        planner.sampler,
                        planner.geometrySampler,
                        planner.naturalSampler
                )
        );
        HashMap<Long, List<HydrologyPoint>> refinedEdges = new HashMap<>();
        HashMap<SourceCompilationKey, SourceCompilation> sourceCompilations = new HashMap<>();
        settleSelection(
                key,
                grid,
                surfaceRouting,
                undergroundRouting,
                surfaceSelection,
                true,
                footprintCompiler,
                refinedEdges,
                sourceCompilations,
                diagnostics
        );
        settleSelection(
                key,
                grid,
                surfaceRouting,
                undergroundRouting,
                undergroundSelection,
                false,
                footprintCompiler,
                refinedEdges,
                sourceCompilations,
                diagnostics
        );
        phaseStarted = profile.record(DraftPhase.SETTLE, phaseStarted);
        PublicationAttempt publication = null;
        List<OutletCandidate> fallbackOutlets = grid == null || surfaceRouting == null
                ? List.of()
                : planner.outletPlanner.resolveSurfaceFallbackOutlets(grid);
        int fallbackOutletIndex = 0;
        while (true) {
            int maximumPublicationPasses = Math.addExact(
                    1,
                    Math.addExact(
                            surfaceSelection.candidateCount(),
                            undergroundSelection.candidateCount()
                    )
            );
            for (int publicationPass = 0; publicationPass < maximumPublicationPasses; publicationPass++) {
                publication = compilePublication(
                        key,
                        grid,
                        surfaceRouting,
                        undergroundRouting,
                        surfaceSelection,
                        undergroundSelection,
                        footprintCompiler,
                        refinedEdges,
                        sourceCompilations,
                        true
                );
                diagnostics.addAll(publication.diagnostics());
                if (crossTileAdmission != null) {
                    CrossTilePublicationAdmission admitted = crossTileAdmission.admit(publication.result());
                    publication = new PublicationAttempt(admitted.result(), publication.diagnostics());
                    diagnostics.addAll(admitted.diagnostics());
                }
                if (grid == null) {
                    break;
                }
                boolean surfaceChanged = surfaceSelection.advanceAfterPublication(
                        publication.result().courses(),
                        grid
                );
                boolean undergroundChanged = undergroundSelection.advanceAfterPublication(
                        publication.result().courses(),
                        grid
                );
                if (!surfaceChanged && !undergroundChanged) {
                    break;
                }
                if (surfaceChanged) {
                    settleSelection(
                            key,
                            grid,
                            surfaceRouting,
                            undergroundRouting,
                            surfaceSelection,
                            true,
                            footprintCompiler,
                            refinedEdges,
                            sourceCompilations,
                            diagnostics
                    );
                }
                if (undergroundChanged) {
                    settleSelection(
                            key,
                            grid,
                            surfaceRouting,
                            undergroundRouting,
                            undergroundSelection,
                            false,
                            footprintCompiler,
                            refinedEdges,
                            sourceCompilations,
                            diagnostics
                    );
                }
                if (publicationPass + 1 >= maximumPublicationPasses) {
                    throw new IllegalStateException("Hydrology source publication exceeded its candidate bound.");
                }
            }
            if (grid == null || !surfaceSelection.needsSurfaceFallback()) {
                break;
            }
            boolean fallbackSelected = false;
            while (fallbackOutletIndex < fallbackOutlets.size()) {
                OutletCandidate fallbackOutlet = fallbackOutlets.get(fallbackOutletIndex++);
                HydrologyRoutingPlan fallbackRouting = planner.sourcePlanner.requireOrganicSurface(planner.sourcePlanner.buildRouting(grid, List.of(fallbackOutlet), true));
                HashMap<HydrologyTileKey, SourceRoutingContext> fallbackContexts = new HashMap<>();
                fallbackContexts.put(key, new SourceRoutingContext(
                        grid,
                        fallbackRouting,
                        undergroundRouting,
                        List.of()
                ));
                SourceSelection fallbackSelection = planner.sourcePlanner.selectSources(
                        key,
                        grid,
                        fallbackRouting,
                        true,
                        false,
                        diagnostics,
                        fallbackContexts
                );
                settleSelection(
                        key,
                        grid,
                        fallbackRouting,
                        undergroundRouting,
                        fallbackSelection,
                        true,
                        footprintCompiler,
                        refinedEdges,
                        sourceCompilations,
                        diagnostics
                );
                if (!fallbackSelection.hasAcceptedSelection()) {
                    continue;
                }
                surfaceRouting = fallbackRouting;
                surfaceSelection = fallbackSelection;
                fallbackSelected = true;
                break;
            }
            if (!fallbackSelected) {
                break;
            }
        }
        profile.record(DraftPhase.PUBLISH, phaseStarted);
        if (publication == null) {
            throw new IllegalStateException("Hydrology publication did not execute.");
        }
        if (grid != null) {
            surfaceSelection.addFinalAdmissionDiagnostics(grid, diagnostics);
            undergroundSelection.addFinalAdmissionDiagnostics(grid, diagnostics);
        }
        HydrologyCaveCourseFilter.Result result = publication.result();
        List<HydrologyDiagnosticCandidate> uniqueDiagnostics = planner.sourcePlanner.uniqueDiagnostics(diagnostics);
        HydrologyOwnerDraft draft = new HydrologyOwnerDraft(
                key,
                result,
                uniqueDiagnostics,
                retainFootprintCompiler ? footprintCompiler : null
        );
        validateOwnerDraftReach(draft);
        return draft;
    }

    HydrologyTile materializeAcceptedTile(CrossTileResolution resolution) {
        HydrologyCaveCourseFilter.Result result = resolution.draft().result();
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>(resolution.draft().diagnostics());
        for (CrossTileRejectedCourse rejection : resolution.observedRejections()) {
            diagnostics.add(rejection.course().type() == RiverCourseType.SURFACE
                    ? surfaceConflictDiagnostic(rejection.course(), rejection.winnerSourceId())
                    : HydrologyCaveCourseFilter.overlapDiagnostic(
                    rejection.course(),
                    rejection.winnerSourceId()
            ));
        }
        HydrologyFootprintCompiler footprintCompiler = resolution.draft().footprintCompiler();
        if (footprintCompiler == null) {
            footprintCompiler = new HydrologyFootprintCompiler(
                    planner.settings,
                    new HydrologyFootprintCompiler.Sampling(
                            planner.sampler,
                            planner.geometrySampler,
                            planner.naturalSampler
                    )
            );
        }
        MaterializedHydrology materialized = materializeFinalHydrology(
                result,
                diagnostics,
                footprintCompiler,
                !resolution.observedRejections().isEmpty()
        );
        result = materialized.result();
        RiverFootprint footprint = materialized.footprint();
        if (footprintCompiler.fullMaterializationCount() < 1) {
            throw new IllegalStateException("Hydrology publication did not materialize its full footprint.");
        }
        return new HydrologyTile(
                resolution.draft().key(),
                planner.worldSeed,
                planner.settings.fingerprint(),
                planner.settings.routing().tileSize(),
                result.nodes(),
                result.edges(),
                result.outlets(),
                result.courses(),
                result.cavePlans(),
                planner.sourcePlanner.uniqueDiagnostics(diagnostics),
                footprint
        );
    }

    MaterializedHydrology materializeFinalHydrology(
            HydrologyCaveCourseFilter.Result initial,
            List<HydrologyDiagnosticCandidate> diagnostics,
            HydrologyFootprintCompiler footprintCompiler,
            boolean revalidationRequired
    ) {
        HydrologyCaveCourseFilter.Result current = initial;
        int maximumPasses = Math.addExact(initial.courses().size(), 1);
        for (int pass = 0; pass < maximumPasses; pass++) {
            RiverFootprint footprint = footprintCompiler.compile(current.courses());
            if (current.cavePlans().isEmpty()) {
                return new MaterializedHydrology(current, footprint);
            }
            HydrologyFootprintCompiler.ValidationRaster compactValidation = footprintCompiler.compileValidation(
                    current.courses()
            );
            if (!revalidationRequired
                    && compactSurfaceMatchesMaterializedPlans(current, compactValidation, footprint)) {
                return new MaterializedHydrology(current, footprint);
            }
            HydrologyFootprintCompiler.ValidationRaster validation = compactValidation.withMaterializedSurface(
                    footprint
            );
            HydrologyObservedPlannedSurface plannedSurface = new HydrologyObservedPlannedSurface(
                    validation.plannedSurface()
            );
            CaveVoxelView caveView = Objects.requireNonNull(
                    planner.caveViewFactory.create(plannedSurface),
                    "Hydrology cave view factory returned null"
            );
            HydrologyCaveCourseFilter.Result revalidated = new HydrologyCaveCourseFilter(
                    caveView,
                    new HydrologyCaveCourseFilter.Options(
                            planner.settings.underground().connectToExistingCaves(),
                            planner.settings.outlets().coastalGrotto().maximumVolume(),
                            planner.settings.outlets().inlandGrotto().maximumVolume()
                    ),
                    null,
                    null,
                    plannedSurface
            ).filter(
                    current.nodes(),
                    current.edges(),
                    current.outlets(),
                    current.courses(),
                    validation,
                    diagnostics
            );
            if (revalidated.courses().equals(current.courses())) {
                return new MaterializedHydrology(revalidated, footprint);
            }
            current = revalidated;
            revalidationRequired = true;
        }
        throw new IllegalStateException("Final hydrology cave publication exceeded its course bound.");
    }

    boolean compactSurfaceMatchesMaterializedPlans(
            HydrologyCaveCourseFilter.Result result,
            HydrologyFootprintCompiler.ValidationRaster validation,
            RiverFootprint footprint
    ) {
        HashSet<Long> visitedColumns = new HashSet<>();
        for (HydrologyCavePlan plan : result.cavePlans()) {
            for (CavePosition position : plan.baselinePreconditions().keySet()) {
                long packed = RiverFootprint.pack(position.x(), position.z());
                if (!visitedColumns.add(packed)) {
                    continue;
                }
                HydrologyColumnSample sample = footprint.sample(position.x(), position.z()).orElse(null);
                HydrologyTerrainSample terrain = sample == null
                        ? Objects.requireNonNull(planner.sampler.sample(position.x(), position.z()))
                        : null;
                int naturalHeight = sample == null ? terrain.naturalHeight() : sample.naturalHeight();
                int materializedHeight = sample == null ? naturalHeight : sample.terrainHeight();
                int compactHeight = validation.plannedSurface().resolve(
                        position.x(),
                        position.z(),
                        naturalHeight
                );
                if (compactHeight != materializedHeight) {
                    return false;
                }
            }
        }
        return true;
    }

    CrossTileBlockers lowerRankBlockers(
            HydrologyTileKey ownerKey,
            HydrologyCaveCourseFilter.Result result,
            int ownerRank,
            CrossTileResolutionContext context,
            Map<HydrologyTileKey, HydrologyForkJoin.Task<CrossTileResolvedOwner>> preparedOwners
    ) {
        ArrayList<HydrologyCrossTileCaveAdmission.RankedClaim> blockers = new ArrayList<>();
        ArrayList<HydrologyCrossTileSurfaceAdmission.RankedClaim> surfaceBlockers = new ArrayList<>();
        ArrayList<HydrologyTileKey> candidateKeys = new ArrayList<>();
        for (HydrologyTileKey candidateKey : conflictOwnerKeys(ownerKey, result)) {
            if (candidateKey.equals(ownerKey)) {
                continue;
            }
            int candidateRank = ownerColorRank(candidateKey);
            if (candidateRank == ownerRank) {
                throw new IllegalStateException("Equal owner colors cannot share a cave-action conflict envelope.");
            }
            if (candidateRank > ownerRank) {
                continue;
            }
            candidateKeys.add(candidateKey);
        }
        long dependenciesStarted = System.nanoTime();
        List<CrossTileResolvedOwner> resolvedCandidates = planner.resolveLowerRankOwners(candidateKeys, context, preparedOwners);
        planner.currentDraftProfile().recordDependencies(candidateKeys.size(), dependenciesStarted);
        for (int index = 0; index < candidateKeys.size(); index++) {
            HydrologyTileKey candidateKey = candidateKeys.get(index);
            int candidateRank = ownerColorRank(candidateKey);
            CrossTileResolvedOwner resolved = resolvedCandidates.get(index);
            for (HydrologyCrossTileCaveAdmission.Claim claim : caveClaims(resolved.draft())) {
                blockers.add(new HydrologyCrossTileCaveAdmission.RankedClaim(
                        candidateKey,
                        candidateRank,
                        claim
                ));
            }
            for (HydrologyCrossTileSurfaceAdmission.Claim claim : surfaceClaims(resolved.draft())) {
                surfaceBlockers.add(new HydrologyCrossTileSurfaceAdmission.RankedClaim(
                        candidateKey,
                        candidateRank,
                        claim
                ));
            }
        }
        return new CrossTileBlockers(List.copyOf(blockers), List.copyOf(surfaceBlockers));
    }

    List<HydrologyTileKey> conflictOwnerKeys(HydrologyOwnerDraft target) {
        return conflictOwnerKeys(target.key(), target.result());
    }

    List<HydrologyTileKey> conflictOwnerKeys(
            HydrologyTileKey ownerKey,
            HydrologyCaveCourseFilter.Result result
    ) {
        int tileSize = planner.settings.routing().tileSize();
        int reach = Math.addExact(planner.settings.publicationRadius(), 1);
        int colorPeriod = planner.settings.crossTileColorPeriod();
        LongOpenHashSet columns = new LongOpenHashSet();
        for (HydrologyCavePlan plan : result.cavePlans()) {
            for (CavePosition position : plan.actions().keySet()) {
                columns.add(RiverFootprint.pack(position.x(), position.z()));
            }
        }
        for (RiverCourse course : result.courses()) {
            if (course.type() != RiverCourseType.SURFACE) {
                continue;
            }
            for (HydraulicSegment segment : course.segments()) {
                for (HydrologyPoint point : segment.centerline()) {
                    columns.add(RiverFootprint.pack(point.x(), point.z()));
                }
            }
        }
        LinkedHashSet<HydrologyTileKey> owners = new LinkedHashSet<>();
        owners.add(ownerKey);
        LongIterator iterator = columns.iterator();
        while (iterator.hasNext()) {
            long column = iterator.nextLong();
            int x = RiverFootprint.unpackX(column);
            int z = RiverFootprint.unpackZ(column);
            int minimumTileX = ownerCoordinate((long) x - reach, tileSize);
            int maximumTileX = ownerCoordinate((long) x + reach, tileSize);
            int minimumTileZ = ownerCoordinate((long) z - reach, tileSize);
            int maximumTileZ = ownerCoordinate((long) z + reach, tileSize);
            long ownerWidth = Math.addExact((long) maximumTileX - minimumTileX, 1L);
            long ownerDepth = Math.addExact((long) maximumTileZ - minimumTileZ, 1L);
            if (ownerWidth > colorPeriod || ownerDepth > colorPeriod) {
                throw new IllegalStateException("A cave action exceeds its bounded owner color neighborhood.");
            }
            for (int tileZ = minimumTileZ; tileZ <= maximumTileZ; tileZ++) {
                for (int tileX = minimumTileX; tileX <= maximumTileX; tileX++) {
                    owners.add(new HydrologyTileKey(tileX, tileZ));
                }
            }
        }
        int sourceReach = Math.max(0, planner.settings.maximumSurfaceSourceSpacing() - 1);
        for (HydrologyCrossTileSurfaceAdmission.Claim claim : surfaceClaims(result)) {
            HydrologyPoint source = claim.centerline().getFirst();
            int minimumTileX = ownerCoordinate((long) source.x() - sourceReach, tileSize);
            int maximumTileX = ownerCoordinate((long) source.x() + sourceReach, tileSize);
            int minimumTileZ = ownerCoordinate((long) source.z() - sourceReach, tileSize);
            int maximumTileZ = ownerCoordinate((long) source.z() + sourceReach, tileSize);
            for (int tileZ = minimumTileZ; tileZ <= maximumTileZ; tileZ++) {
                for (int tileX = minimumTileX; tileX <= maximumTileX; tileX++) {
                    owners.add(new HydrologyTileKey(tileX, tileZ));
                }
            }
        }
        ArrayList<HydrologyTileKey> ordered = new ArrayList<>(owners);
        ordered.sort(HydrologyTileKey::compareTo);
        return List.copyOf(ordered);
    }

    int ownerColorRank(HydrologyTileKey key) {
        int colorPeriod = planner.settings.crossTileColorPeriod();
        int colorX = Math.floorMod(key.tileX(), colorPeriod);
        int colorZ = Math.floorMod(key.tileZ(), colorPeriod);
        return Math.addExact(Math.multiplyExact(colorZ, colorPeriod), colorX);
    }

    List<HydrologyCrossTileCaveAdmission.Claim> caveClaims(HydrologyOwnerDraft draft) {
        return caveClaims(draft.result());
    }

    List<HydrologyCrossTileCaveAdmission.Claim> caveClaims(
            HydrologyCaveCourseFilter.Result result
    ) {
        if (result.cavePlans().isEmpty()) {
            return List.of();
        }
        Map<Long, RiverCourse> coursesById = coursesById(result.courses());
        ArrayList<HydrologyCrossTileCaveAdmission.Claim> claims =
                new ArrayList<>(result.cavePlans().size());
        for (HydrologyCavePlan plan : result.cavePlans()) {
            RiverCourse course = coursesById.get(plan.source().sourceId());
            if (course == null) {
                throw new IllegalStateException("Accepted cave plan has no owner course.");
            }
            claims.add(new HydrologyCrossTileCaveAdmission.Claim(course.profileKey(), plan));
        }
        return List.copyOf(claims);
    }

    List<HydrologyCrossTileSurfaceAdmission.Claim> surfaceClaims(HydrologyOwnerDraft draft) {
        return surfaceClaims(draft.result());
    }

    List<HydrologyCrossTileSurfaceAdmission.Claim> surfaceClaims(
            HydrologyCaveCourseFilter.Result result
    ) {
        ArrayList<HydrologyCrossTileSurfaceAdmission.Claim> claims = new ArrayList<>();
        for (RiverCourse course : result.courses()) {
            if (course.type() != RiverCourseType.SURFACE || course.outletId().isEmpty()) {
                continue;
            }
            ArrayList<HydrologyPoint> centerline = new ArrayList<>();
            int maximumWidth = 1;
            for (HydraulicSegment segment : course.segments()) {
                maximumWidth = Math.max(maximumWidth, segment.width());
                for (HydrologyPoint point : segment.centerline()) {
                    if (centerline.isEmpty()
                            || point.x() != centerline.getLast().x()
                            || point.z() != centerline.getLast().z()) {
                        centerline.add(point);
                    }
                }
            }
            if (centerline.size() < 2) {
                continue;
            }
            HydrologyFeatureType terminalType = course.segments().getLast().type();
            boolean reachesOutlet = terminalType == HydrologyFeatureType.MOUTH
                    || terminalType == HydrologyFeatureType.COASTAL_GROTTO
                    || terminalType == HydrologyFeatureType.INLAND_GROTTO;
            if (!reachesOutlet) {
                continue;
            }
            claims.add(new HydrologyCrossTileSurfaceAdmission.Claim(
                    course.id(),
                    course.outletId().getAsLong(),
                    centerline.getLast(),
                    reachesOutlet,
                    maximumWidth,
                    centerline,
                    sourceSpacing(course, result.nodes())
            ));
        }
        return List.copyOf(claims);
    }

    private int sourceSpacing(RiverCourse course, List<DrainageNode> nodes) {
        int fallback = planner.settings.surface().sources().minimumSpacing();
        if (course.sourceNodeId().isPresent()) {
            long sourceId = course.sourceNodeId().getAsLong();
            for (DrainageNode node : nodes) {
                if (node.id() == sourceId) {
                    return node.terrain().surfacePolicy().sourceSpacing(fallback);
                }
            }
        }
        return fallback;
    }

    Map<Long, RiverCourse> coursesById(List<RiverCourse> courses) {
        LinkedHashMap<Long, RiverCourse> indexed = new LinkedHashMap<>(courses.size());
        for (RiverCourse course : courses) {
            RiverCourse existing = indexed.putIfAbsent(course.id(), course);
            if (existing != null && !existing.equals(course)) {
                throw new IllegalStateException("Hydrology course id collision.");
            }
        }
        return Map.copyOf(indexed);
    }

    void validateOwnerDraftReach(HydrologyOwnerDraft draft) {
        int tileSize = planner.settings.routing().tileSize();
        int reach = Math.addExact(planner.settings.publicationRadius(), 1);
        long minimumX = (long) draft.key().minimumBlockX(tileSize) - reach;
        long minimumZ = (long) draft.key().minimumBlockZ(tileSize) - reach;
        long maximumX = (long) draft.key().minimumBlockX(tileSize) + tileSize - 1L + reach;
        long maximumZ = (long) draft.key().minimumBlockZ(tileSize) + tileSize - 1L + reach;
        for (HydrologyCavePlan plan : draft.result().cavePlans()) {
            for (CavePosition position : plan.actions().keySet()) {
                if (position.x() < minimumX || position.x() > maximumX
                        || position.z() < minimumZ || position.z() > maximumZ) {
                    throw new IllegalStateException("Hydrology cave plan exceeds its bounded owner reach.");
                }
            }
        }
    }

    static int ownerCoordinate(long blockCoordinate, int tileSize) {
        return Math.toIntExact(Math.floorDiv(blockCoordinate, tileSize));
    }

    void settleSelection(
            HydrologyTileKey key,
            HydrologySampledGrid grid,
            HydrologyRoutingPlan surfaceRouting,
            HydrologyRoutingPlan undergroundRouting,
            SourceSelection selection,
            boolean surface,
            HydrologyFootprintCompiler footprintCompiler,
            Map<Long, List<HydrologyPoint>> refinedEdges,
            Map<SourceCompilationKey, SourceCompilation> sourceCompilations,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        HydrologyRoutingPlan selectedRouting = surface ? surfaceRouting : undergroundRouting;
        if (grid == null
                || selectedRouting == null
                || selection.selectedCandidateIndices.isEmpty()) {
            return;
        }
        SourceSelection emptySelection = SourceSelection.empty(!surface);
        int maximumPasses = Math.addExact(1, selection.candidateCount());
        for (int pass = 0; pass < maximumPasses; pass++) {
            PublicationAttempt attempt = compilePublication(
                    key,
                    grid,
                    surfaceRouting,
                    undergroundRouting,
                    surface ? selection : emptySelection,
                    surface ? emptySelection : selection,
                    footprintCompiler,
                    refinedEdges,
                    sourceCompilations,
                    false
            );
            diagnostics.addAll(attempt.diagnostics());
            if (!selection.advanceAfterPublication(attempt.result().courses(), grid)) {
                return;
            }
        }
        throw new IllegalStateException("Hydrology source trial exceeded its candidate bound.");
    }

    PublicationAttempt compilePublication(
            HydrologyTileKey key,
            HydrologySampledGrid grid,
            HydrologyRoutingPlan surfaceRouting,
            HydrologyRoutingPlan undergroundRouting,
            SourceSelection surfaceSelection,
            SourceSelection undergroundSelection,
            HydrologyFootprintCompiler footprintCompiler,
            Map<Long, List<HydrologyPoint>> refinedEdges,
            Map<SourceCompilationKey, SourceCompilation> sourceCompilations,
            boolean includeDeepFluids
    ) {
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();
        ArrayList<RiverCourse> courses = new ArrayList<>();
        CompiledGraph graph = new CompiledGraph(List.of(), List.of(), List.of(), Map.of());
        if (grid != null) {
            List<Integer> surfaceSources = surfaceSelection.selectedNodeIndices();
            List<Integer> undergroundSources = undergroundSelection.selectedNodeIndices();
            if (surfaceRouting != null && surfaceRouting == undergroundRouting) {
                int[] surfaceContributions = planner.sourcePlanner.countContributions(surfaceSources, surfaceRouting);
                int[] undergroundContributions = planner.sourcePlanner.countContributions(undergroundSources, surfaceRouting);
                graph = planner.sourcePlanner.compileGraph(
                        key,
                        grid,
                        surfaceRouting,
                        surfaceContributions,
                        undergroundContributions,
                        refinedEdges,
                        true
                );
                if (planner.settings.surface().enabled()) {
                    planner.surfaceCourses.compileSurfaceCourses(
                            key,
                            grid,
                            surfaceRouting,
                            graph,
                            surfaceSources,
                            courses,
                            diagnostics
                    );
                }
                if (planner.settings.underground().enabled()) {
                    planner.undergroundCourses.compileUndergroundCourses(
                            key,
                            grid,
                            surfaceRouting,
                            graph,
                            undergroundSources,
                            courses,
                            diagnostics
                    );
                }
            } else {
                CompiledGraph surfaceGraph = new CompiledGraph(List.of(), List.of(), List.of(), Map.of());
                CompiledGraph undergroundGraph = new CompiledGraph(List.of(), List.of(), List.of(), Map.of());
                if (surfaceRouting != null && !surfaceSources.isEmpty()) {
                    SourceCompilation surfaceCompilation = compileSourceSelection(
                            true,
                            key,
                            grid,
                            surfaceRouting,
                            surfaceSources,
                            refinedEdges,
                            sourceCompilations
                    );
                    surfaceGraph = surfaceCompilation.graph();
                    courses.addAll(surfaceCompilation.courses());
                    diagnostics.addAll(surfaceCompilation.diagnostics());
                }
                if (undergroundRouting != null && !undergroundSources.isEmpty()) {
                    SourceCompilation undergroundCompilation = compileSourceSelection(
                            false,
                            key,
                            grid,
                            undergroundRouting,
                            undergroundSources,
                            refinedEdges,
                            sourceCompilations
                    );
                    undergroundGraph = undergroundCompilation.graph();
                    courses.addAll(undergroundCompilation.courses());
                    diagnostics.addAll(undergroundCompilation.diagnostics());
                }
                graph = planner.sourcePlanner.mergeGraphs(surfaceGraph, undergroundGraph);
            }
        }
        HydrologySurfaceProfiles.rejectExcludedWetFootprints(footprintCompiler, courses, diagnostics);
        List<RiverCourse> normalizedTrunkCourses = planner.tributaries.normalizeSharedTrunks(courses, diagnostics);
        List<RiverCourse> normalizedOutletCourses = planner.tributaries.normalizeOutletContinuations(normalizedTrunkCourses);
        courses.clear();
        courses.addAll(normalizedOutletCourses);
        if (includeDeepFluids) {
            planner.featureSites.compileDeepFluidCourses(key, courses, diagnostics);
        }
        planner.featureSites.compileSurfacePools(key, courses, diagnostics);
        if (includeDeepFluids && grid != null) {
            planner.featureSites.compileSeaCaves(grid, courses, diagnostics);
        }
        DraftProfile profile = planner.currentDraftProfile();
        long rasterStarted = System.nanoTime();
        HydrologyFootprintCompiler.ValidationRaster validation = footprintCompiler.compileValidation(courses);
        profile.rasterNanos += System.nanoTime() - rasterStarted;
        profile.rasterCalls++;
        HydrologyObservedPlannedSurface plannedSurface = new HydrologyObservedPlannedSurface(
                validation.plannedSurface()
        );
        CaveVoxelView caveView = Objects.requireNonNull(
                planner.caveViewFactory.create(plannedSurface),
                "Hydrology cave view factory returned null"
        );
        HydrologyPlanner.PlanningSamples samples = planner.planningSamples.get();
        if (samples != null && caveView instanceof HydrologyTerrainCaveVoxelView terrainCaveView) {
            caveView = terrainCaveView.withSampleCache(samples.detailed);
        }
        Map<HydrologyCaveCourseFilter.CandidateKey, HydrologyCaveCandidate> candidateCache =
                samples == null ? null : samples.caveCandidates;
        HydrologyCaveContainmentPlanner.ValidationCache validationCache =
                samples == null ? null : samples.caveValidations;
        long filterStarted = System.nanoTime();
        HydrologyCaveCourseFilter.Result containment = new HydrologyCaveCourseFilter(
                caveView,
                new HydrologyCaveCourseFilter.Options(
                        planner.settings.underground().connectToExistingCaves(),
                        planner.settings.outlets().coastalGrotto().maximumVolume(),
                        planner.settings.outlets().inlandGrotto().maximumVolume()
                ),
                candidateCache,
                validationCache,
                plannedSurface
        ).filter(
                graph.nodes(),
                graph.edges(),
                graph.outlets(),
                courses,
                validation,
                diagnostics
        );
        profile.filterNanos += System.nanoTime() - filterStarted;
        profile.filterCalls++;
        return new PublicationAttempt(containment, diagnostics);
    }

    SourceCompilation compileSourceSelection(
            boolean surface,
            HydrologyTileKey key,
            HydrologySampledGrid grid,
            HydrologyRoutingPlan routing,
            List<Integer> sources,
            Map<Long, List<HydrologyPoint>> refinedEdges,
            Map<SourceCompilationKey, SourceCompilation> sourceCompilations
    ) {
        SourceCompilationKey compilationKey = new SourceCompilationKey(surface, routing, sources);
        SourceCompilation cached = sourceCompilations.get(compilationKey);
        if (cached != null) {
            return cached;
        }
        int[] contributions = planner.sourcePlanner.countContributions(sources, routing);
        CompiledGraph graph = planner.sourcePlanner.compileGraph(
                key,
                grid,
                routing,
                surface ? contributions : new int[contributions.length],
                surface ? new int[contributions.length] : contributions,
                refinedEdges,
                surface
        );
        ArrayList<RiverCourse> courses = new ArrayList<>();
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();
        if (surface && planner.settings.surface().enabled()) {
            planner.surfaceCourses.compileSurfaceCourses(key, grid, routing, graph, sources, courses, diagnostics);
        } else if (!surface && planner.settings.underground().enabled()) {
            planner.undergroundCourses.compileUndergroundCourses(key, grid, routing, graph, sources, courses, diagnostics);
        }
        SourceCompilation compiled = new SourceCompilation(
                graph,
                List.copyOf(courses),
                List.copyOf(diagnostics)
        );
        sourceCompilations.put(compilationKey, compiled);
        return compiled;
    }

    record SourceCompilationKey(
            boolean surface,
            HydrologyRoutingPlan routing,
            List<Integer> sources
    ) {
        SourceCompilationKey {
            Objects.requireNonNull(routing, "routing");
            sources = List.copyOf(sources);
        }
    }

    record SourceCompilation(
            CompiledGraph graph,
            List<RiverCourse> courses,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
    }

    record PublicationAttempt(
            HydrologyCaveCourseFilter.Result result,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
    }

    record MaterializedHydrology(
            HydrologyCaveCourseFilter.Result result,
            RiverFootprint footprint
    ) {
        MaterializedHydrology {
            Objects.requireNonNull(result);
            Objects.requireNonNull(footprint);
        }
    }

    HydrologyDiagnosticCandidate surfaceConflictDiagnostic(
            RiverCourse course,
            long winnerCourseId
    ) {
        HydraulicSegment representative = course.segments().getFirst();
        return new HydrologyDiagnosticCandidate(
                HydrologyHash.mix(course.id(), HydrologySourcePlanner.DIAGNOSTIC_SALT, winnerCourseId),
                HydrologyCandidateKind.SOURCE,
                HydrologyFeatureType.SURFACE_POOL,
                representative.start(),
                HydrologyCandidateRejection.SOURCE_SPACING, 0
        );
    }
}
