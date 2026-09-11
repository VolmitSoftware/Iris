package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.cave.HydrologyCavePlan;

import java.util.List;

record HydrologyRegionalNetwork(
        List<DrainageNode> nodes,
        List<DrainageEdge> edges,
        List<RiverOutlet> outlets,
        List<RiverCourse> courses,
        List<HydrologyDiagnosticCandidate> diagnostics,
        List<HydrologyCavePlan> cavePlans
) {
    static final HydrologyRegionalNetwork EMPTY = new HydrologyRegionalNetwork(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

    HydrologyRegionalNetwork {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        outlets = List.copyOf(outlets);
        courses = List.copyOf(courses);
        diagnostics = List.copyOf(diagnostics);
        cavePlans = List.copyOf(cavePlans);
    }

    int stations() {
        int count = 0;
        for (RiverCourse course : courses) {
            for (HydraulicSegment segment : course.segments()) {
                count = Math.addExact(count, segment.centerline().size());
            }
        }
        for (HydrologyCavePlan plan : cavePlans) {
            count = Math.addExact(count, plan.baselinePreconditions().size());
        }
        return count;
    }
}
