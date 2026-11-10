package art.arcane.iris.core.runtime.jigsaw;

import art.arcane.iris.engine.object.structure.jigsaw.JigsawDirection;

public enum JigsawPlanarDirection {
    NORTH(1),
    EAST(2),
    SOUTH(4),
    WEST(8);

    private final int bit;

    JigsawPlanarDirection(int bit) {
        this.bit = bit;
    }

    public int bit() {
        return bit;
    }

    public JigsawDirection direction() {
        return switch (this) {
            case NORTH -> JigsawDirection.NORTH_NEGATIVE_Z;
            case EAST -> JigsawDirection.EAST_POSITIVE_X;
            case SOUTH -> JigsawDirection.SOUTH_POSITIVE_Z;
            case WEST -> JigsawDirection.WEST_NEGATIVE_X;
        };
    }

    public JigsawPlanarDirection rotateClockwise(int quarterTurns) {
        int normalizedTurns = Math.floorMod(quarterTurns, values().length);
        return values()[(ordinal() + normalizedTurns) % values().length];
    }

    public JigsawPlanarDirection opposite() {
        return rotateClockwise(2);
    }
}
