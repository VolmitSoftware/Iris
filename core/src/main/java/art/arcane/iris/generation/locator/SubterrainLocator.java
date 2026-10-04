package art.arcane.iris.generation.locator;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.subterrain.IrisSubterrainFamily;
import art.arcane.iris.generation.subterrain.SubterrainCell;
import art.arcane.iris.generation.subterrain.SubterrainBounds;
import art.arcane.iris.generation.subterrain.SubterrainPlan;
import art.arcane.iris.generation.subterrain.SubterrainPlanner;
import art.arcane.iris.generation.subterrain.SubterrainPosition;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.BooleanSupplier;

public final class SubterrainLocator {
    private static final int SEARCH_TILE_SIZE = 512;

    private SubterrainLocator() {
    }

    public static Optional<Result> nearest(Engine engine, Query query, int x, int worldY, int z, int maximumDistance) {
        return nearest(engine, query, x, worldY, z, maximumDistance, ignored -> true);
    }

    public static Optional<Result> nearest(Engine engine, Query query, int x, int worldY, int z,
                                           int maximumDistance, Predicate<Result> allowed) {
        Objects.requireNonNull(engine, "engine");
        return nearest(engine.getComplex().getSubterrainPlanner(), query, x, worldY, z, maximumDistance, allowed);
    }

    public static Optional<Result> nearest(SubterrainPlanner planner, Query query, int x, int worldY, int z,
                                           int maximumDistance, Predicate<Result> allowed) {
        return nearest(planner, query, x, worldY, z, maximumDistance, allowed, () -> true);
    }

    public static Optional<Result> nearest(SubterrainPlanner planner, Query query, int x, int worldY, int z,
                                           int maximumDistance, Predicate<Result> allowed, BooleanSupplier running) {
        Objects.requireNonNull(planner, "planner");
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(allowed, "allowed");
        if (maximumDistance < 0 || maximumDistance > 1_048_576) {
            throw new IllegalArgumentException("Subterrain search distance must be between 0 and 1048576 blocks");
        }
        if (planner.isEmpty()) {
            return Optional.empty();
        }
        long centerTileX = Math.floorDiv(x, SEARCH_TILE_SIZE);
        long centerTileZ = Math.floorDiv(z, SEARCH_TILE_SIZE);
        int tileRadius = maximumDistance / SEARCH_TILE_SIZE + 1;
        Set<String> visited = new HashSet<>();
        Result nearest = null;
        double nearestDistance = (double) maximumDistance * maximumDistance;
        int inspectedTiles = 0;
        for (int ring = 0; ring <= tileRadius; ring++) {
            for (int offsetX = -ring; offsetX <= ring; offsetX++) {
                int offsetZStep = Math.abs(offsetX) == ring ? 1 : Math.max(1, ring * 2);
                for (int offsetZ = -ring; offsetZ <= ring; offsetZ += offsetZStep) {
                    if (!running.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                        return Optional.empty();
                    }
                    long tileX = (centerTileX + offsetX) * SEARCH_TILE_SIZE;
                    long tileZ = (centerTileZ + offsetZ) * SEARCH_TILE_SIZE;
                    long tileMaxX = tileX + SEARCH_TILE_SIZE - 1L;
                    long tileMaxZ = tileZ + SEARCH_TILE_SIZE - 1L;
                    if (tileMaxX < Integer.MIN_VALUE || tileX > Integer.MAX_VALUE
                            || tileMaxZ < Integer.MIN_VALUE || tileZ > Integer.MAX_VALUE) {
                        continue;
                    }
                    double deltaTileX = x < tileX ? tileX - x : x > tileMaxX ? (long) x - tileMaxX : 0;
                    double deltaTileZ = z < tileZ ? tileZ - z : z > tileMaxZ ? (long) z - tileMaxZ : 0;
                    if (deltaTileX * deltaTileX + deltaTileZ * deltaTileZ > nearestDistance) {
                        continue;
                    }
                    if (++inspectedTiles > 65_536) {
                        throw new IllegalStateException("Subterrain search exceeded 65536 analytic tiles; use a smaller search radius");
                    }
                    for (SubterrainPlan plan : planner.plansForBounds(
                            (int) Math.max(Integer.MIN_VALUE, tileX), (int) Math.max(Integer.MIN_VALUE, tileZ),
                            (int) Math.min(Integer.MAX_VALUE, tileMaxX), (int) Math.min(Integer.MAX_VALUE, tileMaxZ))) {
                        if (!visited.add(plan.id()) || !query.matches(plan)) {
                            continue;
                        }
                        SubterrainPosition anchor = plan.anchor();
                        double deltaX = (double) anchor.x() - x;
                        double deltaY = (double) anchor.y() - worldY;
                        double deltaZ = (double) anchor.z() - z;
                        double distance = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
                        if (distance > nearestDistance) {
                            continue;
                        }
                        SubterrainCell owner = planner.sample(anchor.x(), anchor.y(), anchor.z());
                        if (!owner.occupied() || !owner.room().featureId().equals(plan.id())) {
                            continue;
                        }
                        Result candidate = new Result(plan.id(), plan.family(), plan.biome(), anchor.x(), anchor.y(), anchor.z());
                        if (!allowed.test(candidate) || nearest != null && distance == nearestDistance
                                && candidate.featureId().compareTo(nearest.featureId()) >= 0) {
                            continue;
                        }
                        nearest = candidate;
                        nearestDistance = distance;
                    }
                }
            }
            long leftDistance = (long) x - (centerTileX - ring) * SEARCH_TILE_SIZE + 1;
            long rightDistance = (centerTileX + ring + 1) * SEARCH_TILE_SIZE - x;
            long frontDistance = (long) z - (centerTileZ - ring) * SEARCH_TILE_SIZE + 1;
            long backDistance = (centerTileZ + ring + 1) * SEARCH_TILE_SIZE - z;
            long remainingDistance = Math.min(Math.min(leftDistance, rightDistance), Math.min(frontDistance, backDistance));
            if ((double) remainingDistance * remainingDistance > nearestDistance) {
                break;
            }
        }
        return Optional.ofNullable(nearest);
    }

    public static Optional<Result> dryLanding(SubterrainPlanner planner, Result target, BooleanSupplier running) {
        return dryLanding(planner, target, running, ignored -> true);
    }

    public static Optional<Result> dryLanding(SubterrainPlanner planner, Result target, BooleanSupplier running,
                                               Predicate<Result> allowed) {
        for (SubterrainPlan plan : planner.plansForBounds(target.x(), target.z(), target.x(), target.z())) {
            if (!plan.id().equals(target.featureId())) {
                continue;
            }
            SubterrainBounds bounds = plan.bounds();
            int radiusLimit = Math.min(512, Math.max(Math.max(target.x() - bounds.minX(), bounds.maxX() - target.x()),
                    Math.max(target.z() - bounds.minZ(), bounds.maxZ() - target.z())));
            for (int radius = 0; radius <= radiusLimit; radius++) {
                if (!running.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                    return Optional.empty();
                }
                int stride = Math.max(1, radius / 8);
                for (int offset = -radius; offset <= radius; offset += stride) {
                    if (!running.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                        return Optional.empty();
                    }
                    SubterrainPosition landing = eligibleDryLanding(planner, plan, target, target.x() + offset, target.z() - radius, allowed);
                    if (landing == null && radius != 0) {
                        landing = eligibleDryLanding(planner, plan, target, target.x() + offset, target.z() + radius, allowed);
                    }
                    if (landing == null && radius != 0) {
                        landing = eligibleDryLanding(planner, plan, target, target.x() - radius, target.z() + offset, allowed);
                    }
                    if (landing == null && radius != 0) {
                        landing = eligibleDryLanding(planner, plan, target, target.x() + radius, target.z() + offset, allowed);
                    }
                    if (landing != null) {
                        return Optional.of(new Result(target.featureId(), target.family(), target.biome(),
                                landing.x(), landing.y(), landing.z()));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static SubterrainPosition eligibleDryLanding(SubterrainPlanner planner, SubterrainPlan plan,
                                                           Result target, int x, int z, Predicate<Result> allowed) {
        SubterrainPosition landing = dryOwnedLanding(planner, plan, x, z);
        return landing != null && allowed.test(new Result(target.featureId(), target.family(), target.biome(),
                landing.x(), landing.y(), landing.z())) ? landing : null;
    }

    private static SubterrainPosition dryOwnedLanding(SubterrainPlanner planner, SubterrainPlan plan, int x, int z) {
        SubterrainPosition landing = plan.dryLandingAt(x, z);
        if (landing == null) {
            return null;
        }
        SubterrainCell feet = planner.sample(landing.x(), landing.y(), landing.z());
        SubterrainCell head = planner.sample(landing.x(), landing.y() + 1, landing.z());
        SubterrainCell floor = planner.sample(landing.x(), landing.y() - 1, landing.z());
        return feet.kind() == SubterrainCell.Kind.AIR && head.kind() == SubterrainCell.Kind.AIR && floor.solid()
                && feet.room().featureId().equals(plan.id()) && head.room().featureId().equals(plan.id())
                ? landing : null;
    }

    public record Query(String featureId, IrisSubterrainFamily family, String biome) {
        public Query {
            featureId = Objects.requireNonNullElse(featureId, "").trim();
            biome = Objects.requireNonNullElse(biome, "").trim();
        }

        public boolean matches(SubterrainPlan plan) {
            return (featureId.isEmpty() || plan.id().equals(featureId) || plan.id().startsWith(featureId + ":"))
                    && (family == null || plan.family() == family)
                    && (biome.isEmpty() || plan.biome().equals(biome));
        }
    }

    public record Result(String featureId, IrisSubterrainFamily family, String biome, int x, int y, int z) {
    }
}
