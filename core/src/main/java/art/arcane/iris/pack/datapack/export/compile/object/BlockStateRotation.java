package art.arcane.iris.pack.datapack.export.compile.object;

import art.arcane.iris.pack.datapack.export.VanillaBlockState;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

final class BlockStateRotation {
    private static final int SIGN_STEPS = 16;
    private static final int SIGN_QUARTER = 4;

    private final Map<Direction, Direction> directions;
    private final int yQuarters;
    private final boolean flipped;

    private BlockStateRotation(int xQuarters, int yQuarters, int zQuarters) {
        this.yQuarters = Math.floorMod(yQuarters, 4);
        directions = new HashMap<>();
        for (Direction direction : Direction.values()) {
            int[] vector = {direction.dx, direction.dy, direction.dz};
            vector = turn(vector, Math.floorMod(xQuarters, 4), Axis.X);
            vector = turn(vector, Math.floorMod(zQuarters, 4), Axis.Z);
            vector = turn(vector, this.yQuarters, Axis.Y);
            directions.put(direction, Direction.of(vector[0], vector[1], vector[2]));
        }
        flipped = directions.get(Direction.UP) == Direction.DOWN;
    }

    static BlockStateRotation of(double xDegrees, double yDegrees, double zDegrees) {
        return new BlockStateRotation(quarters(xDegrees), quarters(yDegrees), quarters(zDegrees));
    }

    boolean identity() {
        for (Map.Entry<Direction, Direction> entry : directions.entrySet()) {
            if (entry.getKey() != entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    VanillaBlockState apply(VanillaBlockState state) {
        if (identity() || state.properties().isEmpty()) {
            return state;
        }
        Map<String, String> source = state.properties();
        Map<String, String> rotated = new TreeMap<>();
        for (Map.Entry<String, String> property : source.entrySet()) {
            Direction connection = Direction.named(property.getKey());
            if (connection != null) {
                rotated.put(directions.get(connection).serialized, property.getValue());
                continue;
            }
            rotated.put(property.getKey(), value(property.getKey(), property.getValue()));
        }
        return new VanillaBlockState(state.name(), rotated);
    }

    private String value(String key, String value) {
        return switch (key) {
            case "facing", "vertical_direction", "hopper_facing" -> mapDirection(value);
            case "axis" -> mapAxis(value);
            case "rotation" -> mapSignRotation(value);
            case "orientation", "shape" -> mapCompound(value);
            case "half", "type" -> flipped ? flipVertical(value) : value;
            case "face", "attach_face" -> flipped ? flipFace(value) : value;
            default -> value;
        };
    }

    private String mapDirection(String value) {
        Direction direction = Direction.named(value);
        return direction == null ? value : directions.get(direction).serialized;
    }

    private String mapAxis(String value) {
        Direction direction = switch (value) {
            case "x" -> Direction.EAST;
            case "y" -> Direction.UP;
            case "z" -> Direction.SOUTH;
            default -> null;
        };
        if (direction == null) {
            return value;
        }
        return switch (directions.get(direction)) {
            case EAST, WEST -> "x";
            case UP, DOWN -> "y";
            case NORTH, SOUTH -> "z";
        };
    }

    private String mapSignRotation(String value) {
        try {
            int rotation = Integer.parseInt(value);
            return Integer.toString(Math.floorMod(rotation - yQuarters * SIGN_QUARTER, SIGN_STEPS));
        } catch (NumberFormatException exception) {
            return value;
        }
    }

    private String mapCompound(String value) {
        String[] parts = value.split("_");
        if (parts.length == 2 && parts[0].equals("ascending")) {
            Direction direction = Direction.named(parts[1]);
            return direction == null ? value : "ascending_" + directions.get(direction).serialized;
        }
        if (parts.length != 2) {
            return value;
        }
        Direction first = Direction.named(parts[0]);
        Direction second = Direction.named(parts[1]);
        if (first == null || second == null) {
            return value;
        }
        Direction mappedFirst = directions.get(first);
        Direction mappedSecond = directions.get(second);
        if (!mappedFirst.horizontal() || !mappedSecond.horizontal() || !first.horizontal() || !second.horizontal()) {
            return mappedFirst.serialized + "_" + mappedSecond.serialized;
        }
        if (mappedFirst.northSouth() && mappedSecond.northSouth()) {
            return "north_south";
        }
        if (!mappedFirst.northSouth() && !mappedSecond.northSouth()) {
            return "east_west";
        }
        Direction northSouth = mappedFirst.northSouth() ? mappedFirst : mappedSecond;
        Direction eastWest = mappedFirst.northSouth() ? mappedSecond : mappedFirst;
        return northSouth.serialized + "_" + eastWest.serialized;
    }

    private static String flipVertical(String value) {
        return switch (value) {
            case "top" -> "bottom";
            case "bottom" -> "top";
            default -> value;
        };
    }

    private static String flipFace(String value) {
        return switch (value) {
            case "floor" -> "ceiling";
            case "ceiling" -> "floor";
            default -> value;
        };
    }

    private static int quarters(double degrees) {
        return (int) Math.floorMod(Math.round(degrees / 90.0), 4L);
    }

    private static int[] turn(int[] vector, int quarters, Axis axis) {
        int[] result = vector.clone();
        for (int index = 0; index < quarters; index++) {
            int x = result[0];
            int y = result[1];
            int z = result[2];
            result = switch (axis) {
                case X -> new int[]{x, -z, y};
                case Y -> new int[]{z, y, -x};
                case Z -> new int[]{-y, x, z};
            };
        }
        return result;
    }

    private enum Axis {
        X,
        Y,
        Z
    }

    private enum Direction {
        NORTH(0, 0, -1),
        SOUTH(0, 0, 1),
        EAST(1, 0, 0),
        WEST(-1, 0, 0),
        UP(0, 1, 0),
        DOWN(0, -1, 0);

        private final int dx;
        private final int dy;
        private final int dz;
        private final String serialized;

        Direction(int dx, int dy, int dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            serialized = name().toLowerCase(Locale.ROOT);
        }

        private boolean horizontal() {
            return dy == 0;
        }

        private boolean northSouth() {
            return dz != 0;
        }

        private static Direction named(String value) {
            for (Direction direction : values()) {
                if (direction.serialized.equals(value)) {
                    return direction;
                }
            }
            return null;
        }

        private static Direction of(int dx, int dy, int dz) {
            for (Direction direction : values()) {
                if (direction.dx == dx && direction.dy == dy && direction.dz == dz) {
                    return direction;
                }
            }
            throw new IllegalStateException("Quarter turn produced a non-axis direction " + dx + "," + dy + "," + dz);
        }
    }
}
