package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.grid.CellHash;

import java.util.Map;

public final class FluidSeams {
    private static final int CHUNK = 16;
    private static final int RING = 2;
    private static final int WINDOW = CHUNK + 2 * RING;
    private static final int CELLS = WINDOW * WINDOW;
    private static final long SALT_SEAM = 0x5345414d4c4eL;
    private static final double SPREAD = 0.6D;
    private static final byte UNKNOWN = 0;
    private static final byte LAND = 1;
    private static final byte SEA = 2;

    private FluidSeams() {
    }

    public static String[] seal(Map<String, String> seams, long seed, int chunkX, int chunkZ, Columns columns) {
        if (seams.isEmpty()) {
            return null;
        }
        int originX = chunkX * CHUNK - RING;
        int originZ = chunkZ * CHUNK - RING;
        String[] fluid = new String[CELLS];
        boolean sealable = false;
        for (int cell = 0; cell < CELLS; cell++) {
            fluid[cell] = columns.fluid(originX + cell % WINDOW, originZ + cell / WINDOW);
            sealable |= fluid[cell] != null && seams.containsKey(fluid[cell]);
        }
        if (!sealable) {
            return null;
        }
        byte[] sea = new byte[CELLS];
        boolean[] contact = new boolean[CELLS];
        for (int gz = 1; gz < WINDOW - 1; gz++) {
            for (int gx = 1; gx < WINDOW - 1; gx++) {
                int cell = gz * WINDOW + gx;
                contact[cell] = touchesOther(cell, fluid, seams, sea, columns, originX, originZ);
            }
        }
        String[] sealed = null;
        for (int lz = 0; lz < CHUNK; lz++) {
            for (int lx = 0; lx < CHUNK; lx++) {
                int cell = (lz + RING) * WINDOW + lx + RING;
                String seam = fluid[cell] == null ? null : seams.get(fluid[cell]);
                if (seam == null || !contact[cell] && !spreads(cell, fluid, contact, seed, originX, originZ)) {
                    continue;
                }
                if (sealed == null) {
                    sealed = new String[CHUNK * CHUNK];
                }
                sealed[lz << 4 | lx] = seam;
            }
        }
        return sealed;
    }

    public static String fallingFluid(Map<String, String> seams, String source, String landing) {
        String seam = source == null ? null : seams.get(source);
        if (seam == null || source.equals(landing) || seam.equals(landing)) {
            return source;
        }
        return seam;
    }

    private static boolean touchesOther(int cell, String[] fluid, Map<String, String> seams, byte[] sea, Columns columns,
                                        int originX, int originZ) {
        String own = fluid[cell];
        String seam = own == null ? null : seams.get(own);
        if (seam == null) {
            return false;
        }
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int next = cell + dz * WINDOW + dx;
                String other = fluid[next];
                if (other == null ? sea(next, sea, columns, originX, originZ) : !other.equals(own) && !other.equals(seam)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean spreads(int cell, String[] fluid, boolean[] contact, long seed, int originX, int originZ) {
        int x = originX + cell % WINDOW;
        int z = originZ + cell / WINDOW;
        if (CellHash.unit(CellHash.mix(seed, SALT_SEAM, x, z)) >= SPREAD) {
            return false;
        }
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int next = cell + dz * WINDOW + dx;
                if (next != cell && contact[next] && fluid[cell].equals(fluid[next])) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean sea(int cell, byte[] sea, Columns columns, int originX, int originZ) {
        if (sea[cell] == UNKNOWN) {
            sea[cell] = columns.sea(originX + cell % WINDOW, originZ + cell / WINDOW) ? SEA : LAND;
        }
        return sea[cell] == SEA;
    }

    public interface Columns {
        String fluid(int x, int z);

        boolean sea(int x, int z);
    }
}
