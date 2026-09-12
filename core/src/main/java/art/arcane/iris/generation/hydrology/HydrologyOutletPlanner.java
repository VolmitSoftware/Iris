package art.arcane.iris.generation.hydrology;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Map;
import art.arcane.iris.generation.hydrology.policy.SurfaceRiverPolicy;

final class HydrologyOutletPlanner {
    private final HydrologyPlanner planner;

    HydrologyOutletPlanner(HydrologyPlanner planner) {
        this.planner = planner;
    }

    static final long NODE_SALT = 0x4e4f4445L;
    static final long OUTLET_SALT = 0x4f55544c4554L;

    /** Sea outlet types in the order the coastal budget takes them, one of each type per turn. */
    static final List<HydrologyFeatureType> COASTAL_OUTLET_TYPES = List.of(
            HydrologyFeatureType.MOUTH,
            HydrologyFeatureType.COASTAL_GROTTO
    );

    static final List<HydrologyGridOffset> CARDINAL_OFFSETS = List.of(
            new HydrologyGridOffset(-1, 0),
            new HydrologyGridOffset(1, 0),
            new HydrologyGridOffset(0, -1),
            new HydrologyGridOffset(0, 1)
    );

    HydrologySampledGrid sample(HydrologyTileKey key) {
        HydrologyPlannerSettings.Routing routing = planner.settings.routing();
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
                planner.routingSampler.sampleGrid(gridRequest),
                "Hydrology routing terrain sampler returned null"
        );
        if (terrainSamples.length != nodeCount) {
            throw new IllegalStateException(
                    "Hydrology routing terrain sampler returned " + terrainSamples.length
                            + " samples for a " + width + " by " + width + " grid"
            );
        }
        ArrayList<HydrologyGridNode> nodes = new ArrayList<>(nodeCount);
        for (int gridZ = 0; gridZ < width; gridZ++) {
            int z = minimumZ + gridZ * routing.sampleSpacing();
            for (int gridX = 0; gridX < width; gridX++) {
                int x = minimumX + gridX * routing.sampleSpacing();
                int index = gridZ * width + gridX;
                HydrologyTerrainSample terrain = Objects.requireNonNull(
                        terrainSamples[index],
                        "Hydrology routing terrain sampler returned null at " + x + "," + z
                );
                long id = HydrologyHash.mix(planner.worldSeed, NODE_SALT, x, z);
                nodes.add(new HydrologyGridNode(index, gridX, gridZ, x, z, id, terrain));
            }
        }
        return new HydrologySampledGrid(
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

    List<OutletCandidate> resolveOutlets(
            HydrologyTileKey key,
            HydrologySampledGrid grid,
            boolean surface,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        ArrayList<OutletCandidate> oceanCandidates = oceanOutletCandidates(grid, surface);
        if (!surface && planner.settings.outlets().inlandGrotto().enabled()) {
            addOutletLevelDiagnostics(oceanCandidates, diagnostics);
        }
        sortOutletCandidates(key, grid, surface, oceanCandidates);
        // Sea outlets and inland outlets are budgeted separately: a coast never starves the
        // sinkholes behind it and a sinkhole never displaces a mouth.
        List<OutletCandidate> selectedOcean = surface
                ? limitSurfaceOutlets(key, grid, oceanCandidates, true)
                : limitOutletsByType(oceanCandidates, planner.settings.outlets().maximumCoastalPerTile());
        addRejectedOutlets(oceanCandidates, selectedOcean, diagnostics);
        if (!planner.settings.outlets().inlandGrotto().enabled()) {
            return selectedOcean;
        }
        if (surface) {
            HydrologySurfaceBudgets budgets = HydrologySurfaceBudgets.sample(grid, planner.settings.surface().sources());
            if (budgets.overridden()) {
                oceanCandidates.removeIf(candidate -> !surfaceOutletHasBudget(key, grid, budgets, candidate, true));
            }
        }
        boolean[] oceanReachable = outletReachable(grid, oceanCandidates, surface);
        ArrayList<OutletCandidate> inlandCandidates = inlandOutletCandidates(grid, oceanReachable);
        sortInlandOutletCandidates(grid, inlandCandidates);
        List<OutletCandidate> selectedInland = styledInlandOutlets(
                grid,
                surface ? limitSurfaceOutlets(key, grid, inlandCandidates, false)
                        : limitOutlets(inlandCandidates, planner.settings.outlets().maximumPerTile()),
                surface
        );
        addRejectedOutlets(inlandCandidates, selectedInland, diagnostics);
        ArrayList<OutletCandidate> selected = new ArrayList<>(selectedOcean.size() + selectedInland.size());
        selected.addAll(selectedOcean);
        selected.addAll(selectedInland);
        return List.copyOf(selected);
    }

    ArrayList<OutletCandidate> oceanOutletCandidates(HydrologySampledGrid grid, boolean surface) {
        ArrayList<OutletCandidate> oceanCandidates = new ArrayList<>();
        for (HydrologyGridNode land : grid.nodes()) {
            if (land.terrain().ocean() || !land.terrain().transitAllowed() || !land.terrain().outletAllowed()) {
                continue;
            }
            HydrologyGridNode ocean = firstOceanNeighbor(grid, land, surface);
            if (ocean == null || !land.terrain().drainsInto(ocean.terrain())) {
                // A confined shore only reaches a sea that belongs to its own area.
                continue;
            }
            HydrologyOceanBoundaryRefiner.Result boundary = refineOceanBoundary(land, ocean);
            if (boundary == null) {
                continue;
            }
            HydrologyTerrainSample receiving = planner.sampleBasisWithoutSlope(boundary.oceanPoint().x(), boundary.oceanPoint().z());
            if (receiving == null || !boundary.landwardTerrain().drainsInto(receiving)
                    || !boundary.landwardTerrain().transitAllowed()
                    || !boundary.landwardTerrain().outletAllowed()
                    || surface && !HydrologySurfaceProfiles.sharesProfile(boundary.landwardTerrain(), receiving)) {
                continue;
            }
            HydrologyFeatureType type = coastalOutletType(boundary.landwardTerrain());
            if (type == null) {
                continue;
            }
            long outletId = HydrologyHash.mix(planner.worldSeed, OUTLET_SALT, land.id(), ocean.id(), type.ordinal());
            int seaLevel = planner.settingsSeaLevel(ocean.terrain());
            RiverOutlet outlet = new RiverOutlet(
                    outletId,
                    type,
                    land.id(),
                    HydrologyPlanner.withY(boundary.landwardPoint(), seaLevel),
                    HydrologyPlanner.withY(boundary.oceanPoint(), seaLevel),
                    seaLevel,
                    true
            );
            oceanCandidates.add(new OutletCandidate(land.index(), ocean.index(), outlet));
        }
        return oceanCandidates;
    }

    List<OutletCandidate> resolveSurfaceFallbackOutlets(HydrologySampledGrid grid) {
        ArrayList<OutletCandidate> oceanCandidates = oceanOutletCandidates(grid, true);
        ArrayList<OutletCandidate> inlandCandidates = new ArrayList<>();
        if (planner.settings.outlets().surfaceSinkholesEnabled()
                && planner.settings.outlets().inlandGrotto().enabled()) {
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
        HydrologySurfaceBudgets budgets = HydrologySurfaceBudgets.sample(grid, planner.settings.surface().sources());
        if (budgets.overridden()) {
            HydrologyTileKey key = HydrologyTileKey.fromBlock(grid.ownerMinimumX(), grid.ownerMinimumZ(), grid.ownerSize());
            oceanCandidates.removeIf(candidate -> !surfaceOutletHasBudget(key, grid, budgets, candidate, true));
            inlandCandidates.removeIf(candidate -> !surfaceOutletHasBudget(key, grid, budgets, candidate, false));
        }
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
                HydrologySourcePlanner.OPTIONAL_SOURCE_REJECTIONS_PER_TARGET,
                Math.multiplyExact(
                        Math.max(planner.settings.outlets().maximumPerTile(), planner.settings.outlets().maximumCoastalPerTile()),
                        16
                )
        );
        maximumTrials = Math.min(16, maximumTrials);
        return limitOutlets(candidates, maximumTrials);
    }

    private boolean surfaceOutletHasBudget(HydrologyTileKey key, HydrologySampledGrid grid,
                                           HydrologySurfaceBudgets budgets, OutletCandidate candidate, boolean coastal) {
        HydrologySurfaceBudgets.Area area = budgets.area(grid.node(candidate.landIndex()).terrain().surfacePolicy());
        return area != null && budgets.outletTarget(area, coastal, planner, key) > 0;
    }

    List<OutletCandidate> limitSurfaceOutlets(HydrologyTileKey key, HydrologySampledGrid grid,
                                             List<OutletCandidate> candidates, boolean coastal) {
        HydrologySurfaceBudgets budgets = HydrologySurfaceBudgets.sample(grid, planner.settings.surface().sources());
        if (!budgets.overridden()) {
            return coastal ? limitOutletsByType(candidates, planner.settings.outlets().maximumCoastalPerTile())
                    : limitOutlets(candidates, planner.settings.outlets().maximumPerTile());
        }
        LinkedHashMap<SurfaceRiverPolicy.Budget, ArrayList<OutletCandidate>> grouped = new LinkedHashMap<>();
        for (OutletCandidate candidate : candidates) {
            SurfaceRiverPolicy policy = grid.node(candidate.landIndex()).terrain().surfacePolicy();
            if (budgets.area(policy) != null) {
                grouped.computeIfAbsent(policy.budget(), ignored -> new ArrayList<>()).add(candidate);
            }
        }
        ArrayList<OutletCandidate> selected = new ArrayList<>();
        long spacingSquared = outletSpacingSquared();
        for (Map.Entry<SurfaceRiverPolicy.Budget, ArrayList<OutletCandidate>> entry : grouped.entrySet()) {
            int target = budgets.outletTarget(budgets.area(entry.getKey()), coastal, planner, key);
            List<OutletCandidate> local = coastal ? limitOutletsByType(entry.getValue(), target)
                    : limitOutlets(entry.getValue(), target);
            for (OutletCandidate candidate : local) {
                if (!withinOutletSpacing(candidate, selected, spacingSquared)) {
                    selected.add(candidate);
                }
            }
        }
        return List.copyOf(selected);
    }

    ArrayList<OutletCandidate> inlandOutletCandidates(
            HydrologySampledGrid grid,
            boolean[] oceanReachable
    ) {
        ArrayList<OutletCandidate> inlandCandidates = new ArrayList<>();
        for (HydrologyGridNode node : grid.nodes()) {
            if (!grid.owns(node.x(), node.z())
                    || node.terrain().ocean() || !node.terrain().caveAvailable()
                    || !node.terrain().transitAllowed() || !node.terrain().outletAllowed()
                    || oceanReachable[node.index()]) {
                continue;
            }
            InlandConnection connection = inlandConnection(node);
            int poolY = HydrologyPlanner.clamp(
                    connection.terrain().caveFluidY(),
                    planner.settings.underground().minimumFluidY(),
                    planner.settings.underground().maximumFluidY()
            );
            if (planner.settings.outlets().surfaceSinkholesEnabled()
                    && node.terrain().naturalHeight() - planner.settings.surface().banks().sink() <= poolY) {
                continue;
            }
            long outletId = HydrologyHash.mix(
                    planner.worldSeed,
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
                    HydrologyPlanner.withY(connection.point(), poolY),
                    planner.settingsSeaLevel(node.terrain()),
                    false
            );
            inlandCandidates.add(new OutletCandidate(node.index(), -1, outlet));
        }
        return inlandCandidates;
    }

    void sortInlandOutletCandidates(
            HydrologySampledGrid grid,
            List<OutletCandidate> inlandCandidates
    ) {
        inlandCandidates.sort(Comparator
                .comparing((OutletCandidate candidate) -> !isDrainageBasin(grid, candidate.landIndex()))
                .thenComparingInt((OutletCandidate candidate) -> grid.node(candidate.landIndex()).terrain().naturalHeight())
                .thenComparingLong((OutletCandidate candidate) -> candidate.outlet().id()));
    }

    void sortSurfaceFallbackOutletCandidates(
            HydrologySampledGrid grid,
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

    boolean isDrainageBasin(HydrologySampledGrid grid, int nodeIndex) {
        HydrologyGridNode node = grid.node(nodeIndex);
        int height = node.terrain().naturalHeight();
        for (HydrologyGridOffset offset : HydrologySourcePlanner.ROUTING_OFFSETS) {
            HydrologyGridNode neighbor = grid.nodeAt(node.gridX() + offset.x(), node.gridZ() + offset.z());
            if (neighbor != null
                    && !neighbor.terrain().ocean()
                    && neighbor.terrain().transitAllowed()
                    && neighbor.terrain().naturalHeight() < height) {
                return false;
            }
        }
        return true;
    }

    boolean[] outletReachable(HydrologySampledGrid grid, List<OutletCandidate> outlets, boolean surface) {
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
            HydrologyGridNode node = grid.node(queue[readIndex++]);
            for (HydrologyGridOffset offset : HydrologySourcePlanner.ROUTING_OFFSETS) {
                HydrologyGridNode neighbor = grid.nodeAt(node.gridX() + offset.x(), node.gridZ() + offset.z());
                if (neighbor == null || reachable[neighbor.index()] || neighbor.terrain().ocean()
                        || !neighbor.terrain().transitAllowed()
                        || surface && !HydrologySurfaceProfiles.sharesProfile(neighbor.terrain(), node.terrain())
                        || surface && node.terrain().naturalHeight() - neighbor.terrain().naturalHeight()
                        > planner.sourcePlanner.maximumSurfaceEdgeRise(node.terrain())) {
                    continue;
                }
                reachable[neighbor.index()] = true;
                queue[writeIndex++] = neighbor.index();
            }
        }
        return reachable;
    }

    List<OutletCandidate> styledInlandOutlets(
            HydrologySampledGrid grid,
            List<OutletCandidate> candidates,
            boolean surface
    ) {
        ArrayList<OutletCandidate> styled = new ArrayList<>(candidates.size());
        for (OutletCandidate candidate : candidates) {
            HydrologyGridNode node = grid.node(candidate.landIndex());
            RiverOutlet outlet = candidate.outlet();
            HydrologyPoint landward = planner.routeGeometry.routeAnchor(node, surface);
            HydrologyTerrainSample landwardTerrain = Objects.requireNonNull(
                    planner.sampleDetailed(landward.x(), landward.z()),
                    "Hydrology inland outlet anchor left sampled terrain"
            );
            if (surface && (!HydrologySurfaceProfiles.sharesProfile(node.terrain(), landwardTerrain)
                    || !node.terrain().surfacePolicy().areaKey().equals(landwardTerrain.surfacePolicy().areaKey()))) {
                continue;
            }
            int connectionOffsetX = Integer.compare(outlet.connectionPoint().x(), outlet.landwardPoint().x());
            int connectionOffsetZ = Integer.compare(outlet.connectionPoint().z(), outlet.landwardPoint().z());
            HydrologyPoint connection = new HydrologyPoint(
                    landward.x() + connectionOffsetX,
                    outlet.connectionPoint().y(),
                    landward.z() + connectionOffsetZ
            );
            int poolY = planner.sampleGeometry(
                    HydrologyGeometrySampler.Field.UNDERGROUND_FLUID_LEVEL,
                    "",
                    connection.x(),
                    connection.z(),
                    0L,
                    planner.settings.underground().minimumFluidY(),
                    planner.settings.underground().maximumFluidY()
            );
            if (surface && planner.settings.outlets().surfaceSinkholesEnabled()) {
                poolY = Math.min(
                        poolY,
                        landwardTerrain.naturalHeight() - planner.settings.surface().banks().sink() - 1
                );
                poolY = Math.max(
                        poolY,
                        landwardTerrain.naturalHeight() - planner.settings.routing().sampleSpacing()
                );
            }
            styled.add(new OutletCandidate(
                    candidate.landIndex(),
                    candidate.oceanIndex(),
                    new RiverOutlet(
                            outlet.id(),
                            outlet.type(),
                            outlet.drainageNodeId(),
                            HydrologyPlanner.withY(landward, landwardTerrain.naturalHeight()),
                            HydrologyPlanner.withY(connection, poolY),
                            outlet.seaLevel(),
                            outlet.directOcean()
                    )
            ));
        }
        return List.copyOf(styled);
    }

    InlandConnection inlandConnection(HydrologyGridNode node) {
        HydrologyGridOffset offset = CARDINAL_OFFSETS.getFirst();
        long selectedTie = inlandConnectionTie(node, offset);
        for (int index = 1; index < CARDINAL_OFFSETS.size(); index++) {
            HydrologyGridOffset candidate = CARDINAL_OFFSETS.get(index);
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

    long inlandConnectionTie(HydrologyGridNode node, HydrologyGridOffset offset) {
        return HydrologyHash.mix(
                planner.worldSeed,
                OUTLET_SALT,
                node.id(),
                node.x() + offset.x(),
                node.z() + offset.z()
        );
    }

    HydrologyGridNode firstOceanNeighbor(HydrologySampledGrid grid, HydrologyGridNode land, boolean surface) {
        ArrayList<HydrologyGridNode> oceans = new ArrayList<>();
        for (HydrologyGridOffset offset : CARDINAL_OFFSETS) {
            HydrologyGridNode neighbor = grid.nodeAt(land.gridX() + offset.x(), land.gridZ() + offset.z());
            if (neighbor != null && neighbor.terrain().ocean()
                    && (!surface || HydrologySurfaceProfiles.sharesProfile(land.terrain(), neighbor.terrain()))) {
                oceans.add(neighbor);
            }
        }
        oceans.sort(Comparator.comparingLong(HydrologyGridNode::id));
        return oceans.isEmpty() ? null : oceans.getFirst();
    }

    HydrologyOceanBoundaryRefiner.Result refineOceanBoundary(HydrologyGridNode land, HydrologyGridNode ocean) {
        List<HydrologyPoint> crossing = planner.segments.line(land.naturalPoint(), ocean.naturalPoint(), 1);
        return HydrologyOceanBoundaryRefiner.refine(crossing, planner.sampler, planner.routingSampler, planner.settings.seaLevel());
    }

    HydrologyFeatureType coastalOutletType(HydrologyTerrainSample land) {
        HydrologyPlannerSettings.Outlets outlets = planner.settings.outlets();
        // A zero slope factor turns the slope rule off, so only the coast's height over the sea decides.
        boolean cliff = land.naturalHeight() - planner.settingsSeaLevel(land)
                >= outlets.coastalCliffMinimumHeight()
                || outlets.coastalCliffSlopeFactor() > 0D
                && land.slope() >= outlets.coastalCliffMinimumHeight() * outlets.coastalCliffSlopeFactor();
        if (cliff && outlets.coastalGrotto().enabled()) {
            return HydrologyFeatureType.COASTAL_GROTTO;
        }
        return outlets.oceanEnabled() ? HydrologyFeatureType.MOUTH : null;
    }

    Comparator<OutletCandidate> outletComparator(HydrologyTileKey key) {
        long tileHash = HydrologyHash.mix(planner.worldSeed, key.tileX(), key.tileZ(), OUTLET_SALT);
        return Comparator.comparingLong((OutletCandidate candidate) -> HydrologyHash.mix(tileHash, candidate.outlet().id()));
    }

    void sortOutletCandidates(
            HydrologyTileKey key,
            HydrologySampledGrid grid,
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

    int ownedOutletRank(HydrologySampledGrid grid, OutletCandidate candidate) {
        HydrologyGridNode land = grid.node(candidate.landIndex());
        return grid.owns(land.x(), land.z()) ? 0 : 1;
    }

    int surfaceOutletPriority(OutletCandidate candidate) {
        return switch (candidate.outlet().type()) {
            case MOUTH -> 0;
            case COASTAL_GROTTO -> 1;
            case INLAND_GROTTO -> 2;
            default -> 3;
        };
    }

    long surfaceOutletCapacity(HydrologySampledGrid grid, int outletNodeIndex) {
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
            HydrologyGridNode downstream = grid.node(queue[readIndex++]);
            if (grid.owns(downstream.x(), downstream.z())
                    && downstream.terrain().surfaceSourceAllowed()
                    && downstream.terrain().naturalHeight() >= planner.settings.surface().sources().minimumElevation()
                    && distances[downstream.index()]
                    >= planner.sourcePlanner.minimumCourseLength(downstream.terrain(), true)) {
                capacity = Math.addExact(
                        capacity,
                        1_000_000L
                                + distances[downstream.index()]
                                + Math.max(0, downstream.terrain().naturalHeight() - planner.settings.seaLevel())
                );
            }
            for (HydrologyGridOffset offset : HydrologySourcePlanner.ROUTING_OFFSETS) {
                HydrologyGridNode upstream = grid.nodeAt(
                        downstream.gridX() + offset.x(),
                        downstream.gridZ() + offset.z()
                );
                if (upstream == null
                        || visited[upstream.index()]
                        || upstream.terrain().ocean()
                        || !upstream.terrain().transitAllowed()
                        || !HydrologySurfaceProfiles.sharesProfile(upstream.terrain(), downstream.terrain())) {
                    continue;
                }
                visited[upstream.index()] = true;
                distances[upstream.index()] = Math.addExact(
                        distances[downstream.index()],
                        planner.settings.routing().sampleSpacing()
                );
                queue[writeIndex++] = upstream.index();
            }
        }
        return capacity;
    }

    List<OutletCandidate> limitOutlets(List<OutletCandidate> candidates, int maximum) {
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
    List<OutletCandidate> limitOutletsByType(List<OutletCandidate> candidates, int maximum) {
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

    long outletSpacingSquared() {
        int minimumSpacing = planner.settings.routing().sampleSpacing() * 2;
        return (long) minimumSpacing * minimumSpacing;
    }

    boolean withinOutletSpacing(
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

    void addRejectedOutlets(
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
                        HydrologyHash.mix(candidate.outlet().id(), HydrologySourcePlanner.DIAGNOSTIC_SALT),
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
    void addOutletLevelDiagnostics(
            List<OutletCandidate> candidates,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        int maximumFluidY = planner.settings.underground().maximumFluidY();
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
                            HydrologySourcePlanner.DIAGNOSTIC_SALT,
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

    record InlandConnection(HydrologyPoint point, HydrologyTerrainSample terrain) {
    }
}
