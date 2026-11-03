package art.arcane.iris.generation.hydrology.underground;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.mantle.MantleWriter;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

import java.util.Arrays;

public final class PassageRasterizer {
    public static final int SHELL = 2;
    private static final int CHUNK = 16;
    private static final int GRID = CHUNK + 2 * SHELL;
    static final int NONE = Integer.MIN_VALUE;

    public void write(SealedPassage passage, int chunkX, int chunkZ, MantleWriter writer, String fluid, SubterrainFluid subterrain,
                      HistoricalChunks historical) {
        if (!touches(passage, chunkX, chunkZ)) {
            return;
        }
        int[] floors = new int[GRID * GRID];
        int[] ceilings = new int[GRID * GRID];
        if (!shape(passage, (chunkX << 4) - SHELL, (chunkZ << 4) - SHELL, GRID, floors, ceilings)) {
            return;
        }
        HydrologyCaveCell wet = new HydrologyCaveCell(HydrologyCaveAction.WET_SOURCE, fluid);
        HydrologyCaveCell dry = new HydrologyCaveCell(HydrologyCaveAction.DRY_AIR, fluid);
        HydrologyCaveCell guard = new HydrologyCaveCell(HydrologyCaveAction.SEAL_GUARD, fluid);
        int water = passage.waterLevel();
        boolean[] historicalAround = historicalAround(chunkX, chunkZ, historical);
        for (int lz = 0; lz < CHUNK; lz++) {
            for (int lx = 0; lx < CHUNK; lx++) {
                int cell = (lz + SHELL) * GRID + lx + SHELL;
                int x = (chunkX << 4) + lx;
                int z = (chunkZ << 4) + lz;
                int low = nearby(floors, GRID, cell, true);
                if (low == NONE) {
                    continue;
                }
                int floor = floors[cell];
                if (floor == NONE) {
                    guard(writer, x, low - SHELL, water, z, guard);
                } else if (historicalAround != null && besideHistorical(historicalAround, lx, lz)) {
                    guard(writer, x, low - SHELL, ceilings[cell], z, guard);
                } else {
                    for (int y = floor + 1; y <= water; y++) {
                        writer.setData(x, y, z, wet);
                        writer.clearData(x, y, z, NativeBlockState.class);
                    }
                    for (int y = water + 1; y <= ceilings[cell]; y++) {
                        writer.setData(x, y, z, dry);
                    }
                    guard(writer, x, low - SHELL, floor, z, guard);
                }
                int roof = nearbyRoof(ceilings, cell, water);
                for (int y = water + 1; y <= roof + SHELL; y++) {
                    if (subterrain.at(x, y, z)) {
                        guard(writer, x, y, y, z, guard);
                    }
                }
            }
        }
    }

    public interface SubterrainFluid {
        SubterrainFluid NOWHERE = (x, y, z) -> false;

        boolean at(int x, int y, int z);
    }

    public interface HistoricalChunks {
        HistoricalChunks NONE = (chunkX, chunkZ) -> false;

        boolean at(int chunkX, int chunkZ);
    }

    public static boolean touches(SealedPassage passage, int chunkX, int chunkZ) {
        int[] bounds = passage.bounds();
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        return bounds[0] <= minX + CHUNK - 1 + SHELL && bounds[2] >= minX - SHELL && bounds[1] <= minZ + CHUNK - 1 + SHELL
                && bounds[3] >= minZ - SHELL;
    }

    static boolean shape(SealedPassage passage, int originX, int originZ, int size, int[] floors, int[] ceilings) {
        Arrays.fill(floors, NONE);
        Arrays.fill(ceilings, NONE);
        boolean any = false;
        int[] column = new int[2];
        for (int gz = 0; gz < size; gz++) {
            for (int gx = 0; gx < size; gx++) {
                if (passage.column(originX + gx, originZ + gz, column)) {
                    floors[gz * size + gx] = column[0];
                    ceilings[gz * size + gx] = column[1];
                    any = true;
                }
            }
        }
        return any;
    }

    static int nearby(int[] values, int size, int cell, boolean lowest) {
        int found = NONE;
        int gx = cell % size;
        int gz = cell / size;
        for (int dz = -SHELL; dz <= SHELL; dz++) {
            for (int dx = -SHELL; dx <= SHELL; dx++) {
                int value = values[(gz + dz) * size + gx + dx];
                if (value != NONE && (found == NONE || (lowest ? value < found : value > found))) {
                    found = value;
                }
            }
        }
        return found;
    }

    private static int nearbyRoof(int[] ceilings, int cell, int water) {
        int roof = water - SHELL;
        int gx = cell % GRID;
        int gz = cell / GRID;
        for (int dz = -SHELL; dz <= SHELL; dz++) {
            for (int dx = -SHELL; dx <= SHELL; dx++) {
                int ceiling = ceilings[(gz + dz) * GRID + gx + dx];
                if (ceiling > water && ceiling > roof) {
                    roof = ceiling;
                }
            }
        }
        return roof;
    }

    private static boolean[] historicalAround(int chunkX, int chunkZ, HistoricalChunks historical) {
        boolean[] around = null;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if ((dx != 0 || dz != 0) && historical.at(chunkX + dx, chunkZ + dz)) {
                    if (around == null) {
                        around = new boolean[9];
                    }
                    around[(dz + 1) * 3 + dx + 1] = true;
                }
            }
        }
        return around;
    }

    private static boolean besideHistorical(boolean[] historicalAround, int lx, int lz) {
        int fromX = lx < SHELL ? -1 : 0;
        int toX = lx >= CHUNK - SHELL ? 1 : 0;
        int fromZ = lz < SHELL ? -1 : 0;
        int toZ = lz >= CHUNK - SHELL ? 1 : 0;
        for (int dz = fromZ; dz <= toZ; dz++) {
            for (int dx = fromX; dx <= toX; dx++) {
                if (historicalAround[(dz + 1) * 3 + dx + 1]) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void guard(MantleWriter writer, int x, int fromY, int toY, int z, HydrologyCaveCell guard) {
        for (int y = fromY; y <= toY; y++) {
            if (writer.getDataIfPresent(x, y, z, HydrologyCaveCell.class) == null) {
                writer.setData(x, y, z, guard);
            }
        }
    }
}
