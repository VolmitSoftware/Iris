package art.arcane.iris.generation.hydrology;

public record HydrologyColumnSnapshot(boolean available, HydrologyColumnSample column) {
    private static final HydrologyColumnSnapshot UNAVAILABLE = new HydrologyColumnSnapshot(false, null);
    private static final HydrologyColumnSnapshot READY_NATURAL = new HydrologyColumnSnapshot(true, null);

    public HydrologyColumnSnapshot {
        if (!available && column != null) {
            throw new IllegalArgumentException("Unavailable hydrology cannot contain a planned column.");
        }
    }

    public static HydrologyColumnSnapshot unavailable() {
        return UNAVAILABLE;
    }

    public static HydrologyColumnSnapshot ready(HydrologyColumnSample column) {
        return column == null ? READY_NATURAL : new HydrologyColumnSnapshot(true, column);
    }
}
