package art.arcane.iris.generation.hydrology.surface;

import art.arcane.iris.generation.hydrology.HydrologyTerrainSample;

import java.util.Objects;

public record SurfaceColumn(
        int x,
        int z,
        HydrologyTerrainSample terrain,
        int station,
        SurfaceRole role,
        int height,
        int headY,
        boolean apron
) {
    public SurfaceColumn {
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(role, "role");
    }

    /** Dry columns sit at their own height; the head follows it. */
    public SurfaceColumn withHeight(int replacementHeight) {
        return new SurfaceColumn(x, z, terrain, station, role, replacementHeight, replacementHeight, apron);
    }

    /** Wet columns keep their head; only the bed moves. */
    public SurfaceColumn withBed(int replacementBed) {
        return new SurfaceColumn(x, z, terrain, station, role, replacementBed, headY, apron);
    }
}
