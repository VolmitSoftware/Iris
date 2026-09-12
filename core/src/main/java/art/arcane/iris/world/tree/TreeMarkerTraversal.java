package art.arcane.iris.world.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TreeMarkerTraversal {
    public static final int MAX_MEMBERS = 131_072;
    public static final int MAX_VISITED = 1_000_000;
    public static final int MAX_AXIS_DISTANCE = 256;

    private TreeMarkerTraversal() {
    }

    public static Discovery discover(Position trigger, String marker, int minimumY, int maximumY, MarkerLookup lookup) {
        ArrayDeque<Position> pending = new ArrayDeque<>();
        Set<Position> visited = new HashSet<>();
        List<Position> members = new ArrayList<>();
        pending.add(trigger);
        visited.add(trigger);

        while (!pending.isEmpty()) {
            Position current = pending.removeFirst();
            if (!marker.equals(lookup.markerAt(current.x(), current.y(), current.z()))) {
                continue;
            }
            if (members.size() >= MAX_MEMBERS) {
                return new Discovery(List.copyOf(members), false);
            }
            members.add(current);

            for (int xOffset = -1; xOffset <= 1; xOffset++) {
                for (int yOffset = -1; yOffset <= 1; yOffset++) {
                    for (int zOffset = -1; zOffset <= 1; zOffset++) {
                        if (xOffset == 0 && yOffset == 0 && zOffset == 0) {
                            continue;
                        }
                        Position next = current.offset(xOffset, yOffset, zOffset);
                        if (next.y() < minimumY || next.y() >= maximumY) {
                            continue;
                        }
                        if (!withinAxisBounds(trigger, next)) {
                            if (marker.equals(lookup.markerAt(next.x(), next.y(), next.z()))) {
                                return new Discovery(List.copyOf(members), false);
                            }
                            continue;
                        }
                        if (!visited.add(next)) {
                            continue;
                        }
                        if (visited.size() > MAX_VISITED) {
                            return new Discovery(List.copyOf(members), false);
                        }
                        pending.addLast(next);
                    }
                }
            }
        }

        return new Discovery(List.copyOf(members), true);
    }

    private static boolean withinAxisBounds(Position trigger, Position position) {
        return Math.abs(position.x() - trigger.x()) <= MAX_AXIS_DISTANCE
                && Math.abs(position.y() - trigger.y()) <= MAX_AXIS_DISTANCE
                && Math.abs(position.z() - trigger.z()) <= MAX_AXIS_DISTANCE;
    }

    @FunctionalInterface
    public interface MarkerLookup {
        String markerAt(int x, int y, int z);
    }

    public record Position(int x, int y, int z) {
        public Position offset(int xOffset, int yOffset, int zOffset) {
            return new Position(x + xOffset, y + yOffset, z + zOffset);
        }
    }

    public record Discovery(List<Position> members, boolean complete) {
    }
}
