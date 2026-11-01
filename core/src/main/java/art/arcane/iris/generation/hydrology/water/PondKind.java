package art.arcane.iris.generation.hydrology.water;

import art.arcane.iris.generation.hydrology.PersistedCodes;

public enum PondKind {
    NATURAL(0),
    SEEDED(1),
    CHAINED(2),
    LAKE(3);

    private static final PersistedCodes<PondKind> CODES = PersistedCodes.of(PondKind.class, PondKind::code);

    private final int code;

    PondKind(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static PondKind fromCode(int code) {
        return CODES.decode(code);
    }
}
