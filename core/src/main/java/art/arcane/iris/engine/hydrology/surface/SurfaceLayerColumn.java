package art.arcane.iris.engine.hydrology.surface;

import art.arcane.iris.engine.hydrology.HydrologyColumnLayer;
import art.arcane.iris.engine.hydrology.HydrologyTerrainSample;

import java.util.Objects;

public record SurfaceLayerColumn(
        int x,
        int z,
        HydrologyTerrainSample terrain,
        HydrologyColumnLayer layer,
        SurfaceRole role,
        boolean apron,
        int station
) {
    public SurfaceLayerColumn {
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(role, "role");
        if (station < 0) {
            throw new IllegalArgumentException("A surface column requires a nonnegative station.");
        }
    }
}
