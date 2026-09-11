package art.arcane.iris.engine.hydrology;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class HydrologyCrossTileSurfaceAdmission {
    private HydrologyCrossTileSurfaceAdmission() {
    }

    static Result admit(
            List<Claim> currentClaims,
            List<RankedClaim> blockers
    ) {
        ArrayList<Rejection> rejections = new ArrayList<>();
        for (Claim current : currentClaims) {
            for (RankedClaim ranked : blockers) {
                Claim blocker = ranked.claim();
                if (!conflicts(current, blocker)) {
                    continue;
                }
                rejections.add(new Rejection(current, blocker.courseId()));
                break;
            }
        }
        return new Result(List.copyOf(rejections));
    }

    private static boolean conflicts(Claim first, Claim second) {
        if (first.outletId() == second.outletId() && first.reachesOutlet() && second.reachesOutlet()) {
            return true;
        }
        int minimumSeparation = Math.max(first.sourceSpacing(), second.sourceSpacing());
        long minimumSeparationSquared = (long) minimumSeparation * minimumSeparation;
        if (first.centerline().getFirst().distanceSquared2D(second.centerline().getFirst())
                < minimumSeparationSquared) {
            return true;
        }
        if (first.reachesOutlet() && second.reachesOutlet()
                && first.terminal().distanceSquared2D(second.terminal())
                < minimumSeparationSquared) {
            return true;
        }
        double clearance = Math.max(4D, (first.maximumWidth() + second.maximumWidth()) / 2D + 4D);
        return centerlinesWithin(first.centerline(), second.centerline(), clearance);
    }

    private static boolean centerlinesWithin(
            List<HydrologyPoint> first,
            List<HydrologyPoint> second,
            double clearance
    ) {
        double clearanceSquared = clearance * clearance;
        for (int firstIndex = 1; firstIndex < first.size(); firstIndex++) {
            HydrologyPoint firstStart = first.get(firstIndex - 1);
            HydrologyPoint firstEnd = first.get(firstIndex);
            for (int secondIndex = 1; secondIndex < second.size(); secondIndex++) {
                HydrologyPoint secondStart = second.get(secondIndex - 1);
                HydrologyPoint secondEnd = second.get(secondIndex);
                if (segmentDistanceSquared(firstStart, firstEnd, secondStart, secondEnd) <= clearanceSquared) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double segmentDistanceSquared(
            HydrologyPoint firstStart,
            HydrologyPoint firstEnd,
            HydrologyPoint secondStart,
            HydrologyPoint secondEnd
    ) {
        if (segmentsIntersect(firstStart, firstEnd, secondStart, secondEnd)) {
            return 0D;
        }
        return Math.min(
                Math.min(pointSegmentDistanceSquared(firstStart, secondStart, secondEnd),
                        pointSegmentDistanceSquared(firstEnd, secondStart, secondEnd)),
                Math.min(pointSegmentDistanceSquared(secondStart, firstStart, firstEnd),
                        pointSegmentDistanceSquared(secondEnd, firstStart, firstEnd))
        );
    }

    private static boolean segmentsIntersect(
            HydrologyPoint firstStart,
            HydrologyPoint firstEnd,
            HydrologyPoint secondStart,
            HydrologyPoint secondEnd
    ) {
        long firstA = orientation(firstStart, firstEnd, secondStart);
        long firstB = orientation(firstStart, firstEnd, secondEnd);
        long secondA = orientation(secondStart, secondEnd, firstStart);
        long secondB = orientation(secondStart, secondEnd, firstEnd);
        return Long.signum(firstA) != Long.signum(firstB)
                && Long.signum(secondA) != Long.signum(secondB);
    }

    private static long orientation(HydrologyPoint start, HydrologyPoint end, HydrologyPoint point) {
        long firstX = end.x() - (long) start.x();
        long firstZ = end.z() - (long) start.z();
        long secondX = point.x() - (long) start.x();
        long secondZ = point.z() - (long) start.z();
        return firstX * secondZ - firstZ * secondX;
    }

    private static double pointSegmentDistanceSquared(
            HydrologyPoint point,
            HydrologyPoint start,
            HydrologyPoint end
    ) {
        double deltaX = end.x() - start.x();
        double deltaZ = end.z() - start.z();
        double lengthSquared = deltaX * deltaX + deltaZ * deltaZ;
        if (lengthSquared == 0D) {
            return point.distanceSquared2D(start);
        }
        double progress = ((point.x() - start.x()) * deltaX + (point.z() - start.z()) * deltaZ)
                / lengthSquared;
        progress = Math.max(0D, Math.min(1D, progress));
        double closestX = start.x() + deltaX * progress;
        double closestZ = start.z() + deltaZ * progress;
        double pointX = point.x() - closestX;
        double pointZ = point.z() - closestZ;
        return pointX * pointX + pointZ * pointZ;
    }

    record Claim(
            long courseId,
            long outletId,
            HydrologyPoint terminal,
            boolean reachesOutlet,
            int maximumWidth,
            List<HydrologyPoint> centerline,
            int sourceSpacing
    ) {
        Claim {
            Objects.requireNonNull(terminal, "terminal");
            centerline = List.copyOf(Objects.requireNonNull(centerline, "centerline"));
            if (maximumWidth < 1 || centerline.size() < 2 || sourceSpacing < 0 || sourceSpacing > 8192) {
                throw new IllegalArgumentException("Surface claims require a positive width and centerline.");
            }
        }
    }

    record RankedClaim(HydrologyTileKey ownerKey, int ownerRank, Claim claim) {
        RankedClaim {
            Objects.requireNonNull(ownerKey, "ownerKey");
            Objects.requireNonNull(claim, "claim");
        }
    }

    record Rejection(Claim loser, long winnerCourseId) {
        Rejection {
            Objects.requireNonNull(loser, "loser");
        }
    }

    record Result(List<Rejection> rejections) {
        Result {
            rejections = List.copyOf(Objects.requireNonNull(rejections, "rejections"));
        }
    }
}
