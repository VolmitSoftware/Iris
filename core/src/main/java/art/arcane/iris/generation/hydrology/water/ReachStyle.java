package art.arcane.iris.generation.hydrology.water;

import art.arcane.iris.generation.hydrology.PersistedCodes;

public enum ReachStyle {
    RUN(0),
    RIFFLE(1),
    CASCADE(2),
    WATERFALL(3),
    POND_OUTLET(4),
    MOUTH(5),
    TIDAL(6);

    private static final PersistedCodes<ReachStyle> CODES = PersistedCodes.of(ReachStyle.class, ReachStyle::code);

    private final int code;

    ReachStyle(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static ReachStyle fromCode(int code) {
        return CODES.decode(code);
    }
}
