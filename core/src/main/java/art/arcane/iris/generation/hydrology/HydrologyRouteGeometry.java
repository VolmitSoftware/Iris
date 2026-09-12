package art.arcane.iris.generation.hydrology;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

final class HydrologyRouteGeometry {
    private final HydrologyPlanner planner;

    HydrologyRouteGeometry(HydrologyPlanner planner) {
        this.planner = planner;
    }

    static final double CROSS_DROP_WEIGHT = 3D;
    static final long ROUTE_ANCHOR_X_SALT = 0x52414e434858L;
    static final long ROUTE_ANCHOR_Z_SALT = 0x52414e43485aL;
    static final long ROUTE_WORM_PRIMARY_SALT = 0x52575052494dL;
    static final long ROUTE_WORM_DETAIL_SALT = 0x525744455441L;
    static final double ROUTE_ANCHOR_MAXIMUM_OFFSET_RATIO = 0.32D;
    static final double ROUTE_TANGENT_SCALE = 0.68D;
    static final int ROUTE_TRANSVERSE_CANDIDATES = 2;
    static final int ROUTE_FALLBACK_TRANSVERSE_CANDIDATES = 4;
    static final int SURFACE_COURSE_TRANSVERSE_CANDIDATES = 2;
    static final double SURFACE_COURSE_MAXIMUM_TURN_DEGREES = 50D;
    static final double SURFACE_COURSE_TURN_COST = 2.5D;
    static final int SURFACE_MINIMUM_NATURAL_BANK_RISE = 1;
    static final int MAXIMUM_SURFACE_BORE_STEP = 4;

    HydrologyPoint edgeContinuation(
            HydrologySampledGrid grid,
            HydrologyRoutingPlan routing,
            HydrologyGridNode downstream,
            RiverOutlet outlet,
            boolean surface
    ) {
        int continuationIndex = routing.parent()[downstream.index()];
        if (continuationIndex >= 0) {
            return routeAnchor(grid.node(continuationIndex), surface);
        }
        HydrologyPoint landward = outlet.landwardPoint();
        if (landward.x() != downstream.x() || landward.z() != downstream.z()) {
            return landward;
        }
        return outlet.connectionPoint();
    }

    HydrologyPoint routeAnchor(HydrologyGridNode node, boolean surface) {
        HydrologyPlanner.PlanningSamples samples = planner.planningSamples.get();
        HashMap<Long, HydrologyPoint> anchors = samples == null ? null
                : surface ? samples.surfaceRouteAnchors : samples.routeAnchors;
        if (samples != null) {
            HydrologyPoint cached = anchors.get(node.id());
            if (cached != null) {
                return cached;
            }
        }
        int spacing = planner.settings.routing().sampleSpacing();
        HydrologyPlannerSettings.Meanders meanders = planner.settings.geometry().meanders();
        double maximumOffsetRatio = Math.min(
                ROUTE_ANCHOR_MAXIMUM_OFFSET_RATIO,
                meanders.maximumOffsetRatio() * 0.625D
        );
        if (maximumOffsetRatio <= 0D) {
            return node.naturalPoint();
        }
        double primaryWavelength = meanders.primaryWavelength() * 5D;
        double detailWavelength = meanders.detailWavelength() * 8D;
        double primaryX = planner.routeAnchorX.noiseSigned(
                node.x() / primaryWavelength,
                node.z() / primaryWavelength
        );
        double primaryZ = planner.routeAnchorZ.noiseSigned(
                node.x() / primaryWavelength,
                node.z() / primaryWavelength
        );
        double detailX = planner.routeAnchorX.noiseSigned(
                node.x() / detailWavelength + 31.75D,
                node.z() / detailWavelength - 19.25D
        );
        double detailZ = planner.routeAnchorZ.noiseSigned(
                node.x() / detailWavelength - 27.5D,
                node.z() / detailWavelength + 43.5D
        );
        double vectorX = primaryX * 0.72D + detailX * 0.28D;
        double vectorZ = primaryZ * 0.72D + detailZ * 0.28D;
        double vectorLength = StrictMath.hypot(vectorX, vectorZ);
        if (vectorLength < 0.1D) {
            double angle = HydrologyHash.unit(HydrologyHash.mix(planner.worldSeed, node.id(), ROUTE_ANCHOR_X_SALT))
                    * StrictMath.PI * 2D;
            vectorX = StrictMath.cos(angle);
            vectorZ = StrictMath.sin(angle);
            vectorLength = 1D;
        }
        double maximumOffset = spacing * maximumOffsetRatio;
        double displacement = maximumOffset * (0.62D + Math.min(1D, vectorLength) * 0.38D);
        int desiredX = (int) StrictMath.round(node.x() + vectorX / vectorLength * displacement);
        int desiredZ = (int) StrictMath.round(node.z() + vectorZ / vectorLength * displacement);
        int searchRadius = Math.max(planner.settings.routing().refinementSpacing(), spacing / 5);
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
                maximumOffsetRatio,
                surface
        );
        for (int[] offset : offsets) {
            int x = desiredX + offset[0];
            int z = desiredZ + offset[1];
            double score = anchorScore(node, x, z, desiredX, desiredZ, maximumOffsetRatio, surface);
            if (score < selectedScore) {
                HydrologyTerrainSample terrain = planner.sampleLandBasis(x, z);
                selected = new HydrologyPoint(x, terrain.naturalHeight(), z);
                selectedScore = score;
            }
        }
        if (samples != null) {
            anchors.put(node.id(), selected);
        }
        return selected;
    }

    double anchorScore(
            HydrologyGridNode node,
            int x,
            int z,
            int desiredX,
            int desiredZ,
            double maximumOffsetRatio,
            boolean surface
    ) {
        double distanceFromNode = StrictMath.hypot(x - node.x(), z - node.z());
        if (distanceFromNode > planner.settings.routing().sampleSpacing() * maximumOffsetRatio) {
            return Double.POSITIVE_INFINITY;
        }
        HydrologyTerrainSample terrain = planner.sampleLandBasisWithoutSlope(x, z);
        if (terrain == null || !terrain.transitAllowed() || !withinConfines(terrain, node.terrain().confinesKey())) {
            return Double.POSITIVE_INFINITY;
        }
        if (surface) {
            int maximumDrop = Math.max(0, Math.min(planner.sourcePlanner.permittedSurfaceIncision(node.terrain()),
                    planner.sourcePlanner.permittedSurfaceIncision(terrain))
                    - planner.settings.surface().banks().sink() - planner.settings.surface().minimumDepth());
            int drop = node.terrain().naturalHeight() - terrain.naturalHeight();
            if (drop < 0 || drop > maximumDrop
                    || !HydrologySurfaceProfiles.sharesProfile(node.terrain(), terrain)
                    || !node.terrain().surfacePolicy().areaKey().equals(terrain.surfacePolicy().areaKey())) {
                return Double.POSITIVE_INFINITY;
            }
        }
        if (!planner.routePaths.traversableHop(node.naturalPoint(), new HydrologyPoint(x, terrain.naturalHeight(), z))) {
            return Double.POSITIVE_INFINITY;
        }
        terrain = planner.sampleLandBasis(x, z);
        double distanceFromDesired = StrictMath.hypot(x - desiredX, z - desiredZ);
        double score = terrain.naturalHeight() * planner.settings.routing().valleyPreference()
                + terrain.slope() * planner.settings.routing().slopePenalty()
                + terrain.routingCost() * terrain.routingMultiplier()
                + distanceFromDesired * 2.4D
                + distanceFromNode * 0.08D;
        long tie = HydrologyHash.mix(planner.worldSeed, node.id(), x, z);
        return score + HydrologyHash.unit(tie) * 1.0E-6D;
    }

    List<HydrologyPoint> refineEdge(
            long upstreamId,
            long downstreamId,
            HydrologyPoint upstream,
            HydrologyPoint downstream,
            HydrologyPoint continuation,
            int transverseCandidates,
            boolean surface
    ) {
        int refinement = planner.settings.routing().refinementSpacing();
        int effectiveTransverseCandidates = transverseCandidates;
        double deltaX = downstream.x() - upstream.x();
        double deltaZ = downstream.z() - upstream.z();
        double distance = StrictMath.hypot(deltaX, deltaZ);
        int steps = Math.max(1, (int) StrictMath.ceil(distance / refinement));
        RouteDirection directTangent = planner.routePaths.direction(upstream.x(), upstream.z(), downstream.x(), downstream.z());
        RouteDirection startTangent = directTangent;
        RouteDirection endTangent = planner.routePaths.direction(downstream.x(), downstream.z(), continuation.x(), continuation.z());
        if (endTangent.x() == 0D && endTangent.z() == 0D) {
            endTangent = startTangent;
        }
        HydrologyTerrainSample upstreamTerrain = planner.sampleLandBasis(upstream.x(), upstream.z());
        HydrologyTerrainSample downstreamTerrain = planner.sampleLandBasis(downstream.x(), downstream.z());
        int transitionThreshold = planner.settings.hydraulics().waterfallMinimumDrop();
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
                    elevationTransition && !surface ? 0 : effectiveTransverseCandidates,
                    upstreamTerrain == null ? null : upstreamTerrain.confinesKey()
            );
            if (candidates.isEmpty()) {
                return List.of();
            }
            layers.add(candidates);
        }
        return solveTerrainRoute(layers, distance / steps);
    }

    double routeWormBlend() {
        HydrologyPlannerSettings.Meanders meanders = planner.settings.geometry().meanders();
        double strength = meanders.primaryStrength() + meanders.detailStrength() * 0.35D;
        if (strength <= 0D || meanders.maximumOffsetRatio() <= 0D) {
            return 0D;
        }
        return Math.min(0.18D, meanders.maximumOffsetRatio() * (0.08D + strength * 0.05D));
    }

    RouteDirection routeWormDirection(HydrologyPoint point, RouteDirection fallback, double blend) {
        HydrologyPlannerSettings.Meanders meanders = planner.settings.geometry().meanders();
        double wavelength = Math.max(
                planner.settings.routing().sampleSpacing() * 2D,
                meanders.primaryWavelength() * 2.5D
        );
        double fieldX = planner.routeWormPrimary.noiseSigned(point.x() / wavelength, point.z() / wavelength);
        double fieldZ = planner.routeWormDetail.noiseSigned(
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
                : new RouteDirection(blendedX / blendedLength, blendedZ / blendedLength);
    }

    List<HydrologyPoint> constrainedTerrainFallbackEdge(
            HydrologyPoint upstream,
            HydrologyPoint downstream
    ) {
        if (planner.routePaths.crossesTerrainCrevasse(upstream, downstream)) {
            return List.of();
        }
        double deltaX = downstream.x() - upstream.x();
        double deltaZ = downstream.z() - upstream.z();
        double distance = StrictMath.hypot(deltaX, deltaZ);
        if (distance <= 0D) {
            return List.of();
        }
        int refinement = planner.settings.routing().refinementSpacing();
        double perpendicularX = -deltaZ / distance;
        double perpendicularZ = deltaX / distance;
        List<HydrologyPoint> direct = planner.segments.line(upstream, downstream, refinement);
        ArrayList<HydrologyPoint> points = new ArrayList<>(direct.size());
        for (int index = 0; index < direct.size(); index++) {
            HydrologyPoint point = direct.get(index);
            HydrologyTerrainSample terrain = planner.sampleLandBasisWithoutSlope(point.x(), point.z());
            if (terrain == null || !terrain.transitAllowed()) {
                return List.of();
            }
            if (index > 0 && index < direct.size() - 1
                    && hasLateralRouteFreedom(point, perpendicularX, perpendicularZ, refinement)) {
                return List.of();
            }
            points.add(new HydrologyPoint(point.x(), terrain.naturalHeight(), point.z()));
        }
        return planner.routePaths.traversableRoute(points) && !planner.routePaths.containsTerrainPit(points)
                ? List.copyOf(points)
                : List.of();
    }

    boolean hasLateralRouteFreedom(
            HydrologyPoint point,
            double perpendicularX,
            double perpendicularZ,
            int refinement
    ) {
        for (int direction : List.of(-1, 1)) {
            int x = (int) StrictMath.round(point.x() + perpendicularX * refinement * direction);
            int z = (int) StrictMath.round(point.z() + perpendicularZ * refinement * direction);
            HydrologyTerrainSample terrain = planner.sampleLandBasisWithoutSlope(x, z);
            if (terrain != null && terrain.transitAllowed()) {
                return true;
            }
        }
        return false;
    }

    RouteCandidate routeEndpoint(
            HydrologyPoint point,
            RouteDirection tangent
    ) {
        HydrologyTerrainSample terrain = planner.sampleLandBasis(point.x(), point.z());
        if (terrain == null || !terrain.transitAllowed()) {
            return new RouteCandidate(
                    point,
                    point.x(),
                    point.z(),
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
                point.x(),
                point.z(),
                0D,
                terrainScore,
                terrainScore,
                tangent,
                true
        );
    }

    RoutePosition routePosition(
            HydrologyPoint upstream,
            HydrologyPoint downstream,
            RouteDirection startTangent,
            RouteDirection endTangent,
            double tangentMagnitude,
            double distance,
            double progress,
            double meanderScale
    ) {
        double tangentX = planner.routePaths.hermiteDerivative(
                upstream.x(),
                downstream.x(),
                startTangent.x() * tangentMagnitude,
                endTangent.x() * tangentMagnitude,
                progress
        );
        double tangentZ = planner.routePaths.hermiteDerivative(
                upstream.z(),
                downstream.z(),
                startTangent.z() * tangentMagnitude,
                endTangent.z() * tangentMagnitude,
                progress
        );
        double tangentLength = StrictMath.hypot(tangentX, tangentZ);
        RouteDirection tangent = tangentLength <= 0D
                ? startTangent
                : new RouteDirection(tangentX / tangentLength, tangentZ / tangentLength);
        double baseX = planner.routePaths.hermite(
                upstream.x(),
                downstream.x(),
                startTangent.x() * tangentMagnitude,
                endTangent.x() * tangentMagnitude,
                progress
        );
        double baseZ = planner.routePaths.hermite(
                upstream.z(),
                downstream.z(),
                startTangent.z() * tangentMagnitude,
                endTangent.z() * tangentMagnitude,
                progress
        );
        double envelope = StrictMath.pow(StrictMath.sin(StrictMath.PI * progress), 2D);
        HydrologyPlannerSettings.Meanders meanders = planner.settings.geometry().meanders();
        double primary = planner.routeWormPrimary.noiseSigned(
                baseX / meanders.primaryWavelength(),
                baseZ / meanders.primaryWavelength()
        );
        double detail = planner.routeWormDetail.noiseSigned(
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
        double transverseRatio = planner.settings.routing().refinementSpacing()
                / (double) planner.settings.routing().sampleSpacing();
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
    static boolean withinConfines(HydrologyTerrainSample terrain, String confines) {
        return confines == null || confines.equals(terrain.confinesKey());
    }

    List<RouteCandidate> routeCandidates(
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
            HydrologyTerrainSample terrain = planner.sampleLandBasis(x, z);
            if (terrain == null || !terrain.transitAllowed() || !withinConfines(terrain, confines)) {
                continue;
            }
            long packed = RiverFootprint.pack(x, z);
            double localScore = routeTerrainScore(terrain)
                    + StrictMath.abs(offset) * 0.18D
                    + HydrologyHash.unit(HydrologyHash.mix(planner.worldSeed, upstreamId, downstreamId, x, z)) * 1.0E-6D;
            RouteCandidate candidate = new RouteCandidate(
                    new HydrologyPoint(x, terrain.naturalHeight(), z),
                    x,
                    z,
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
        HydrologyTerrainSample fallbackTerrain = planner.sampleLandBasis(fallbackX, fallbackZ);
        if (fallbackTerrain != null
                && fallbackTerrain.transitAllowed()
                && withinConfines(fallbackTerrain, confines)) {
            long packed = RiverFootprint.pack(fallbackX, fallbackZ);
            double fallbackOffset = (nominal.baseX() - nominal.x()) * -nominal.tangent().z()
                    + (nominal.baseZ() - nominal.z()) * nominal.tangent().x();
            double localScore = routeTerrainScore(fallbackTerrain)
                    + StrictMath.abs(fallbackOffset) * 0.18D
                    + HydrologyHash.unit(HydrologyHash.mix(
                    planner.worldSeed,
                    upstreamId,
                    downstreamId,
                    fallbackX,
                    fallbackZ
            )) * 1.0E-6D;
            RouteCandidate fallback = new RouteCandidate(
                    new HydrologyPoint(fallbackX, fallbackTerrain.naturalHeight(), fallbackZ),
                    fallbackX,
                    fallbackZ,
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

    List<RouteCandidate> unresolvedSurfaceRouteCandidates(
            HydrologySampledGrid grid,
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
                    planner.worldSeed,
                    upstreamId,
                    downstreamId,
                    x,
                    z
            )) * 1.0E-6D;
            RouteCandidate candidate = new RouteCandidate(
                    new HydrologyPoint(x, estimate.height(), z),
                    nominal.x() - nominal.tangent().z() * offset,
                    nominal.z() + nominal.tangent().x() * offset,
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

    RouteTerrainEstimate routeTerrainEstimate(HydrologySampledGrid grid, int x, int z) {
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
        double height = planner.routePaths.bilinear(
                northwest.naturalHeight(),
                northeast.naturalHeight(),
                southwest.naturalHeight(),
                southeast.naturalHeight(),
                progressX,
                progressZ
        );
        double terrainScore = planner.routePaths.bilinear(
                routeTerrainScore(northwest),
                routeTerrainScore(northeast),
                routeTerrainScore(southwest),
                routeTerrainScore(southeast),
                progressX,
                progressZ
        );
        return new RouteTerrainEstimate((int) StrictMath.round(height), terrainScore);
    }

    RouteCandidate resolveRouteCandidate(RouteCandidate candidate) {
        if (candidate.terrainResolved()) {
            return candidate;
        }
        HydrologyPoint point = candidate.point();
        HydrologyTerrainSample terrain = planner.sampleLandBasis(point.x(), point.z());
        if (terrain == null || !terrain.transitAllowed()) {
            return new RouteCandidate(
                    point,
                    candidate.continuousX(),
                    candidate.continuousZ(),
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
                candidate.continuousX(),
                candidate.continuousZ(),
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
    double crossDropPenalty(HydrologyPoint point, RouteDirection tangent, HydrologyTerrainSample center) {
        double length = StrictMath.hypot(tangent.x(), tangent.z());
        if (length <= 0D) {
            return 0D;
        }
        double normalX = -tangent.z() / length;
        double normalZ = tangent.x() / length;
        double reach = planner.settings.surface().maximumWidth() / 2D + planner.settings.surface().shoreWidth() + 2D;
        int lowest = center.naturalHeight();
        for (double distance = reach / 2D; distance <= reach; distance += reach / 2D) {
            for (double direction = -1D; direction <= 1D; direction += 2D) {
                HydrologyTerrainSample side = planner.sampleLandBasisWithoutSlope(
                        (int) StrictMath.round(point.x() + normalX * distance * direction),
                        (int) StrictMath.round(point.z() + normalZ * distance * direction));
                if (side != null) {
                    lowest = Math.min(lowest, side.naturalHeight());
                }
            }
        }
        double drop = center.naturalHeight() - lowest;
        return drop * planner.settings.routing().valleyPreference() * CROSS_DROP_WEIGHT;
    }

    double routeTerrainScore(HydrologyTerrainSample terrain) {
        return terrain.naturalHeight() * planner.settings.routing().valleyPreference()
                + terrain.slope() * planner.settings.routing().slopePenalty()
                + terrain.routingCost() * terrain.routingMultiplier();
    }

    List<HydrologyPoint> solveTerrainRoute(
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
            int pitRemoval = planner.routePaths.removeTerrainPits(availableLayers, route);
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
            return planner.routePaths.smoothTerrainRoute(points);
        }
    }

    RouteCandidate[] selectTerrainRoute(
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
                    double transition = planner.routePaths.routeTransitionGeometryCost(previous, current, targetStepLength);
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

    TerrainTransitionKey unsupportedTerrainTransition(RouteCandidate[] route) {
        for (int routeIndex = 0; routeIndex < route.length - 1; routeIndex++) {
            RouteCandidate start = route[routeIndex];
            RouteCandidate end = route[routeIndex + 1];
            if (!planner.routePaths.traversableTerrainTransition(start.point(), end.point())) {
                return new TerrainTransitionKey(start.point(), end.point());
            }
        }
        return null;
    }

    RouteCandidate[] selectCurvatureAwareTerrainRoute(
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

    CurvatureRouteSelection selectCurvatureAwareTerrainRoute(
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
                            double turn = continuousTurnDegrees(
                                    beforeLayer.get(beforeIndex),
                                    previous,
                                    current
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

    boolean evaluateSelectedSurfaceCosts(
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
            localPenalties[layerIndex][candidateIndex] = planner.surfaceCourses.surfaceRouteCandidateBankPenalty(
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
            double transition = planner.routePaths.routeTransitionCost(previous, current, targetStepLength);
            if (Double.isFinite(transition)) {
                transition += surfaceRouteTransitionPenalty(previous, current);
            }
            transitionCosts[layerIndex][previousIndex][currentIndex] = transition;
            evaluated = true;
        }
        return evaluated;
    }

    double resolvedLocalPenalty(double penalty) {
        return Double.isNaN(penalty) ? 0D : penalty;
    }

    double resolvedTransitionCost(
            RouteCandidate previous,
            RouteCandidate current,
            double targetStepLength,
            double exactCost
    ) {
        return Double.isNaN(exactCost)
                ? planner.routePaths.routeTransitionGeometryCost(previous, current, targetStepLength)
                : exactCost;
    }

    double curvatureCost(double turn, double maximumTurn, double turnCost) {
        double excess = Math.max(0D, turn - 8D);
        return excess * excess * turnCost / maximumTurn;
    }

    double continuousTurnDegrees(RouteCandidate before, RouteCandidate center, RouteCandidate after) {
        double incomingX = center.continuousX() - before.continuousX();
        double incomingZ = center.continuousZ() - before.continuousZ();
        double outgoingX = after.continuousX() - center.continuousX();
        double outgoingZ = after.continuousZ() - center.continuousZ();
        double length = StrictMath.hypot(incomingX, incomingZ) * StrictMath.hypot(outgoingX, outgoingZ);
        if (length <= 0D) {
            return 0D;
        }
        double cosine = (incomingX * outgoingX + incomingZ * outgoingZ) / length;
        return StrictMath.toDegrees(StrictMath.acos(Math.max(-1D, Math.min(1D, cosine))));
    }

    double surfaceRouteTransitionPenalty(
            RouteCandidate start,
            RouteCandidate end
    ) {
        int minimumBankDistance = planner.surfaceCourses.surfaceBankDistance(planner.settings.surface().minimumWidth());
        int maximumBankDistance = planner.surfaceCourses.surfaceBankDistance(planner.settings.surface().maximumWidth());
        RouteDirection tangent = planner.routePaths.direction(start.point().x(), start.point().z(), end.point().x(), end.point().z());
        int endpointCeiling = Math.max(start.point().y(), end.point().y());
        double penalty = 0D;
        List<HydrologyPoint> raster = planner.segments.rasterLine(start.point(), end.point());
        int sampleStride = Math.max(1, planner.settings.routing().refinementSpacing() / 4);
        for (int pointIndex = 0; pointIndex < raster.size(); pointIndex += sampleStride) {
            HydrologyPoint point = raster.get(pointIndex);
            HydrologyTerrainSample terrain = planner.sampleLandBasis(point.x(), point.z());
            if (terrain == null) {
                return 1.0E12D;
            }
            int permittedIncision = planner.sourcePlanner.permittedSurfaceIncision(terrain);
            int ridgeExcess = Math.max(
                    0,
                    terrain.naturalHeight() - endpointCeiling - permittedIncision
            );
            penalty += (double) ridgeExcess * ridgeExcess * 65536D;
            if (terrain.slope() >= planner.settings.hydraulics().waterfallMinimumDrop()) {
                continue;
            }
            int head = Math.subtractExact(
                    terrain.naturalHeight(),
                    planner.settings.surface().banks().sink()
            );
            penalty += planner.surfaceCourses.surfaceRouteBankPenalty(
                    point,
                    tangent,
                    minimumBankDistance,
                    maximumBankDistance,
                    head,
                    planner.surfaceCourses.maximumSurfaceBankRise(terrain)
            );
        }
        if ((raster.size() - 1) % sampleStride != 0) {
            HydrologyPoint point = raster.getLast();
            HydrologyTerrainSample terrain = planner.sampleLandBasis(point.x(), point.z());
            if (terrain == null) {
                return 1.0E12D;
            }
            int permittedIncision = planner.sourcePlanner.permittedSurfaceIncision(terrain);
            int ridgeExcess = Math.max(
                    0,
                    terrain.naturalHeight() - endpointCeiling - permittedIncision
            );
            penalty += (double) ridgeExcess * ridgeExcess * 65536D;
            if (terrain.slope() < planner.settings.hydraulics().waterfallMinimumDrop()) {
                int head = Math.subtractExact(
                        terrain.naturalHeight(),
                        planner.settings.surface().banks().sink()
                );
                penalty += planner.surfaceCourses.surfaceRouteBankPenalty(
                        point,
                        tangent,
                        minimumBankDistance,
                        maximumBankDistance,
                        head,
                        planner.surfaceCourses.maximumSurfaceBankRise(terrain)
                );
            }
        }
        return penalty;
    }

    record CurvatureRouteSelection(RouteCandidate[] route, int[] indices) {
        private static CurvatureRouteSelection empty() {
            return new CurvatureRouteSelection(new RouteCandidate[0], new int[0]);
        }
    }

    record TerrainTransitionKey(HydrologyPoint start, HydrologyPoint end) {
    }
}
