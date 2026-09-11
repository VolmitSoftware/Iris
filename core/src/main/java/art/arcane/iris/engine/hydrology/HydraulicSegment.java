package art.arcane.iris.engine.hydrology;

import java.util.List;
import java.util.Objects;

public record HydraulicSegment(
        long id,
        long courseId,
        HydrologyFeatureType type,
        int upstreamHeadY,
        int downstreamHeadY,
        int width,
        int depth,
        boolean fallingFluid,
        boolean receivingPool,
        List<HydrologyPoint> centerline,
        HydraulicChannelProfile channelProfile
) {
    public HydraulicSegment {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(centerline, "centerline");
        Objects.requireNonNull(channelProfile, "channelProfile");
        centerline = List.copyOf(centerline);
        if (centerline.isEmpty()) {
            throw new IllegalArgumentException("A hydraulic segment requires a centerline.");
        }
        if (upstreamHeadY < downstreamHeadY) {
            throw new IllegalArgumentException("Hydraulic heads cannot rise downstream.");
        }
        if (width <= 0 || depth <= 0) {
            throw new IllegalArgumentException("Hydraulic segment width and depth must be positive.");
        }
        if (channelProfile.size() != 1 && channelProfile.size() != centerline.size()) {
            throw new IllegalArgumentException("Channel dimensions must cover every centerline station.");
        }
        if (channelProfile.maximumWidth() > width || channelProfile.maximumDepth() > depth) {
            throw new IllegalArgumentException("Hydraulic segment bounds must contain its channel dimensions.");
        }
        int drop = upstreamHeadY - downstreamHeadY;
        if (fallingFluid && drop == 0) {
            throw new IllegalArgumentException("Falling fluid requires a positive head transition.");
        }
        if (drop > 0 && !type.isDrop()) {
            throw new IllegalArgumentException("A positive head transition requires a drop segment type.");
        }
        if (drop == 0 && type.isDrop()) {
            throw new IllegalArgumentException("A drop segment type requires a positive head transition.");
        }
        if (drop > 0 && !fallingFluid) {
            if (centerline.getFirst().y() != upstreamHeadY || centerline.getLast().y() != downstreamHeadY) {
                throw new IllegalArgumentException("A graded head transition must span its declared heads.");
            }
            int previousHead = upstreamHeadY;
            for (HydrologyPoint point : centerline) {
                if (point.y() > previousHead || point.y() < downstreamHeadY) {
                    throw new IllegalArgumentException("A graded head transition must descend monotonically.");
                }
                previousHead = point.y();
            }
        }
    }

    public int drop() {
        return upstreamHeadY - downstreamHeadY;
    }

    public HydrologyPoint start() {
        return centerline.getFirst();
    }

    public HydrologyPoint end() {
        return centerline.getLast();
    }
}
