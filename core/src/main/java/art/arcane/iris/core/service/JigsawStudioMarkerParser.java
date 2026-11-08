package art.arcane.iris.core.service;

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMarkerKeyCodec;
import art.arcane.iris.engine.object.value.IrisDirection;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.engine.object.value.IrisPosition;
import art.arcane.iris.engine.object.structure.jigsaw.JigsawJoint;

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
                .setPosition(new IrisPosition(x, y, z))
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
            case "down_east" -> new Directions(IrisDirection.DOWN_NEGATIVE_Y, IrisDirection.EAST_POSITIVE_X);
            case "down_north" -> new Directions(IrisDirection.DOWN_NEGATIVE_Y, IrisDirection.NORTH_NEGATIVE_Z);
            case "down_south" -> new Directions(IrisDirection.DOWN_NEGATIVE_Y, IrisDirection.SOUTH_POSITIVE_Z);
            case "down_west" -> new Directions(IrisDirection.DOWN_NEGATIVE_Y, IrisDirection.WEST_NEGATIVE_X);
            case "up_east" -> new Directions(IrisDirection.UP_POSITIVE_Y, IrisDirection.EAST_POSITIVE_X);
            case "up_north" -> new Directions(IrisDirection.UP_POSITIVE_Y, IrisDirection.NORTH_NEGATIVE_Z);
            case "up_south" -> new Directions(IrisDirection.UP_POSITIVE_Y, IrisDirection.SOUTH_POSITIVE_Z);
            case "up_west" -> new Directions(IrisDirection.UP_POSITIVE_Y, IrisDirection.WEST_NEGATIVE_X);
            case "west_up" -> new Directions(IrisDirection.WEST_NEGATIVE_X, IrisDirection.UP_POSITIVE_Y);
            case "east_up" -> new Directions(IrisDirection.EAST_POSITIVE_X, IrisDirection.UP_POSITIVE_Y);
            case "north_up" -> new Directions(IrisDirection.NORTH_NEGATIVE_Z, IrisDirection.UP_POSITIVE_Y);
            case "south_up" -> new Directions(IrisDirection.SOUTH_POSITIVE_Z, IrisDirection.UP_POSITIVE_Y);
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

    record Directions(IrisDirection front, IrisDirection top) {
        Directions {
            Objects.requireNonNull(front, "Jigsaw front direction");
            Objects.requireNonNull(top, "Jigsaw top direction");
        }
    }
}
