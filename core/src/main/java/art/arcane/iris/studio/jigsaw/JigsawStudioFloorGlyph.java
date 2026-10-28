package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.pack.value.IrisDirection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class JigsawStudioFloorGlyph {
    private static final JigsawStudioFloorGlyph EMPTY = new JigsawStudioFloorGlyph(0, 0, Map.of());

    private final int width;
    private final int depth;
    private final Map<Long, Cell> cells;

    private JigsawStudioFloorGlyph(int width, int depth, Map<Long, Cell> cells) {
        this.width = width;
        this.depth = depth;
        this.cells = cells;
    }

    public static JigsawStudioFloorGlyph empty() {
        return EMPTY;
    }

    public static JigsawStudioFloorGlyph of(int width, int depth, List<Marker> markers) {
        List<Marker> sourceMarkers = List.copyOf(Objects.requireNonNull(markers, "Jigsaw Studio floor markers"));
        if (width < 1 || depth < 1 || sourceMarkers.isEmpty()) {
            return EMPTY;
        }
        Map<Long, Cell> cells = new HashMap<>();
        int centerX = width / 2;
        int centerZ = depth / 2;
        for (Marker marker : sourceMarkers) {
            int x = clamp(marker.x(), width);
            int z = clamp(marker.z(), depth);
            drawLine(cells, centerX, centerZ, x, z);
            if (!marker.direction().isVertical()) {
                drawExtension(cells, x, z, marker.direction(), width, depth);
            }
        }
        for (Marker marker : sourceMarkers) {
            int x = clamp(marker.x(), width);
            int z = clamp(marker.z(), depth);
            Cell socket = switch (marker.direction()) {
                case UP_POSITIVE_Y -> Cell.UP_SOCKET;
                case DOWN_NEGATIVE_Y -> Cell.DOWN_SOCKET;
                case NORTH_NEGATIVE_Z, SOUTH_POSITIVE_Z, EAST_POSITIVE_X, WEST_NEGATIVE_X -> Cell.HORIZONTAL_SOCKET;
            };
            cells.merge(key(x, z), socket, JigsawStudioFloorGlyph::merge);
        }
        return new JigsawStudioFloorGlyph(width, depth, Map.copyOf(cells));
    }

    public Cell cell(int localX, int localZ) {
        if (localX < 0 || localZ < 0 || localX >= width || localZ >= depth) {
            return Cell.BASE;
        }
        return cells.getOrDefault(key(localX, localZ), Cell.BASE);
    }

    private static void drawLine(Map<Long, Cell> cells, int startX, int startZ, int endX, int endZ) {
        int deltaX = Math.abs(endX - startX);
        int deltaZ = -Math.abs(endZ - startZ);
        int stepX = startX < endX ? 1 : -1;
        int stepZ = startZ < endZ ? 1 : -1;
        int error = deltaX + deltaZ;
        int x = startX;
        int z = startZ;
        while (true) {
            cells.merge(key(x, z), Cell.LINE, JigsawStudioFloorGlyph::merge);
            if (x == endX && z == endZ) {
                return;
            }
            int doubled = error * 2;
            if (doubled >= deltaZ) {
                error += deltaZ;
                x += stepX;
            }
            if (doubled <= deltaX) {
                error += deltaX;
                z += stepZ;
            }
        }
    }

    private static void drawExtension(
            Map<Long, Cell> cells,
            int startX,
            int startZ,
            IrisDirection direction,
            int width,
            int depth
    ) {
        int x = startX + direction.x();
        int z = startZ + direction.z();
        while (x >= 0 && z >= 0 && x < width && z < depth) {
            cells.merge(key(x, z), Cell.LINE, JigsawStudioFloorGlyph::merge);
            x += direction.x();
            z += direction.z();
        }
    }

    private static Cell merge(Cell current, Cell next) {
        if (current == Cell.UP_SOCKET && next == Cell.DOWN_SOCKET
                || current == Cell.DOWN_SOCKET && next == Cell.UP_SOCKET) {
            return Cell.UP_AND_DOWN_SOCKET;
        }
        return next.priority() > current.priority() ? next : current;
    }

    private static int clamp(int value, int size) {
        return Math.max(0, Math.min(size - 1, value));
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    public enum Cell {
        BASE(0),
        LINE(1),
        HORIZONTAL_SOCKET(2),
        UP_SOCKET(3),
        DOWN_SOCKET(3),
        UP_AND_DOWN_SOCKET(4);

        private final int priority;

        Cell(int priority) {
            this.priority = priority;
        }

        int priority() {
            return priority;
        }
    }

    public record Marker(int x, int z, IrisDirection direction) {
        public Marker {
            Objects.requireNonNull(direction, "Jigsaw Studio floor marker direction");
        }
    }
}
