package art.arcane.iris.engine.hydrology.cave;

import art.arcane.iris.engine.hydrology.HydrologyObservedPlannedSurface;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiPredicate;

public final class HydrologyCaveContainmentPlanner {
    private static final int MAXIMUM_INITIAL_GUARD_INDEX_SIZE = 16_384;
    private static final List<CavePosition> DIRECTIONS = List.of(
            new CavePosition(1, 0, 0),
            new CavePosition(-1, 0, 0),
            new CavePosition(0, 1, 0),
            new CavePosition(0, -1, 0),
            new CavePosition(0, 0, 1),
            new CavePosition(0, 0, -1)
    );
    private static final Comparator<HydrologyCaveSource> SOURCE_PRIORITY =
            HydrologyCaveConflictPolicy.sourcePriority();

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

        PathResult throat = buildThroat(view, source, settings);
        if (throat.rejection() != HydrologyCaveRejection.NONE) {
            return rejected(source, throat.rejection());
        }

        return switch (source.mode()) {
            case CLOSED_COMPONENT -> planClosedComponent(view, source, settings, throat.positions());
            case GENERATED_GROTTO -> planGeneratedGrotto(view, source, settings, throat.positions());
            case GROTTO_OR_CLOSED_COMPONENT -> planGrottoOrClosedComponent(
                    view,
                    source,
                    settings,
                    throat.positions()
            );
            case WATERFALL_POOL -> planWaterfallPool(
                    view,
                    source,
                    settings,
                    throat.positions()
            );
            case DEEP_POOL -> planDeepPool(view, source, settings, throat.positions());
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
        ValidationBatch batch = validateBatch(
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

    private ValidationBatch validateBatch(
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

        return new ValidationBatch(
                List.copyOf(plans),
                combinedActions,
                combinedPreconditions
        );
    }

    private record ValidationBatch(
            List<HydrologyCavePlan> plans,
            Map<CavePosition, HydrologyCaveAction> actions,
            Map<CavePosition, CaveVoxelPrecondition> baselinePreconditions
    ) {
    }

    public static final class ValidationCache {
        private static final int DEFAULT_MAXIMUM_ENTRIES = 256;
        private static final long DEFAULT_MAXIMUM_RETAINED_POSITIONS = 262_144L;

        private final IdentityHashMap<HydrologyCaveCandidate, CachedValidation> validations;
        private final HashMap<HydrologyCaveCandidate, CachedValidation> equivalentValidations;
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
            CachedValidation cached = validations.get(candidate);
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
            ViewObservations viewObservations = recording.snapshot();
            SurfaceObservations surfaceObservations = plannedSurface == null
                    ? SurfaceObservations.empty()
                    : SurfaceObservations.capture(viewObservations, plannedSurface);
            if (observeColumnsOnly) {
                viewObservations = ViewObservations.empty();
            }
            CachedValidation validation = new CachedValidation(plan, viewObservations, surfaceObservations);
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

        private void retain(HydrologyCaveCandidate candidate, CachedValidation validation) {
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

        private void remove(CachedValidation cached) {
            validations.entrySet().removeIf(
                    (Map.Entry<HydrologyCaveCandidate, CachedValidation> entry) -> entry.getValue() == cached
            );
            equivalentValidations.entrySet().removeIf(
                    (Map.Entry<HydrologyCaveCandidate, CachedValidation> entry) -> entry.getValue() == cached
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

    private static final class RecordingCaveVoxelView implements CaveVoxelView {
        private static final byte IN_WORLD = 0;
        private static final byte VOXEL = 1;
        private static final byte OPEN_TO_SURFACE = 2;
        private static final byte ABOVE_TERRAIN_SURFACE = 3;

        private final CaveVoxelView delegate;
        private final boolean observeColumnsOnly;
        private CavePosition[] positions;
        private byte[] operations;
        private byte[] values;
        private CavePositionIndex[] observed;
        private int size;

        private RecordingCaveVoxelView(CaveVoxelView delegate, boolean observeColumnsOnly) {
            this.delegate = delegate;
            this.observeColumnsOnly = observeColumnsOnly;
            this.positions = new CavePosition[1024];
            this.operations = observeColumnsOnly ? new byte[0] : new byte[1024];
            this.values = observeColumnsOnly ? new byte[0] : new byte[1024];
            this.observed = observeColumnsOnly
                    ? new CavePositionIndex[]{new CavePositionIndex()}
                    : new CavePositionIndex[]{
                            new CavePositionIndex(),
                            new CavePositionIndex(),
                            new CavePositionIndex(),
                            new CavePositionIndex()
                    };
        }

        @Override
        public boolean isInWorld(CavePosition position) {
            boolean result = delegate.isInWorld(position);
            add(position, IN_WORLD, result ? 1 : 0);
            return result;
        }

        @Override
        public CaveVoxel voxelAt(CavePosition position) {
            CaveVoxel result = delegate.voxelAt(position);
            add(position, VOXEL, result.ordinal());
            return result;
        }

        @Override
        public boolean isOpenToSurface(CavePosition position) {
            boolean result = delegate.isOpenToSurface(position);
            add(position, OPEN_TO_SURFACE, result ? 1 : 0);
            return result;
        }

        @Override
        public boolean isAboveTerrainSurface(CavePosition position) {
            boolean result = delegate.isAboveTerrainSurface(position);
            add(position, ABOVE_TERRAIN_SURFACE, result ? 1 : 0);
            return result;
        }

        private void add(CavePosition position, byte operation, int value) {
            int observedIndex = observeColumnsOnly ? 0 : operation;
            int observedY = observeColumnsOnly ? 0 : position.y();
            if (!observed[observedIndex].add(position.x(), observedY, position.z())) {
                return;
            }
            if (size == positions.length) {
                int expandedSize = Math.multiplyExact(size, 2);
                positions = Arrays.copyOf(positions, expandedSize);
                if (!observeColumnsOnly) {
                    operations = Arrays.copyOf(operations, expandedSize);
                    values = Arrays.copyOf(values, expandedSize);
                }
            }
            positions[size] = position;
            if (!observeColumnsOnly) {
                operations[size] = operation;
                values[size] = (byte) value;
            }
            size++;
        }

        private ViewObservations snapshot() {
            observed = null;
            if (positions.length == 1024) {
                return new ViewObservations(
                        Arrays.copyOf(positions, size),
                        observeColumnsOnly ? operations : Arrays.copyOf(operations, size),
                        observeColumnsOnly ? values : Arrays.copyOf(values, size),
                        size
                );
            }
            return new ViewObservations(positions, operations, values, size);
        }
    }

    private record CachedValidation(
            HydrologyCavePlan plan,
            ViewObservations viewObservations,
            SurfaceObservations surfaceObservations
    ) {
        private long retainedPositions() {
            return Math.max(1L, (long) plan.actions().size()
                    + plan.baselinePreconditions().size()
                    + viewObservations.size()
                    + surfaceObservations.observations().size());
        }
    }

    private record SurfaceObservations(
            List<HydrologyObservedPlannedSurface.Observation> observations
    ) {
        private static SurfaceObservations empty() {
            return new SurfaceObservations(List.of());
        }

        private static SurfaceObservations capture(
                ViewObservations viewObservations,
                HydrologyObservedPlannedSurface plannedSurface
        ) {
            ArrayList<HydrologyObservedPlannedSurface.Observation> observations = new ArrayList<>();
            for (int index = 0; index < viewObservations.size(); index++) {
                CavePosition position = viewObservations.positions()[index];
                observations.addAll(plannedSurface.observationsAt(position.x(), position.z()));
            }
            return new SurfaceObservations(List.copyOf(observations));
        }

        private boolean matches(HydrologyObservedPlannedSurface plannedSurface) {
            for (HydrologyObservedPlannedSurface.Observation observation : observations) {
                int resolvedHeight = plannedSurface.resolve(
                        observation.x(),
                        observation.z(),
                        observation.naturalHeight()
                );
                if (resolvedHeight != observation.resolvedHeight()) {
                    return false;
                }
            }
            return true;
        }
    }

    private record ViewObservations(
            CavePosition[] positions,
            byte[] operations,
            byte[] values,
            int size
    ) {
        private static ViewObservations empty() {
            return new ViewObservations(new CavePosition[0], new byte[0], new byte[0], 0);
        }

        private boolean matches(CaveVoxelView view) {
            for (int index = 0; index < size; index++) {
                CavePosition position = positions[index];
                int actual = switch (operations[index]) {
                    case RecordingCaveVoxelView.IN_WORLD -> view.isInWorld(position) ? 1 : 0;
                    case RecordingCaveVoxelView.VOXEL -> view.voxelAt(position).ordinal();
                    case RecordingCaveVoxelView.OPEN_TO_SURFACE -> view.isOpenToSurface(position) ? 1 : 0;
                    case RecordingCaveVoxelView.ABOVE_TERRAIN_SURFACE ->
                            view.isAboveTerrainSurface(position) ? 1 : 0;
                    default -> throw new IllegalStateException("Unknown cave view operation.");
                };
                if (actual != Byte.toUnsignedInt(values[index])) {
                    return false;
                }
            }
            return true;
        }
    }

    public HydrologyCavePlan validate(CaveVoxelView view, HydrologyCaveCandidate candidate) {
        return validate(view, candidate, false);
    }

    private HydrologyCavePlan validate(
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

    private boolean exceedsFloodVolume(
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

    private boolean eligibleForTerrainSurfaceCheck(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            CavePosition position
    ) {
        return view.isInWorld(position)
                && validateBounds(source, settings, position) == HydrologyCaveRejection.NONE;
    }

    private boolean isPlannedVolumeAboveTerrainSurface(
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

    private HydrologyCaveRejection validatePlannedPosition(
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

    private HydrologyCaveRejection addPlannedBoundaryGuards(
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

    private HydrologyCaveRejection validateIntentionalOpening(
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

    private OptionalLong findConflictingSourceId(
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

    private HydrologyCavePlan planGrottoOrClosedComponent(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            List<CavePosition> throat
    ) {
        CaveVoxel targetVoxel = voxelAt(view, source.target());
        if (isFluidReachable(targetVoxel, settings)) {
            return planClosedComponent(view, source, settings, throat);
        }
        if (targetVoxel == CaveVoxel.LAVA) {
            return rejected(source, HydrologyCaveRejection.LAVA_CONTACT);
        }
        if (targetVoxel == CaveVoxel.INCOMPATIBLE_FLUID) {
            return rejected(source, HydrologyCaveRejection.INCOMPATIBLE_FLUID);
        }
        return planGeneratedGrotto(view, source, settings, throat);
    }

    private HydrologyCavePlan planWaterfallPool(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            List<CavePosition> throat
    ) {
        if (!view.isOpenToSurface(source.target())) {
            return planGrottoOrClosedComponent(view, source, settings, throat);
        }
        HydrologyCaveRejection dryThroatRejection = validateDryThroatContacts(view, source, throat);
        if (dryThroatRejection != HydrologyCaveRejection.NONE) {
            return rejected(source, dryThroatRejection);
        }
        HydrologyCaveRejection shaftRejection = validateWaterfallShaft(view, source, settings, throat);
        if (shaftRejection != HydrologyCaveRejection.NONE) {
            return rejected(source, shaftRejection);
        }
        CaveVoxel targetVoxel = voxelAt(view, source.target());
        if (!isFluidReachable(targetVoxel, settings)) {
            return rejected(source, rejectionForTarget(targetVoxel, settings));
        }

        Map<CavePosition, HydrologyCaveAction> actions = new LinkedHashMap<>();
        addThroatActions(actions, throat, source);
        addSealGuards(view, source, actions);
        return accepted(view, source, actions);
    }

    private HydrologyCavePlan planClosedComponent(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            List<CavePosition> throat
    ) {
        HydrologyCaveRejection dryThroatRejection = validateDryThroatContacts(view, source, throat);
        if (dryThroatRejection != HydrologyCaveRejection.NONE) {
            return rejected(source, dryThroatRejection);
        }
        HydrologyCaveRejection waterfallRejection = validateWaterfallShaft(view, source, settings, throat);
        if (waterfallRejection != HydrologyCaveRejection.NONE) {
            return rejected(source, waterfallRejection);
        }

        CaveVoxel targetVoxel = voxelAt(view, source.target());
        if (!isFluidReachable(targetVoxel, settings)) {
            return rejected(source, rejectionForTarget(targetVoxel, settings));
        }

        ComponentResult component = resolveClosedComponent(view, source, settings, throat);
        if (component.rejection() != HydrologyCaveRejection.NONE) {
            return rejected(source, component.rejection());
        }

        Map<CavePosition, HydrologyCaveAction> actions = new LinkedHashMap<>();
        addThroatActions(actions, throat, source);
        for (CavePosition position : component.positions()) {
            actions.put(position, HydrologyCaveAction.WET_SOURCE);
        }
        addSealGuards(view, source, actions);
        return accepted(view, source, actions);
    }

    private HydrologyCaveRejection validateDryThroatContacts(
            CaveVoxelView view,
            HydrologyCaveSource source,
            List<CavePosition> throat
    ) {
        Set<CavePosition> throatPositions = Set.copyOf(throat);
        for (CavePosition position : throat) {
            if (position.y() <= source.waterHeadY()) {
                continue;
            }
            for (CavePosition direction : DIRECTIONS) {
                CavePosition neighbor = position.offset(direction.x(), direction.y(), direction.z());
                if (throatPositions.contains(neighbor) || isInletOpening(source, neighbor)) {
                    continue;
                }
                if (!view.isInWorld(neighbor)) {
                    return HydrologyCaveRejection.WORLD_BOUNDARY;
                }
                CaveVoxel voxel = voxelAt(view, neighbor);
                if (voxel == CaveVoxel.LAVA) {
                    return HydrologyCaveRejection.LAVA_CONTACT;
                }
                if (voxel == CaveVoxel.COMPATIBLE_FLUID) {
                    return HydrologyCaveRejection.EXISTING_FLUID;
                }
                if (voxel == CaveVoxel.INCOMPATIBLE_FLUID) {
                    return HydrologyCaveRejection.INCOMPATIBLE_FLUID;
                }
            }
        }
        return HydrologyCaveRejection.NONE;
    }

    private HydrologyCaveRejection validateWaterfallShaft(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            List<CavePosition> throat
    ) {
        if (source.mode() != HydrologyCaveMode.WATERFALL_POOL) {
            return HydrologyCaveRejection.NONE;
        }

        Set<CavePosition> throatPositions = Set.copyOf(throat);
        for (CavePosition position : throat) {
            if (position.y() <= source.waterHeadY()) {
                continue;
            }
            for (CavePosition direction : DIRECTIONS) {
                CavePosition neighbor = position.offset(direction.x(), direction.y(), direction.z());
                if (throatPositions.contains(neighbor) || isInletOpening(source, neighbor)) {
                    continue;
                }
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
                if (voxel != CaveVoxel.SOLID) {
                    return HydrologyCaveRejection.WATERFALL_SHAFT_OPEN;
                }
            }
        }
        return HydrologyCaveRejection.NONE;
    }

    private HydrologyCavePlan planGeneratedGrotto(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            List<CavePosition> throat
    ) {
        GrottoResult grotto = buildGrotto(source, settings);
        if (grotto.rejection() != HydrologyCaveRejection.NONE) {
            return rejected(source, grotto.rejection());
        }
        Set<CavePosition> chamber = grotto.positions();

        Set<CavePosition> carve = new LinkedHashSet<>(chamber.size() + throat.size());
        carve.addAll(chamber);
        carve.addAll(throat);
        HydrologyCaveRejection carveRejection = validateGeneratedCarve(view, source, settings, carve);
        if (carveRejection != HydrologyCaveRejection.NONE) {
            return rejected(source, carveRejection);
        }

        BoundaryResult boundary = validateGeneratedBoundary(view, source, settings, carve);
        if (boundary.rejection() != HydrologyCaveRejection.NONE) {
            return rejected(source, boundary.rejection());
        }

        Map<CavePosition, HydrologyCaveAction> actions = new LinkedHashMap<>();
        addChamberActions(actions, chamber, source.waterHeadY());
        addThroatActions(actions, throat, source);
        for (CavePosition position : boundary.sealGuards()) {
            actions.put(position, HydrologyCaveAction.SEAL_GUARD);
        }
        return accepted(view, source, actions);
    }

    private HydrologyCavePlan planDeepPool(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            List<CavePosition> throat
    ) {
        GrottoResult grotto = buildGrotto(source, settings);
        if (grotto.rejection() != HydrologyCaveRejection.NONE) {
            return rejected(source, grotto.rejection());
        }
        Set<CavePosition> chamber = grotto.positions();
        Set<CavePosition> carve = new LinkedHashSet<>(chamber.size() + throat.size());
        carve.addAll(chamber);
        carve.addAll(throat);

        HydrologyCaveRejection carveRejection = validateDeepPoolCarve(view, source, settings, carve);
        if (carveRejection != HydrologyCaveRejection.NONE) {
            return rejected(source, carveRejection);
        }
        BoundaryResult boundary = validateDeepPoolBoundary(view, source, settings, carve);
        if (boundary.rejection() != HydrologyCaveRejection.NONE) {
            return rejected(source, boundary.rejection());
        }

        Map<CavePosition, HydrologyCaveAction> actions = new LinkedHashMap<>();
        addChamberActions(actions, chamber, source.waterHeadY());
        addThroatActions(actions, throat, source);
        for (CavePosition position : boundary.sealGuards()) {
            actions.put(position, HydrologyCaveAction.SEAL_GUARD);
        }
        return accepted(view, source, actions);
    }

    private HydrologyCaveRejection validateDeepPoolCarve(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            Set<CavePosition> carve
    ) {
        for (CavePosition position : carve) {
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
            if (voxel == CaveVoxel.SOLID) {
                continue;
            }
            if (position.y() > source.waterHeadY()
                    && voxel == CaveVoxel.CAVE_AIR
                    && !view.isOpenToSurface(position)) {
                continue;
            }
            return HydrologyCaveRejection.GROTTO_INTERSECTION;
        }
        return HydrologyCaveRejection.NONE;
    }

    private BoundaryResult validateDeepPoolBoundary(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            Set<CavePosition> carve
    ) {
        Set<CavePosition> guards = new LinkedHashSet<>();
        for (CavePosition position : carve) {
            for (CavePosition direction : DIRECTIONS) {
                CavePosition neighbor = position.offset(direction.x(), direction.y(), direction.z());
                if (carve.contains(neighbor)) {
                    continue;
                }
                if (!view.isInWorld(neighbor)) {
                    return BoundaryResult.rejected(HydrologyCaveRejection.WORLD_BOUNDARY);
                }
                HydrologyCaveRejection boundsRejection = validateBounds(source, settings, neighbor);
                if (boundsRejection != HydrologyCaveRejection.NONE) {
                    return BoundaryResult.rejected(boundsRejection);
                }
                CaveVoxel voxel = voxelAt(view, neighbor);
                HydrologyCaveRejection hazard = rejectionForHazard(voxel, settings);
                if (hazard != HydrologyCaveRejection.NONE) {
                    return BoundaryResult.rejected(hazard);
                }
                if (voxel == CaveVoxel.SOLID) {
                    guards.add(neighbor);
                    continue;
                }
                if (neighbor.y() > source.waterHeadY()
                        && voxel == CaveVoxel.CAVE_AIR
                        && !view.isOpenToSurface(neighbor)) {
                    continue;
                }
                return BoundaryResult.rejected(HydrologyCaveRejection.GROTTO_SHELL_OPEN);
            }
        }
        return BoundaryResult.accepted(guards);
    }

    private ComponentResult resolveClosedComponent(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            List<CavePosition> throat
    ) {
        Queue<CavePosition> queue = new ArrayDeque<>();
        Set<CavePosition> queued = new LinkedHashSet<>();
        int visitedCount = 0;

        queue.add(source.target());
        queued.add(source.target());
        HydrologyCaveRejection seedRejection = addThroatContacts(view, source, settings, throat, queue, queued);
        if (seedRejection != HydrologyCaveRejection.NONE) {
            return ComponentResult.rejected(seedRejection);
        }

        while (!queue.isEmpty()) {
            CavePosition position = queue.remove();
            HydrologyCaveRejection positionRejection = validateReachablePosition(view, source, settings, position);
            if (positionRejection != HydrologyCaveRejection.NONE) {
                return ComponentResult.rejected(positionRejection);
            }
            if (++visitedCount > settings.maxFloodVolume()) {
                return ComponentResult.rejected(HydrologyCaveRejection.VOLUME_LIMIT);
            }

            for (CavePosition direction : DIRECTIONS) {
                CavePosition neighbor = position.offset(direction.x(), direction.y(), direction.z());
                HydrologyCaveRejection neighborRejection = inspectReachableNeighbor(
                        view,
                        source,
                        settings,
                        neighbor,
                        queue,
                        queued
                );
                if (neighborRejection != HydrologyCaveRejection.NONE) {
                    return ComponentResult.rejected(neighborRejection);
                }
            }
        }

        return ComponentResult.accepted(queued);
    }

    private HydrologyCaveRejection addThroatContacts(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            List<CavePosition> throat,
            Queue<CavePosition> queue,
            Set<CavePosition> queued
    ) {
        for (CavePosition position : throat) {
            if (position.y() > source.waterHeadY()) {
                continue;
            }
            for (CavePosition direction : DIRECTIONS) {
                CavePosition neighbor = position.offset(direction.x(), direction.y(), direction.z());
                HydrologyCaveRejection rejection = inspectReachableNeighbor(
                        view,
                        source,
                        settings,
                        neighbor,
                        queue,
                        queued
                );
                if (rejection != HydrologyCaveRejection.NONE) {
                    return rejection;
                }
            }
        }
        return HydrologyCaveRejection.NONE;
    }

    private HydrologyCaveRejection inspectReachableNeighbor(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            CavePosition position,
            Queue<CavePosition> queue,
            Set<CavePosition> queued
    ) {
        if (position.y() > source.waterHeadY()) {
            return inspectAboveHeadNeighbor(view, source, position);
        }
        if (!view.isInWorld(position)) {
            return HydrologyCaveRejection.WORLD_BOUNDARY;
        }

        CaveVoxel voxel = voxelAt(view, position);
        HydrologyCaveRejection hazard = rejectionForHazard(voxel, settings);
        if (hazard != HydrologyCaveRejection.NONE) {
            return hazard;
        }
        if (!isFluidReachable(voxel, settings)) {
            return HydrologyCaveRejection.NONE;
        }

        HydrologyCaveRejection boundsRejection = validateBounds(source, settings, position);
        if (boundsRejection != HydrologyCaveRejection.NONE) {
            return boundsRejection;
        }
        if (queued.add(position)) {
            queue.add(position);
        }
        return HydrologyCaveRejection.NONE;
    }

    private HydrologyCaveRejection inspectAboveHeadNeighbor(
            CaveVoxelView view,
            HydrologyCaveSource source,
            CavePosition position
    ) {
        if (isInletOpening(source, position) || !view.isInWorld(position)) {
            return HydrologyCaveRejection.NONE;
        }
        CaveVoxel voxel = voxelAt(view, position);
        return switch (voxel) {
            case LAVA -> HydrologyCaveRejection.LAVA_CONTACT;
            case COMPATIBLE_FLUID -> HydrologyCaveRejection.EXISTING_FLUID;
            case INCOMPATIBLE_FLUID -> HydrologyCaveRejection.INCOMPATIBLE_FLUID;
            default -> HydrologyCaveRejection.NONE;
        };
    }

    private HydrologyCaveRejection validateReachablePosition(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            CavePosition position
    ) {
        if (!view.isInWorld(position)) {
            return HydrologyCaveRejection.WORLD_BOUNDARY;
        }
        HydrologyCaveRejection boundsRejection = validateBounds(source, settings, position);
        if (boundsRejection != HydrologyCaveRejection.NONE) {
            return boundsRejection;
        }
        if (view.isOpenToSurface(position)) {
            return HydrologyCaveRejection.OPEN_SURFACE;
        }
        CaveVoxel voxel = voxelAt(view, position);
        HydrologyCaveRejection hazard = rejectionForHazard(voxel, settings);
        if (hazard != HydrologyCaveRejection.NONE) {
            return hazard;
        }
        return isFluidReachable(voxel, settings)
                ? HydrologyCaveRejection.NONE
                : HydrologyCaveRejection.NO_CAVE_TARGET;
    }

    private PathResult buildThroat(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings
    ) {
        CavePosition entry = source.entry();
        CavePosition target = source.target();
        int deltaX = target.x() - entry.x();
        int deltaY = target.y() - entry.y();
        int deltaZ = target.z() - entry.z();
        int movesX = Math.abs(deltaX);
        int movesY = Math.abs(deltaY);
        int movesZ = Math.abs(deltaZ);
        int length = movesX + movesY + movesZ;
        if (length > settings.maxThroatLength()) {
            return PathResult.rejected(HydrologyCaveRejection.THROAT_LIMIT);
        }

        int stepX = Integer.signum(deltaX);
        int stepY = Integer.signum(deltaY);
        int stepZ = Integer.signum(deltaZ);
        int usedX = 0;
        int usedY = 0;
        int usedZ = 0;
        CavePosition current = entry;
        List<CavePosition> positions = new ArrayList<>(length + 1);

        while (true) {
            HydrologyCaveRejection positionRejection = validateThroatPosition(view, source, settings, current);
            if (positionRejection != HydrologyCaveRejection.NONE) {
                return PathResult.rejected(positionRejection);
            }
            positions.add(current);
            if (current.equals(target)) {
                return expandThroat(view, source, settings, positions);
            }

            int axis = selectNextAxis(source.sourceId(), movesX, movesY, movesZ, usedX, usedY, usedZ);
            if (axis == 0) {
                current = current.offset(stepX, 0, 0);
                usedX++;
            } else if (axis == 1) {
                current = current.offset(0, stepY, 0);
                usedY++;
            } else {
                current = current.offset(0, 0, stepZ);
                usedZ++;
            }
        }
    }

    private PathResult expandThroat(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            List<CavePosition> centerline
    ) {
        int radius = settings.throatRadius();
        int extent = radius - 1;
        int radiusSquared = radius * radius;
        Set<CavePosition> expanded = new LinkedHashSet<>();
        for (CavePosition center : centerline) {
            for (int dx = -extent; dx <= extent; dx++) {
                for (int dy = -extent; dy <= extent; dy++) {
                    for (int dz = -extent; dz <= extent; dz++) {
                        if ((dx * dx) + (dy * dy) + (dz * dz) >= radiusSquared) {
                            continue;
                        }
                        CavePosition position = center.offset(dx, dy, dz);
                        if (position.y() > source.entry().y()) {
                            continue;
                        }
                        HydrologyCaveRejection rejection = validateThroatPosition(view, source, settings, position);
                        if (rejection != HydrologyCaveRejection.NONE) {
                            return PathResult.rejected(rejection);
                        }
                        expanded.add(position);
                        if (expanded.size() > settings.maxFloodVolume()) {
                            return PathResult.rejected(HydrologyCaveRejection.VOLUME_LIMIT);
                        }
                    }
                }
            }
        }
        return PathResult.accepted(List.copyOf(expanded));
    }

    private int selectNextAxis(
            long sourceId,
            int movesX,
            int movesY,
            int movesZ,
            int usedX,
            int usedY,
            int usedZ
    ) {
        double scoreX = nextAxisScore(movesX, usedX);
        double scoreY = nextAxisScore(movesY, usedY);
        double scoreZ = nextAxisScore(movesZ, usedZ);
        double minimum = Math.min(scoreX, Math.min(scoreY, scoreZ));
        int tieOffset = Math.floorMod(sourceId, 3);
        for (int offset = 0; offset < 3; offset++) {
            int axis = (tieOffset + offset) % 3;
            double score = axis == 0 ? scoreX : axis == 1 ? scoreY : scoreZ;
            if (score == minimum) {
                return axis;
            }
        }
        throw new IllegalStateException("No remaining throat axis");
    }

    private double nextAxisScore(int moves, int used) {
        if (used >= moves) {
            return Double.POSITIVE_INFINITY;
        }
        return ((2D * used) + 1D) / moves;
    }

    private HydrologyCaveRejection validateThroatPosition(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            CavePosition position
    ) {
        if (!view.isInWorld(position)) {
            return HydrologyCaveRejection.WORLD_BOUNDARY;
        }
        HydrologyCaveRejection boundsRejection = validateBounds(source, settings, position);
        if (boundsRejection != HydrologyCaveRejection.NONE) {
            return boundsRejection;
        }
        return rejectionForHazard(voxelAt(view, position), settings);
    }

    private GrottoResult buildGrotto(HydrologyCaveSource source, HydrologyCavePlannerSettings settings) {
        int horizontalRadius = settings.grottoHorizontalRadius();
        int verticalRadius = settings.grottoVerticalRadius();
        Set<CavePosition> candidates = new LinkedHashSet<>();

        for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
            for (int dy = -verticalRadius; dy <= verticalRadius; dy++) {
                for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                    if (settings.grottoShape().contains(source, settings, dx, dy, dz)) {
                        candidates.add(source.target().offset(dx, dy, dz));
                        if (candidates.size() > settings.maxFloodVolume()) {
                            return GrottoResult.rejected(HydrologyCaveRejection.VOLUME_LIMIT);
                        }
                    }
                }
            }
        }
        candidates.add(source.target());
        for (int offset = 1; offset <= settings.dryHeadroom(); offset++) {
            CavePosition headroom = new CavePosition(
                    source.target().x(), source.waterHeadY() + offset, source.target().z());
            if (Math.abs(headroom.y() - source.target().y()) > verticalRadius) {
                return GrottoResult.rejected(HydrologyCaveRejection.DRY_HEADROOM_LIMIT);
            }
            candidates.add(headroom);
            if (candidates.size() > settings.maxFloodVolume()) {
                return GrottoResult.rejected(HydrologyCaveRejection.VOLUME_LIMIT);
            }
        }
        return GrottoResult.accepted(connectedGrotto(source.target(), candidates));
    }

    private Set<CavePosition> connectedGrotto(CavePosition target, Set<CavePosition> candidates) {
        Queue<CavePosition> queue = new ArrayDeque<>();
        Set<CavePosition> connected = new LinkedHashSet<>();
        queue.add(target);
        connected.add(target);
        while (!queue.isEmpty()) {
            CavePosition position = queue.remove();
            for (CavePosition direction : DIRECTIONS) {
                CavePosition neighbor = position.offset(direction.x(), direction.y(), direction.z());
                if (candidates.contains(neighbor) && connected.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return connected;
    }

    private HydrologyCaveRejection validateGeneratedCarve(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            Set<CavePosition> carve
    ) {
        for (CavePosition position : carve) {
            if (!view.isInWorld(position)) {
                return HydrologyCaveRejection.WORLD_BOUNDARY;
            }
            HydrologyCaveRejection boundsRejection = validateBounds(source, settings, position);
            if (boundsRejection != HydrologyCaveRejection.NONE) {
                return boundsRejection;
            }
            CaveVoxel voxel = voxelAt(view, position);
            if (isGeneratedInletCarve(view, source, settings, position, voxel)) {
                continue;
            }
            HydrologyCaveRejection hazard = rejectionForHazard(voxel, settings);
            if (hazard != HydrologyCaveRejection.NONE) {
                return hazard;
            }
            if (voxel != CaveVoxel.SOLID) {
                return HydrologyCaveRejection.GROTTO_INTERSECTION;
            }
        }
        return HydrologyCaveRejection.NONE;
    }

    private BoundaryResult validateGeneratedBoundary(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            Set<CavePosition> carve
    ) {
        Set<CavePosition> guards = new LinkedHashSet<>();
        for (CavePosition position : carve) {
            for (CavePosition direction : DIRECTIONS) {
                CavePosition neighbor = position.offset(direction.x(), direction.y(), direction.z());
                if (carve.contains(neighbor)) {
                    continue;
                }
                if (isInletOpening(source, neighbor)
                        || isGeneratedInletOpening(view, source, settings, neighbor)) {
                    continue;
                }
                if (!view.isInWorld(neighbor)) {
                    return BoundaryResult.rejected(HydrologyCaveRejection.WORLD_BOUNDARY);
                }
                HydrologyCaveRejection boundsRejection = validateBounds(source, settings, neighbor);
                if (boundsRejection != HydrologyCaveRejection.NONE) {
                    return BoundaryResult.rejected(boundsRejection);
                }
                CaveVoxel voxel = voxelAt(view, neighbor);
                HydrologyCaveRejection hazard = rejectionForHazard(voxel, settings);
                if (hazard != HydrologyCaveRejection.NONE) {
                    return BoundaryResult.rejected(hazard);
                }
                if (voxel != CaveVoxel.SOLID) {
                    return BoundaryResult.rejected(HydrologyCaveRejection.GROTTO_SHELL_OPEN);
                }
                guards.add(neighbor);
            }
        }
        return BoundaryResult.accepted(guards);
    }

    private void addChamberActions(
            Map<CavePosition, HydrologyCaveAction> actions,
            Collection<CavePosition> positions,
            int waterHeadY
    ) {
        for (CavePosition position : positions) {
            HydrologyCaveAction action = position.y() <= waterHeadY
                    ? HydrologyCaveAction.WET_SOURCE
                    : HydrologyCaveAction.DRY_AIR;
            actions.put(position, action);
        }
    }

    private void addThroatActions(
            Map<CavePosition, HydrologyCaveAction> actions,
            Collection<CavePosition> throat,
            HydrologyCaveSource source
    ) {
        for (CavePosition position : throat) {
            HydrologyCaveAction action;
            if (position.y() <= source.waterHeadY()) {
                action = HydrologyCaveAction.WET_SOURCE;
            } else if (source.mode() == HydrologyCaveMode.WATERFALL_POOL
                    || source.mode() == HydrologyCaveMode.GENERATED_GROTTO) {
                action = HydrologyCaveAction.FALLING_FLUID;
            } else {
                action = HydrologyCaveAction.DRY_AIR;
            }
            actions.put(position, action);
        }
    }

    private void addSealGuards(
            CaveVoxelView view,
            HydrologyCaveSource source,
            Map<CavePosition, HydrologyCaveAction> actions
    ) {
        Set<CavePosition> guards = new LinkedHashSet<>();
        for (CavePosition position : List.copyOf(actions.keySet())) {
            for (CavePosition direction : DIRECTIONS) {
                CavePosition neighbor = position.offset(direction.x(), direction.y(), direction.z());
                if (actions.containsKey(neighbor)
                        || isInletOpening(source, neighbor)
                        || !view.isInWorld(neighbor)) {
                    continue;
                }
                if (voxelAt(view, neighbor) == CaveVoxel.SOLID) {
                    guards.add(neighbor);
                }
            }
        }
        for (CavePosition guard : guards) {
            actions.put(guard, HydrologyCaveAction.SEAL_GUARD);
        }
    }

    private boolean isInletOpening(HydrologyCaveSource source, CavePosition position) {
        return position.equals(source.entry().offset(0, 1, 0));
    }

    private boolean isGeneratedInletCarve(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            CavePosition position,
            CaveVoxel voxel
    ) {
        if (voxel != CaveVoxel.CAVE_AIR && voxel != CaveVoxel.COMPATIBLE_FLUID) {
            return false;
        }
        int extent = Math.max(0, settings.throatRadius() - 1);
        long deltaX = (long) position.x() - source.entry().x();
        long deltaZ = (long) position.z() - source.entry().z();
        return position.y() >= source.entry().y() - extent
                && position.y() <= source.entry().y()
                && deltaX * deltaX + deltaZ * deltaZ <= (long) extent * extent
                && view.isOpenToSurface(position);
    }

    private boolean isGeneratedInletOpening(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            CavePosition position
    ) {
        int radius = Math.max(1, settings.throatRadius());
        long deltaX = (long) position.x() - source.entry().x();
        long deltaZ = (long) position.z() - source.entry().z();
        return position.y() >= source.entry().y()
                && position.y() <= source.entry().y() + 1
                && deltaX * deltaX + deltaZ * deltaZ < (long) radius * radius
                && view.isOpenToSurface(position);
    }

    private HydrologyCaveRejection validateSource(HydrologyCaveSource source) {
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

    private HydrologyCaveRejection validateBounds(
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

    private HydrologyCaveRejection rejectionForTarget(
            CaveVoxel voxel,
            HydrologyCavePlannerSettings settings
    ) {
        HydrologyCaveRejection hazard = rejectionForHazard(voxel, settings);
        return hazard == HydrologyCaveRejection.NONE ? HydrologyCaveRejection.NO_CAVE_TARGET : hazard;
    }

    private HydrologyCaveRejection rejectionForHazard(
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

    private boolean isFluidReachable(CaveVoxel voxel, HydrologyCavePlannerSettings settings) {
        return voxel == CaveVoxel.CAVE_AIR
                || (voxel == CaveVoxel.COMPATIBLE_FLUID
                && settings.existingFluidPolicy() != HydrologyCaveFluidPolicy.REJECT_EXISTING)
                || (voxel == CaveVoxel.INCOMPATIBLE_FLUID
                && settings.existingFluidPolicy() == HydrologyCaveFluidPolicy.REPLACE_CONTAINED);
    }

    private CaveVoxel voxelAt(CaveVoxelView view, CavePosition position) {
        return Objects.requireNonNull(view.voxelAt(position));
    }

    private OptionalLong findWinningSourceId(
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

    private HydrologyCavePlan accepted(
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

    private HydrologyCavePlan accepted(
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

    private HydrologyCavePlan acceptedDecision(HydrologyCaveSource source) {
        return new HydrologyCavePlan(
                source,
                HydrologyCaveRejection.NONE,
                Map.of(),
                Map.of(),
                OptionalLong.empty()
        );
    }

    private HydrologyCavePlan rejected(HydrologyCaveSource source, HydrologyCaveRejection rejection) {
        return new HydrologyCavePlan(
                source,
                rejection,
                Map.of(),
                Map.of(),
                OptionalLong.empty()
        );
    }

    private HydrologyCavePlan rejectedOverlap(HydrologyCaveSource source, long winnerSourceId) {
        return new HydrologyCavePlan(
                source,
                HydrologyCaveRejection.OVERLAPPING_SOURCE,
                Map.of(),
                Map.of(),
                OptionalLong.of(winnerSourceId)
        );
    }

    private record PathResult(List<CavePosition> positions, HydrologyCaveRejection rejection) {
        private static PathResult accepted(List<CavePosition> positions) {
            return new PathResult(List.copyOf(positions), HydrologyCaveRejection.NONE);
        }

        private static PathResult rejected(HydrologyCaveRejection rejection) {
            return new PathResult(List.of(), rejection);
        }
    }

    private record IndependentDecision(
            HydrologyCaveRejection rejection,
            List<CavePosition> guards
    ) {
        private static IndependentDecision accepted(List<CavePosition> guards) {
            return new IndependentDecision(HydrologyCaveRejection.NONE, List.copyOf(guards));
        }

        private static IndependentDecision rejected(HydrologyCaveRejection rejection) {
            return new IndependentDecision(rejection, List.of());
        }
    }

    private record ComponentResult(Set<CavePosition> positions, HydrologyCaveRejection rejection) {
        private static ComponentResult accepted(Set<CavePosition> positions) {
            return new ComponentResult(
                    Collections.unmodifiableSet(new LinkedHashSet<>(positions)),
                    HydrologyCaveRejection.NONE
            );
        }

        private static ComponentResult rejected(HydrologyCaveRejection rejection) {
            return new ComponentResult(Set.of(), rejection);
        }
    }

    private record BoundaryResult(Set<CavePosition> sealGuards, HydrologyCaveRejection rejection) {
        private static BoundaryResult accepted(Set<CavePosition> sealGuards) {
            return new BoundaryResult(
                    Collections.unmodifiableSet(new LinkedHashSet<>(sealGuards)),
                    HydrologyCaveRejection.NONE
            );
        }

        private static BoundaryResult rejected(HydrologyCaveRejection rejection) {
            return new BoundaryResult(Set.of(), rejection);
        }
    }

    private record GrottoResult(Set<CavePosition> positions, HydrologyCaveRejection rejection) {
        private static GrottoResult accepted(Set<CavePosition> positions) {
            return new GrottoResult(
                    Collections.unmodifiableSet(new LinkedHashSet<>(positions)),
                    HydrologyCaveRejection.NONE
            );
        }

        private static GrottoResult rejected(HydrologyCaveRejection rejection) {
            return new GrottoResult(Set.of(), rejection);
        }
    }

    private record CaveClaimGroup(
            HydrologyCaveCandidate candidate,
            Map<CavePosition, HydrologyCaveAction> actions
    ) {
    }
}
