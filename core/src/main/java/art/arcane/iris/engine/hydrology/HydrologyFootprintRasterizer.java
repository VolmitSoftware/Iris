package art.arcane.iris.engine.hydrology;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

import java.util.ArrayList;
import java.util.List;

final class HydrologyFootprintRasterizer {
    private final HydrologyFootprintCompiler compiler;

    HydrologyFootprintRasterizer(HydrologyFootprintCompiler compiler) {
        this.compiler = compiler;
    }

    void rasterizeSegment(
            Long2ObjectLinkedOpenHashMap<FootprintMutableColumn> columns,
            RiverCourse course,
            HydraulicSegment segment,
            boolean firstSegment,
            boolean clipStart,
            boolean clipEnd,
            boolean validationOnly,
            HydrologyFootprintCompiler.SurfaceRasterIndex plannedSurface
    ) {
        Long2ObjectLinkedOpenHashMap<FootprintMutableColumn> segmentColumns = segment.type().isSurface() && segment.fallingFluid()
                ? new Long2ObjectLinkedOpenHashMap<>() : columns;
        List<HydrologyPoint> centerline = continuousCenterline(segment);
        if (segment.fallingFluid() && centerline.size() > 1) {
            HydrologyPoint throat = centerline.getFirst();
            int fluidHead = segment.upstreamHeadY();
            FootprintLayerShape throatShape = compiler.channelGeometry.shape(
                    course,
                    segment,
                    throat,
                    segment.downstreamHeadY() - 1,
                    fluidHead,
                    true,
                    false
            );
            rasterizePoint(
                    segmentColumns,
                    course,
                    segment,
                    throat,
                    0,
                    throatShape,
                    compiler.flowDelta(centerline, 0, true),
                    compiler.flowDelta(centerline, 0, false),
                    firstSegment,
                    true,
                    false,
                    false,
                    validationOnly,
                    plannedSurface
            );
            rasterizeSweptSegment(
                    segmentColumns,
                    course,
                    segment,
                    List.copyOf(centerline.subList(1, centerline.size())),
                    false,
                    true,
                    clipEnd,
                    validationOnly,
                    plannedSurface
            );
            if (segmentColumns != columns) {
                compiler.mergeSurfaceDrop(columns, segmentColumns);
            }
            return;
        }
        if (sweptChannel(segment, centerline)) {
            rasterizeSweptSegment(
                    segmentColumns,
                    course,
                    segment,
                    centerline,
                    firstSegment,
                    clipStart,
                    clipEnd,
                    validationOnly,
                    plannedSurface
            );
            return;
        }
        for (int pointIndex = 0; pointIndex < centerline.size(); pointIndex++) {
            HydrologyPoint point = centerline.get(pointIndex);
            boolean oceanConnection = (segment.type() == HydrologyFeatureType.MOUTH
                    || segment.type() == HydrologyFeatureType.COASTAL_GROTTO)
                    && pointIndex == centerline.size() - 1;
            boolean falling = segment.fallingFluid() && pointIndex == 0;
            boolean receiving = segment.receivingPool() && pointIndex == centerline.size() - 1;
            int fluidHead = falling ? segment.upstreamHeadY() : point.y();
            int bed = falling
                    ? segment.downstreamHeadY() - 1
                    : fluidHead - segment.depth();
            FootprintLayerShape shape = compiler.channelGeometry.shape(course, segment, point, bed, fluidHead, falling, receiving);
            int flowX = compiler.flowDelta(centerline, pointIndex, true);
            int flowZ = compiler.flowDelta(centerline, pointIndex, false);
            rasterizePoint(
                    segmentColumns,
                    course,
                    segment,
                    point,
                    pointIndex,
                    shape,
                    flowX,
                    flowZ,
                    firstSegment && pointIndex == 0,
                    falling,
                    receiving,
                    oceanConnection,
                    validationOnly,
                    plannedSurface
            );
        }
        if (segmentColumns != columns) {
            compiler.mergeSurfaceDrop(columns, segmentColumns);
        }
    }

    boolean sweptChannel(
            HydraulicSegment segment,
            List<HydrologyPoint> centerline
    ) {
        if (centerline.size() < 2 || segment.fallingFluid()) {
            return false;
        }
        return segment.type().isSurface()
                || segment.type() == HydrologyFeatureType.UNDERGROUND_POOL
                || segment.type() == HydrologyFeatureType.UNDERGROUND_DROP
                || segment.type() == HydrologyFeatureType.SINKHOLE
                || segment.type() == HydrologyFeatureType.DEEP_CHANNEL;
    }

    void rasterizeSweptSegment(
            Long2ObjectLinkedOpenHashMap<FootprintMutableColumn> columns,
            RiverCourse course,
            HydraulicSegment segment,
            List<HydrologyPoint> centerline,
            boolean firstSegment,
            boolean clipStart,
            boolean clipEnd,
            boolean validationOnly,
            HydrologyFootprintCompiler.SurfaceRasterIndex plannedSurface
    ) {
        FootprintLayerShape[] shapes = new FootprintLayerShape[centerline.size()];
        int maximumRadius = 1;
        int minimumX = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int minimumZ = Integer.MAX_VALUE;
        int maximumZ = Integer.MIN_VALUE;
        for (int pointIndex = 0; pointIndex < centerline.size(); pointIndex++) {
            HydrologyPoint point = centerline.get(pointIndex);
            int fluidHead = point.y();
            boolean receiving = segment.receivingPool() && pointIndex == centerline.size() - 1;
            FootprintLayerShape shape = compiler.channelGeometry.shape(
                    course,
                    segment,
                    point,
                    fluidHead - segment.depth(),
                    fluidHead,
                    false,
                    receiving
            );
            shapes[pointIndex] = shape;
            int rasterRadius = segment.type().isSurface()
                    ? (int) StrictMath.ceil(shape.totalRadius())
                    : shape.channelRadius();
            maximumRadius = Math.max(maximumRadius, rasterRadius);
            minimumX = Math.min(minimumX, point.x());
            maximumX = Math.max(maximumX, point.x());
            minimumZ = Math.min(minimumZ, point.z());
            maximumZ = Math.max(maximumZ, point.z());
        }
        minimumX = Math.subtractExact(minimumX, maximumRadius);
        maximumX = Math.addExact(maximumX, maximumRadius);
        minimumZ = Math.subtractExact(minimumZ, maximumRadius);
        maximumZ = Math.addExact(maximumZ, maximumRadius);
        HydrologyFeatureRef[][] pointFeatures = new HydrologyFeatureRef[centerline.size()][HydrologyFootprintCompiler.FEATURE_ROLE_COUNT];
        for (int z = minimumZ; z <= maximumZ; z++) {
            for (int x = minimumX; x <= maximumX; x++) {
                if (!withinLongitudinalBounds(centerline, segment.start(), x, z, clipStart, clipEnd)) {
                    continue;
                }
                FootprintCenterlineProjection projection = projectCenterline(centerline, segment.start(), x, z);
                int pointIndex = projection.pointIndex();
                HydrologyPoint point = centerline.get(pointIndex);
                boolean receiving = segment.receivingPool() && pointIndex == centerline.size() - 1;
                FootprintLayerShape shape = shapes[pointIndex];
                int deltaX = x - point.x();
                int deltaZ = z - point.z();
                double rawDistance = projection.distance();
                double rasterRadius = segment.type().isSurface()
                        ? shape.totalRadius()
                        : shape.channelRadius();
                if (rawDistance > rasterRadius + 0.25D) {
                    continue;
                }
                int flowX = projection.flowX();
                int flowZ = projection.flowZ();
                double channelDistance = compiler.channelGeometry.shapedDistance(
                        shape,
                        segment,
                        x,
                        z,
                        x - projection.x(),
                        z - projection.z(),
                        flowX,
                        flowZ,
                        rawDistance
                );
                boolean channel = channelDistance <= shape.channelRadius() + 0.25D;
                if (!segment.type().isSurface() && !channel) {
                    continue;
                }
                boolean shore = !channel
                        && channelDistance <= shape.channelRadius() + shape.shoreWidth() + 0.25D;
                boolean grading = !channel && !shore && rawDistance <= shape.totalRadius() + 0.25D;
                if (!channel && !shore && !grading) {
                    continue;
                }
                double distance = grading ? rawDistance : channelDistance;
                HydrologyRoutingTerrainSampler.NaturalClassification classification = compiler.classifyNatural(x, z);
                boolean oceanConnection = (segment.type() == HydrologyFeatureType.MOUTH
                        || segment.type() == HydrologyFeatureType.COASTAL_GROTTO)
                        && pointIndex == centerline.size() - 1;
                boolean apronEligible = compiler.oceanApronEligible(segment, distance);
                if (classification == HydrologyRoutingTerrainSampler.NaturalClassification.OCEAN
                        && !apronEligible) {
                    continue;
                }
                if (classification == HydrologyRoutingTerrainSampler.NaturalClassification.LAND
                        && oceanConnection) {
                    continue;
                }
                boolean exactSlope = classification != HydrologyRoutingTerrainSampler.NaturalClassification.OCEAN
                        && !oceanConnection
                        && !channel
                        && segment.type() != HydrologyFeatureType.WATERFALL
                        && segment.type() != HydrologyFeatureType.CASCADE;
                HydrologyTerrainSample terrain = exactSlope ? compiler.sampleTerrain(x, z) : compiler.sampleTerrainBasis(x, z);
                if (terrain == null || compiler.elevatedSeaLevelSurfaceColumn(segment, terrain, shape.fluidHead())) {
                    continue;
                }
                boolean naturalOcean = classification == HydrologyRoutingTerrainSampler.NaturalClassification.OCEAN
                        || terrain.ocean()
                        || compiler.naturallySubmergedSurfaceColumn(segment, terrain);
                if (oceanConnection || naturalOcean) {
                    if (naturalOcean && apronEligible) {
                        compiler.addOceanApron(columns, course, segment, point, x, z, distance, flowX, flowZ,
                                terrain, pointFeatures[pointIndex]);
                    }
                    continue;
                }
                boolean source = firstSegment && pointIndex == 0;
                if (validationOnly && segment.type().isSurface() && !segment.fallingFluid()) {
                    compiler.feature(course, segment, x, shape.fluidHead(), z, flowX, flowZ,
                            source && deltaX == 0 && deltaZ == 0, channel, shore, grading,
                            false, false, pointFeatures[pointIndex]);
                    continue;
                }
                HydrologyColumnLayer layer = compiler.regularLayer(
                        course,
                        segment,
                        point,
                        shape,
                        terrain,
                        distance,
                        deltaX,
                        deltaZ,
                        flowX,
                        flowZ,
                        source,
                        channel,
                        shore,
                        grading,
                        false,
                        receiving,
                        pointFeatures[pointIndex]
                );
                layer = compiler.terrainContainedCaveLayer(course, segment, terrain, layer, x, z, plannedSurface);
                if (layer == null) {
                    continue;
                }
                compiler.addLayer(columns, x, z, terrain, layer);
            }
        }
    }

    FootprintCenterlineProjection projectCenterline(List<HydrologyPoint> centerline, HydrologyPoint incoming, int x, int z) {
        if (centerline.size() == 1) {
            HydrologyPoint point = centerline.getFirst();
            return new FootprintCenterlineProjection(
                    0,
                    point.x(),
                    point.z(),
                    StrictMath.hypot(x - point.x(), z - point.z()),
                    point.x() - incoming.x(),
                    point.z() - incoming.z()
            );
        }
        int selectedPoint = 0;
        double selectedX = centerline.getFirst().x();
        double selectedZ = centerline.getFirst().z();
        double selectedDistanceSquared = Double.POSITIVE_INFINITY;
        int selectedFlowX = 1;
        int selectedFlowZ = 0;
        for (int segmentIndex = 0; segmentIndex < centerline.size() - 1; segmentIndex++) {
            HydrologyPoint start = centerline.get(segmentIndex);
            HydrologyPoint end = centerline.get(segmentIndex + 1);
            double flowX = end.x() - start.x();
            double flowZ = end.z() - start.z();
            double lengthSquared = flowX * flowX + flowZ * flowZ;
            if (lengthSquared == 0D) {
                continue;
            }
            double progress = ((x - start.x()) * flowX + (z - start.z()) * flowZ) / lengthSquared;
            progress = Math.max(0D, Math.min(1D, progress));
            double projectedX = start.x() + flowX * progress;
            double projectedZ = start.z() + flowZ * progress;
            double deltaX = x - projectedX;
            double deltaZ = z - projectedZ;
            double distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
            if (distanceSquared >= selectedDistanceSquared) {
                continue;
            }
            selectedDistanceSquared = distanceSquared;
            selectedX = projectedX;
            selectedZ = projectedZ;
            selectedPoint = progress < 0.5D ? segmentIndex : segmentIndex + 1;
            selectedFlowX = end.x() - start.x();
            selectedFlowZ = end.z() - start.z();
        }
        int tangentReach = Math.max(2, compiler.settings.routing().refinementSpacing() * 2);
        HydrologyPoint tangentStart = centerline.get(Math.max(0, selectedPoint - tangentReach));
        HydrologyPoint tangentEnd = centerline.get(Math.min(centerline.size() - 1, selectedPoint + tangentReach));
        int tangentX = tangentEnd.x() - tangentStart.x();
        int tangentZ = tangentEnd.z() - tangentStart.z();
        if (tangentX != 0 || tangentZ != 0) {
            selectedFlowX = tangentX;
            selectedFlowZ = tangentZ;
        }
        return new FootprintCenterlineProjection(
                selectedPoint,
                selectedX,
                selectedZ,
                StrictMath.sqrt(selectedDistanceSquared),
                selectedFlowX,
                selectedFlowZ
        );
    }

    boolean withinLongitudinalBounds(
            List<HydrologyPoint> centerline,
            HydrologyPoint incoming,
            int x,
            int z,
            boolean clipStart,
            boolean clipEnd
    ) {
        if (centerline.size() < 2) {
            HydrologyPoint point = centerline.getFirst();
            long dot = (long) (x - point.x()) * (point.x() - incoming.x())
                    + (long) (z - point.z()) * (point.z() - incoming.z());
            return (!clipStart || dot >= 0L) && (!clipEnd || dot <= 0L);
        }
        HydrologyPoint start = centerline.getFirst();
        HydrologyPoint afterStart = centerline.get(1);
        long startDot = (long) (x - start.x()) * (afterStart.x() - start.x())
                + (long) (z - start.z()) * (afterStart.z() - start.z());
        if (clipStart && startDot < 0L) {
            return false;
        }
        if (!clipEnd) {
            return true;
        }
        HydrologyPoint end = centerline.getLast();
        HydrologyPoint beforeEnd = centerline.get(centerline.size() - 2);
        long endDot = (long) (x - end.x()) * (end.x() - beforeEnd.x())
                + (long) (z - end.z()) * (end.z() - beforeEnd.z());
        return endDot <= 0L;
    }

    boolean segmentsJoin(HydraulicSegment upstream, HydraulicSegment downstream) {
        HydrologyPoint upstreamEnd = upstream.centerline().getLast();
        HydrologyPoint downstreamStart = downstream.centerline().getFirst();
        return upstreamEnd.x() == downstreamStart.x()
                && upstreamEnd.y() == downstreamStart.y()
                && upstreamEnd.z() == downstreamStart.z();
    }

    List<HydrologyPoint> continuousCenterline(HydraulicSegment segment) {
        List<HydrologyPoint> configured = segment.centerline();
        if (configured.size() == 1) {
            return configured;
        }
        ArrayList<HydrologyPoint> continuous = new ArrayList<>();
        for (int pairIndex = 0; pairIndex < configured.size() - 1; pairIndex++) {
            HydrologyPoint start = configured.get(pairIndex);
            HydrologyPoint end = configured.get(pairIndex + 1);
            int steps = Math.max(Math.abs(end.x() - start.x()), Math.abs(end.z() - start.z()));
            if (steps == 0) {
                if (continuous.isEmpty()) {
                    continuous.add(start);
                }
                continue;
            }
            int firstStep = continuous.isEmpty() ? 0 : 1;
            for (int step = firstStep; step <= steps; step++) {
                double progress = step / (double) steps;
                int x = (int) StrictMath.round(start.x() + (end.x() - start.x()) * progress);
                int z = (int) StrictMath.round(start.z() + (end.z() - start.z()) * progress);
                int y = segment.fallingFluid() && !continuous.isEmpty()
                        ? segment.downstreamHeadY()
                        : (int) StrictMath.round(start.y() + (end.y() - start.y()) * progress);
                HydrologyPoint previous = continuous.isEmpty() ? null : continuous.getLast();
                if (previous == null || previous.x() != x || previous.z() != z) {
                    continuous.add(new HydrologyPoint(x, y, z));
                }
            }
        }
        return List.copyOf(continuous);
    }

    void rasterizePoint(
            Long2ObjectLinkedOpenHashMap<FootprintMutableColumn> columns,
            RiverCourse course,
            HydraulicSegment segment,
            HydrologyPoint point,
            int pointIndex,
            FootprintLayerShape shape,
            int flowX,
            int flowZ,
            boolean source,
            boolean falling,
            boolean receiving,
            boolean oceanConnection,
            boolean validationOnly,
            HydrologyFootprintCompiler.SurfaceRasterIndex plannedSurface
    ) {
        FootprintRasterStencil stencil = compiler.rasterStencil(shape);
        HydrologyFeatureRef[] pointFeatures = new HydrologyFeatureRef[HydrologyFootprintCompiler.FEATURE_ROLE_COUNT];
        boolean underground = segment.type().isUnderground() || segment.type().isDeepFluid();
        for (int offsetIndex = 0; offsetIndex < stencil.size(); offsetIndex++) {
            int deltaX = stencil.deltaXs()[offsetIndex];
            int deltaZ = stencil.deltaZs()[offsetIndex];
            int x = point.x() + deltaX;
            int z = point.z() + deltaZ;
            double channelDistance = compiler.channelGeometry.shapedDistance(
                    shape,
                    segment,
                    x,
                    z,
                    deltaX,
                    deltaZ,
                    flowX,
                    flowZ,
                    stencil.distances()[offsetIndex]
            );
            boolean channel = channelDistance <= shape.channelRadius() + 0.25D;
            if (underground && !channel) {
                continue;
            }
            boolean shore = !channel
                    && stencil.distances()[offsetIndex] <= shape.channelRadius() + shape.shoreWidth() + 0.25D;
            boolean grading = !channel
                    && !shore
                    && stencil.distances()[offsetIndex] <= shape.totalRadius() + 0.25D;
            double distance = grading ? stencil.distances()[offsetIndex] : channelDistance;
            HydrologyRoutingTerrainSampler.NaturalClassification classification = compiler.classifyNatural(x, z);
            boolean apronEligible = compiler.oceanApronEligible(segment, distance);
            if (classification == HydrologyRoutingTerrainSampler.NaturalClassification.OCEAN
                    && !apronEligible) {
                continue;
            }
            if (classification == HydrologyRoutingTerrainSampler.NaturalClassification.LAND
                    && oceanConnection) {
                continue;
            }
            boolean exactSlope = classification != HydrologyRoutingTerrainSampler.NaturalClassification.OCEAN
                    && !oceanConnection
                    && !channel
                    && segment.type() != HydrologyFeatureType.WATERFALL
                    && segment.type() != HydrologyFeatureType.CASCADE;
            HydrologyTerrainSample terrain = exactSlope
                    ? compiler.sampleTerrain(x, z)
                    : compiler.sampleTerrainBasis(x, z);
            if (terrain == null || compiler.elevatedSeaLevelSurfaceColumn(segment, terrain, shape.fluidHead())) {
                continue;
            }
            boolean naturalOcean = classification == HydrologyRoutingTerrainSampler.NaturalClassification.OCEAN
                    || terrain.ocean()
                    || compiler.naturallySubmergedSurfaceColumn(segment, terrain);
            if (oceanConnection) {
                if (naturalOcean) {
                    compiler.addOceanApron(
                            columns,
                            course,
                            segment,
                            point,
                            x,
                            z,
                            distance,
                            flowX,
                            flowZ,
                            terrain,
                            pointFeatures
                    );
                }
                continue;
            }
            if (naturalOcean) {
                compiler.addOceanApron(
                        columns,
                        course,
                        segment,
                        point,
                        x,
                        z,
                        distance,
                        flowX,
                        flowZ,
                        terrain,
                        pointFeatures
                );
                continue;
            }
            if (validationOnly && !underground && !segment.fallingFluid()) {
                compiler.feature(
                        course,
                        segment,
                        x,
                        shape.fluidHead(),
                        z,
                        flowX,
                        flowZ,
                        source && deltaX == 0 && deltaZ == 0,
                        channel,
                        shore,
                        grading,
                        channel && falling,
                        channel && receiving,
                        pointFeatures
                );
                continue;
            }
            HydrologyColumnLayer layer = compiler.regularLayer(
                    course,
                    segment,
                    point,
                    shape,
                    terrain,
                    distance,
                    deltaX,
                    deltaZ,
                    flowX,
                    flowZ,
                    source,
                    channel,
                    shore,
                    grading,
                    falling,
                    receiving,
                    pointFeatures
            );
            layer = compiler.terrainContainedCaveLayer(course, segment, terrain, layer, x, z, plannedSurface);
            if (layer == null) {
                continue;
            }
            compiler.addLayer(columns, x, z, terrain, layer);
            if (segment.type() == HydrologyFeatureType.COASTAL_GROTTO) {
                compiler.addAdjacentSeaApron(columns, course, segment, point, x, z, flowX, flowZ, pointFeatures);
            }
        }
    }
}
