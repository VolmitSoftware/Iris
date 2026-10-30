package art.arcane.iris.pack.datapack.export.ir;

import java.util.Objects;

public record RouterDef(
        Field temperature,
        Field vegetation,
        Field continents,
        Field erosion,
        Field depth,
        Field ridges,
        Field chunkSurfaceLevel,
        Field finalDensity,
        AquiferDef aquifer
) {
    public RouterDef {
        Objects.requireNonNull(temperature);
        Objects.requireNonNull(vegetation);
        Objects.requireNonNull(continents);
        Objects.requireNonNull(erosion);
        Objects.requireNonNull(depth);
        Objects.requireNonNull(ridges);
        Objects.requireNonNull(chunkSurfaceLevel);
        Objects.requireNonNull(finalDensity);
        Objects.requireNonNull(aquifer);
    }
}
