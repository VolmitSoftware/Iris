package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.water.Reach;

import java.util.Arrays;

final class ReachCursor {
    final Choice channel = new Choice();
    final Choice bank = new Choice();
    final Choice nearest = new Choice();
    final Choice edge = new Choice();
    final Waters waters = new Waters();

    void reset() {
        channel.reset();
        bank.reset();
        nearest.reset();
        edge.reset();
        waters.reset();
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
                set(candidate, candidateSegment, candidateIndex, candidateT, candidateDistance);
            }
        }

        int point() {
            return t < 0.5D ? segment : segment + 1;
        }

        int grade() {
            return reach.grade()[point()];
        }

        double halfWidth() {
            float[] widths = reach.halfWidth();
            return widths[segment] + t * (widths[segment + 1] - widths[segment]);
        }

        double depth() {
            float[] depths = reach.depth();
            return depths[segment] + t * (depths[segment + 1] - depths[segment]);
        }

        private void set(Reach candidate, int candidateSegment, int candidateIndex, double candidateT, double candidateDistance) {
            reach = candidate;
            segment = candidateSegment;
            index = candidateIndex;
            t = candidateT;
            distance = candidateDistance;
        }

        private ReachHit hit(ReachHit.Coverage coverage) {
            return new ReachHit(reach, segment, (float) t, (float) distance, coverage);
        }
    }

    static final class Waters {
        private Choice[] entries = {new Choice(), new Choice(), new Choice(), new Choice()};
        private int size;

        void reset() {
            size = 0;
        }

        boolean isEmpty() {
            return size == 0;
        }

        int size() {
            return size;
        }

        Choice get(int entry) {
            return entries[entry];
        }

        void offer(Reach reach, int segment, int index, double t, double distance) {
            for (int entry = 0; entry < size; entry++) {
                Choice held = entries[entry];
                if (held.reach.id() == reach.id()) {
                    if (distance < held.distance || distance == held.distance && segment < held.segment) {
                        held.set(reach, segment, index, t, distance);
                    }
                    return;
                }
            }
            if (size == entries.length) {
                grow();
            }
            entries[size++].set(reach, segment, index, t, distance);
        }

        void resolve(Choice channel) {
            channel.reset();
            if (size == 0) {
                return;
            }
            Choice owner = entries[0];
            for (int entry = 1; entry < size; entry++) {
                if (owns(entries[entry], owner)) {
                    owner = entries[entry];
                }
            }
            channel.set(owner.reach, owner.segment, owner.index, owner.t, owner.distance);
        }

        private static boolean owns(Choice candidate, Choice held) {
            int candidateGrade = candidate.grade();
            int heldGrade = held.grade();
            if (candidateGrade != heldGrade) {
                return candidateGrade < heldGrade;
            }
            double candidateWidth = candidate.halfWidth();
            double heldWidth = held.halfWidth();
            if (candidateWidth != heldWidth) {
                return candidateWidth > heldWidth;
            }
            if (candidate.distance != held.distance) {
                return candidate.distance < held.distance;
            }
            if (candidate.reach.id() != held.reach.id()) {
                return candidate.reach.id() < held.reach.id();
            }
            return candidate.segment < held.segment;
        }

        private void grow() {
            int grown = 2 * entries.length;
            Choice[] larger = Arrays.copyOf(entries, grown);
            for (int entry = entries.length; entry < grown; entry++) {
                larger[entry] = new Choice();
            }
            entries = larger;
        }
    }
}
