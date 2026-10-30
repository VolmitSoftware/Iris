package art.arcane.iris.probe.fidelity;

final class CaveWalls {
    private static final int CHUNK_WIDTH = 16;
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private CaveWalls() {
    }

    static boolean besideCave(FidelityChunk chunk, int localX, int y, int localZ) {
        for (int[] side : SIDES) {
            int x = localX + side[0];
            int z = localZ + side[1];
            if (x < 0 || x >= CHUNK_WIDTH || z < 0 || z >= CHUNK_WIDTH) {
                continue;
            }
            if (y < chunk.surfaceY(x, z) && (chunk.open(x, y, z) || chunk.fluid(x, y, z))) {
                return true;
            }
        }
        return false;
    }
}
