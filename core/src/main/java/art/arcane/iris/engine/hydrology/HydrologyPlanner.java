package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.cave.CavePosition;
import art.arcane.iris.engine.hydrology.cave.CaveVoxelView;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveCandidate;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveContainmentPlanner;
import art.arcane.iris.engine.hydrology.cave.HydrologyCavePlan;
import art.arcane.iris.engine.hydrology.surface.ChannelProfile;
import art.arcane.iris.engine.hydrology.surface.ValleyProfile;
import art.arcane.iris.engine.hydrology.surface.ValleyProfileSolver;
import art.arcane.iris.engine.hydrology.surface.SurfaceCenterline;
import art.arcane.iris.engine.hydrology.surface.SurfaceCourseBuilder;
import art.arcane.iris.engine.hydrology.surface.SurfaceCourseResult;
import art.arcane.iris.engine.hydrology.surface.SurfaceTerminal;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.iris.util.project.noise.SimplexNoise;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.function.IntPredicate;

public final class HydrologyPlanner {
    private static final long NODE_SALT = 0x4e4f4445L;
    private static final double CROSS_DROP_WEIGHT = 3D;
    private static final long OUTLET_SALT = 0x4f55544c4554L;
    /** Sea outlet types in the order the coastal budget takes them, one of each type per turn. */
    private static final List<HydrologyFeatureType> COASTAL_OUTLET_TYPES = List.of(
            HydrologyFeatureType.MOUTH,
            HydrologyFeatureType.COASTAL_GROTTO
    );
    private static final long EDGE_SALT = 0x45444745L;
    private static final long SURFACE_SOURCE_SALT = 0x53555246414345L;
    private static final long UNDERGROUND_SOURCE_SALT = 0x554e444552L;
    private static final long COURSE_SALT = 0x434f55525345L;
    private static final long SEGMENT_SALT = 0x5345474d454e54L;
    private static final int TRIBUTARY_JUNCTION_ATTEMPTS = 4;
    private static final int TRIBUTARY_BACKWATER_LIMIT = 3;
    private static final int TRIBUTARY_JUNCTION_FLAT_STATIONS = 6;
    private static final int TRIBUTARY_JUNCTION_PAIR_INDEX = 0x4a4f494e;
    private static final long DEEP_FLUID_SALT = 0x44454550464cL;
    private static final long SURFACE_POOL_SALT = 0x504f4f4cL;
    private static final long SEA_CAVE_SALT = 0x534541434156L;
    private static final long DEEP_FLUID_X_OFFSET_SALT = 0x44454550584fL;
    private static final long DEEP_FLUID_Z_OFFSET_SALT = 0x444545505a4fL;
    private static final long DEEP_CHANNEL_HEADING_SALT = 0x44454550484447L;
    private static final long DEEP_CHANNEL_FIRST_BEND_SALT = 0x444545504231L;
    private static final long DEEP_CHANNEL_SECOND_BEND_SALT = 0x444545504232L;
    private static final long GRADED_DROP_APPROACH_SALT = 0x474441505052L;
    private static final long GRADED_DROP_RUN_SALT = 0x474452554eL;
    private static final long GRADED_DROP_OUTFLOW_SALT = 0x47444f5554L;
    private static final long DIAGNOSTIC_SALT = 0x444941474e4f5354L;
    private static final long ROUTE_ANCHOR_X_SALT = 0x52414e434858L;
    private static final long ROUTE_ANCHOR_Z_SALT = 0x52414e43485aL;
    private static final long ROUTE_WORM_PRIMARY_SALT = 0x52575052494dL;
    private static final long ROUTE_WORM_DETAIL_SALT = 0x525744455441L;
    private static final int ROUTING_CONTEXT_CACHE_SIZE = 64;
    private static final int RESOLVED_OWNER_CACHE_SIZE = 64;
    private static final int REFINED_EDGE_CACHE_SIZE = 4096;
    private static final double ROUTE_ANCHOR_MAXIMUM_OFFSET_RATIO = 0.32D;
    private static final double ROUTE_TANGENT_SCALE = 0.68D;
    private static final int ROUTE_TRANSVERSE_CANDIDATES = 2;
    private static final int ROUTE_FALLBACK_TRANSVERSE_CANDIDATES = 4;
    private static final int SURFACE_COURSE_TRANSVERSE_CANDIDATES = 2;
    private static final int OPTIONAL_SOURCE_REJECTIONS_PER_TARGET = 4;
    private static final int SURFACE_OPTIONAL_SOURCE_REJECTIONS_PER_TARGET = 8;
    private static final double SURFACE_MAXIMUM_RENDERED_TURN_DEGREES = 50D;
    private static final double SURFACE_MAXIMUM_ISOLATED_TURN_DEGREES = 35D;
    private static final double SURFACE_MAXIMUM_ISOLATED_NEIGHBOR_TURN_DEGREES = 10D;
    private static final double SURFACE_MAXIMUM_P95_TURN_DEGREES = 35D;
    private static final double SURFACE_MAXIMUM_SINUOSITY = 1.45D;
    private static final double SURFACE_GRID_LOCK_TOLERANCE_DEGREES = 5D;
    private static final double SURFACE_MAXIMUM_GRID_LOCKED_FRACTION = 0.5D;
    private static final double SURFACE_MAXIMUM_GRID_LOCKED_RUN = 64D;
    private static final double SURFACE_COURSE_MAXIMUM_TURN_DEGREES = 50D;
    private static final double SURFACE_COURSE_TURN_COST = 2.5D;
    private static final double SURFACE_MAXIMUM_WIDTH_SCALE = 1.18D;
    private static final int SURFACE_MINIMUM_NATURAL_BANK_RISE = 1;
    private static final int MAXIMUM_SURFACE_BORE_STEP = 4;
    private static final List<GridOffset> CARDINAL_OFFSETS = List.of(
            new GridOffset(-1, 0),
            new GridOffset(1, 0),
            new GridOffset(0, -1),
            new GridOffset(0, 1)
    );
    private static final List<GridOffset> ROUTING_OFFSETS = List.of(
            new GridOffset(-1, 0),
            new GridOffset(1, 0),
            new GridOffset(0, -1),
            new GridOffset(0, 1),
            new GridOffset(-1, -1),
            new GridOffset(-1, 1),
            new GridOffset(1, -1),
            new GridOffset(1, 1)
    );

    private final long worldSeed;
    private final HydrologyPlannerSettings settings;
    private final HydrologyTerrainSampler sampler;
    private final HydrologyRoutingTerrainSampler routingSampler;
    private final HydrologyNaturalTerrainSampler naturalSampler;
    private final HydrologyGeometrySampler geometrySampler;
    private final int minimumY;
    private final HydrologyCaveVoxelViewFactory caveViewFactory;
    private final Cache<HydrologyTileKey, SourceRoutingContext> routingContexts;
    private final ConcurrentHashMap<HydrologyTileKey, RoutingContextLoad> resolvingRoutingContexts;
    private final Cache<HydrologyTileKey, CrossTileResolvedOwner> resolvedOwners;
    private final Cache<RefinedEdgeKey, List<HydrologyPoint>> refinedEdgeCache;
    private final ConcurrentHashMap<HydrologyTileKey, CompletableFuture<CrossTileResolvedOwner>> resolvingOwners;
    private final ThreadLocal<PlanningSamples> planningSamples;
    private final ThreadLocal<DraftProfile> draftProfiles;
    private final SimplexNoise routeAnchorX;
    private final SimplexNoise routeAnchorZ;
    private final SimplexNoise routeWormPrimary;
    private final SimplexNoise routeWormDetail;
    private final SurfaceCourseBuilder surfaceCourseBuilder;
    private final SurfaceCourseBuilder tributaryCourseBuilder;

    public HydrologyPlanner(long worldSeed, HydrologyPlannerSettings settings, HydrologyTerrainSampler sampler) {
        this(
                worldSeed,
                settings,
                sampler,
                HydrologyGeometrySampler.deterministic(sampler),
                -4096,
                footprint -> new HydrologyTerrainCaveVoxelView(sampler, settings.seaLevel(), -4096, 4096)
        );
    }

    public HydrologyPlanner(
            long worldSeed,
            HydrologyPlannerSettings settings,
            HydrologyTerrainSampler sampler,
            int worldHeight
    ) {
        this(
                worldSeed,
                settings,
                sampler,
                HydrologyGeometrySampler.deterministic(sampler),
                0,
                footprint -> new HydrologyTerrainCaveVoxelView(sampler, settings.seaLevel(), worldHeight)
        );
    }

    public HydrologyPlanner(
            long worldSeed,
            HydrologyPlannerSettings settings,
            HydrologyTerrainSampler sampler,
            CaveVoxelView caveView
    ) {
        this(
                worldSeed,
                settings,
                sampler,
                HydrologyGeometrySampler.deterministic(sampler),
                Integer.MIN_VALUE,
                footprint -> caveView
        );
    }

    public HydrologyPlanner(
            long worldSeed,
            HydrologyPlannerSettings settings,
            HydrologyTerrainSampler sampler,
            int minimumY,
            HydrologyCaveVoxelViewFactory caveViewFactory
    ) {
        this(
                worldSeed,
                settings,
                sampler,
                HydrologyGeometrySampler.deterministic(sampler),
                minimumY,
                caveViewFactory
        );
    }

    public HydrologyPlanner(
            long worldSeed,
            HydrologyPlannerSettings settings,
            HydrologyTerrainSampler sampler,
            HydrologyGeometrySampler geometrySampler,
            int minimumY,
            HydrologyCaveVoxelViewFactory caveViewFactory
    ) {
        this(
                worldSeed,
                settings,
                sampler,
                new PointRoutingTerrainSampler(sampler),
                geometrySampler,
                minimumY,
                caveViewFactory
        );
    }

    public HydrologyPlanner(
            long worldSeed,
            HydrologyPlannerSettings settings,
            HydrologyTerrainSampler sampler,
            HydrologyRoutingTerrainSampler routingSampler,
            HydrologyGeometrySampler geometrySampler,
            int minimumY,
            HydrologyCaveVoxelViewFactory caveViewFactory
    ) {
        this.worldSeed = worldSeed;
        this.settings = Objects.requireNonNull(settings, "settings");
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        this.routingSampler = Objects.requireNonNull(routingSampler, "routingSampler");
        this.naturalSampler = routingSampler instanceof HydrologyNaturalTerrainSampler pointSampler
                ? pointSampler
                : null;
        this.geometrySampler = Objects.requireNonNull(geometrySampler, "geometrySampler");
        this.minimumY = minimumY;
        this.caveViewFactory = Objects.requireNonNull(caveViewFactory, "caveViewFactory");
        this.routingContexts = Caffeine.newBuilder()
                .maximumSize(ROUTING_CONTEXT_CACHE_SIZE)
                .build();
        this.resolvingRoutingContexts = new ConcurrentHashMap<>();
        this.resolvedOwners = Caffeine.newBuilder()
                .maximumSize(RESOLVED_OWNER_CACHE_SIZE)
                .build();
        this.refinedEdgeCache = Caffeine.newBuilder()
                .maximumSize(REFINED_EDGE_CACHE_SIZE)
                .build();
        this.resolvingOwners = new ConcurrentHashMap<>();
        this.planningSamples = new ThreadLocal<>();
        this.draftProfiles = new ThreadLocal<>();
        this.routeAnchorX = new SimplexNoise(HydrologyHash.mix(worldSeed, ROUTE_ANCHOR_X_SALT));
        this.routeAnchorZ = new SimplexNoise(HydrologyHash.mix(worldSeed, ROUTE_ANCHOR_Z_SALT));
        this.routeWormPrimary = new SimplexNoise(HydrologyHash.mix(worldSeed, ROUTE_WORM_PRIMARY_SALT));
        this.routeWormDetail = new SimplexNoise(HydrologyHash.mix(worldSeed, ROUTE_WORM_DETAIL_SALT));
        this.surfaceCourseBuilder = new SurfaceCourseBuilder(
                settings.surface(),
                this::sampleBasisWithoutSlope,
                geometrySampler,
                settings.seaLevel(),
                settings.routing().minimumSurfaceCourseLength()
        );
        // A tributary only has to be a real reach before it joins its stem: half the course minimum.
        this.tributaryCourseBuilder = new SurfaceCourseBuilder(
                settings.surface(),
                this::sampleBasisWithoutSlope,
                geometrySampler,
                settings.seaLevel(),
                settings.routing().minimumSurfaceCourseLength() / 2
        );
    }

    public long worldSeed() {
        return worldSeed;
    }

    public HydrologyPlannerSettings settings() {
        return settings;
    }

    /**
     * A tile with the same identity as a planned tile but no rivers, used when planning the tile
     * failed and terrain there must still generate.
     */
    public HydrologyTile emptyTile(HydrologyTileKey key) {
        Objects.requireNonNull(key, "key");
        return new HydrologyTile(
                key,
                worldSeed,
                settings.fingerprint(),
                settings.routing().tileSize(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                RiverFootprint.empty()
        );
    }

    public HydrologyTile plan(HydrologyTileKey key) {
        long started = System.nanoTime();
        CrossTileResolution resolution = resolveCrossTileOwner(key);
        long materializeStarted = System.nanoTime();
        HydrologyTile tile = materializeAcceptedTile(resolution);
        long finished = System.nanoTime();
        IrisLogging.debug(
                "Hydrology tile %d,%d planned in %dms: owners=%d resolve=%dms materialize=%dms courses=%d on %s",
                key.tileX(),
                key.tileZ(),
                (finished - started) / 1_000_000L,
                resolution.ownerCount(),
                (materializeStarted - started) / 1_000_000L,
                (finished - materializeStarted) / 1_000_000L,
                tile.courses().size(),
                Thread.currentThread().getName()
        );
        return tile;
    }

    void clearOwnerDrafts() {
        resolvedOwners.invalidateAll();
    }

    void reuseResolvedTile(HydrologyTile tile) {
        Objects.requireNonNull(tile, "tile");
        if (tile.worldSeed() != worldSeed
                || tile.settingsFingerprint() != settings.fingerprint()
                || tile.tileSize() != settings.routing().tileSize()) {
            throw new IllegalArgumentException("Hydrology tile does not match this planner.");
        }
        HydrologyCaveCourseFilter.Result result = new HydrologyCaveCourseFilter.Result(
                tile.nodes(),
                tile.edges(),
                tile.outlets(),
                tile.courses(),
                tile.cavePlans()
        );
        HydrologyOwnerDraft draft = new HydrologyOwnerDraft(
                tile.key(),
                result,
                tile.diagnosticCandidates(),
                null
        );
        resolvedOwners.put(tile.key(), new CrossTileResolvedOwner(draft, List.of()));
    }

    private HydrologyOwnerDraft compileOwnerDraft(
            HydrologyTileKey key,
            CrossTileDraftAdmission crossTileAdmission,
            boolean retainFootprintCompiler
    ) {
        PlanningSamples previous = planningSamples.get();
        if (previous == null) {
            planningSamples.set(new PlanningSamples());
        }
        DraftProfile previousProfile = draftProfiles.get();
        DraftProfile profile = new DraftProfile();
        draftProfiles.set(profile);
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
                planningSamples.remove();
            }
            if (previousProfile == null) {
                draftProfiles.remove();
            } else {
                draftProfiles.set(previousProfile);
            }
        }
    }

    private HydrologyOwnerDraft compileScopedOwnerDraft(
            HydrologyTileKey key,
            CrossTileDraftAdmission crossTileAdmission,
            boolean retainFootprintCompiler
    ) {
        Objects.requireNonNull(key, "key");
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();
        SampledGrid grid = null;
        RoutingPlan surfaceRouting = null;
        RoutingPlan undergroundRouting = null;
        SourceSelection surfaceSelection = SourceSelection.empty(true);
        SourceSelection undergroundSelection = SourceSelection.empty(false);
        DraftProfile profile = currentDraftProfile();
        long phaseStarted = System.nanoTime();
        if (hasRoutedSourceSearch()) {
            SourceRoutingContext primaryContext = sourceRoutingContext(key);
            phaseStarted = profile.record(DraftPhase.CONTEXT, phaseStarted);
            grid = primaryContext.grid();
            surfaceRouting = primaryContext.surfaceRouting();
            undergroundRouting = primaryContext.undergroundRouting();
            diagnostics.addAll(primaryContext.diagnostics());
            HashMap<HydrologyTileKey, SourceRoutingContext> sourceRoutingContexts = new HashMap<>();
            sourceRoutingContexts.put(key, primaryContext);
            surfaceSelection = selectSources(
                    key,
                    grid,
                    surfaceRouting,
                    true,
                    true,
                    diagnostics,
                    sourceRoutingContexts
            );
            undergroundSelection = selectSources(
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
                settings,
                new HydrologyFootprintCompiler.Sampling(
                        sampler,
                        geometrySampler,
                        naturalSampler
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
                : resolveSurfaceFallbackOutlets(grid);
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
                RoutingPlan fallbackRouting = requireOrganicSurface(buildRouting(grid, List.of(fallbackOutlet), true));
                HashMap<HydrologyTileKey, SourceRoutingContext> fallbackContexts = new HashMap<>();
                fallbackContexts.put(key, new SourceRoutingContext(
                        grid,
                        fallbackRouting,
                        undergroundRouting,
                        List.of()
                ));
                SourceSelection fallbackSelection = selectSources(
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
        List<HydrologyDiagnosticCandidate> uniqueDiagnostics = uniqueDiagnostics(diagnostics);
        HydrologyOwnerDraft draft = new HydrologyOwnerDraft(
                key,
                result,
                uniqueDiagnostics,
                retainFootprintCompiler ? footprintCompiler : null
        );
        validateOwnerDraftReach(draft);
        return draft;
    }

    private HydrologyTile materializeAcceptedTile(CrossTileResolution resolution) {
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
                    settings,
                    new HydrologyFootprintCompiler.Sampling(
                            sampler,
                            geometrySampler,
                            naturalSampler
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
                worldSeed,
                settings.fingerprint(),
                settings.routing().tileSize(),
                result.nodes(),
                result.edges(),
                result.outlets(),
                result.courses(),
                result.cavePlans(),
                uniqueDiagnostics(diagnostics),
                footprint
        );
    }

    private MaterializedHydrology materializeFinalHydrology(
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
                    caveViewFactory.create(plannedSurface),
                    "Hydrology cave view factory returned null"
            );
            HydrologyCaveCourseFilter.Result revalidated = new HydrologyCaveCourseFilter(
                    caveView,
                    new HydrologyCaveCourseFilter.Options(
                            settings.underground().connectToExistingCaves(),
                            settings.outlets().coastalGrotto().maximumVolume(),
                            settings.outlets().inlandGrotto().maximumVolume()
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


    private boolean compactSurfaceMatchesMaterializedPlans(
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
                        ? Objects.requireNonNull(sampler.sample(position.x(), position.z()))
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

    private CrossTileResolution resolveCrossTileOwner(HydrologyTileKey key) {
        long started = System.nanoTime();
        CrossTileResolutionContext context = new CrossTileResolutionContext(
                key,
                maximumCrossTileDependencyOffset(key),
                maximumCrossTileDependencyOwners(key)
        );
        CrossTileResolvedOwner resolved = resolveCrossTileOwner(key, context);
        return new CrossTileResolution(
                resolved.draft(),
                resolved.observedRejections(),
                context.iterations(),
                context.ownerCount(),
                System.nanoTime() - started
        );
    }

    private CrossTileResolvedOwner resolveCrossTileOwner(
            HydrologyTileKey key,
            CrossTileResolutionContext context
    ) {
        CrossTileResolvedOwner local = context.resolved(key);
        if (local != null) {
            return local;
        }
        CrossTileResolvedOwner cached = resolvedOwners.getIfPresent(key);
        if (cached != null) {
            context.remember(key, cached);
            return cached;
        }
        CompletableFuture<CrossTileResolvedOwner> owned = new CompletableFuture<>();
        CompletableFuture<CrossTileResolvedOwner> existing = resolvingOwners.putIfAbsent(key, owned);
        if (existing != null) {
            CrossTileResolvedOwner resolved = awaitResolvedOwner(existing);
            context.remember(key, resolved);
            return resolved;
        }
        if (!context.begin(key)) {
            resolvingOwners.remove(key, owned);
            throw new IllegalStateException("Cross-tile owner color dependencies must be acyclic.");
        }
        try {
            int ownerRank = ownerColorRank(key);
            ColorRankedDraftAdmission admission = new ColorRankedDraftAdmission(key, ownerRank, context);
            HydrologyOwnerDraft draft;
            try (admission) {
                try {
                    draft = compileOwnerDraft(
                            key,
                            admission,
                            key.equals(context.root())
                    );
                } catch (RuntimeException | Error failure) {
                    admission.primaryFailure = failure;
                    throw failure;
                }
            }
            CrossTileResolvedOwner resolved = new CrossTileResolvedOwner(draft, admission.observedRejections());
            context.remember(key, resolved);
            CrossTileResolvedOwner shared = resolved.withoutFootprintCompiler();
            resolvedOwners.put(key, shared);
            owned.complete(shared);
            return resolved;
        } catch (Throwable failure) {
            owned.completeExceptionally(failure);
            throw propagateOwnerFailure(failure);
        } finally {
            context.end(key);
            resolvingOwners.remove(key, owned);
        }
    }

    private CrossTileBlockers lowerRankBlockers(
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
        List<CrossTileResolvedOwner> resolvedCandidates = resolveLowerRankOwners(candidateKeys, context, preparedOwners);
        currentDraftProfile().recordDependencies(candidateKeys.size(), dependenciesStarted);
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

    private List<CrossTileResolvedOwner> resolveLowerRankOwners(
            List<HydrologyTileKey> candidateKeys,
            CrossTileResolutionContext context,
            Map<HydrologyTileKey, HydrologyForkJoin.Task<CrossTileResolvedOwner>> preparedOwners
    ) {
        if (candidateKeys.size() < 2 || !IrisPlatforms.isBound()) {
            ArrayList<CrossTileResolvedOwner> resolved = new ArrayList<>(candidateKeys.size());
            for (HydrologyTileKey candidateKey : candidateKeys) {
                HydrologyForkJoin.Task<CrossTileResolvedOwner> prepared = preparedOwners.get(candidateKey);
                CrossTileResolvedOwner owner = prepared == null
                        ? resolveCrossTileOwner(candidateKey, context) : prepared.await();
                context.remember(candidateKey, owner);
                resolved.add(owner);
            }
            return List.copyOf(resolved);
        }
        // Neighbour drafts use the current pool (or the burst pool from outside any pool).
        // Waiting workers claim their own tasks without helping unrelated owner drafts.
        ArrayList<Callable<CrossTileResolvedOwner>> tasks = new ArrayList<>(candidateKeys.size());
        for (HydrologyTileKey candidateKey : candidateKeys) {
            HydrologyForkJoin.Task<CrossTileResolvedOwner> prepared = preparedOwners.get(candidateKey);
            tasks.add(prepared == null ? () -> resolveIndependentOwner(candidateKey) : prepared::await);
        }
        List<CrossTileResolvedOwner> owners = HydrologyForkJoin.invokeAll(tasks, MultiBurst.burst);
        ArrayList<CrossTileResolvedOwner> resolved = new ArrayList<>(candidateKeys.size());
        for (int index = 0; index < candidateKeys.size(); index++) {
            CrossTileResolvedOwner owner = owners.get(index);
            context.remember(candidateKeys.get(index), owner);
            resolved.add(owner);
        }
        return List.copyOf(resolved);
    }

    private CrossTileResolvedOwner resolveIndependentOwner(HydrologyTileKey key) {
        CrossTileResolutionContext context = new CrossTileResolutionContext(
                key,
                maximumCrossTileDependencyOffset(key),
                maximumCrossTileDependencyOwners(key)
        );
        return resolveCrossTileOwner(key, context);
    }

    private static CrossTileResolvedOwner awaitResolvedOwner(
            CompletableFuture<CrossTileResolvedOwner> future
    ) {
        try {
            return future.join();
        } catch (CompletionException failure) {
            throw propagateOwnerFailure(failure.getCause());
        }
    }

    private static RuntimeException propagateOwnerFailure(Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("Hydrology owner planning failed.", failure);
    }

    private List<HydrologyTileKey> conflictOwnerKeys(HydrologyOwnerDraft target) {
        return conflictOwnerKeys(target.key(), target.result());
    }

    private List<HydrologyTileKey> conflictOwnerKeys(
            HydrologyTileKey ownerKey,
            HydrologyCaveCourseFilter.Result result
    ) {
        int tileSize = settings.routing().tileSize();
        int reach = Math.addExact(settings.publicationRadius(), 1);
        int colorPeriod = settings.crossTileColorPeriod();
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
        int sourceReach = Math.max(0, settings.surface().sources().minimumSpacing() - 1);
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

    private int ownerColorRank(HydrologyTileKey key) {
        int colorPeriod = settings.crossTileColorPeriod();
        int colorX = Math.floorMod(key.tileX(), colorPeriod);
        int colorZ = Math.floorMod(key.tileZ(), colorPeriod);
        return Math.addExact(Math.multiplyExact(colorZ, colorPeriod), colorX);
    }

    int maximumCrossTileDependencyOwners(HydrologyTileKey key) {
        long offset = maximumCrossTileDependencyOffset(key);
        long width = Math.addExact(Math.multiplyExact(2L, offset), 1L);
        return Math.toIntExact(Math.multiplyExact(width, width));
    }

    private long maximumCrossTileDependencyOffset(HydrologyTileKey key) {
        return Math.multiplyExact(
                (long) ownerColorRank(key),
                settings.crossTileColorPeriod() - 1L
        );
    }

    private List<HydrologyCrossTileCaveAdmission.Claim> caveClaims(HydrologyOwnerDraft draft) {
        return caveClaims(draft.result());
    }

    private List<HydrologyCrossTileCaveAdmission.Claim> caveClaims(
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

    private List<HydrologyCrossTileSurfaceAdmission.Claim> surfaceClaims(HydrologyOwnerDraft draft) {
        return surfaceClaims(draft.result());
    }

    private List<HydrologyCrossTileSurfaceAdmission.Claim> surfaceClaims(
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
                    centerline
            ));
        }
        return List.copyOf(claims);
    }

    private Map<Long, RiverCourse> coursesById(List<RiverCourse> courses) {
        LinkedHashMap<Long, RiverCourse> indexed = new LinkedHashMap<>(courses.size());
        for (RiverCourse course : courses) {
            RiverCourse existing = indexed.putIfAbsent(course.id(), course);
            if (existing != null && !existing.equals(course)) {
                throw new IllegalStateException("Hydrology course id collision.");
            }
        }
        return Map.copyOf(indexed);
    }

    private void validateOwnerDraftReach(HydrologyOwnerDraft draft) {
        int tileSize = settings.routing().tileSize();
        int reach = Math.addExact(settings.publicationRadius(), 1);
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

    private static int ownerCoordinate(long blockCoordinate, int tileSize) {
        return Math.toIntExact(Math.floorDiv(blockCoordinate, tileSize));
    }

    private void settleSelection(
            HydrologyTileKey key,
            SampledGrid grid,
            RoutingPlan surfaceRouting,
            RoutingPlan undergroundRouting,
            SourceSelection selection,
            boolean surface,
            HydrologyFootprintCompiler footprintCompiler,
            Map<Long, List<HydrologyPoint>> refinedEdges,
            Map<SourceCompilationKey, SourceCompilation> sourceCompilations,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        RoutingPlan selectedRouting = surface ? surfaceRouting : undergroundRouting;
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

    private PublicationAttempt compilePublication(
            HydrologyTileKey key,
            SampledGrid grid,
            RoutingPlan surfaceRouting,
            RoutingPlan undergroundRouting,
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
                int[] surfaceContributions = countContributions(surfaceSources, surfaceRouting);
                int[] undergroundContributions = countContributions(undergroundSources, surfaceRouting);
                graph = compileGraph(
                        key,
                        grid,
                        surfaceRouting,
                        surfaceContributions,
                        undergroundContributions,
                        refinedEdges
                );
                if (settings.surface().enabled()) {
                    compileSurfaceCourses(
                            key,
                            grid,
                            surfaceRouting,
                            graph,
                            surfaceSources,
                            courses,
                            diagnostics
                    );
                }
                if (settings.underground().enabled()) {
                    compileUndergroundCourses(
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
                graph = mergeGraphs(surfaceGraph, undergroundGraph);
            }
        }
        List<RiverCourse> normalizedTrunkCourses = normalizeSharedTrunks(courses, diagnostics);
        List<RiverCourse> normalizedOutletCourses = normalizeOutletContinuations(normalizedTrunkCourses);
        courses.clear();
        courses.addAll(normalizedOutletCourses);
        if (includeDeepFluids) {
            compileDeepFluidCourses(key, courses, diagnostics);
        }
        compileSurfacePools(key, courses, diagnostics);
        if (includeDeepFluids && grid != null) {
            compileSeaCaves(grid, courses, diagnostics);
        }
        DraftProfile profile = currentDraftProfile();
        long rasterStarted = System.nanoTime();
        HydrologyFootprintCompiler.ValidationRaster validation = footprintCompiler.compileValidation(courses);
        profile.rasterNanos += System.nanoTime() - rasterStarted;
        profile.rasterCalls++;
        HydrologyObservedPlannedSurface plannedSurface = new HydrologyObservedPlannedSurface(
                validation.plannedSurface()
        );
        CaveVoxelView caveView = Objects.requireNonNull(
                caveViewFactory.create(plannedSurface),
                "Hydrology cave view factory returned null"
        );
        PlanningSamples samples = planningSamples.get();
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
                        settings.underground().connectToExistingCaves(),
                        settings.outlets().coastalGrotto().maximumVolume(),
                        settings.outlets().inlandGrotto().maximumVolume()
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


    private SourceCompilation compileSourceSelection(
            boolean surface,
            HydrologyTileKey key,
            SampledGrid grid,
            RoutingPlan routing,
            List<Integer> sources,
            Map<Long, List<HydrologyPoint>> refinedEdges,
            Map<SourceCompilationKey, SourceCompilation> sourceCompilations
    ) {
        SourceCompilationKey compilationKey = new SourceCompilationKey(surface, routing, sources);
        SourceCompilation cached = sourceCompilations.get(compilationKey);
        if (cached != null) {
            return cached;
        }
        int[] contributions = countContributions(sources, routing);
        CompiledGraph graph = compileGraph(
                key,
                grid,
                routing,
                surface ? contributions : new int[contributions.length],
                surface ? new int[contributions.length] : contributions,
                refinedEdges
        );
        ArrayList<RiverCourse> courses = new ArrayList<>();
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();
        if (surface && settings.surface().enabled()) {
            compileSurfaceCourses(key, grid, routing, graph, sources, courses, diagnostics);
        } else if (!surface && settings.underground().enabled()) {
            compileUndergroundCourses(key, grid, routing, graph, sources, courses, diagnostics);
        }
        SourceCompilation compiled = new SourceCompilation(
                graph,
                List.copyOf(courses),
                List.copyOf(diagnostics)
        );
        sourceCompilations.put(compilationKey, compiled);
        return compiled;
    }

    private List<RiverCourse> normalizeOutletContinuations(List<RiverCourse> courses) {
        HashMap<OutletCourseKey, RiverCourse> canonicalCourses = new HashMap<>();
        for (RiverCourse course : courses) {
            if (!hasInlandOutletContinuation(course) || course.outletId().isEmpty()) {
                continue;
            }
            OutletCourseKey key = new OutletCourseKey(course.outletId().getAsLong());
            canonicalCourses.merge(
                    key,
                    course,
                    this::preferredOutletContinuation
            );
        }
        ArrayList<RiverCourse> normalized = new ArrayList<>(courses.size());
        for (RiverCourse course : courses) {
            if (course.outletId().isEmpty()) {
                normalized.add(course);
                continue;
            }
            OutletCourseKey key = new OutletCourseKey(course.outletId().getAsLong());
            RiverCourse canonicalCourse = canonicalCourses.get(key);
            if (canonicalCourse == null || canonicalCourse.id() == course.id()) {
                normalized.add(course);
                continue;
            }
            List<HydraulicSegment> segments = course.segments();
            int retainedSize = course.surfaceSinkholeContinuation()
                    ? segments.size() - 2
                    : segments.getLast().type() == HydrologyFeatureType.INLAND_GROTTO
                    ? segments.size() - 1
                    : segments.size();
            retainedSize = trimCanonicalOutletOverlap(course, canonicalCourse, retainedSize);
            if (retainedSize < 1) {
                continue;
            }
            normalized.add(new RiverCourse(
                    course.id(),
                    course.type(),
                    course.sourceNodeId(),
                    course.outletId(),
                    course.profileKey(),
                    course.discharge(),
                    course.drainageEdges(),
                    segments.subList(0, retainedSize)
            ));
        }
        return List.copyOf(normalized);
    }

    private int trimCanonicalOutletOverlap(
            RiverCourse course,
            RiverCourse canonicalCourse,
            int retainedSize
    ) {
        if (!canonicalCourse.surfaceSinkholeContinuation()
                || canonicalCourse.segments().isEmpty()
                || retainedSize < 1) {
            return retainedSize;
        }
        HydraulicSegment canonicalGrotto = canonicalCourse.segments().getLast();
        if (canonicalGrotto.type() != HydrologyFeatureType.INLAND_GROTTO) {
            return retainedSize;
        }
        int exclusionRadius = Math.addExact(
                settings.outlets().inlandGrotto().horizontalRadius(),
                Math.addExact((int) StrictMath.ceil(settings.underground().maximumWidth() / 2D), 2)
        );
        long exclusionRadiusSquared = (long) exclusionRadius * exclusionRadius;
        while (retainedSize > 0 && segmentOverlapsOutlet(
                course.segments().get(retainedSize - 1),
                canonicalGrotto,
                exclusionRadiusSquared
        )) {
            retainedSize--;
        }
        return retainedSize;
    }

    private boolean segmentOverlapsOutlet(
            HydraulicSegment segment,
            HydraulicSegment outlet,
            long exclusionRadiusSquared
    ) {
        for (HydrologyPoint point : segment.centerline()) {
            for (HydrologyPoint outletPoint : outlet.centerline()) {
                if (point.distanceSquared2D(outletPoint) <= exclusionRadiusSquared) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<RiverCourse> normalizeSharedTrunks(List<RiverCourse> courses, List<HydrologyDiagnosticCandidate> diagnostics) {
        HashMap<CourseEdgeKey, RiverCourse> edgeOwners = new HashMap<>();
        for (RiverCourse course : courses) {
            if (course.sourceNodeId().isEmpty()) {
                continue;
            }
            for (DrainageEdge edge : course.drainageEdges()) {
                CourseEdgeKey key = new CourseEdgeKey(course.type(), edge.id());
                edgeOwners.merge(
                        key,
                        course,
                        this::preferredTrunkCourse
                );
            }
        }
        ArrayList<RiverCourse> normalized = new ArrayList<>(courses.size());
        for (RiverCourse course : courses) {
            if (course.sourceNodeId().isEmpty() || course.drainageEdges().isEmpty()) {
                normalized.add(course);
                continue;
            }
            int ownedEdgeCount = 0;
            for (DrainageEdge edge : course.drainageEdges()) {
                RiverCourse owner = edgeOwners.get(new CourseEdgeKey(course.type(), edge.id()));
                if (owner == null || owner.id() != course.id()) {
                    break;
                }
                ownedEdgeCount++;
            }
            if (ownedEdgeCount == course.drainageEdges().size()) {
                normalized.add(course);
                continue;
            }
            if (ownedEdgeCount < 1) {
                continue;
            }
            HydrologyPoint boundary = course.drainageEdges()
                    .get(ownedEdgeCount - 1)
                    .centerline()
                    .getLast();
            if (course.type() == RiverCourseType.SURFACE) {
                // Surface tributaries are cut at their junction before they are built, so a surface
                // course still sharing drainage with a longer trunk has no junction to keep it.
                addTributaryDiagnostic(course.id(), course.segments().getFirst().start(),
                        HydrologyCandidateRejection.NO_DRAINAGE_PATH, ownedEdgeCount, diagnostics);
                continue;
            }
            int retainedSegmentCount = retainedSegmentCount(course.segments(), boundary);
            if (retainedSegmentCount < 1) {
                normalized.add(course);
                continue;
            }
            normalized.add(new RiverCourse(
                    course.id(),
                    course.type(),
                    course.sourceNodeId(),
                    course.outletId(),
                    course.profileKey(),
                    course.discharge(),
                    course.drainageEdges().subList(0, ownedEdgeCount),
                    course.segments().subList(0, retainedSegmentCount)
            ));
        }
        return List.copyOf(normalized);
    }

    /**
     * Builds a later draft to the stem's outlet as a tributary. The draft's path is cut where it first
     * comes within a stem width of the stem or first enters drainage the stem already owns, and it is
     * shaped with the stem's water level at that station as its terminal, so it arrives at or above the
     * stem and steps down into it. When the tributary would arrive below the stem's water there, the
     * junction slides downstream along the stem to the first station whose water is low enough and the
     * course is shaped again. The tributary owns only the drainage edges upstream of the junction.
     */
    private RiverCourse buildSurfaceTributary(
            SurfaceCourseDraft draft,
            RiverCourse stem,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        CoursePath path = draft.path();
        HydrologyPoint sourcePoint = path.points().getFirst();
        HashSet<Long> stemEdges = new HashSet<>();
        for (DrainageEdge edge : stem.drainageEdges()) {
            stemEdges.add(edge.id());
        }
        ArrayList<HydrologyPoint> stemStations = new ArrayList<>();
        ArrayList<Integer> stemWidths = new ArrayList<>();
        for (HydraulicSegment segment : stem.segments()) {
            for (HydrologyPoint point : segment.centerline()) {
                stemStations.add(point);
                stemWidths.add(segment.width());
            }
        }
        int minimumStemIndex = 0;
        HydrologyCandidateRejection rejection = HydrologyCandidateRejection.NO_DRAINAGE_PATH;
        int rejectionDetail = 0;
        for (int attempt = 0; attempt < TRIBUTARY_JUNCTION_ATTEMPTS; attempt++) {
            int[] junction = tributaryJunction(path, stemStations, stemWidths, stemEdges, minimumStemIndex);
            if (junction == null) {
                break;
            }
            int joinIndex = junction[0];
            int stemIndex = junction[1];
            HydrologyPoint joinStation = stemStations.get(stemIndex);
            if (joinIndex < 1) {
                rejection = HydrologyCandidateRejection.COURSE_TOO_SHORT;
                rejectionDetail = 0;
                break;
            }
            TributaryBuild build = buildSurfaceTributaryAt(draft, joinIndex, stemIndex, stemStations, stemWidths, stemEdges);
            if (build.course() != null) {
                return build.course();
            }
            rejection = build.rejection();
            rejectionDetail = build.rejectionDetail();
            if (rejection != HydrologyCandidateRejection.SURFACE_HEAD_RANGE) {
                break;
            }
            // The tributary arrives rejectionDetail blocks below the stem's water: slide the junction
            // downstream to the first stem station whose water is that much lower.
            int target = joinStation.y() - rejectionDetail;
            int nextStemIndex = -1;
            for (int index = stemIndex + 1; index < stemStations.size(); index++) {
                if (stemStations.get(index).y() <= target) {
                    nextStemIndex = index;
                    break;
                }
            }
            if (nextStemIndex < 0) {
                break;
            }
            minimumStemIndex = nextStemIndex;
        }
        addTributaryDiagnostic(draft.courseId(), sourcePoint, rejection, rejectionDetail, diagnostics);
        return null;
    }

    /**
     * The first draft point, in path order, that lies within a stem width of a stem station at or past
     * {@code minimumStemIndex} or that enters drainage the stem owns, as {draft point index, stem station
     * index}; null when the draft never meets the stem there. Among the stations the point can reach the
     * lowest water wins, then the flattest stretch, so the tributary's mouth sits at the river's level
     * where the two channels touch and never above the stem's shore.
     */
    private static int[] tributaryJunction(
            CoursePath path,
            List<HydrologyPoint> stemStations,
            List<Integer> stemWidths,
            Set<Long> stemEdges,
            int minimumStemIndex
    ) {
        for (int index = 0; index < path.points().size(); index++) {
            HydrologyPoint point = path.points().get(index);
            boolean sharedEdge = index > 0 && stemEdges.contains(path.pairEdges().get(index - 1).id());
            int best = -1;
            int bestFlatness = -1;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (int stemIndex = minimumStemIndex; stemIndex < stemStations.size(); stemIndex++) {
                double distance = point.distanceSquared2D(stemStations.get(stemIndex));
                double reach = stemWidths.get(stemIndex) + 1D;
                boolean reachable = distance <= reach * reach;
                if (!reachable && !(sharedEdge && best < 0 && distance < bestDistance)) {
                    continue;
                }
                int flatness = reachable ? stemFlatness(stemStations, stemIndex) : -1;
                int level = stemStations.get(stemIndex).y();
                int bestLevel = best < 0 ? Integer.MAX_VALUE : stemStations.get(best).y();
                if (level < bestLevel
                        || level == bestLevel && (flatness > bestFlatness || flatness == bestFlatness && distance < bestDistance)) {
                    best = stemIndex;
                    bestFlatness = flatness;
                    bestDistance = distance;
                }
            }
            if (best >= 0) {
                return new int[] {index, best};
            }
        }
        return null;
    }

    /** How many of the stem stations around {@code stemIndex} share its water level. */
    private static int stemFlatness(List<HydrologyPoint> stemStations, int stemIndex) {
        int level = stemStations.get(stemIndex).y();
        int flat = 0;
        for (int offset = -TRIBUTARY_JUNCTION_FLAT_STATIONS; offset <= TRIBUTARY_JUNCTION_FLAT_STATIONS; offset++) {
            int index = stemIndex + offset;
            if (index >= 0 && index < stemStations.size() && stemStations.get(index).y() == level) {
                flat++;
            }
        }
        return flat;
    }

    /** Raises every trailing station below {@code level} to it, so the tail is a flat pool at the stem's water. */
    private static void backwater(List<HydraulicSegment> segments, int level) {
        for (int index = segments.size() - 1; index >= 0; index--) {
            HydraulicSegment segment = segments.get(index);
            if (segment.upstreamHeadY() >= level && segment.downstreamHeadY() >= level) {
                break;
            }
            ArrayList<HydrologyPoint> centerline = new ArrayList<>(segment.centerline().size());
            for (HydrologyPoint point : segment.centerline()) {
                centerline.add(point.y() < level ? new HydrologyPoint(point.x(), level, point.z()) : point);
            }
            int upstream = Math.max(segment.upstreamHeadY(), level);
            int downstream = Math.max(segment.downstreamHeadY(), level);
            segments.set(index, new HydraulicSegment(
                    segment.id(),
                    segment.courseId(),
                    upstream == downstream ? HydrologyFeatureType.SURFACE_POOL : segment.type(),
                    upstream,
                    downstream,
                    segment.width(),
                    segment.depth(),
                    segment.fallingFluid(),
                    segment.receivingPool(),
                    List.copyOf(centerline)
            ));
            if (segment.upstreamHeadY() >= level) {
                break;
            }
        }
    }

    /**
     * Lowers the trailing stations of a tributary towards {@code level}, one block per cascade run of
     * centerline distance from the junction and never deeper than the channel may cut, so the reach
     * grades down into the stem instead of hanging above it. Where the ground holds a station up, the
     * stations upstream of it are held with it, so the graded reach still descends into the stem.
     */
    void levelApproach(List<HydraulicSegment> segments, int level) {
        int run = Math.max(1, settings.surface().banks().cascadeRun());
        int maximumIncision = settings.surface().maximumIncision();
        double distance = 0D;
        HydrologyPoint previous = null;
        int downstreamHead = Integer.MIN_VALUE;
        for (int index = segments.size() - 1; index >= 0; index--) {
            HydraulicSegment segment = segments.get(index);
            ArrayList<HydrologyPoint> centerline = new ArrayList<>(segment.centerline());
            boolean changed = false;
            boolean settled = false;
            for (int pointIndex = centerline.size() - 1; pointIndex >= 0; pointIndex--) {
                HydrologyPoint point = centerline.get(pointIndex);
                if (previous != null) {
                    distance += StrictMath.hypot(point.x() - previous.x(), point.z() - previous.z());
                }
                previous = point;
                int allowed = level + (int) StrictMath.floor(distance / run);
                if (point.y() <= allowed) {
                    settled = true;
                    break;
                }
                int floor = sampleBasisWithoutSlope(point.x(), point.z()).naturalHeight() - maximumIncision;
                int lowered = Math.min(point.y(), Math.max(Math.max(allowed, floor), downstreamHead));
                if (lowered < point.y()) {
                    centerline.set(pointIndex, new HydrologyPoint(point.x(), lowered, point.z()));
                    changed = true;
                }
                downstreamHead = lowered;
            }
            if (changed) {
                // A level reach may carry a bump above its heads (a pool's centerline is not graded);
                // once its tail is cut down the reach becomes a graded transition, which must never rise
                // downstream, so every station is held at or below the one before it.
                int ceiling = centerline.getFirst().y();
                for (int pointIndex = 1; pointIndex < centerline.size(); pointIndex++) {
                    HydrologyPoint point = centerline.get(pointIndex);
                    if (point.y() > ceiling) {
                        centerline.set(pointIndex, new HydrologyPoint(point.x(), ceiling, point.z()));
                    } else {
                        ceiling = point.y();
                    }
                }
                int upstream = centerline.getFirst().y();
                int downstream = centerline.getLast().y();
                HydrologyFeatureType type = segment.type();
                if (upstream > downstream && !type.isDrop()) {
                    type = upstream - downstream >= settings.hydraulics().waterfallMinimumDrop()
                            ? HydrologyFeatureType.WATERFALL
                            : upstream - downstream == 1 ? HydrologyFeatureType.RIFFLE : HydrologyFeatureType.CASCADE;
                } else if (upstream == downstream && type.isDrop()) {
                    type = HydrologyFeatureType.SURFACE_POOL;
                }
                segments.set(index, new HydraulicSegment(
                        segment.id(),
                        segment.courseId(),
                        type,
                        upstream,
                        downstream,
                        segment.width(),
                        segment.depth(),
                        segment.fallingFluid(),
                        segment.receivingPool(),
                        List.copyOf(centerline)
                ));
            }
            if (settled) {
                break;
            }
        }
    }

    private static int lastWidth(SurfaceCourseResult result) {
        return Math.max(1, result.lastWidth());
    }

    private record TributaryBuild(RiverCourse course, HydrologyCandidateRejection rejection, int rejectionDetail) {
        private static TributaryBuild rejected(HydrologyCandidateRejection rejection, int detail) {
            return new TributaryBuild(null, rejection, detail);
        }
    }

    private TributaryBuild buildSurfaceTributaryAt(
            SurfaceCourseDraft draft,
            int joinIndex,
            int stemIndex,
            List<HydrologyPoint> stemStations,
            List<Integer> stemWidths,
            Set<Long> stemEdges
    ) {
        CoursePath path = draft.path();
        HydrologyPoint joinStation = stemStations.get(stemIndex);
        ArrayList<HydrologyPoint> points = new ArrayList<>(path.points().subList(0, joinIndex + 1));
        HydrologyPoint last = points.getLast();
        if (last.x() != joinStation.x() || last.z() != joinStation.z()) {
            points.add(new HydrologyPoint(joinStation.x(), last.y(), joinStation.z()));
        }
        LinkedHashSet<DrainageEdge> ownedEdges = new LinkedHashSet<>();
        for (DrainageEdge edge : path.pairEdges().subList(0, joinIndex)) {
            if (!stemEdges.contains(edge.id())) {
                ownedEdges.add(edge);
            }
        }
        if (ownedEdges.isEmpty()) {
            return TributaryBuild.rejected(HydrologyCandidateRejection.COURSE_TOO_SHORT, joinIndex);
        }
        int stemHead = joinStation.y();
        SurfaceCourseResult result = tributaryCourseBuilder.build(
                worldSeed,
                draft.courseId(),
                draft.profileKey(),
                List.copyOf(points),
                SurfaceTerminal.TRIBUTARY,
                stemHead
        );
        if (!result.accepted()) {
            return TributaryBuild.rejected(result.rejection(), result.rejectionDetail());
        }
        if (path.organicSurfaceRequired()) {
            // The reach is judged without its approach into the stem: the turn a tributary makes to
            // enter its river is junction geometry, not a bend in the river itself.
            double approachRadius = 3D * lastWidth(result) + 8D;
            int judged = joinIndex;
            while (judged > 0 && path.points().get(judged).distanceSquared2D(joinStation) <= approachRadius * approachRadius) {
                judged--;
            }
            int shapeRejection = judged >= 2 ? surfaceShapeRejection(path.points().subList(0, judged + 1), true) : 0;
            if (shapeRejection != 0) {
                return TributaryBuild.rejected(HydrologyCandidateRejection.SURFACE_SHAPE_UNSUPPORTED, shapeRejection);
            }
        }
        ArrayList<HydraulicSegment> segments = new ArrayList<>(result.segments());
        // Where the two channels touch, the tributary must sit at the lowest stem water it reaches.
        double touch = stemWidths.get(stemIndex) + lastWidth(result) + 1D;
        for (int index = 0; index < stemStations.size(); index++) {
            HydrologyPoint station = stemStations.get(index);
            if (station.distanceSquared2D(joinStation) <= touch * touch) {
                stemHead = Math.min(stemHead, station.y());
            }
        }
        if (segments.getLast().downstreamHeadY() < stemHead) {
            int shortfall = stemHead - segments.getLast().downstreamHeadY();
            if (shortfall > TRIBUTARY_BACKWATER_LIMIT) {
                return TributaryBuild.rejected(HydrologyCandidateRejection.SURFACE_HEAD_RANGE, shortfall);
            }
            // The stem's water stands a little above the tributary's floor at the junction: the tail of
            // the tributary backs up to the stem's level, a short still reach the bank lip contains.
            backwater(segments, stemHead);
        } else if (segments.getLast().downstreamHeadY() > stemHead) {
            // The tributary hangs above its river: its last stretch is cut down to the stem's water,
            // a block per cascade run, so the channel meets the river at its level and not above
            // the stem's shore.
            levelApproach(segments, stemHead);
        }
        HydraulicSegment lastSegment = segments.getLast();
        HydrologyPoint end = lastSegment.end();
        if (end.x() != joinStation.x() || end.z() != joinStation.z() || lastSegment.downstreamHeadY() != stemHead) {
            // The hop onto the stem is graded like any in-course drop, so the water steps down block
            // by block into the river instead of standing above the stem's shore.
            int drop = lastSegment.downstreamHeadY() - stemHead;
            HydrologyFeatureType type = drop == 0
                    ? HydrologyFeatureType.SURFACE_POOL
                    : drop >= settings.hydraulics().waterfallMinimumDrop()
                            ? HydrologyFeatureType.WATERFALL
                            : drop == 1 ? HydrologyFeatureType.RIFFLE : HydrologyFeatureType.CASCADE;
            boolean graded = addHydraulicSegments(
                    draft.courseId(),
                    TRIBUTARY_JUNCTION_PAIR_INDEX,
                    type,
                    lastSegment.downstreamHeadY(),
                    stemHead,
                    lastSegment.width(),
                    lastSegment.depth(),
                    List.of(end, new HydrologyPoint(joinStation.x(), stemHead, joinStation.z())),
                    true,
                    segments
            );
            if (!graded) {
                return TributaryBuild.rejected(HydrologyCandidateRejection.SURFACE_DROP_UNSUPPORTED, drop);
            }
        }
        List<DrainageEdge> edges = List.copyOf(ownedEdges);
        return new TributaryBuild(new RiverCourse(
                draft.courseId(),
                RiverCourseType.SURFACE,
                OptionalLong.of(draft.source().id()),
                OptionalLong.of(path.outlet().id()),
                draft.profileKey(),
                maximumSurfaceDischarge(edges),
                edges,
                segments
        ), null, 0);
    }

    private void addTributaryDiagnostic(
            long courseId,
            HydrologyPoint sourcePoint,
            HydrologyCandidateRejection rejection,
            int detail,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        diagnostics.add(new HydrologyDiagnosticCandidate(
                HydrologyHash.mix(courseId, DIAGNOSTIC_SALT, rejection.ordinal(), 0x5452494255544152L),
                HydrologyCandidateKind.TRIBUTARY,
                HydrologyFeatureType.SURFACE_POOL,
                sourcePoint,
                rejection,
                detail
        ));
    }

    private RiverCourse preferredTrunkCourse(RiverCourse first, RiverCourse second) {
        double firstLength = edgeLength(first.drainageEdges(), first.drainageEdges().size());
        double secondLength = edgeLength(second.drainageEdges(), second.drainageEdges().size());
        if (firstLength != secondLength) {
            return firstLength > secondLength ? first : second;
        }
        if (first.discharge() != second.discharge()) {
            return first.discharge() > second.discharge() ? first : second;
        }
        return Long.compareUnsigned(first.id(), second.id()) <= 0 ? first : second;
    }

    private double edgeLength(List<DrainageEdge> edges, int edgeCount) {
        double length = 0D;
        for (int edgeIndex = 0; edgeIndex < edgeCount; edgeIndex++) {
            List<HydrologyPoint> centerline = edges.get(edgeIndex).centerline();
            for (int pointIndex = 1; pointIndex < centerline.size(); pointIndex++) {
                HydrologyPoint previous = centerline.get(pointIndex - 1);
                HydrologyPoint current = centerline.get(pointIndex);
                length += StrictMath.hypot(current.x() - previous.x(), current.z() - previous.z());
            }
        }
        return length;
    }

    private int retainedSegmentCount(List<HydraulicSegment> segments, HydrologyPoint boundary) {
        for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
            HydrologyPoint end = segments.get(segmentIndex).centerline().getLast();
            if (end.x() == boundary.x() && end.z() == boundary.z()) {
                return segmentIndex + 1;
            }
        }
        return 0;
    }

    private RiverCourse preferredOutletContinuation(RiverCourse first, RiverCourse second) {
        boolean firstSurfaceSinkhole = first.surfaceSinkholeContinuation();
        boolean secondSurfaceSinkhole = second.surfaceSinkholeContinuation();
        if (firstSurfaceSinkhole != secondSurfaceSinkhole) {
            return firstSurfaceSinkhole ? first : second;
        }
        return Long.compareUnsigned(first.id(), second.id()) <= 0 ? first : second;
    }

    private boolean hasInlandOutletContinuation(RiverCourse course) {
        return course.surfaceSinkholeContinuation()
                || course.segments().getLast().type() == HydrologyFeatureType.INLAND_GROTTO;
    }

    private boolean hasRoutedSourceSearch() {
        HydrologyPlannerSettings.Source surface = settings.surface().sources();
        if (settings.surface().enabled() && surface.enabled()) {
            return true;
        }
        HydrologyPlannerSettings.Source underground = settings.underground().sources();
        return settings.underground().enabled()
                && underground.enabled();
    }

    private List<HydrologyDiagnosticCandidate> uniqueDiagnostics(
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        LinkedHashMap<Long, HydrologyDiagnosticCandidate> unique = new LinkedHashMap<>();
        for (HydrologyDiagnosticCandidate diagnostic : diagnostics) {
            unique.putIfAbsent(diagnostic.id(), diagnostic);
        }
        return List.copyOf(unique.values());
    }

    private SampledGrid sample(HydrologyTileKey key) {
        HydrologyPlannerSettings.Routing routing = settings.routing();
        int ownerMinimumX = key.minimumBlockX(routing.tileSize());
        int ownerMinimumZ = key.minimumBlockZ(routing.tileSize());
        int halo = Math.min(
                routing.maximumRouteLength(),
                Math.multiplyExact(routing.sampleSpacing(), 2)
        );
        int alignedHalo = Math.floorDiv(halo, routing.sampleSpacing()) * routing.sampleSpacing();
        int minimumX = ownerMinimumX - alignedHalo;
        int minimumZ = ownerMinimumZ - alignedHalo;
        int width = (routing.tileSize() + alignedHalo * 2) / routing.sampleSpacing() + 1;
        int nodeCount = Math.multiplyExact(width, width);
        HydrologyRoutingTerrainSampler.GridRequest gridRequest = new HydrologyRoutingTerrainSampler.GridRequest(
                minimumX,
                minimumZ,
                width,
                routing.sampleSpacing()
        );
        HydrologyTerrainSample[] terrainSamples = Objects.requireNonNull(
                routingSampler.sampleGrid(gridRequest),
                "Hydrology routing terrain sampler returned null"
        );
        if (terrainSamples.length != nodeCount) {
            throw new IllegalStateException(
                    "Hydrology routing terrain sampler returned " + terrainSamples.length
                            + " samples for a " + width + " by " + width + " grid"
            );
        }
        ArrayList<GridNode> nodes = new ArrayList<>(nodeCount);
        for (int gridZ = 0; gridZ < width; gridZ++) {
            int z = minimumZ + gridZ * routing.sampleSpacing();
            for (int gridX = 0; gridX < width; gridX++) {
                int x = minimumX + gridX * routing.sampleSpacing();
                int index = gridZ * width + gridX;
                HydrologyTerrainSample terrain = Objects.requireNonNull(
                        terrainSamples[index],
                        "Hydrology routing terrain sampler returned null at " + x + "," + z
                );
                long id = HydrologyHash.mix(worldSeed, NODE_SALT, x, z);
                nodes.add(new GridNode(index, gridX, gridZ, x, z, id, terrain));
            }
        }
        return new SampledGrid(
                minimumX,
                minimumZ,
                ownerMinimumX,
                ownerMinimumZ,
                routing.tileSize(),
                width,
                routing.sampleSpacing(),
                List.copyOf(nodes)
        );
    }

    private List<OutletCandidate> resolveOutlets(
            HydrologyTileKey key,
            SampledGrid grid,
            boolean surface,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        ArrayList<OutletCandidate> oceanCandidates = oceanOutletCandidates(grid, surface);
        if (!surface && settings.outlets().inlandGrotto().enabled()) {
            addOutletLevelDiagnostics(oceanCandidates, diagnostics);
        }
        sortOutletCandidates(key, grid, surface, oceanCandidates);
        // Sea outlets and inland outlets are budgeted separately: a coast never starves the
        // sinkholes behind it and a sinkhole never displaces a mouth.
        List<OutletCandidate> selectedOcean = limitOutletsByType(
                oceanCandidates,
                settings.outlets().maximumCoastalPerTile()
        );
        addRejectedOutlets(oceanCandidates, selectedOcean, diagnostics);
        if (!settings.outlets().inlandGrotto().enabled()) {
            return selectedOcean;
        }
        boolean[] oceanReachable = outletReachable(grid, oceanCandidates, surface);
        ArrayList<OutletCandidate> inlandCandidates = inlandOutletCandidates(grid, oceanReachable);
        sortInlandOutletCandidates(grid, inlandCandidates);
        List<OutletCandidate> selectedInland = styledInlandOutlets(
                grid,
                limitOutlets(inlandCandidates, settings.outlets().maximumPerTile()),
                surface
        );
        addRejectedOutlets(inlandCandidates, selectedInland, diagnostics);
        ArrayList<OutletCandidate> selected = new ArrayList<>(selectedOcean.size() + selectedInland.size());
        selected.addAll(selectedOcean);
        selected.addAll(selectedInland);
        return List.copyOf(selected);
    }

    private ArrayList<OutletCandidate> oceanOutletCandidates(SampledGrid grid, boolean surface) {
        ArrayList<OutletCandidate> oceanCandidates = new ArrayList<>();
        for (GridNode land : grid.nodes()) {
            if (land.terrain().ocean() || !land.terrain().transitAllowed() || !land.terrain().outletAllowed()) {
                continue;
            }
            GridNode ocean = firstOceanNeighbor(grid, land);
            if (ocean == null || !land.terrain().drainsInto(ocean.terrain())) {
                // A confined shore only reaches a sea that belongs to its own area.
                continue;
            }
            HydrologyOceanBoundaryRefiner.Result boundary = refineOceanBoundary(land, ocean);
            if (boundary == null
                    || !boundary.landwardTerrain().transitAllowed()
                    || !boundary.landwardTerrain().outletAllowed()) {
                continue;
            }
            HydrologyFeatureType type = coastalOutletType(boundary.landwardTerrain());
            if (type == null) {
                continue;
            }
            long outletId = HydrologyHash.mix(worldSeed, OUTLET_SALT, land.id(), ocean.id(), type.ordinal());
            int seaLevel = settingsSeaLevel(ocean.terrain());
            RiverOutlet outlet = new RiverOutlet(
                    outletId,
                    type,
                    land.id(),
                    withY(boundary.landwardPoint(), seaLevel),
                    withY(boundary.oceanPoint(), seaLevel),
                    seaLevel,
                    true
            );
            oceanCandidates.add(new OutletCandidate(land.index(), ocean.index(), outlet));
        }
        return oceanCandidates;
    }

    private List<OutletCandidate> resolveSurfaceFallbackOutlets(SampledGrid grid) {
        ArrayList<OutletCandidate> oceanCandidates = oceanOutletCandidates(grid, true);
        ArrayList<OutletCandidate> inlandCandidates = new ArrayList<>();
        if (settings.outlets().surfaceSinkholesEnabled()
                && settings.outlets().inlandGrotto().enabled()) {
            inlandCandidates.addAll(styledInlandOutlets(
                    grid,
                    inlandOutletCandidates(grid, new boolean[grid.nodes().size()]),
                    true
            ));
        }
        if (oceanCandidates.isEmpty() && inlandCandidates.isEmpty()) {
            return List.of();
        }
        sortSurfaceFallbackOutletCandidates(grid, oceanCandidates);
        sortSurfaceFallbackOutletCandidates(grid, inlandCandidates);
        // Trials alternate sea and inland outlets, sea first: a coast with more mouths than trials
        // still gives a sinkhole its turn, and a rejected mouth is retried at the next coast.
        ArrayList<OutletCandidate> candidates = new ArrayList<>(oceanCandidates.size() + inlandCandidates.size());
        for (int index = 0; index < Math.max(oceanCandidates.size(), inlandCandidates.size()); index++) {
            if (index < oceanCandidates.size()) {
                candidates.add(oceanCandidates.get(index));
            }
            if (index < inlandCandidates.size()) {
                candidates.add(inlandCandidates.get(index));
            }
        }
        int maximumTrials = Math.max(
                OPTIONAL_SOURCE_REJECTIONS_PER_TARGET,
                Math.multiplyExact(
                        Math.max(settings.outlets().maximumPerTile(), settings.outlets().maximumCoastalPerTile()),
                        16
                )
        );
        maximumTrials = Math.min(16, maximumTrials);
        return limitOutlets(candidates, maximumTrials);
    }

    private ArrayList<OutletCandidate> inlandOutletCandidates(
            SampledGrid grid,
            boolean[] oceanReachable
    ) {
        ArrayList<OutletCandidate> inlandCandidates = new ArrayList<>();
        for (GridNode node : grid.nodes()) {
            if (!grid.owns(node.x(), node.z())
                    || node.terrain().ocean() || !node.terrain().caveAvailable()
                    || !node.terrain().transitAllowed() || !node.terrain().outletAllowed()
                    || oceanReachable[node.index()]) {
                continue;
            }
            InlandConnection connection = inlandConnection(node);
            int poolY = clamp(
                    connection.terrain().caveFluidY(),
                    settings.underground().minimumFluidY(),
                    settings.underground().maximumFluidY()
            );
            if (settings.outlets().surfaceSinkholesEnabled()
                    && node.terrain().naturalHeight() - settings.surface().banks().sink() <= poolY) {
                continue;
            }
            long outletId = HydrologyHash.mix(
                    worldSeed,
                    OUTLET_SALT,
                    node.id(),
                    connection.point().x(),
                    connection.point().z(),
                    HydrologyFeatureType.INLAND_GROTTO.ordinal()
            );
            RiverOutlet outlet = new RiverOutlet(
                    outletId,
                    HydrologyFeatureType.INLAND_GROTTO,
                    node.id(),
                    new HydrologyPoint(node.x(), node.terrain().naturalHeight(), node.z()),
                    withY(connection.point(), poolY),
                    settingsSeaLevel(node.terrain()),
                    false
            );
            inlandCandidates.add(new OutletCandidate(node.index(), -1, outlet));
        }
        return inlandCandidates;
    }

    private void sortInlandOutletCandidates(
            SampledGrid grid,
            List<OutletCandidate> inlandCandidates
    ) {
        inlandCandidates.sort(Comparator
                .comparing((OutletCandidate candidate) -> !isDrainageBasin(grid, candidate.landIndex()))
                .thenComparingInt((OutletCandidate candidate) -> grid.node(candidate.landIndex()).terrain().naturalHeight())
                .thenComparingLong((OutletCandidate candidate) -> candidate.outlet().id()));
    }

    private void sortSurfaceFallbackOutletCandidates(
            SampledGrid grid,
            List<OutletCandidate> candidates
    ) {
        HashMap<Integer, Long> capacities = new HashMap<>(candidates.size());
        for (OutletCandidate candidate : candidates) {
            capacities.put(candidate.landIndex(), surfaceOutletCapacity(grid, candidate.landIndex()));
        }
        candidates.sort(Comparator
                .comparing((OutletCandidate candidate) -> !isDrainageBasin(grid, candidate.landIndex()))
                .thenComparingInt((OutletCandidate candidate) ->
                        capacities.get(candidate.landIndex()) > 0L ? 0 : 1)
                .thenComparingInt((OutletCandidate candidate) -> ownedOutletRank(grid, candidate))
                .thenComparing(Comparator
                        .comparingLong((OutletCandidate candidate) -> capacities.get(candidate.landIndex()))
                        .reversed())
                .thenComparingInt((OutletCandidate candidate) ->
                        grid.node(candidate.landIndex()).terrain().naturalHeight())
                .thenComparingLong((OutletCandidate candidate) -> candidate.outlet().id()));
    }

    private boolean isDrainageBasin(SampledGrid grid, int nodeIndex) {
        GridNode node = grid.node(nodeIndex);
        int height = node.terrain().naturalHeight();
        for (GridOffset offset : ROUTING_OFFSETS) {
            GridNode neighbor = grid.nodeAt(node.gridX() + offset.x(), node.gridZ() + offset.z());
            if (neighbor != null
                    && !neighbor.terrain().ocean()
                    && neighbor.terrain().transitAllowed()
                    && neighbor.terrain().naturalHeight() < height) {
                return false;
            }
        }
        return true;
    }

    private boolean[] outletReachable(SampledGrid grid, List<OutletCandidate> outlets, boolean surface) {
        int maximumRise = surface ? maximumSurfaceEdgeRise() : Integer.MAX_VALUE;
        boolean[] reachable = new boolean[grid.nodes().size()];
        int[] queue = new int[reachable.length];
        int readIndex = 0;
        int writeIndex = 0;
        for (OutletCandidate outlet : outlets) {
            if (reachable[outlet.landIndex()]) {
                continue;
            }
            reachable[outlet.landIndex()] = true;
            queue[writeIndex++] = outlet.landIndex();
        }
        while (readIndex < writeIndex) {
            GridNode node = grid.node(queue[readIndex++]);
            for (GridOffset offset : ROUTING_OFFSETS) {
                GridNode neighbor = grid.nodeAt(node.gridX() + offset.x(), node.gridZ() + offset.z());
                if (neighbor == null || reachable[neighbor.index()] || neighbor.terrain().ocean()
                        || !neighbor.terrain().transitAllowed()
                        || node.terrain().naturalHeight() - neighbor.terrain().naturalHeight() > maximumRise) {
                    continue;
                }
                reachable[neighbor.index()] = true;
                queue[writeIndex++] = neighbor.index();
            }
        }
        return reachable;
    }

    private List<OutletCandidate> styledInlandOutlets(
            SampledGrid grid,
            List<OutletCandidate> candidates,
            boolean surface
    ) {
        ArrayList<OutletCandidate> styled = new ArrayList<>(candidates.size());
        for (OutletCandidate candidate : candidates) {
            GridNode node = grid.node(candidate.landIndex());
            RiverOutlet outlet = candidate.outlet();
            HydrologyPoint landward = routeAnchor(node);
            HydrologyTerrainSample landwardTerrain = Objects.requireNonNull(
                    sampleDetailed(landward.x(), landward.z()),
                    "Hydrology inland outlet anchor left sampled terrain"
            );
            int connectionOffsetX = Integer.compare(outlet.connectionPoint().x(), outlet.landwardPoint().x());
            int connectionOffsetZ = Integer.compare(outlet.connectionPoint().z(), outlet.landwardPoint().z());
            HydrologyPoint connection = new HydrologyPoint(
                    landward.x() + connectionOffsetX,
                    outlet.connectionPoint().y(),
                    landward.z() + connectionOffsetZ
            );
            int poolY = sampleGeometry(
                    HydrologyGeometrySampler.Field.UNDERGROUND_FLUID_LEVEL,
                    "",
                    connection.x(),
                    connection.z(),
                    0L,
                    settings.underground().minimumFluidY(),
                    settings.underground().maximumFluidY()
            );
            if (surface && settings.outlets().surfaceSinkholesEnabled()) {
                poolY = Math.min(
                        poolY,
                        landwardTerrain.naturalHeight() - settings.surface().banks().sink() - 1
                );
                poolY = Math.max(
                        poolY,
                        landwardTerrain.naturalHeight() - settings.routing().sampleSpacing()
                );
            }
            styled.add(new OutletCandidate(
                    candidate.landIndex(),
                    candidate.oceanIndex(),
                    new RiverOutlet(
                            outlet.id(),
                            outlet.type(),
                            outlet.drainageNodeId(),
                            withY(landward, landwardTerrain.naturalHeight()),
                            withY(connection, poolY),
                            outlet.seaLevel(),
                            outlet.directOcean()
                    )
            ));
        }
        return List.copyOf(styled);
    }

    private InlandConnection inlandConnection(GridNode node) {
        GridOffset offset = CARDINAL_OFFSETS.getFirst();
        long selectedTie = inlandConnectionTie(node, offset);
        for (int index = 1; index < CARDINAL_OFFSETS.size(); index++) {
            GridOffset candidate = CARDINAL_OFFSETS.get(index);
            long candidateTie = inlandConnectionTie(node, candidate);
            if (candidateTie < selectedTie) {
                offset = candidate;
                selectedTie = candidateTie;
            }
        }
        HydrologyTerrainSample terrain = node.terrain();
        return new InlandConnection(
                new HydrologyPoint(
                        node.x() + offset.x(),
                        terrain.caveFluidY(),
                        node.z() + offset.z()
                ),
                terrain
        );
    }

    private long inlandConnectionTie(GridNode node, GridOffset offset) {
        return HydrologyHash.mix(
                worldSeed,
                OUTLET_SALT,
                node.id(),
                node.x() + offset.x(),
                node.z() + offset.z()
        );
    }

    private GridNode firstOceanNeighbor(SampledGrid grid, GridNode land) {
        ArrayList<GridNode> oceans = new ArrayList<>();
        for (GridOffset offset : CARDINAL_OFFSETS) {
            GridNode neighbor = grid.nodeAt(land.gridX() + offset.x(), land.gridZ() + offset.z());
            if (neighbor != null && neighbor.terrain().ocean()) {
                oceans.add(neighbor);
            }
        }
        oceans.sort(Comparator.comparingLong(GridNode::id));
        return oceans.isEmpty() ? null : oceans.getFirst();
    }

    private HydrologyOceanBoundaryRefiner.Result refineOceanBoundary(GridNode land, GridNode ocean) {
        List<HydrologyPoint> crossing = line(land.naturalPoint(), ocean.naturalPoint(), 1);
        return HydrologyOceanBoundaryRefiner.refine(crossing, sampler, routingSampler);
    }

    private HydrologyFeatureType coastalOutletType(HydrologyTerrainSample land) {
        HydrologyPlannerSettings.Outlets outlets = settings.outlets();
        // A zero slope factor turns the slope rule off, so only the coast's height over the sea decides.
        boolean cliff = land.naturalHeight() - settingsSeaLevel(land)
                >= outlets.coastalCliffMinimumHeight()
                || outlets.coastalCliffSlopeFactor() > 0D
                && land.slope() >= outlets.coastalCliffMinimumHeight() * outlets.coastalCliffSlopeFactor();
        if (cliff && outlets.coastalGrotto().enabled()) {
            return HydrologyFeatureType.COASTAL_GROTTO;
        }
        return outlets.oceanEnabled() ? HydrologyFeatureType.MOUTH : null;
    }

    private Comparator<OutletCandidate> outletComparator(HydrologyTileKey key) {
        long tileHash = HydrologyHash.mix(worldSeed, key.tileX(), key.tileZ(), OUTLET_SALT);
        return Comparator.comparingLong((OutletCandidate candidate) -> HydrologyHash.mix(tileHash, candidate.outlet().id()));
    }

    private void sortOutletCandidates(
            HydrologyTileKey key,
            SampledGrid grid,
            boolean surface,
            List<OutletCandidate> candidates
    ) {
        // An owned coast outranks a neighbour's: a halo mouth only takes a slot when no owned
        // coast can serve the tile's sources.
        Comparator<OutletCandidate> owned = Comparator.comparingInt(
                (OutletCandidate candidate) -> ownedOutletRank(grid, candidate));
        if (!surface) {
            candidates.sort(owned.thenComparing(outletComparator(key)));
            return;
        }
        HashMap<Integer, Long> capacities = new HashMap<>(candidates.size());
        for (OutletCandidate candidate : candidates) {
            capacities.put(candidate.landIndex(), surfaceOutletCapacity(grid, candidate.landIndex()));
        }
        Comparator<OutletCandidate> stable = outletComparator(key);
        candidates.sort(Comparator
                .comparingInt((OutletCandidate candidate) ->
                        capacities.get(candidate.landIndex()) > 0L ? 0 : 1)
                .thenComparing(owned)
                .thenComparingInt(this::surfaceOutletPriority)
                .thenComparing(Comparator
                        .comparingLong((OutletCandidate candidate) -> capacities.get(candidate.landIndex()))
                        .reversed())
                .thenComparing(stable));
    }

    private int ownedOutletRank(SampledGrid grid, OutletCandidate candidate) {
        GridNode land = grid.node(candidate.landIndex());
        return grid.owns(land.x(), land.z()) ? 0 : 1;
    }

    private int surfaceOutletPriority(OutletCandidate candidate) {
        return switch (candidate.outlet().type()) {
            case MOUTH -> 0;
            case COASTAL_GROTTO -> 1;
            case INLAND_GROTTO -> 2;
            default -> 3;
        };
    }

    private long surfaceOutletCapacity(SampledGrid grid, int outletNodeIndex) {
        boolean[] visited = new boolean[grid.nodes().size()];
        int[] distances = new int[grid.nodes().size()];
        Arrays.fill(distances, -1);
        int[] queue = new int[visited.length];
        int readIndex = 0;
        int writeIndex = 0;
        visited[outletNodeIndex] = true;
        distances[outletNodeIndex] = 0;
        queue[writeIndex++] = outletNodeIndex;
        long capacity = 0L;
        while (readIndex < writeIndex) {
            GridNode downstream = grid.node(queue[readIndex++]);
            if (grid.owns(downstream.x(), downstream.z())
                    && downstream.terrain().surfaceSourceAllowed()
                    && downstream.terrain().naturalHeight() >= settings.surface().sources().minimumElevation()
                    && distances[downstream.index()]
                    >= settings.routing().minimumSurfaceCourseLength()) {
                capacity = Math.addExact(
                        capacity,
                        1_000_000L
                                + distances[downstream.index()]
                                + Math.max(0, downstream.terrain().naturalHeight() - settings.seaLevel())
                );
            }
            for (GridOffset offset : ROUTING_OFFSETS) {
                GridNode upstream = grid.nodeAt(
                        downstream.gridX() + offset.x(),
                        downstream.gridZ() + offset.z()
                );
                if (upstream == null
                        || visited[upstream.index()]
                        || upstream.terrain().ocean()
                        || !upstream.terrain().transitAllowed()) {
                    continue;
                }
                visited[upstream.index()] = true;
                distances[upstream.index()] = Math.addExact(
                        distances[downstream.index()],
                        settings.routing().sampleSpacing()
                );
                queue[writeIndex++] = upstream.index();
            }
        }
        return capacity;
    }

    private List<OutletCandidate> limitOutlets(List<OutletCandidate> candidates, int maximum) {
        if (maximum <= 0 || candidates.isEmpty()) {
            return List.of();
        }
        ArrayList<OutletCandidate> selected = new ArrayList<>(Math.min(maximum, candidates.size()));
        long minimumSpacingSquared = outletSpacingSquared();
        for (OutletCandidate candidate : candidates) {
            if (!withinOutletSpacing(candidate, selected, minimumSpacingSquared)) {
                selected.add(candidate);
                if (selected.size() == maximum) {
                    break;
                }
            }
        }
        if (selected.isEmpty() && !candidates.isEmpty()) {
            selected.add(candidates.getFirst());
        }
        return List.copyOf(selected);
    }

    /**
     * Takes sea outlets in turns by type (the best mouth, the best coastal grotto, the next mouth, ...)
     * from a sorted candidate list until the budget is spent or no type has a candidate left that
     * keeps the outlet spacing. A coast with both cliffs and beaches gets both kinds of opening.
     */
    private List<OutletCandidate> limitOutletsByType(List<OutletCandidate> candidates, int maximum) {
        if (maximum <= 0 || candidates.isEmpty()) {
            return List.of();
        }
        ArrayList<OutletCandidate> selected = new ArrayList<>(Math.min(maximum, candidates.size()));
        long minimumSpacingSquared = outletSpacingSquared();
        boolean[] consumed = new boolean[candidates.size()];
        boolean progressed = true;
        while (selected.size() < maximum && progressed) {
            progressed = false;
            for (HydrologyFeatureType type : COASTAL_OUTLET_TYPES) {
                for (int index = 0; index < candidates.size(); index++) {
                    OutletCandidate candidate = candidates.get(index);
                    if (consumed[index] || candidate.outlet().type() != type) {
                        continue;
                    }
                    consumed[index] = true;
                    if (withinOutletSpacing(candidate, selected, minimumSpacingSquared)) {
                        continue;
                    }
                    selected.add(candidate);
                    progressed = true;
                    break;
                }
                if (selected.size() == maximum) {
                    break;
                }
            }
        }
        return List.copyOf(selected);
    }

    private long outletSpacingSquared() {
        int minimumSpacing = settings.routing().sampleSpacing() * 2;
        return (long) minimumSpacing * minimumSpacing;
    }

    private boolean withinOutletSpacing(
            OutletCandidate candidate,
            List<OutletCandidate> selected,
            long minimumSpacingSquared
    ) {
        for (OutletCandidate existing : selected) {
            if (candidate.outlet().landwardPoint().distanceSquared2D(existing.outlet().landwardPoint())
                    < minimumSpacingSquared) {
                return true;
            }
        }
        return false;
    }

    private void addRejectedOutlets(
            List<OutletCandidate> candidates,
            List<OutletCandidate> selected,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        for (OutletCandidate candidate : candidates) {
            boolean accepted = false;
            for (OutletCandidate acceptedCandidate : selected) {
                if (candidate.outlet().id() == acceptedCandidate.outlet().id()) {
                    accepted = true;
                    break;
                }
            }
            if (!accepted) {
                diagnostics.add(new HydrologyDiagnosticCandidate(
                        HydrologyHash.mix(candidate.outlet().id(), DIAGNOSTIC_SALT),
                        HydrologyCandidateKind.OUTLET,
                        candidate.outlet().type(),
                        candidate.outlet().landwardPoint(),
                        HydrologyCandidateRejection.OUTLET_LIMIT, 0
                ));
            }
        }
    }

    /**
     * Drops the sea outlets an underground river cannot reach because the sea sits above the
     * underground fluid ceiling, reporting each as OUTLET_LEVEL with the shortfall as the detail.
     */
    private void addOutletLevelDiagnostics(
            List<OutletCandidate> candidates,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        int maximumFluidY = settings.underground().maximumFluidY();
        ArrayList<OutletCandidate> retained = new ArrayList<>(candidates.size());
        for (OutletCandidate candidate : candidates) {
            int seaY = candidate.outlet().connectionPoint().y();
            if (seaY <= maximumFluidY) {
                retained.add(candidate);
                continue;
            }
            diagnostics.add(new HydrologyDiagnosticCandidate(
                    HydrologyHash.mix(
                            candidate.outlet().id(),
                            DIAGNOSTIC_SALT,
                            HydrologyCandidateRejection.OUTLET_LEVEL.ordinal()
                    ),
                    HydrologyCandidateKind.OUTLET,
                    candidate.outlet().type(),
                    candidate.outlet().landwardPoint(),
                    HydrologyCandidateRejection.OUTLET_LEVEL,
                    seaY - maximumFluidY
            ));
        }
        candidates.clear();
        candidates.addAll(retained);
    }

    private RoutingPlan buildRouting(
            SampledGrid grid,
            List<OutletCandidate> outlets,
            boolean surface
    ) {
        // A surface river never climbs: any rise along its route becomes a cut of at least that rise,
        // so lattice edges that rise more than a small saddle are not drainage at all.
        int maximumRise = surface ? maximumSurfaceEdgeRise() : Integer.MAX_VALUE;
        int nodeCount = grid.nodes().size();
        double[] potential = new double[nodeCount];
        int[] parent = new int[nodeCount];
        int[] outletIndex = new int[nodeCount];
        int[] minimumNeighborHeights = new int[nodeCount];
        for (GridNode node : grid.nodes()) {
            minimumNeighborHeights[node.index()] = minimumNeighborHeight(grid, node);
        }
        Arrays.fill(potential, Double.POSITIVE_INFINITY);
        Arrays.fill(parent, -1);
        Arrays.fill(outletIndex, -1);
        PriorityQueue<RoutingQueueEntry> queue = new PriorityQueue<>();
        for (int index = 0; index < outlets.size(); index++) {
            OutletCandidate outlet = outlets.get(index);
            potential[outlet.landIndex()] = 0D;
            outletIndex[outlet.landIndex()] = index;
            queue.add(new RoutingQueueEntry(outlet.landIndex(), 0D, index));
        }
        while (!queue.isEmpty()) {
            RoutingQueueEntry currentEntry = queue.poll();
            if (currentEntry.potential() != potential[currentEntry.nodeIndex()]
                    || currentEntry.outletIndex() != outletIndex[currentEntry.nodeIndex()]) {
                continue;
            }
            GridNode downstream = grid.node(currentEntry.nodeIndex());
            for (GridOffset offset : ROUTING_OFFSETS) {
                GridNode upstream = grid.nodeAt(downstream.gridX() + offset.x(), downstream.gridZ() + offset.z());
                if (upstream == null || upstream.terrain().ocean() || !upstream.terrain().transitAllowed()
                        || !upstream.terrain().drainsInto(downstream.terrain())) {
                    // Confined ground only drains into its own area, so every course that starts or
                    // arrives there keeps to that area up to and including its outlet.
                    continue;
                }
                if (downstream.terrain().naturalHeight() - upstream.terrain().naturalHeight() > maximumRise) {
                    continue;
                }
                double edgeCost = routeCost(
                        upstream,
                        downstream,
                        offset,
                        minimumNeighborHeights[downstream.index()]
                );
                double candidatePotential = currentEntry.potential() + edgeCost;
                int existingParent = parent[upstream.index()];
                boolean replace = candidatePotential < potential[upstream.index()] - 1.0E-9D;
                if (!replace && StrictMath.abs(candidatePotential - potential[upstream.index()]) <= 1.0E-9D) {
                    replace = existingParent < 0 || downstream.id() < grid.node(existingParent).id();
                }
                if (!replace) {
                    continue;
                }
                potential[upstream.index()] = candidatePotential;
                parent[upstream.index()] = downstream.index();
                outletIndex[upstream.index()] = currentEntry.outletIndex();
                queue.add(new RoutingQueueEntry(
                        upstream.index(),
                        candidatePotential,
                        currentEntry.outletIndex()
                ));
            }
        }
        return new RoutingPlan(
                potential,
                parent,
                outletIndex,
                routeLengths(parent, grid.width(), settings.routing()),
                List.copyOf(outlets),
                false
        );
    }

    /**
     * Largest rise a surface drainage edge may take. Heads only fall, so a rise along a route becomes a cut
     * of at least that rise; the lattice may climb no more than the cut the valley solver still accepts.
     */
    private int maximumSurfaceEdgeRise() {
        int permitted = settings.surface().maximumIncision() - settings.surface().banks().sink() - settings.surface().minimumDepth();
        return Math.max(1, permitted);
    }

    private RoutingPlan requireOrganicSurface(RoutingPlan routing) {
        return new RoutingPlan(
                routing.potential(),
                routing.parent(),
                routing.outletIndex(),
                routing.routeLengths(),
                routing.outlets(),
                true
        );
    }

    private int permittedSurfaceIncision(HydrologyTerrainSample terrain) {
        return permittedSurfaceIncision(
                settings.surface().maximumIncision(),
                terrain.incisionMultiplier()
        );
    }

    static int permittedSurfaceIncision(int maximumIncision, double incisionMultiplier) {
        return Math.min(
                maximumIncision,
                (int) StrictMath.floor(maximumIncision * incisionMultiplier)
        );
    }

    private double routeCost(
            GridNode upstream,
            GridNode downstream,
            GridOffset offset,
            int valleyFloor
    ) {
        HydrologyPlannerSettings.Routing routing = settings.routing();
        double baseDistance = routing.sampleSpacing() * (offset.x() != 0 && offset.z() != 0 ? StrictMath.sqrt(2D) : 1D);
        double downstreamRise = Math.max(0D, downstream.terrain().naturalHeight() - upstream.terrain().naturalHeight());
        double valleyCost = Math.max(0D, downstream.terrain().naturalHeight() - valleyFloor) * routing.valleyPreference();
        double policyCost = upstream.terrain().routingCost() + downstream.terrain().routingCost();
        double policyMultiplier = Math.max(0.01D,
                (upstream.terrain().routingMultiplier() + downstream.terrain().routingMultiplier()) * 0.5D);
        double slopeCost = (upstream.terrain().slope() + downstream.terrain().slope())
                * 0.5D * routing.slopePenalty();
        double attractionDiscount = routing.confluenceAttraction()
                * Math.max(0D, upstream.terrain().naturalHeight() - downstream.terrain().naturalHeight());
        return Math.max(0.001D,
                (baseDistance + downstreamRise * routing.uphillPenalty() + valleyCost + slopeCost + policyCost)
                        * policyMultiplier - attractionDiscount);
    }

    private int minimumNeighborHeight(SampledGrid grid, GridNode node) {
        int minimum = node.terrain().naturalHeight();
        for (GridOffset offset : ROUTING_OFFSETS) {
            GridNode neighbor = grid.nodeAt(node.gridX() + offset.x(), node.gridZ() + offset.z());
            if (neighbor != null && !neighbor.terrain().ocean()) {
                minimum = Math.min(minimum, neighbor.terrain().naturalHeight());
            }
        }
        return minimum;
    }

    private SourceSelection selectSources(
            HydrologyTileKey key,
            SampledGrid grid,
            RoutingPlan routing,
            boolean surface,
            boolean enforceGlobalSpacing,
            List<HydrologyDiagnosticCandidate> diagnostics,
            Map<HydrologyTileKey, SourceRoutingContext> routingContexts
    ) {
        HydrologyPlannerSettings.Source sourceSettings = surface
                ? settings.surface().sources()
                : settings.underground().sources();
        if (!sourceSettings.enabled()) {
            return SourceSelection.empty(surface);
        }
        long sourceSalt = surface ? SURFACE_SOURCE_SALT : UNDERGROUND_SOURCE_SALT;
        ArrayList<SourceCandidate> candidates = new ArrayList<>();
        boolean hasRequiredCandidate = false;
        for (GridNode node : grid.nodes()) {
            if (!grid.owns(node.x(), node.z())) {
                continue;
            }
            HydrologyTerrainSample terrain = node.terrain();
            boolean allowed = surface ? terrain.surfaceSourceAllowed() : terrain.undergroundSourceAllowed();
            boolean required = surface ? terrain.surfaceSourceRequired() : terrain.undergroundSourceRequired();
            if (!allowed || surface && terrain.naturalHeight() < sourceSettings.minimumElevation()) {
                continue;
            }
            double weight = surface ? terrain.surfaceSourceWeight() : terrain.undergroundSourceWeight();
            if (weight <= 0D && !required) {
                continue;
            }
            long stable = sourceStableId(sourceSalt, node.x(), node.z());
            if (routing.outlets().isEmpty()) {
                addSourceDiagnostic(node, surface, stable, HydrologyCandidateRejection.NO_LEGAL_OUTLET, diagnostics);
                continue;
            }
            if (!Double.isFinite(routing.potential()[node.index()]) || routing.parent()[node.index()] < 0) {
                addSourceDiagnostic(node, surface, stable, terrain.confinesKey() != null
                        ? HydrologyCandidateRejection.CONFINED_NO_OUTLET
                        : HydrologyCandidateRejection.NO_DRAINAGE_PATH, diagnostics);
                continue;
            }
            if (!sourceOutletAllowed(node, routing, surface)) {
                addSourceDiagnostic(node, surface, stable, HydrologyCandidateRejection.NO_LEGAL_OUTLET, diagnostics);
                continue;
            }
            if (!surface && settings.underground().connectToExistingCaves() && !terrain.caveAvailable()) {
                addSourceDiagnostic(node, false, stable, HydrologyCandidateRejection.CAVE_CONTAINMENT, diagnostics);
                continue;
            }
            int routeLength = routing.routeLengths()[node.index()];
            if (routeLength <= 0 || routeLength > settings.routing().maximumRouteLength()) {
                addSourceDiagnostic(node, surface, stable, HydrologyCandidateRejection.ROUTE_LIMIT, diagnostics);
                continue;
            }
            if (routeLength < settings.routing().minimumCourseLength(surface)) {
                addSourceDiagnostic(node, surface, stable, HydrologyCandidateRejection.COURSE_TOO_SHORT, diagnostics);
                continue;
            }
            // Elevation and route length share the same scale: 1000 route blocks count as 100 blocks of height.
            double score = (required ? 1.0E15D : 0D)
                    + weight * 1.0E9D
                    + (terrain.naturalHeight() + routeLength * 0.1D * settings.routing().lengthPreference()) * 1.0E5D
                    + routing.potential()[node.index()]
                    + HydrologyHash.unit(stable);
            candidates.add(new SourceCandidate(node.index(), stable, score, required));
            hasRequiredCandidate |= required;
        }
        int target = expectedCount(sourceSettings.density(), HydrologyHash.mix(
                worldSeed,
                sourceSalt,
                key.tileX(),
                key.tileZ()
        ));
        int requiredMinimum = hasRequiredCandidate
                ? Math.max(1, sourceSettings.minimumPerTile())
                : 0;
        target = Math.max(requiredMinimum, target);
        int maximum = hasRequiredCandidate
                ? Math.max(1, sourceSettings.maximumPerTile())
                : sourceSettings.maximumPerTile();
        target = Math.min(maximum, target);
        int tributaries = surface ? settings.routing().tributaries() : settings.underground().tributaries();
        int coursesPerOutlet = 1 + tributaries;
        // Tributaries are budgeted on top of the source density: every outlet may draw its extra courses
        // beyond the tile's expected count, or no junction would ever fit inside a one-source budget.
        int tributaryBudget = Math.multiplyExact(routing.outlets().size(), tributaries);
        target = Math.min(Math.addExact(maximum, tributaryBudget), Math.addExact(target, tributaryBudget));
        target = effectiveSourceTarget(surface && !enforceGlobalSpacing, target, routing.outlets().size(), coursesPerOutlet);
        Comparator<SourceCandidate> candidateOrder = Comparator
                .comparing(SourceCandidate::required)
                .reversed();
        candidates.sort(candidateOrder
                .thenComparing(Comparator.comparingDouble(SourceCandidate::score).reversed())
                .thenComparingLong(SourceCandidate::stableId));
        prioritizeInlandSource(candidates, routing);
        int guaranteed = Math.min(target, requiredMinimum);
        int maximumCoursesPerOutlet = coursesPerOutlet;
        SourceAdmissionSelection admission = selectSourceAdmissionsByOutlet(
                candidates,
                routing,
                target,
                guaranteed,
                maximumCoursesPerOutlet,
                (int candidateIndex) -> {
                    if (!enforceGlobalSpacing) {
                        return true;
                    }
                    SourceCandidate candidate = candidates.get(candidateIndex);
                    return globallyAdmittedSource(
                            grid.node(candidate.nodeIndex()),
                            sourceSettings,
                            sourceSalt,
                            surface,
                            routingContexts
                    );
                }
        );
        int rejectionsPerTarget = surface
                ? SURFACE_OPTIONAL_SOURCE_REJECTIONS_PER_TARGET
                : OPTIONAL_SOURCE_REJECTIONS_PER_TARGET;
        int maximumOptionalRejections = Math.max(
                rejectionsPerTarget,
                target * rejectionsPerTarget
        );
        int admittedGuaranteed = Math.min(guaranteed, admission.selectedCandidateIndices().size());
        return new SourceSelection(
                surface,
                candidates,
                admission,
                admittedGuaranteed,
                maximumOptionalRejections
        );
    }

    static int effectiveSourceTarget(boolean outletBounded, int requestedTarget, int outletCount) {
        return effectiveSourceTarget(outletBounded, requestedTarget, outletCount, 1);
    }

    static int effectiveSourceTarget(boolean outletBounded, int requestedTarget, int outletCount, int coursesPerOutlet) {
        if (requestedTarget < 0 || outletCount < 0 || coursesPerOutlet < 1) {
            throw new IllegalArgumentException("Source target bounds cannot be negative.");
        }
        long bound = (long) outletCount * coursesPerOutlet;
        return outletBounded ? (int) Math.min(requestedTarget, bound) : requestedTarget;
    }

    private SourceAdmissionSelection selectSourceAdmissionsByOutlet(
            List<SourceCandidate> candidates,
            RoutingPlan routing,
            int target,
            int guaranteed,
            int maximumCoursesPerOutlet,
            IntPredicate globallyAdmitted
    ) {
        int candidateCount = candidates.size();
        ArrayList<Integer> selected = new ArrayList<>(Math.min(candidateCount, target));
        boolean[] selectedCandidates = new boolean[candidateCount];
        boolean[] spacingRejectedCandidates = new boolean[candidateCount];
        boolean[] evaluatedCandidates = new boolean[candidateCount];
        int[] outletIndices = new int[candidateCount];
        HashMap<Integer, Integer> selectedByOutlet = new HashMap<>();
        for (int candidateIndex = 0; candidateIndex < candidateCount; candidateIndex++) {
            outletIndices[candidateIndex] = routing.outletIndex()[candidates.get(candidateIndex).nodeIndex()];
        }
        for (int candidateIndex = 0;
             candidateIndex < candidateCount && selected.size() < target;
             candidateIndex++) {
            int outletIndex = outletIndices[candidateIndex];
            if (selectedByOutlet.getOrDefault(outletIndex, 0) >= maximumCoursesPerOutlet) {
                continue;
            }
            evaluatedCandidates[candidateIndex] = true;
            if (!globallyAdmitted.test(candidateIndex)) {
                spacingRejectedCandidates[candidateIndex] = true;
                continue;
            }
            selected.add(candidateIndex);
            selectedCandidates[candidateIndex] = true;
            selectedByOutlet.merge(outletIndex, 1, Math::addExact);
        }
        for (int candidateIndex = 0;
             candidateIndex < candidateCount && selected.size() < guaranteed;
             candidateIndex++) {
            if (selectedCandidates[candidateIndex]) {
                continue;
            }
            int outletIndex = outletIndices[candidateIndex];
            if (selectedByOutlet.getOrDefault(outletIndex, 0) >= maximumCoursesPerOutlet) {
                continue;
            }
            selected.add(candidateIndex);
            selectedCandidates[candidateIndex] = true;
            spacingRejectedCandidates[candidateIndex] = false;
            selectedByOutlet.merge(outletIndex, 1, Math::addExact);
        }
        return new SourceAdmissionSelection(
                target,
                selected,
                selectedCandidates,
                spacingRejectedCandidates,
                evaluatedCandidates,
                globallyAdmitted,
                outletIndices,
                maximumCoursesPerOutlet
        );
    }

    private void prioritizeInlandSource(List<SourceCandidate> candidates, RoutingPlan routing) {
        if (candidates.size() < 2) {
            return;
        }
        boolean required = candidates.getFirst().required();
        for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
            SourceCandidate candidate = candidates.get(candidateIndex);
            if (candidate.required() != required) {
                break;
            }
            if (!isInlandSource(candidate, routing)) {
                continue;
            }
            if (candidateIndex > 0) {
                candidates.addFirst(candidates.remove(candidateIndex));
            }
            return;
        }
    }

    private boolean isInlandSource(SourceCandidate candidate, RoutingPlan routing) {
        if (candidate == null) {
            return false;
        }
        int outletIndex = routing.outletIndex()[candidate.nodeIndex()];
        return outletIndex >= 0
                && routing.outlets().get(outletIndex).outlet().type() == HydrologyFeatureType.INLAND_GROTTO;
    }

    static SourceAdmissionSelection selectSourceAdmissions(
            int candidateCount,
            int target,
            int guaranteed,
            IntPredicate globallyAdmitted
    ) {
        if (candidateCount < 0 || target < 0 || guaranteed < 0 || guaranteed > target) {
            throw new IllegalArgumentException("Source admission bounds are invalid.");
        }
        Objects.requireNonNull(globallyAdmitted, "globallyAdmitted");
        ArrayList<Integer> selected = new ArrayList<>(Math.min(candidateCount, target));
        boolean[] selectedCandidates = new boolean[candidateCount];
        boolean[] spacingRejectedCandidates = new boolean[candidateCount];
        boolean[] evaluatedCandidates = new boolean[candidateCount];
        for (int candidateIndex = 0;
             candidateIndex < candidateCount && selected.size() < target;
             candidateIndex++) {
            evaluatedCandidates[candidateIndex] = true;
            if (!globallyAdmitted.test(candidateIndex)) {
                spacingRejectedCandidates[candidateIndex] = true;
                continue;
            }
            selected.add(candidateIndex);
            selectedCandidates[candidateIndex] = true;
        }
        for (int candidateIndex = 0;
             candidateIndex < candidateCount && selected.size() < guaranteed;
             candidateIndex++) {
            if (selectedCandidates[candidateIndex]) {
                continue;
            }
            selected.add(candidateIndex);
            selectedCandidates[candidateIndex] = true;
            spacingRejectedCandidates[candidateIndex] = false;
        }
        return new SourceAdmissionSelection(
                target,
                selected,
                selectedCandidates,
                spacingRejectedCandidates,
                evaluatedCandidates,
                globallyAdmitted,
                new int[candidateCount],
                Integer.MAX_VALUE
        );
    }

    private boolean globallyAdmittedSource(
            GridNode candidate,
            HydrologyPlannerSettings.Source sourceSettings,
            long sourceSalt,
            boolean surface,
            Map<HydrologyTileKey, SourceRoutingContext> routingContexts
    ) {
        int minimumSpacing = sourceSettings.minimumSpacing();
        boolean required = surface
                ? candidate.terrain().surfaceSourceRequired()
                : candidate.terrain().undergroundSourceRequired();
        if (minimumSpacing <= 0 || required) {
            return true;
        }
        SourcePriority candidatePriority = sourcePriority(candidate.x(), candidate.z(), candidate.terrain(), sourceSalt, surface);
        int sampleSpacing = settings.routing().sampleSpacing();
        int latticeRadius = (int) StrictMath.ceil(minimumSpacing / (double) sampleSpacing);
        for (int offsetZ = -latticeRadius; offsetZ <= latticeRadius; offsetZ++) {
            for (int offsetX = -latticeRadius; offsetX <= latticeRadius; offsetX++) {
                if (offsetX == 0 && offsetZ == 0) {
                    continue;
                }
                long deltaX = (long) offsetX * sampleSpacing;
                long deltaZ = (long) offsetZ * sampleSpacing;
                if (StrictMath.hypot(deltaX, deltaZ) >= minimumSpacing) {
                    continue;
                }
                long neighborX = (long) candidate.x() + deltaX;
                long neighborZ = (long) candidate.z() + deltaZ;
                if (neighborX < Integer.MIN_VALUE || neighborX > Integer.MAX_VALUE
                        || neighborZ < Integer.MIN_VALUE || neighborZ > Integer.MAX_VALUE) {
                    continue;
                }
                int x = (int) neighborX;
                int z = (int) neighborZ;
                HydrologyTileKey owner = HydrologyTileKey.fromBlock(x, z, settings.routing().tileSize());
                SourceRoutingContext context = routingContexts.computeIfAbsent(owner, this::sourceRoutingContext);
                GridNode neighbor = context.grid().nodeAtWorld(x, z);
                if (neighbor == null) {
                    throw new IllegalStateException("Source coordinate is absent from its owner routing lattice.");
                }
                HydrologyTerrainSample terrain = neighbor.terrain();
                if (!rawSourceEligible(terrain, sourceSettings, surface)) {
                    continue;
                }
                if (!routeViable(neighbor, context, surface)) {
                    continue;
                }
                SourcePriority neighborPriority = sourcePriority(x, z, terrain, sourceSalt, surface);
                if (compareSourcePriority(neighborPriority, candidatePriority) > 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private SourceRoutingContext sourceRoutingContext(HydrologyTileKey key) {
        SourceRoutingContext cached = routingContexts.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        RoutingContextLoad owned = new RoutingContextLoad(Thread.currentThread(), new CompletableFuture<>());
        RoutingContextLoad existing = resolvingRoutingContexts.putIfAbsent(key, owned);
        if (existing != null) {
            if (existing.owner() == Thread.currentThread()) {
                throw new IllegalStateException("Hydrology routing context cannot recursively load its own tile.");
            }
            try {
                return existing.future().join();
            } catch (CompletionException failure) {
                throw propagateOwnerFailure(failure.getCause());
            }
        }
        try {
            SourceRoutingContext resolved = routingContexts.getIfPresent(key);
            if (resolved == null) {
                resolved = compileSourceRoutingContext(key);
                routingContexts.put(key, resolved);
            }
            owned.future().complete(resolved);
            return resolved;
        } catch (Throwable failure) {
            owned.future().completeExceptionally(failure);
            throw propagateOwnerFailure(failure);
        } finally {
            resolvingRoutingContexts.remove(key, owned);
        }
    }

    private SourceRoutingContext compileSourceRoutingContext(HydrologyTileKey key) {
        SampledGrid grid = sample(key);
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();
        List<OutletCandidate> surfaceOutlets = resolveOutlets(key, grid, true, diagnostics);
        RoutingPlan surfaceRouting = buildRouting(grid, surfaceOutlets, true);
        List<OutletCandidate> undergroundOutlets = settings.underground().enabled()
                ? resolveOutlets(key, grid, false, diagnostics)
                : surfaceOutlets;
        RoutingPlan undergroundRouting = settings.underground().enabled()
                ? buildRouting(grid, undergroundOutlets, false)
                : surfaceRouting;
        return new SourceRoutingContext(
                grid,
                surfaceRouting,
                undergroundRouting,
                List.copyOf(diagnostics)
        );
    }

    private boolean routeViable(GridNode node, SourceRoutingContext context, boolean surface) {
        RoutingPlan routing = context.routing(surface);
        if (routing.outlets().isEmpty()
                || !Double.isFinite(routing.potential()[node.index()])
                || routing.parent()[node.index()] < 0
                || !sourceOutletAllowed(node, routing, surface)) {
            return false;
        }
        int routeLength = routing.routeLengths()[node.index()];
        return routeLength >= settings.routing().minimumCourseLength(surface)
                && routeLength <= settings.routing().maximumRouteLength();
    }

    private boolean sourceOutletAllowed(GridNode node, RoutingPlan routing, boolean surface) {
        int outletIndex = routing.outletIndex()[node.index()];
        if (outletIndex < 0 || outletIndex >= routing.outlets().size()) {
            return false;
        }
        RiverOutlet outlet = routing.outlets().get(outletIndex).outlet();
        return !surface
                || outlet.type() != HydrologyFeatureType.INLAND_GROTTO
                || settings.outlets().surfaceSinkholesEnabled();
    }

    private boolean rawSourceEligible(
            HydrologyTerrainSample terrain,
            HydrologyPlannerSettings.Source sourceSettings,
            boolean surface
    ) {
        if (terrain.ocean() || !terrain.transitAllowed()) {
            return false;
        }
        boolean allowed = surface ? terrain.surfaceSourceAllowed() : terrain.undergroundSourceAllowed();
        boolean required = surface ? terrain.surfaceSourceRequired() : terrain.undergroundSourceRequired();
        if (!allowed || surface && terrain.naturalHeight() < sourceSettings.minimumElevation()) {
            return false;
        }
        if (!surface && settings.underground().connectToExistingCaves() && !terrain.caveAvailable()) {
            return false;
        }
        double weight = surface ? terrain.surfaceSourceWeight() : terrain.undergroundSourceWeight();
        return required || weight > 0D;
    }

    private SourcePriority sourcePriority(
            int x,
            int z,
            HydrologyTerrainSample terrain,
            long sourceSalt,
            boolean surface
    ) {
        return new SourcePriority(
                surface ? terrain.surfaceSourceRequired() : terrain.undergroundSourceRequired(),
                surface ? terrain.surfaceSourceWeight() : terrain.undergroundSourceWeight(),
                terrain.naturalHeight(),
                sourceStableId(sourceSalt, x, z),
                x,
                z
        );
    }

    private long sourceStableId(long sourceSalt, int x, int z) {
        return HydrologyHash.mix(worldSeed, sourceSalt, x, z);
    }

    private int compareSourcePriority(SourcePriority first, SourcePriority second) {
        int requiredComparison = Boolean.compare(first.required(), second.required());
        if (requiredComparison != 0) {
            return requiredComparison;
        }
        int weightComparison = Double.compare(first.weight(), second.weight());
        if (weightComparison != 0) {
            return weightComparison;
        }
        int stableComparison = Long.compareUnsigned(first.stableId(), second.stableId());
        if (stableComparison != 0) {
            return stableComparison;
        }
        int heightComparison = Integer.compare(first.naturalHeight(), second.naturalHeight());
        if (heightComparison != 0) {
            return heightComparison;
        }
        int xComparison = Integer.compare(first.x(), second.x());
        return xComparison != 0 ? xComparison : Integer.compare(first.z(), second.z());
    }

    private static void addSourceDiagnostic(
            GridNode node,
            boolean surface,
            long stableId,
            HydrologyCandidateRejection rejection,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        int y = surface ? node.terrain().naturalHeight() : node.terrain().caveFluidY();
        diagnostics.add(new HydrologyDiagnosticCandidate(
                HydrologyHash.mix(stableId, DIAGNOSTIC_SALT, rejection.ordinal()),
                HydrologyCandidateKind.SOURCE,
                surface ? HydrologyFeatureType.SURFACE_POOL : HydrologyFeatureType.UNDERGROUND_POOL,
                new HydrologyPoint(node.x(), y, node.z()),
                rejection, 0
        ));
    }

    private int expectedCount(double density, long stable) {
        int count = (int) StrictMath.floor(density);
        double remainder = density - count;
        return count + (HydrologyHash.unit(stable) < remainder ? 1 : 0);
    }

    static int[] routeLengths(int[] parent, int width, HydrologyPlannerSettings.Routing routing) {
        int[] lengths = new int[parent.length];
        int[] pending = new int[parent.length];
        double[] distances = new double[parent.length];
        Arrays.fill(distances, Double.NaN);
        double diagonalDistance = StrictMath.hypot(routing.sampleSpacing(), routing.sampleSpacing());
        for (int sourceIndex = 0; sourceIndex < parent.length; sourceIndex++) {
            if (!Double.isNaN(distances[sourceIndex])) {
                continue;
            }
            int current = sourceIndex;
            int pendingCount = 0;
            while (current >= 0 && Double.isNaN(distances[current])) {
                pending[pendingCount++] = current;
                distances[current] = -1D;
                current = parent[current];
            }
            if (current >= 0 && distances[current] < 0D) {
                throw new IllegalStateException("Hydrology drainage routing contains a cycle.");
            }
            double distance = current < 0 ? 0D : distances[current];
            while (pendingCount > 0) {
                int node = pending[--pendingCount];
                int next = parent[node];
                if (next >= 0) {
                    boolean cardinal = node / width == next / width || node % width == next % width;
                    distance += cardinal ? routing.sampleSpacing() : diagonalDistance;
                }
                distances[node] = distance;
                lengths[node] = distance > routing.maximumRouteLength()
                        ? Integer.MAX_VALUE
                        : (int) StrictMath.ceil(distance);
            }
        }
        return lengths;
    }

    private int[] countContributions(List<Integer> sources, RoutingPlan routing) {
        int[] counts = new int[routing.parent().length];
        for (int source : sources) {
            int current = source;
            int steps = 0;
            while (routing.parent()[current] >= 0 && steps++ < settings.routing().maximumRouteNodes()) {
                counts[current]++;
                current = routing.parent()[current];
            }
        }
        return counts;
    }

    private CompiledGraph compileGraph(
            HydrologyTileKey key,
            SampledGrid grid,
            RoutingPlan routing,
            int[] surfaceContributions,
            int[] undergroundContributions,
            Map<Long, List<HydrologyPoint>> refinedEdges
    ) {
        HashSet<Integer> includedNodeIndices = new HashSet<>();
        ArrayList<DrainageEdge> edges = new ArrayList<>();
        HashMap<Integer, DrainageEdge> edgeByUpstream = new HashMap<>();
        for (GridNode upstream : grid.nodes()) {
            int downstreamIndex = routing.parent()[upstream.index()];
            if (downstreamIndex < 0 || !Double.isFinite(routing.potential()[upstream.index()])) {
                continue;
            }
            boolean acceptedCourseEdge = surfaceContributions[upstream.index()] > 0
                    || undergroundContributions[upstream.index()] > 0;
            if (!acceptedCourseEdge) {
                continue;
            }
            GridNode downstream = grid.node(downstreamIndex);
            RiverOutlet outlet = routing.outlets().get(routing.outletIndex()[upstream.index()]).outlet();
            long edgeId = HydrologyHash.mix(worldSeed, EDGE_SALT, upstream.id(), downstream.id(), outlet.id());
            List<HydrologyPoint> centerline = refinedEdges.get(edgeId);
            int transverseCandidates = ROUTE_TRANSVERSE_CANDIDATES;
            if (centerline == null) {
                HydrologyPoint upstreamAnchor = routeAnchor(upstream);
                HydrologyPoint downstreamAnchor = routeAnchor(downstream);
                HydrologyPoint continuation = edgeContinuation(grid, routing, downstream, outlet);
                RefinedEdgeKey refinedEdgeKey = new RefinedEdgeKey(
                        upstream.id(),
                        downstream.id(),
                        upstreamAnchor.x(),
                        upstreamAnchor.z(),
                        downstreamAnchor.x(),
                        downstreamAnchor.z(),
                        continuation.x(),
                        continuation.z(),
                        transverseCandidates
                );
                centerline = refinedEdgeCache.get(
                        refinedEdgeKey,
                        ignored -> refineEdge(
                                upstream.id(),
                                downstream.id(),
                                upstreamAnchor,
                                downstreamAnchor,
                                continuation,
                                transverseCandidates
                        )
                );
                refinedEdges.put(edgeId, centerline);
            }
            if (centerline.isEmpty()) {
                HydrologyPoint upstreamAnchor = routeAnchor(upstream);
                HydrologyPoint downstreamAnchor = routeAnchor(downstream);
                HydrologyPoint continuation = edgeContinuation(grid, routing, downstream, outlet);
                RefinedEdgeKey refinedEdgeKey = new RefinedEdgeKey(
                        upstream.id(),
                        downstream.id(),
                        upstreamAnchor.x(),
                        upstreamAnchor.z(),
                        downstreamAnchor.x(),
                        downstreamAnchor.z(),
                        continuation.x(),
                        continuation.z(),
                        ROUTE_FALLBACK_TRANSVERSE_CANDIDATES
                );
                centerline = refinedEdgeCache.get(
                        refinedEdgeKey,
                        ignored -> refineEdge(
                                upstream.id(),
                                downstream.id(),
                                upstreamAnchor,
                                downstreamAnchor,
                                continuation,
                                ROUTE_FALLBACK_TRANSVERSE_CANDIDATES
                        )
                );
                refinedEdges.put(edgeId, centerline);
            }
            if (centerline.isEmpty()) {
                centerline = constrainedTerrainFallbackEdge(
                        routeAnchor(upstream),
                        routeAnchor(downstream)
                );
                refinedEdges.put(edgeId, centerline);
            }
            if (centerline.isEmpty()) {
                continue;
            }
            double cost = routing.potential()[upstream.index()] - routing.potential()[downstreamIndex];
            DrainageEdge edge = new DrainageEdge(
                    edgeId,
                    upstream.id(),
                    downstream.id(),
                    outlet.id(),
                    cost,
                    surfaceContributions[upstream.index()],
                    undergroundContributions[upstream.index()],
                    centerline
            );
            edges.add(edge);
            edgeByUpstream.put(upstream.index(), edge);
            includedNodeIndices.add(upstream.index());
            includedNodeIndices.add(downstream.index());
        }
        ArrayList<DrainageNode> nodes = new ArrayList<>(includedNodeIndices.size());
        for (GridNode node : grid.nodes()) {
            if (!includedNodeIndices.contains(node.index())) {
                continue;
            }
            int outletIndex = routing.outletIndex()[node.index()];
            RiverOutlet outlet = routing.outlets().get(outletIndex).outlet();
            HydrologyPoint anchor = routeAnchor(node);
            HydrologyTerrainSample terrain = Objects.requireNonNull(
                    sampleLandBasis(anchor.x(), anchor.z()),
                    "Hydrology route anchor left natural land"
            );
            nodes.add(new DrainageNode(
                    node.id(),
                    anchor.x(),
                    anchor.z(),
                    terrain,
                    routing.potential()[node.index()],
                    outlet.id()
            ));
        }
        ArrayList<RiverOutlet> outlets = new ArrayList<>(routing.outlets().size());
        for (OutletCandidate outletCandidate : routing.outlets()) {
            outlets.add(outletCandidate.outlet());
        }
        return new CompiledGraph(List.copyOf(nodes), List.copyOf(edges), List.copyOf(outlets), Map.copyOf(edgeByUpstream));
    }

    private CompiledGraph mergeGraphs(CompiledGraph first, CompiledGraph second) {
        LinkedHashMap<Long, DrainageNode> nodes = new LinkedHashMap<>();
        for (DrainageNode node : first.nodes()) {
            nodes.put(node.id(), node);
        }
        for (DrainageNode node : second.nodes()) {
            nodes.putIfAbsent(node.id(), node);
        }
        LinkedHashMap<Long, DrainageEdge> edges = new LinkedHashMap<>();
        for (DrainageEdge edge : first.edges()) {
            edges.put(edge.id(), edge);
        }
        for (DrainageEdge edge : second.edges()) {
            DrainageNode upstream = nodes.get(edge.upstreamNodeId());
            DrainageNode downstream = nodes.get(edge.downstreamNodeId());
            if (upstream != null
                    && downstream != null
                    && downstream.potential() < upstream.potential()) {
                edges.putIfAbsent(edge.id(), edge);
            }
        }
        LinkedHashMap<Long, RiverOutlet> outlets = new LinkedHashMap<>();
        for (RiverOutlet outlet : first.outlets()) {
            outlets.put(outlet.id(), outlet);
        }
        for (RiverOutlet outlet : second.outlets()) {
            outlets.putIfAbsent(outlet.id(), outlet);
        }
        HashMap<Integer, DrainageEdge> edgeByUpstream = new HashMap<>(first.edgeByUpstream());
        for (Map.Entry<Integer, DrainageEdge> entry : second.edgeByUpstream().entrySet()) {
            if (entry.getValue().equals(edges.get(entry.getValue().id()))) {
                edgeByUpstream.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return new CompiledGraph(
                List.copyOf(nodes.values()),
                List.copyOf(edges.values()),
                List.copyOf(outlets.values()),
                Map.copyOf(edgeByUpstream)
        );
    }

    private HydrologyPoint edgeContinuation(
            SampledGrid grid,
            RoutingPlan routing,
            GridNode downstream,
            RiverOutlet outlet
    ) {
        int continuationIndex = routing.parent()[downstream.index()];
        if (continuationIndex >= 0) {
            return routeAnchor(grid.node(continuationIndex));
        }
        HydrologyPoint landward = outlet.landwardPoint();
        if (landward.x() != downstream.x() || landward.z() != downstream.z()) {
            return landward;
        }
        return outlet.connectionPoint();
    }

    private HydrologyPoint routeAnchor(GridNode node) {
        PlanningSamples samples = planningSamples.get();
        if (samples != null) {
            HydrologyPoint cached = samples.routeAnchors.get(node.id());
            if (cached != null) {
                return cached;
            }
        }
        int spacing = settings.routing().sampleSpacing();
        HydrologyPlannerSettings.Meanders meanders = settings.geometry().meanders();
        double maximumOffsetRatio = Math.min(
                ROUTE_ANCHOR_MAXIMUM_OFFSET_RATIO,
                meanders.maximumOffsetRatio() * 0.625D
        );
        if (maximumOffsetRatio <= 0D) {
            return node.naturalPoint();
        }
        double primaryWavelength = meanders.primaryWavelength() * 5D;
        double detailWavelength = meanders.detailWavelength() * 8D;
        double primaryX = routeAnchorX.noiseSigned(
                node.x() / primaryWavelength,
                node.z() / primaryWavelength
        );
        double primaryZ = routeAnchorZ.noiseSigned(
                node.x() / primaryWavelength,
                node.z() / primaryWavelength
        );
        double detailX = routeAnchorX.noiseSigned(
                node.x() / detailWavelength + 31.75D,
                node.z() / detailWavelength - 19.25D
        );
        double detailZ = routeAnchorZ.noiseSigned(
                node.x() / detailWavelength - 27.5D,
                node.z() / detailWavelength + 43.5D
        );
        double vectorX = primaryX * 0.72D + detailX * 0.28D;
        double vectorZ = primaryZ * 0.72D + detailZ * 0.28D;
        double vectorLength = StrictMath.hypot(vectorX, vectorZ);
        if (vectorLength < 0.1D) {
            double angle = HydrologyHash.unit(HydrologyHash.mix(worldSeed, node.id(), ROUTE_ANCHOR_X_SALT))
                    * StrictMath.PI * 2D;
            vectorX = StrictMath.cos(angle);
            vectorZ = StrictMath.sin(angle);
            vectorLength = 1D;
        }
        double maximumOffset = spacing * maximumOffsetRatio;
        double displacement = maximumOffset * (0.62D + Math.min(1D, vectorLength) * 0.38D);
        int desiredX = (int) StrictMath.round(node.x() + vectorX / vectorLength * displacement);
        int desiredZ = (int) StrictMath.round(node.z() + vectorZ / vectorLength * displacement);
        int searchRadius = Math.max(settings.routing().refinementSpacing(), spacing / 5);
        int innerRadius = Math.max(1, searchRadius / 2);
        int[][] offsets = {
                {0, 0},
                {innerRadius, 0}, {-innerRadius, 0}, {0, innerRadius}, {0, -innerRadius},
                {innerRadius, innerRadius}, {innerRadius, -innerRadius},
                {-innerRadius, innerRadius}, {-innerRadius, -innerRadius},
                {searchRadius, 0}, {-searchRadius, 0}, {0, searchRadius}, {0, -searchRadius},
                {node.x() - desiredX, node.z() - desiredZ}
        };
        HydrologyPoint selected = node.naturalPoint();
        double selectedScore = anchorScore(
                node,
                selected.x(),
                selected.z(),
                desiredX,
                desiredZ,
                maximumOffsetRatio
        );
        for (int[] offset : offsets) {
            int x = desiredX + offset[0];
            int z = desiredZ + offset[1];
            double score = anchorScore(node, x, z, desiredX, desiredZ, maximumOffsetRatio);
            if (score < selectedScore) {
                HydrologyTerrainSample terrain = sampleLandBasis(x, z);
                selected = new HydrologyPoint(x, terrain.naturalHeight(), z);
                selectedScore = score;
            }
        }
        if (samples != null) {
            samples.routeAnchors.put(node.id(), selected);
        }
        return selected;
    }

    private double anchorScore(
            GridNode node,
            int x,
            int z,
            int desiredX,
            int desiredZ,
            double maximumOffsetRatio
    ) {
        double distanceFromNode = StrictMath.hypot(x - node.x(), z - node.z());
        if (distanceFromNode > settings.routing().sampleSpacing() * maximumOffsetRatio) {
            return Double.POSITIVE_INFINITY;
        }
        HydrologyTerrainSample terrain = sampleLandBasisWithoutSlope(x, z);
        if (terrain == null || !terrain.transitAllowed() || !withinConfines(terrain, node.terrain().confinesKey())) {
            return Double.POSITIVE_INFINITY;
        }
        if (!traversableHop(node.naturalPoint(), new HydrologyPoint(x, terrain.naturalHeight(), z))) {
            return Double.POSITIVE_INFINITY;
        }
        terrain = sampleLandBasis(x, z);
        double distanceFromDesired = StrictMath.hypot(x - desiredX, z - desiredZ);
        double score = terrain.naturalHeight() * settings.routing().valleyPreference()
                + terrain.slope() * settings.routing().slopePenalty()
                + terrain.routingCost() * terrain.routingMultiplier()
                + distanceFromDesired * 2.4D
                + distanceFromNode * 0.08D;
        long tie = HydrologyHash.mix(worldSeed, node.id(), x, z);
        return score + HydrologyHash.unit(tie) * 1.0E-6D;
    }

    private List<HydrologyPoint> refineEdge(
            long upstreamId,
            long downstreamId,
            HydrologyPoint upstream,
            HydrologyPoint downstream,
            HydrologyPoint continuation,
            int transverseCandidates
    ) {
        int refinement = settings.routing().refinementSpacing();
        int effectiveTransverseCandidates = transverseCandidates;
        double deltaX = downstream.x() - upstream.x();
        double deltaZ = downstream.z() - upstream.z();
        double distance = StrictMath.hypot(deltaX, deltaZ);
        int steps = Math.max(1, (int) StrictMath.ceil(distance / refinement));
        Direction directTangent = direction(upstream.x(), upstream.z(), downstream.x(), downstream.z());
        Direction startTangent = directTangent;
        Direction endTangent = direction(downstream.x(), downstream.z(), continuation.x(), continuation.z());
        if (endTangent.x() == 0D && endTangent.z() == 0D) {
            endTangent = startTangent;
        }
        HydrologyTerrainSample upstreamTerrain = sampleLandBasis(upstream.x(), upstream.z());
        HydrologyTerrainSample downstreamTerrain = sampleLandBasis(downstream.x(), downstream.z());
        int transitionThreshold = settings.hydraulics().waterfallMinimumDrop();
        boolean elevationTransition = Math.abs(upstream.y() - downstream.y()) >= transitionThreshold
                || upstreamTerrain != null && upstreamTerrain.slope() >= transitionThreshold
                || downstreamTerrain != null && downstreamTerrain.slope() >= transitionThreshold;
        if (elevationTransition) {
            endTangent = directTangent;
        }
        double wormBlend = elevationTransition ? 0D : routeWormBlend();
        double tangentMagnitude = distance * (ROUTE_TANGENT_SCALE + wormBlend * 0.42D);
        if (wormBlend > 0D) {
            startTangent = routeWormDirection(upstream, startTangent, wormBlend);
            endTangent = routeWormDirection(downstream, endTangent, wormBlend);
        }
        ArrayList<List<RouteCandidate>> layers = new ArrayList<>(steps + 1);
        for (int step = 0; step <= steps; step++) {
            if (step == 0) {
                layers.add(List.of(routeEndpoint(upstream, startTangent)));
                continue;
            }
            if (step == steps) {
                layers.add(List.of(routeEndpoint(downstream, endTangent)));
                continue;
            }
            double progress = step / (double) steps;
            RoutePosition nominal = routePosition(
                    upstream,
                    downstream,
                    startTangent,
                    endTangent,
                    tangentMagnitude,
                    distance,
                    progress,
                    elevationTransition ? 0D : 1D
            );
            List<RouteCandidate> candidates = routeCandidates(
                    upstreamId,
                    downstreamId,
                    nominal,
                    progress,
                    refinement,
                    elevationTransition ? 0 : effectiveTransverseCandidates,
                    upstreamTerrain == null ? null : upstreamTerrain.confinesKey()
            );
            if (candidates.isEmpty()) {
                return List.of();
            }
            layers.add(candidates);
        }
        return solveTerrainRoute(layers, distance / steps);
    }

    private double routeWormBlend() {
        HydrologyPlannerSettings.Meanders meanders = settings.geometry().meanders();
        double strength = meanders.primaryStrength() + meanders.detailStrength() * 0.35D;
        if (strength <= 0D || meanders.maximumOffsetRatio() <= 0D) {
            return 0D;
        }
        return Math.min(0.18D, meanders.maximumOffsetRatio() * (0.08D + strength * 0.05D));
    }

    private Direction routeWormDirection(HydrologyPoint point, Direction fallback, double blend) {
        HydrologyPlannerSettings.Meanders meanders = settings.geometry().meanders();
        double wavelength = Math.max(
                settings.routing().sampleSpacing() * 2D,
                meanders.primaryWavelength() * 2.5D
        );
        double fieldX = routeWormPrimary.noiseSigned(point.x() / wavelength, point.z() / wavelength);
        double fieldZ = routeWormDetail.noiseSigned(
                point.x() / wavelength + 37.25D,
                point.z() / wavelength - 21.75D
        );
        double fieldLength = StrictMath.hypot(fieldX, fieldZ);
        if (fieldLength < 0.05D) {
            return fallback;
        }
        fieldX /= fieldLength;
        fieldZ /= fieldLength;
        if (fieldX * fallback.x() + fieldZ * fallback.z() < 0D) {
            fieldX = -fieldX;
            fieldZ = -fieldZ;
        }
        double blendedX = fallback.x() * (1D - blend) + fieldX * blend;
        double blendedZ = fallback.z() * (1D - blend) + fieldZ * blend;
        double blendedLength = StrictMath.hypot(blendedX, blendedZ);
        return blendedLength <= 0D
                ? fallback
                : new Direction(blendedX / blendedLength, blendedZ / blendedLength);
    }

    private List<HydrologyPoint> constrainedTerrainFallbackEdge(
            HydrologyPoint upstream,
            HydrologyPoint downstream
    ) {
        if (crossesTerrainCrevasse(upstream, downstream)) {
            return List.of();
        }
        double deltaX = downstream.x() - upstream.x();
        double deltaZ = downstream.z() - upstream.z();
        double distance = StrictMath.hypot(deltaX, deltaZ);
        if (distance <= 0D) {
            return List.of();
        }
        int refinement = settings.routing().refinementSpacing();
        double perpendicularX = -deltaZ / distance;
        double perpendicularZ = deltaX / distance;
        List<HydrologyPoint> direct = line(upstream, downstream, refinement);
        ArrayList<HydrologyPoint> points = new ArrayList<>(direct.size());
        for (int index = 0; index < direct.size(); index++) {
            HydrologyPoint point = direct.get(index);
            HydrologyTerrainSample terrain = sampleLandBasisWithoutSlope(point.x(), point.z());
            if (terrain == null || !terrain.transitAllowed()) {
                return List.of();
            }
            if (index > 0 && index < direct.size() - 1
                    && hasLateralRouteFreedom(point, perpendicularX, perpendicularZ, refinement)) {
                return List.of();
            }
            points.add(new HydrologyPoint(point.x(), terrain.naturalHeight(), point.z()));
        }
        return traversableRoute(points) && !containsTerrainPit(points)
                ? List.copyOf(points)
                : List.of();
    }

    private boolean hasLateralRouteFreedom(
            HydrologyPoint point,
            double perpendicularX,
            double perpendicularZ,
            int refinement
    ) {
        for (int direction : List.of(-1, 1)) {
            int x = (int) StrictMath.round(point.x() + perpendicularX * refinement * direction);
            int z = (int) StrictMath.round(point.z() + perpendicularZ * refinement * direction);
            HydrologyTerrainSample terrain = sampleLandBasisWithoutSlope(x, z);
            if (terrain != null && terrain.transitAllowed()) {
                return true;
            }
        }
        return false;
    }

    private boolean containsTerrainPit(List<HydrologyPoint> route) {
        int threshold = settings.hydraulics().waterfallMinimumDrop();
        for (int pointIndex = 1; pointIndex < route.size() - 1; pointIndex++) {
            HydrologyPoint previous = route.get(pointIndex - 1);
            HydrologyPoint point = route.get(pointIndex);
            HydrologyPoint next = route.get(pointIndex + 1);
            if (previous.y() - point.y() >= threshold && next.y() - point.y() >= threshold) {
                return true;
            }
        }
        return false;
    }

    private RouteCandidate routeEndpoint(
            HydrologyPoint point,
            Direction tangent
    ) {
        HydrologyTerrainSample terrain = sampleLandBasis(point.x(), point.z());
        if (terrain == null || !terrain.transitAllowed()) {
            return new RouteCandidate(
                    point,
                    0D,
                    Double.POSITIVE_INFINITY,
                    Double.POSITIVE_INFINITY,
                    tangent,
                    true
            );
        }
        double terrainScore = routeTerrainScore(terrain);
        return new RouteCandidate(
                new HydrologyPoint(point.x(), terrain.naturalHeight(), point.z()),
                0D,
                terrainScore,
                terrainScore,
                tangent,
                true
        );
    }

    private RoutePosition routePosition(
            HydrologyPoint upstream,
            HydrologyPoint downstream,
            Direction startTangent,
            Direction endTangent,
            double tangentMagnitude,
            double distance,
            double progress,
            double meanderScale
    ) {
        double tangentX = hermiteDerivative(
                upstream.x(),
                downstream.x(),
                startTangent.x() * tangentMagnitude,
                endTangent.x() * tangentMagnitude,
                progress
        );
        double tangentZ = hermiteDerivative(
                upstream.z(),
                downstream.z(),
                startTangent.z() * tangentMagnitude,
                endTangent.z() * tangentMagnitude,
                progress
        );
        double tangentLength = StrictMath.hypot(tangentX, tangentZ);
        Direction tangent = tangentLength <= 0D
                ? startTangent
                : new Direction(tangentX / tangentLength, tangentZ / tangentLength);
        double baseX = hermite(
                upstream.x(),
                downstream.x(),
                startTangent.x() * tangentMagnitude,
                endTangent.x() * tangentMagnitude,
                progress
        );
        double baseZ = hermite(
                upstream.z(),
                downstream.z(),
                startTangent.z() * tangentMagnitude,
                endTangent.z() * tangentMagnitude,
                progress
        );
        double envelope = StrictMath.pow(StrictMath.sin(StrictMath.PI * progress), 2D);
        HydrologyPlannerSettings.Meanders meanders = settings.geometry().meanders();
        double primary = routeWormPrimary.noiseSigned(
                baseX / meanders.primaryWavelength(),
                baseZ / meanders.primaryWavelength()
        );
        double detail = routeWormDetail.noiseSigned(
                baseX / meanders.detailWavelength(),
                baseZ / meanders.detailWavelength()
        );
        double worm = distance * meanders.maximumOffsetRatio() * envelope * meanderScale * (
                primary * meanders.primaryStrength()
                        + detail * meanders.detailStrength()
        );
        double anchorRatio = Math.min(
                ROUTE_ANCHOR_MAXIMUM_OFFSET_RATIO,
                meanders.maximumOffsetRatio() * 0.625D
        );
        double transverseRatio = settings.routing().refinementSpacing()
                / (double) settings.routing().sampleSpacing();
        double maximumWorm = distance * Math.max(0D, 0.5D - anchorRatio - transverseRatio);
        worm = Math.max(-maximumWorm, Math.min(maximumWorm, worm));
        return new RoutePosition(
                baseX - tangent.z() * worm,
                baseZ + tangent.x() * worm,
                baseX,
                baseZ,
                tangent
        );
    }

    /**
     * {@code confines} is the area the edge's upstream end is confined to, or null: candidates outside it
     * are not offered, so a refined route between two confined lattice nodes stays inside their area.
     */
    private static boolean withinConfines(HydrologyTerrainSample terrain, String confines) {
        return confines == null || confines.equals(terrain.confinesKey());
    }

    private List<RouteCandidate> routeCandidates(
            long upstreamId,
            long downstreamId,
            RoutePosition nominal,
            double progress,
            int refinement,
            int transverseCandidates,
            String confines
    ) {
        double searchEnvelope = StrictMath.sin(StrictMath.PI * progress);
        LinkedHashMap<Long, RouteCandidate> candidates = new LinkedHashMap<>();
        for (int offsetIndex = -transverseCandidates;
             offsetIndex <= transverseCandidates;
             offsetIndex++) {
            double offset = offsetIndex * refinement * searchEnvelope;
            int x = (int) StrictMath.round(nominal.x() - nominal.tangent().z() * offset);
            int z = (int) StrictMath.round(nominal.z() + nominal.tangent().x() * offset);
            HydrologyTerrainSample terrain = sampleLandBasis(x, z);
            if (terrain == null || !terrain.transitAllowed() || !withinConfines(terrain, confines)) {
                continue;
            }
            long packed = RiverFootprint.pack(x, z);
            double localScore = routeTerrainScore(terrain)
                    + StrictMath.abs(offset) * 0.18D
                    + HydrologyHash.unit(HydrologyHash.mix(worldSeed, upstreamId, downstreamId, x, z)) * 1.0E-6D;
            RouteCandidate candidate = new RouteCandidate(
                    new HydrologyPoint(x, terrain.naturalHeight(), z),
                    offset,
                    localScore,
                    routeTerrainScore(terrain),
                    nominal.tangent(),
                    true
            );
            RouteCandidate current = candidates.get(packed);
            if (current == null || candidate.localScore() < current.localScore()) {
                candidates.put(packed, candidate);
            }
        }
        if (!candidates.isEmpty()) {
            return List.copyOf(candidates.values());
        }
        int fallbackX = (int) StrictMath.round(nominal.baseX());
        int fallbackZ = (int) StrictMath.round(nominal.baseZ());
        HydrologyTerrainSample fallbackTerrain = sampleLandBasis(fallbackX, fallbackZ);
        if (fallbackTerrain != null
                && fallbackTerrain.transitAllowed()
                && withinConfines(fallbackTerrain, confines)) {
            long packed = RiverFootprint.pack(fallbackX, fallbackZ);
            double fallbackOffset = (nominal.baseX() - nominal.x()) * -nominal.tangent().z()
                    + (nominal.baseZ() - nominal.z()) * nominal.tangent().x();
            double localScore = routeTerrainScore(fallbackTerrain)
                    + StrictMath.abs(fallbackOffset) * 0.18D
                    + HydrologyHash.unit(HydrologyHash.mix(
                    worldSeed,
                    upstreamId,
                    downstreamId,
                    fallbackX,
                    fallbackZ
            )) * 1.0E-6D;
            RouteCandidate fallback = new RouteCandidate(
                    new HydrologyPoint(fallbackX, fallbackTerrain.naturalHeight(), fallbackZ),
                    fallbackOffset,
                    localScore,
                    routeTerrainScore(fallbackTerrain),
                    nominal.tangent(),
                    true
            );
            RouteCandidate current = candidates.get(packed);
            if (current == null || fallback.localScore() < current.localScore()) {
                candidates.put(packed, fallback);
            }
        }
        return List.copyOf(candidates.values());
    }

    private List<RouteCandidate> unresolvedSurfaceRouteCandidates(
            SampledGrid grid,
            long upstreamId,
            long downstreamId,
            RoutePosition nominal,
            double progress,
            int refinement,
            int transverseCandidates
    ) {
        double searchEnvelope = StrictMath.sin(StrictMath.PI * progress);
        LinkedHashMap<Long, RouteCandidate> candidates = new LinkedHashMap<>();
        for (int offsetIndex = -transverseCandidates;
             offsetIndex <= transverseCandidates;
             offsetIndex++) {
            double offset = offsetIndex * refinement * searchEnvelope;
            int x = (int) StrictMath.round(nominal.x() - nominal.tangent().z() * offset);
            int z = (int) StrictMath.round(nominal.z() + nominal.tangent().x() * offset);
            long packed = RiverFootprint.pack(x, z);
            RouteTerrainEstimate estimate = routeTerrainEstimate(grid, x, z);
            double localScore = estimate.terrainScore()
                    + StrictMath.abs(offset) * 0.18D
                    + HydrologyHash.unit(HydrologyHash.mix(
                    worldSeed,
                    upstreamId,
                    downstreamId,
                    x,
                    z
            )) * 1.0E-6D;
            RouteCandidate candidate = new RouteCandidate(
                    new HydrologyPoint(x, estimate.height(), z),
                    offset,
                    localScore,
                    estimate.terrainScore(),
                    nominal.tangent(),
                    false
            );
            RouteCandidate current = candidates.get(packed);
            if (current == null || candidate.localScore() < current.localScore()) {
                candidates.put(packed, candidate);
            }
        }
        return List.copyOf(candidates.values());
    }

    private RouteTerrainEstimate routeTerrainEstimate(SampledGrid grid, int x, int z) {
        double gridX = (x - grid.minimumX()) / (double) grid.spacing();
        double gridZ = (z - grid.minimumZ()) / (double) grid.spacing();
        int minimumGridX = Math.max(0, Math.min(grid.width() - 1, (int) StrictMath.floor(gridX)));
        int minimumGridZ = Math.max(0, Math.min(grid.width() - 1, (int) StrictMath.floor(gridZ)));
        int maximumGridX = Math.min(grid.width() - 1, minimumGridX + 1);
        int maximumGridZ = Math.min(grid.width() - 1, minimumGridZ + 1);
        double progressX = Math.max(0D, Math.min(1D, gridX - minimumGridX));
        double progressZ = Math.max(0D, Math.min(1D, gridZ - minimumGridZ));
        HydrologyTerrainSample northwest = grid.nodeAt(minimumGridX, minimumGridZ).terrain();
        HydrologyTerrainSample northeast = grid.nodeAt(maximumGridX, minimumGridZ).terrain();
        HydrologyTerrainSample southwest = grid.nodeAt(minimumGridX, maximumGridZ).terrain();
        HydrologyTerrainSample southeast = grid.nodeAt(maximumGridX, maximumGridZ).terrain();
        double height = bilinear(
                northwest.naturalHeight(),
                northeast.naturalHeight(),
                southwest.naturalHeight(),
                southeast.naturalHeight(),
                progressX,
                progressZ
        );
        double terrainScore = bilinear(
                routeTerrainScore(northwest),
                routeTerrainScore(northeast),
                routeTerrainScore(southwest),
                routeTerrainScore(southeast),
                progressX,
                progressZ
        );
        return new RouteTerrainEstimate((int) StrictMath.round(height), terrainScore);
    }

    private double bilinear(
            double northwest,
            double northeast,
            double southwest,
            double southeast,
            double progressX,
            double progressZ
    ) {
        double north = northwest + (northeast - northwest) * progressX;
        double south = southwest + (southeast - southwest) * progressX;
        return north + (south - north) * progressZ;
    }

    private RouteCandidate resolveRouteCandidate(RouteCandidate candidate) {
        if (candidate.terrainResolved()) {
            return candidate;
        }
        HydrologyPoint point = candidate.point();
        HydrologyTerrainSample terrain = sampleLandBasis(point.x(), point.z());
        if (terrain == null || !terrain.transitAllowed()) {
            return new RouteCandidate(
                    point,
                    candidate.offset(),
                    Double.POSITIVE_INFINITY,
                    Double.POSITIVE_INFINITY,
                    candidate.tangent(),
                    true
            );
        }
        double terrainScore = routeTerrainScore(terrain) + crossDropPenalty(point, candidate.tangent(), terrain);
        double exactLocalScore = candidate.localScore() - candidate.terrainScore() + terrainScore;
        return new RouteCandidate(
                new HydrologyPoint(point.x(), terrain.naturalHeight(), point.z()),
                candidate.offset(),
                exactLocalScore,
                terrainScore,
                candidate.tangent(),
                true
        );
    }

    /**
     * Ground that falls away beside a candidate forces the valley solver to cut the channel down to the
     * lower bank; costing that drop keeps refined routes on valley floors and contour lines instead of
     * traversing hillsides that the incision cap would later reject.
     */
    private double crossDropPenalty(HydrologyPoint point, Direction tangent, HydrologyTerrainSample center) {
        double length = StrictMath.hypot(tangent.x(), tangent.z());
        if (length <= 0D) {
            return 0D;
        }
        double normalX = -tangent.z() / length;
        double normalZ = tangent.x() / length;
        double reach = settings.surface().maximumWidth() / 2D + settings.surface().shoreWidth() + 2D;
        int lowest = center.naturalHeight();
        for (double distance = reach / 2D; distance <= reach; distance += reach / 2D) {
            for (double direction = -1D; direction <= 1D; direction += 2D) {
                HydrologyTerrainSample side = sampleLandBasisWithoutSlope(
                        (int) StrictMath.round(point.x() + normalX * distance * direction),
                        (int) StrictMath.round(point.z() + normalZ * distance * direction));
                if (side != null) {
                    lowest = Math.min(lowest, side.naturalHeight());
                }
            }
        }
        double drop = center.naturalHeight() - lowest;
        return drop * settings.routing().valleyPreference() * CROSS_DROP_WEIGHT;
    }

    private double routeTerrainScore(HydrologyTerrainSample terrain) {
        return terrain.naturalHeight() * settings.routing().valleyPreference()
                + terrain.slope() * settings.routing().slopePenalty()
                + terrain.routingCost() * terrain.routingMultiplier();
    }

    private List<HydrologyPoint> solveTerrainRoute(
            List<List<RouteCandidate>> layers,
            double targetStepLength
    ) {
        ArrayList<List<RouteCandidate>> availableLayers = new ArrayList<>(layers);
        HashSet<TerrainTransitionKey> rejectedTransitions = new HashSet<>();
        while (true) {
            RouteCandidate[] route = selectTerrainRoute(
                    availableLayers,
                    targetStepLength,
                    rejectedTransitions
            );
            if (route.length == 0) {
                return List.of();
            }
            TerrainTransitionKey unsupportedTransition = unsupportedTerrainTransition(route);
            if (unsupportedTransition != null) {
                rejectedTransitions.add(unsupportedTransition);
                continue;
            }
            int pitRemoval = removeTerrainPits(availableLayers, route);
            if (pitRemoval < 0) {
                return List.of();
            }
            if (pitRemoval > 0) {
                continue;
            }
            ArrayList<HydrologyPoint> points = new ArrayList<>(route.length);
            for (RouteCandidate candidate : route) {
                HydrologyPoint point = candidate.point();
                if (points.isEmpty()
                        || point.x() != points.getLast().x()
                        || point.z() != points.getLast().z()) {
                    points.add(point);
                }
            }
            if (points.size() < 2) {
                return List.of();
            }
            return smoothTerrainRoute(points);
        }
    }

    private RouteCandidate[] selectTerrainRoute(
            List<List<RouteCandidate>> layers,
            double targetStepLength,
            Set<TerrainTransitionKey> rejectedTransitions
    ) {
        int layerCount = layers.size();
        double[][] costs = new double[layerCount][];
        int[][] predecessors = new int[layerCount][];
        costs[0] = new double[]{layers.getFirst().getFirst().localScore()};
        predecessors[0] = new int[]{-1};
        for (int layerIndex = 1; layerIndex < layerCount; layerIndex++) {
            List<RouteCandidate> currentLayer = layers.get(layerIndex);
            List<RouteCandidate> previousLayer = layers.get(layerIndex - 1);
            costs[layerIndex] = new double[currentLayer.size()];
            predecessors[layerIndex] = new int[currentLayer.size()];
            Arrays.fill(costs[layerIndex], Double.POSITIVE_INFINITY);
            Arrays.fill(predecessors[layerIndex], -1);
            for (int currentIndex = 0; currentIndex < currentLayer.size(); currentIndex++) {
                RouteCandidate current = currentLayer.get(currentIndex);
                for (int previousIndex = 0; previousIndex < previousLayer.size(); previousIndex++) {
                    if (!Double.isFinite(costs[layerIndex - 1][previousIndex])) {
                        continue;
                    }
                    RouteCandidate previous = previousLayer.get(previousIndex);
                    TerrainTransitionKey transitionKey = new TerrainTransitionKey(
                            previous.point(),
                            current.point()
                    );
                    if (rejectedTransitions.contains(transitionKey)) {
                        continue;
                    }
                    double transition = routeTransitionGeometryCost(previous, current, targetStepLength);
                    if (!Double.isFinite(transition)) {
                        continue;
                    }
                    double cost = costs[layerIndex - 1][previousIndex] + current.localScore() + transition;
                    if (cost < costs[layerIndex][currentIndex]) {
                        costs[layerIndex][currentIndex] = cost;
                        predecessors[layerIndex][currentIndex] = previousIndex;
                    }
                }
            }
        }
        int selectedIndex = 0;
        double selectedCost = costs[layerCount - 1][0];
        for (int candidateIndex = 1; candidateIndex < costs[layerCount - 1].length; candidateIndex++) {
            if (costs[layerCount - 1][candidateIndex] < selectedCost) {
                selectedIndex = candidateIndex;
                selectedCost = costs[layerCount - 1][candidateIndex];
            }
        }
        if (!Double.isFinite(selectedCost)) {
            return new RouteCandidate[0];
        }
        RouteCandidate[] route = new RouteCandidate[layerCount];
        for (int layerIndex = layerCount - 1; layerIndex >= 0; layerIndex--) {
            route[layerIndex] = layers.get(layerIndex).get(selectedIndex);
            selectedIndex = predecessors[layerIndex][selectedIndex];
        }
        return route;
    }

    private TerrainTransitionKey unsupportedTerrainTransition(RouteCandidate[] route) {
        for (int routeIndex = 0; routeIndex < route.length - 1; routeIndex++) {
            RouteCandidate start = route[routeIndex];
            RouteCandidate end = route[routeIndex + 1];
            if (!traversableTerrainTransition(start.point(), end.point())) {
                return new TerrainTransitionKey(start.point(), end.point());
            }
        }
        return null;
    }

    private RouteCandidate[] selectCurvatureAwareTerrainRoute(
            List<List<RouteCandidate>> layers,
            double targetStepLength,
            double maximumTurn,
            double turnCost
    ) {
        int layerCount = layers.size();
        if (layerCount < 2) {
            return new RouteCandidate[0];
        }
        double[][] localPenalties = new double[layerCount][];
        double[][][] transitionCosts = new double[layerCount][][];
        double[][][][] turnCosts = new double[layerCount][][][];
        for (int layerIndex = 0; layerIndex < layerCount; layerIndex++) {
            int layerSize = layers.get(layerIndex).size();
            localPenalties[layerIndex] = new double[layerSize];
            if (layerIndex > 0 && layerIndex < layerCount - 1) {
                Arrays.fill(localPenalties[layerIndex], Double.NaN);
            }
            if (layerIndex == 0) {
                continue;
            }
            int previousSize = layers.get(layerIndex - 1).size();
            transitionCosts[layerIndex] = new double[previousSize][layerSize];
            for (double[] row : transitionCosts[layerIndex]) {
                Arrays.fill(row, Double.NaN);
            }
        }
        while (true) {
            CurvatureRouteSelection selection = selectCurvatureAwareTerrainRoute(
                    layers,
                    targetStepLength,
                    maximumTurn,
                    turnCost,
                    localPenalties,
                    transitionCosts,
                    turnCosts
            );
            if (selection.route().length == 0) {
                return selection.route();
            }
            boolean evaluated = evaluateSelectedSurfaceCosts(
                    selection,
                    layers,
                    localPenalties,
                    transitionCosts,
                    targetStepLength
            );
            if (!evaluated) {
                return selection.route();
            }
        }
    }

    private CurvatureRouteSelection selectCurvatureAwareTerrainRoute(
            List<List<RouteCandidate>> layers,
            double targetStepLength,
            double maximumTurn,
            double turnCost,
            double[][] localPenalties,
            double[][][] transitionCosts,
            double[][][][] turnCosts
    ) {
        int layerCount = layers.size();
        double[][][] costs = new double[layerCount][][];
        int[][][] predecessors = new int[layerCount][][];
        List<RouteCandidate> firstLayer = layers.getFirst();
        List<RouteCandidate> secondLayer = layers.get(1);
        costs[1] = new double[firstLayer.size()][secondLayer.size()];
        predecessors[1] = new int[firstLayer.size()][secondLayer.size()];
        for (int firstIndex = 0; firstIndex < firstLayer.size(); firstIndex++) {
            Arrays.fill(costs[1][firstIndex], Double.POSITIVE_INFINITY);
            Arrays.fill(predecessors[1][firstIndex], -1);
            for (int secondIndex = 0; secondIndex < secondLayer.size(); secondIndex++) {
                RouteCandidate first = firstLayer.get(firstIndex);
                RouteCandidate second = secondLayer.get(secondIndex);
                double transition = resolvedTransitionCost(
                        first,
                        second,
                        targetStepLength,
                        transitionCosts[1][firstIndex][secondIndex]
                );
                if (Double.isFinite(transition)) {
                    costs[1][firstIndex][secondIndex] = first.localScore()
                            + second.localScore()
                            + resolvedLocalPenalty(localPenalties[1][secondIndex])
                            + transition;
                }
            }
        }
        for (int layerIndex = 2; layerIndex < layerCount; layerIndex++) {
            List<RouteCandidate> beforeLayer = layers.get(layerIndex - 2);
            List<RouteCandidate> previousLayer = layers.get(layerIndex - 1);
            List<RouteCandidate> currentLayer = layers.get(layerIndex);
            costs[layerIndex] = new double[previousLayer.size()][currentLayer.size()];
            predecessors[layerIndex] = new int[previousLayer.size()][currentLayer.size()];
            if (turnCosts[layerIndex] == null) {
                turnCosts[layerIndex] = new double[previousLayer.size()][currentLayer.size()][];
            }
            for (int previousIndex = 0; previousIndex < previousLayer.size(); previousIndex++) {
                Arrays.fill(costs[layerIndex][previousIndex], Double.POSITIVE_INFINITY);
                Arrays.fill(predecessors[layerIndex][previousIndex], -1);
                RouteCandidate previous = previousLayer.get(previousIndex);
                for (int currentIndex = 0; currentIndex < currentLayer.size(); currentIndex++) {
                    RouteCandidate current = currentLayer.get(currentIndex);
                    double transition = resolvedTransitionCost(
                            previous,
                            current,
                            targetStepLength,
                            transitionCosts[layerIndex][previousIndex][currentIndex]
                    );
                    if (!Double.isFinite(transition)) {
                        continue;
                    }
                    double localPenalty = resolvedLocalPenalty(localPenalties[layerIndex][currentIndex]);
                    for (int beforeIndex = 0; beforeIndex < beforeLayer.size(); beforeIndex++) {
                        double previousCost = costs[layerIndex - 1][beforeIndex][previousIndex];
                        if (!Double.isFinite(previousCost)) {
                            continue;
                        }
                        double[] candidateTurns = turnCosts[layerIndex][previousIndex][currentIndex];
                        if (candidateTurns == null) {
                            candidateTurns = new double[beforeLayer.size()];
                            Arrays.fill(candidateTurns, Double.NaN);
                            turnCosts[layerIndex][previousIndex][currentIndex] = candidateTurns;
                        }
                        double turnPenalty = candidateTurns[beforeIndex];
                        if (Double.isNaN(turnPenalty)) {
                            double turn = routeTurnDegrees(
                                    beforeLayer.get(beforeIndex).point(),
                                    previous.point(),
                                    current.point()
                            );
                            turnPenalty = turn > maximumTurn
                                    ? Double.POSITIVE_INFINITY
                                    : curvatureCost(turn, maximumTurn, turnCost);
                            candidateTurns[beforeIndex] = turnPenalty;
                        }
                        if (turnPenalty == Double.POSITIVE_INFINITY) {
                            continue;
                        }
                        double cost = previousCost
                                + current.localScore()
                                + localPenalty
                                + transition
                                + turnPenalty;
                        if (cost < costs[layerIndex][previousIndex][currentIndex]) {
                            costs[layerIndex][previousIndex][currentIndex] = cost;
                            predecessors[layerIndex][previousIndex][currentIndex] = beforeIndex;
                        }
                    }
                }
            }
        }
        int lastLayerIndex = layerCount - 1;
        int selectedPreviousIndex = -1;
        int selectedCurrentIndex = -1;
        double selectedCost = Double.POSITIVE_INFINITY;
        for (int previousIndex = 0; previousIndex < costs[lastLayerIndex].length; previousIndex++) {
            for (int currentIndex = 0;
                 currentIndex < costs[lastLayerIndex][previousIndex].length;
                 currentIndex++) {
                if (costs[lastLayerIndex][previousIndex][currentIndex] < selectedCost) {
                    selectedCost = costs[lastLayerIndex][previousIndex][currentIndex];
                    selectedPreviousIndex = previousIndex;
                    selectedCurrentIndex = currentIndex;
                }
            }
        }
        if (!Double.isFinite(selectedCost)) {
            return CurvatureRouteSelection.empty();
        }
        RouteCandidate[] route = new RouteCandidate[layerCount];
        int[] indices = new int[layerCount];
        route[lastLayerIndex] = layers.get(lastLayerIndex).get(selectedCurrentIndex);
        route[lastLayerIndex - 1] = layers.get(lastLayerIndex - 1).get(selectedPreviousIndex);
        indices[lastLayerIndex] = selectedCurrentIndex;
        indices[lastLayerIndex - 1] = selectedPreviousIndex;
        for (int layerIndex = lastLayerIndex; layerIndex >= 2; layerIndex--) {
            int beforeIndex = predecessors[layerIndex][selectedPreviousIndex][selectedCurrentIndex];
            if (beforeIndex < 0) {
                return CurvatureRouteSelection.empty();
            }
            route[layerIndex - 2] = layers.get(layerIndex - 2).get(beforeIndex);
            indices[layerIndex - 2] = beforeIndex;
            selectedCurrentIndex = selectedPreviousIndex;
            selectedPreviousIndex = beforeIndex;
        }
        return new CurvatureRouteSelection(route, indices);
    }

    private boolean evaluateSelectedSurfaceCosts(
            CurvatureRouteSelection selection,
            List<List<RouteCandidate>> layers,
            double[][] localPenalties,
            double[][][] transitionCosts,
            double targetStepLength
    ) {
        boolean evaluated = false;
        RouteCandidate[] route = selection.route();
        int[] indices = selection.indices();
        for (int layerIndex = 1; layerIndex < route.length - 1; layerIndex++) {
            RouteCandidate candidate = route[layerIndex];
            if (candidate.terrainResolved()) {
                continue;
            }
            RouteCandidate resolved = resolveRouteCandidate(candidate);
            ArrayList<RouteCandidate> updatedLayer = new ArrayList<>(layers.get(layerIndex));
            updatedLayer.set(indices[layerIndex], resolved);
            layers.set(layerIndex, List.copyOf(updatedLayer));
            route[layerIndex] = resolved;
            evaluated = true;
        }
        for (int layerIndex = 1; layerIndex < route.length - 1; layerIndex++) {
            int candidateIndex = indices[layerIndex];
            if (!Double.isNaN(localPenalties[layerIndex][candidateIndex])) {
                continue;
            }
            if (!Double.isFinite(route[layerIndex].localScore())) {
                localPenalties[layerIndex][candidateIndex] = 0D;
                continue;
            }
            localPenalties[layerIndex][candidateIndex] = surfaceRouteCandidateBankPenalty(
                    route[layerIndex]
            );
            evaluated = true;
        }
        for (int layerIndex = 1; layerIndex < route.length; layerIndex++) {
            int previousIndex = indices[layerIndex - 1];
            int currentIndex = indices[layerIndex];
            if (!Double.isNaN(transitionCosts[layerIndex][previousIndex][currentIndex])) {
                continue;
            }
            RouteCandidate previous = route[layerIndex - 1];
            RouteCandidate current = route[layerIndex];
            if (!Double.isFinite(previous.localScore()) || !Double.isFinite(current.localScore())) {
                transitionCosts[layerIndex][previousIndex][currentIndex] = Double.POSITIVE_INFINITY;
                continue;
            }
            double transition = routeTransitionCost(previous, current, targetStepLength);
            if (Double.isFinite(transition)) {
                transition += surfaceRouteTransitionPenalty(previous, current);
            }
            transitionCosts[layerIndex][previousIndex][currentIndex] = transition;
            evaluated = true;
        }
        return evaluated;
    }

    private double resolvedLocalPenalty(double penalty) {
        return Double.isNaN(penalty) ? 0D : penalty;
    }

    private double resolvedTransitionCost(
            RouteCandidate previous,
            RouteCandidate current,
            double targetStepLength,
            double exactCost
    ) {
        return Double.isNaN(exactCost)
                ? routeTransitionGeometryCost(previous, current, targetStepLength)
                : exactCost;
    }

    private double curvatureCost(double turn, double maximumTurn, double turnCost) {
        double excess = Math.max(0D, turn - 8D);
        return excess * excess * turnCost / maximumTurn;
    }

    private double surfaceRouteTransitionPenalty(
            RouteCandidate start,
            RouteCandidate end
    ) {
        int minimumBankDistance = surfaceBankDistance(settings.surface().minimumWidth());
        int maximumBankDistance = surfaceBankDistance(settings.surface().maximumWidth());
        int maximumBankRise = Math.addExact(
                settings.surface().maximumIncision(),
                (int) StrictMath.ceil(settings.surface().banks().minimumBlendWidth() * 0.5D)
        );
        Direction tangent = direction(start.point().x(), start.point().z(), end.point().x(), end.point().z());
        int endpointCeiling = Math.max(start.point().y(), end.point().y());
        double penalty = 0D;
        List<HydrologyPoint> raster = rasterLine(start.point(), end.point());
        int sampleStride = Math.max(1, settings.routing().refinementSpacing() / 4);
        for (int pointIndex = 0; pointIndex < raster.size(); pointIndex += sampleStride) {
            HydrologyPoint point = raster.get(pointIndex);
            HydrologyTerrainSample terrain = sampleLandBasis(point.x(), point.z());
            if (terrain == null) {
                return 1.0E12D;
            }
            int permittedIncision = permittedSurfaceIncision(terrain);
            int ridgeExcess = Math.max(
                    0,
                    terrain.naturalHeight() - endpointCeiling - permittedIncision
            );
            penalty += (double) ridgeExcess * ridgeExcess * 65536D;
            if (terrain.slope() >= settings.hydraulics().waterfallMinimumDrop()) {
                continue;
            }
            int head = Math.subtractExact(
                    terrain.naturalHeight(),
                    settings.surface().banks().sink()
            );
            penalty += surfaceRouteBankPenalty(
                    point,
                    tangent,
                    minimumBankDistance,
                    maximumBankDistance,
                    head,
                    maximumBankRise
            );
        }
        if ((raster.size() - 1) % sampleStride != 0) {
            HydrologyPoint point = raster.getLast();
            HydrologyTerrainSample terrain = sampleLandBasis(point.x(), point.z());
            if (terrain == null) {
                return 1.0E12D;
            }
            int permittedIncision = permittedSurfaceIncision(terrain);
            int ridgeExcess = Math.max(
                    0,
                    terrain.naturalHeight() - endpointCeiling - permittedIncision
            );
            penalty += (double) ridgeExcess * ridgeExcess * 65536D;
            if (terrain.slope() < settings.hydraulics().waterfallMinimumDrop()) {
                int head = Math.subtractExact(
                        terrain.naturalHeight(),
                        settings.surface().banks().sink()
                );
                penalty += surfaceRouteBankPenalty(
                        point,
                        tangent,
                        minimumBankDistance,
                        maximumBankDistance,
                        head,
                        maximumBankRise
                );
            }
        }
        return penalty;
    }

    private int removeTerrainPits(List<List<RouteCandidate>> layers, RouteCandidate[] route) {
        int removed = 0;
        for (int routeIndex = 1; routeIndex < route.length - 1; routeIndex++) {
            RouteCandidate candidate = route[routeIndex];
            if (!isTerrainPit(candidate)) {
                continue;
            }
            ArrayList<RouteCandidate> candidates = new ArrayList<>(layers.get(routeIndex));
            candidates.remove(candidate);
            if (candidates.isEmpty()) {
                return -1;
            }
            layers.set(routeIndex, List.copyOf(candidates));
            removed++;
        }
        return removed;
    }

    private boolean isTerrainPit(RouteCandidate candidate) {
        HydrologyPoint point = candidate.point();
        Direction tangent = candidate.tangent();
        int refinement = settings.routing().refinementSpacing();
        int offsetX = (int) StrictMath.round(tangent.x() * refinement);
        int offsetZ = (int) StrictMath.round(tangent.z() * refinement);
        if (offsetX == 0 && offsetZ == 0) {
            return false;
        }
        HydrologyTerrainSample before = sampleLandBasisWithoutSlope(point.x() - offsetX, point.z() - offsetZ);
        HydrologyTerrainSample after = sampleLandBasisWithoutSlope(point.x() + offsetX, point.z() + offsetZ);
        int threshold = settings.hydraulics().waterfallMinimumDrop();
        return before != null
                && after != null
                && before.naturalHeight() - point.y() >= threshold
                && after.naturalHeight() - point.y() >= threshold;
    }

    private List<HydrologyPoint> smoothTerrainRoute(List<HydrologyPoint> route) {
        List<HydrologyPoint> current = List.copyOf(route);
        List<HydrologyPoint> best = current;
        double bestTurnScore = routeTurnScore(current);
        HydrologyPlannerSettings.Meanders meanders = settings.geometry().meanders();
        int maximumPasses = Math.max(8, meanders.smoothingPasses() * 4);
        for (int pass = 0; pass < maximumPasses; pass++) {
            ArrayList<HydrologyPoint> smoothed = new ArrayList<>(current.size());
            smoothed.add(current.getFirst());
            for (int pointIndex = 1; pointIndex < current.size() - 1; pointIndex++) {
                HydrologyPoint previous = current.get(pointIndex - 1);
                HydrologyPoint point = current.get(pointIndex);
                HydrologyPoint next = current.get(pointIndex + 1);
                int x = (int) StrictMath.round(previous.x() * 0.25D + point.x() * 0.5D + next.x() * 0.25D);
                int z = (int) StrictMath.round(previous.z() * 0.25D + point.z() * 0.5D + next.z() * 0.25D);
                HydrologyTerrainSample terrain = sampleLandBasisWithoutSlope(x, z);
                smoothed.add(terrain == null || !terrain.transitAllowed()
                        ? point
                        : new HydrologyPoint(x, terrain.naturalHeight(), z));
            }
            smoothed.add(current.getLast());
            List<HydrologyPoint> candidate = withoutDuplicateRoutePoints(smoothed);
            if (candidate.size() < 2 || !traversableRoute(candidate)) {
                break;
            }
            current = candidate;
            double candidateTurnScore = routeTurnScore(candidate);
            if (candidateTurnScore < bestTurnScore) {
                best = candidate;
                bestTurnScore = candidateTurnScore;
            }
        }
        List<HydrologyPoint> smoothed = removeRouteHairpins(best, meanders.maximumTurnDegrees());
        return containsTerrainPit(smoothed) ? List.of() : smoothed;
    }

    private List<HydrologyPoint> removeRouteHairpins(List<HydrologyPoint> route, double maximumTurnDegrees) {
        ArrayList<HydrologyPoint> points = new ArrayList<>(route);
        while (points.size() > 2) {
            int sharpestIndex = sharpestRoutePoint(points);
            if (sharpestIndex < 1 || routeTurnDegrees(
                    points.get(sharpestIndex - 1),
                    points.get(sharpestIndex),
                    points.get(sharpestIndex + 1)
            ) <= maximumTurnDegrees) {
                break;
            }
            HydrologyPoint previous = points.get(sharpestIndex - 1);
            HydrologyPoint next = points.get(sharpestIndex + 1);
            if (!traversableHop(previous, next) || crossesTerrainCrevasse(previous, next)) {
                break;
            }
            points.remove(sharpestIndex);
        }
        return List.copyOf(points);
    }

    private int sharpestRoutePoint(List<HydrologyPoint> route) {
        int selectedIndex = -1;
        double selectedTurn = 0D;
        for (int pointIndex = 1; pointIndex < route.size() - 1; pointIndex++) {
            double turn = routeTurnDegrees(
                    route.get(pointIndex - 1),
                    route.get(pointIndex),
                    route.get(pointIndex + 1)
            );
            if (turn > selectedTurn) {
                selectedIndex = pointIndex;
                selectedTurn = turn;
            }
        }
        return selectedIndex;
    }

    private double routeTurnDegrees(HydrologyPoint previous, HydrologyPoint point, HydrologyPoint next) {
        double incomingX = point.x() - previous.x();
        double incomingZ = point.z() - previous.z();
        double outgoingX = next.x() - point.x();
        double outgoingZ = next.z() - point.z();
        double incomingLength = StrictMath.hypot(incomingX, incomingZ);
        double outgoingLength = StrictMath.hypot(outgoingX, outgoingZ);
        if (incomingLength == 0D || outgoingLength == 0D) {
            return 0D;
        }
        double cosine = (incomingX * outgoingX + incomingZ * outgoingZ)
                / (incomingLength * outgoingLength);
        return StrictMath.toDegrees(StrictMath.acos(Math.max(-1D, Math.min(1D, cosine))));
    }

    private List<HydrologyPoint> withoutDuplicateRoutePoints(List<HydrologyPoint> points) {
        ArrayList<HydrologyPoint> unique = new ArrayList<>(points.size());
        for (HydrologyPoint point : points) {
            if (unique.isEmpty()
                    || point.x() != unique.getLast().x()
                    || point.z() != unique.getLast().z()) {
                unique.add(point);
            }
        }
        return List.copyOf(unique);
    }

    private boolean traversableRoute(List<HydrologyPoint> route) {
        for (int pointIndex = 0; pointIndex < route.size() - 1; pointIndex++) {
            HydrologyPoint start = route.get(pointIndex);
            HydrologyPoint end = route.get(pointIndex + 1);
            if (!traversableHop(start, end) || crossesTerrainCrevasse(start, end)) {
                return false;
            }
        }
        return true;
    }

    private double routeTransitionCost(
            RouteCandidate previous,
            RouteCandidate current,
            double targetStepLength
    ) {
        if (!traversableTerrainTransition(previous.point(), current.point())) {
            return Double.POSITIVE_INFINITY;
        }
        return routeTransitionGeometryCost(previous, current, targetStepLength);
    }

    private double routeTransitionGeometryCost(
            RouteCandidate previous,
            RouteCandidate current,
            double targetStepLength
    ) {
        double deltaX = current.point().x() - previous.point().x();
        double deltaZ = current.point().z() - previous.point().z();
        double length = StrictMath.hypot(deltaX, deltaZ);
        if (length <= 0D || length > targetStepLength * 5D + 8D) {
            return Double.POSITIVE_INFINITY;
        }
        double transverseChange = StrictMath.abs(current.offset() - previous.offset());
        double alignment = (deltaX * current.tangent().x() + deltaZ * current.tangent().z()) / length;
        if (alignment < -0.5D) {
            return Double.POSITIVE_INFINITY;
        }
        int rise = Math.max(0, current.point().y() - previous.point().y());
        return StrictMath.abs(length - targetStepLength) * 1.4D
                + transverseChange * 5D
                + (1D - alignment) * 32D
                + rise * settings.routing().uphillPenalty();
    }

    private boolean traversableTerrainTransition(HydrologyPoint start, HydrologyPoint end) {
        List<HydrologyPoint> crossing = rasterLine(start, end);
        HydrologyTerrainSample startTerrain = sampleLandBasisWithoutSlope(start.x(), start.z());
        HydrologyTerrainSample endTerrain = sampleLandBasisWithoutSlope(end.x(), end.z());
        if (startTerrain == null || endTerrain == null) {
            return false;
        }
        int boundaryHeight = Math.min(startTerrain.naturalHeight(), endTerrain.naturalHeight());
        int threshold = settings.hydraulics().waterfallMinimumDrop();
        for (int pointIndex = 1; pointIndex < crossing.size() - 1; pointIndex++) {
            HydrologyPoint point = crossing.get(pointIndex);
            HydrologyTerrainSample terrain = sampleLandBasisWithoutSlope(point.x(), point.z());
            if (terrain == null
                    || !terrain.transitAllowed()
                    || boundaryHeight - terrain.naturalHeight() >= threshold) {
                return false;
            }
        }
        return true;
    }

    private boolean crossesTerrainCrevasse(HydrologyPoint start, HydrologyPoint end) {
        List<HydrologyPoint> crossing = rasterLine(start, end);
        if (crossing.size() < 3) {
            return false;
        }
        HydrologyTerrainSample startTerrain = sampleLandBasisWithoutSlope(start.x(), start.z());
        HydrologyTerrainSample endTerrain = sampleLandBasisWithoutSlope(end.x(), end.z());
        if (startTerrain == null || endTerrain == null) {
            return true;
        }
        int boundaryHeight = Math.min(startTerrain.naturalHeight(), endTerrain.naturalHeight());
        int threshold = settings.hydraulics().waterfallMinimumDrop();
        for (int pointIndex = 1; pointIndex < crossing.size() - 1; pointIndex++) {
            HydrologyPoint point = crossing.get(pointIndex);
            HydrologyTerrainSample terrain = sampleLandBasisWithoutSlope(point.x(), point.z());
            if (terrain == null || boundaryHeight - terrain.naturalHeight() >= threshold) {
                return true;
            }
        }
        return false;
    }

    private Direction direction(int startX, int startZ, int endX, int endZ) {
        double x = endX - startX;
        double z = endZ - startZ;
        double length = StrictMath.hypot(x, z);
        if (length <= 0D) {
            return new Direction(0D, 0D);
        }
        return new Direction(x / length, z / length);
    }

    private double hermite(double start, double end, double startTangent, double endTangent, double progress) {
        double squared = progress * progress;
        double cubed = squared * progress;
        double startBasis = 2D * cubed - 3D * squared + 1D;
        double startTangentBasis = cubed - 2D * squared + progress;
        double endBasis = -2D * cubed + 3D * squared;
        double endTangentBasis = cubed - squared;
        return startBasis * start
                + startTangentBasis * startTangent
                + endBasis * end
                + endTangentBasis * endTangent;
    }

    private double hermiteDerivative(
            double start,
            double end,
            double startTangent,
            double endTangent,
            double progress
    ) {
        double squared = progress * progress;
        return (6D * squared - 6D * progress) * start
                + (3D * squared - 4D * progress + 1D) * startTangent
                + (-6D * squared + 6D * progress) * end
                + (3D * squared - 2D * progress) * endTangent;
    }

    private boolean traversableHop(HydrologyPoint start, HydrologyPoint end) {
        List<HydrologyPoint> crossing = rasterLine(start, end);
        for (int pointIndex = 1; pointIndex < crossing.size() - 1; pointIndex++) {
            HydrologyPoint point = crossing.get(pointIndex);
            HydrologyTerrainSample terrain = sampleLandBasisWithoutSlope(point.x(), point.z());
            if (terrain == null || terrain.ocean() || !terrain.transitAllowed()) {
                return false;
            }
        }
        return true;
    }

    private void compileSurfaceCourses(
            HydrologyTileKey key,
            SampledGrid grid,
            RoutingPlan routing,
            CompiledGraph graph,
            List<Integer> sources,
            List<RiverCourse> courses,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        LinkedHashMap<Long, ArrayList<SurfaceCourseDraft>> draftsByOutlet = new LinkedHashMap<>();
        for (int source : sources) {
            GridNode sourceNode = grid.node(source);
            long sourceCourseId = HydrologyHash.mix(
                    worldSeed,
                    COURSE_SALT,
                    SURFACE_SOURCE_SALT,
                    sourceNode.id()
            );
            CoursePath path = coursePath(grid, routing, graph, source, true);
            if (path == null) {
                addCompiledSourceDiagnostic(
                        sourceNode,
                        true,
                        sourceCourseId,
                        HydrologyCandidateRejection.NO_DRAINAGE_PATH,
                        diagnostics
                );
                continue;
            }
            long courseId = HydrologyHash.mix(sourceCourseId, path.outlet().id());
            HydrologyPoint trunkPoint = path.points().getFirst();
            HydrologyTerrainSample trunkTerrain = Objects.requireNonNull(
                    sampleDetailed(trunkPoint.x(), trunkPoint.z()),
                    "Hydrology surface trunk left sampled terrain"
            );
            String profileKey = chooseProfile(trunkTerrain, path.outlet().id());
            draftsByOutlet.computeIfAbsent(path.outlet().id(), (Long ignored) -> new ArrayList<>())
                    .add(new SurfaceCourseDraft(sourceNode, courseId, profileKey, path));
        }
        for (ArrayList<SurfaceCourseDraft> outletDrafts : draftsByOutlet.values()) {
            outletDrafts.sort(Comparator
                    .comparingDouble((SurfaceCourseDraft draft) -> edgeLength(
                            draft.path().edges(),
                            draft.path().edges().size()
                    ))
                    .reversed()
                    .thenComparingLong(SurfaceCourseDraft::courseId));
            RiverCourse mainCourse = null;
            HashMap<Long, SurfaceCourseBuild> mainRejections = new HashMap<>();
            int tributaries = 0;
            for (SurfaceCourseDraft draft : outletDrafts) {
                if (mainCourse != null && tributaries >= settings.routing().tributaries()) {
                    break;
                }
                if (mainCourse == null) {
                    SurfaceCourseBuild candidate = buildSurfaceCourse(
                            draft.courseId(),
                            draft.profileKey(),
                            draft.source(),
                            draft.path()
                    );
                    if (candidate.course() == null) {
                        mainRejections.put(draft.courseId(), candidate);
                        continue;
                    }
                    mainCourse = candidate.course();
                    courses.add(mainCourse);
                    continue;
                }
                RiverCourse tributary = buildSurfaceTributary(draft, mainCourse, diagnostics);
                if (tributary != null) {
                    courses.add(tributary);
                    tributaries++;
                }
            }
            if (mainCourse == null) {
                for (SurfaceCourseDraft draft : outletDrafts) {
                    SurfaceCourseBuild rejected = mainRejections.get(draft.courseId());
                    addCompiledSourceDiagnostic(
                            draft.source(),
                            true,
                            draft.courseId(),
                            rejected.rejection(),
                            rejected.rejectionDetail(),
                            diagnostics
                    );
                }
            }
        }
    }

    private SurfaceCourseBuild buildSurfaceCourse(
            long courseId,
            String profileKey,
            GridNode source,
            CoursePath initialPath
    ) {
        return buildSurfaceCourseAtPath(courseId, profileKey, source, initialPath);
    }

    private SurfaceCourseBuild buildSurfaceCourseAtPath(
            long courseId,
            String profileKey,
            GridNode source,
            CoursePath path
    ) {
        if (path.points().size() < 2) {
            return SurfaceCourseBuild.rejected(HydrologyCandidateRejection.COURSE_TOO_SHORT, 0);
        }
        RiverOutlet outlet = path.outlet();
        boolean surfaceSinkhole = path.reachesOutlet() && surfaceSinkhole(outlet);
        SurfaceTerminal terminal;
        if (!path.reachesOutlet() || outlet.type() == HydrologyFeatureType.INLAND_GROTTO) {
            terminal = SurfaceTerminal.SINKHOLE;
        } else if (outlet.type() == HydrologyFeatureType.COASTAL_GROTTO) {
            terminal = SurfaceTerminal.COASTAL_GROTTO;
        } else {
            terminal = SurfaceTerminal.OCEAN_MOUTH;
        }
        int outletHead = outletHead(outlet);
        int terminalHead = surfaceSinkhole ? Math.addExact(outletHead, 1) : outletHead;
        SurfaceCourseResult result = surfaceCourseBuilder.build(
                worldSeed,
                courseId,
                profileKey,
                path.points(),
                terminal,
                terminalHead
        );
        if (!result.accepted()) {
            return SurfaceCourseBuild.rejected(result.rejection(), result.rejectionDetail());
        }
        ArrayList<HydraulicSegment> segments = new ArrayList<>(result.segments());
        if (path.reachesOutlet()) {
            CoursePath terminalPath = new CoursePath(
                    List.of(result.pathEnd()),
                    path.pairEdges(),
                    path.edges(),
                    outlet,
                    true,
                    path.organicSurfaceRequired(),
                    null
            );
            appendOutletSegments(
                    RiverCourseType.SURFACE,
                    courseId,
                    terminalPath,
                    result.lastHead(),
                    new int[] {result.lastWidth()},
                    new int[] {result.lastDepth()},
                    segments
            );
        }
        if (path.organicSurfaceRequired()) {
            int shapeRejection = surfaceShapeRejection(path.points());
            if (shapeRejection != 0) {
                return SurfaceCourseBuild.rejected(HydrologyCandidateRejection.SURFACE_SHAPE_UNSUPPORTED, shapeRejection);
            }
        }
        int discharge = maximumSurfaceDischarge(path.edges());
        return SurfaceCourseBuild.accepted(new RiverCourse(
                courseId,
                RiverCourseType.SURFACE,
                OptionalLong.of(source.id()),
                OptionalLong.of(outlet.id()),
                profileKey,
                discharge,
                path.edges(),
                segments
        ));
    }

    /** 0 when the shape is organic; otherwise 1 too short, 2 too sinuous, 3 grid-locked, 4 sharp turns. */
    private int surfaceShapeRejection(List<HydrologyPoint> routedCenterline) {
        return surfaceShapeRejection(routedCenterline, false);
    }

    /**
     * As above; a tributary reach keeps the hard turn cap and the overall turn budget but may bend
     * once where it leaves its own valley for the stem's, which a main river is not allowed to do.
     */
    private int surfaceShapeRejection(List<HydrologyPoint> routedCenterline, boolean tributary) {
        List<HydrologyPoint> centerline = withoutDuplicateRoutePoints(routedCenterline);
        if (centerline.size() < 3) {
            return 1;
        }
        double routedLength = centerlineLength(centerline);
        HydrologyPoint start = centerline.getFirst();
        HydrologyPoint end = centerline.getLast();
        double directLength = StrictMath.hypot(end.x() - start.x(), end.z() - start.z());
        if (directLength <= 0D
                || routedLength / directLength > SURFACE_MAXIMUM_SINUOSITY) {
            return 2;
        }
        List<HydrologyPoint> sampled = resampleRouteCenterline(
                centerline,
                Math.max(16D, settings.routing().refinementSpacing() * 4D)
        );
        double lockedLength = 0D;
        double currentLockedRun = 0D;
        double longestLockedRun = 0D;
        for (int pointIndex = 1; pointIndex < sampled.size(); pointIndex++) {
            HydrologyPoint previous = sampled.get(pointIndex - 1);
            HydrologyPoint point = sampled.get(pointIndex);
            double deltaX = point.x() - previous.x();
            double deltaZ = point.z() - previous.z();
            double length = StrictMath.hypot(deltaX, deltaZ);
            if (length <= 0D) {
                continue;
            }
            double angle = StrictMath.toDegrees(StrictMath.atan2(deltaZ, deltaX));
            double remainder = Math.floorMod(
                    (int) StrictMath.round(angle * 1_000_000D),
                    45_000_000
            ) / 1_000_000D;
            double gridDistance = Math.min(remainder, 45D - remainder);
            if (gridDistance <= SURFACE_GRID_LOCK_TOLERANCE_DEGREES) {
                lockedLength += length;
                currentLockedRun += length;
                longestLockedRun = Math.max(longestLockedRun, currentLockedRun);
            } else {
                currentLockedRun = 0D;
            }
        }
        if (routedLength >= 96D
                && (lockedLength / routedLength > SURFACE_MAXIMUM_GRID_LOCKED_FRACTION
                || longestLockedRun > SURFACE_MAXIMUM_GRID_LOCKED_RUN)) {
            return 3;
        }
        ArrayList<Double> turnAngles = new ArrayList<>();
        for (int pointIndex = 1; pointIndex < sampled.size() - 1; pointIndex++) {
            double turn = routeTurnDegrees(
                    sampled.get(pointIndex - 1),
                    sampled.get(pointIndex),
                    sampled.get(pointIndex + 1)
            );
            if (turn > SURFACE_MAXIMUM_RENDERED_TURN_DEGREES) {
                return 4;
            }
            turnAngles.add(turn);
        }
        if (turnAngles.isEmpty()) {
            return 1;
        }
        for (int turnIndex = 0; turnIndex < turnAngles.size() && !tributary; turnIndex++) {
            double turn = turnAngles.get(turnIndex);
            double previous = turnIndex == 0 ? 0D : turnAngles.get(turnIndex - 1);
            double next = turnIndex + 1 == turnAngles.size() ? 0D : turnAngles.get(turnIndex + 1);
            if (turn > SURFACE_MAXIMUM_ISOLATED_TURN_DEGREES
                    && previous <= SURFACE_MAXIMUM_ISOLATED_NEIGHBOR_TURN_DEGREES
                    && next <= SURFACE_MAXIMUM_ISOLATED_NEIGHBOR_TURN_DEGREES) {
                return 4;
            }
        }
        turnAngles.sort(Double::compare);
        int percentileIndex = (int) StrictMath.ceil(0.95D * turnAngles.size()) - 1;
        double percentile = turnAngles.get(
                Math.max(0, Math.min(percentileIndex, turnAngles.size() - 1))
        );
        if (percentile > (tributary ? SURFACE_MAXIMUM_RENDERED_TURN_DEGREES : SURFACE_MAXIMUM_P95_TURN_DEGREES)) {
            return 4;
        }
        return 0;
    }

    private double centerlineLength(List<HydrologyPoint> points) {
        double length = 0D;
        for (int pointIndex = 1; pointIndex < points.size(); pointIndex++) {
            HydrologyPoint previous = points.get(pointIndex - 1);
            HydrologyPoint current = points.get(pointIndex);
            length += StrictMath.hypot(current.x() - previous.x(), current.z() - previous.z());
        }
        return length;
    }

    private List<HydrologyPoint> resampleRouteCenterline(
            List<HydrologyPoint> points,
            double spacing
    ) {
        double[] cumulative = new double[points.size()];
        for (int pointIndex = 1; pointIndex < points.size(); pointIndex++) {
            HydrologyPoint previous = points.get(pointIndex - 1);
            HydrologyPoint point = points.get(pointIndex);
            cumulative[pointIndex] = cumulative[pointIndex - 1]
                    + StrictMath.hypot(point.x() - previous.x(), point.z() - previous.z());
        }
        double totalLength = cumulative[cumulative.length - 1];
        if (totalLength <= spacing) {
            return points;
        }
        int samples = Math.max(2, (int) StrictMath.floor(totalLength / spacing));
        ArrayList<HydrologyPoint> sampled = new ArrayList<>(samples + 2);
        int segmentIndex = 0;
        for (double distance = 0D; distance < totalLength; distance += spacing) {
            while (segmentIndex < points.size() - 2
                    && cumulative[segmentIndex + 1] < distance) {
                segmentIndex++;
            }
            HydrologyPoint start = points.get(segmentIndex);
            HydrologyPoint end = points.get(segmentIndex + 1);
            double segmentLength = cumulative[segmentIndex + 1] - cumulative[segmentIndex];
            double progress = segmentLength <= 0D
                    ? 0D
                    : (distance - cumulative[segmentIndex]) / segmentLength;
            HydrologyPoint point = new HydrologyPoint(
                    (int) StrictMath.round(start.x() + (end.x() - start.x()) * progress),
                    (int) StrictMath.round(start.y() + (end.y() - start.y()) * progress),
                    (int) StrictMath.round(start.z() + (end.z() - start.z()) * progress)
            );
            if (sampled.isEmpty()
                    || point.x() != sampled.getLast().x()
                    || point.z() != sampled.getLast().z()) {
                sampled.add(point);
            }
        }
        HydrologyPoint last = points.getLast();
        HydrologyPoint sampledLast = sampled.isEmpty() ? null : sampled.getLast();
        double remaining = sampledLast == null
                ? Double.POSITIVE_INFINITY
                : StrictMath.hypot(last.x() - sampledLast.x(), last.z() - sampledLast.z());
        if (remaining >= spacing * 0.5D) {
            sampled.add(last);
        }
        return List.copyOf(sampled);
    }

    private void compileUndergroundCourses(
            HydrologyTileKey key,
            SampledGrid grid,
            RoutingPlan routing,
            CompiledGraph graph,
            List<Integer> sources,
            List<RiverCourse> courses,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        LinkedHashMap<Long, ArrayList<UndergroundCourseDraft>> draftsByOutlet = new LinkedHashMap<>();
        for (int sourceIndex : sources) {
            GridNode source = grid.node(sourceIndex);
            long courseId = HydrologyHash.mix(worldSeed, COURSE_SALT, UNDERGROUND_SOURCE_SALT, source.id());
            CoursePath path = coursePath(null, routing, graph, sourceIndex, false);
            if (path == null) {
                addCompiledSourceDiagnostic(
                        source,
                        false,
                        courseId,
                        HydrologyCandidateRejection.NO_DRAINAGE_PATH,
                        diagnostics
                );
                continue;
            }
            draftsByOutlet.computeIfAbsent(path.outlet().id(), (Long ignored) -> new ArrayList<>())
                    .add(new UndergroundCourseDraft(source, courseId, path));
        }
        for (ArrayList<UndergroundCourseDraft> outletDrafts : draftsByOutlet.values()) {
            outletDrafts.sort(Comparator
                    .comparingDouble((UndergroundCourseDraft draft) -> edgeLength(
                            draft.path().edges(),
                            draft.path().edges().size()
                    ))
                    .reversed()
                    .thenComparingLong(UndergroundCourseDraft::courseId));
            RiverCourse mainCourse = null;
            int tributaries = 0;
            for (UndergroundCourseDraft draft : outletDrafts) {
                if (mainCourse != null && tributaries >= settings.underground().tributaries()) {
                    break;
                }
                if (mainCourse == null) {
                    RiverCourse course = buildUndergroundCourse(draft, draft.path(), outletHead(draft.path().outlet()), diagnostics);
                    if (course != null) {
                        mainCourse = course;
                        courses.add(course);
                    }
                    continue;
                }
                RiverCourse tributary = buildUndergroundTributary(draft, mainCourse, diagnostics);
                if (tributary != null) {
                    courses.add(tributary);
                    tributaries++;
                }
            }
        }
    }

    private record UndergroundCourseDraft(GridNode source, long courseId, CoursePath path) {
    }

    /**
     * Builds a later underground draft to the stem's outlet as a tributary: the path is cut at the first
     * point the stem's passage already runs through, and the passage is solved with the stem's fluid
     * level there as its outlet level, so it joins the stem at or above its water.
     */
    private RiverCourse buildUndergroundTributary(
            UndergroundCourseDraft draft,
            RiverCourse stem,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        CoursePath path = draft.path();
        HashSet<Long> stemEdges = new HashSet<>();
        for (DrainageEdge edge : stem.drainageEdges()) {
            stemEdges.add(edge.id());
        }
        HashMap<Long, Integer> stemHeads = new HashMap<>();
        for (HydraulicSegment segment : stem.segments()) {
            for (HydrologyPoint point : segment.centerline()) {
                stemHeads.putIfAbsent(RiverFootprint.pack(point.x(), point.z()), point.y());
            }
        }
        int joinIndex = -1;
        for (int index = 0; index < path.points().size(); index++) {
            HydrologyPoint point = path.points().get(index);
            boolean sharedEdge = index > 0 && stemEdges.contains(path.pairEdges().get(index - 1).id());
            if (sharedEdge || stemHeads.containsKey(RiverFootprint.pack(point.x(), point.z()))) {
                joinIndex = index;
                break;
            }
        }
        if (joinIndex < 1 || !stemHeads.containsKey(RiverFootprint.pack(
                path.points().get(joinIndex).x(), path.points().get(joinIndex).z()))) {
            addCompiledSourceDiagnostic(draft.source(), false, draft.courseId(),
                    joinIndex < 0 ? HydrologyCandidateRejection.NO_DRAINAGE_PATH : HydrologyCandidateRejection.COURSE_TOO_SHORT,
                    diagnostics);
            return null;
        }
        List<HydrologyPoint> points = path.points().subList(0, joinIndex + 1);
        List<DrainageEdge> pairEdges = path.pairEdges().subList(0, joinIndex);
        LinkedHashSet<DrainageEdge> ownedEdges = new LinkedHashSet<>();
        for (DrainageEdge edge : pairEdges) {
            if (!stemEdges.contains(edge.id())) {
                ownedEdges.add(edge);
            }
        }
        if (ownedEdges.isEmpty()
                || centerlineLength(points) < settings.routing().minimumUndergroundCourseLength() / 2D) {
            addCompiledSourceDiagnostic(draft.source(), false, draft.courseId(),
                    HydrologyCandidateRejection.COURSE_TOO_SHORT, joinIndex, diagnostics);
            return null;
        }
        HydrologyPoint junction = points.getLast();
        CoursePath junctionPath = new CoursePath(
                List.copyOf(points),
                List.copyOf(pairEdges),
                List.copyOf(ownedEdges),
                path.outlet(),
                false,
                false,
                junction
        );
        int junctionHead = stemHeads.get(RiverFootprint.pack(junction.x(), junction.z()));
        return buildUndergroundCourse(draft, junctionPath, junctionHead, diagnostics);
    }

    private RiverCourse buildUndergroundCourse(
            UndergroundCourseDraft draft,
            CoursePath path,
            int outletHead,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        GridNode source = draft.source();
        long courseId = draft.courseId();
        HydrologyPoint sourcePoint = path.points().getFirst();
        HydrologyPoint trunkPoint = path.points().getLast();
        HydrologyTerrainSample trunkTerrain = Objects.requireNonNull(
                sampleDetailed(trunkPoint.x(), trunkPoint.z()),
                "Hydrology underground trunk left sampled terrain"
        );
        String profileKey = chooseProfile(trunkTerrain, path.outlet().id());
        int styledHead = sampleGeometry(
                HydrologyGeometrySampler.Field.UNDERGROUND_FLUID_LEVEL,
                profileKey,
                sourcePoint.x(),
                sourcePoint.z(),
                0L,
                settings.underground().minimumFluidY(),
                settings.underground().maximumFluidY()
        );
        if (outletHead > settings.underground().maximumFluidY()) {
            addCompiledSourceDiagnostic(
                    source,
                    false,
                    courseId,
                    HydrologyCandidateRejection.OUTLET_LEVEL,
                    diagnostics
            );
            return null;
        }
        int initialHead = Math.max(styledHead, outletHead);
        RiverCourse course = buildUndergroundCourse(courseId, profileKey, source, path, initialHead, outletHead);
        if (course == null) {
            addCompiledSourceDiagnostic(
                    source,
                    false,
                    courseId,
                    HydrologyCandidateRejection.CAVE_CONTAINMENT,
                    diagnostics
            );
        }
        return course;
    }

    private void addCompiledSourceDiagnostic(
            GridNode source,
            boolean surface,
            long courseId,
            HydrologyCandidateRejection rejection,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        addCompiledSourceDiagnostic(source, surface, courseId, rejection, 0, diagnostics);
    }

    private void addCompiledSourceDiagnostic(
            GridNode source,
            boolean surface,
            long courseId,
            HydrologyCandidateRejection rejection,
            int detail,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        int y = surface ? source.terrain().naturalHeight() : source.terrain().caveFluidY();
        diagnostics.add(new HydrologyDiagnosticCandidate(
                HydrologyHash.mix(courseId, DIAGNOSTIC_SALT, rejection.ordinal()),
                HydrologyCandidateKind.SOURCE,
                surface ? HydrologyFeatureType.SURFACE_POOL : HydrologyFeatureType.UNDERGROUND_POOL,
                new HydrologyPoint(source.x(), y, source.z()),
                rejection,
                detail
        ));
    }

    private RiverCourse buildUndergroundCourse(
            long courseId,
            String profileKey,
            GridNode source,
            CoursePath path,
            int initialHead,
            int outletHead
    ) {
        int pairCount = path.points().size() - 1;
        if (pairCount < 1) {
            return null;
        }
        int[] heads = new int[path.points().size()];
        int[] preferredHeads = new int[path.points().size()];
        int[] widths = new int[pairCount];
        int[] depths = new int[pairCount];
        preferredHeads[0] = initialHead;
        for (int index = 0; index < pairCount; index++) {
            HydrologyPoint point = path.points().get(index);
            HydrologyTerrainSample terrain = sampleBasisWithoutSlope(point.x(), point.z());
            int discharge = Math.max(1, path.pairEdges().get(index).contributingUndergroundSources());
            widths[index] = scaledDimension(
                    sampleGeometry(
                            HydrologyGeometrySampler.Field.UNDERGROUND_WIDTH,
                            profileKey,
                            point.x(),
                            point.z(),
                            0L,
                            settings.underground().minimumWidth(),
                            settings.underground().maximumWidth()
                    ),
                    settings.underground().minimumWidth(),
                    settings.underground().maximumWidth(),
                    discharge,
                    terrain.widthMultiplier()
            );
            depths[index] = scaledDimension(
                    sampleGeometry(
                            HydrologyGeometrySampler.Field.UNDERGROUND_DEPTH,
                            profileKey,
                            point.x(),
                            point.z(),
                            0L,
                            settings.underground().minimumDepth(),
                            settings.underground().maximumDepth()
                    ),
                    settings.underground().minimumDepth(),
                    settings.underground().maximumDepth(),
                    discharge,
                    terrain.depthMultiplier()
            );
            HydrologyPoint downstreamPoint = path.points().get(index + 1);
            int caveHead = sampleGeometry(
                    HydrologyGeometrySampler.Field.UNDERGROUND_FLUID_LEVEL,
                    profileKey,
                    downstreamPoint.x(),
                    downstreamPoint.z(),
                    0L,
                    settings.underground().minimumFluidY(),
                    settings.underground().maximumFluidY()
            );
            preferredHeads[index + 1] = caveHead;
        }
        int[] minimumHeads = undergroundMinimumHeads(path, depths, outletHead);
        int[] optimisticMaximumHeads = undergroundOptimisticMaximumHeads(path);
        if (!undergroundHeadsFeasible(minimumHeads, optimisticMaximumHeads, outletHead)) {
            return null;
        }
        int[] maximumHeads = undergroundMaximumHeads(courseId, profileKey, path, widths);
        if (!solveUndergroundHeads(preferredHeads, minimumHeads, maximumHeads, outletHead, heads)) {
            return null;
        }
        levelUndergroundMouth(path, heads, outletHead);
        ArrayList<HydraulicSegment> segments = new ArrayList<>(pairCount + 1);
        for (int index = 0; index < pairCount; index++) {
            int drop = heads[index] - heads[index + 1];
            HydrologyFeatureType type = drop > 0
                    ? HydrologyFeatureType.UNDERGROUND_DROP
                    : HydrologyFeatureType.UNDERGROUND_POOL;
            if (!addHydraulicSegments(
                    courseId,
                    index,
                    type,
                    heads[index],
                    heads[index + 1],
                    widths[index],
                    depths[index],
                    List.of(
                            withY(path.points().get(index), heads[index]),
                            withY(path.points().get(index + 1), heads[index + 1])
                    ),
                    false,
                    segments
            )) {
                return null;
            }
        }
        appendOutletSegments(
                RiverCourseType.UNDERGROUND,
                courseId,
                path,
                heads[heads.length - 1],
                widths,
                depths,
                segments
        );
        int discharge = maximumUndergroundDischarge(path.edges());
        return new RiverCourse(
                courseId,
                RiverCourseType.UNDERGROUND,
                OptionalLong.of(source.id()),
                OptionalLong.of(path.outlet().id()),
                profileKey,
                discharge,
                path.edges(),
                segments
        );
    }

    private int[] undergroundMaximumHeads(
            long courseId,
            String profileKey,
            CoursePath path,
            int[] widths
    ) {
        int[] segmentCaps = new int[widths.length];
        for (int index = 0; index < widths.length; index++) {
            int radius = Math.max(1, widths[index] / 2);
            segmentCaps[index] = undergroundSegmentCap(
                    courseId,
                    profileKey,
                    path.points().get(index),
                    path.points().get(index + 1),
                    radius
            );
        }
        return nodeMaximumHeads(segmentCaps);
    }

    private int[] undergroundOptimisticMaximumHeads(CoursePath path) {
        int segmentCount = path.points().size() - 1;
        int[] segmentCaps = new int[segmentCount];
        Arrays.fill(segmentCaps, Integer.MAX_VALUE);
        int minimumHeadroom = settings.underground().minimumHeadroom();
        for (int index = 0; index < segmentCount; index++) {
            HydrologyPoint start = path.points().get(index);
            HydrologyPoint end = path.points().get(index + 1);
            segmentCaps[index] = Math.min(
                    optimisticUndergroundSegmentCap(start, minimumHeadroom),
                    optimisticUndergroundSegmentCap(end, minimumHeadroom)
            );
        }
        return nodeMaximumHeads(segmentCaps);
    }

    private int optimisticUndergroundSegmentCap(HydrologyPoint point, int minimumHeadroom) {
        HydrologyTerrainSample terrain = sampleLandBasis(point.x(), point.z());
        return terrain == null || terrain.ocean()
                ? Integer.MAX_VALUE
                : terrain.naturalHeight() - minimumHeadroom - settings.underground().minimumRockCover();
    }

    private int undergroundSegmentCap(
            long courseId,
            String profileKey,
            HydrologyPoint start,
            HydrologyPoint end,
            int radius
    ) {
        int minimumHeadroom = settings.underground().minimumHeadroom();
        int maximumHeadroom = settings.underground().maximumHeadroom();
        UndergroundSegmentCapKey key = new UndergroundSegmentCapKey(
                courseId,
                profileKey,
                start.x(),
                start.z(),
                end.x(),
                end.z(),
                radius,
                minimumHeadroom,
                maximumHeadroom
        );
        PlanningSamples samples = planningSamples.get();
        if (samples != null) {
            Integer cached = samples.undergroundSegmentCaps.get(key);
            if (cached != null) {
                return cached;
            }
        }
        int segmentCap = Integer.MAX_VALUE;
        List<HydrologyPoint> centerline = line(start, end, 1);
        for (HydrologyPoint point : centerline) {
            int headroom = sampleGeometry(
                    HydrologyGeometrySampler.Field.UNDERGROUND_HEADROOM,
                    profileKey,
                    point.x(),
                    point.z(),
                    courseId,
                    minimumHeadroom,
                    maximumHeadroom
            );
            int minimumLandHeight = minimumLandHeightWithinRadius(point.x(), point.z(), radius);
            if (minimumLandHeight != Integer.MAX_VALUE) {
                segmentCap = Math.min(segmentCap, minimumLandHeight - headroom - settings.underground().minimumRockCover());
            }
        }
        if (samples != null) {
            samples.undergroundSegmentCaps.put(key, segmentCap);
        }
        return segmentCap;
    }

    private int[] nodeMaximumHeads(int[] segmentCaps) {
        int[] maximumHeads = new int[segmentCaps.length + 1];
        maximumHeads[0] = segmentCaps[0];
        for (int index = 1; index < maximumHeads.length - 1; index++) {
            maximumHeads[index] = Math.min(segmentCaps[index - 1], segmentCaps[index]);
        }
        maximumHeads[maximumHeads.length - 1] = segmentCaps[segmentCaps.length - 1];
        return maximumHeads;
    }

    private int minimumLandHeightWithinRadius(int x, int z, int radius) {
        PlanningSamples samples = planningSamples.get();
        MinimumLandHeightKey key = new MinimumLandHeightKey(x, z, radius);
        if (samples != null) {
            Integer cached = samples.minimumLandHeights.get(key);
            if (cached != null) {
                return cached;
            }
        }

        List<GridOffset> offsets = samples == null
                ? radialOffsets(radius)
                : samples.radialOffsets.computeIfAbsent(radius, this::radialOffsets);
        int minimumHeight = Integer.MAX_VALUE;
        for (GridOffset offset : offsets) {
            HydrologyTerrainSample terrain = sampleLandBasis(x + offset.x(), z + offset.z());
            if (terrain == null || terrain.ocean()) {
                continue;
            }
            minimumHeight = Math.min(minimumHeight, terrain.naturalHeight());
        }
        if (samples != null) {
            samples.minimumLandHeights.put(key, minimumHeight);
        }
        return minimumHeight;
    }

    private List<GridOffset> radialOffsets(int radius) {
        int blockRadius = (int) StrictMath.ceil(radius + 0.25D);
        ArrayList<GridOffset> offsets = new ArrayList<>();
        for (int deltaZ = -blockRadius; deltaZ <= blockRadius; deltaZ++) {
            for (int deltaX = -blockRadius; deltaX <= blockRadius; deltaX++) {
                if (StrictMath.hypot(deltaX, deltaZ) <= radius + 0.25D) {
                    offsets.add(new GridOffset(deltaX, deltaZ));
                }
            }
        }
        return List.copyOf(offsets);
    }

    private int[] undergroundMinimumHeads(CoursePath path, int[] depths, int outletHead) {
        int[] minimumHeads = new int[path.points().size()];
        for (int index = 0; index < minimumHeads.length; index++) {
            int depth = index == 0
                    ? depths[0]
                    : index == depths.length ? depths[depths.length - 1] : Math.max(depths[index - 1], depths[index]);
            depth = Math.max(depth, settings.geometry().drops().maximumBasinDepth());
            long worldFloorHead = (long) minimumY + depth + settings.underground().minimumFloorCover();
            int boundedWorldFloor = worldFloorHead < Integer.MIN_VALUE
                    ? Integer.MIN_VALUE
                    : worldFloorHead > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) worldFloorHead;
            int configuredMinimum = index == minimumHeads.length - 1
                    ? outletHead
                    : settings.underground().minimumFluidY();
            minimumHeads[index] = Math.max(outletHead, Math.max(configuredMinimum, boundedWorldFloor));
        }
        return minimumHeads;
    }

    private static boolean undergroundHeadsFeasible(
            int[] minimumHeads,
            int[] maximumHeads,
            int outletHead
    ) {
        if (minimumHeads.length == 0 || minimumHeads.length != maximumHeads.length) {
            throw new IllegalArgumentException("Underground head arrays must have the same positive length.");
        }
        int index = minimumHeads.length - 1;
        int requiredHead = Math.max(minimumHeads[index], outletHead);
        if (requiredHead > maximumHeads[index]) {
            return false;
        }
        for (index--; index >= 0; index--) {
            requiredHead = Math.max(minimumHeads[index], requiredHead);
            if (requiredHead > maximumHeads[index]) {
                return false;
            }
        }
        return true;
    }

    static boolean solveUndergroundHeads(
            int[] preferredHeads,
            int[] minimumHeads,
            int[] maximumHeads,
            int outletHead,
            int[] solvedHeads
    ) {
        int length = preferredHeads.length;
        if (length == 0
                || minimumHeads.length != length
                || maximumHeads.length != length
                || solvedHeads.length != length) {
            throw new IllegalArgumentException("Underground head arrays must have the same positive length.");
        }
        int[] requiredHeads = new int[length];
        requiredHeads[length - 1] = Math.max(minimumHeads[length - 1], outletHead);
        if (requiredHeads[length - 1] > maximumHeads[length - 1]) {
            return false;
        }
        for (int index = length - 2; index >= 0; index--) {
            requiredHeads[index] = Math.max(minimumHeads[index], requiredHeads[index + 1]);
            if (requiredHeads[index] > maximumHeads[index]) {
                return false;
            }
        }
        solvedHeads[0] = clamp(preferredHeads[0], requiredHeads[0], maximumHeads[0]);
        for (int index = 1; index < length; index++) {
            int maximum = Math.min(maximumHeads[index], solvedHeads[index - 1]);
            if (requiredHeads[index] > maximum) {
                return false;
            }
            solvedHeads[index] = clamp(preferredHeads[index], requiredHeads[index], maximum);
        }
        solvedHeads[length - 1] = outletHead;
        return outletHead >= minimumHeads[length - 1]
                && outletHead <= maximumHeads[length - 1]
                && (length == 1 || solvedHeads[length - 2] >= outletHead);
    }

    private boolean addHydraulicSegments(
            long courseId,
            int pairIndex,
            HydrologyFeatureType type,
            int upstreamHead,
            int downstreamHead,
            int width,
            int depth,
            List<HydrologyPoint> centerline,
            boolean surfaceCourse,
            List<HydraulicSegment> segments
    ) {
        int drop = upstreamHead - downstreamHead;
        if (drop <= 0 || centerline.size() < 2) {
            long segmentId = HydrologyHash.mix(worldSeed, SEGMENT_SALT, courseId, pairIndex, type.ordinal());
            segments.add(new HydraulicSegment(
                    segmentId,
                    courseId,
                    type,
                    upstreamHead,
                    downstreamHead,
                    width,
                    depth,
                    false,
                    false,
                    centerline
            ));
            return true;
        }
        if (type == HydrologyFeatureType.WATERFALL) {
            return addFallingDropSegments(
                    courseId,
                    pairIndex,
                    upstreamHead,
                    downstreamHead,
                    width,
                    depth,
                    centerline,
                    surfaceCourse,
                    segments
            );
        }
        return addGradedDropSegments(
                courseId,
                pairIndex,
                type,
                upstreamHead,
                downstreamHead,
                width,
                depth,
                centerline,
                surfaceCourse,
                segments
        );
    }

    private boolean addFallingDropSegments(
            long courseId,
            int pairIndex,
            int upstreamHead,
            int downstreamHead,
            int width,
            int depth,
            List<HydrologyPoint> centerline,
            boolean surfaceCourse,
            List<HydraulicSegment> segments
    ) {
        List<HydrologyPoint> raster = rasterCenterline(centerline);
        if (raster.size() < 2) {
            return false;
        }
        int lipIndex = Math.min(waterfallLipIndex(raster), raster.size() - 2);
        int receiverIndex = lipIndex + 1;
        addGradedTransitionSegment(
                courseId,
                pairIndex,
                GRADED_DROP_APPROACH_SALT,
                HydrologyFeatureType.SURFACE_POOL,
                upstreamHead,
                width,
                depth,
                levelSlice(raster, 0, lipIndex, upstreamHead),
                segments
        );
        HydrologyPlannerSettings.Drops drops = settings.geometry().drops();
        int flowWidth = hydraulicDropWidth(width, surfaceCourse);
        int flowDepth = drops.flowDepth(depth);
        HydrologyPoint lip = withY(raster.get(lipIndex), upstreamHead);
        HydrologyPoint receiver = withY(raster.get(receiverIndex), downstreamHead);
        segments.add(new HydraulicSegment(
                HydrologyHash.mix(worldSeed, SEGMENT_SALT, courseId, pairIndex, GRADED_DROP_RUN_SALT),
                courseId,
                HydrologyFeatureType.WATERFALL,
                upstreamHead,
                downstreamHead,
                flowWidth,
                flowDepth,
                true,
                true,
                List.of(lip, receiver)
        ));
        addGradedTransitionSegment(
                courseId,
                pairIndex,
                GRADED_DROP_OUTFLOW_SALT,
                HydrologyFeatureType.SURFACE_POOL,
                downstreamHead,
                width,
                depth,
                levelSlice(raster, receiverIndex, raster.size() - 1, downstreamHead),
                segments
        );
        return true;
    }

    private boolean addGradedDropSegments(
            long courseId,
            int pairIndex,
            HydrologyFeatureType type,
            int upstreamHead,
            int downstreamHead,
            int width,
            int depth,
            List<HydrologyPoint> centerline,
            boolean surfaceCourse,
            List<HydraulicSegment> segments
    ) {
        int drop = upstreamHead - downstreamHead;
        HydrologyPlannerSettings.Drops drops = settings.geometry().drops();
        boolean continuousSurfaceBore = surfaceCourse && type == HydrologyFeatureType.UNDERGROUND_DROP;
        List<HydrologyPoint> directRaster = continuousSurfaceBore
                ? rasterCenterline(centerline)
                : List.of();
        int maximumStep = drops.stepLimit(type);
        if (continuousSurfaceBore) {
            if (directRaster.size() < 2) {
                return false;
            }
            int requiredStep = Math.floorDiv(drop + directRaster.size() - 2, directRaster.size() - 1);
            if (requiredStep > MAXIMUM_SURFACE_BORE_STEP) {
                return false;
            }
            maximumStep = Math.max(maximumStep, requiredStep);
        }
        int minimumRun = Math.floorDiv(drop + maximumStep - 1, maximumStep) + 1;
        int maximumGeneratedRun = Math.addExact(settings.routing().maximumRouteLength(), 1);
        if (minimumRun > maximumGeneratedRun) {
            return false;
        }
        int preferredRun = Math.addExact(Math.multiplyExact(drop, drops.cascadeRunPerBlock()), 1);
        int undergroundRun = drops.undergroundCascadeRunPerBlock();
        int desiredRun = type.isUnderground()
                ? (undergroundRun == 0
                        ? minimumRun
                        : Math.min(
                                Math.max(minimumRun, Math.addExact(Math.multiplyExact(drop, undergroundRun), 1)),
                                maximumGeneratedRun
                        ))
                : Math.min(preferredRun, maximumGeneratedRun);
        List<HydrologyPoint> raster = continuousSurfaceBore
                ? directRaster
                : type.isUnderground()
                ? organicDropRaster(courseId, pairIndex, centerline, desiredRun)
                : rasterCenterline(centerline);
        if (raster.size() < minimumRun) {
            return false;
        }
        int runLength = Math.min(raster.size(), Math.max(minimumRun, desiredRun));
        int lipIndex = waterfallLipIndex(raster);
        int runStart = continuousSurfaceBore
                ? 0
                : type.isUnderground()
                ? clamp(lipIndex - runLength / 3, 0, raster.size() - runLength)
                : 0;
        int runEnd = continuousSurfaceBore
                ? raster.size() - 1
                : type.isUnderground() ? runStart + runLength - 1 : raster.size() - 1;
        HydrologyFeatureType flatType = type.isUnderground()
                ? HydrologyFeatureType.UNDERGROUND_POOL
                : HydrologyFeatureType.SURFACE_POOL;
        if (!continuousSurfaceBore) {
            addGradedTransitionSegment(
                    courseId,
                    pairIndex,
                    GRADED_DROP_APPROACH_SALT,
                    flatType,
                    upstreamHead,
                    width,
                    depth,
                    levelSlice(raster, 0, runStart, upstreamHead),
                    segments
            );
        }
        List<HydrologyPoint> graded = gradedDropCenterline(
                raster.subList(runStart, runEnd + 1),
                upstreamHead,
                downstreamHead,
                drops,
                maximumStep
        );
        int dropWidth = hydraulicDropWidth(width, surfaceCourse);
        int dropDepth = drops.flowDepth(depth);
        segments.add(new HydraulicSegment(
                HydrologyHash.mix(worldSeed, SEGMENT_SALT, courseId, pairIndex, GRADED_DROP_RUN_SALT),
                courseId,
                type,
                upstreamHead,
                downstreamHead,
                dropWidth,
                dropDepth,
                false,
                !continuousSurfaceBore,
                graded
        ));
        if (!continuousSurfaceBore) {
            addGradedTransitionSegment(
                    courseId,
                    pairIndex,
                    GRADED_DROP_OUTFLOW_SALT,
                    flatType,
                    downstreamHead,
                    width,
                    depth,
                    levelSlice(raster, runEnd, raster.size() - 1, downstreamHead),
                    segments
            );
        }
        return true;
    }

    private int hydraulicDropWidth(int channelWidth, boolean surfaceCourse) {
        int dropWidth = settings.geometry().drops().flowWidth(channelWidth);
        return surfaceCourse ? Math.max(settings.surface().minimumWidth(), dropWidth) : dropWidth;
    }

    private List<HydrologyPoint> organicDropRaster(
            long courseId,
            int pairIndex,
            List<HydrologyPoint> centerline,
            int desiredRun
    ) {
        List<HydrologyPoint> base = rasterCenterline(centerline);
        if (base.size() >= desiredRun) {
            return base;
        }
        int wavelength = settings.geometry().meanders().detailWavelength();
        double phase = HydrologyHash.unit(HydrologyHash.mix(worldSeed, courseId, pairIndex, GRADED_DROP_RUN_SALT))
                * StrictMath.PI * 2D;
        int maximumAmplitude = Math.max(3, settings.routing().sampleSpacing() / 2);
        List<HydrologyPoint> longest = base;
        for (int amplitude = 3; amplitude <= maximumAmplitude; amplitude += 2) {
            int cycles = Math.max(1, (int) StrictMath.ceil(
                    Math.max(0, desiredRun - base.size()) / (2D * amplitude)
            ));
            int samples = Math.max(base.size(), desiredRun * 3);
            ArrayList<HydrologyPoint> worm = new ArrayList<>(samples);
            for (int sampleIndex = 0; sampleIndex < samples; sampleIndex++) {
                double progress = sampleIndex / (double) (samples - 1);
                double baseIndex = progress * (base.size() - 1);
                int lowerIndex = Math.min(base.size() - 2, (int) StrictMath.floor(baseIndex));
                int upperIndex = lowerIndex + 1;
                double localProgress = baseIndex - lowerIndex;
                HydrologyPoint lower = base.get(lowerIndex);
                HydrologyPoint upper = base.get(upperIndex);
                double baseX = lower.x() + (upper.x() - lower.x()) * localProgress;
                double baseZ = lower.z() + (upper.z() - lower.z()) * localProgress;
                double tangentX = upper.x() - lower.x();
                double tangentZ = upper.z() - lower.z();
                double tangentLength = StrictMath.hypot(tangentX, tangentZ);
                if (tangentLength <= 0D) {
                    continue;
                }
                double envelope = StrictMath.pow(StrictMath.sin(StrictMath.PI * progress), 2D);
                double displacement = StrictMath.sin(
                        progress * StrictMath.PI * 2D * cycles
                                + baseIndex * StrictMath.PI * 2D / wavelength
                                + phase
                ) * amplitude * envelope;
                int x = (int) StrictMath.round(baseX - tangentZ / tangentLength * displacement);
                int z = (int) StrictMath.round(baseZ + tangentX / tangentLength * displacement);
                if (worm.isEmpty() || worm.getLast().x() != x || worm.getLast().z() != z) {
                    worm.add(new HydrologyPoint(x, lower.y(), z));
                }
            }
            List<HydrologyPoint> raster = rasterCenterline(worm);
            if (raster.size() > longest.size()) {
                longest = raster;
            }
            if (raster.size() >= desiredRun) {
                return raster;
            }
        }
        return longest;
    }

    private void addGradedTransitionSegment(
            long courseId,
            int pairIndex,
            long salt,
            HydrologyFeatureType type,
            int head,
            int width,
            int depth,
            List<HydrologyPoint> centerline,
            List<HydraulicSegment> segments
    ) {
        if (centerline.size() < 2) {
            return;
        }
        segments.add(new HydraulicSegment(
                HydrologyHash.mix(worldSeed, SEGMENT_SALT, courseId, pairIndex, salt),
                courseId,
                type,
                head,
                head,
                width,
                depth,
                false,
                false,
                List.copyOf(centerline)
        ));
    }

    private List<HydrologyPoint> gradedDropCenterline(
            List<HydrologyPoint> points,
            int upstreamHead,
            int downstreamHead,
            HydrologyPlannerSettings.Drops drops,
            int maximumStep
    ) {
        int pointCount = points.size();
        int drop = upstreamHead - downstreamHead;
        if ((long) (pointCount - 1) * maximumStep < drop) {
            throw new IllegalArgumentException("Graded drop centerline cannot contain its configured head loss.");
        }
        ArrayList<HydrologyPoint> graded = new ArrayList<>(pointCount);
        int previousHead = upstreamHead;
        for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
            double progress = pointIndex / (double) (pointCount - 1);
            int idealHead = upstreamHead - (int) StrictMath.round(
                    drop * StrictMath.pow(progress, drops.cascadeExponent())
            );
            int remainingSteps = pointCount - pointIndex - 1;
            int latestReachableHead = downstreamHead + remainingSteps * maximumStep;
            int head = Math.min(previousHead, Math.min(idealHead, latestReachableHead));
            head = Math.max(downstreamHead, Math.max(previousHead - maximumStep, head));
            if (pointIndex == pointCount - 1) {
                head = downstreamHead;
            }
            HydrologyPoint point = points.get(pointIndex);
            graded.add(new HydrologyPoint(point.x(), head, point.z()));
            previousHead = head;
        }
        return List.copyOf(graded);
    }

    private List<HydrologyPoint> levelSlice(
            List<HydrologyPoint> points,
            int startIndex,
            int endIndex,
            int head
    ) {
        ArrayList<HydrologyPoint> leveled = new ArrayList<>(Math.max(0, endIndex - startIndex + 1));
        for (int pointIndex = startIndex; pointIndex <= endIndex; pointIndex++) {
            leveled.add(withY(points.get(pointIndex), head));
        }
        return List.copyOf(leveled);
    }

    private int waterfallLipIndex(List<HydrologyPoint> raster) {
        return waterfallLipIndex(raster, 0);
    }

    private int waterfallLipIndex(List<HydrologyPoint> raster, int requiredDecline) {
        int fallback = Math.max(0, raster.size() / 2 - 1);
        int selected = fallback;
        long strongestDecline = Long.MIN_VALUE;
        for (int pointIndex = 0; pointIndex < raster.size() - 1; pointIndex++) {
            HydrologyPoint current = raster.get(pointIndex);
            HydrologyPoint downstream = raster.get(pointIndex + 1);
            HydrologyTerrainSample currentTerrain = sampleBasisWithoutSlope(current.x(), current.z());
            HydrologyTerrainSample downstreamTerrain = sampleBasisWithoutSlope(downstream.x(), downstream.z());
            long decline = (long) currentTerrain.naturalHeight() - downstreamTerrain.naturalHeight();
            if (decline > strongestDecline) {
                strongestDecline = decline;
                selected = pointIndex;
            }
        }
        return requiredDecline <= 0 || strongestDecline >= requiredDecline ? selected : -1;
    }

    private List<HydrologyPoint> rasterCenterline(List<HydrologyPoint> centerline) {
        ArrayList<HydrologyPoint> raster = new ArrayList<>();
        for (int pointIndex = 0; pointIndex < centerline.size() - 1; pointIndex++) {
            List<HydrologyPoint> span = rasterLine(centerline.get(pointIndex), centerline.get(pointIndex + 1));
            int first = raster.isEmpty() ? 0 : 1;
            for (int spanIndex = first; spanIndex < span.size(); spanIndex++) {
                raster.add(span.get(spanIndex));
            }
        }
        return List.copyOf(raster);
    }

    private List<HydrologyPoint> rasterLine(HydrologyPoint start, HydrologyPoint end) {
        int steps = Math.max(Math.abs(end.x() - start.x()), Math.abs(end.z() - start.z()));
        if (steps == 0) {
            return List.of(start);
        }
        ArrayList<HydrologyPoint> points = new ArrayList<>(steps + 1);
        for (int step = 0; step <= steps; step++) {
            double progress = step / (double) steps;
            int x = (int) StrictMath.round(start.x() + (end.x() - start.x()) * progress);
            int z = (int) StrictMath.round(start.z() + (end.z() - start.z()) * progress);
            points.add(new HydrologyPoint(x, start.y(), z));
        }
        return List.copyOf(points);
    }

    private CoursePath coursePath(
            SampledGrid grid,
            RoutingPlan routing,
            CompiledGraph graph,
            int sourceIndex,
            boolean surface
    ) {
        ArrayList<DrainageEdge> edges = new ArrayList<>();
        ArrayList<HydrologyPoint> guide = new ArrayList<>();
        ArrayList<DrainageEdge> guideEdges = new ArrayList<>();
        int current = sourceIndex;
        int steps = 0;
        while (routing.parent()[current] >= 0) {
            DrainageEdge edge = graph.edgeByUpstream().get(current);
            if (edge == null) {
                return null;
            }
            edges.add(edge);
            int downstream = routing.parent()[current];
            if (surface) {
                for (HydrologyPoint point : edge.centerline()) {
                    if (guide.isEmpty()) {
                        guide.add(point);
                    } else if (point.x() != guide.getLast().x() || point.z() != guide.getLast().z()) {
                        guide.add(point);
                        guideEdges.add(edge);
                    }
                }
            }
            current = downstream;
            if (++steps > settings.routing().maximumRouteNodes()) {
                return null;
            }
        }
        int outletIndex = routing.outletIndex()[sourceIndex];
        if (outletIndex < 0 || edges.isEmpty()) {
            return null;
        }
        RiverOutlet outlet = routing.outlets().get(outletIndex).outlet();
        if (!surface) {
            return undergroundCoursePath(edges, outlet);
        }
        if (guide.size() < 2 || guideEdges.size() != guide.size() - 1) {
            return null;
        }
        ArrayList<HydrologyPoint> routedGuide = new ArrayList<>(guide);
        ArrayList<DrainageEdge> routedGuideEdges = new ArrayList<>(guideEdges);
        HydrologyPoint landward = outlet.landwardPoint();
        HydrologyPoint guideEnd = routedGuide.getLast();
        boolean terminateAtSinkhole = surfaceSinkhole(outlet)
                && (guideEnd.x() != landward.x() || guideEnd.z() != landward.z());
        if (terminateAtSinkhole) {
            routedGuide.set(routedGuide.size() - 1, landward);
        }
        List<HydrologyPoint> surfaceGuide = withoutDuplicateRoutePoints(routedGuide);
        long geometryId = HydrologyHash.mix(
                worldSeed,
                COURSE_SALT,
                SURFACE_SOURCE_SALT,
                edges.getFirst().upstreamNodeId(),
                outlet.id()
        );
        Objects.requireNonNull(grid, "Surface course routing grid is required");
        PlanningSamples samples = planningSamples.get();
        DraftProfile profile = currentDraftProfile();
        profile.routeCalls++;
        List<HydrologyPoint> points;
        if (samples == null) {
            profile.routeSolves++;
            long solveStarted = System.nanoTime();
            points = surfaceCourseCenterline(grid, surfaceGuide, geometryId);
            profile.routeSolveNanos += System.nanoTime() - solveStarted;
        } else {
            HashMap<SurfaceRouteKey, List<HydrologyPoint>> routes = samples.surfaceRoutes.computeIfAbsent(
                    grid, ignored -> new HashMap<>()
            );
            SurfaceRouteKey routeKey = new SurfaceRouteKey(surfaceGuide, geometryId);
            points = routes.computeIfAbsent(routeKey, ignored -> {
                profile.routeSolves++;
                long solveStarted = System.nanoTime();
                List<HydrologyPoint> solved = surfaceCourseCenterline(grid, surfaceGuide, geometryId);
                profile.routeSolveNanos += System.nanoTime() - solveStarted;
                return solved;
            });
        }
        if (points.size() < 2) {
            return null;
        }
        List<DrainageEdge> pairEdges = mapCoursePairs(points, routedGuide, routedGuideEdges);
        if (pairEdges.size() != points.size() - 1) {
            return null;
        }
        return new CoursePath(
                points,
                pairEdges,
                List.copyOf(edges),
                outlet,
                true,
                routing.organicSurfaceRequired(),
                null
        );
    }

    private List<HydrologyPoint> surfaceCourseCenterline(
            SampledGrid grid,
            List<HydrologyPoint> guide,
            long geometryId
    ) {
        if (guide.size() < 2) {
            return List.of();
        }
        int refinement = settings.routing().refinementSpacing();
        List<SurfaceCurvePoint> base = smoothSurfaceGuide(resampleSurfaceGuide(guide, refinement));
        List<SurfaceCurvePoint> nominal = meanderSurfaceGuide(base, geometryId, refinement);
        if (nominal.size() < 2) {
            return List.of();
        }
        ArrayList<List<RouteCandidate>> layers = new ArrayList<>(nominal.size());
        int transverseCandidates = SURFACE_COURSE_TRANSVERSE_CANDIDATES;
        for (int pointIndex = 0; pointIndex < nominal.size(); pointIndex++) {
            SurfaceCurvePoint point = nominal.get(pointIndex);
            Direction tangent = surfaceCurveTangent(nominal, pointIndex);
            if (pointIndex == 0 || pointIndex == nominal.size() - 1) {
                HydrologyPoint endpoint = pointIndex == 0 ? guide.getFirst() : guide.getLast();
                layers.add(List.of(routeEndpoint(endpoint, tangent)));
                continue;
            }
            double progress = pointIndex / (double) (nominal.size() - 1);
            SurfaceCurvePoint basePoint = base.get(pointIndex);
            RoutePosition position = new RoutePosition(
                    point.x(),
                    point.z(),
                    basePoint.x(),
                    basePoint.z(),
                    tangent
            );
            long candidateId = HydrologyHash.mix(geometryId, pointIndex);
            List<RouteCandidate> candidates = unresolvedSurfaceRouteCandidates(
                    grid,
                    geometryId,
                    candidateId,
                    position,
                    progress,
                    refinement,
                    transverseCandidates
            );
            if (candidates.isEmpty()) {
                return List.of();
            }
            layers.add(candidates);
        }
        ArrayList<List<RouteCandidate>> availableLayers = new ArrayList<>(layers);
        double maximumTurn = Math.min(
                SURFACE_COURSE_MAXIMUM_TURN_DEGREES,
                settings.geometry().meanders().maximumTurnDegrees()
        );
        while (true) {
            RouteCandidate[] route = selectCurvatureAwareTerrainRoute(
                    availableLayers,
                    refinement,
                    maximumTurn,
                    SURFACE_COURSE_TURN_COST
            );
            if (route.length == 0) {
                if (addExhaustedSurfaceRouteFallbacks(
                        grid,
                        availableLayers,
                        nominal,
                        base,
                        geometryId
                )) {
                    continue;
                }
                return List.of();
            }
            int pitRemoval = removeTerrainPits(availableLayers, route);
            if (pitRemoval < 0) {
                return List.of();
            }
            if (pitRemoval > 0) {
                continue;
            }
            ArrayList<HydrologyPoint> points = new ArrayList<>(route.length);
            for (RouteCandidate candidate : route) {
                HydrologyPoint point = candidate.point();
                if (points.isEmpty()
                        || point.x() != points.getLast().x()
                        || point.z() != points.getLast().z()) {
                    points.add(point);
                }
            }
            if (points.size() < 2 || !traversableRoute(points)) {
                return List.of();
            }
            return smoothTerrainRoute(points);
        }
    }

    private boolean addExhaustedSurfaceRouteFallbacks(
            SampledGrid grid,
            List<List<RouteCandidate>> layers,
            List<SurfaceCurvePoint> nominal,
            List<SurfaceCurvePoint> base,
            long geometryId
    ) {
        boolean added = false;
        for (int layerIndex = 1; layerIndex < layers.size() - 1; layerIndex++) {
            List<RouteCandidate> layer = layers.get(layerIndex);
            if (!exhaustedSurfaceRouteLayer(layer)) {
                continue;
            }
            SurfaceCurvePoint nominalPoint = nominal.get(layerIndex);
            SurfaceCurvePoint basePoint = base.get(layerIndex);
            Direction tangent = surfaceCurveTangent(nominal, layerIndex);
            int fallbackX = (int) StrictMath.round(basePoint.x());
            int fallbackZ = (int) StrictMath.round(basePoint.z());
            long packed = RiverFootprint.pack(fallbackX, fallbackZ);
            boolean present = false;
            for (RouteCandidate candidate : layer) {
                if (RiverFootprint.pack(candidate.point().x(), candidate.point().z()) == packed) {
                    present = true;
                    break;
                }
            }
            if (present) {
                continue;
            }
            RouteTerrainEstimate estimate = routeTerrainEstimate(grid, fallbackX, fallbackZ);
            double offset = (basePoint.x() - nominalPoint.x()) * -tangent.z()
                    + (basePoint.z() - nominalPoint.z()) * tangent.x();
            double localScore = estimate.terrainScore()
                    + StrictMath.abs(offset) * 0.18D
                    + HydrologyHash.unit(HydrologyHash.mix(
                    worldSeed,
                    geometryId,
                    HydrologyHash.mix(geometryId, layerIndex),
                    fallbackX,
                    fallbackZ
            )) * 1.0E-6D;
            ArrayList<RouteCandidate> expanded = new ArrayList<>(layer);
            expanded.add(new RouteCandidate(
                    new HydrologyPoint(fallbackX, estimate.height(), fallbackZ),
                    offset,
                    localScore,
                    estimate.terrainScore(),
                    tangent,
                    false
            ));
            layers.set(layerIndex, List.copyOf(expanded));
            added = true;
        }
        return added;
    }

    private boolean exhaustedSurfaceRouteLayer(List<RouteCandidate> layer) {
        for (RouteCandidate candidate : layer) {
            if (!candidate.terrainResolved() || Double.isFinite(candidate.localScore())) {
                return false;
            }
        }
        return true;
    }

    private double routeTurnScore(List<HydrologyPoint> route) {
        List<HydrologyPoint> sampled = resampleRouteCenterline(
                route,
                Math.max(16D, settings.routing().refinementSpacing() * 4D)
        );
        if (sampled.size() < 3) {
            return Double.POSITIVE_INFINITY;
        }
        ArrayList<Double> turns = new ArrayList<>(sampled.size() - 2);
        double maximum = 0D;
        for (int pointIndex = 1; pointIndex < sampled.size() - 1; pointIndex++) {
            double turn = routeTurnDegrees(
                    sampled.get(pointIndex - 1),
                    sampled.get(pointIndex),
                    sampled.get(pointIndex + 1)
            );
            turns.add(turn);
            maximum = Math.max(maximum, turn);
        }
        turns.sort(Double::compare);
        int percentileIndex = (int) StrictMath.ceil(0.95D * turns.size()) - 1;
        double percentile = turns.get(Math.max(0, Math.min(percentileIndex, turns.size() - 1)));
        return percentile + maximum * 0.05D;
    }

    private double surfaceRouteCandidateBankPenalty(RouteCandidate candidate) {
        int minimumBankDistance = surfaceBankDistance(settings.surface().minimumWidth());
        int maximumBankDistance = surfaceBankDistance(settings.surface().maximumWidth());
        int maximumBankRise = Math.addExact(
                settings.surface().maximumIncision(),
                (int) StrictMath.ceil(settings.surface().banks().minimumBlendWidth() * 0.5D)
        );
        HydrologyPoint point = candidate.point();
        HydrologyTerrainSample terrain = sampleLandBasis(point.x(), point.z());
        if (terrain == null) {
            return Double.POSITIVE_INFINITY;
        }
        if (terrain.slope() >= settings.hydraulics().waterfallMinimumDrop()) {
            return 0D;
        }
        int head = Math.subtractExact(
                terrain.naturalHeight(),
                settings.surface().banks().sink()
        );
        return surfaceRouteBankPenalty(
                point,
                candidate.tangent(),
                minimumBankDistance,
                maximumBankDistance,
                head,
                maximumBankRise
        );
    }

    private double surfaceRouteBankPenalty(
            HydrologyPoint point,
            Direction tangent,
            int minimumDistance,
            int maximumDistance,
            int head,
            int maximumBankRise
    ) {
        double penalty = 0D;
        for (int distance = minimumDistance; distance <= maximumDistance; distance++) {
            penalty += surfaceRouteBankPenalty(point, tangent, distance, head, maximumBankRise);
            penalty += surfaceRouteBankPenalty(point, tangent, -distance, head, maximumBankRise);
        }
        return penalty;
    }

    private double surfaceRouteBankPenalty(
            HydrologyPoint point,
            Direction tangent,
            int signedDistance,
            int head,
            int maximumBankRise
    ) {
        HydrologyTerrainSample bank = surfaceBankTerrain(point, tangent, signedDistance);
        if (bank == null) {
            return 1.0E12D;
        }
        int rise = bank.naturalHeight() - head;
        int shortfall = SURFACE_MINIMUM_NATURAL_BANK_RISE - rise;
        if (shortfall > 0) {
            return (double) shortfall * shortfall * 4096D;
        }
        int excess = Math.max(0, rise - maximumBankRise);
        return (double) excess * excess * 128D;
    }

    private int surfaceBankDistance(int width) {
        int channelRadius = (int) StrictMath.ceil(width / 2D);
        return Math.max(
                channelRadius + 1,
                (int) StrictMath.ceil(
                        channelRadius * SURFACE_MAXIMUM_WIDTH_SCALE + settings.surface().shoreWidth()
                ) + 1
        );
    }

    private List<SurfaceCurvePoint> resampleSurfaceGuide(List<HydrologyPoint> guide, int spacing) {
        double[] cumulative = new double[guide.size()];
        for (int pointIndex = 1; pointIndex < guide.size(); pointIndex++) {
            HydrologyPoint previous = guide.get(pointIndex - 1);
            HydrologyPoint point = guide.get(pointIndex);
            cumulative[pointIndex] = cumulative[pointIndex - 1]
                    + StrictMath.hypot(point.x() - previous.x(), point.z() - previous.z());
        }
        double totalLength = cumulative[cumulative.length - 1];
        if (totalLength <= 0D) {
            return List.of();
        }
        int capacity = Math.addExact(2, (int) StrictMath.ceil(totalLength / spacing));
        ArrayList<SurfaceCurvePoint> sampled = new ArrayList<>(capacity);
        int segmentIndex = 0;
        for (double distance = 0D; distance < totalLength; distance += spacing) {
            while (segmentIndex < guide.size() - 2
                    && cumulative[segmentIndex + 1] < distance) {
                segmentIndex++;
            }
            HydrologyPoint start = guide.get(segmentIndex);
            HydrologyPoint end = guide.get(segmentIndex + 1);
            double segmentLength = cumulative[segmentIndex + 1] - cumulative[segmentIndex];
            double progress = segmentLength <= 0D
                    ? 0D
                    : (distance - cumulative[segmentIndex]) / segmentLength;
            sampled.add(new SurfaceCurvePoint(
                    start.x() + (end.x() - start.x()) * progress,
                    start.z() + (end.z() - start.z()) * progress,
                    distance
            ));
        }
        HydrologyPoint end = guide.getLast();
        sampled.add(new SurfaceCurvePoint(end.x(), end.z(), totalLength));
        return List.copyOf(sampled);
    }

    private List<SurfaceCurvePoint> smoothSurfaceGuide(List<SurfaceCurvePoint> guide) {
        if (guide.size() < 3) {
            return guide;
        }
        List<SurfaceCurvePoint> current = guide;
        int passes = Math.max(2, settings.geometry().meanders().smoothingPasses() + 1);
        for (int pass = 0; pass < passes; pass++) {
            ArrayList<SurfaceCurvePoint> smoothed = new ArrayList<>(current.size());
            smoothed.add(current.getFirst());
            for (int pointIndex = 1; pointIndex < current.size() - 1; pointIndex++) {
                SurfaceCurvePoint previous = current.get(pointIndex - 1);
                SurfaceCurvePoint point = current.get(pointIndex);
                SurfaceCurvePoint next = current.get(pointIndex + 1);
                smoothed.add(new SurfaceCurvePoint(
                        previous.x() * 0.25D + point.x() * 0.5D + next.x() * 0.25D,
                        previous.z() * 0.25D + point.z() * 0.5D + next.z() * 0.25D,
                        point.distance()
                ));
            }
            smoothed.add(current.getLast());
            current = List.copyOf(smoothed);
        }
        return current;
    }

    private List<SurfaceCurvePoint> meanderSurfaceGuide(
            List<SurfaceCurvePoint> guide,
            long geometryId,
            int refinement
    ) {
        HydrologyPlannerSettings.Meanders meanders = settings.geometry().meanders();
        double detailWeight = meanders.detailStrength() * 0.08D;
        double totalWeight = meanders.primaryStrength() + detailWeight;
        if (guide.size() < 3 || totalWeight <= 0D || meanders.maximumOffsetRatio() <= 0D) {
            return guide;
        }
        double maximumOffset = settings.routing().sampleSpacing() * Math.min(
                0.16D,
                meanders.maximumOffsetRatio() * 0.32D
        );
        double primaryPhase = HydrologyHash.unit(HydrologyHash.mix(geometryId, ROUTE_WORM_PRIMARY_SALT))
                * StrictMath.PI * 2D;
        double detailPhase = HydrologyHash.unit(HydrologyHash.mix(geometryId, ROUTE_WORM_DETAIL_SALT))
                * StrictMath.PI * 2D;
        double totalLength = guide.getLast().distance();
        double taperLength = Math.max(
                refinement * 8D,
                Math.min(settings.routing().sampleSpacing() * 0.75D, meanders.primaryWavelength() * 0.35D)
        );
        ArrayList<SurfaceCurvePoint> meandered = new ArrayList<>(guide.size());
        for (int pointIndex = 0; pointIndex < guide.size(); pointIndex++) {
            SurfaceCurvePoint point = guide.get(pointIndex);
            Direction tangent = surfaceCurveTangent(guide, pointIndex);
            double endpointDistance = Math.min(point.distance(), totalLength - point.distance());
            double taperProgress = Math.min(1D, endpointDistance / taperLength);
            double taper = StrictMath.pow(StrictMath.sin(taperProgress * StrictMath.PI * 0.5D), 2D);
            double primary = StrictMath.sin(
                    point.distance() * StrictMath.PI * 2D / meanders.primaryWavelength() + primaryPhase
            ) * meanders.primaryStrength();
            double detail = StrictMath.sin(
                    point.distance() * StrictMath.PI * 2D / meanders.detailWavelength() + detailPhase
            ) * detailWeight;
            double offset = maximumOffset * taper * (primary + detail) / totalWeight;
            meandered.add(new SurfaceCurvePoint(
                    point.x() - tangent.z() * offset,
                    point.z() + tangent.x() * offset,
                    point.distance()
            ));
        }
        return List.copyOf(meandered);
    }

    private Direction surfaceCurveTangent(List<SurfaceCurvePoint> points, int pointIndex) {
        SurfaceCurvePoint start = points.get(Math.max(0, pointIndex - 2));
        SurfaceCurvePoint end = points.get(Math.min(points.size() - 1, pointIndex + 2));
        double deltaX = end.x() - start.x();
        double deltaZ = end.z() - start.z();
        double length = StrictMath.hypot(deltaX, deltaZ);
        return length <= 0D
                ? new Direction(1D, 0D)
                : new Direction(deltaX / length, deltaZ / length);
    }

    private CoursePath undergroundCoursePath(List<DrainageEdge> edges, RiverOutlet outlet) {
        ArrayList<HydrologyPoint> points = new ArrayList<>();
        ArrayList<DrainageEdge> pairEdges = new ArrayList<>();
        for (DrainageEdge edge : edges) {
            for (int pointIndex = 0; pointIndex < edge.centerline().size(); pointIndex++) {
                HydrologyPoint point = edge.centerline().get(pointIndex);
                if (points.isEmpty()) {
                    points.add(point);
                } else if (pointIndex > 0) {
                    points.add(point);
                    pairEdges.add(edge);
                }
            }
        }
        return points.size() >= 2 && pairEdges.size() == points.size() - 1
                ? new CoursePath(
                List.copyOf(points),
                List.copyOf(pairEdges),
                List.copyOf(edges),
                outlet,
                true,
                false,
                null
        )
                : null;
    }

    private List<DrainageEdge> mapCoursePairs(
            List<HydrologyPoint> points,
            List<HydrologyPoint> guide,
            List<DrainageEdge> guideEdges
    ) {
        ArrayList<DrainageEdge> mapped = new ArrayList<>(points.size() - 1);
        int minimumGuideIndex = 0;
        for (int pointIndex = 0; pointIndex < points.size() - 1; pointIndex++) {
            HydrologyPoint first = points.get(pointIndex);
            HydrologyPoint second = points.get(pointIndex + 1);
            double x = (first.x() + second.x()) * 0.5D;
            double z = (first.z() + second.z()) * 0.5D;
            int selectedIndex = minimumGuideIndex;
            double selectedDistance = Double.POSITIVE_INFINITY;
            for (int guideIndex = minimumGuideIndex; guideIndex < guideEdges.size(); guideIndex++) {
                double distance = pointToSegmentDistanceSquared(
                        x,
                        z,
                        guide.get(guideIndex),
                        guide.get(guideIndex + 1)
                );
                if (distance < selectedDistance) {
                    selectedIndex = guideIndex;
                    selectedDistance = distance;
                }
            }
            minimumGuideIndex = selectedIndex;
            mapped.add(guideEdges.get(selectedIndex));
        }
        return List.copyOf(mapped);
    }

    private double pointToSegmentDistanceSquared(
            double x,
            double z,
            HydrologyPoint start,
            HydrologyPoint end
    ) {
        double deltaX = end.x() - start.x();
        double deltaZ = end.z() - start.z();
        double lengthSquared = deltaX * deltaX + deltaZ * deltaZ;
        if (lengthSquared == 0D) {
            double pointX = x - start.x();
            double pointZ = z - start.z();
            return pointX * pointX + pointZ * pointZ;
        }
        double progress = ((x - start.x()) * deltaX + (z - start.z()) * deltaZ) / lengthSquared;
        progress = Math.max(0D, Math.min(1D, progress));
        double nearestX = start.x() + deltaX * progress;
        double nearestZ = start.z() + deltaZ * progress;
        double pointX = x - nearestX;
        double pointZ = z - nearestZ;
        return pointX * pointX + pointZ * pointZ;
    }

    private HydrologyTerrainSample surfaceBankTerrain(
            HydrologyPoint point,
            Direction tangent,
            int signedDistance
    ) {
        HydrologyPoint bankPoint = surfaceBankPoint(point, tangent, signedDistance);
        int bankX = bankPoint.x();
        int bankZ = bankPoint.z();
        return sampleLandBasisWithoutSlope(bankX, bankZ);
    }

    private HydrologyPoint surfaceBankPoint(HydrologyPoint point, Direction tangent, int signedDistance) {
        return new HydrologyPoint(
                (int) StrictMath.round(point.x() - tangent.z() * signedDistance),
                point.y(),
                (int) StrictMath.round(point.z() + tangent.x() * signedDistance)
        );
    }

    private void levelUndergroundMouth(CoursePath path, int[] heads, int outletHead) {
        heads[heads.length - 1] = outletHead;
        int levelingDistance = settings.outlets().mouthLevelingDistance();
        if (levelingDistance == 0) {
            return;
        }
        double remaining = 0D;
        int initialHead = heads[0];
        for (int index = path.points().size() - 2; index >= 0; index--) {
            HydrologyPoint current = path.points().get(index);
            HydrologyPoint downstream = path.points().get(index + 1);
            remaining += StrictMath.hypot(downstream.x() - current.x(), downstream.z() - current.z());
            if (remaining > levelingDistance) {
                break;
            }
            double ratio = remaining / levelingDistance;
            int cap = outletHead + (int) StrictMath.ceil((initialHead - outletHead) * ratio);
            heads[index] = Math.min(heads[index], cap);
        }
        for (int index = 1; index < heads.length; index++) {
            heads[index] = Math.min(heads[index - 1], heads[index]);
        }
        heads[heads.length - 1] = outletHead;
    }

    private void appendOutletSegments(
            RiverCourseType courseType,
            long courseId,
            CoursePath path,
            int head,
            int[] widths,
            int[] depths,
            List<HydraulicSegment> segments
    ) {
        RiverOutlet outlet = path.outlet();
        int width = widths.length == 0 ? 1 : widths[widths.length - 1];
        int depth = depths.length == 0 ? 1 : depths[depths.length - 1];
        if (courseType != RiverCourseType.SURFACE
                && outlet.type() == HydrologyFeatureType.INLAND_GROTTO) {
            width = settings.underground().minimumWidth();
            depth = settings.underground().minimumDepth();
        }
        HydrologyPoint pathEnd = withY(path.points().getLast(), head);
        HydrologyPoint landward = withY(outlet.landwardPoint(), head);
        if (courseType == RiverCourseType.SURFACE
                && outlet.type() == HydrologyFeatureType.COASTAL_GROTTO) {
            appendSurfaceCoastalGrotto(
                    courseId,
                    outlet,
                    head,
                    width,
                    depth,
                    pathEnd,
                    landward,
                    segments
            );
            return;
        }
        if (outlet.type() == HydrologyFeatureType.MOUTH) {
            if (courseType == RiverCourseType.SURFACE) {
                appendSurfaceMouth(courseId, outlet, head, width, depth, pathEnd, segments);
                return;
            }
            HydrologyPoint connection = withY(outlet.connectionPoint(), head);
            addFlatSegment(
                    courseId,
                    HydrologyFeatureType.MOUTH,
                    head,
                    width,
                    depth,
                    line(pathEnd, connection, settings.routing().refinementSpacing()),
                    segments
            );
            return;
        }
        if (courseType == RiverCourseType.SURFACE && surfaceSinkhole(outlet)) {
            appendSurfaceSinkhole(courseId, outlet, head, width, depth, pathEnd, landward, segments);
            return;
        }
        HydrologyPoint connection = withY(outlet.connectionPoint(), head);
        if (pathEnd.x() != landward.x() || pathEnd.z() != landward.z()) {
            HydrologyFeatureType connectorType = courseType == RiverCourseType.SURFACE
                    ? HydrologyFeatureType.MOUTH
                    : HydrologyFeatureType.UNDERGROUND_POOL;
            addFlatSegment(
                    courseId,
                    connectorType,
                    head,
                    width,
                    depth,
                    line(pathEnd, landward, settings.routing().refinementSpacing()),
                    segments
            );
        }
        List<HydrologyPoint> centerline = landward.x() == connection.x() && landward.z() == connection.z()
                ? List.of(landward)
                : List.of(landward, connection);
        addFlatSegment(courseId, outlet.type(), head, width, depth, centerline, segments);
    }

    private void appendSurfaceMouth(
            long courseId,
            RiverOutlet outlet,
            int surfaceHead,
            int width,
            int depth,
            HydrologyPoint pathEnd,
            List<HydraulicSegment> segments
    ) {
        int seaLevel = outletHead(outlet);
        HydrologyPoint connection = withY(outlet.connectionPoint(), seaLevel);
        if (surfaceHead != seaLevel) {
            throw new IllegalStateException("A surface mouth reached the coastal crossing above sea level.");
        }
        addFlatSegment(
                courseId,
                HydrologyFeatureType.MOUTH,
                seaLevel,
                width,
                depth,
                line(pathEnd, connection, settings.routing().refinementSpacing()),
                segments
        );
    }

    private void appendSurfaceCoastalGrotto(
            long courseId,
            RiverOutlet outlet,
            int surfaceHead,
            int width,
            int depth,
            HydrologyPoint pathEnd,
            HydrologyPoint landward,
            List<HydraulicSegment> segments
    ) {
        if (pathEnd.x() != landward.x() || pathEnd.z() != landward.z()) {
            addFlatSegment(
                    courseId,
                    HydrologyFeatureType.SURFACE_POOL,
                    surfaceHead,
                    width,
                    depth,
                    line(pathEnd, landward, settings.routing().refinementSpacing()),
                    segments
            );
        }
        int seaLevel = outletHead(outlet);
        HydrologyPoint connection = withY(outlet.connectionPoint(), seaLevel);
        if (surfaceHead > seaLevel) {
            addFallingDropSegments(
                    courseId,
                    segments.size(),
                    surfaceHead,
                    seaLevel,
                    width,
                    depth,
                    List.of(landward, connection),
                    true,
                    segments
            );
        }
        addFlatSegment(
                courseId,
                HydrologyFeatureType.COASTAL_GROTTO,
                seaLevel,
                width,
                depth,
                List.of(withY(outlet.landwardPoint(), seaLevel), connection),
                segments
        );
    }

    private void appendSurfaceSinkhole(
            long courseId,
            RiverOutlet outlet,
            int surfaceHead,
            int surfaceWidth,
            int surfaceDepth,
            HydrologyPoint pathEnd,
            HydrologyPoint landward,
            List<HydraulicSegment> segments
    ) {
        if (pathEnd.x() != landward.x() || pathEnd.z() != landward.z()) {
            addFlatSegment(
                    courseId,
                    HydrologyFeatureType.SURFACE_POOL,
                    surfaceHead,
                    surfaceWidth,
                    surfaceDepth,
                    line(pathEnd, landward, settings.routing().refinementSpacing()),
                    segments
            );
        }
        int undergroundHead = outletHead(outlet);
        int throatWidth = Math.max(settings.underground().minimumWidth(), surfaceWidth - 1);
        int throatDepth = settings.underground().minimumDepth();
        HydrologyPoint receiving = withY(outlet.connectionPoint(), undergroundHead);
        List<HydrologyPoint> descent = sinkholeDescent(
                courseId,
                landward,
                receiving,
                surfaceHead - undergroundHead
        );
        List<HydrologyPoint> gradedDescent = gradedDropCenterline(
                rasterCenterline(descent),
                surfaceHead,
                undergroundHead,
                settings.geometry().drops(),
                settings.geometry().drops().stepLimit(HydrologyFeatureType.SINKHOLE)
        );
        int descentWidth = Math.max(throatWidth, hydraulicDropWidth(throatWidth, true));
        segments.add(new HydraulicSegment(
                HydrologyHash.mix(
                        worldSeed,
                        SEGMENT_SALT,
                        courseId,
                        segments.size(),
                        HydrologyFeatureType.SINKHOLE.ordinal()
                ),
                courseId,
                HydrologyFeatureType.SINKHOLE,
                surfaceHead,
                undergroundHead,
                descentWidth,
                throatDepth,
                false,
                true,
                gradedDescent
        ));
        addFlatSegment(
                courseId,
                HydrologyFeatureType.INLAND_GROTTO,
                undergroundHead,
                throatWidth,
                throatDepth,
                List.of(receiving),
                segments
        );
    }

    private List<HydrologyPoint> sinkholeDescent(
            long courseId,
            HydrologyPoint landward,
            HydrologyPoint receiving,
            int drop
    ) {
        int desiredRun = Math.max(
                4,
                (int) StrictMath.ceil(drop / (double) settings.geometry().drops().maximumCascadeStep()) + 1
        );
        double startX = landward.x() - receiving.x();
        double startZ = landward.z() - receiving.z();
        double startRadius = StrictMath.hypot(startX, startZ);
        double startAngle = startRadius <= 0D
                ? HydrologyHash.unit(HydrologyHash.mix(worldSeed, courseId, SEGMENT_SALT)) * StrictMath.PI * 2D
                : StrictMath.atan2(startZ, startX);
        double maximumRadius = Math.max(2D, settings.outlets().inlandGrotto().horizontalRadius() - 1D);
        int turns = Math.max(1, (int) StrictMath.ceil(desiredRun / (StrictMath.PI * maximumRadius)));
        ArrayList<HydrologyPoint> points = new ArrayList<>(desiredRun * 4 + 1);
        int samples = desiredRun * 4;
        for (int sample = 0; sample <= samples; sample++) {
            double progress = sample / (double) samples;
            double radius = Math.min(
                    maximumRadius,
                    startRadius * (1D - progress) + maximumRadius * StrictMath.sin(StrictMath.PI * progress)
            );
            double angle = startAngle + turns * StrictMath.PI * 2D * progress;
            int x = (int) StrictMath.round(receiving.x() + StrictMath.cos(angle) * radius);
            int z = (int) StrictMath.round(receiving.z() + StrictMath.sin(angle) * radius);
            if (points.isEmpty() || points.getLast().x() != x || points.getLast().z() != z) {
                points.add(new HydrologyPoint(x, landward.y(), z));
            }
        }
        if (points.getLast().x() != receiving.x() || points.getLast().z() != receiving.z()) {
            points.add(receiving);
        }
        return List.copyOf(points);
    }

    private boolean surfaceSinkhole(RiverOutlet outlet) {
        return outlet.type() == HydrologyFeatureType.INLAND_GROTTO
                && settings.outlets().surfaceSinkholesEnabled();
    }

    private void addFlatSegment(
            long courseId,
            HydrologyFeatureType type,
            int head,
            int width,
            int depth,
            List<HydrologyPoint> centerline,
            List<HydraulicSegment> segments
    ) {
        long segmentId = HydrologyHash.mix(worldSeed, SEGMENT_SALT, courseId, segments.size(), type.ordinal());
        segments.add(new HydraulicSegment(
                segmentId,
                courseId,
                type,
                head,
                head,
                width,
                depth,
                false,
                false,
                centerline
        ));
    }

    /**
     * Standing surface pools: a jittered lattice of candidate sites per pool profile, admitted where the
     * policy lists the pool, the ground is open land clear of every accepted course, and the bowl fits
     * the incision cap. Each accepted pool is an independent course with one STANDING_POOL segment.
     */
    private void compileSurfacePools(
            HydrologyTileKey key,
            List<RiverCourse> courses,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        int tileSize = settings.routing().tileSize();
        for (HydrologyPlannerSettings.SurfacePool pool : settings.surfacePools()) {
            if (!pool.enabled() || pool.maximumPerTile() == 0) {
                continue;
            }
            long profileSeed = HydrologyHash.mix(worldSeed, SURFACE_POOL_SALT, HydrologyHash.text(pool.id()));
            int target = Math.min(pool.maximumPerTile(), expectedCount(pool.density(), HydrologyHash.mix(
                    profileSeed, key.tileX(), key.tileZ())));
            int accepted = 0;
            for (DeepSite site : poolSites(key, pool, profileSeed, tileSize)) {
                if (accepted >= target) {
                    addPoolDiagnostic(site, HydrologyCandidateRejection.SOURCE_QUOTA, diagnostics);
                    continue;
                }
                HydrologyCandidateRejection rejection = poolAdmission(pool, site, courses);
                if (rejection != null) {
                    addPoolDiagnostic(site, rejection, diagnostics);
                    continue;
                }
                RiverCourse course = buildSurfacePoolCourse(pool, site);
                if (course == null) {
                    addPoolDiagnostic(site, HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED, diagnostics);
                    continue;
                }
                courses.add(course);
                accepted++;
            }
        }
    }

    private ArrayList<DeepSite> poolSites(
            HydrologyTileKey key,
            HydrologyPlannerSettings.SurfacePool pool,
            long profileSeed,
            int tileSize
    ) {
        int minimumX = key.minimumBlockX(tileSize);
        int minimumZ = key.minimumBlockZ(tileSize);
        int spacing = pool.spacing();
        int xOffset = HydrologyHash.between(HydrologyHash.mix(profileSeed, DEEP_FLUID_X_OFFSET_SALT), 0, spacing - 1);
        int zOffset = HydrologyHash.between(HydrologyHash.mix(profileSeed, DEEP_FLUID_Z_OFFSET_SALT), 0, spacing - 1);
        long firstCellX = ceilDiv((long) minimumX - xOffset, spacing);
        long firstCellZ = ceilDiv((long) minimumZ - zOffset, spacing);
        long lastCellX = Math.floorDiv((long) minimumX + tileSize - 1L - xOffset, spacing);
        long lastCellZ = Math.floorDiv((long) minimumZ + tileSize - 1L - zOffset, spacing);
        ArrayList<DeepSite> sites = new ArrayList<>();
        for (long cellZ = firstCellZ; cellZ <= lastCellZ; cellZ++) {
            for (long cellX = firstCellX; cellX <= lastCellX; cellX++) {
                long stable = HydrologyHash.mix(profileSeed, cellX, cellZ);
                // Jitter inside the cell keeps pools off a visible grid.
                int jitter = Math.max(1, spacing / 3);
                int x = Math.toIntExact(xOffset + cellX * spacing + HydrologyHash.between(HydrologyHash.mix(stable, 11), -jitter, jitter));
                int z = Math.toIntExact(zOffset + cellZ * spacing + HydrologyHash.between(HydrologyHash.mix(stable, 12), -jitter, jitter));
                HydrologyTerrainSample terrain = sampleLandBasis(x, z);
                sites.add(new DeepSite(x, z, terrain == null ? 0 : terrain.naturalHeight(), stable));
            }
        }
        sites.sort(Comparator.comparingLong(DeepSite::stableId));
        return sites;
    }

    private HydrologyCandidateRejection poolAdmission(
            HydrologyPlannerSettings.SurfacePool pool,
            DeepSite site,
            List<RiverCourse> courses
    ) {
        HydrologyTerrainSample terrain = sampleLandBasis(site.x(), site.z());
        if (terrain == null || terrain.ocean() || terrain.naturalHeight() <= settings.seaLevel() + 1) {
            return HydrologyCandidateRejection.SURFACE_EXPOSURE;
        }
        if (!terrain.transitAllowed() || !terrain.surfacePoolKeys().contains(pool.id())) {
            return HydrologyCandidateRejection.POLICY_EXCLUDED;
        }
        long clearance = pool.maximumRadius() + (long) StrictMath.ceil(settings.surface().shoreWidth())
                + settings.surface().banks().maximumBlendWidth() + settings.surface().maximumWidth();
        long clearanceSquared = clearance * clearance;
        for (RiverCourse course : courses) {
            for (HydraulicSegment segment : course.segments()) {
                for (HydrologyPoint point : segment.centerline()) {
                    long deltaX = (long) point.x() - site.x();
                    long deltaZ = (long) point.z() - site.z();
                    if (deltaX * deltaX + deltaZ * deltaZ < clearanceSquared) {
                        return HydrologyCandidateRejection.SOURCE_SPACING;
                    }
                }
            }
        }
        return null;
    }

    private RiverCourse buildSurfacePoolCourse(HydrologyPlannerSettings.SurfacePool pool, DeepSite site) {
        int radius = HydrologyHash.between(HydrologyHash.mix(site.stableId(), 3), pool.minimumRadius(), pool.maximumRadius());
        HydrologyTerrainSample terrain = sampleLandBasis(site.x(), site.z());
        List<HydrologyPoint> points = List.of(
                new HydrologyPoint(site.x() - 1, site.head(), site.z()),
                new HydrologyPoint(site.x(), site.head(), site.z()),
                new HydrologyPoint(site.x() + 1, site.head(), site.z())
        );
        SurfaceCenterline centerline = SurfaceCenterline.densify(points);
        int count = centerline.size();
        double[] halfWidth = new double[count];
        double[] depth = new double[count];
        double[] bank = new double[count];
        Arrays.fill(halfWidth, radius);
        Arrays.fill(depth, pool.depth());
        Arrays.fill(bank, terrain == null ? 1D : terrain.bankMultiplier());
        ChannelProfile profile = new ChannelProfile(halfWidth, depth, bank);
        ValleyProfile valley = new ValleyProfileSolver(settings.surface(), this::sampleBasis, settings.seaLevel(), 0)
                .solve(centerline, profile, SurfaceTerminal.SINKHOLE, Integer.MIN_VALUE);
        if (!valley.accepted()) {
            return null;
        }
        int center = Math.min(1, valley.exposedStations() - 1);
        int head = valley.head()[center];
        // A pool belongs in a hollow or on level ground: the whole rim must sit within a few blocks of the
        // water, or the bowl becomes a scar dug into a slope.
        int rimAllowance = pool.depth() + settings.surface().banks().sink() + 2;
        for (int station = 0; station < valley.exposedStations(); station++) {
            if (valley.crossMax()[station] - valley.head()[station] > rimAllowance) {
                return null;
            }
        }
        long courseId = HydrologyHash.mix(worldSeed, SURFACE_POOL_SALT, HydrologyHash.text(pool.id()), site.stableId());
        long segmentId = HydrologyHash.mix(courseId, SEGMENT_SALT, HydrologyFeatureType.STANDING_POOL.ordinal());
        ArrayList<HydrologyPoint> centerlinePoints = new ArrayList<>(points.size());
        for (HydrologyPoint point : points) {
            centerlinePoints.add(new HydrologyPoint(point.x(), head, point.z()));
        }
        HydraulicSegment segment = new HydraulicSegment(
                segmentId,
                courseId,
                HydrologyFeatureType.STANDING_POOL,
                head,
                head,
                radius * 2,
                pool.depth(),
                false,
                false,
                centerlinePoints
        );
        return new RiverCourse(
                courseId,
                RiverCourseType.SURFACE_POOL,
                OptionalLong.empty(),
                OptionalLong.empty(),
                pool.id(),
                1,
                List.of(),
                List.of(segment)
        );
    }

    private void addPoolDiagnostic(
            DeepSite site,
            HydrologyCandidateRejection rejection,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        diagnostics.add(new HydrologyDiagnosticCandidate(
                HydrologyHash.mix(site.stableId(), DIAGNOSTIC_SALT, rejection.ordinal()),
                HydrologyCandidateKind.POOL,
                HydrologyFeatureType.STANDING_POOL,
                new HydrologyPoint(site.x(), site.head(), site.z()),
                rejection,
                0
        ));
    }

    /**
     * Sea caves: coastal grottos the sea opens into without a river. Every owned land node with a proven
     * ocean boundary is a site, ranked by how high the coast stands over the sea; each accepted site is an
     * independent course whose chamber is swept inland from the shoreline and opens only through its ocean
     * face. Rivers keep their outlets: sea caves stay clear of every mouth and grotto already planned.
     */
    private void compileSeaCaves(
            SampledGrid grid,
            List<RiverCourse> courses,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        HydrologyPlannerSettings.SeaCaves seaCaves = settings.seaCaves();
        if (!seaCaves.enabled() || seaCaves.maximumPerTile() == 0) {
            return;
        }
        HydrologyPlannerSettings.Grotto grotto = settings.outlets().coastalGrotto();
        boolean fits = sweptGrottoVolume(grotto, seaCaves.depth()) <= grotto.maximumVolume();
        ArrayList<SeaCaveSite> accepted = new ArrayList<>();
        for (SeaCaveSite site : seaCaveSites(grid)) {
            if (accepted.size() >= seaCaves.maximumPerTile()) {
                addSeaCaveDiagnostic(site, HydrologyCandidateRejection.SOURCE_QUOTA, diagnostics);
                continue;
            }
            if (!fits) {
                addSeaCaveDiagnostic(site, HydrologyCandidateRejection.VOLUME_LIMIT, diagnostics);
                continue;
            }
            HydrologyCandidateRejection rejection = seaCaveAdmission(site, accepted, courses);
            if (rejection != null) {
                addSeaCaveDiagnostic(site, rejection, diagnostics);
                continue;
            }
            courses.add(buildSeaCaveCourse(site));
            accepted.add(site);
        }
    }

    private ArrayList<SeaCaveSite> seaCaveSites(SampledGrid grid) {
        int depth = settings.seaCaves().depth();
        ArrayList<SeaCaveSite> sites = new ArrayList<>();
        for (GridNode land : grid.nodes()) {
            HydrologyTerrainSample terrain = land.terrain();
            if (!grid.owns(land.x(), land.z())
                    || terrain.ocean()
                    || !terrain.transitAllowed()
                    || !terrain.outletAllowed()) {
                continue;
            }
            GridNode ocean = firstOceanNeighbor(grid, land);
            if (ocean == null) {
                continue;
            }
            HydrologyOceanBoundaryRefiner.Result boundary = refineOceanBoundary(land, ocean);
            if (boundary == null
                    || !boundary.landwardTerrain().transitAllowed()
                    || !boundary.landwardTerrain().outletAllowed()) {
                continue;
            }
            long stableId = HydrologyHash.mix(worldSeed, SEA_CAVE_SALT, land.id(), ocean.id());
            HydrologyPoint inner = seaCaveInnerPoint(land, ocean, boundary.landwardPoint(), stableId, depth);
            int seaLevel = settingsSeaLevel(ocean.terrain());
            int coastHeight = seaCaveCoastHeight(boundary, inner, seaLevel);
            sites.add(new SeaCaveSite(land, ocean, boundary, inner, coastHeight, stableId));
        }
        sites.sort(Comparator.comparingInt(SeaCaveSite::coastHeight).reversed()
                .thenComparingLong(SeaCaveSite::stableId));
        return sites;
    }

    /**
     * How high the coast stands over the sea across the chamber: the lowest land at the shoreline, at the
     * back of the chamber and at both flanks of each. Flanks in the sea are ignored (the chamber opens
     * there like its face); a back in the sea leaves no coast at all.
     */
    private int seaCaveCoastHeight(HydrologyOceanBoundaryRefiner.Result boundary, HydrologyPoint inner, int seaLevel) {
        HydrologyTerrainSample innerTerrain = sampleLandBasis(inner.x(), inner.z());
        if (innerTerrain == null) {
            return 0;
        }
        HydrologyPoint landward = boundary.landwardPoint();
        int lowest = Math.min(boundary.landwardTerrain().naturalHeight(), innerTerrain.naturalHeight());
        int radius = settings.outlets().coastalGrotto().horizontalRadius();
        // Flanks lie along the shore, across the crossing from the landward point to the sea.
        int spanX = boundary.oceanPoint().x() - landward.x();
        int spanZ = boundary.oceanPoint().z() - landward.z();
        double length = StrictMath.hypot(spanX, spanZ);
        int flankX = (int) StrictMath.round(-spanZ / length * radius);
        int flankZ = (int) StrictMath.round(spanX / length * radius);
        int[][] flanks = {
                {landward.x() + flankX, landward.z() + flankZ},
                {landward.x() - flankX, landward.z() - flankZ},
                {inner.x() + flankX, inner.z() + flankZ},
                {inner.x() - flankX, inner.z() - flankZ}
        };
        for (int[] flank : flanks) {
            HydrologyTerrainSample terrain = sampleLandBasis(flank[0], flank[1]);
            if (terrain != null) {
                lowest = Math.min(lowest, terrain.naturalHeight());
            }
        }
        return lowest - seaLevel;
    }

    // The chamber runs inland along the coast normal, turned by a stable jitter so caves do not all
    // face the same way along a straight shore.
    private HydrologyPoint seaCaveInnerPoint(
            GridNode land,
            GridNode ocean,
            HydrologyPoint landward,
            long stableId,
            int depth
    ) {
        double normalX = land.x() - ocean.x();
        double normalZ = land.z() - ocean.z();
        double length = StrictMath.hypot(normalX, normalZ);
        double jitter = StrictMath.toRadians(
                (HydrologyHash.unit(HydrologyHash.mix(stableId, 7)) * 2D - 1D)
                        * settings.seaCaves().sweepJitterDegrees()
        );
        double cos = StrictMath.cos(jitter);
        double sin = StrictMath.sin(jitter);
        double unitX = normalX / length;
        double unitZ = normalZ / length;
        double inlandX = unitX * cos - unitZ * sin;
        double inlandZ = unitX * sin + unitZ * cos;
        return new HydrologyPoint(
                (int) StrictMath.round(landward.x() + inlandX * depth),
                landward.y(),
                (int) StrictMath.round(landward.z() + inlandZ * depth)
        );
    }

    private HydrologyCandidateRejection seaCaveAdmission(
            SeaCaveSite site,
            List<SeaCaveSite> accepted,
            List<RiverCourse> courses
    ) {
        HydrologyPlannerSettings.SeaCaves seaCaves = settings.seaCaves();
        if (site.coastHeight() < seaCaves.minimumCoastHeight()) {
            return HydrologyCandidateRejection.SURFACE_HEAD_RANGE;
        }
        HydrologyPoint landward = site.boundary().landwardPoint();
        long spacingSquared = (long) seaCaves.minimumSpacing() * seaCaves.minimumSpacing();
        for (SeaCaveSite other : accepted) {
            if (landward.distanceSquared2D(other.boundary().landwardPoint()) < spacingSquared) {
                return HydrologyCandidateRejection.SOURCE_SPACING;
            }
        }
        long clearance = 2L * settings.outlets().coastalGrotto().horizontalRadius() + seaCaves.depth();
        long clearanceSquared = clearance * clearance;
        for (RiverCourse course : courses) {
            if (course.type() == RiverCourseType.SEA_CAVE) {
                continue;
            }
            for (HydraulicSegment segment : course.segments()) {
                if (segment.type() != HydrologyFeatureType.MOUTH
                        && segment.type() != HydrologyFeatureType.COASTAL_GROTTO
                        && segment.type() != HydrologyFeatureType.INLAND_GROTTO) {
                    continue;
                }
                for (HydrologyPoint point : segment.centerline()) {
                    if (landward.distanceSquared2D(point) < clearanceSquared) {
                        return HydrologyCandidateRejection.SOURCE_SPACING;
                    }
                }
            }
        }
        return null;
    }

    private RiverCourse buildSeaCaveCourse(SeaCaveSite site) {
        HydrologyPlannerSettings.Grotto grotto = settings.outlets().coastalGrotto();
        long courseId = HydrologyHash.mix(worldSeed, COURSE_SALT, SEA_CAVE_SALT, site.stableId());
        String profileKey = chooseProfile(site.boundary().landwardTerrain(), courseId);
        int seaLevel = settingsSeaLevel(site.ocean().terrain());
        // The last point is the ocean connection: the footprint opens the chamber there and nowhere else.
        List<HydrologyPoint> centerline = line(
                withY(site.inner(), seaLevel),
                withY(site.boundary().oceanPoint(), seaLevel),
                settings.routing().refinementSpacing()
        );
        ArrayList<HydraulicSegment> segments = new ArrayList<>(1);
        addFlatSegment(
                courseId,
                HydrologyFeatureType.COASTAL_GROTTO,
                seaLevel,
                grotto.horizontalRadius() * 2,
                grotto.verticalRadius(),
                centerline,
                segments
        );
        return new RiverCourse(
                courseId,
                RiverCourseType.SEA_CAVE,
                OptionalLong.empty(),
                OptionalLong.empty(),
                profileKey,
                1,
                List.of(),
                segments
        );
    }

    private void addSeaCaveDiagnostic(
            SeaCaveSite site,
            HydrologyCandidateRejection rejection,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        diagnostics.add(new HydrologyDiagnosticCandidate(
                HydrologyHash.mix(site.stableId(), DIAGNOSTIC_SALT, rejection.ordinal()),
                HydrologyCandidateKind.OUTLET,
                HydrologyFeatureType.COASTAL_GROTTO,
                site.boundary().landwardPoint(),
                rejection,
                0
        ));
    }

    /**
     * The raster a swept grotto occupies: one ellipsoid plus its central cross-section extruded over the
     * sweep. The containment filter measures the real carve; this only refuses chambers that cannot fit.
     */
    static long sweptGrottoVolume(HydrologyPlannerSettings.Grotto grotto, int depth) {
        int radius = grotto.horizontalRadius();
        long section = 0L;
        for (int deltaX = -radius; deltaX <= radius; deltaX++) {
            double normalized = Math.min(1D, Math.abs(deltaX) / Math.max(1D, radius));
            double scale = StrictMath.sqrt(Math.max(0D, 1D - normalized * normalized));
            section += (long) StrictMath.ceil(grotto.verticalRadius() * scale)
                    + (long) StrictMath.floor(grotto.headroom() * scale)
                    + 1L;
        }
        return HydrologyPlannerSettings.ellipsoidVolume(radius, grotto.verticalRadius(), grotto.headroom())
                + section * depth;
    }

    private record SeaCaveSite(
            GridNode land,
            GridNode ocean,
            HydrologyOceanBoundaryRefiner.Result boundary,
            HydrologyPoint inner,
            int coastHeight,
            long stableId
    ) {
    }

    private void compileDeepFluidCourses(
            HydrologyTileKey key,
            List<RiverCourse> courses,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        for (HydrologyPlannerSettings.DeepFluid deepFluid : settings.deepFluids()) {
            if (!deepFluid.enabled() || deepFluid.maximumPerTile() == 0) {
                continue;
            }
            ArrayList<DeepSite> sites = deepSites(key, deepFluid, diagnostics);
            int target = Math.min(
                    deepFluid.maximumPerTile(),
                    expectedCount(deepFluid.density(), HydrologyHash.mix(
                            worldSeed,
                            DEEP_FLUID_SALT,
                            HydrologyHash.text(deepFluid.id()),
                            key.tileX(),
                            key.tileZ()
                    ))
            );
            for (int index = 0; index < sites.size(); index++) {
                DeepSite site = sites.get(index);
                if (index >= target) {
                    addDeepDiagnostic(
                            deepFluid,
                            site,
                            HydrologyCandidateRejection.SOURCE_QUOTA,
                            diagnostics
                    );
                    continue;
                }
                RiverCourse course = buildDeepFluidCourse(deepFluid, site, index);
                if (course != null) {
                    courses.add(course);
                } else {
                    addDeepDiagnostic(
                            deepFluid,
                            site,
                            HydrologyCandidateRejection.VOLUME_LIMIT,
                            diagnostics
                    );
                }
            }
        }
    }

    private ArrayList<DeepSite> deepSites(
            HydrologyTileKey key,
            HydrologyPlannerSettings.DeepFluid deepFluid,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        int tileSize = settings.routing().tileSize();
        int minimumX = key.minimumBlockX(tileSize);
        int minimumZ = key.minimumBlockZ(tileSize);
        int spacing = deepFluid.spacing();
        long profileSeed = HydrologyHash.mix(worldSeed, DEEP_FLUID_SALT, HydrologyHash.text(deepFluid.id()));
        int xOffset = HydrologyHash.between(
                HydrologyHash.mix(profileSeed, DEEP_FLUID_X_OFFSET_SALT),
                0,
                spacing - 1
        );
        int zOffset = HydrologyHash.between(
                HydrologyHash.mix(profileSeed, DEEP_FLUID_Z_OFFSET_SALT),
                0,
                spacing - 1
        );
        long firstCellX = ceilDiv((long) minimumX - xOffset, spacing);
        long firstCellZ = ceilDiv((long) minimumZ - zOffset, spacing);
        long lastCellX = Math.floorDiv((long) minimumX + tileSize - 1L - xOffset, spacing);
        long lastCellZ = Math.floorDiv((long) minimumZ + tileSize - 1L - zOffset, spacing);
        ArrayList<DeepSite> sites = new ArrayList<>();
        for (long cellZ = firstCellZ; cellZ <= lastCellZ; cellZ++) {
            for (long cellX = firstCellX; cellX <= lastCellX; cellX++) {
                long stable = HydrologyHash.mix(
                        worldSeed,
                        DEEP_FLUID_SALT,
                        HydrologyHash.text(deepFluid.id()),
                        cellX,
                        cellZ
                );
                int x = Math.toIntExact(xOffset + cellX * spacing);
                int z = Math.toIntExact(zOffset + cellZ * spacing);
                int head = sampleGeometry(
                        HydrologyGeometrySampler.Field.DEEP_FLUID_HEIGHT,
                        deepFluid.id(),
                        x,
                        z,
                        stable,
                        deepFluid.minimumY(),
                        deepFluid.maximumY()
                );
                DeepSite site = new DeepSite(x, z, head, stable);
                HydrologyTerrainSample terrain = sampleLandBasis(x, z);
                int verticalRadius = HydrologyHash.between(
                        HydrologyHash.mix(stable, 3),
                        deepFluid.minimumVerticalRadius(),
                        deepFluid.maximumVerticalRadius()
                );
                if (!deepSiteFits(terrain, deepFluid, head, verticalRadius, minimumY)) {
                    if (terrain != null && !terrain.ocean()) {
                        addDeepDiagnostic(
                                deepFluid,
                                site,
                                HydrologyCandidateRejection.CAVE_CONTAINMENT,
                                diagnostics
                        );
                    }
                    continue;
                }
                sites.add(site);
            }
        }
        sites.sort(Comparator.comparingLong(DeepSite::stableId));
        return sites;
    }

    private long ceilDiv(long value, int divisor) {
        long quotient = Math.floorDiv(value, divisor);
        return quotient * divisor == value ? quotient : quotient + 1L;
    }

    static boolean deepSiteFits(
            HydrologyTerrainSample terrain,
            HydrologyPlannerSettings.DeepFluid deepFluid,
            int head,
            int verticalRadius,
            int minimumY
    ) {
        if (terrain == null || terrain.ocean()) {
            return false;
        }
        int lowerEnvelope = deepFluid.containedPools()
                ? Math.max(deepFluid.channelDepth(), verticalRadius)
                : deepFluid.channelDepth();
        return (long) head - lowerEnvelope > minimumY
                && head + deepFluid.headroom() < terrain.naturalHeight();
    }

    private void addDeepDiagnostic(
            HydrologyPlannerSettings.DeepFluid deepFluid,
            DeepSite site,
            HydrologyCandidateRejection rejection,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        diagnostics.add(new HydrologyDiagnosticCandidate(
                HydrologyHash.mix(site.stableId(), DIAGNOSTIC_SALT, rejection.ordinal()),
                HydrologyCandidateKind.DEEP_FLUID,
                deepFluid.containedPools() ? HydrologyFeatureType.DEEP_POOL : HydrologyFeatureType.DEEP_CHANNEL,
                new HydrologyPoint(site.x(), site.head(), site.z()),
                rejection, 0
        ));
    }

    private RiverCourse buildDeepFluidCourse(
            HydrologyPlannerSettings.DeepFluid deepFluid,
            DeepSite site,
            int siteIndex
    ) {
        long courseId = HydrologyHash.mix(
                worldSeed,
                COURSE_SALT,
                DEEP_FLUID_SALT,
                HydrologyHash.text(deepFluid.id()),
                site.stableId(),
                siteIndex
        );
        int horizontalRadius = HydrologyHash.between(
                HydrologyHash.mix(site.stableId(), 2),
                deepFluid.minimumHorizontalRadius(),
                deepFluid.maximumHorizontalRadius()
        );
        int verticalRadius = HydrologyHash.between(
                HydrologyHash.mix(site.stableId(), 3),
                deepFluid.minimumVerticalRadius(),
                deepFluid.maximumVerticalRadius()
        );
        ArrayList<HydraulicSegment> segments = new ArrayList<>();
        HydrologyPoint center = new HydrologyPoint(site.x(), site.head(), site.z());
        long usedVolume = 0L;
        if (deepFluid.containedPools()) {
            long poolVolume = HydrologyPlannerSettings.ellipsoidVolume(
                    horizontalRadius,
                    Math.max(deepFluid.channelDepth(), verticalRadius),
                    deepFluid.headroom()
            );
            if (poolVolume > deepFluid.maximumVolume()) {
                return null;
            }
            usedVolume = poolVolume;
            long poolId = HydrologyHash.mix(worldSeed, SEGMENT_SALT, courseId, HydrologyFeatureType.DEEP_POOL.ordinal());
            segments.add(new HydraulicSegment(
                    poolId,
                    courseId,
                    HydrologyFeatureType.DEEP_POOL,
                    site.head(),
                    site.head(),
                    horizontalRadius * 2 + 1,
                    Math.max(deepFluid.channelDepth(), verticalRadius),
                    false,
                    false,
                    List.of(center)
            ));
        }
        if (deepFluid.shortChannels() && deepFluid.maximumChannelLength() > 0) {
            int volumeBoundedMaximum = deepFluid.maximumChannelLength();
            while (volumeBoundedMaximum >= deepFluid.minimumChannelLength()
                    && usedVolume + deepChannelVolumeBound(deepFluid, volumeBoundedMaximum)
                    > deepFluid.maximumVolume()) {
                volumeBoundedMaximum--;
            }
            if (volumeBoundedMaximum >= deepFluid.minimumChannelLength()) {
                int length = HydrologyHash.between(
                    HydrologyHash.mix(site.stableId(), 4),
                    deepFluid.minimumChannelLength(),
                    volumeBoundedMaximum
                );
                long channelId = HydrologyHash.mix(worldSeed, SEGMENT_SALT, courseId, HydrologyFeatureType.DEEP_CHANNEL.ordinal());
                segments.add(new HydraulicSegment(
                        channelId,
                        courseId,
                        HydrologyFeatureType.DEEP_CHANNEL,
                        site.head(),
                        site.head(),
                        deepFluid.channelWidth(),
                        deepFluid.channelDepth(),
                        false,
                        false,
                        organicDeepChannelCenterline(
                                center,
                                length,
                                settings.routing().refinementSpacing(),
                                site.stableId()
                        )
                ));
            }
        }
        if (segments.isEmpty()) {
            return null;
        }
        return new RiverCourse(
                courseId,
                RiverCourseType.DEEP_FLUID,
                OptionalLong.empty(),
                OptionalLong.empty(),
                deepFluid.id(),
                1,
                List.of(),
                segments
        );
    }

    private long deepChannelVolumeBound(HydrologyPlannerSettings.DeepFluid deepFluid, int length) {
        int radius = Math.max(1, deepFluid.channelWidth() / 2);
        int points = (int) StrictMath.ceil(
                length * StrictMath.sqrt(2D) / settings.routing().refinementSpacing()
        ) + 1;
        long horizontalEnvelope = (long) (radius * 2 + 1) * (radius * 2 + 1);
        long verticalEnvelope = deepFluid.channelDepth() + deepFluid.headroom() + 1L;
        return horizontalEnvelope * verticalEnvelope * points;
    }

    private List<HydrologyPoint> organicDeepChannelCenterline(
            HydrologyPoint start,
            int length,
            int spacing,
            long stableId
    ) {
        double heading = HydrologyHash.unit(HydrologyHash.mix(stableId, DEEP_CHANNEL_HEADING_SALT))
                * StrictMath.PI * 2D;
        double forwardX = StrictMath.cos(heading);
        double forwardZ = StrictMath.sin(heading);
        double lateralX = -forwardZ;
        double lateralZ = forwardX;
        double bendEnvelope = Math.max(1.5D, Math.min(8D, length * 0.3D));
        double firstBend = signedOrganicOffset(stableId, DEEP_CHANNEL_FIRST_BEND_SALT, bendEnvelope);
        double secondBend = signedOrganicOffset(stableId, DEEP_CHANNEL_SECOND_BEND_SALT, bendEnvelope);
        if (StrictMath.signum(firstBend) == StrictMath.signum(secondBend)) {
            secondBend = -secondBend;
        }

        double endX = start.x() + forwardX * length;
        double endZ = start.z() + forwardZ * length;
        double firstControlX = start.x() + forwardX * length / 3D + lateralX * firstBend;
        double firstControlZ = start.z() + forwardZ * length / 3D + lateralZ * firstBend;
        double secondControlX = start.x() + forwardX * length * 2D / 3D + lateralX * secondBend;
        double secondControlZ = start.z() + forwardZ * length * 2D / 3D + lateralZ * secondBend;
        int steps = Math.max(4, (int) StrictMath.ceil(length / (double) Math.max(2, spacing)) * 2);
        ArrayList<HydrologyPoint> points = new ArrayList<>(steps + 1);
        for (int step = 0; step <= steps; step++) {
            double progress = step / (double) steps;
            double inverse = 1D - progress;
            double x = inverse * inverse * inverse * start.x()
                    + 3D * inverse * inverse * progress * firstControlX
                    + 3D * inverse * progress * progress * secondControlX
                    + progress * progress * progress * endX;
            double z = inverse * inverse * inverse * start.z()
                    + 3D * inverse * inverse * progress * firstControlZ
                    + 3D * inverse * progress * progress * secondControlZ
                    + progress * progress * progress * endZ;
            HydrologyPoint point = new HydrologyPoint(
                    (int) StrictMath.round(x),
                    start.y(),
                    (int) StrictMath.round(z)
            );
            if (points.isEmpty() || point.x() != points.getLast().x() || point.z() != points.getLast().z()) {
                points.add(point);
            }
        }
        return List.copyOf(points);
    }

    private double signedOrganicOffset(long stableId, long salt, double envelope) {
        double unit = HydrologyHash.unit(HydrologyHash.mix(stableId, salt));
        double magnitude = envelope * (0.55D + unit * 0.45D);
        return (HydrologyHash.mix(stableId, salt, 1L) & 1L) == 0L ? magnitude : -magnitude;
    }

    private List<HydrologyPoint> line(HydrologyPoint start, HydrologyPoint end, int spacing) {
        double distance = StrictMath.hypot(end.x() - start.x(), end.z() - start.z());
        int steps = Math.max(1, (int) StrictMath.ceil(distance / spacing));
        ArrayList<HydrologyPoint> points = new ArrayList<>(steps + 1);
        for (int step = 0; step <= steps; step++) {
            double progress = step / (double) steps;
            int x = (int) StrictMath.round(start.x() + (end.x() - start.x()) * progress);
            int z = (int) StrictMath.round(start.z() + (end.z() - start.z()) * progress);
            HydrologyPoint point = new HydrologyPoint(x, start.y(), z);
            if (points.isEmpty() || point.x() != points.getLast().x() || point.z() != points.getLast().z()) {
                points.add(point);
            }
        }
        return List.copyOf(points);
    }

    private int scaledDimension(
            int styledBase,
            int minimum,
            int maximum,
            int discharge,
            double multiplier
    ) {
        double flowScale = Math.min(
                1D,
                StrictMath.log(discharge + 1D) / StrictMath.log(settings.underground().wideningSources() + 1D)
        );
        int base = styledBase + (int) StrictMath.round((maximum - styledBase) * flowScale);
        return clamp((int) StrictMath.round(base * multiplier), minimum, maximum);
    }

    private int maximumSurfaceDischarge(List<DrainageEdge> edges) {
        int maximum = 1;
        for (DrainageEdge edge : edges) {
            maximum = Math.max(maximum, edge.contributingSurfaceSources());
        }
        return maximum;
    }

    private int maximumUndergroundDischarge(List<DrainageEdge> edges) {
        int maximum = 1;
        for (DrainageEdge edge : edges) {
            maximum = Math.max(maximum, edge.contributingUndergroundSources());
        }
        return maximum;
    }

    private String chooseProfile(HydrologyTerrainSample terrain, long courseId) {
        List<String> profiles = terrain.preferredProfileKeys();
        int index = HydrologyHash.between(HydrologyHash.mix(worldSeed, courseId, HydrologyHash.text("profile")), 0, profiles.size() - 1);
        return profiles.get(index);
    }

    private int outletHead(RiverOutlet outlet) {
        return outlet.directOcean() ? outlet.seaLevel() : outlet.connectionPoint().y();
    }

    private int settingsSeaLevel(HydrologyTerrainSample terrain) {
        return settings.seaLevel();
    }

    private static HydrologyPoint withY(HydrologyPoint point, int y) {
        return new HydrologyPoint(point.x(), y, point.z());
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private int sampleGeometry(
            HydrologyGeometrySampler.Field field,
            String profileKey,
            int x,
            int z,
            long stableId,
            int minimum,
            int maximum
    ) {
        HydrologyGeometrySampler.Request request = new HydrologyGeometrySampler.Request(
                field,
                profileKey,
                x,
                z,
                stableId,
                minimum,
                maximum
        );
        PlanningSamples samples = planningSamples.get();
        if (samples != null) {
            Integer cached = samples.geometry.get(request);
            if (cached != null) {
                return cached;
            }
        }
        int sampled = geometrySampler.sample(request);
        if (sampled < minimum || sampled > maximum) {
            throw new IllegalStateException(
                    "Hydrology geometry sampler returned " + sampled + " outside " + minimum + ".." + maximum
                            + " for " + field + " at " + x + "," + z + "."
            );
        }
        if (samples != null) {
            samples.geometry.put(request, sampled);
        }
        return sampled;
    }

    private HydrologyTerrainSample sampleBasis(int blockX, int blockZ) {
        PlanningSamples samples = planningSamples.get();
        if (samples == null) {
            return naturalSampler == null
                    ? sampler.sample(blockX, blockZ)
                    : naturalSampler.sampleBasis(blockX, blockZ);
        }
        long packed = RiverFootprint.pack(blockX, blockZ);
        HydrologyTerrainSample cached = samples.basis.get(packed);
        if (cached != null || samples.basis.containsKey(packed)) {
            return cached;
        }
        HydrologyTerrainSample sampled = naturalSampler == null
                ? sampler.sample(blockX, blockZ)
                : naturalSampler.sampleBasis(blockX, blockZ);
        samples.basis.put(packed, sampled);
        return sampled;
    }

    private HydrologyTerrainSample sampleLandBasis(int blockX, int blockZ) {
        HydrologyTerrainSample terrain = sampleBasis(blockX, blockZ);
        return terrain == null || terrain.ocean() ? null : terrain;
    }

    private HydrologyTerrainSample sampleBasisWithoutSlope(int blockX, int blockZ) {
        if (naturalSampler == null) {
            return sampleBasis(blockX, blockZ);
        }
        PlanningSamples samples = planningSamples.get();
        if (samples == null) {
            return naturalSampler.sampleBasisWithoutSlope(blockX, blockZ);
        }
        long packed = RiverFootprint.pack(blockX, blockZ);
        HydrologyTerrainSample fullSample = samples.basis.get(packed);
        if (fullSample != null || samples.basis.containsKey(packed)) {
            return fullSample;
        }
        HydrologyTerrainSample cached = samples.basisWithoutSlope.get(packed);
        if (cached != null || samples.basisWithoutSlope.containsKey(packed)) {
            return cached;
        }
        HydrologyTerrainSample sampled = naturalSampler.sampleBasisWithoutSlope(blockX, blockZ);
        samples.basisWithoutSlope.put(packed, sampled);
        return sampled;
    }

    private HydrologyTerrainSample sampleLandBasisWithoutSlope(int blockX, int blockZ) {
        HydrologyTerrainSample terrain = sampleBasisWithoutSlope(blockX, blockZ);
        return terrain == null || terrain.ocean() ? null : terrain;
    }

    private HydrologyTerrainSample sampleDetailed(int blockX, int blockZ) {
        PlanningSamples samples = planningSamples.get();
        if (samples == null) {
            return sampler.sample(blockX, blockZ);
        }
        long packed = RiverFootprint.pack(blockX, blockZ);
        HydrologyTerrainSample cached = samples.detailed.get(packed);
        if (cached != null || samples.detailed.containsKey(packed)) {
            return cached;
        }
        HydrologyTerrainSample sampled = sampler.sample(blockX, blockZ);
        samples.detailed.put(packed, sampled);
        return sampled;
    }

    private HydrologyRoutingTerrainSampler.NaturalClassification classifyNatural(int blockX, int blockZ) {
        PlanningSamples samples = planningSamples.get();
        if (samples == null) {
            return naturalSampler.classifyNatural(blockX, blockZ);
        }
        long packed = RiverFootprint.pack(blockX, blockZ);
        HydrologyRoutingTerrainSampler.NaturalClassification cached = samples.classifications.get(packed);
        if (cached != null) {
            return cached;
        }
        HydrologyRoutingTerrainSampler.NaturalClassification sampled = Objects.requireNonNull(
                naturalSampler.classifyNatural(blockX, blockZ),
                "Hydrology natural terrain classifier returned null at " + blockX + "," + blockZ
        );
        samples.classifications.put(packed, sampled);
        return sampled;
    }

    private static final class PointRoutingTerrainSampler implements HydrologyRoutingTerrainSampler {
        private final HydrologyTerrainSampler sampler;

        private PointRoutingTerrainSampler(HydrologyTerrainSampler sampler) {
            this.sampler = Objects.requireNonNull(sampler, "sampler");
        }

        @Override
        public HydrologyTerrainSample[] sampleGrid(GridRequest request) {
            int minimumX = request.minimumX();
            int minimumZ = request.minimumZ();
            int width = request.width();
            int spacing = request.spacing();
            HydrologyTerrainSample[] samples = new HydrologyTerrainSample[Math.multiplyExact(width, width)];
            for (int gridZ = 0; gridZ < width; gridZ++) {
                int z = Math.toIntExact((long) minimumZ + (long) gridZ * spacing);
                for (int gridX = 0; gridX < width; gridX++) {
                    int x = Math.toIntExact((long) minimumX + (long) gridX * spacing);
                    samples[gridZ * width + gridX] = sampler.sample(x, z);
                }
            }
            return samples;
        }

        @Override
        public NaturalClassification classifyNatural(int blockX, int blockZ) {
            HydrologyTerrainSample terrain = sampler.sample(blockX, blockZ);
            if (terrain == null) {
                return NaturalClassification.UNAVAILABLE;
            }
            return terrain.ocean() ? NaturalClassification.OCEAN : NaturalClassification.LAND;
        }
    }

    private static final class PlanningSamples {
        private final Long2ObjectOpenHashMap<HydrologyTerrainSample> basis;
        private final Long2ObjectOpenHashMap<HydrologyTerrainSample> basisWithoutSlope;
        private final Long2ObjectOpenHashMap<HydrologyTerrainSample> detailed;
        private final Long2ObjectOpenHashMap<HydrologyRoutingTerrainSampler.NaturalClassification> classifications;
        private final HashMap<HydrologyGeometrySampler.Request, Integer> geometry;
        private final HashMap<MinimumLandHeightKey, Integer> minimumLandHeights;
        private final HashMap<Integer, List<GridOffset>> radialOffsets;
        private final HashMap<UndergroundSegmentCapKey, Integer> undergroundSegmentCaps;
        private final HashMap<Long, HydrologyPoint> routeAnchors;
        private final IdentityHashMap<SampledGrid, HashMap<SurfaceRouteKey, List<HydrologyPoint>>> surfaceRoutes;
        private final HydrologyCaveCourseFilter.CandidateCache caveCandidates;
        private final HydrologyCaveContainmentPlanner.ValidationCache caveValidations;

        private PlanningSamples() {
            this.basis = new Long2ObjectOpenHashMap<>();
            this.basisWithoutSlope = new Long2ObjectOpenHashMap<>();
            this.detailed = new Long2ObjectOpenHashMap<>();
            this.classifications = new Long2ObjectOpenHashMap<>();
            this.geometry = new HashMap<>();
            this.minimumLandHeights = new HashMap<>();
            this.radialOffsets = new HashMap<>();
            this.undergroundSegmentCaps = new HashMap<>();
            this.routeAnchors = new HashMap<>();
            this.surfaceRoutes = new IdentityHashMap<>();
            this.caveCandidates = new HydrologyCaveCourseFilter.CandidateCache();
            this.caveValidations = new HydrologyCaveContainmentPlanner.ValidationCache();
        }
    }

    private record GridOffset(int x, int z) {
    }

    private record MinimumLandHeightKey(int x, int z, int radius) {
    }

    private record UndergroundSegmentCapKey(
            long courseId,
            String profileKey,
            int startX,
            int startZ,
            int endX,
            int endZ,
            int radius,
            int minimumHeadroom,
            int maximumHeadroom
    ) {
    }

    private record RefinedEdgeKey(
            long upstreamId,
            long downstreamId,
            int upstreamX,
            int upstreamZ,
            int downstreamX,
            int downstreamZ,
            int continuationX,
            int continuationZ,
            int transverseCandidates
    ) {
    }

    private record Direction(double x, double z) {
    }

    private record SurfaceCurvePoint(double x, double z, double distance) {
    }

    private record RoutePosition(double x, double z, double baseX, double baseZ, Direction tangent) {
    }

    private record SurfaceRouteKey(List<HydrologyPoint> guide, long geometryId) {
    }

    private record RouteCandidate(
            HydrologyPoint point,
            double offset,
            double localScore,
            double terrainScore,
            Direction tangent,
            boolean terrainResolved
    ) {
    }

    private record RouteTerrainEstimate(int height, double terrainScore) {
    }

    private record CurvatureRouteSelection(RouteCandidate[] route, int[] indices) {
        private static CurvatureRouteSelection empty() {
            return new CurvatureRouteSelection(new RouteCandidate[0], new int[0]);
        }
    }

    private record TerrainTransitionKey(HydrologyPoint start, HydrologyPoint end) {
    }

    private record GridNode(
            int index,
            int gridX,
            int gridZ,
            int x,
            int z,
            long id,
            HydrologyTerrainSample terrain
    ) {
        HydrologyPoint naturalPoint() {
            return new HydrologyPoint(x, terrain.naturalHeight(), z);
        }
    }

    private record SampledGrid(
            int minimumX,
            int minimumZ,
            int ownerMinimumX,
            int ownerMinimumZ,
            int ownerSize,
            int width,
            int spacing,
            List<GridNode> nodes
    ) {
        GridNode node(int index) {
            return nodes.get(index);
        }

        GridNode nodeAt(int gridX, int gridZ) {
            if (gridX < 0 || gridZ < 0 || gridX >= width || gridZ >= width) {
                return null;
            }
            return nodes.get(gridZ * width + gridX);
        }

        GridNode nodeAtWorld(int x, int z) {
            long deltaX = (long) x - minimumX;
            long deltaZ = (long) z - minimumZ;
            if (deltaX < 0L || deltaZ < 0L || deltaX % spacing != 0L || deltaZ % spacing != 0L) {
                return null;
            }
            long gridX = deltaX / spacing;
            long gridZ = deltaZ / spacing;
            if (gridX >= width || gridZ >= width) {
                return null;
            }
            return nodeAt((int) gridX, (int) gridZ);
        }

        boolean owns(int x, int z) {
            return x >= ownerMinimumX && x < ownerMinimumX + ownerSize
                    && z >= ownerMinimumZ && z < ownerMinimumZ + ownerSize;
        }
    }

    private record OutletCandidate(int landIndex, int oceanIndex, RiverOutlet outlet) {
    }

    private record InlandConnection(HydrologyPoint point, HydrologyTerrainSample terrain) {
    }

    private record RoutingQueueEntry(
            int nodeIndex,
            double potential,
            int outletIndex
    )
            implements Comparable<RoutingQueueEntry> {
        @Override
        public int compareTo(RoutingQueueEntry other) {
            int potentialComparison = Double.compare(potential, other.potential);
            if (potentialComparison != 0) {
                return potentialComparison;
            }
            int nodeComparison = Integer.compare(nodeIndex, other.nodeIndex);
            return nodeComparison != 0 ? nodeComparison : Integer.compare(outletIndex, other.outletIndex);
        }
    }

    private record RoutingPlan(
            double[] potential,
            int[] parent,
            int[] outletIndex,
            int[] routeLengths,
            List<OutletCandidate> outlets,
            boolean organicSurfaceRequired
    ) {
    }

    private record SourceCandidate(
            int nodeIndex,
            long stableId,
            double score,
            boolean required
    ) {
    }

    private static final class SourceSelection {
        private final boolean surface;
        private final List<SourceCandidate> candidates;
        private final SourceAdmissionSelection admission;
        private final int guaranteedMinimum;
        private final int maximumOptionalRejections;
        private final int targetCount;
        private final ArrayList<Integer> selectedCandidateIndices;
        private final boolean[] selectedCandidates;
        private final boolean[] attemptedCandidates;
        private int rejectedOptionalCandidates;

        private SourceSelection(
                boolean surface,
                List<SourceCandidate> candidates,
                SourceAdmissionSelection admission,
                int guaranteedMinimum,
                int maximumOptionalRejections
        ) {
            if (guaranteedMinimum < 0 || guaranteedMinimum > admission.selectedCandidateIndices().size()) {
                throw new IllegalArgumentException("Guaranteed source minimum is outside the admission bounds.");
            }
            if (maximumOptionalRejections < 0) {
                throw new IllegalArgumentException("Maximum optional source rejections cannot be negative.");
            }
            this.surface = surface;
            this.candidates = List.copyOf(candidates);
            this.admission = admission;
            this.guaranteedMinimum = guaranteedMinimum;
            this.maximumOptionalRejections = maximumOptionalRejections;
            this.selectedCandidateIndices = new ArrayList<>(admission.selectedCandidateIndices());
            this.targetCount = admission.targetCount();
            this.selectedCandidates = new boolean[candidates.size()];
            this.attemptedCandidates = new boolean[candidates.size()];
            for (int candidateIndex : selectedCandidateIndices) {
                selectedCandidates[candidateIndex] = true;
            }
        }

        private static SourceSelection empty(boolean surface) {
            return new SourceSelection(
                    surface,
                    List.of(),
                    new SourceAdmissionSelection(
                            0,
                            List.of(),
                            new boolean[0],
                            new boolean[0],
                            new boolean[0],
                            (int candidateIndex) -> false,
                            new int[0],
                            Integer.MAX_VALUE
                    ),
                    0,
                    0
            );
        }

        private List<Integer> selectedNodeIndices() {
            ArrayList<Integer> selectedNodes = new ArrayList<>(selectedCandidateIndices.size());
            for (int candidateIndex : selectedCandidateIndices) {
                selectedNodes.add(candidates.get(candidateIndex).nodeIndex());
            }
            return List.copyOf(selectedNodes);
        }

        private int candidateCount() {
            return candidates.size();
        }

        private boolean needsSurfaceFallback() {
            return surface && targetCount > 0 && selectedCandidateIndices.isEmpty();
        }

        private boolean hasAcceptedSelection() {
            return !selectedCandidateIndices.isEmpty();
        }

        private boolean advanceAfterPublication(List<RiverCourse> acceptedCourses, SampledGrid grid) {
            Set<Long> acceptedSourceNodeIds = acceptedSourceNodeIds(acceptedCourses);
            int acceptedRequired = 0;
            boolean changed = false;
            int selectedPosition = 0;
            while (selectedPosition < selectedCandidateIndices.size()) {
                int candidateIndex = selectedCandidateIndices.get(selectedPosition);
                SourceCandidate candidate = candidates.get(candidateIndex);
                long sourceNodeId = grid.node(candidate.nodeIndex()).id();
                if (acceptedSourceNodeIds.contains(sourceNodeId)) {
                    if (candidate.required()) {
                        attemptedCandidates[candidateIndex] = true;
                        acceptedRequired++;
                    }
                    selectedPosition++;
                    continue;
                }
                attemptedCandidates[candidateIndex] = true;
                if (!candidate.required()) {
                    rejectedOptionalCandidates++;
                }
                selectedCandidateIndices.remove(selectedPosition);
                selectedCandidates[candidateIndex] = false;
                changed = true;
            }
            int requiredVacancies = Math.max(0, guaranteedMinimum - acceptedRequired);
            for (int vacancy = 0; vacancy < requiredVacancies; vacancy++) {
                int replacement = nextRequiredCandidate();
                if (replacement < 0) {
                    break;
                }
                selectedCandidateIndices.add(replacement);
                selectedCandidates[replacement] = true;
                changed = true;
            }
            while (selectedCandidateIndices.size() < targetCount) {
                int replacement = nextAdmittedCandidate();
                if (replacement < 0) {
                    break;
                }
                selectedCandidateIndices.add(replacement);
                selectedCandidates[replacement] = true;
                changed = true;
            }
            return changed;
        }

        private Set<Long> acceptedSourceNodeIds(List<RiverCourse> acceptedCourses) {
            RiverCourseType expectedType = surface ? RiverCourseType.SURFACE : RiverCourseType.UNDERGROUND;
            HashSet<Long> accepted = new HashSet<>();
            for (RiverCourse course : acceptedCourses) {
                if (course.type() == expectedType && course.sourceNodeId().isPresent()) {
                    accepted.add(course.sourceNodeId().getAsLong());
                }
            }
            return accepted;
        }

        private int nextRequiredCandidate() {
            for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
                SourceCandidate candidate = candidates.get(candidateIndex);
                if (candidate.required()
                        && !selectedCandidates[candidateIndex]
                        && !attemptedCandidates[candidateIndex]
                        && admission.outletQuotaAvailable(candidateIndex, selectedCandidateIndices)) {
                    return candidateIndex;
                }
            }
            return -1;
        }

        private int nextAdmittedCandidate() {
            if (rejectedOptionalCandidates >= maximumOptionalRejections) {
                return -1;
            }
            for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
                if (!selectedCandidates[candidateIndex]
                        && !attemptedCandidates[candidateIndex]
                        && admission.outletQuotaAvailable(candidateIndex, selectedCandidateIndices)
                        && admission.admitted(candidateIndex)) {
                    return candidateIndex;
                }
            }
            return -1;
        }

        private void addFinalAdmissionDiagnostics(
                SampledGrid grid,
                List<HydrologyDiagnosticCandidate> diagnostics
        ) {
            for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
                if (selectedCandidates[candidateIndex] || attemptedCandidates[candidateIndex]) {
                    continue;
                }
                SourceCandidate candidate = candidates.get(candidateIndex);
                addSourceDiagnostic(
                        grid.node(candidate.nodeIndex()),
                        surface,
                        candidate.stableId(),
                        admission.rejectedBySpacing(candidateIndex)
                                ? HydrologyCandidateRejection.SOURCE_SPACING
                                : HydrologyCandidateRejection.SOURCE_QUOTA,
                        diagnostics
                );
            }
        }
    }

    static final class SourceAdmissionSelection {
        private final int targetCount;
        private final List<Integer> selectedCandidateIndices;
        private final boolean[] selectedCandidates;
        private final boolean[] spacingRejectedCandidates;
        private final boolean[] evaluatedCandidates;
        private final IntPredicate globallyAdmitted;
        private final int[] outletIndices;
        private final int maximumCoursesPerOutlet;

        private SourceAdmissionSelection(
                int targetCount,
                List<Integer> selectedCandidateIndices,
                boolean[] selectedCandidates,
                boolean[] spacingRejectedCandidates,
                boolean[] evaluatedCandidates,
                IntPredicate globallyAdmitted,
                int[] outletIndices,
                int maximumCoursesPerOutlet
        ) {
            if (outletIndices.length != selectedCandidates.length || maximumCoursesPerOutlet < 1) {
                throw new IllegalArgumentException("Source outlet admission bounds are invalid.");
            }
            this.targetCount = targetCount;
            this.selectedCandidateIndices = List.copyOf(selectedCandidateIndices);
            this.selectedCandidates = selectedCandidates;
            this.spacingRejectedCandidates = spacingRejectedCandidates;
            this.evaluatedCandidates = evaluatedCandidates;
            this.globallyAdmitted = globallyAdmitted;
            this.outletIndices = outletIndices;
            this.maximumCoursesPerOutlet = maximumCoursesPerOutlet;
        }

        List<Integer> selectedCandidateIndices() {
            return selectedCandidateIndices;
        }

        int targetCount() {
            return targetCount;
        }

        boolean selected(int candidateIndex) {
            return selectedCandidates[candidateIndex];
        }

        boolean rejectedBySpacing(int candidateIndex) {
            return spacingRejectedCandidates[candidateIndex];
        }

        boolean admitted(int candidateIndex) {
            if (!evaluatedCandidates[candidateIndex]) {
                evaluatedCandidates[candidateIndex] = true;
                spacingRejectedCandidates[candidateIndex] = !globallyAdmitted.test(candidateIndex);
            }
            return !spacingRejectedCandidates[candidateIndex];
        }

        boolean outletQuotaAvailable(int candidateIndex, List<Integer> selectedIndices) {
            int outletIndex = outletIndices[candidateIndex];
            int selectedForOutlet = 0;
            for (int selectedIndex : selectedIndices) {
                if (outletIndices[selectedIndex] == outletIndex) {
                    selectedForOutlet++;
                }
            }
            return selectedForOutlet < maximumCoursesPerOutlet;
        }
    }

    private record SourcePriority(
            boolean required,
            double weight,
            int naturalHeight,
            long stableId,
            int x,
            int z
    ) {
    }

    private record RoutingContextLoad(Thread owner, CompletableFuture<SourceRoutingContext> future) {
    }

    private record SourceRoutingContext(
            SampledGrid grid,
            RoutingPlan surfaceRouting,
            RoutingPlan undergroundRouting,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        private RoutingPlan routing(boolean surface) {
            return surface ? surfaceRouting : undergroundRouting;
        }
    }

    private record SourceCompilationKey(
            boolean surface,
            RoutingPlan routing,
            List<Integer> sources
    ) {
        private SourceCompilationKey {
            Objects.requireNonNull(routing, "routing");
            sources = List.copyOf(sources);
        }
    }

    private record SourceCompilation(
            CompiledGraph graph,
            List<RiverCourse> courses,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
    }

    private record CompiledGraph(
            List<DrainageNode> nodes,
            List<DrainageEdge> edges,
            List<RiverOutlet> outlets,
            Map<Integer, DrainageEdge> edgeByUpstream
    ) {
    }

    private record PublicationAttempt(
            HydrologyCaveCourseFilter.Result result,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
    }

    private record CrossTilePublicationAdmission(
            HydrologyCaveCourseFilter.Result result,
            List<HydrologyDiagnosticCandidate> diagnostics,
            boolean rejectedCourses
    ) {
        private CrossTilePublicationAdmission {
            Objects.requireNonNull(result, "result");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }

    private record HydrologyOwnerDraft(
            HydrologyTileKey key,
            HydrologyCaveCourseFilter.Result result,
            List<HydrologyDiagnosticCandidate> diagnostics,
            HydrologyFootprintCompiler footprintCompiler
    ) {
        private HydrologyOwnerDraft withoutFootprintCompiler() {
            if (footprintCompiler == null) {
                return this;
            }
            return new HydrologyOwnerDraft(key, result, diagnostics, null);
        }
    }

    private record MaterializedHydrology(
            HydrologyCaveCourseFilter.Result result,
            RiverFootprint footprint
    ) {
        private MaterializedHydrology {
            Objects.requireNonNull(result);
            Objects.requireNonNull(footprint);
        }
    }

    private interface CrossTileDraftAdmission {
        void prepare();

        CrossTilePublicationAdmission admit(HydrologyCaveCourseFilter.Result result);
    }

    private final class ColorRankedDraftAdmission implements CrossTileDraftAdmission, AutoCloseable {
        private final HydrologyTileKey ownerKey;
        private final int ownerRank;
        private final CrossTileResolutionContext context;
        private final Set<Long> rejectedCourseIds;
        private final Map<HydrologyTileKey, HydrologyForkJoin.Task<CrossTileResolvedOwner>> preparedOwners = new LinkedHashMap<>();
        private final Set<HydrologyTileKey> demandedOwners = new HashSet<>();
        private Throwable primaryFailure;
        private final LinkedHashMap<CrossTileRejectionKey, CrossTileRejectedCourse> observedRejections;

        private ColorRankedDraftAdmission(
                HydrologyTileKey ownerKey,
                int ownerRank,
                CrossTileResolutionContext context
        ) {
            this.ownerKey = Objects.requireNonNull(ownerKey, "ownerKey");
            this.ownerRank = ownerRank;
            this.context = Objects.requireNonNull(context, "context");
            this.rejectedCourseIds = new HashSet<>();
            this.observedRejections = new LinkedHashMap<>();
        }

        @Override
        public void prepare() {
            if (ownerRank == 0 || settings.crossTileColorPeriod() != 2 || !hasRoutedSourceSearch()
                    || !(Thread.currentThread() instanceof ForkJoinWorkerThread worker)) {
                return;
            }
            int radius = settings.crossTileColorPeriod() - 1;
            ArrayList<HydrologyTileKey> candidates = new ArrayList<>();
            for (long tileZ = (long) ownerKey.tileZ() - radius; tileZ <= (long) ownerKey.tileZ() + radius; tileZ++) {
                for (long tileX = (long) ownerKey.tileX() - radius; tileX <= (long) ownerKey.tileX() + radius; tileX++) {
                    if (tileX < Integer.MIN_VALUE || tileX > Integer.MAX_VALUE
                            || tileZ < Integer.MIN_VALUE || tileZ > Integer.MAX_VALUE) {
                        continue;
                    }
                    HydrologyTileKey key = new HydrologyTileKey((int) tileX, (int) tileZ);
                    if (ownerColorRank(key) >= ownerRank || preparedOwners.containsKey(key)
                            || resolvedOwners.getIfPresent(key) != null || resolvingOwners.containsKey(key)) {
                        continue;
                    }
                    candidates.add(key);
                }
            }
            candidates.sort(Comparator.comparingInt(HydrologyPlanner.this::ownerColorRank).reversed()
                    .thenComparing(HydrologyTileKey::compareTo));
            for (HydrologyTileKey key : candidates) {
                if (resolvedOwners.getIfPresent(key) != null || resolvingOwners.containsKey(key)) {
                    continue;
                }
                HydrologyForkJoin.Task<CrossTileResolvedOwner> task = new HydrologyForkJoin.Task<>(() -> resolveIndependentOwner(key));
                preparedOwners.put(key, task);
                try {
                    worker.getPool().execute(task);
                } catch (RuntimeException | Error failure) {
                    preparedOwners.remove(key);
                    throw failure;
                }
                currentDraftProfile().earlyOwners++;
            }
        }

        @Override
        public void close() {
            Error failure = null;
            Map<HydrologyTileKey, RuntimeException> unusedFailures = new LinkedHashMap<>();
            for (Map.Entry<HydrologyTileKey, HydrologyForkJoin.Task<CrossTileResolvedOwner>> entry : preparedOwners.entrySet()) {
                try {
                    entry.getValue().await();
                } catch (RuntimeException unusedFailure) {
                    if (!demandedOwners.contains(entry.getKey())) {
                        unusedFailures.put(entry.getKey(), unusedFailure);
                    }
                } catch (Error failed) {
                    if (failed == primaryFailure) {
                        continue;
                    }
                    if (failure == null) {
                        failure = failed;
                    } else if (failure != failed) {
                        failure.addSuppressed(failed);
                    }
                }
            }
            for (Map.Entry<HydrologyTileKey, RuntimeException> entry : unusedFailures.entrySet()) {
                IrisLogging.reportError("Unused early hydrology owner " + entry.getKey().tileX()
                        + "," + entry.getKey().tileZ() + " failed to prepare.", entry.getValue());
            }
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public CrossTilePublicationAdmission admit(HydrologyCaveCourseFilter.Result result) {
            context.recordIteration();
            HydrologyCaveCourseFilter.Result current = HydrologyCaveCourseFilter.withoutCourses(
                    result,
                    rejectedCourseIds
            );
            demandedOwners.addAll(conflictOwnerKeys(ownerKey, current));
            CrossTileBlockers blockers = lowerRankBlockers(
                    ownerKey,
                    current,
                    ownerRank,
                    context,
                    preparedOwners
            );
            HydrologyCrossTileSurfaceAdmission.Result surfaceAdmission =
                    HydrologyCrossTileSurfaceAdmission.admit(
                            surfaceClaims(current),
                            blockers.surfaceClaims(),
                            settings.surface().sources().minimumSpacing()
                    );
            Map<Long, RiverCourse> currentCourses = coursesById(current.courses());
            HashSet<Long> rejectedSurfaceOutlets = new HashSet<>();
            HashMap<Long, Long> surfaceWinners = new HashMap<>();
            for (HydrologyCrossTileSurfaceAdmission.Rejection rejection : surfaceAdmission.rejections()) {
                RiverCourse course = currentCourses.get(rejection.loser().courseId());
                if (course == null || course.outletId().isEmpty()) {
                    throw new IllegalStateException("Cross-tile surface admission rejected an unknown current course.");
                }
                long outletId = course.outletId().getAsLong();
                rejectedSurfaceOutlets.add(outletId);
                surfaceWinners.putIfAbsent(outletId, rejection.winnerCourseId());
            }
            HashSet<Long> newRejectedCourseIds = new HashSet<>();
            ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();
            for (RiverCourse course : current.courses()) {
                if (course.outletId().isEmpty()
                        || !rejectedSurfaceOutlets.contains(course.outletId().getAsLong())) {
                    continue;
                }
                long winnerCourseId = surfaceWinners.get(course.outletId().getAsLong());
                rejectCrossTileCourse(course, winnerCourseId, newRejectedCourseIds, diagnostics);
            }
            HydrologyCaveCourseFilter.Result surfaceAccepted = HydrologyCaveCourseFilter.withoutCourses(
                    current,
                    newRejectedCourseIds
            );
            HydrologyCrossTileCaveAdmission.Result caveAdmission = HydrologyCrossTileCaveAdmission.admit(
                    ownerRank,
                    caveClaims(surfaceAccepted),
                    blockers.caveClaims()
            );
            if (newRejectedCourseIds.isEmpty() && caveAdmission.rejections().isEmpty()) {
                return new CrossTilePublicationAdmission(current, List.of(), false);
            }
            Map<Long, RiverCourse> surfaceAcceptedCourses = coursesById(surfaceAccepted.courses());
            for (HydrologyCrossTileCaveAdmission.Rejection rejection : caveAdmission.rejections()) {
                HydrologyCrossTileCaveAdmission.Claim claim = rejection.loser();
                RiverCourse course = surfaceAcceptedCourses.get(claim.courseId());
                if (course == null) {
                    throw new IllegalStateException("Cross-tile cave admission rejected an unknown current course.");
                }
                rejectCrossTileCourse(course, rejection.winnerSourceId(), newRejectedCourseIds, diagnostics);
            }
            return new CrossTilePublicationAdmission(
                    HydrologyCaveCourseFilter.withoutCourses(current, newRejectedCourseIds),
                    diagnostics,
                    true
            );
        }

        private void rejectCrossTileCourse(
                RiverCourse course,
                long winnerCourseId,
                Set<Long> newRejectedCourseIds,
                List<HydrologyDiagnosticCandidate> diagnostics
        ) {
            if (!rejectedCourseIds.add(course.id())) {
                throw new IllegalStateException("Cross-tile admission repeated a rejected course.");
            }
            newRejectedCourseIds.add(course.id());
            CrossTileRejectionKey rejectionKey = new CrossTileRejectionKey(course.id(), winnerCourseId);
            if (observedRejections.containsKey(rejectionKey)) {
                return;
            }
            observedRejections.put(
                    rejectionKey,
                    new CrossTileRejectedCourse(course, winnerCourseId)
            );
            diagnostics.add(course.type() == RiverCourseType.SURFACE
                    ? surfaceConflictDiagnostic(course, winnerCourseId)
                    : HydrologyCaveCourseFilter.overlapDiagnostic(course, winnerCourseId));
        }

        private List<CrossTileRejectedCourse> observedRejections() {
            return List.copyOf(observedRejections.values());
        }
    }

    private HydrologyDiagnosticCandidate surfaceConflictDiagnostic(
            RiverCourse course,
            long winnerCourseId
    ) {
        HydraulicSegment representative = course.segments().getFirst();
        return new HydrologyDiagnosticCandidate(
                HydrologyHash.mix(course.id(), DIAGNOSTIC_SALT, winnerCourseId),
                HydrologyCandidateKind.SOURCE,
                HydrologyFeatureType.SURFACE_POOL,
                representative.start(),
                HydrologyCandidateRejection.SOURCE_SPACING, 0
        );
    }

    private enum DraftPhase {
        CONTEXT,
        SELECT,
        SETTLE,
        PUBLISH
    }

    /**
     * Wall-clock breakdown of one owner draft for the debug log: sampling and routing context, source
     * selection, settling, publication passes, and the time spent waiting on lower-rank neighbour drafts.
     */
    private static final class DraftProfile {
        private final long[] phaseNanos = new long[DraftPhase.values().length];
        private long dependencyNanos;
        private int dependencies;
        private int admissions;
        private int earlyOwners;
        private int routeCalls;
        private int routeSolves;
        private long routeSolveNanos;
        private int rasterCalls;
        private long rasterNanos;
        private int filterCalls;
        private long filterNanos;

        private long record(DraftPhase phase, long started) {
            long now = System.nanoTime();
            phaseNanos[phase.ordinal()] += now - started;
            return now;
        }

        private void recordDependencies(int count, long started) {
            dependencyNanos += System.nanoTime() - started;
            dependencies += count;
            admissions++;
        }

        private void log(HydrologyTileKey key, int rank, long totalNanos) {
            IrisLogging.debug(
                    "Hydrology owner %d,%d rank=%d drafted in %dms: context=%dms select=%dms settle=%dms publish=%dms deps=%d wait=%dms admissions=%d earlyOwners=%d routes=%d reuses=%d routeSolve=%dms rasters=%d raster=%dms filters=%d filter=%dms on %s",
                    key.tileX(),
                    key.tileZ(),
                    rank,
                    totalNanos / 1_000_000L,
                    phaseNanos[DraftPhase.CONTEXT.ordinal()] / 1_000_000L,
                    phaseNanos[DraftPhase.SELECT.ordinal()] / 1_000_000L,
                    phaseNanos[DraftPhase.SETTLE.ordinal()] / 1_000_000L,
                    phaseNanos[DraftPhase.PUBLISH.ordinal()] / 1_000_000L,
                    dependencies,
                    dependencyNanos / 1_000_000L,
                    admissions,
                    earlyOwners,
                    routeCalls,
                    routeCalls - routeSolves,
                    routeSolveNanos / 1_000_000L,
                    rasterCalls,
                    rasterNanos / 1_000_000L,
                    filterCalls,
                    filterNanos / 1_000_000L,
                    Thread.currentThread().getName()
            );
        }
    }

    private DraftProfile currentDraftProfile() {
        DraftProfile profile = draftProfiles.get();
        return profile == null ? new DraftProfile() : profile;
    }

    private static final class CrossTileResolutionContext {
        private final Map<HydrologyTileKey, CrossTileResolvedOwner> resolved;
        private final Set<HydrologyTileKey> resolving;
        private final HydrologyTileKey root;
        private final long maximumTileOffset;
        private final int maximumOwners;
        private int iterations;

        private CrossTileResolutionContext(
                HydrologyTileKey root,
                long maximumTileOffset,
                int maximumOwners
        ) {
            this.resolved = new HashMap<>();
            this.resolving = new HashSet<>();
            this.root = Objects.requireNonNull(root, "root");
            this.maximumTileOffset = maximumTileOffset;
            this.maximumOwners = maximumOwners;
        }

        private CrossTileResolvedOwner resolved(HydrologyTileKey key) {
            return resolved.get(key);
        }

        private void remember(HydrologyTileKey key, CrossTileResolvedOwner owner) {
            long offsetX = Math.abs((long) key.tileX() - root.tileX());
            long offsetZ = Math.abs((long) key.tileZ() - root.tileZ());
            if (offsetX > maximumTileOffset || offsetZ > maximumTileOffset) {
                throw new IllegalStateException("Cross-tile owner dependency exceeded its color-ranked geometry bound.");
            }
            CrossTileResolvedOwner existing = resolved.putIfAbsent(key, owner);
            if (existing != null
                    && !existing.withoutFootprintCompiler().equals(owner.withoutFootprintCompiler())) {
                throw new IllegalStateException("Cross-tile owner resolution produced inconsistent cached results.");
            }
            if (resolved.size() > maximumOwners) {
                throw new IllegalStateException("Cross-tile owner dependencies exceeded their color-ranked count bound.");
            }
        }

        private boolean begin(HydrologyTileKey key) {
            return resolving.add(key);
        }

        private void end(HydrologyTileKey key) {
            resolving.remove(key);
        }

        private void recordIteration() {
            iterations++;
        }

        private int iterations() {
            return iterations;
        }

        private int ownerCount() {
            return resolved.size();
        }

        private HydrologyTileKey root() {
            return root;
        }
    }

    private record CrossTileResolvedOwner(
            HydrologyOwnerDraft draft,
            List<CrossTileRejectedCourse> observedRejections
    ) {
        private CrossTileResolvedOwner {
            Objects.requireNonNull(draft, "draft");
            observedRejections = List.copyOf(Objects.requireNonNull(observedRejections, "observedRejections"));
        }

        private CrossTileResolvedOwner withoutFootprintCompiler() {
            HydrologyOwnerDraft uncachedDraft = draft.withoutFootprintCompiler();
            return uncachedDraft == draft
                    ? this
                    : new CrossTileResolvedOwner(uncachedDraft, observedRejections);
        }
    }

    private record CrossTileRejectionKey(long courseId, long winnerCourseId) {
    }

    private record CrossTileBlockers(
            List<HydrologyCrossTileCaveAdmission.RankedClaim> caveClaims,
            List<HydrologyCrossTileSurfaceAdmission.RankedClaim> surfaceClaims
    ) {
        private CrossTileBlockers {
            caveClaims = List.copyOf(Objects.requireNonNull(caveClaims, "caveClaims"));
            surfaceClaims = List.copyOf(Objects.requireNonNull(surfaceClaims, "surfaceClaims"));
        }
    }

    private record CrossTileRejectedCourse(RiverCourse course, long winnerSourceId) {
        private CrossTileRejectedCourse {
            Objects.requireNonNull(course, "course");
        }
    }

    private record CrossTileResolution(
            HydrologyOwnerDraft draft,
            List<CrossTileRejectedCourse> observedRejections,
            int iterations,
            int ownerCount,
            long resolutionNanos
    ) {
        private CrossTileResolution {
            Objects.requireNonNull(draft, "draft");
            observedRejections = List.copyOf(Objects.requireNonNull(observedRejections, "observedRejections"));
        }
    }

    /**
     * A routed source-to-outlet path, or, when {@code junction} is set, the cut prefix of one that ends
     * on the stem it joins: the path then owns only the drainage edges upstream of the junction.
     */
    private record CoursePath(
            List<HydrologyPoint> points,
            List<DrainageEdge> pairEdges,
            List<DrainageEdge> edges,
            RiverOutlet outlet,
            boolean reachesOutlet,
            boolean organicSurfaceRequired,
            HydrologyPoint junction
    ) {
    }

    private record SurfaceCourseDraft(
            GridNode source,
            long courseId,
            String profileKey,
            CoursePath path
    ) {
    }

    private record SurfaceCourseBuild(
            RiverCourse course,
            HydrologyCandidateRejection rejection,
            int rejectionDetail
    ) {
        private SurfaceCourseBuild {
            if ((course == null) == (rejection == null)) {
                throw new IllegalArgumentException("Surface course builds require exactly one result.");
            }
        }

        private static SurfaceCourseBuild accepted(RiverCourse course) {
            return new SurfaceCourseBuild(Objects.requireNonNull(course, "course"), null, 0);
        }

        private static SurfaceCourseBuild rejected(HydrologyCandidateRejection rejection, int detail) {
            return new SurfaceCourseBuild(null, Objects.requireNonNull(rejection, "rejection"), detail);
        }
    }

    private record OutletCourseKey(long outletId) {
    }

    private record CourseEdgeKey(RiverCourseType type, long edgeId) {
        private CourseEdgeKey {
            Objects.requireNonNull(type, "type");
        }
    }

    private record DeepSite(int x, int z, int head, long stableId) {
    }
}
