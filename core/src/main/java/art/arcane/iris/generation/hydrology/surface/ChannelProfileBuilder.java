package art.arcane.iris.generation.hydrology.surface;

import art.arcane.iris.generation.hydrology.HydrologyGeometrySampler;
import art.arcane.iris.generation.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSample;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSampler;

import java.util.Objects;

public final class ChannelProfileBuilder {
    private final HydrologyPlannerSettings.Surface surface;
    private final HydrologyTerrainSampler sampler;
    private final HydrologyGeometrySampler geometry;

    public ChannelProfileBuilder(
            HydrologyPlannerSettings.Surface surface,
            HydrologyTerrainSampler sampler,
            HydrologyGeometrySampler geometry
    ) {
        this.surface = Objects.requireNonNull(surface, "surface");
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        this.geometry = Objects.requireNonNull(geometry, "geometry");
    }

    public ChannelProfile build(
            SurfaceCenterline centerline,
            String profileKey,
            boolean directOcean
    ) {
        int count = centerline.size();
        double[] width = new double[count];
        double[] depth = new double[count];
        double[] bank = new double[count];
        int coast = count;
        for (int station = 0; station < count; station++) {
            int stationX = centerline.x()[station];
            int stationZ = centerline.z()[station];
            int sampledWidth = geometry.sample(
                    HydrologyGeometrySampler.Field.SURFACE_WIDTH,
                    profileKey,
                    stationX,
                    stationZ,
                    0L,
                    surface.minimumWidth(),
                    surface.maximumWidth()
            );
            int sampledDepth = geometry.sample(
                    HydrologyGeometrySampler.Field.SURFACE_DEPTH,
                    profileKey,
                    stationX,
                    stationZ,
                    0L,
                    surface.minimumDepth(),
                    surface.maximumDepth()
            );
            HydrologyTerrainSample terrain = sampler.sample(stationX, stationZ);
            if (coast == count && (terrain == null || terrain.ocean())) {
                coast = station;
            }
            double widthMultiplier = terrain == null ? 1D : terrain.widthMultiplier();
            double depthMultiplier = terrain == null ? 1D : terrain.depthMultiplier();
            bank[station] = terrain == null ? 1D : terrain.bankMultiplier();
            width[station] = clamp(sampledWidth * widthMultiplier, surface.minimumWidth(), surface.maximumWidth() * 2D);
            depth[station] = clamp(sampledDepth * depthMultiplier, 1D, surface.maximumDepth() * 2D);
        }
        HydrologyPlannerSettings.Channel channel = surface.banks().channel();
        double[] smoothWidth = smooth(width, channel.smoothingRadius());
        double[] smoothDepth = smooth(depth, channel.smoothingRadius());
        // The headwater opens as a spring pool: wider and the spring extra depth deeper, narrowing to the
        // cruise width. Ground that falls away across the pool shrinks it so it never demands a cut the
        // valley solver rejects.
        int spring = Math.min(surface.banks().springLength(), count / 2);
        double springRatio = surface.banks().springWidthRatio();
        for (int station = 0; station < spring; station++) {
            double remaining = 1D - SurfaceNoise.smoothStep(station / (double) spring);
            double localRatio = 1D + (springRatio - 1D) * springRoom(centerline, station, smoothWidth[station] * springRatio / 2D);
            smoothWidth[station] *= 1D + (localRatio - 1D) * remaining;
            smoothDepth[station] += remaining * channel.springExtraDepth();
        }
        // The inlet: over its length before the coast the channel widens toward the mouth flare and
        // its bed deepens by the inlet depth, so the estuary is wider and deeper than the river. The
        // flare completes at the shoreline, the first station in the sea, and the stations past it keep it.
        HydrologyPlannerSettings.Inlet inlet = surface.banks().inlet();
        if (directOcean && inlet.length() > 0 && coast > 1) {
            int flare = Math.min(inlet.length(), coast - 1);
            int flareStart = coast - flare;
            for (int station = flareStart; station < count; station++) {
                double progress = SurfaceNoise.smoothStep(Math.min(1D, (station - flareStart) / (double) Math.max(1, flare - 1)));
                smoothWidth[station] *= 1D + (surface.banks().mouthFlareRatio() - 1D) * progress;
                smoothDepth[station] += inlet.depth() * progress;
            }
        }
        double[] halfWidth = new double[count];
        for (int station = 0; station < count; station++) {
            halfWidth[station] = Math.max(0.5D, smoothWidth[station] / 2D);
            smoothDepth[station] = Math.max(1D, smoothDepth[station]);
        }
        return new ChannelProfile(halfWidth, smoothDepth, bank);
    }

    /** Fraction of the spring pool the ground allows: 1 on level ground, 0 where the pool ring would sit half the permitted cut lower. */
    private double springRoom(SurfaceCenterline centerline, int station, double reach) {
        int centerX = centerline.x()[station];
        int centerZ = centerline.z()[station];
        HydrologyTerrainSample center = sampler.sample(centerX, centerZ);
        if (center == null) {
            return 1D;
        }
        double normalX = centerline.normalX(station);
        double normalZ = centerline.normalZ(station);
        int lowest = center.naturalHeight();
        for (double direction = -1D; direction <= 1D; direction += 2D) {
            HydrologyTerrainSample side = sampler.sample(
                    (int) StrictMath.round(centerX + normalX * reach * direction),
                    (int) StrictMath.round(centerZ + normalZ * reach * direction));
            if (side != null) {
                lowest = Math.min(lowest, side.naturalHeight());
            }
        }
        double drop = center.naturalHeight() - lowest;
        double allowance = Math.max(1D, center.surfacePolicy().maximumIncision(surface.maximumIncision()) / 2D);
        return Math.max(0D, Math.min(1D, 1D - drop / allowance));
    }

    /** Triangle-weighted running mean over {@code radius} stations either side; a zero radius keeps every sample. */
    static double[] smooth(double[] values, int radius) {
        int count = values.length;
        if (radius <= 0) {
            return values.clone();
        }
        double[] smoothed = new double[count];
        for (int station = 0; station < count; station++) {
            double total = 0D;
            double weight = 0D;
            for (int offset = -radius; offset <= radius; offset++) {
                int index = station + offset;
                if (index < 0 || index >= count) {
                    continue;
                }
                double factor = radius + 1 - Math.abs(offset);
                total += values[index] * factor;
                weight += factor;
            }
            smoothed[station] = total / weight;
        }
        return smoothed;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
