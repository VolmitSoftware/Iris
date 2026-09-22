package art.arcane.iris.generation.hydrology;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReferenceArray;

final class HydrologyDrainageGeometry {
    private final HydrologyPlanner planner;
    private final Basin basin;
    private final int[] heads;
    private final AtomicReferenceArray<HydrologyPoint> anchors;
    private final AtomicReferenceArray<CompletableFuture<List<HydrologyPoint>>> edges;

    HydrologyDrainageGeometry(HydrologyPlanner planner, Basin basin) {
        this.planner = planner;
        this.basin = basin;
        this.heads = new int[basin.grid().nodes().size()];
        this.anchors = new AtomicReferenceArray<>(heads.length);
        this.edges = new AtomicReferenceArray<>(heads.length);
        Arrays.fill(heads, Integer.MIN_VALUE);
        planHeads();
    }

    int head(int index) {
        return heads[index];
    }

    HydrologyPoint anchor(int index) {
        HydrologyPoint existing = anchors.get(index);
        if (existing != null) {
            return existing;
        }
        HydrologyGridNode node = basin.grid().node(index);
        HydrologyPoint point;
        if (basin.parent()[index] < 0 && basin.outletIndex()[index] >= 0) {
            HydrologyPoint landward = basin.outlets().get(basin.outletIndex()[index]).outlet().landwardPoint();
            point = new HydrologyPoint(landward.x(), heads[index], landward.z());
        } else {
            point = displacedAnchor(node);
        }
        anchors.compareAndSet(index, null, point);
        return anchors.get(index);
    }

    List<HydrologyPoint> edge(int index) {
        DraftProfile profile = planner.currentDraftProfile();
        profile.routeCalls++;
        CompletableFuture<List<HydrologyPoint>> future = edges.get(index);
        if (future == null) {
            CompletableFuture<List<HydrologyPoint>> owned = new CompletableFuture<>();
            CompletableFuture<List<HydrologyPoint>> existing = edges.compareAndExchange(index, null, owned);
            if (existing == null) {
                long started = System.nanoTime();
                profile.routeSolves++;
                try {
                    List<HydrologyPoint> points = compile(index);
                    owned.complete(points);
                    return points;
                } catch (Throwable failure) {
                    owned.completeExceptionally(failure);
                    edges.compareAndSet(index, owned, null);
                    throw HydrologyPlanner.propagateOwnerFailure(failure);
                } finally {
                    profile.routeSolveNanos += System.nanoTime() - started;
                }
            }
            future = existing;
        }
        try {
            return future.join();
        } catch (CompletionException failure) {
            throw HydrologyPlanner.propagateOwnerFailure(failure.getCause());
        }
    }

    long maximumRetainedBytes() {
        int stations = Math.ceilDiv(basin.grid().spacing() * 3, planner.settings.routing().refinementSpacing()) + 2;
        return (long) heads.length * (112L + stations * 40L);
    }

    private void planHeads() {
        int[] path = new int[heads.length];
        int[] nominal = new int[heads.length];
        for (int index = 0; index < heads.length; index++) {
            if (heads[index] != Integer.MIN_VALUE || basin.outletIndex()[index] < 0) {
                continue;
            }
            int current = index;
            int size = 0;
            while (current >= 0 && heads[current] == Integer.MIN_VALUE) {
                if (size == path.length) {
                    throw new IllegalStateException("Surface drainage must be acyclic.");
                }
                path[size++] = current;
                current = basin.parent()[current];
            }
            int downstream = current < 0 ? Integer.MIN_VALUE : heads[current];
            while (size > 0) {
                int node = path[--size];
                HydrologyTerrainSample terrain = basin.grid().node(node).terrain();
                if (basin.parent()[node] < 0) {
                    HydrologyPoint landward = basin.outlets().get(basin.outletIndex()[node]).outlet().landwardPoint();
                    HydrologyTerrainSample sampled = planner.sampleLandBasisWithoutSlope(landward.x(), landward.z());
                    if (sampled != null) {
                        terrain = sampled;
                    }
                }
                int depth = (planner.settings.surface().minimumDepth() + planner.settings.surface().maximumDepth()) / 2;
                nominal[node] = terrain.naturalHeight() - planner.settings.surface().banks().sink() - depth;
                int required = terrain.naturalHeight() - planner.sourcePlanner.permittedSurfaceIncision(terrain) + depth;
                heads[node] = Math.max(Math.max(planner.settings.seaLevel(), required), downstream);
                downstream = heads[node];
            }
        }
        int[] required = heads.clone();
        int[] ceiling = new int[heads.length];
        Arrays.fill(ceiling, Integer.MAX_VALUE);
        int[] upstream = new int[heads.length];
        for (int index = 0; index < heads.length; index++) {
            if (heads[index] == Integer.MIN_VALUE) {
                continue;
            }
            if (planner.sourcePlanner.rawSourceEligible(basin.grid().node(index).terrain(),
                    planner.settings.surface().sources(), true)) {
                ceiling[index] = Math.max(required[index], nominal[index]);
            }
            int parent = basin.parent()[index];
            if (parent >= 0) {
                upstream[parent]++;
            }
        }
        int end = 0;
        for (int index = 0; index < heads.length; index++) {
            if (heads[index] != Integer.MIN_VALUE && upstream[index] == 0) {
                path[end++] = index;
            }
        }
        for (int position = 0; position < end; position++) {
            int node = path[position];
            int parent = basin.parent()[node];
            if (parent >= 0) {
                ceiling[parent] = Math.min(ceiling[parent], ceiling[node]);
                if (--upstream[parent] == 0) {
                    path[end++] = parent;
                }
            }
        }
        for (int position = end - 1; position >= 0; position--) {
            int node = path[position];
            int parent = basin.parent()[node];
            int downstream = parent < 0 ? Integer.MIN_VALUE : heads[parent];
            heads[node] = Math.max(Math.max(required[node], downstream), Math.min(nominal[node], ceiling[node]));
        }
    }

    private HydrologyPoint displacedAnchor(HydrologyGridNode node) {
        double ratio = Math.min(HydrologyRouteGeometry.ROUTE_ANCHOR_MAXIMUM_OFFSET_RATIO,
                planner.settings.geometry().meanders().maximumOffsetRatio() * 0.625D);
        double wavelength = planner.settings.geometry().meanders().primaryWavelength() * 5D;
        int x = node.x() + (int) StrictMath.round(basin.grid().spacing() * ratio
                * planner.routeAnchorX.noiseSigned(node.x() / wavelength, node.z() / wavelength));
        int z = node.z() + (int) StrictMath.round(basin.grid().spacing() * ratio
                * planner.routeAnchorZ.noiseSigned(node.x() / wavelength, node.z() / wavelength));
        if (!permittedAnchor(node, x, z)) {
            x = node.x();
            z = node.z();
        }
        if (nearCoast(node)) {
            int clearance = (int) StrictMath.ceil(planner.settings.surface().maximumWidth()
                    * planner.settings.surface().banks().springWidthRatio() / 2D) + 1;
            if (!landClearance(x, z, clearance)) {
                int radius = Math.max(1, (int) StrictMath.ceil(basin.grid().spacing()
                        * HydrologyRouteGeometry.ROUTE_ANCHOR_MAXIMUM_OFFSET_RATIO));
                long nearest = Long.MAX_VALUE;
                int selectedX = x;
                int selectedZ = z;
                for (HydrologyGridOffset offset : HydrologySourcePlanner.ROUTING_OFFSETS) {
                    int candidateX = node.x() + offset.x() * radius;
                    int candidateZ = node.z() + offset.z() * radius;
                    long dx = (long) candidateX - x;
                    long dz = (long) candidateZ - z;
                    long distance = dx * dx + dz * dz;
                    if (distance < nearest && permittedAnchor(node, candidateX, candidateZ)
                            && landClearance(candidateX, candidateZ, clearance)) {
                        nearest = distance;
                        selectedX = candidateX;
                        selectedZ = candidateZ;
                    }
                }
                x = selectedX;
                z = selectedZ;
            }
        }
        return new HydrologyPoint(x, heads[node.index()], z);
    }

    private boolean permittedAnchor(HydrologyGridNode node, int x, int z) {
        HydrologyTerrainSample terrain = planner.sampleLandBasisWithoutSlope(x, z);
        return terrain != null && !terrain.ocean() && terrain.transitAllowed()
                && node.terrain().drainsInto(terrain) && terrain.drainsInto(node.terrain())
                && HydrologySurfaceProfiles.sharesProfile(node.terrain(), terrain)
                && node.terrain().surfacePolicy().areaKey().equals(terrain.surfacePolicy().areaKey());
    }

    private boolean nearCoast(HydrologyGridNode node) {
        for (HydrologyGridOffset offset : HydrologySourcePlanner.ROUTING_OFFSETS) {
            HydrologyGridNode neighbor = basin.grid().nodeAt(node.gridX() + offset.x(), node.gridZ() + offset.z());
            if (neighbor != null && neighbor.terrain().ocean()) {
                return true;
            }
        }
        return false;
    }

    private boolean landClearance(int x, int z, int radius) {
        for (HydrologyGridOffset offset : HydrologySourcePlanner.ROUTING_OFFSETS) {
            HydrologyTerrainSample terrain = planner.sampleLandBasisWithoutSlope(x + offset.x() * radius,
                    z + offset.z() * radius);
            if (terrain == null || terrain.ocean()) {
                return false;
            }
        }
        return true;
    }

    private List<HydrologyPoint> compile(int index) {
        int downstream = basin.parent()[index];
        if (downstream < 0) {
            return List.of();
        }
        HydrologyPoint start = anchor(index);
        HydrologyPoint end = anchor(downstream);
        int continuation = basin.parent()[downstream];
        HydrologyPoint next = continuation < 0 ? basin.outlets().get(basin.outletIndex()[index])
                .outlet().connectionPoint() : anchor(continuation);
        RouteDirection startTangent = planner.routePaths.direction(start.x(), start.z(), end.x(), end.z());
        RouteDirection endTangent = planner.routePaths.direction(end.x(), end.z(), next.x(), next.z());
        if (endTangent.x() == 0D && endTangent.z() == 0D) {
            endTangent = startTangent;
        }
        double distance = StrictMath.hypot(end.x() - start.x(), end.z() - start.z());
        int steps = Math.max(1, (int) StrictMath.ceil(distance / planner.settings.routing().refinementSpacing()));
        ArrayList<HydrologyPoint> points = new ArrayList<>(steps + 1);
        for (int step = 0; step <= steps; step++) {
            double progress = step / (double) steps;
            RoutePosition position = planner.routeGeometry.routePosition(start, end, startTangent, endTangent,
                    distance * HydrologyRouteGeometry.ROUTE_TANGENT_SCALE, distance, progress, 1D);
            HydrologyPoint point = step == 0 ? start : step == steps ? end
                    : new HydrologyPoint((int) StrictMath.round(position.x()),
                    (int) StrictMath.round(start.y() + (end.y() - start.y()) * progress),
                    (int) StrictMath.round(position.z()));
            if (points.isEmpty() || points.getLast().distanceSquared2D(point) > 0L) {
                points.add(point);
            }
        }
        return permitted(points) ? List.copyOf(points) : List.of();
    }

    private boolean permitted(List<HydrologyPoint> points) {
        HydrologyTerrainSample previous = null;
        for (int index = 1; index < points.size(); index++) {
            for (HydrologyPoint point : planner.segments.rasterLine(points.get(index - 1), points.get(index))) {
                HydrologyTerrainSample terrain = planner.sampleLandBasisWithoutSlope(point.x(), point.z());
                if (terrain == null || terrain.ocean() || !terrain.transitAllowed()
                        || previous != null && (!previous.drainsInto(terrain)
                        || !HydrologySurfaceProfiles.sharesProfile(previous, terrain))) {
                    return false;
                }
                previous = terrain;
            }
        }
        return points.size() >= 2;
    }

    record Basin(HydrologySampledGrid grid, int[] parent, int[] outletIndex, List<OutletCandidate> outlets) {
    }
}
