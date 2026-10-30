package art.arcane.iris.pack.datapack.export.ir;

import java.util.Objects;

public record AquiferDef(Field barrier, Field floodedness, Field spread, Field lava, Field exclusion, Field surfaceLevel) {
    private static final Field VANILLA_BARRIER = new Field.Noise("minecraft:aquifer_barrier", 1.0, 0.5, null, null, null);
    private static final Field VANILLA_SPREAD = new Field.Noise("minecraft:aquifer_fluid_level_spread", 1.0, 0.7142857142857143, null, null, null);
    private static final Field VANILLA_LAVA = new Field.Noise("minecraft:aquifer_lava", 1.0, 1.0, null, null, null);
    private static final Field DISABLED = new Field.Const(-1.0);

    public AquiferDef {
        Objects.requireNonNull(barrier);
        Objects.requireNonNull(floodedness);
        Objects.requireNonNull(spread);
        Objects.requireNonNull(lava);
        Objects.requireNonNull(exclusion);
        Objects.requireNonNull(surfaceLevel);
    }

    public static AquiferDef dry(Field surfaceLevel) {
        return new AquiferDef(VANILLA_BARRIER, DISABLED, VANILLA_SPREAD, VANILLA_LAVA, DISABLED, surfaceLevel);
    }
}
