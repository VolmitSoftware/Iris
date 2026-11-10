package art.arcane.iris.core.runtime.jigsaw;

public record JigsawStudioCellDimensions(int width, int height, int depth) {
    public static final int MAX_HORIZONTAL_AXIS = 128;
    public static final int MAX_HEIGHT = 192;
    public static final long MAX_VOLUME = 2_097_152L;

    public JigsawStudioCellDimensions {
        if (width < 1 || height < 1 || depth < 1) {
            throw new IllegalArgumentException("Jigsaw Studio cell dimensions must be positive");
        }
        if (width > MAX_HORIZONTAL_AXIS || depth > MAX_HORIZONTAL_AXIS) {
            throw new IllegalArgumentException("Jigsaw Studio cell width and depth cannot exceed "
                    + MAX_HORIZONTAL_AXIS + " blocks");
        }
        if (height > MAX_HEIGHT) {
            throw new IllegalArgumentException("Jigsaw Studio cell height cannot exceed "
                    + MAX_HEIGHT + " blocks");
        }
        long volume = (long) width * height * depth;
        if (volume > MAX_VOLUME) {
            throw new IllegalArgumentException("Jigsaw Studio cell volume cannot exceed "
                    + MAX_VOLUME + " blocks");
        }
    }

    public long volume() {
        return (long) width * height * depth;
    }
}
