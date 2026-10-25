package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.water.Reach;

final class ReachCursor {
    final Choice channel = new Choice();
    final Choice bank = new Choice();
    final Choice nearest = new Choice();
    final Choice edge = new Choice();

    void reset() {
        channel.reset();
        bank.reset();
        nearest.reset();
        edge.reset();
    }

    ReachHit hit() {
        if (channel.reach != null) {
            return channel.hit(ReachHit.Coverage.CHANNEL);
        }
        if (bank.reach != null) {
            return bank.hit(ReachHit.Coverage.BANK);
        }
        return nearest.reach == null ? null : nearest.hit(ReachHit.Coverage.OUTSIDE);
    }

    static final class Choice {
        Reach reach;
        int segment;
        int index = -1;
        double t;
        double distance = Double.POSITIVE_INFINITY;

        void reset() {
            reach = null;
            segment = 0;
            index = -1;
            t = 0D;
            distance = Double.POSITIVE_INFINITY;
        }

        void offer(Reach candidate, int candidateSegment, int candidateIndex, double candidateT, double candidateDistance) {
            if (candidateDistance < distance || candidateDistance == distance && reach != null && (candidate.id() < reach.id()
                    || candidate.id() == reach.id() && candidateSegment < segment)) {
                reach = candidate;
                segment = candidateSegment;
                index = candidateIndex;
                t = candidateT;
                distance = candidateDistance;
            }
        }

        int point() {
            return t < 0.5D ? segment : segment + 1;
        }

        double halfWidth() {
            float[] widths = reach.halfWidth();
            return widths[segment] + t * (widths[segment + 1] - widths[segment]);
        }

        double depth() {
            float[] depths = reach.depth();
            return depths[segment] + t * (depths[segment + 1] - depths[segment]);
        }

        private ReachHit hit(ReachHit.Coverage coverage) {
            return new ReachHit(reach, segment, (float) t, (float) distance, coverage);
        }
    }
}
