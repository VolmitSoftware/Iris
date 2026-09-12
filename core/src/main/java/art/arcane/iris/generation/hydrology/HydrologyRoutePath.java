package art.arcane.iris.generation.hydrology;

import java.util.ArrayList;
import java.util.List;

final class HydrologyRoutePath {
    private final HydrologyPlanner planner;

    HydrologyRoutePath(HydrologyPlanner planner) {
        this.planner = planner;
    }

    boolean containsTerrainPit(List<HydrologyPoint> route) {
        int threshold = planner.settings.hydraulics().waterfallMinimumDrop();
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

    double bilinear(
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

    int removeTerrainPits(List<List<RouteCandidate>> layers, RouteCandidate[] route) {
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

    boolean isTerrainPit(RouteCandidate candidate) {
        HydrologyPoint point = candidate.point();
        RouteDirection tangent = candidate.tangent();
        int refinement = planner.settings.routing().refinementSpacing();
        int offsetX = (int) StrictMath.round(tangent.x() * refinement);
        int offsetZ = (int) StrictMath.round(tangent.z() * refinement);
        if (offsetX == 0 && offsetZ == 0) {
            return false;
        }
        HydrologyTerrainSample before = planner.sampleLandBasisWithoutSlope(point.x() - offsetX, point.z() - offsetZ);
        HydrologyTerrainSample after = planner.sampleLandBasisWithoutSlope(point.x() + offsetX, point.z() + offsetZ);
        int threshold = planner.settings.hydraulics().waterfallMinimumDrop();
        return before != null
                && after != null
                && before.naturalHeight() - point.y() >= threshold
                && after.naturalHeight() - point.y() >= threshold;
    }

    List<HydrologyPoint> smoothTerrainRoute(List<HydrologyPoint> route) {
        List<HydrologyPoint> current = List.copyOf(route);
        List<HydrologyPoint> best = current;
        double bestTurnScore = planner.surfaceCourses.routeTurnScore(current);
        HydrologyPlannerSettings.Meanders meanders = planner.settings.geometry().meanders();
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
                HydrologyTerrainSample terrain = planner.sampleLandBasisWithoutSlope(x, z);
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
            double candidateTurnScore = planner.surfaceCourses.routeTurnScore(candidate);
            if (candidateTurnScore < bestTurnScore) {
                best = candidate;
                bestTurnScore = candidateTurnScore;
            }
        }
        List<HydrologyPoint> smoothed = removeRouteHairpins(best, meanders.maximumTurnDegrees());
        return containsTerrainPit(smoothed) ? List.of() : smoothed;
    }

    List<HydrologyPoint> removeRouteHairpins(List<HydrologyPoint> route, double maximumTurnDegrees) {
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

    int sharpestRoutePoint(List<HydrologyPoint> route) {
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

    static double routeTurnDegrees(HydrologyPoint previous, HydrologyPoint point, HydrologyPoint next) {
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

    List<HydrologyPoint> withoutDuplicateRoutePoints(List<HydrologyPoint> points) {
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

    boolean traversableRoute(List<HydrologyPoint> route) {
        for (int pointIndex = 0; pointIndex < route.size() - 1; pointIndex++) {
            HydrologyPoint start = route.get(pointIndex);
            HydrologyPoint end = route.get(pointIndex + 1);
            if (!traversableHop(start, end) || crossesTerrainCrevasse(start, end)) {
                return false;
            }
        }
        return true;
    }

    double routeTransitionCost(
            RouteCandidate previous,
            RouteCandidate current,
            double targetStepLength
    ) {
        if (!traversableTerrainTransition(previous.point(), current.point())) {
            return Double.POSITIVE_INFINITY;
        }
        return routeTransitionGeometryCost(previous, current, targetStepLength);
    }

    double routeTransitionGeometryCost(
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
                + rise * planner.settings.routing().uphillPenalty();
    }

    boolean traversableTerrainTransition(HydrologyPoint start, HydrologyPoint end) {
        List<HydrologyPoint> crossing = planner.segments.rasterLine(start, end);
        HydrologyTerrainSample startTerrain = planner.sampleLandBasisWithoutSlope(start.x(), start.z());
        HydrologyTerrainSample endTerrain = planner.sampleLandBasisWithoutSlope(end.x(), end.z());
        if (startTerrain == null || endTerrain == null) {
            return false;
        }
        int boundaryHeight = Math.min(startTerrain.naturalHeight(), endTerrain.naturalHeight());
        int threshold = planner.settings.hydraulics().waterfallMinimumDrop();
        for (int pointIndex = 1; pointIndex < crossing.size() - 1; pointIndex++) {
            HydrologyPoint point = crossing.get(pointIndex);
            HydrologyTerrainSample terrain = planner.sampleLandBasisWithoutSlope(point.x(), point.z());
            if (terrain == null
                    || !terrain.transitAllowed()
                    || boundaryHeight - terrain.naturalHeight() >= threshold) {
                return false;
            }
        }
        return true;
    }

    boolean crossesTerrainCrevasse(HydrologyPoint start, HydrologyPoint end) {
        List<HydrologyPoint> crossing = planner.segments.rasterLine(start, end);
        if (crossing.size() < 3) {
            return false;
        }
        HydrologyTerrainSample startTerrain = planner.sampleLandBasisWithoutSlope(start.x(), start.z());
        HydrologyTerrainSample endTerrain = planner.sampleLandBasisWithoutSlope(end.x(), end.z());
        if (startTerrain == null || endTerrain == null) {
            return true;
        }
        int boundaryHeight = Math.min(startTerrain.naturalHeight(), endTerrain.naturalHeight());
        int threshold = planner.settings.hydraulics().waterfallMinimumDrop();
        for (int pointIndex = 1; pointIndex < crossing.size() - 1; pointIndex++) {
            HydrologyPoint point = crossing.get(pointIndex);
            HydrologyTerrainSample terrain = planner.sampleLandBasisWithoutSlope(point.x(), point.z());
            if (terrain == null || boundaryHeight - terrain.naturalHeight() >= threshold) {
                return true;
            }
        }
        return false;
    }

    RouteDirection direction(int startX, int startZ, int endX, int endZ) {
        double x = endX - startX;
        double z = endZ - startZ;
        double length = StrictMath.hypot(x, z);
        if (length <= 0D) {
            return new RouteDirection(0D, 0D);
        }
        return new RouteDirection(x / length, z / length);
    }

    double hermite(double start, double end, double startTangent, double endTangent, double progress) {
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

    double hermiteDerivative(
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

    boolean traversableHop(HydrologyPoint start, HydrologyPoint end) {
        List<HydrologyPoint> crossing = planner.segments.rasterLine(start, end);
        for (int pointIndex = 1; pointIndex < crossing.size() - 1; pointIndex++) {
            HydrologyPoint point = crossing.get(pointIndex);
            HydrologyTerrainSample terrain = planner.sampleLandBasisWithoutSlope(point.x(), point.z());
            if (terrain == null || terrain.ocean() || !terrain.transitAllowed()) {
                return false;
            }
        }
        return true;
    }
}
