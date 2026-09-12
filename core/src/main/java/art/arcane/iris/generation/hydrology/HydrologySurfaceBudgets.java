package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.policy.SurfaceRiverPolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class HydrologySurfaceBudgets {
    private static final long SOURCE_SALT = 0x41524541534f5552L;
    private static final long INLAND_SALT = 0x41524541494e4c44L;
    private static final long COASTAL_SALT = 0x41524541434f4153L;

    private final int ownedCells;
    private final Map<SurfaceRiverPolicy.Budget, Area> areas;
    private final boolean overridden;

    private HydrologySurfaceBudgets(Sampling sampling) {
        ownedCells = sampling.ownedCells();
        areas = sampling.areas();
        overridden = sampling.overridden();
    }

    static HydrologySurfaceBudgets sample(HydrologySampledGrid grid, HydrologyPlannerSettings.Source sources) {
        LinkedHashMap<SurfaceRiverPolicy.Budget, Area> areas = new LinkedHashMap<>();
        int ownedCells = 0;
        boolean overridden = false;
        for (HydrologyGridNode node : grid.nodes()) {
            if (!grid.owns(node.x(), node.z())) {
                continue;
            }
            ownedCells++;
            HydrologyTerrainSample terrain = node.terrain();
            if (terrain.ocean() || !terrain.transitAllowed()) {
                continue;
            }
            SurfaceRiverPolicy policy = terrain.surfacePolicy();
            overridden |= policy.overridden();
            Area area = areas.computeIfAbsent(policy.budget(), ignored -> new Area(policy));
            area.landCells++;
            if (terrain.surfaceSourceAllowed() && terrain.naturalHeight() >= sources.minimumElevation()
                    && (terrain.surfaceSourceWeight() > 0D || terrain.surfaceSourceRequired())) {
                area.sourceCells++;
            }
        }
        return new HydrologySurfaceBudgets(new Sampling(ownedCells, areas, overridden));
    }

    boolean overridden() {
        return overridden;
    }

    List<Area> areas() {
        return List.copyOf(areas.values());
    }

    Area area(SurfaceRiverPolicy policy) {
        return area(policy.budget());
    }

    Area area(SurfaceRiverPolicy.Budget budget) {
        return areas.get(budget);
    }

    int sourceTarget(Area area, int outlets, HydrologyPlanner planner, HydrologyTileKey tile) {
        double density = area.policy.sourceDensity(planner.settings.surface().sources().density());
        if (density <= 0D || area.sourceCells == 0) {
            return 0;
        }
        int tributaries = area.policy.tributaries(planner.settings.routing().tributaries());
        double expected = (Math.min(64D, density) + outlets * tributaries) * fraction(area.sourceCells);
        return count(expected, planner.worldSeed, tile, area.policy, SOURCE_SALT);
    }

    int outletTarget(Area area, boolean coastal, HydrologyPlanner planner, HydrologyTileKey tile) {
        int maximum = coastal
                ? area.policy.coastalOutlets(planner.settings.outlets().maximumCoastalPerTile())
                : area.policy.inlandOutlets(planner.settings.outlets().maximumPerTile());
        return count(maximum * fraction(area.landCells), planner.worldSeed, tile, area.policy,
                coastal ? COASTAL_SALT : INLAND_SALT);
    }

    private double fraction(int cells) {
        return ownedCells == 0 ? 0D : cells / (double) ownedCells;
    }

    private static int count(double expected, long seed, HydrologyTileKey tile, SurfaceRiverPolicy policy, long salt) {
        int whole = (int) StrictMath.floor(expected);
        long stable = HydrologyHash.mix(seed, salt, tile.tileX(), tile.tileZ(),
                HydrologyHash.text(policy.areaKey()), policy.budget().hashCode());
        return whole + (HydrologyHash.unit(stable) < expected - whole ? 1 : 0);
    }

    static final class Area {
        final SurfaceRiverPolicy policy;
        int landCells;
        int sourceCells;

        private Area(SurfaceRiverPolicy policy) {
            this.policy = policy;
        }
    }

    private record Sampling(int ownedCells, Map<SurfaceRiverPolicy.Budget, Area> areas, boolean overridden) {
    }
}
