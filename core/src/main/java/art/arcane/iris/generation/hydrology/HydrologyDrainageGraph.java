package art.arcane.iris.generation.hydrology;

final class HydrologyDrainageGraph {
    private final HydrologyPlannerSettings settings;
    private final int[] upstream;
    private final double[] costs;
    private final byte[] eligibility;

    HydrologyDrainageGraph(HydrologySampledGrid grid, HydrologySourcePlanner planner) {
        settings = planner.settings();
        int directions = HydrologySourcePlanner.ROUTING_OFFSETS.size();
        int size = Math.multiplyExact(grid.nodes().size(), directions);
        upstream = new int[size];
        costs = new double[size];
        eligibility = new byte[size];
        for (HydrologyGridNode downstream : grid.nodes()) {
            int valleyFloor = planner.minimumNeighborHeight(grid, downstream);
            for (int direction = 0; direction < directions; direction++) {
                int edge = downstream.index() * directions + direction;
                HydrologyGridOffset offset = HydrologySourcePlanner.ROUTING_OFFSETS.get(direction);
                HydrologyGridNode source = grid.nodeAt(downstream.gridX() + offset.x(), downstream.gridZ() + offset.z());
                upstream[edge] = source == null ? -1 : source.index();
                if (source == null || source.terrain().ocean() || !source.terrain().transitAllowed()
                        || !source.terrain().drainsInto(downstream.terrain())) {
                    continue;
                }
                eligibility[edge] = 1;
                if (HydrologySurfaceProfiles.sharesProfile(source.terrain(), downstream.terrain())
                        && downstream.terrain().naturalHeight() - source.terrain().naturalHeight()
                        <= planner.maximumSurfaceEdgeRise(downstream.terrain())) {
                    eligibility[edge] |= 2;
                }
                costs[edge] = planner.routeCost(source, downstream, offset, valleyFloor);
            }
        }
    }

    HydrologyPlannerSettings settings() {
        return settings;
    }

    int upstream(int downstream, int direction, boolean surface) {
        int edge = downstream * HydrologySourcePlanner.ROUTING_OFFSETS.size() + direction;
        return (eligibility[edge] & (surface ? 2 : 1)) == 0 ? -1 : upstream[edge];
    }

    double cost(int downstream, int direction) {
        return costs[downstream * HydrologySourcePlanner.ROUTING_OFFSETS.size() + direction];
    }
}
