package art.arcane.iris.generation.hydrology.cave;

import art.arcane.iris.generation.hydrology.HydrologyObservedPlannedSurface;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.BiPredicate;

public final class HydrologyCaveContainmentPlanner {
    static final int MAXIMUM_INITIAL_GUARD_INDEX_SIZE = 16_384;

    static final List<CavePosition> DIRECTIONS = List.of(
            new CavePosition(1, 0, 0),
            new CavePosition(-1, 0, 0),
            new CavePosition(0, 1, 0),
            new CavePosition(0, -1, 0),
            new CavePosition(0, 0, 1),
            new CavePosition(0, 0, -1)
    );

    static final Comparator<HydrologyCaveSource> SOURCE_PRIORITY =
            HydrologyCaveConflictPolicy.sourcePriority();
    final HydrologyCaveChamberPlanner chambers;

    public HydrologyCaveContainmentPlanner() {
        this.chambers = new HydrologyCaveChamberPlanner(this);
    }

    public HydrologyCavePlan plan(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings
    ) {
        Objects.requireNonNull(view);
        Objects.requireNonNull(source);
        Objects.requireNonNull(settings);

        HydrologyCaveRejection sourceRejection = validateSource(source);
        if (sourceRejection != HydrologyCaveRejection.NONE) {
            return rejected(source, sourceRejection);
        }

        CavePathResult throat = chambers.buildThroat(view, source, settings);
        if (throat.rejection() != HydrologyCaveRejection.NONE) {
            return rejected(source, throat.rejection());
        }

        return switch (source.mode()) {
            case CLOSED_COMPONENT -> chambers.planClosedComponent(view, source, settings, throat.positions());
            case GENERATED_GROTTO -> chambers.planGeneratedGrotto(view, source, settings, throat.positions());
            case GROTTO_OR_CLOSED_COMPONENT -> chambers.planGrottoOrClosedComponent(
                    view,
                    source,
                    settings,
                    throat.positions()
            );
            case WATERFALL_POOL -> chambers.planWaterfallPool(
                    view,
                    source,
                    settings,
                    throat.positions()
            );
            case DEEP_POOL -> chambers.planDeepPool(view, source, settings, throat.positions());
        };
    }

    public HydrologyCavePlanningResult planAll(
            CaveVoxelView view,
            Collection<HydrologyCaveSource> sources,
            HydrologyCavePlannerSettings settings
    ) {
        Objects.requireNonNull(view);
        Objects.requireNonNull(sources);
        Objects.requireNonNull(settings);

        List<HydrologyCaveSource> orderedSources = new ArrayList<>(sources);
        orderedSources.sort(SOURCE_PRIORITY);
        List<HydrologyCavePlan> plans = new ArrayList<>(orderedSources.size());
        Map<CavePosition, HydrologyCaveAction> combinedActions = new LinkedHashMap<>();
        Map<CavePosition, HydrologyCaveSource> claimedBy = new HashMap<>();
        Map<CavePosition, CaveVoxelPrecondition> combinedPreconditions = new LinkedHashMap<>();

        for (HydrologyCaveSource source : orderedSources) {
            HydrologyCavePlan candidate = plan(view, source, settings);
            if (!candidate.accepted()) {
                plans.add(candidate);
                continue;
            }
            OptionalLong winnerSourceId = findWinningSourceId(
                    candidate.actions().keySet(),
                    claimedBy
            );
            if (winnerSourceId.isPresent()) {
                plans.add(rejectedOverlap(source, winnerSourceId.getAsLong()));
            } else {
                plans.add(candidate);
                combinedActions.putAll(candidate.actions());
                combinedPreconditions.putAll(candidate.baselinePreconditions());
            }
            for (CavePosition position : candidate.actions().keySet()) {
                claimedBy.putIfAbsent(position, source);
            }
        }

        return new HydrologyCavePlanningResult(plans, combinedActions, combinedPreconditions);
    }

    public HydrologyCavePlanningResult validateAll(
            CaveVoxelView view,
            Collection<HydrologyCaveCandidate> candidates
    ) {
        return validateAll(view, candidates, null);
    }

    public HydrologyCavePlanningResult validateAll(
            CaveVoxelView view,
            Collection<HydrologyCaveCandidate> candidates,
            ValidationCache validationCache
    ) {
        return validateAll(view, candidates, validationCache, null);
    }

    public HydrologyCavePlanningResult validateAll(
            CaveVoxelView view,
            Collection<HydrologyCaveCandidate> candidates,
            ValidationCache validationCache,
            HydrologyObservedPlannedSurface plannedSurface
    ) {
        CaveValidationBatch batch = validateBatch(
                view,
                candidates,
                validationCache,
                plannedSurface,
                Set.of(),
                (HydrologyCaveCandidate first, HydrologyCaveCandidate second) -> false,
                true
        );
        return new HydrologyCavePlanningResult(
                batch.plans(),
                batch.actions(),
                batch.baselinePreconditions()
        );
    }

    public List<HydrologyCavePlan> validateAllPlans(
            CaveVoxelView view,
            Collection<HydrologyCaveCandidate> candidates
    ) {
        return validateAllPlans(view, candidates, null, null);
    }

    public List<HydrologyCavePlan> validateAllPlans(
            CaveVoxelView view,
            Collection<HydrologyCaveCandidate> candidates,
            ValidationCache validationCache,
            HydrologyObservedPlannedSurface plannedSurface
    ) {
        return validateAllPlans(view, candidates, validationCache, plannedSurface, Set.of());
    }

    public List<HydrologyCavePlan> validateAllPlans(
            CaveVoxelView view,
            Collection<HydrologyCaveCandidate> candidates,
            ValidationCache validationCache,
            HydrologyObservedPlannedSurface plannedSurface,
            Set<HydrologyCaveCandidate> exposureValidatedCandidates
    ) {
        return validateAllPlans(
                view,
                candidates,
                validationCache,
                plannedSurface,
                exposureValidatedCandidates,
                (HydrologyCaveCandidate first, HydrologyCaveCandidate second) -> false
        );
    }

    public List<HydrologyCavePlan> validateAllPlans(
            CaveVoxelView view,
            Collection<HydrologyCaveCandidate> candidates,
            ValidationCache validationCache,
            HydrologyObservedPlannedSurface plannedSurface,
            Set<HydrologyCaveCandidate> exposureValidatedCandidates,
            BiPredicate<HydrologyCaveCandidate, HydrologyCaveCandidate> compatibleOverlap
    ) {
        Objects.requireNonNull(exposureValidatedCandidates);
        Objects.requireNonNull(compatibleOverlap);
        return validateBatch(
                view,
                candidates,
                validationCache,
                plannedSurface,
                exposureValidatedCandidates,
                compatibleOverlap,
                false
        ).plans();
    }

    CaveValidationBatch validateBatch(
            CaveVoxelView view,
            Collection<HydrologyCaveCandidate> candidates,
            ValidationCache validationCache,
            HydrologyObservedPlannedSurface plannedSurface,
            Set<HydrologyCaveCandidate> exposureValidatedCandidates,
            BiPredicate<HydrologyCaveCandidate, HydrologyCaveCandidate> compatibleOverlap,
            boolean aggregateResults
    ) {
        Objects.requireNonNull(view);
        Objects.requireNonNull(candidates);

        List<HydrologyCaveCandidate> orderedCandidates = new ArrayList<>(candidates);
        orderedCandidates.sort(Comparator.comparing(HydrologyCaveCandidate::source, SOURCE_PRIORITY));
        List<HydrologyCavePlan> plans = new ArrayList<>(orderedCandidates.size());
        Map<CavePosition, HydrologyCaveAction> combinedActions = new LinkedHashMap<>();
        Map<CavePosition, CaveVoxelPrecondition> combinedPreconditions = new LinkedHashMap<>();
        Map<CavePosition, CaveClaimGroup> claimedBy = null;

        for (HydrologyCaveCandidate candidate : orderedCandidates) {
            boolean exposureValidated = exposureValidatedCandidates.contains(candidate);
            HydrologyCavePlan plan = validationCache == null
                    ? validate(view, candidate, exposureValidated)
                    : validationCache.validate(this, view, candidate, plannedSurface, exposureValidated);
            if (!plan.accepted()) {
                plans.add(plan);
                continue;
            }
            OptionalLong winnerSourceId = claimedBy == null
                    ? OptionalLong.empty()
                    : findConflictingSourceId(candidate, plan.actions(), claimedBy, compatibleOverlap);
            if (winnerSourceId.isPresent()) {
                plans.add(rejectedOverlap(candidate.source(), winnerSourceId.getAsLong()));
                continue;
            }
            plans.add(plan);
            if (aggregateResults) {
                plan.forEachAction(combinedActions::put);
                plan.forEachPrecondition(combinedPreconditions::put);
            }
            if (claimedBy == null) {
                claimedBy = HashMap.newHashMap(plan.actions().size());
            }
            CaveClaimGroup claimGroup = new CaveClaimGroup(candidate, plan.actions());
            Map<CavePosition, CaveClaimGroup> claims = claimedBy;
            plan.forEachAction((CavePosition position, HydrologyCaveAction ignored) ->
                    claims.putIfAbsent(position, claimGroup));
        }

        return new CaveValidationBatch(
                List.copyOf(plans),
                combinedActions,
                combinedPreconditions
        );
    }

    public static final class ValidationCache {
        private static final int DEFAULT_MAXIMUM_ENTRIES = 256;
        private static final long DEFAULT_MAXIMUM_RETAINED_POSITIONS = 262_144L;

        private final IdentityHashMap<HydrologyCaveCandidate, CaveCachedValidation> validations;
        private final HashMap<HydrologyCaveCandidate, CaveCachedValidation> equivalentValidations;
        private final int maximumEntries;
        private final long maximumRetainedPositions;
        private long retainedPositions;
        private long hits;
        private long misses;

        public ValidationCache() {
            this(DEFAULT_MAXIMUM_ENTRIES, DEFAULT_MAXIMUM_RETAINED_POSITIONS);
        }

        ValidationCache(int maximumEntries, long maximumRetainedPositions) {
            if (maximumEntries < 1) {
                throw new IllegalArgumentException("Maximum validation cache entries must be positive.");
            }
            if (maximumRetainedPositions < 1L) {
                throw new IllegalArgumentException("Maximum retained validation positions must be positive.");
            }
            this.validations = new IdentityHashMap<>();
            this.equivalentValidations = new HashMap<>();
            this.maximumEntries = maximumEntries;
            this.maximumRetainedPositions = maximumRetainedPositions;
        }

        private HydrologyCavePlan validate(
                HydrologyCaveContainmentPlanner planner,
                CaveVoxelView view,
                HydrologyCaveCandidate candidate,
                HydrologyObservedPlannedSurface plannedSurface,
                boolean exposureValidated
        ) {
            CaveCachedValidation cached = validations.get(candidate);
            if (cached == null) {
                cached = equivalentValidations.get(candidate);
                if (cached != null) {
                    validations.put(candidate, cached);
                }
            }
            if (cached != null) {
                boolean matches = plannedSurface == null
                        ? cached.viewObservations().matches(view)
                        : cached.surfaceObservations().matches(plannedSurface);
                if (matches) {
                    hits++;
                    return cached.plan();
                }
                remove(cached);
            }
            misses++;
            boolean observeColumnsOnly = plannedSurface != null;
            RecordingCaveVoxelView recording = new RecordingCaveVoxelView(view, observeColumnsOnly);
            HydrologyCavePlan plan = planner.validate(recording, candidate, exposureValidated);
            CaveViewObservations viewObservations = recording.snapshot();
            CaveSurfaceObservations surfaceObservations = plannedSurface == null
                    ? CaveSurfaceObservations.empty()
                    : CaveSurfaceObservations.capture(viewObservations, plannedSurface);
            if (observeColumnsOnly) {
                viewObservations = CaveViewObservations.empty();
            }
            CaveCachedValidation validation = new CaveCachedValidation(plan, viewObservations, surfaceObservations);
            retain(candidate, validation);
            return plan;
        }

        public long hits() {
            return hits;
        }

        public void clear() {
            clearEntries();
            hits = 0L;
            misses = 0L;
        }

        private void retain(HydrologyCaveCandidate candidate, CaveCachedValidation validation) {
            long weight = validation.retainedPositions();
            if (weight > maximumRetainedPositions) {
                return;
            }
            if (equivalentValidations.size() >= maximumEntries
                    || retainedPositions + weight > maximumRetainedPositions) {
                clearEntries();
            }
            validations.put(candidate, validation);
            equivalentValidations.put(candidate, validation);
            retainedPositions += weight;
        }

        private void remove(CaveCachedValidation cached) {
            validations.entrySet().removeIf(
                    (Map.Entry<HydrologyCaveCandidate, CaveCachedValidation> entry) -> entry.getValue() == cached
            );
            equivalentValidations.entrySet().removeIf(
                    (Map.Entry<HydrologyCaveCandidate, CaveCachedValidation> entry) -> entry.getValue() == cached
            );
            retainedPositions -= cached.retainedPositions();
        }

        private void clearEntries() {
            validations.clear();
            equivalentValidations.clear();
            retainedPositions = 0L;
        }

        public long misses() {
            return misses;
        }
    }

    public HydrologyCavePlan validate(CaveVoxelView view, HydrologyCaveCandidate candidate) {
        return validate(view, candidate, false);
    }

    HydrologyCavePlan validate(
            CaveVoxelView view,
            HydrologyCaveCandidate candidate,
            boolean exposureValidated
    ) {
        Objects.requireNonNull(view);
        Objects.requireNonNull(candidate);
        HydrologyCaveSource source = candidate.source();
        HydrologyCavePlannerSettings settings = candidate.settings();
        HydrologyCaveRejection sourceRejection = validateSource(source);
        if (sourceRejection != HydrologyCaveRejection.NONE) {
            return rejected(source, sourceRejection);
        }
        if (exceedsFloodVolume(candidate.actions(), settings.maxFloodVolume())) {
            return rejected(source, HydrologyCaveRejection.VOLUME_LIMIT);
        }
        CavePositionIndex actionIndex = CavePositionIndex.copyOf(candidate.actions().keySet());
        CavePositionIndex openingIndex = CavePositionIndex.copyOf(candidate.intentionalOpenings());
        if (!exposureValidated
                && isPlannedVolumeAboveTerrainSurface(view, candidate, actionIndex, openingIndex)) {
            return rejected(source, HydrologyCaveRejection.OPEN_SURFACE);
        }

        Map<CavePosition, HydrologyCaveAction> plannedActions = candidate.actions();
        LinkedHashMap<CavePosition, CaveVoxelPrecondition> preconditions =
                new LinkedHashMap<>(plannedActions.size());
        ArrayList<CavePosition> guards = new ArrayList<>();
        HydrologyCaveRejection boundaryRejection = addPlannedBoundaryGuards(
                view,
                source,
                settings,
                candidate.allowDryCaveConnections(),
                candidate.intentionalOpenings(),
                plannedActions,
                preconditions,
                actionIndex,
                openingIndex,
                guards
        );
        if (boundaryRejection != HydrologyCaveRejection.NONE) {
            return rejected(source, boundaryRejection);
        }

        for (Map.Entry<CavePosition, HydrologyCaveAction> entry : plannedActions.entrySet()) {
            CavePosition position = entry.getKey();
            HydrologyCaveRejection rejection = validatePlannedPosition(
                    view,
                    source,
                    settings,
                    position,
                    preconditions
            );
            if (rejection != HydrologyCaveRejection.NONE) {
                return rejected(source, rejection);
            }
        }
        LinkedHashMap<CavePosition, HydrologyCaveAction> actions =
                new LinkedHashMap<>(plannedActions.size() + guards.size());
        actions.putAll(plannedActions);
        for (CavePosition guard : guards) {
            actions.put(guard, HydrologyCaveAction.SEAL_GUARD);
        }
        return accepted(source, actions, preconditions);
    }

    boolean exceedsFloodVolume(
            Map<CavePosition, HydrologyCaveAction> actions,
            int maximumFloodVolume
    ) {
        if (actions.size() > HydrologyCavePlannerSettings.MAXIMUM_PLANNED_MUTATIONS) {
            return true;
        }
        int mutationCount = 0;
        for (HydrologyCaveAction action : actions.values()) {
            if (action != HydrologyCaveAction.SEAL_GUARD && ++mutationCount > maximumFloodVolume) {
                return true;
            }
        }
        return false;
    }

    boolean eligibleForTerrainSurfaceCheck(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            CavePosition position
    ) {
        return view.isInWorld(position)
                && validateBounds(source, settings, position) == HydrologyCaveRejection.NONE;
    }

    boolean isPlannedVolumeAboveTerrainSurface(
            CaveVoxelView view,
            HydrologyCaveCandidate candidate,
            CavePositionIndex actionIndex,
            CavePositionIndex openingIndex
    ) {
        HydrologyCaveSource source = candidate.source();
        HydrologyCavePlannerSettings settings = candidate.settings();
        for (CavePosition position : candidate.actions().keySet()) {
            if (!openingIndex.contains(position.x(), position.y(), position.z())
                    && eligibleForTerrainSurfaceCheck(view, source, settings, position)
                    && view.isAboveTerrainSurface(position)) {
                return true;
            }
            for (CavePosition direction : DIRECTIONS) {
                int neighborX = position.x() + direction.x();
                int neighborY = position.y() + direction.y();
                int neighborZ = position.z() + direction.z();
                if (actionIndex.contains(neighborX, neighborY, neighborZ)
                        || openingIndex.contains(neighborX, neighborY, neighborZ)) {
                    continue;
                }
                CavePosition neighbor = new CavePosition(neighborX, neighborY, neighborZ);
                if (!eligibleForTerrainSurfaceCheck(view, source, settings, neighbor)) {
                    continue;
                }
                if (view.isAboveTerrainSurface(neighbor)) {
                    return true;
                }
            }
        }
        return false;
    }

    HydrologyCaveRejection validatePlannedPosition(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            CavePosition position,
            Map<CavePosition, CaveVoxelPrecondition> preconditions
    ) {
        if (preconditions.containsKey(position)) {
            return HydrologyCaveRejection.NONE;
        }
        if (!view.isInWorld(position)) {
            return HydrologyCaveRejection.WORLD_BOUNDARY;
        }
        HydrologyCaveRejection boundsRejection = validateBounds(source, settings, position);
        if (boundsRejection != HydrologyCaveRejection.NONE) {
            return boundsRejection;
        }
        CaveVoxel voxel = voxelAt(view, position);
        HydrologyCaveRejection hazard = rejectionForHazard(voxel, settings);
        if (hazard != HydrologyCaveRejection.NONE) {
            return hazard;
        }
        boolean openToSurface = view.isOpenToSurface(position);
        preconditions.put(position, new CaveVoxelPrecondition(voxel, openToSurface));
        return HydrologyCaveRejection.NONE;
    }

    HydrologyCaveRejection addPlannedBoundaryGuards(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            boolean allowDryCaveConnections,
            Set<CavePosition> intentionalOpenings,
            Map<CavePosition, HydrologyCaveAction> actions,
            Map<CavePosition, CaveVoxelPrecondition> preconditions,
            CavePositionIndex actionIndex,
            CavePositionIndex openingIndex,
            List<CavePosition> guards
    ) {
        for (CavePosition opening : intentionalOpenings) {
            HydrologyCaveRejection rejection = validateIntentionalOpening(
                    view,
                    source,
                    settings,
                    opening,
                    preconditions
            );
            if (rejection != HydrologyCaveRejection.NONE) {
                return rejection;
            }
        }
        CavePositionIndex guardIndex = new CavePositionIndex(
                Math.min(actions.size(), MAXIMUM_INITIAL_GUARD_INDEX_SIZE)
        );
        for (Map.Entry<CavePosition, HydrologyCaveAction> entry : actions.entrySet()) {
            CavePosition position = entry.getKey();
            for (CavePosition direction : DIRECTIONS) {
                int neighborX = position.x() + direction.x();
                int neighborY = position.y() + direction.y();
                int neighborZ = position.z() + direction.z();
                if (actionIndex.contains(neighborX, neighborY, neighborZ)
                        || openingIndex.contains(neighborX, neighborY, neighborZ)) {
                    continue;
                }
                CavePosition neighbor = new CavePosition(neighborX, neighborY, neighborZ);
                CaveVoxelPrecondition precondition = preconditions.get(neighbor);
                if (precondition == null) {
                    if (!view.isInWorld(neighbor)) {
                        return HydrologyCaveRejection.WORLD_BOUNDARY;
                    }
                    HydrologyCaveRejection boundsRejection = validateBounds(source, settings, neighbor);
                    if (boundsRejection != HydrologyCaveRejection.NONE) {
                        return boundsRejection;
                    }
                    CaveVoxel voxel = voxelAt(view, neighbor);
                    HydrologyCaveRejection hazard = rejectionForHazard(voxel, settings);
                    if (hazard != HydrologyCaveRejection.NONE) {
                        return hazard;
                    }
                    precondition = new CaveVoxelPrecondition(voxel, view.isOpenToSurface(neighbor));
                    preconditions.put(neighbor, precondition);
                }
                if (precondition.voxel() == CaveVoxel.UNCONDITIONAL) {
                    continue;
                }
                if (entry.getValue() == HydrologyCaveAction.DRY_AIR
                        && precondition.voxel() == CaveVoxel.CAVE_AIR
                        && allowDryCaveConnections
                        && !precondition.openToSurface()) {
                    continue;
                }
                if (guardIndex.add(neighborX, neighborY, neighborZ)) {
                    if (actions.size() + guards.size()
                            >= HydrologyCavePlannerSettings.MAXIMUM_PLANNED_MUTATIONS) {
                        return HydrologyCaveRejection.VOLUME_LIMIT;
                    }
                    guards.add(neighbor);
                }
            }
        }
        return HydrologyCaveRejection.NONE;
    }

    HydrologyCaveRejection validateIntentionalOpening(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            CavePosition position,
            Map<CavePosition, CaveVoxelPrecondition> preconditions
    ) {
        if (!view.isInWorld(position)) {
            return HydrologyCaveRejection.WORLD_BOUNDARY;
        }
        HydrologyCaveRejection boundsRejection = validateBounds(source, settings, position);
        if (boundsRejection != HydrologyCaveRejection.NONE) {
            return boundsRejection;
        }
        CaveVoxel voxel = voxelAt(view, position);
        if (voxel == CaveVoxel.LAVA) {
            return HydrologyCaveRejection.LAVA_CONTACT;
        }
        if (voxel == CaveVoxel.INCOMPATIBLE_FLUID) {
            return HydrologyCaveRejection.INCOMPATIBLE_FLUID;
        }
        boolean openToSurface = voxel == CaveVoxel.UNCONDITIONAL || view.isOpenToSurface(position);
        preconditions.put(position, new CaveVoxelPrecondition(voxel, openToSurface));
        return HydrologyCaveRejection.NONE;
    }

    OptionalLong findConflictingSourceId(
            HydrologyCaveCandidate candidate,
            Map<CavePosition, HydrologyCaveAction> actions,
            Map<CavePosition, CaveClaimGroup> claimedBy,
            BiPredicate<HydrologyCaveCandidate, HydrologyCaveCandidate> compatibleOverlap
    ) {
        HydrologyCaveSource winner = null;
        for (Map.Entry<CavePosition, HydrologyCaveAction> entry : actions.entrySet()) {
            CaveClaimGroup claim = claimedBy.get(entry.getKey());
            if (claim == null
                    || claim.candidate().profileKey().equals(candidate.profileKey())
                    && claim.actions().get(entry.getKey()) == entry.getValue()
                    || compatibleOverlap.test(claim.candidate(), candidate)) {
                continue;
            }
            HydrologyCaveSource contender = claim.candidate().source();
            if (winner == null || SOURCE_PRIORITY.compare(contender, winner) < 0) {
                winner = contender;
            }
        }
        return winner == null ? OptionalLong.empty() : OptionalLong.of(winner.sourceId());
    }

    HydrologyCaveRejection validateSource(HydrologyCaveSource source) {
        if (source.entry().y() < source.waterHeadY()) {
            return HydrologyCaveRejection.INVALID_SOURCE;
        }
        if (source.target().y() > source.waterHeadY()) {
            return HydrologyCaveRejection.INVALID_SOURCE;
        }
        if (source.target().y() > source.entry().y()) {
            return HydrologyCaveRejection.INVALID_SOURCE;
        }
        return HydrologyCaveRejection.NONE;
    }

    HydrologyCaveRejection validateBounds(
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            CavePosition position
    ) {
        boolean closedComponent = source.mode() == HydrologyCaveMode.CLOSED_COMPONENT
                || source.mode() == HydrologyCaveMode.WATERFALL_POOL;
        int horizontalRadius = closedComponent
                ? settings.maxClosedComponentHorizontalRadius()
                : settings.maxHorizontalRadius();
        int maximumDepth = closedComponent
                ? settings.maxClosedComponentDepth()
                : settings.maxDepth();
        long deltaX = (long) position.x() - source.entry().x();
        long deltaZ = (long) position.z() - source.entry().z();
        long radiusSquared = (long) horizontalRadius * horizontalRadius;
        if ((deltaX * deltaX) + (deltaZ * deltaZ) > radiusSquared) {
            return HydrologyCaveRejection.RADIUS_LIMIT;
        }
        long depth = (long) source.entry().y() - position.y();
        int maximumGeneratedY = source.waterHeadY() + settings.dryHeadroom() + 1;
        boolean allowedGeneratedHeadroom = !closedComponent
                && position.y() <= maximumGeneratedY;
        if ((depth < 0L && !allowedGeneratedHeadroom) || depth > maximumDepth) {
            return HydrologyCaveRejection.DEPTH_LIMIT;
        }
        return HydrologyCaveRejection.NONE;
    }

    HydrologyCaveRejection rejectionForTarget(
            CaveVoxel voxel,
            HydrologyCavePlannerSettings settings
    ) {
        HydrologyCaveRejection hazard = rejectionForHazard(voxel, settings);
        return hazard == HydrologyCaveRejection.NONE ? HydrologyCaveRejection.NO_CAVE_TARGET : hazard;
    }

    HydrologyCaveRejection rejectionForHazard(
            CaveVoxel voxel,
            HydrologyCavePlannerSettings settings
    ) {
        return switch (voxel) {
            case LAVA -> HydrologyCaveRejection.LAVA_CONTACT;
            case INCOMPATIBLE_FLUID -> settings.existingFluidPolicy() == HydrologyCaveFluidPolicy.REPLACE_CONTAINED
                    ? HydrologyCaveRejection.NONE
                    : HydrologyCaveRejection.INCOMPATIBLE_FLUID;
            case COMPATIBLE_FLUID -> settings.existingFluidPolicy() == HydrologyCaveFluidPolicy.REJECT_EXISTING
                    ? HydrologyCaveRejection.EXISTING_FLUID
                    : HydrologyCaveRejection.NONE;
            default -> HydrologyCaveRejection.NONE;
        };
    }

    boolean isFluidReachable(CaveVoxel voxel, HydrologyCavePlannerSettings settings) {
        return voxel == CaveVoxel.CAVE_AIR
                || (voxel == CaveVoxel.COMPATIBLE_FLUID
                && settings.existingFluidPolicy() != HydrologyCaveFluidPolicy.REJECT_EXISTING)
                || (voxel == CaveVoxel.INCOMPATIBLE_FLUID
                && settings.existingFluidPolicy() == HydrologyCaveFluidPolicy.REPLACE_CONTAINED);
    }

    CaveVoxel voxelAt(CaveVoxelView view, CavePosition position) {
        return Objects.requireNonNull(view.voxelAt(position));
    }

    OptionalLong findWinningSourceId(
            Set<CavePosition> positions,
            Map<CavePosition, HydrologyCaveSource> claimedBy
    ) {
        HydrologyCaveSource winner = null;
        for (CavePosition position : positions) {
            HydrologyCaveSource contender = claimedBy.get(position);
            if (contender == null) {
                continue;
            }
            if (winner == null || SOURCE_PRIORITY.compare(contender, winner) < 0) {
                winner = contender;
            }
        }
        return winner == null ? OptionalLong.empty() : OptionalLong.of(winner.sourceId());
    }

    HydrologyCavePlan accepted(
            CaveVoxelView view,
            HydrologyCaveSource source,
            Map<CavePosition, HydrologyCaveAction> actions
    ) {
        Map<CavePosition, CaveVoxelPrecondition> preconditions = new LinkedHashMap<>(actions.size());
        for (CavePosition position : actions.keySet()) {
            preconditions.put(
                    position,
                    new CaveVoxelPrecondition(voxelAt(view, position), view.isOpenToSurface(position))
            );
        }
        return accepted(source, actions, preconditions);
    }

    HydrologyCavePlan accepted(
            HydrologyCaveSource source,
            Map<CavePosition, HydrologyCaveAction> actions,
            Map<CavePosition, CaveVoxelPrecondition> preconditions
    ) {
        return new HydrologyCavePlan(
                source,
                HydrologyCaveRejection.NONE,
                actions,
                preconditions,
                OptionalLong.empty()
        );
    }

    HydrologyCavePlan acceptedDecision(HydrologyCaveSource source) {
        return new HydrologyCavePlan(
                source,
                HydrologyCaveRejection.NONE,
                Map.of(),
                Map.of(),
                OptionalLong.empty()
        );
    }

    HydrologyCavePlan rejected(HydrologyCaveSource source, HydrologyCaveRejection rejection) {
        return new HydrologyCavePlan(
                source,
                rejection,
                Map.of(),
                Map.of(),
                OptionalLong.empty()
        );
    }

    HydrologyCavePlan rejectedOverlap(HydrologyCaveSource source, long winnerSourceId) {
        return new HydrologyCavePlan(
                source,
                HydrologyCaveRejection.OVERLAPPING_SOURCE,
                Map.of(),
                Map.of(),
                OptionalLong.of(winnerSourceId)
        );
    }
}
