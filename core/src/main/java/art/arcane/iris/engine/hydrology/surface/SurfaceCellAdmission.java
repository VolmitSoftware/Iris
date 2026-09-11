package art.arcane.iris.engine.hydrology.surface;

import art.arcane.iris.engine.hydrology.HydrologyTerrainSample;

public final class SurfaceCellAdmission {
    private SurfaceCellAdmission() {
    }

    public static boolean writable(HydrologyTerrainSample terrain, int seaLevel) {
        return terrain != null && !terrain.ocean() && terrain.naturalHeight() > seaLevel;
    }

    public static boolean mouthLand(HydrologyTerrainSample terrain, int seaLevel) {
        return terrain != null && !terrain.ocean() && terrain.naturalHeight() >= seaLevel;
    }
}
