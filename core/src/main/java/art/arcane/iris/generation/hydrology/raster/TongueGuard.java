package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ShapedNetwork;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class TongueGuard {
    private TongueGuard() {
    }

    public static ShapedNetwork guard(ShapedNetwork network, NaturalHeights natural, long seed, int seaLevel, int maxFill) {
        List<Reach> reaches = new ArrayList<>(network.reaches().size());
        for (Reach reach : network.reaches()) {
            reaches.add(guard(reach, natural, seed, seaLevel, maxFill));
        }
        return new ShapedNetwork(reaches, network.ponds());
    }

    static Reach guard(Reach reach, NaturalHeights natural, long seed, int seaLevel, int maxFill) {
        ReachGeometry geometry = ReachGeometry.of(reach, reach, seed, seaLevel);
        int[] tongues = geometry.tongueFalls();
        if (tongues.length == 0) {
            return reach;
        }
        ReachSegments segments = new ReachSegments(reach.xs().length);
        segments.addAll(reach);
        ReachCursor cursor = new ReachCursor();
        int[] stopped = new int[tongues.length];
        int count = 0;
        for (int fall : tongues) {
            int lowestGround = WaterHeads.snap(reach.grade()[fall], seaLevel, reach.tier().stepHeight()) - 1 - maxFill;
            if (passesTheGround(reach, geometry, segments, cursor, fall, natural, lowestGround)) {
                stopped[count++] = fall;
            }
        }
        return count == 0 ? reach : reach.withStraightFalls(Arrays.copyOf(stopped, count));
    }

    private static boolean passesTheGround(Reach reach, ReachGeometry geometry, ReachSegments segments, ReachCursor cursor, int fall,
                                           NaturalHeights natural, int lowestGround) {
        float[] xs = reach.xs();
        float[] zs = reach.zs();
        float[] halfWidth = reach.halfWidth();
        double end = geometry.tongueEnd(fall);
        for (int segment = fall; segment + 1 < xs.length && geometry.along(segment) <= end; segment++) {
            float radius = Math.max(halfWidth[segment], halfWidth[segment + 1]);
            int minX = (int) Math.floor(Math.min(xs[segment], xs[segment + 1]) - radius);
            int maxX = (int) Math.ceil(Math.max(xs[segment], xs[segment + 1]) + radius);
            int minZ = (int) Math.floor(Math.min(zs[segment], zs[segment + 1]) - radius);
            int maxZ = (int) Math.ceil(Math.max(zs[segment], zs[segment + 1]) + radius);
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    segments.nearest(x, z, ReachSegments.Scope.CHANNEL, cursor);
                    if (cursor.channel.reach != null && geometry.curvedFall(cursor.channel) == fall && Math.round(natural.height(x, z)) < lowestGround) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
