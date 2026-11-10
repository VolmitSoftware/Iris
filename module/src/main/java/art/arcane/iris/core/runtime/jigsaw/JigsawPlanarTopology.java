package art.arcane.iris.core.runtime.jigsaw;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum JigsawPlanarTopology {
    BLANK(0),
    NORTH_END(1),
    EAST_END(2),
    NORTH_EAST_CORNER(3),
    SOUTH_END(4),
    NORTH_SOUTH_STRAIGHT(5),
    EAST_SOUTH_CORNER(6),
    NORTH_EAST_SOUTH_TEE(7),
    WEST_END(8),
    NORTH_WEST_CORNER(9),
    EAST_WEST_STRAIGHT(10),
    NORTH_EAST_WEST_TEE(11),
    SOUTH_WEST_CORNER(12),
    NORTH_SOUTH_WEST_TEE(13),
    EAST_SOUTH_WEST_TEE(14),
    CROSS(15);

    private static final JigsawPlanarTopology[] BY_MASK = buildMaskIndex();

    private final int mask;
    private final JigsawPlanarTopologyKind kind;
    private final Set<JigsawPlanarDirection> directions;

    JigsawPlanarTopology(int mask) {
        this.mask = mask;
        this.kind = resolveKind(mask);
        this.directions = Collections.unmodifiableSet(resolveDirections(mask));
    }

    public static JigsawPlanarTopology fromMask(int mask) {
        if (mask < 0 || mask >= BY_MASK.length) {
            throw new IllegalArgumentException("Planar topology mask must be between 0 and 15");
        }
        return BY_MASK[mask];
    }

    public int mask() {
        return mask;
    }

    public JigsawPlanarTopologyKind kind() {
        return kind;
    }

    public Set<JigsawPlanarDirection> directions() {
        return directions;
    }

    public boolean connects(JigsawPlanarDirection direction) {
        return directions.contains(direction);
    }

    public JigsawPlanarTopology rotateClockwise(int quarterTurns) {
        int normalizedTurns = Math.floorMod(quarterTurns, 4);
        if (normalizedTurns == 0 || mask == 0 || mask == 15) {
            return this;
        }
        int rotatedMask = ((mask << normalizedTurns) | (mask >>> (4 - normalizedTurns))) & 15;
        return fromMask(rotatedMask);
    }

    private static JigsawPlanarTopology[] buildMaskIndex() {
        JigsawPlanarTopology[] index = new JigsawPlanarTopology[16];
        for (JigsawPlanarTopology topology : values()) {
            index[topology.mask] = topology;
        }
        return index;
    }

    private static EnumSet<JigsawPlanarDirection> resolveDirections(int mask) {
        EnumSet<JigsawPlanarDirection> result = EnumSet.noneOf(JigsawPlanarDirection.class);
        for (JigsawPlanarDirection direction : JigsawPlanarDirection.values()) {
            if ((mask & direction.bit()) != 0) {
                result.add(direction);
            }
        }
        return result;
    }

    private static JigsawPlanarTopologyKind resolveKind(int mask) {
        int connectionCount = Integer.bitCount(mask);
        return switch (connectionCount) {
            case 0 -> JigsawPlanarTopologyKind.BLANK;
            case 1 -> JigsawPlanarTopologyKind.END;
            case 2 -> mask == 5 || mask == 10
                    ? JigsawPlanarTopologyKind.STRAIGHT
                    : JigsawPlanarTopologyKind.CORNER;
            case 3 -> JigsawPlanarTopologyKind.TEE;
            case 4 -> JigsawPlanarTopologyKind.CROSS;
            default -> throw new IllegalArgumentException("Invalid planar topology mask " + mask);
        };
    }
}
