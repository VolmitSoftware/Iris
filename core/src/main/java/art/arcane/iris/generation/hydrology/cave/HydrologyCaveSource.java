package art.arcane.iris.generation.hydrology.cave;

import java.util.Objects;

public record HydrologyCaveSource(
        long sourceId,
        CavePosition entry,
        CavePosition target,
        int waterHeadY,
        HydrologyCaveMode mode
) {
    public HydrologyCaveSource {
        Objects.requireNonNull(entry);
        Objects.requireNonNull(target);
        Objects.requireNonNull(mode);
    }
}
