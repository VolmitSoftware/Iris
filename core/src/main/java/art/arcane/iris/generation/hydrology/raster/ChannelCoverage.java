package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.water.Reach;

import java.util.List;

public final class ChannelCoverage {
    private static final int CAPACITY = 64;

    private final ReachSegments segments = new ReachSegments(CAPACITY);
    private final ReachCursor cursor = new ReachCursor();

    public ChannelCoverage(List<Reach> reaches, float minX, float minZ, float maxX, float maxZ) {
        for (Reach reach : reaches) {
            segments.addNear(reach, minX, minZ, maxX, maxZ, 0f);
        }
    }

    public int grade(int x, int z) {
        if (segments.isEmpty()) {
            return Integer.MIN_VALUE;
        }
        segments.nearest(x, z, ReachSegments.Scope.CHANNEL, cursor);
        return cursor.channel.reach == null ? Integer.MIN_VALUE : cursor.channel.grade();
    }
}
