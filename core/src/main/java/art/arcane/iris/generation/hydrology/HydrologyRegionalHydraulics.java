package art.arcane.iris.generation.hydrology;

final class HydrologyRegionalHydraulics {
    private final HydrologyPlannerSettings settings;

    HydrologyRegionalHydraulics(HydrologyPlannerSettings settings) {
        this.settings = settings;
    }

    int minimumHead(HydrologyTerrainSample terrain) {
        return minimumHead(terrain, terrain.surfacePolicy().maximumIncision(settings.surface().maximumIncision()));
    }

    int inletMinimumHead(HydrologyTerrainSample terrain) {
        return minimumHead(terrain, Math.max(terrain.surfacePolicy().maximumIncision(settings.surface().maximumIncision()),
                settings.surface().banks().inlet().maximumIncision()));
    }

    int maximumHead(HydrologyTerrainSample terrain) {
        return Math.max(settings.seaLevel(), terrain.naturalHeight() - settings.surface().banks().sink());
    }

    private int minimumHead(HydrologyTerrainSample terrain, int incision) {
        int permitted = Math.min(incision, (int) StrictMath.floor(incision * terrain.incisionMultiplier()));
        int depth = (int) StrictMath.round(Math.max(1D, Math.min(settings.surface().maximumDepth() * 2D,
                settings.surface().minimumDepth() * terrain.depthMultiplier())));
        return Math.max(settings.seaLevel(), terrain.naturalHeight() - permitted + depth);
    }

}
