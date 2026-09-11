package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.surface.SurfaceBounds;
import art.arcane.iris.engine.hydrology.surface.SurfaceCenterline;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class HydrologySurfaceDropRaster {
    private static final HydrologySurfaceDropRaster EMPTY = new HydrologySurfaceDropRaster(List.of(), Map.of());

    private final List<HydrologyColumnSample> columns;
    private final Map<Long, HydrologyColumnSample> indexed;
    private final Map<Long, OceanReceiver> oceanReceivers;

    private HydrologySurfaceDropRaster(List<HydrologyColumnSample> columns, Map<Long, OceanReceiver> oceanReceivers) {
        this.columns = List.copyOf(columns);
        Map<Long, HydrologyColumnSample> indexed = new HashMap<>(columns.size());
        for (HydrologyColumnSample sample : columns) {
            indexed.put(RiverFootprint.pack(sample.x(), sample.z()), sample);
        }
        this.indexed = Map.copyOf(indexed);
        this.oceanReceivers = Map.copyOf(oceanReceivers);
    }

    public static HydrologySurfaceDropRaster empty() {
        return EMPTY;
    }

    public static HydrologySurfaceDropRaster compile(HydrologyPlannerSettings settings,
                                                     HydrologyTerrainSampler sampler,
                                                     HydrologyGeometrySampler geometry,
                                                     RiverCourse course) {
        return compile(settings, sampler, geometry, course, null);
    }

    public static HydrologySurfaceDropRaster compile(HydrologyPlannerSettings settings,
                                                     HydrologyTerrainSampler sampler,
                                                     HydrologyGeometrySampler geometry,
                                                     RiverCourse course,
                                                     SurfaceBounds bounds) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(sampler, "sampler");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(course, "course");
        HydrologyFootprintCompiler compiler = null;
        Long2ObjectLinkedOpenHashMap<FootprintMutableColumn> columns = new Long2ObjectLinkedOpenHashMap<>();
        HydrologyFootprintCompiler.SurfaceRasterIndex plannedSurface = null;
        for (int index = 0; index < course.segments().size(); index++) {
            HydraulicSegment segment = course.segments().get(index);
            if (!segment.type().isSurface() || !segment.fallingFluid()
                    || bounds != null && !intersects(settings, segment, bounds)) {
                continue;
            }
            if (compiler == null) {
                compiler = new HydrologyFootprintCompiler(settings, sampler, geometry);
                plannedSurface = compiler.emptySurfaceRaster();
            }
            boolean clipStart = index > 0 && compiler.rasterizer.segmentsJoin(course.segments().get(index - 1), segment);
            boolean clipEnd = index + 1 < course.segments().size()
                    && compiler.rasterizer.segmentsJoin(segment, course.segments().get(index + 1));
            compiler.rasterizer.rasterizeSegment(columns, course, segment, index == 0, clipStart, clipEnd,
                    true, plannedSurface);
        }
        if (compiler == null) {
            return EMPTY;
        }
        if (bounds != null) {
            columns.long2ObjectEntrySet().removeIf(entry -> !bounds.contains(
                    RiverFootprint.unpackX(entry.getLongKey()), RiverFootprint.unpackZ(entry.getLongKey())));
        }
        Map<Long, OceanReceiver> oceanReceivers = oceanReceivers(settings, sampler, course, bounds);
        return columns.isEmpty() && oceanReceivers.isEmpty() ? EMPTY
                : new HydrologySurfaceDropRaster(compiler.buildValidationColumns(columns), oceanReceivers);
    }

    public List<HydrologyColumnSample> columns() {
        return columns;
    }

    public Optional<HydrologyColumnSample> sample(int x, int z) {
        return Optional.ofNullable(indexed.get(RiverFootprint.pack(x, z)));
    }

    public boolean connects(int x, int z, int head) {
        HydrologyColumnSample sample = indexed.get(RiverFootprint.pack(x, z));
        if (sample == null) {
            return false;
        }
        for (HydrologyColumnLayer layer : sample.layers()) {
            if (layer.channel() && layer.fluidOwned() && !layer.oceanApron()
                    && layer.bedY() < head && head <= layer.fluidHeadY()) {
                return true;
            }
        }
        return false;
    }

    public boolean containsRequiredFluid(RiverCourse course, SurfaceBounds bounds) {
        for (HydraulicSegment segment : course.segments()) {
            if (!segment.type().isSurface() || !segment.fallingFluid()) {
                continue;
            }
            HydrologyPoint start = segment.start();
            if ((bounds == null || bounds.contains(start.x(), start.z()))
                    && !connects(start.x(), start.z(), segment.upstreamHeadY())) {
                return false;
            }
            SurfaceCenterline centerline = SurfaceCenterline.densify(segment.centerline());
            for (int station = 0; station < centerline.size(); station++) {
                int x = centerline.x()[station];
                int z = centerline.z()[station];
                if ((bounds == null || bounds.contains(x, z)) && !connects(x, z, segment.downstreamHeadY())
                        && !receivingOcean(segment, x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean receivingOcean(HydraulicSegment segment, int x, int z) {
        OceanReceiver receiver = oceanReceivers.get(segment.id());
        return receiver != null && receiver.x() == x && receiver.z() == z
                && receiver.bedY() < segment.downstreamHeadY() && segment.downstreamHeadY() <= receiver.headY();
    }

    private static Map<Long, OceanReceiver> oceanReceivers(HydrologyPlannerSettings settings,
                                                          HydrologyTerrainSampler sampler,
                                                          RiverCourse course, SurfaceBounds bounds) {
        List<HydraulicSegment> segments = course.segments();
        if (segments.size() < 2) {
            return Map.of();
        }
        HydraulicSegment terminal = segments.getLast();
        boolean mouth = terminal.type() == HydrologyFeatureType.MOUTH;
        if (!mouth && terminal.type() != HydrologyFeatureType.COASTAL_GROTTO
                || mouth && terminal.upstreamHeadY() != settings.seaLevel()) {
            return Map.of();
        }
        HydraulicSegment fall = segments.get(segments.size() - 2);
        HydrologyPoint receiver = mouth ? terminal.start() : terminal.end();
        if (!fall.type().isSurface() || !fall.fallingFluid()
                || fall.downstreamHeadY() != settings.seaLevel() || terminal.downstreamHeadY() != settings.seaLevel()
                || fall.end().distanceSquared2D(receiver) != 0L
                || bounds != null && !bounds.contains(receiver.x(), receiver.z())) {
            return Map.of();
        }
        HydrologyTerrainSample terrain = sampler.sample(receiver.x(), receiver.z());
        if (terrain == null || terrain.naturalHeight() >= settings.seaLevel()
                || (mouth ? !sampler.receivingWater(receiver.x(), receiver.z(), settings.seaLevel()) : !terrain.ocean())) {
            return Map.of();
        }
        return Map.of(fall.id(), new OceanReceiver(receiver.x(), receiver.z(), terrain.naturalHeight(), settings.seaLevel()));
    }

    private static boolean intersects(HydrologyPlannerSettings settings, HydraulicSegment segment, SurfaceBounds bounds) {
        double radius = Math.max(segment.width(), settings.geometry().drops().basinWidth(segment.width())) / 2D
                + settings.surface().shoreWidth() + settings.surface().banks().maximumBlendWidth() + 2D;
        int minimumX = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int minimumZ = Integer.MAX_VALUE;
        int maximumZ = Integer.MIN_VALUE;
        for (HydrologyPoint point : segment.centerline()) {
            minimumX = Math.min(minimumX, point.x());
            maximumX = Math.max(maximumX, point.x());
            minimumZ = Math.min(minimumZ, point.z());
            maximumZ = Math.max(maximumZ, point.z());
        }
        return minimumX - radius <= bounds.maximumX() && maximumX + radius >= bounds.minimumX()
                && minimumZ - radius <= bounds.maximumZ() && maximumZ + radius >= bounds.minimumZ();
    }

    private record OceanReceiver(int x, int z, int bedY, int headY) {
    }
}
