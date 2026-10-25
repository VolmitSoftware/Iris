package art.arcane.iris.generation.hydrology.underground;

final class SeaInletFootprint {
    private static final byte OPEN = 1;
    private static final byte COVERED = 2;
    private static final int SHELL = SeaInletRasterizer.SHELL;

    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;
    private final byte[] kinds;

    private SeaInletFootprint(SeaInlet inlet) {
        int[] bounds = SeaInletRasterizer.bounds(inlet);
        this.minX = bounds[0];
        this.minZ = bounds[1];
        this.maxX = bounds[2];
        this.maxZ = bounds[3];
        this.kinds = new byte[(maxX - minX + 1) * (maxZ - minZ + 1)];
        int[] column = new int[2];
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                if (SeaInletRasterizer.column(inlet, x, z, column)) {
                    kinds[index(x, z)] = column[1] == SeaInlet.OPEN ? OPEN : COVERED;
                }
            }
        }
    }

    static SeaInletFootprint of(SeaInlet inlet) {
        return new SeaInletFootprint(inlet);
    }

    boolean conflicts(SeaInletFootprint other) {
        return opensBeside(this, other) || opensBeside(other, this);
    }

    private static boolean opensBeside(SeaInletFootprint open, SeaInletFootprint covered) {
        int fromX = Math.max(open.minX, covered.minX - SHELL);
        int toX = Math.min(open.maxX, covered.maxX + SHELL);
        int fromZ = Math.max(open.minZ, covered.minZ - SHELL);
        int toZ = Math.min(open.maxZ, covered.maxZ + SHELL);
        for (int z = fromZ; z <= toZ; z++) {
            for (int x = fromX; x <= toX; x++) {
                if (open.kind(x, z) == OPEN && covered.coveredNear(x, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean coveredNear(int x, int z) {
        for (int dz = -SHELL; dz <= SHELL; dz++) {
            for (int dx = -SHELL; dx <= SHELL; dx++) {
                if (kind(x + dx, z + dz) == COVERED) {
                    return true;
                }
            }
        }
        return false;
    }

    private byte kind(int x, int z) {
        return x < minX || x > maxX || z < minZ || z > maxZ ? 0 : kinds[index(x, z)];
    }

    private int index(int x, int z) {
        return (z - minZ) * (maxX - minX + 1) + x - minX;
    }
}
