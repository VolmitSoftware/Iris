package art.arcane.iris.generation.hydrology.cave;

@FunctionalInterface
public interface HydrologyCaveGrottoShape {
    HydrologyCaveGrottoShape ELLIPSOID = (source, settings, dx, dy, dz) -> {
        double horizontalRadius = settings.grottoHorizontalRadius();
        double verticalRadius = settings.grottoVerticalRadius();
        double normalized = ((double) dx * dx / (horizontalRadius * horizontalRadius))
                + ((double) dy * dy / (verticalRadius * verticalRadius))
                + ((double) dz * dz / (horizontalRadius * horizontalRadius));
        return normalized <= 1D;
    };

    boolean contains(
            HydrologyCaveSource source,
            HydrologyCavePlannerSettings settings,
            int offsetX,
            int offsetY,
            int offsetZ
    );
}
