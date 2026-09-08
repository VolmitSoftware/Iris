package art.arcane.iris.engine.hydrology.cave;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

final class HydrologyCaveChamberPlanner {
    private final HydrologyCaveContainmentPlanner planner;

    HydrologyCaveChamberPlanner(HydrologyCaveContainmentPlanner planner) {
        this.planner = planner;
    }

    HydrologyCavePlan planGrottoOrClosedComponent(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            List<CavePosition> throat
    ) {
        CaveVoxel targetVoxel = planner.voxelAt(view, source.target());
        if (planner.isFluidReachable(targetVoxel, settings)) {
            return planClosedComponent(view, source, settings, throat);
        }
        if (targetVoxel == CaveVoxel.LAVA) {
            return planner.rejected(source, HydrologyCaveRejection.LAVA_CONTACT);
        }
        if (targetVoxel == CaveVoxel.INCOMPATIBLE_FLUID) {
            return planner.rejected(source, HydrologyCaveRejection.INCOMPATIBLE_FLUID);
        }
        return planGeneratedGrotto(view, source, settings, throat);
    }

    HydrologyCavePlan planWaterfallPool(
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
            return planner.rejected(source, dryThroatRejection);
        }
        HydrologyCaveRejection shaftRejection = validateWaterfallShaft(view, source, settings, throat);
        if (shaftRejection != HydrologyCaveRejection.NONE) {
            return planner.rejected(source, shaftRejection);
        }
        CaveVoxel targetVoxel = planner.voxelAt(view, source.target());
        if (!planner.isFluidReachable(targetVoxel, settings)) {
            return planner.rejected(source, planner.rejectionForTarget(targetVoxel, settings));
        }

        Map<CavePosition, HydrologyCaveAction> actions = new LinkedHashMap<>();
        addThroatActions(actions, throat, source);
        addSealGuards(view, source, actions);
        return planner.accepted(view, source, actions);
    }

    HydrologyCavePlan planClosedComponent(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            List<CavePosition> throat
    ) {
        HydrologyCaveRejection dryThroatRejection = validateDryThroatContacts(view, source, throat);
        if (dryThroatRejection != HydrologyCaveRejection.NONE) {
            return planner.rejected(source, dryThroatRejection);
        }
        HydrologyCaveRejection waterfallRejection = validateWaterfallShaft(view, source, settings, throat);
        if (waterfallRejection != HydrologyCaveRejection.NONE) {
            return planner.rejected(source, waterfallRejection);
        }

        CaveVoxel targetVoxel = planner.voxelAt(view, source.target());
        if (!planner.isFluidReachable(targetVoxel, settings)) {
            return planner.rejected(source, planner.rejectionForTarget(targetVoxel, settings));
        }

        CaveComponentResult component = resolveClosedComponent(view, source, settings, throat);
        if (component.rejection() != HydrologyCaveRejection.NONE) {
            return planner.rejected(source, component.rejection());
        }

        Map<CavePosition, HydrologyCaveAction> actions = new LinkedHashMap<>();
        addThroatActions(actions, throat, source);
        for (CavePosition position : component.positions()) {
            actions.put(position, HydrologyCaveAction.WET_SOURCE);
        }
        addSealGuards(view, source, actions);
        return planner.accepted(view, source, actions);
    }

    HydrologyCaveRejection validateDryThroatContacts(
            CaveVoxelView view,
            HydrologyCaveSource source,
            List<CavePosition> throat
    ) {
        Set<CavePosition> throatPositions = Set.copyOf(throat);
        for (CavePosition position : throat) {
            if (position.y() <= source.waterHeadY()) {
                continue;
            }
            for (CavePosition direction : HydrologyCaveContainmentPlanner.DIRECTIONS) {
                CavePosition neighbor = position.offset(direction.x(), direction.y(), direction.z());
                if (throatPositions.contains(neighbor) || isInletOpening(source, neighbor)) {
                    continue;
                }
                if (!view.isInWorld(neighbor)) {
                    return HydrologyCaveRejection.WORLD_BOUNDARY;
                }
                CaveVoxel voxel = planner.voxelAt(view, neighbor);
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

    HydrologyCaveRejection validateWaterfallShaft(
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
            for (CavePosition direction : HydrologyCaveContainmentPlanner.DIRECTIONS) {
                CavePosition neighbor = position.offset(direction.x(), direction.y(), direction.z());
                if (throatPositions.contains(neighbor) || isInletOpening(source, neighbor)) {
                    continue;
                }
                if (!view.isInWorld(neighbor)) {
                    return HydrologyCaveRejection.WORLD_BOUNDARY;
                }
                HydrologyCaveRejection boundsRejection = planner.validateBounds(source, settings, neighbor);
                if (boundsRejection != HydrologyCaveRejection.NONE) {
                    return boundsRejection;
                }
                CaveVoxel voxel = planner.voxelAt(view, neighbor);
                HydrologyCaveRejection hazard = planner.rejectionForHazard(voxel, settings);
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

    HydrologyCavePlan planGeneratedGrotto(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            List<CavePosition> throat
    ) {
        CaveGrottoResult grotto = buildGrotto(source, settings);
        if (grotto.rejection() != HydrologyCaveRejection.NONE) {
            return planner.rejected(source, grotto.rejection());
        }
        Set<CavePosition> chamber = grotto.positions();

        Set<CavePosition> carve = new LinkedHashSet<>(chamber.size() + throat.size());
        carve.addAll(chamber);
        carve.addAll(throat);
        HydrologyCaveRejection carveRejection = validateGeneratedCarve(view, source, settings, carve);
        if (carveRejection != HydrologyCaveRejection.NONE) {
            return planner.rejected(source, carveRejection);
        }

        CaveBoundaryResult boundary = validateGeneratedBoundary(view, source, settings, carve);
        if (boundary.rejection() != HydrologyCaveRejection.NONE) {
            return planner.rejected(source, boundary.rejection());
        }

        Map<CavePosition, HydrologyCaveAction> actions = new LinkedHashMap<>();
        addChamberActions(actions, chamber, source.waterHeadY());
        addThroatActions(actions, throat, source);
        for (CavePosition position : boundary.sealGuards()) {
            actions.put(position, HydrologyCaveAction.SEAL_GUARD);
        }
        return planner.accepted(view, source, actions);
    }

    HydrologyCavePlan planDeepPool(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            List<CavePosition> throat
    ) {
        CaveGrottoResult grotto = buildGrotto(source, settings);
        if (grotto.rejection() != HydrologyCaveRejection.NONE) {
            return planner.rejected(source, grotto.rejection());
        }
        Set<CavePosition> chamber = grotto.positions();
        Set<CavePosition> carve = new LinkedHashSet<>(chamber.size() + throat.size());
        carve.addAll(chamber);
        carve.addAll(throat);

        HydrologyCaveRejection carveRejection = validateDeepPoolCarve(view, source, settings, carve);
        if (carveRejection != HydrologyCaveRejection.NONE) {
            return planner.rejected(source, carveRejection);
        }
        CaveBoundaryResult boundary = validateDeepPoolBoundary(view, source, settings, carve);
        if (boundary.rejection() != HydrologyCaveRejection.NONE) {
            return planner.rejected(source, boundary.rejection());
        }

        Map<CavePosition, HydrologyCaveAction> actions = new LinkedHashMap<>();
        addChamberActions(actions, chamber, source.waterHeadY());
        addThroatActions(actions, throat, source);
        for (CavePosition position : boundary.sealGuards()) {
            actions.put(position, HydrologyCaveAction.SEAL_GUARD);
        }
        return planner.accepted(view, source, actions);
    }

    HydrologyCaveRejection validateDeepPoolCarve(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            Set<CavePosition> carve
    ) {
        for (CavePosition position : carve) {
            if (!view.isInWorld(position)) {
                return HydrologyCaveRejection.WORLD_BOUNDARY;
            }
            HydrologyCaveRejection boundsRejection = planner.validateBounds(source, settings, position);
            if (boundsRejection != HydrologyCaveRejection.NONE) {
                return boundsRejection;
            }
            CaveVoxel voxel = planner.voxelAt(view, position);
            HydrologyCaveRejection hazard = planner.rejectionForHazard(voxel, settings);
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

    CaveBoundaryResult validateDeepPoolBoundary(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            Set<CavePosition> carve
    ) {
        Set<CavePosition> guards = new LinkedHashSet<>();
        for (CavePosition position : carve) {
            for (CavePosition direction : HydrologyCaveContainmentPlanner.DIRECTIONS) {
                CavePosition neighbor = position.offset(direction.x(), direction.y(), direction.z());
                if (carve.contains(neighbor)) {
                    continue;
                }
                if (!view.isInWorld(neighbor)) {
                    return CaveBoundaryResult.rejected(HydrologyCaveRejection.WORLD_BOUNDARY);
                }
                HydrologyCaveRejection boundsRejection = planner.validateBounds(source, settings, neighbor);
                if (boundsRejection != HydrologyCaveRejection.NONE) {
                    return CaveBoundaryResult.rejected(boundsRejection);
                }
                CaveVoxel voxel = planner.voxelAt(view, neighbor);
                HydrologyCaveRejection hazard = planner.rejectionForHazard(voxel, settings);
                if (hazard != HydrologyCaveRejection.NONE) {
                    return CaveBoundaryResult.rejected(hazard);
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
                return CaveBoundaryResult.rejected(HydrologyCaveRejection.GROTTO_SHELL_OPEN);
            }
        }
        return CaveBoundaryResult.accepted(guards);
    }

    CaveComponentResult resolveClosedComponent(
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
            return CaveComponentResult.rejected(seedRejection);
        }

        while (!queue.isEmpty()) {
            CavePosition position = queue.remove();
            HydrologyCaveRejection positionRejection = validateReachablePosition(view, source, settings, position);
            if (positionRejection != HydrologyCaveRejection.NONE) {
                return CaveComponentResult.rejected(positionRejection);
            }
            if (++visitedCount > settings.maxFloodVolume()) {
                return CaveComponentResult.rejected(HydrologyCaveRejection.VOLUME_LIMIT);
            }

            for (CavePosition direction : HydrologyCaveContainmentPlanner.DIRECTIONS) {
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
                    return CaveComponentResult.rejected(neighborRejection);
                }
            }
        }

        return CaveComponentResult.accepted(queued);
    }

    HydrologyCaveRejection addThroatContacts(
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
            for (CavePosition direction : HydrologyCaveContainmentPlanner.DIRECTIONS) {
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

    HydrologyCaveRejection inspectReachableNeighbor(
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

        CaveVoxel voxel = planner.voxelAt(view, position);
        HydrologyCaveRejection hazard = planner.rejectionForHazard(voxel, settings);
        if (hazard != HydrologyCaveRejection.NONE) {
            return hazard;
        }
        if (!planner.isFluidReachable(voxel, settings)) {
            return HydrologyCaveRejection.NONE;
        }

        HydrologyCaveRejection boundsRejection = planner.validateBounds(source, settings, position);
        if (boundsRejection != HydrologyCaveRejection.NONE) {
            return boundsRejection;
        }
        if (queued.add(position)) {
            queue.add(position);
        }
        return HydrologyCaveRejection.NONE;
    }

    HydrologyCaveRejection inspectAboveHeadNeighbor(
            CaveVoxelView view,
            HydrologyCaveSource source,
            CavePosition position
    ) {
        if (isInletOpening(source, position) || !view.isInWorld(position)) {
            return HydrologyCaveRejection.NONE;
        }
        CaveVoxel voxel = planner.voxelAt(view, position);
        return switch (voxel) {
            case LAVA -> HydrologyCaveRejection.LAVA_CONTACT;
            case COMPATIBLE_FLUID -> HydrologyCaveRejection.EXISTING_FLUID;
            case INCOMPATIBLE_FLUID -> HydrologyCaveRejection.INCOMPATIBLE_FLUID;
            default -> HydrologyCaveRejection.NONE;
        };
    }

    HydrologyCaveRejection validateReachablePosition(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            CavePosition position
    ) {
        if (!view.isInWorld(position)) {
            return HydrologyCaveRejection.WORLD_BOUNDARY;
        }
        HydrologyCaveRejection boundsRejection = planner.validateBounds(source, settings, position);
        if (boundsRejection != HydrologyCaveRejection.NONE) {
            return boundsRejection;
        }
        if (view.isOpenToSurface(position)) {
            return HydrologyCaveRejection.OPEN_SURFACE;
        }
        CaveVoxel voxel = planner.voxelAt(view, position);
        HydrologyCaveRejection hazard = planner.rejectionForHazard(voxel, settings);
        if (hazard != HydrologyCaveRejection.NONE) {
            return hazard;
        }
        return planner.isFluidReachable(voxel, settings)
                ? HydrologyCaveRejection.NONE
                : HydrologyCaveRejection.NO_CAVE_TARGET;
    }

    CavePathResult buildThroat(
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
            return CavePathResult.rejected(HydrologyCaveRejection.THROAT_LIMIT);
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
                return CavePathResult.rejected(positionRejection);
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

    CavePathResult expandThroat(
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
                            return CavePathResult.rejected(rejection);
                        }
                        expanded.add(position);
                        if (expanded.size() > settings.maxFloodVolume()) {
                            return CavePathResult.rejected(HydrologyCaveRejection.VOLUME_LIMIT);
                        }
                    }
                }
            }
        }
        return CavePathResult.accepted(List.copyOf(expanded));
    }

    int selectNextAxis(
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

    double nextAxisScore(int moves, int used) {
        if (used >= moves) {
            return Double.POSITIVE_INFINITY;
        }
        return ((2D * used) + 1D) / moves;
    }

    HydrologyCaveRejection validateThroatPosition(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            CavePosition position
    ) {
        if (!view.isInWorld(position)) {
            return HydrologyCaveRejection.WORLD_BOUNDARY;
        }
        HydrologyCaveRejection boundsRejection = planner.validateBounds(source, settings, position);
        if (boundsRejection != HydrologyCaveRejection.NONE) {
            return boundsRejection;
        }
        return planner.rejectionForHazard(planner.voxelAt(view, position), settings);
    }

    CaveGrottoResult buildGrotto(HydrologyCaveSource source, HydrologyCavePlannerSettings settings) {
        int horizontalRadius = settings.grottoHorizontalRadius();
        int verticalRadius = settings.grottoVerticalRadius();
        Set<CavePosition> candidates = new LinkedHashSet<>();

        for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
            for (int dy = -verticalRadius; dy <= verticalRadius; dy++) {
                for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                    if (settings.grottoShape().contains(source, settings, dx, dy, dz)) {
                        candidates.add(source.target().offset(dx, dy, dz));
                        if (candidates.size() > settings.maxFloodVolume()) {
                            return CaveGrottoResult.rejected(HydrologyCaveRejection.VOLUME_LIMIT);
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
                return CaveGrottoResult.rejected(HydrologyCaveRejection.DRY_HEADROOM_LIMIT);
            }
            candidates.add(headroom);
            if (candidates.size() > settings.maxFloodVolume()) {
                return CaveGrottoResult.rejected(HydrologyCaveRejection.VOLUME_LIMIT);
            }
        }
        return CaveGrottoResult.accepted(connectedGrotto(source.target(), candidates));
    }

    Set<CavePosition> connectedGrotto(CavePosition target, Set<CavePosition> candidates) {
        Queue<CavePosition> queue = new ArrayDeque<>();
        Set<CavePosition> connected = new LinkedHashSet<>();
        queue.add(target);
        connected.add(target);
        while (!queue.isEmpty()) {
            CavePosition position = queue.remove();
            for (CavePosition direction : HydrologyCaveContainmentPlanner.DIRECTIONS) {
                CavePosition neighbor = position.offset(direction.x(), direction.y(), direction.z());
                if (candidates.contains(neighbor) && connected.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return connected;
    }

    HydrologyCaveRejection validateGeneratedCarve(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            Set<CavePosition> carve
    ) {
        for (CavePosition position : carve) {
            if (!view.isInWorld(position)) {
                return HydrologyCaveRejection.WORLD_BOUNDARY;
            }
            HydrologyCaveRejection boundsRejection = planner.validateBounds(source, settings, position);
            if (boundsRejection != HydrologyCaveRejection.NONE) {
                return boundsRejection;
            }
            CaveVoxel voxel = planner.voxelAt(view, position);
            if (isGeneratedInletCarve(view, source, settings, position, voxel)) {
                continue;
            }
            HydrologyCaveRejection hazard = planner.rejectionForHazard(voxel, settings);
            if (hazard != HydrologyCaveRejection.NONE) {
                return hazard;
            }
            if (voxel != CaveVoxel.SOLID) {
                return HydrologyCaveRejection.GROTTO_INTERSECTION;
            }
        }
        return HydrologyCaveRejection.NONE;
    }

    CaveBoundaryResult validateGeneratedBoundary(
            CaveVoxelView view,
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            Set<CavePosition> carve
    ) {
        Set<CavePosition> guards = new LinkedHashSet<>();
        for (CavePosition position : carve) {
            for (CavePosition direction : HydrologyCaveContainmentPlanner.DIRECTIONS) {
                CavePosition neighbor = position.offset(direction.x(), direction.y(), direction.z());
                if (carve.contains(neighbor)) {
                    continue;
                }
                if (isInletOpening(source, neighbor)
                        || isGeneratedInletOpening(view, source, settings, neighbor)) {
                    continue;
                }
                if (!view.isInWorld(neighbor)) {
                    return CaveBoundaryResult.rejected(HydrologyCaveRejection.WORLD_BOUNDARY);
                }
                HydrologyCaveRejection boundsRejection = planner.validateBounds(source, settings, neighbor);
                if (boundsRejection != HydrologyCaveRejection.NONE) {
                    return CaveBoundaryResult.rejected(boundsRejection);
                }
                CaveVoxel voxel = planner.voxelAt(view, neighbor);
                HydrologyCaveRejection hazard = planner.rejectionForHazard(voxel, settings);
                if (hazard != HydrologyCaveRejection.NONE) {
                    return CaveBoundaryResult.rejected(hazard);
                }
                if (voxel != CaveVoxel.SOLID) {
                    return CaveBoundaryResult.rejected(HydrologyCaveRejection.GROTTO_SHELL_OPEN);
                }
                guards.add(neighbor);
            }
        }
        return CaveBoundaryResult.accepted(guards);
    }

    void addChamberActions(
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

    void addThroatActions(
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

    void addSealGuards(
            CaveVoxelView view,
            HydrologyCaveSource source,
            Map<CavePosition, HydrologyCaveAction> actions
    ) {
        Set<CavePosition> guards = new LinkedHashSet<>();
        for (CavePosition position : List.copyOf(actions.keySet())) {
            for (CavePosition direction : HydrologyCaveContainmentPlanner.DIRECTIONS) {
                CavePosition neighbor = position.offset(direction.x(), direction.y(), direction.z());
                if (actions.containsKey(neighbor)
                        || isInletOpening(source, neighbor)
                        || !view.isInWorld(neighbor)) {
                    continue;
                }
                if (planner.voxelAt(view, neighbor) == CaveVoxel.SOLID) {
                    guards.add(neighbor);
                }
            }
        }
        for (CavePosition guard : guards) {
            actions.put(guard, HydrologyCaveAction.SEAL_GUARD);
        }
    }

    boolean isInletOpening(HydrologyCaveSource source, CavePosition position) {
        return position.equals(source.entry().offset(0, 1, 0));
    }

    boolean isGeneratedInletCarve(
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

    boolean isGeneratedInletOpening(
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
}
