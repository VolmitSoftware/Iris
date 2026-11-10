package art.arcane.iris.core.service;

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMarkerKeyCodec;
import art.arcane.iris.engine.object.structure.jigsaw.JigsawDirection;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.engine.object.structure.jigsaw.JigsawJoint;
import com.volmit.geometry.BlockPosition;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class JigsawStudioMarkerParser {
    private JigsawStudioMarkerParser() {
    }

    static IrisJigsawConnector parse(
            Map<String, Object> nbt,
            String orientation,
            int x,
            int y,
            int z
    ) {
        Map<String, Object> properties = Objects.requireNonNull(nbt, "Jigsaw marker NBT");
        Directions directions = directions(orientation);
        String jointName = requiredString(properties, "joint").toUpperCase(Locale.ROOT);
        JigsawJoint joint;
        try {
            joint = JigsawJoint.valueOf(jointName);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported jigsaw joint '" + jointName + "'", exception);
        }
        return new IrisJigsawConnector()
                .setPosition(new BlockPosition(x, y, z))
                .setDirection(directions.front())
                .setTop(directions.top())
                .setPool(JigsawStudioMarkerKeyCodec.decodePool(requiredString(properties, "pool")))
                .setName(requiredString(properties, "name"))
                .setTargetName(requiredString(properties, "target"))
                .setChannel(optionalString(properties, "channel"))
                .setJoint(joint)
                .setFinalState(requiredString(properties, "final_state"))
                .setSelectionPriority(optionalInt(properties, "selection_priority"))
                .setPlacementPriority(optionalInt(properties, "placement_priority"));
    }

    /** The front and top a jigsaw block's {@code orientation} property names, such as {@code down_east}. */
    static Directions directions(String orientation) {
        String name = Objects.requireNonNull(orientation, "Jigsaw orientation").toLowerCase(Locale.ROOT);
        return switch (name) {
            case "down_east" -> new Directions(JigsawDirection.DOWN_NEGATIVE_Y, JigsawDirection.EAST_POSITIVE_X);
            case "down_north" -> new Directions(JigsawDirection.DOWN_NEGATIVE_Y, JigsawDirection.NORTH_NEGATIVE_Z);
            case "down_south" -> new Directions(JigsawDirection.DOWN_NEGATIVE_Y, JigsawDirection.SOUTH_POSITIVE_Z);
            case "down_west" -> new Directions(JigsawDirection.DOWN_NEGATIVE_Y, JigsawDirection.WEST_NEGATIVE_X);
            case "up_east" -> new Directions(JigsawDirection.UP_POSITIVE_Y, JigsawDirection.EAST_POSITIVE_X);
            case "up_north" -> new Directions(JigsawDirection.UP_POSITIVE_Y, JigsawDirection.NORTH_NEGATIVE_Z);
            case "up_south" -> new Directions(JigsawDirection.UP_POSITIVE_Y, JigsawDirection.SOUTH_POSITIVE_Z);
            case "up_west" -> new Directions(JigsawDirection.UP_POSITIVE_Y, JigsawDirection.WEST_NEGATIVE_X);
            case "west_up" -> new Directions(JigsawDirection.WEST_NEGATIVE_X, JigsawDirection.UP_POSITIVE_Y);
            case "east_up" -> new Directions(JigsawDirection.EAST_POSITIVE_X, JigsawDirection.UP_POSITIVE_Y);
            case "north_up" -> new Directions(JigsawDirection.NORTH_NEGATIVE_Z, JigsawDirection.UP_POSITIVE_Y);
            case "south_up" -> new Directions(JigsawDirection.SOUTH_POSITIVE_Z, JigsawDirection.UP_POSITIVE_Y);
            default -> throw new IllegalArgumentException("Unsupported jigsaw orientation '" + orientation + "'");
        };
    }

    private static String requiredString(Map<String, Object> properties, String key) {
        Object value = properties.get(key);
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw new IllegalArgumentException("Jigsaw marker requires non-empty string NBT '" + key + "'");
        }
        return stringValue.trim();
    }

    private static String optionalString(Map<String, Object> properties, String key) {
        Object value = properties.get(key);
        if (value == null) {
            return "";
        }
        if (!(value instanceof String stringValue)) {
            throw new IllegalArgumentException("Jigsaw marker NBT '" + key + "' must be a string");
        }
        return stringValue.trim();
    }

    private static int optionalInt(Map<String, Object> properties, String key) {
        Object value = properties.get(key);
        if (value == null) {
            return 0;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Jigsaw marker NBT '" + key + "' must be an integer");
        }
        long longValue = number.longValue();
        if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Jigsaw marker NBT '" + key + "' is outside the integer range");
        }
        return (int) longValue;
    }

    record Directions(JigsawDirection front, JigsawDirection top) {
        Directions {
            Objects.requireNonNull(front, "Jigsaw front direction");
            Objects.requireNonNull(top, "Jigsaw top direction");
        }
    }
}
