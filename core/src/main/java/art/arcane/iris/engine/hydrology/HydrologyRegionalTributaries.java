package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.surface.SurfaceBounds;
import art.arcane.iris.engine.hydrology.surface.SurfaceFootprint;
import art.arcane.iris.engine.hydrology.surface.SurfaceFootprintCompiler;
import art.arcane.iris.engine.hydrology.surface.SurfaceLayerColumn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.OptionalLong;

final class HydrologyRegionalTributaries {
    private static final long JUNCTION_SALT = 0x5245474a554e4354L;

    private final HydrologyPlanner planner;

    HydrologyRegionalTributaries(HydrologyPlanner planner) {
        this.planner = planner;
    }

    CompiledGraph connect(CompiledGraph graph, List<RiverCourse> courses,
                          HydrologyFootprintCompiler footprints, List<HydrologyDiagnosticCandidate> diagnostics) {
        HydrologyRegionalNetwork regional = footprints.regionalNetwork;
        if (regional.courses().isEmpty()) {
            return graph;
        }
        LinkedHashMap<Long, DrainageNode> nodes = new LinkedHashMap<>();
        LinkedHashMap<Long, DrainageEdge> edges = new LinkedHashMap<>();
        LinkedHashMap<Long, RiverOutlet> outlets = new LinkedHashMap<>();
        for (DrainageNode node : graph.nodes()) { nodes.put(node.id(), node); }
        for (DrainageEdge edge : graph.edges()) { edges.put(edge.id(), edge); }
        for (RiverOutlet outlet : graph.outlets()) { outlets.put(outlet.id(), outlet); }
        Map<Long, DrainageNode> regionalNodes = new HashMap<>();
        Map<Long, RiverOutlet> regionalOutlets = new HashMap<>();
        for (DrainageNode node : regional.nodes()) { regionalNodes.put(node.id(), node); }
        for (RiverOutlet outlet : regional.outlets()) { regionalOutlets.put(outlet.id(), outlet); }
        ListIterator<RiverCourse> iterator = courses.listIterator();
        while (iterator.hasNext()) {
            RiverCourse local = iterator.next();
            if (local.type() != RiverCourseType.SURFACE || !planner.regional.conflicts(local, regional.courses())) {
                continue;
            }
            DrainageNode source = nodes.get(local.sourceNodeId().orElseThrow());
            Junction accepted = null;
            for (RiverCourse stem : regional.courses()) {
                if (!planner.regional.conflicts(local, List.of(stem)) || source == null) {
                    continue;
                }
                RiverOutlet outlet = regionalOutlets.get(stem.outletId().orElseThrow());
                accepted = build(local, source, new Receiver(stem, outlet, regionalNodes), footprints, diagnostics);
                if (accepted != null) {
                    break;
                }
            }
            if (accepted == null || conflictsOtherStem(accepted.course(), regional.courses())) {
                iterator.remove();
                planner.tributaries.addTributaryDiagnostic(local.id(), local.segments().getFirst().start(),
                        HydrologyCandidateRejection.NO_DRAINAGE_PATH, 0, diagnostics);
                continue;
            }
            iterator.set(accepted.course());
            for (DrainageNode node : accepted.nodes()) { nodes.put(node.id(), node); }
            for (DrainageEdge edge : accepted.course().drainageEdges()) { edges.put(edge.id(), edge); }
            outlets.put(accepted.outlet().id(), accepted.outlet());
        }
        return new CompiledGraph(List.copyOf(nodes.values()), List.copyOf(edges.values()),
                List.copyOf(outlets.values()), graph.edgeByUpstream());
    }

    private Junction build(RiverCourse local, DrainageNode source, Receiver receiver,
                           HydrologyFootprintCompiler footprints, List<HydrologyDiagnosticCandidate> diagnostics) {
        if (receiver.outlet() == null || local.drainageEdges().isEmpty()) {
            return null;
        }
        List<HydrologyPoint> points = points(local);
        ArrayList<DrainageEdge> pairs = new ArrayList<>(Math.max(0, points.size() - 1));
        for (int index = 1; index < points.size(); index++) {
            pairs.add(local.drainageEdges().getFirst());
        }
        HydrologyCoursePath path = new HydrologyCoursePath(points, pairs, local.drainageEdges(), receiver.outlet(),
                false, false, null);
        HydrologyGridNode sourceNode = new HydrologyGridNode(0, 0, 0, source.x(), source.z(), source.id(), source.terrain());
        RiverCourse tributary = planner.tributaries.buildSurfaceTributary(
                new SurfaceCourseDraft(sourceNode, local.id(), local.profileKey(), path), receiver.course(), diagnostics);
        if (tributary == null || !confined(tributary)) {
            return null;
        }
        SurfaceFootprint footprint = footprints.surfaceFootprint(tributary);
        if (!footprint.accepted() || !joinsWater(tributary, footprint, receiver.course())) {
            if (!footprint.accepted()) {
                planner.tributaries.addTributaryDiagnostic(local.id(), local.segments().getFirst().start(),
                        footprint.rejection(), footprint.rejectionDetail(), diagnostics);
            }
            return null;
        }
        return reroot(tributary, source, receiver);
    }

    private boolean conflictsOtherStem(RiverCourse course, List<RiverCourse> stems) {
        for (RiverCourse stem : stems) {
            if (stem.outletId().getAsLong() != course.outletId().getAsLong()
                    && planner.regional.conflicts(course, List.of(stem))) {
                return true;
            }
        }
        return false;
    }

    private boolean joinsWater(RiverCourse tributary, SurfaceFootprint footprint, RiverCourse stem) {
        HydrologyPoint end = tributary.segments().getLast().end();
        SurfaceBounds bounds = new SurfaceBounds(end.x(), end.z(), end.x(), end.z());
        SurfaceFootprint receiving = new SurfaceFootprintCompiler(planner.settings,
                planner::sampleBasisWithoutSlope, planner.geometrySampler).compile(stem, bounds);
        HydrologyColumnLayer receiver = null;
        for (SurfaceLayerColumn column : receiving.columns()) {
            if (column.layer().fluidOwned()) {
                receiver = column.layer();
                break;
            }
        }
        if (!receiving.accepted() || receiver == null) {
            return false;
        }
        for (SurfaceLayerColumn column : footprint.columns()) {
            HydrologyColumnLayer layer = column.layer();
            if (column.x() == end.x() && column.z() == end.z() && layer.fluidOwned()) {
                return layer.fluidHeadY() == receiver.fluidHeadY()
                        && Math.max(layer.bedY(), receiver.bedY()) < layer.fluidHeadY();
            }
        }
        return false;
    }

    private boolean confined(RiverCourse course) {
        HydrologyTerrainSample previous = null;
        List<HydrologyPoint> points = points(course);
        for (int index = 1; index < points.size(); index++) {
            HydrologyPoint start = points.get(index - 1);
            HydrologyPoint end = points.get(index);
            int steps = Math.max(Math.abs(end.x() - start.x()), Math.abs(end.z() - start.z()));
            for (int step = index == 1 ? 0 : 1; step <= steps; step++) {
                double progress = steps == 0 ? 0D : step / (double) steps;
                int x = (int) StrictMath.round(start.x() + (end.x() - start.x()) * progress);
                int z = (int) StrictMath.round(start.z() + (end.z() - start.z()) * progress);
                HydrologyTerrainSample terrain = planner.sampleBasisWithoutSlope(x, z);
                if (terrain == null || terrain.ocean() || !terrain.transitAllowed()
                        || !terrain.preferredProfileKeys().contains(course.profileKey())
                        || previous != null && !previous.drainsInto(terrain)) {
                    return false;
                }
                previous = terrain;
            }
        }
        return true;
    }

    private Junction reroot(RiverCourse tributary, DrainageNode source, Receiver receiver) {
        HydrologyPoint end = tributary.segments().getLast().end();
        DrainageEdge nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (DrainageEdge edge : receiver.course().drainageEdges()) {
            HydrologyPoint start = edge.centerline().getFirst();
            HydrologyPoint finish = edge.centerline().getLast();
            double deltaX = finish.x() - start.x();
            double deltaZ = finish.z() - start.z();
            double squared = deltaX * deltaX + deltaZ * deltaZ;
            double progress = squared == 0D ? 0D : Math.max(0D, Math.min(1D,
                    ((end.x() - start.x()) * deltaX + (end.z() - start.z()) * deltaZ) / squared));
            double distance = StrictMath.hypot(end.x() - start.x() - deltaX * progress,
                    end.z() - start.z() - deltaZ * progress);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = edge;
            }
        }
        if (nearest == null) {
            return null;
        }
        DrainageNode downstream = receiver.nodes().get(nearest.downstreamNodeId());
        HydrologyTerrainSample junctionTerrain = planner.sampleBasisWithoutSlope(end.x(), end.z());
        long outletId = receiver.outlet().id();
        long sourceId = HydrologyHash.mix(tributary.id(), JUNCTION_SALT, source.id());
        long junctionId = HydrologyHash.mix(tributary.id(), JUNCTION_SALT, end.x(), end.z());
        double connectorLength = Math.max(1D, StrictMath.sqrt(end.distanceSquared2D(downstream.naturalPoint())));
        double sourceLength = Math.max(1D, length(points(tributary)));
        DrainageNode junction = new DrainageNode(junctionId, end.x(), end.z(), junctionTerrain,
                downstream.potential() + connectorLength, outletId);
        DrainageNode origin = new DrainageNode(sourceId, source.x(), source.z(), source.terrain(),
                junction.potential() + sourceLength, outletId);
        DrainageEdge branch = new DrainageEdge(HydrologyHash.mix(sourceId, junctionId), sourceId, junctionId,
                outletId, sourceLength, tributary.discharge(), 0, points(tributary));
        DrainageEdge connector = new DrainageEdge(HydrologyHash.mix(junctionId, downstream.id()), junctionId, downstream.id(),
                outletId, connectorLength, tributary.discharge(), 0, List.of(end, downstream.naturalPoint()));
        RiverCourse connected = new RiverCourse(tributary.id(), tributary.type(), OptionalLong.of(sourceId),
                OptionalLong.of(outletId), tributary.profileKey(), tributary.discharge(), List.of(branch, connector), tributary.segments());
        return new Junction(connected, List.of(origin, junction, downstream), receiver.outlet());
    }

    private static List<HydrologyPoint> points(RiverCourse course) {
        ArrayList<HydrologyPoint> points = new ArrayList<>();
        for (HydraulicSegment segment : course.segments()) {
            if (!segment.type().isSurface()) {
                break;
            }
            for (HydrologyPoint point : segment.centerline()) {
                if (points.isEmpty() || point.distanceSquared2D(points.getLast()) > 0L) {
                    points.add(point);
                }
            }
        }
        return List.copyOf(points);
    }

    private static double length(List<HydrologyPoint> points) {
        double length = 0D;
        for (int index = 1; index < points.size(); index++) {
            length += StrictMath.sqrt(points.get(index).distanceSquared2D(points.get(index - 1)));
        }
        return length;
    }

    private record Receiver(RiverCourse course, RiverOutlet outlet, Map<Long, DrainageNode> nodes) {
    }

    private record Junction(RiverCourse course, List<DrainageNode> nodes, RiverOutlet outlet) {
    }
}
