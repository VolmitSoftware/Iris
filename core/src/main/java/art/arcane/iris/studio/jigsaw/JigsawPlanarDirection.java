package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.pack.value.IrisDirection;

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

    public IrisDirection irisDirection() {
        return switch (this) {
            case NORTH -> IrisDirection.NORTH_NEGATIVE_Z;
            case EAST -> IrisDirection.EAST_POSITIVE_X;
            case SOUTH -> IrisDirection.SOUTH_POSITIVE_Z;
            case WEST -> IrisDirection.WEST_NEGATIVE_X;
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
