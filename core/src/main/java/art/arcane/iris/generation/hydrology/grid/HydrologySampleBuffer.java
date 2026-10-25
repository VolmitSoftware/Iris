package art.arcane.iris.generation.hydrology.grid;

public final class HydrologySampleBuffer {
    public static final byte ALLOW_STREAMS = 1;
    public static final byte ALLOW_PONDS = 2;

    private final float[] height;
    private final boolean[] ocean;
    private final float[] rainfall;
    private final float[] pondDensity;
    private final byte[] permission;
    private final String[] fluidProfile;

    public HydrologySampleBuffer(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Invalid sample buffer capacity=" + capacity);
        }
        this.height = new float[capacity];
        this.ocean = new boolean[capacity];
        this.rainfall = new float[capacity];
        this.pondDensity = new float[capacity];
        this.permission = new byte[capacity];
        this.fluidProfile = new String[capacity];
    }

    public float[] height() {
        return height;
    }

    public boolean[] ocean() {
        return ocean;
    }

    public float[] rainfall() {
        return rainfall;
    }

    public float[] pondDensity() {
        return pondDensity;
    }

    public byte[] permission() {
        return permission;
    }

    public String[] fluidProfile() {
        return fluidProfile;
    }
}
