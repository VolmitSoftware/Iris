package art.arcane.iris.world.history;

import java.util.List;
import java.util.Objects;

public record BoundaryGeometryInfluence(double newTerrainWeight, double openingWeight, List<Contribution> contributions) {
    private static final BoundaryGeometryInfluence NONE = new BoundaryGeometryInfluence(1D, 1D, List.of());

    public BoundaryGeometryInfluence {
        if (!Double.isFinite(newTerrainWeight) || newTerrainWeight < 0D || newTerrainWeight > 1D) {
            throw new IllegalArgumentException("Geometry blend weight must be between zero and one");
        }
        if (!Double.isFinite(openingWeight) || openingWeight < 0D || openingWeight > 1D) {
            throw new IllegalArgumentException("Opening blend weight must be between zero and one");
        }
        contributions = List.copyOf(contributions);
        double total = 0D;
        for (Contribution contribution : contributions) {
            total += contribution.weight();
        }
        if (!contributions.isEmpty() && Math.abs(total - 1D) > 1E-9D) {
            throw new IllegalArgumentException("Geometry contribution weights must sum to one");
        }
    }

    public static BoundaryGeometryInfluence none() {
        return NONE;
    }

    public record Contribution(BoundaryColumnGeometry geometry, double weight) {
        public Contribution {
            Objects.requireNonNull(geometry, "Historical geometry");
            if (geometry.height() == 0) {
                throw new IllegalArgumentException("Historical boundary geometry is missing");
            }
            if (!Double.isFinite(weight) || weight <= 0D || weight > 1D) {
                throw new IllegalArgumentException("Geometry contribution weight must be positive and at most one");
            }
        }
    }
}
