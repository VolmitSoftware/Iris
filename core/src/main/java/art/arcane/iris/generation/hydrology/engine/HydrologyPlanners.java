package art.arcane.iris.generation.hydrology.engine;

import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.grid.HydrologyTerrainSource;
import art.arcane.iris.generation.hydrology.plan.HydrologyPlanCache;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.RegionPlanner;
import art.arcane.iris.generation.hydrology.plan.WindowPlanner;
import art.arcane.iris.generation.hydrology.water.PondPlanner;
import art.arcane.iris.generation.hydrology.water.ReachShaper;

import java.util.Objects;

public record HydrologyPlanners(RegionPlanner regions, WindowPlanner windows, HeightLattice fineLattice) {
    public HydrologyPlanners {
        Objects.requireNonNull(regions, "regions");
        Objects.requireNonNull(windows, "windows");
        Objects.requireNonNull(fineLattice, "fineLattice");
    }

    public static HydrologyPlanners create(HydrologySettings settings, HydrologyTerrainSource source) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(source, "source");
        HeightLattice fine = new HeightLattice(source, WindowPlanner.FINE_CELL, false, HydrologyPlanCache.FINE_LATTICE_CELLS);
        PondPlanner ponds = new PondPlanner(settings);
        ReachShaper shaper = new ReachShaper(settings);
        RegionPlanner regions = new RegionPlanner(settings,
                new HeightLattice(source, RegionPlanner.CELL, true, HydrologyPlanCache.REGION_LATTICE_CELLS), fine, ponds, shaper);
        return new HydrologyPlanners(regions, new WindowPlanner(settings, fine, ponds, shaper), fine);
    }
}
