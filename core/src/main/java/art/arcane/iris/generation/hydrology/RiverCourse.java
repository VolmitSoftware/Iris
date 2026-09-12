package art.arcane.iris.generation.hydrology;

import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

public record RiverCourse(
        long id,
        RiverCourseType type,
        OptionalLong sourceNodeId,
        OptionalLong outletId,
        String profileKey,
        int discharge,
        List<DrainageEdge> drainageEdges,
        List<HydraulicSegment> segments
) {
    public RiverCourse {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(sourceNodeId, "sourceNodeId");
        Objects.requireNonNull(outletId, "outletId");
        Objects.requireNonNull(drainageEdges, "drainageEdges");
        Objects.requireNonNull(segments, "segments");
        if (profileKey == null || profileKey.isBlank()) {
            throw new IllegalArgumentException("profileKey must not be blank.");
        }
        profileKey = profileKey.trim();
        if (discharge <= 0) {
            throw new IllegalArgumentException("Course discharge must be positive.");
        }
        drainageEdges = List.copyOf(drainageEdges);
        segments = List.copyOf(segments);
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("A river course requires at least one hydraulic segment.");
        }
        for (HydraulicSegment segment : segments) {
            if (segment.courseId() != id) {
                throw new IllegalArgumentException("Every segment must belong to its containing course.");
            }
        }
        validateSinkholeContinuation(type, segments);
        boolean independent = type == RiverCourseType.DEEP_FLUID
                || type == RiverCourseType.SURFACE_POOL
                || type == RiverCourseType.SEA_CAVE;
        if (independent && (sourceNodeId.isPresent() || outletId.isPresent())) {
            throw new IllegalArgumentException("Independent deep-fluid, pool and sea-cave courses cannot reference drainage nodes or river outlets.");
        }
        if (!independent && (sourceNodeId.isEmpty() || outletId.isEmpty())) {
            throw new IllegalArgumentException("Surface and underground courses require a source and outlet.");
        }
    }

    public boolean hydraulicallyNonRising() {
        int previousHead = Integer.MAX_VALUE;
        for (HydraulicSegment segment : segments) {
            if (segment.upstreamHeadY() > previousHead || segment.downstreamHeadY() > segment.upstreamHeadY()) {
                return false;
            }
            previousHead = segment.downstreamHeadY();
        }
        return true;
    }

    public boolean surfaceSinkholeContinuation() {
        return type == RiverCourseType.SURFACE
                && segments.size() >= 2
                && segments.get(segments.size() - 2).type() == HydrologyFeatureType.SINKHOLE
                && segments.getLast().type() == HydrologyFeatureType.INLAND_GROTTO;
    }

    private static void validateSinkholeContinuation(
            RiverCourseType courseType,
            List<HydraulicSegment> segments
    ) {
        int sinkholeCount = 0;
        for (HydraulicSegment segment : segments) {
            if (segment.type() == HydrologyFeatureType.SINKHOLE) {
                sinkholeCount++;
            }
        }
        boolean terminalInlandGrotto = courseType == RiverCourseType.SURFACE
                && segments.getLast().type() == HydrologyFeatureType.INLAND_GROTTO;
        if (!terminalInlandGrotto) {
            if (sinkholeCount > 0) {
                throw new IllegalArgumentException("A sinkhole must link a surface course to its terminal inland grotto.");
            }
            return;
        }
        if (sinkholeCount != 1 || segments.size() < 3) {
            throw new IllegalArgumentException("A surface inland grotto requires one explicit sinkhole link and surface lip.");
        }
        HydraulicSegment lip = segments.get(segments.size() - 3);
        HydraulicSegment sinkhole = segments.get(segments.size() - 2);
        HydraulicSegment grotto = segments.getLast();
        if (!lip.type().isSurface()
                || sinkhole.type() != HydrologyFeatureType.SINKHOLE
                || sinkhole.drop() <= 0
                || !sinkhole.receivingPool()
                || lip.downstreamHeadY() != sinkhole.upstreamHeadY()
                || sinkhole.downstreamHeadY() != grotto.upstreamHeadY()
                || grotto.upstreamHeadY() != grotto.downstreamHeadY()
                || lip.end().y() != sinkhole.upstreamHeadY()
                || sinkhole.start().y() != sinkhole.upstreamHeadY()
                || sinkhole.end().y() != sinkhole.downstreamHeadY()
                || grotto.start().y() != grotto.upstreamHeadY()
                || lip.end().distanceSquared2D(sinkhole.start()) != 0L
                || sinkhole.end().distanceSquared2D(grotto.start()) != 0L) {
            throw new IllegalArgumentException("A surface sinkhole requires a connected lip, graded descent, and receiving grotto.");
        }
    }
}
