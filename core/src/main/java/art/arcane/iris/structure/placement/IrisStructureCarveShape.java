package art.arcane.iris.structure.placement;

import art.arcane.volmlib.util.documentation.Description;

@Description("Controls the shape of the expanded terrain carve around an overbored structure.")
public enum IrisStructureCarveShape {
    @Description("Carves the full expanded rectangular volume with straight walls, ceiling, and floor.")
    BOX,

    @Description("Carves a smooth rounded volume around the structure without erosion noise.")
    ROUNDED,

    @Description("Carves a rounded volume whose outer boundary is eroded by deterministic noise.")
    ERODED;

    public double maximumCeilingScale() {
        return maximumCeilingScale(1D);
    }

    public double maximumCeilingScale(double erosionStrength) {
        double strength = Double.isFinite(erosionStrength)
                ? Math.max(0D, Math.min(1D, erosionStrength)) : 1D;
        return switch (this) {
            case BOX, ROUNDED -> 1D;
            case ERODED -> 1D + 0.8D * strength;
        };
    }

    public int maximumCeilingExtension(int configuredHeight) {
        return maximumCeilingExtension(configuredHeight, 1D);
    }

    public int maximumCeilingExtension(int configuredHeight, double erosionStrength) {
        return (int) Math.ceil(Math.max(0, configuredHeight) * maximumCeilingScale(erosionStrength));
    }
}
