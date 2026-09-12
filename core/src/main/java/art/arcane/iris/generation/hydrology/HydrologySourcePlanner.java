package art.arcane.iris.generation.hydrology;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.function.IntPredicate;
import art.arcane.iris.generation.hydrology.policy.SurfaceRiverPolicy;

final class HydrologySourcePlanner {
    private final HydrologyPlanner planner;

    HydrologySourcePlanner(HydrologyPlanner planner) {
        this.planner = planner;
    }

    static final long EDGE_SALT = 0x45444745L;
    static final long SURFACE_SOURCE_SALT = 0x53555246414345L;
    static final long UNDERGROUND_SOURCE_SALT = 0x554e444552L;
    static final long DIAGNOSTIC_SALT = 0x444941474e4f5354L;
    static final int OPTIONAL_SOURCE_REJECTIONS_PER_TARGET = 4;
    static final int SURFACE_OPTIONAL_SOURCE_REJECTIONS_PER_TARGET = 8;

    static final List<HydrologyGridOffset> ROUTING_OFFSETS = List.of(
            new HydrologyGridOffset(-1, 0),
            new HydrologyGridOffset(1, 0),
            new HydrologyGridOffset(0, -1),
            new HydrologyGridOffset(0, 1),
            new HydrologyGridOffset(-1, -1),
            new HydrologyGridOffset(-1, 1),
            new HydrologyGridOffset(1, -1),
            new HydrologyGridOffset(1, 1)
    );

    boolean hasRoutedSourceSearch() {
        HydrologyPlannerSettings.Source surface = planner.settings.surface().sources();
        if (planner.settings.surface().enabled() && surface.enabled()) {
            return true;
        }
        HydrologyPlannerSettings.Source underground = planner.settings.underground().sources();
        return planner.settings.underground().enabled()
                && underground.enabled();
    }

    List<HydrologyDiagnosticCandidate> uniqueDiagnostics(
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        LinkedHashMap<Long, HydrologyDiagnosticCandidate> unique = new LinkedHashMap<>();
        for (HydrologyDiagnosticCandidate diagnostic : diagnostics) {
            unique.putIfAbsent(diagnostic.id(), diagnostic);
        }
        return List.copyOf(unique.values());
    }

    HydrologyRoutingPlan buildRouting(
            HydrologySampledGrid grid,
            List<OutletCandidate> outlets,
            boolean surface
    ) {
        // A surface river never climbs: any rise along its route becomes a cut of at least that rise,
        // so lattice edges that rise more than a small saddle are not drainage at all.
        int nodeCount = grid.nodes().size();
        double[] potential = new double[nodeCount];
        int[] parent = new int[nodeCount];
        int[] outletIndex = new int[nodeCount];
        int[] minimumNeighborHeights = new int[nodeCount];
        for (HydrologyGridNode node : grid.nodes()) {
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
            HydrologyGridNode downstream = grid.node(currentEntry.nodeIndex());
            for (HydrologyGridOffset offset : ROUTING_OFFSETS) {
                HydrologyGridNode upstream = grid.nodeAt(downstream.gridX() + offset.x(), downstream.gridZ() + offset.z());
                if (upstream == null || upstream.terrain().ocean() || !upstream.terrain().transitAllowed()
                        || !upstream.terrain().drainsInto(downstream.terrain())
                        || surface && !HydrologySurfaceProfiles.sharesProfile(upstream.terrain(), downstream.terrain())) {
                    // Confined ground only drains into its own area, so every course that starts or
                    // arrives there keeps to that area up to and including its outlet.
                    continue;
                }
                if (surface && downstream.terrain().naturalHeight() - upstream.terrain().naturalHeight()
                        > maximumSurfaceEdgeRise(downstream.terrain())) {
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
        return new HydrologyRoutingPlan(
                potential,
                parent,
                outletIndex,
                routeLengths(parent, grid.width(), planner.settings.routing()),
                List.copyOf(outlets),
                false
        );
    }

    /**
     * Largest rise a surface drainage edge may take. Heads only fall, so a rise along a route becomes a cut
     * of at least that rise; the lattice may climb no more than the cut the valley solver still accepts.
     */
    int maximumSurfaceEdgeRise(HydrologyTerrainSample terrain) {
        int permitted = permittedSurfaceIncision(terrain) - planner.settings.surface().banks().sink() - planner.settings.surface().minimumDepth();
        return Math.max(1, permitted);
    }

    int minimumCourseLength(HydrologyTerrainSample source, boolean surface) {
        int fallback = planner.settings.routing().minimumCourseLength(surface);
        return surface ? source.surfacePolicy().minimumCourseLength(fallback) : fallback;
    }

    HydrologyRoutingPlan requireOrganicSurface(HydrologyRoutingPlan routing) {
        return new HydrologyRoutingPlan(
                routing.potential(),
                routing.parent(),
                routing.outletIndex(),
                routing.routeLengths(),
                routing.outlets(),
                true
        );
    }

    int permittedSurfaceIncision(HydrologyTerrainSample terrain) {
        return permittedSurfaceIncision(
                terrain.surfacePolicy().maximumIncision(planner.settings.surface().maximumIncision()),
                terrain.incisionMultiplier()
        );
    }

    static int permittedSurfaceIncision(int maximumIncision, double incisionMultiplier) {
        return Math.min(
                maximumIncision,
                (int) StrictMath.floor(maximumIncision * incisionMultiplier)
        );
    }

    double routeCost(
            HydrologyGridNode upstream,
            HydrologyGridNode downstream,
            HydrologyGridOffset offset,
            int valleyFloor
    ) {
        HydrologyPlannerSettings.Routing routing = planner.settings.routing();
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

    int minimumNeighborHeight(HydrologySampledGrid grid, HydrologyGridNode node) {
        int minimum = node.terrain().naturalHeight();
        for (HydrologyGridOffset offset : ROUTING_OFFSETS) {
            HydrologyGridNode neighbor = grid.nodeAt(node.gridX() + offset.x(), node.gridZ() + offset.z());
            if (neighbor != null && !neighbor.terrain().ocean()) {
                minimum = Math.min(minimum, neighbor.terrain().naturalHeight());
            }
        }
        return minimum;
    }

    SourceSelection selectSources(
            HydrologyTileKey key,
            HydrologySampledGrid grid,
            HydrologyRoutingPlan routing,
            boolean surface,
            boolean enforceGlobalSpacing,
            List<HydrologyDiagnosticCandidate> diagnostics,
            Map<HydrologyTileKey, SourceRoutingContext> routingContexts
    ) {
        HydrologyPlannerSettings.Source sourceSettings = surface
                ? planner.settings.surface().sources()
                : planner.settings.underground().sources();
        if (!sourceSettings.enabled()) {
            return SourceSelection.empty(surface);
        }
        long sourceSalt = surface ? SURFACE_SOURCE_SALT : UNDERGROUND_SOURCE_SALT;
        HydrologySurfaceBudgets surfaceBudgets = surface ? HydrologySurfaceBudgets.sample(grid, sourceSettings) : null;
        ArrayList<SourceCandidate> candidates = new ArrayList<>();
        boolean hasRequiredCandidate = false;
        for (HydrologyGridNode node : grid.nodes()) {
            if (!grid.owns(node.x(), node.z())) {
                continue;
            }
            HydrologyTerrainSample terrain = node.terrain();
            boolean allowed = surface ? terrain.surfaceSourceAllowed() : terrain.undergroundSourceAllowed();
            boolean required = surface ? terrain.surfaceSourceRequired() : terrain.undergroundSourceRequired();
            if (!allowed || surface && (terrain.naturalHeight() < sourceSettings.minimumElevation()
                    || terrain.surfacePolicy().sourceDensity() != null && terrain.surfacePolicy().sourceDensity() == 0D)) {
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
            if (!sourceOutletAllowed(node, grid, routing, surface)) {
                addSourceDiagnostic(node, surface, stable, HydrologyCandidateRejection.NO_LEGAL_OUTLET, diagnostics);
                continue;
            }
            if (!surface && planner.settings.underground().connectToExistingCaves() && !terrain.caveAvailable()) {
                addSourceDiagnostic(node, false, stable, HydrologyCandidateRejection.CAVE_CONTAINMENT, diagnostics);
                continue;
            }
            int routeLength = routing.routeLengths()[node.index()];
            if (routeLength <= 0 || routeLength > planner.settings.routing().maximumRouteLength()) {
                addSourceDiagnostic(node, surface, stable, HydrologyCandidateRejection.ROUTE_LIMIT, diagnostics);
                continue;
            }
            if (routeLength < minimumCourseLength(terrain, surface)) {
                addSourceDiagnostic(node, surface, stable, HydrologyCandidateRejection.COURSE_TOO_SHORT, diagnostics);
                continue;
            }
            // Elevation and route length share the same scale: 1000 route blocks count as 100 blocks of height.
            double score = (required ? 1.0E15D : 0D)
                    + weight * 1.0E9D
                    + (terrain.naturalHeight() + routeLength * 0.1D * planner.settings.routing().lengthPreference()) * 1.0E5D
                    + routing.potential()[node.index()]
                    + HydrologyHash.unit(stable);
            candidates.add(new SourceCandidate(node.index(), stable, score, required));
            hasRequiredCandidate |= required;
        }
        if (surfaceBudgets != null && surfaceBudgets.overridden()) {
            return selectLocalSurfaceSources(key, grid, routing, candidates, surfaceBudgets, enforceGlobalSpacing, routingContexts);
        }
        int target = expectedCount(sourceSettings.density(), HydrologyHash.mix(
                planner.worldSeed,
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
        int tributaries = surface ? planner.settings.routing().tributaries() : planner.settings.underground().tributaries();
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
                new int[]{admittedGuaranteed},
                maximumOptionalRejections
        );
    }

    private SourceSelection selectLocalSurfaceSources(
            HydrologyTileKey key,
            HydrologySampledGrid grid,
            HydrologyRoutingPlan routing,
            List<SourceCandidate> candidates,
            HydrologySurfaceBudgets budgets,
            boolean enforceGlobalSpacing,
            Map<HydrologyTileKey, SourceRoutingContext> routingContexts
    ) {
        candidates.sort(Comparator.comparing(SourceCandidate::required).reversed()
                .thenComparing(Comparator.comparingDouble(SourceCandidate::score).reversed())
                .thenComparingLong(SourceCandidate::stableId));
        prioritizeInlandSource(candidates, routing);
        List<HydrologySurfaceBudgets.Area> areas = budgets.areas();
        Map<SurfaceRiverPolicy.Budget, Integer> areaIndices = new HashMap<>();
        ArrayList<HashSet<Integer>> areaOutlets = new ArrayList<>(areas.size());
        for (int index = 0; index < areas.size(); index++) {
            areaIndices.put(areas.get(index).policy.budget(), index);
            areaOutlets.add(new HashSet<>());
        }
        int count = candidates.size();
        int[] candidateAreas = new int[count];
        int[] candidateOutlets = new int[count];
        int[] outletLimits = new int[count];
        int[] requiredCandidates = new int[areas.size()];
        for (int index = 0; index < count; index++) {
            HydrologyGridNode node = grid.node(candidates.get(index).nodeIndex());
            SurfaceRiverPolicy policy = node.terrain().surfacePolicy();
            int area = areaIndices.get(policy.budget());
            int outlet = routing.outletIndex()[node.index()];
            candidateAreas[index] = area;
            candidateOutlets[index] = outlet;
            outletLimits[index] = 1 + policy.tributaries(planner.settings.routing().tributaries());
            areaOutlets.get(area).add(outlet);
            if (candidates.get(index).required()) {
                requiredCandidates[area]++;
            }
        }
        int[] areaLimits = new int[areas.size()];
        int[] requiredMinimums = new int[areas.size()];
        int target = 0;
        for (int index = 0; index < areas.size(); index++) {
            requiredMinimums[index] = Math.min(requiredCandidates[index], Math.max(1,
                    planner.settings.surface().sources().minimumPerTile()));
            areaLimits[index] = Math.max(requiredMinimums[index],
                    budgets.sourceTarget(areas.get(index), areaOutlets.get(index).size(), planner, key));
            target = Math.addExact(target, areaLimits[index]);
        }
        boolean[] selected = new boolean[count];
        boolean[] spacingRejected = new boolean[count];
        boolean[] evaluated = new boolean[count];
        IntPredicate admitted = candidateIndex -> !enforceGlobalSpacing || globallyAdmittedSource(
                grid.node(candidates.get(candidateIndex).nodeIndex()), planner.settings.surface().sources(),
                SURFACE_SOURCE_SALT, true, routingContexts);
        ArrayList<Integer> selectedIndices = new ArrayList<>(Math.min(count, target));
        int[] selectedByArea = new int[areas.size()];
        int[] selectedRequiredByArea = new int[areas.size()];
        int[] selectedByOutlet = new int[routing.outlets().size()];
        for (int index = 0; index < count && selectedIndices.size() < target; index++) {
            int area = candidateAreas[index];
            int outlet = candidateOutlets[index];
            if (selectedByArea[area] >= areaLimits[area] || selectedByOutlet[outlet] >= outletLimits[index]) {
                continue;
            }
            evaluated[index] = true;
            spacingRejected[index] = !admitted.test(index);
            if (spacingRejected[index]) {
                continue;
            }
            selected[index] = true;
            selectedIndices.add(index);
            selectedByArea[area]++;
            selectedByOutlet[outlet]++;
            if (candidates.get(index).required()) {
                selectedRequiredByArea[area]++;
            }
        }
        for (int area = 0; area < areas.size(); area++) {
            requiredMinimums[area] = Math.min(requiredMinimums[area], selectedRequiredByArea[area]);
        }
        SourceAdmissionSelection admission = new SourceAdmissionSelection(target, selectedIndices, selected,
                spacingRejected, evaluated, admitted,
                new SourceAdmissionSelection.Quotas(candidateOutlets, outletLimits, candidateAreas, areaLimits));
        return new SourceSelection(true, candidates, admission, requiredMinimums,
                Math.max(SURFACE_OPTIONAL_SOURCE_REJECTIONS_PER_TARGET, target * SURFACE_OPTIONAL_SOURCE_REJECTIONS_PER_TARGET));
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

    SourceAdmissionSelection selectSourceAdmissionsByOutlet(
            List<SourceCandidate> candidates,
            HydrologyRoutingPlan routing,
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
                SourceAdmissionSelection.Quotas.uniform(outletIndices, target, maximumCoursesPerOutlet)
        );
    }

    void prioritizeInlandSource(List<SourceCandidate> candidates, HydrologyRoutingPlan routing) {
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

    boolean isInlandSource(SourceCandidate candidate, HydrologyRoutingPlan routing) {
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
                SourceAdmissionSelection.Quotas.uniform(new int[candidateCount], target, Integer.MAX_VALUE)
        );
    }

    boolean globallyAdmittedSource(
            HydrologyGridNode candidate,
            HydrologyPlannerSettings.Source sourceSettings,
            long sourceSalt,
            boolean surface,
            Map<HydrologyTileKey, SourceRoutingContext> routingContexts
    ) {
        int minimumSpacing = surface
                ? candidate.terrain().surfacePolicy().sourceSpacing(sourceSettings.minimumSpacing())
                : sourceSettings.minimumSpacing();
        boolean required = surface
                ? candidate.terrain().surfaceSourceRequired()
                : candidate.terrain().undergroundSourceRequired();
        if ((!surface && minimumSpacing <= 0) || required) {
            return true;
        }
        SourcePriority candidatePriority = sourcePriority(candidate.x(), candidate.z(), candidate.terrain(), sourceSalt, surface);
        int sampleSpacing = planner.settings.routing().sampleSpacing();
        int searchSpacing = surface ? Math.max(minimumSpacing, planner.settings.maximumSurfaceSourceSpacing()) : minimumSpacing;
        int latticeRadius = (int) StrictMath.ceil(searchSpacing / (double) sampleSpacing);
        for (int offsetZ = -latticeRadius; offsetZ <= latticeRadius; offsetZ++) {
            for (int offsetX = -latticeRadius; offsetX <= latticeRadius; offsetX++) {
                if (offsetX == 0 && offsetZ == 0) {
                    continue;
                }
                long deltaX = (long) offsetX * sampleSpacing;
                long deltaZ = (long) offsetZ * sampleSpacing;
                double separation = StrictMath.hypot(deltaX, deltaZ);
                if (separation >= searchSpacing) {
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
                HydrologyTileKey owner = HydrologyTileKey.fromBlock(x, z, planner.settings.routing().tileSize());
                SourceRoutingContext context = routingContexts.computeIfAbsent(owner, planner::sourceRoutingContext);
                HydrologyGridNode neighbor = context.grid().nodeAtWorld(x, z);
                if (neighbor == null) {
                    throw new IllegalStateException("Source coordinate is absent from its owner routing lattice.");
                }
                HydrologyTerrainSample terrain = neighbor.terrain();
                int pairSpacing = surface
                        ? Math.max(minimumSpacing, terrain.surfacePolicy().sourceSpacing(sourceSettings.minimumSpacing()))
                        : minimumSpacing;
                if (separation >= pairSpacing) {
                    continue;
                }
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

    SourceRoutingContext compileSourceRoutingContext(HydrologyTileKey key) {
        HydrologySampledGrid grid = planner.outletPlanner.sample(key);
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();
        List<OutletCandidate> surfaceOutlets = planner.outletPlanner.resolveOutlets(key, grid, true, diagnostics);
        HydrologyRoutingPlan surfaceRouting = buildRouting(grid, surfaceOutlets, true);
        List<OutletCandidate> undergroundOutlets = planner.settings.underground().enabled()
                ? planner.outletPlanner.resolveOutlets(key, grid, false, diagnostics)
                : surfaceOutlets;
        HydrologyRoutingPlan undergroundRouting = planner.settings.underground().enabled()
                ? buildRouting(grid, undergroundOutlets, false)
                : surfaceRouting;
        return new SourceRoutingContext(
                grid,
                surfaceRouting,
                undergroundRouting,
                List.copyOf(diagnostics)
        );
    }

    boolean routeViable(HydrologyGridNode node, SourceRoutingContext context, boolean surface) {
        HydrologyRoutingPlan routing = context.routing(surface);
        if (routing.outlets().isEmpty()
                || !Double.isFinite(routing.potential()[node.index()])
                || routing.parent()[node.index()] < 0
                || !sourceOutletAllowed(node, context.grid(), routing, surface)) {
            return false;
        }
        int routeLength = routing.routeLengths()[node.index()];
        return routeLength >= minimumCourseLength(node.terrain(), surface)
                && routeLength <= planner.settings.routing().maximumRouteLength();
    }

    boolean sourceOutletAllowed(HydrologyGridNode node, HydrologySampledGrid grid, HydrologyRoutingPlan routing, boolean surface) {
        int outletIndex = routing.outletIndex()[node.index()];
        if (outletIndex < 0 || outletIndex >= routing.outlets().size()) {
            return false;
        }
        OutletCandidate candidate = routing.outlets().get(outletIndex);
        if (surface && !HydrologySurfaceProfiles.sharesProfile(node.terrain(), grid.node(candidate.landIndex()).terrain())) {
            return false;
        }
        RiverOutlet outlet = candidate.outlet();
        return !surface
                || outlet.type() != HydrologyFeatureType.INLAND_GROTTO
                || planner.settings.outlets().surfaceSinkholesEnabled();
    }

    boolean rawSourceEligible(
            HydrologyTerrainSample terrain,
            HydrologyPlannerSettings.Source sourceSettings,
            boolean surface
    ) {
        if (terrain.ocean() || !terrain.transitAllowed()) {
            return false;
        }
        boolean allowed = surface ? terrain.surfaceSourceAllowed() : terrain.undergroundSourceAllowed();
        boolean required = surface ? terrain.surfaceSourceRequired() : terrain.undergroundSourceRequired();
        if (!allowed || surface && (terrain.naturalHeight() < sourceSettings.minimumElevation()
                || terrain.surfacePolicy().sourceDensity() != null && terrain.surfacePolicy().sourceDensity() == 0D)) {
            return false;
        }
        if (!surface && planner.settings.underground().connectToExistingCaves() && !terrain.caveAvailable()) {
            return false;
        }
        double weight = surface ? terrain.surfaceSourceWeight() : terrain.undergroundSourceWeight();
        return required || weight > 0D;
    }

    SourcePriority sourcePriority(
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

    long sourceStableId(long sourceSalt, int x, int z) {
        return HydrologyHash.mix(planner.worldSeed, sourceSalt, x, z);
    }

    int compareSourcePriority(SourcePriority first, SourcePriority second) {
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

    static void addSourceDiagnostic(
            HydrologyGridNode node,
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

    int expectedCount(double density, long stable) {
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

    int[] countContributions(List<Integer> sources, HydrologyRoutingPlan routing) {
        int[] counts = new int[routing.parent().length];
        for (int source : sources) {
            int current = source;
            int steps = 0;
            while (routing.parent()[current] >= 0 && steps++ < planner.settings.routing().maximumRouteNodes()) {
                counts[current]++;
                current = routing.parent()[current];
            }
        }
        return counts;
    }

    CompiledGraph compileGraph(
            HydrologyTileKey key,
            HydrologySampledGrid grid,
            HydrologyRoutingPlan routing,
            int[] surfaceContributions,
            int[] undergroundContributions,
            Map<Long, List<HydrologyPoint>> refinedEdges,
            boolean surface
    ) {
        HashSet<Integer> includedNodeIndices = new HashSet<>();
        ArrayList<DrainageEdge> edges = new ArrayList<>();
        HashMap<Integer, DrainageEdge> edgeByUpstream = new HashMap<>();
        for (HydrologyGridNode upstream : grid.nodes()) {
            int downstreamIndex = routing.parent()[upstream.index()];
            if (downstreamIndex < 0 || !Double.isFinite(routing.potential()[upstream.index()])) {
                continue;
            }
            boolean acceptedCourseEdge = surfaceContributions[upstream.index()] > 0
                    || undergroundContributions[upstream.index()] > 0;
            if (!acceptedCourseEdge) {
                continue;
            }
            HydrologyGridNode downstream = grid.node(downstreamIndex);
            RiverOutlet outlet = routing.outlets().get(routing.outletIndex()[upstream.index()]).outlet();
            long edgeId = HydrologyHash.mix(planner.worldSeed, EDGE_SALT, upstream.id(), downstream.id(), outlet.id());
            long refinementId = surface ? HydrologyHash.mix(edgeId, SURFACE_SOURCE_SALT) : edgeId;
            List<HydrologyPoint> centerline = refinedEdges.get(refinementId);
            int transverseCandidates = HydrologyRouteGeometry.ROUTE_TRANSVERSE_CANDIDATES;
            if (centerline == null) {
                HydrologyPoint upstreamAnchor = planner.routeGeometry.routeAnchor(upstream, surface);
                HydrologyPoint downstreamAnchor = planner.routeGeometry.routeAnchor(downstream, surface);
                HydrologyPoint continuation = planner.routeGeometry.edgeContinuation(grid, routing, downstream, outlet, surface);
                RefinedEdgeKey refinedEdgeKey = new RefinedEdgeKey(
                        upstream.id(),
                        downstream.id(),
                        upstreamAnchor.x(),
                        upstreamAnchor.z(),
                        downstreamAnchor.x(),
                        downstreamAnchor.z(),
                        continuation.x(),
                        continuation.z(),
                        transverseCandidates,
                        surface
                );
                centerline = planner.refinedEdgeCache.get(
                        refinedEdgeKey,
                        ignored -> planner.routeGeometry.refineEdge(
                                upstream.id(),
                                downstream.id(),
                                upstreamAnchor,
                                downstreamAnchor,
                                continuation,
                                transverseCandidates,
                                surface
                        )
                );
                refinedEdges.put(refinementId, centerline);
            }
            if (centerline.isEmpty()) {
                HydrologyPoint upstreamAnchor = planner.routeGeometry.routeAnchor(upstream, surface);
                HydrologyPoint downstreamAnchor = planner.routeGeometry.routeAnchor(downstream, surface);
                HydrologyPoint continuation = planner.routeGeometry.edgeContinuation(grid, routing, downstream, outlet, surface);
                RefinedEdgeKey refinedEdgeKey = new RefinedEdgeKey(
                        upstream.id(),
                        downstream.id(),
                        upstreamAnchor.x(),
                        upstreamAnchor.z(),
                        downstreamAnchor.x(),
                        downstreamAnchor.z(),
                        continuation.x(),
                        continuation.z(),
                        HydrologyRouteGeometry.ROUTE_FALLBACK_TRANSVERSE_CANDIDATES,
                        surface
                );
                centerline = planner.refinedEdgeCache.get(
                        refinedEdgeKey,
                        ignored -> planner.routeGeometry.refineEdge(
                                upstream.id(),
                                downstream.id(),
                                upstreamAnchor,
                                downstreamAnchor,
                                continuation,
                                HydrologyRouteGeometry.ROUTE_FALLBACK_TRANSVERSE_CANDIDATES,
                                surface
                        )
                );
                refinedEdges.put(refinementId, centerline);
            }
            if (centerline.isEmpty()) {
                centerline = planner.routeGeometry.constrainedTerrainFallbackEdge(
                        planner.routeGeometry.routeAnchor(upstream, surface),
                        planner.routeGeometry.routeAnchor(downstream, surface)
                );
                refinedEdges.put(refinementId, centerline);
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
        for (HydrologyGridNode node : grid.nodes()) {
            if (!includedNodeIndices.contains(node.index())) {
                continue;
            }
            int outletIndex = routing.outletIndex()[node.index()];
            RiverOutlet outlet = routing.outlets().get(outletIndex).outlet();
            HydrologyPoint anchor = planner.routeGeometry.routeAnchor(node, surface);
            HydrologyTerrainSample terrain = Objects.requireNonNull(
                    planner.sampleLandBasis(anchor.x(), anchor.z()),
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

    CompiledGraph mergeGraphs(CompiledGraph first, CompiledGraph second) {
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

    record SourcePriority(
            boolean required,
            double weight,
            int naturalHeight,
            long stableId,
            int x,
            int z
    ) {
    }
}
