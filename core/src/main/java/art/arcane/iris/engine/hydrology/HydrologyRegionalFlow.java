package art.arcane.iris.engine.hydrology;

import java.util.List;

final class HydrologyRegionalFlow {
    private final List<HydrologyPoint> points;
    private final int[] contributions;
    private final int maximum;

    HydrologyRegionalFlow(List<HydrologyPoint> points, int[] accumulated) {
        if (points.size() != accumulated.length) {
            throw new IllegalArgumentException("Regional flow requires one contribution per guide point.");
        }
        this.points = List.copyOf(points);
        this.contributions = new int[accumulated.length];
        int previous = 1;
        for (int index = 0; index < accumulated.length; index++) {
            previous = Math.max(previous, accumulated[index]);
            contributions[index] = previous;
        }
        this.maximum = previous;
    }

    double fraction(int x, int z) {
        return StrictMath.sqrt(contribution(x, z) / maximum);
    }

    int contributionAt(int x, int z) {
        return Math.max(1, (int) StrictMath.round(contribution(x, z)));
    }

    private double contribution(int x, int z) {
        double nearestDistance = Double.POSITIVE_INFINITY;
        double contribution = contributions[0];
        for (int index = 0; index + 1 < points.size(); index++) {
            HydrologyPoint first = points.get(index);
            HydrologyPoint next = points.get(index + 1);
            double deltaX = next.x() - first.x();
            double deltaZ = next.z() - first.z();
            double length = deltaX * deltaX + deltaZ * deltaZ;
            double progress = length <= 0D ? 0D : Math.max(0D, Math.min(1D,
                    ((x - first.x()) * deltaX + (z - first.z()) * deltaZ) / length));
            double distanceX = x - first.x() - deltaX * progress;
            double distanceZ = z - first.z() - deltaZ * progress;
            double distance = distanceX * distanceX + distanceZ * distanceZ;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                contribution = contributions[index] + (contributions[index + 1] - contributions[index]) * progress;
            }
        }
        return contribution;
    }
}
