package art.arcane.iris.generation.hydrology.surface;

import art.arcane.iris.generation.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSample;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSampler;

import java.util.Objects;

public final class SurfaceBankSupport {
    static final int BANK_LOOKAHEAD = 2;

    private final HydrologyPlannerSettings.Surface surface;
    private final int seaLevel;

    public SurfaceBankSupport(HydrologyPlannerSettings.Surface surface, int seaLevel) {
        this.surface = Objects.requireNonNull(surface, "surface");
        this.seaLevel = seaLevel;
    }

    public CrossSection crossSection(HydrologyTerrainSampler sampler, Station station, boolean oceanMouth) {
        double roughness = surface.banks().roughness();
        double outline = station.halfWidth() * (1D + roughness);
        // Every cell from the narrowest possible outline outward can end up beside water, so it joins the minimum.
        double innerOutline = station.halfWidth() * (1D - roughness);
        double reach = outline + 2D;
        double normalX = -station.tangentZ();
        double normalZ = station.tangentX();
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        for (double offset = -reach; offset <= reach; offset += 0.5D) {
            int cellX = (int) StrictMath.round(station.x() + normalX * offset);
            int cellZ = (int) StrictMath.round(station.z() + normalZ * offset);
            HydrologyTerrainSample terrain = sampler.sample(cellX, cellZ);
            boolean writable = oceanMouth ? SurfaceCellAdmission.mouthLand(terrain, seaLevel)
                    : SurfaceCellAdmission.writable(terrain, seaLevel);
            if (!writable) {
                if (oceanMouth && sampler.receivingWater(cellX, cellZ, seaLevel)) {
                    minimum = Math.min(minimum, seaLevel);
                    maximum = Math.max(maximum, seaLevel);
                    continue;
                }
                return new CrossSection(0, 0, true);
            }
            if (Math.abs(offset) < innerOutline - 0.75D) {
                continue;
            }
            minimum = Math.min(minimum, terrain.naturalHeight());
            maximum = Math.max(maximum, terrain.naturalHeight());
        }
        return new CrossSection(minimum, maximum, false);
    }

    public Perimeter perimeter(HydrologyTerrainSampler sampler, Station station, int ceiling) {
        double roughness = surface.banks().roughness();
        HydrologyPlannerSettings.Channel settings = surface.banks().channel();
        double halfWidth = station.halfWidth();
        double inner = Math.max(0D, halfWidth * Math.max(settings.outlineMinimumRatio(),
                Math.min(settings.outlineMaximumRatio(), 1D - roughness)) - 0.75D);
        double outer = halfWidth * Math.max(settings.outlineMinimumRatio(),
                Math.min(settings.outlineMaximumRatio(), 1D + roughness)) + 2D;
        int radius = (int) StrictMath.ceil(outer);
        double innerSquared = inner * inner;
        double outerSquared = outer * outer;
        int minimum = ceiling;
        boolean complete = true;
        int cliffFloor = ceiling - surface.banks().waterfallMinimumDrop();
        for (int dz = -radius; dz <= radius; dz++) {
            double rowOuter = outerSquared - (double) dz * dz;
            if (rowOuter < 0D) {
                continue;
            }
            int rowRadius = (int) StrictMath.floor(StrictMath.sqrt(rowOuter));
            int innerRadius = innerSquared > (double) dz * dz
                    ? (int) StrictMath.ceil(StrictMath.sqrt(innerSquared - (double) dz * dz)) : 0;
            for (int dx = -rowRadius; dx <= rowRadius; dx++) {
                if (innerRadius > 0 && dx > -innerRadius && dx < innerRadius) {
                    dx = innerRadius - 1;
                    continue;
                }
                double along = dx * station.tangentX() + dz * station.tangentZ();
                if (StrictMath.abs(along) > BANK_LOOKAHEAD + 0.5D) {
                    continue;
                }
                HydrologyTerrainSample terrain = sampler.sample(station.x() + dx, station.z() + dz);
                complete &= terrain != null;
                if (SurfaceCellAdmission.writable(terrain, seaLevel)
                        && terrain.naturalHeight() > cliffFloor) {
                    minimum = Math.min(minimum, terrain.naturalHeight());
                }
            }
        }
        return new Perimeter(minimum, complete);
    }

    public record Perimeter(int minimum, boolean complete) {
    }

    public record Station(int x, int z, double tangentX, double tangentZ, double halfWidth) {
    }

    public record CrossSection(int minimum, int maximum, boolean blocked) {
    }
}
