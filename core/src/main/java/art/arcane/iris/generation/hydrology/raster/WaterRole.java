package art.arcane.iris.generation.hydrology.raster;

public enum WaterRole {
    NONE(0),
    CHANNEL(1),
    BANK(2),
    SHORE(3),
    POND(4),
    POND_SHORE(5),
    DAM(6),
    MOUTH(7),
    CASCADE(8),
    WATERFALL(9),
    INLET(10);

    private final int code;

    WaterRole(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public boolean wet() {
        return switch (this) {
            case CHANNEL, POND, MOUTH, CASCADE, WATERFALL, INLET -> true;
            case NONE, BANK, SHORE, POND_SHORE, DAM -> false;
        };
    }
}
