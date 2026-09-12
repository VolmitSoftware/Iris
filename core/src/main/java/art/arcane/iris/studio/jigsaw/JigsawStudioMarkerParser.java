package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.pack.value.IrisDirection;
import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.structure.jigsaw.JigsawJoint;
import org.bukkit.block.Orientation;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class JigsawStudioMarkerParser {
    private JigsawStudioMarkerParser() {
    }

    static IrisJigsawConnector parse(
            Map<String, Object> nbt,
            Orientation orientation,
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

    static Directions directions(Orientation orientation) {
        return switch (Objects.requireNonNull(orientation, "Jigsaw orientation")) {
            case DOWN_EAST -> new Directions(IrisDirection.DOWN_NEGATIVE_Y, IrisDirection.EAST_POSITIVE_X);
            case DOWN_NORTH -> new Directions(IrisDirection.DOWN_NEGATIVE_Y, IrisDirection.NORTH_NEGATIVE_Z);
            case DOWN_SOUTH -> new Directions(IrisDirection.DOWN_NEGATIVE_Y, IrisDirection.SOUTH_POSITIVE_Z);
            case DOWN_WEST -> new Directions(IrisDirection.DOWN_NEGATIVE_Y, IrisDirection.WEST_NEGATIVE_X);
            case UP_EAST -> new Directions(IrisDirection.UP_POSITIVE_Y, IrisDirection.EAST_POSITIVE_X);
            case UP_NORTH -> new Directions(IrisDirection.UP_POSITIVE_Y, IrisDirection.NORTH_NEGATIVE_Z);
            case UP_SOUTH -> new Directions(IrisDirection.UP_POSITIVE_Y, IrisDirection.SOUTH_POSITIVE_Z);
            case UP_WEST -> new Directions(IrisDirection.UP_POSITIVE_Y, IrisDirection.WEST_NEGATIVE_X);
            case WEST_UP -> new Directions(IrisDirection.WEST_NEGATIVE_X, IrisDirection.UP_POSITIVE_Y);
            case EAST_UP -> new Directions(IrisDirection.EAST_POSITIVE_X, IrisDirection.UP_POSITIVE_Y);
            case NORTH_UP -> new Directions(IrisDirection.NORTH_NEGATIVE_Z, IrisDirection.UP_POSITIVE_Y);
            case SOUTH_UP -> new Directions(IrisDirection.SOUTH_POSITIVE_Z, IrisDirection.UP_POSITIVE_Y);
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
