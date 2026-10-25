package art.arcane.iris.generation.hydrology.underground;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.mantle.MantleWriter;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

import java.util.Arrays;

public final class SeaInletRasterizer {
    public static final int SHELL = 2;
    private static final int CHUNK = 16;
    private static final int GRID = CHUNK + 2 * SHELL;
    static final int NONE = Integer.MIN_VALUE;

    public void write(SeaInlet inlet, int chunkX, int chunkZ, MantleWriter writer, String fluid, String floodedBiome, SubterrainFluid subterrain) {
        if (!touches(inlet, chunkX, chunkZ)) {
            return;
        }
        int[] floors = new int[GRID * GRID];
        int[] ceilings = new int[GRID * GRID];
        if (!shape(inlet, (chunkX << 4) - SHELL, (chunkZ << 4) - SHELL, GRID, floors, ceilings)) {
            return;
        }
        HydrologyCaveCell wet = new HydrologyCaveCell(HydrologyCaveAction.WET_SOURCE, fluid, floodedBiome);
        HydrologyCaveCell dry = new HydrologyCaveCell(HydrologyCaveAction.DRY_AIR, fluid, floodedBiome);
        HydrologyCaveCell guard = new HydrologyCaveCell(HydrologyCaveAction.SEAL_GUARD, fluid, floodedBiome);
        int water = inlet.waterLevel();
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
                } else if (ceilings[cell] == SeaInlet.OPEN) {
                    guard(writer, x, low - SHELL, floor - 1, z, guard);
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

    public static boolean touches(SeaInlet inlet, int chunkX, int chunkZ) {
        int[] bounds = bounds(inlet);
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        return bounds[0] <= minX + CHUNK - 1 + SHELL && bounds[2] >= minX - SHELL && bounds[1] <= minZ + CHUNK - 1 + SHELL
                && bounds[3] >= minZ - SHELL;
    }

    public static int[] bounds(SeaInlet inlet) {
        float minX = inlet.chamberX() - inlet.chamberRadius();
        float minZ = inlet.chamberZ() - inlet.chamberRadius();
        float maxX = inlet.chamberX() + inlet.chamberRadius() + 1;
        float maxZ = inlet.chamberZ() + inlet.chamberRadius() + 1;
        for (int station = 0; station < inlet.stations(); station++) {
            float reach = inlet.halfWidth()[station] + 1f;
            minX = Math.min(minX, inlet.xs()[station] - reach);
            minZ = Math.min(minZ, inlet.zs()[station] - reach);
            maxX = Math.max(maxX, inlet.xs()[station] + reach);
            maxZ = Math.max(maxZ, inlet.zs()[station] + reach);
        }
        return new int[]{(int) Math.floor(minX), (int) Math.floor(minZ), (int) Math.ceil(maxX), (int) Math.ceil(maxZ)};
    }

    public static boolean column(SeaInlet inlet, int x, int z, int[] out) {
        double px = x + 0.5D;
        double pz = z + 0.5D;
        int floor = Integer.MAX_VALUE;
        int ceiling = NONE;
        float[] xs = inlet.xs();
        float[] zs = inlet.zs();
        for (int station = 0; station + 1 < xs.length; station++) {
            double ax = xs[station];
            double az = zs[station];
            double dx = xs[station + 1] - ax;
            double dz = zs[station + 1] - az;
            double span = dx * dx + dz * dz;
            double t = span <= 0D ? 0D : Math.clamp(((px - ax) * dx + (pz - az) * dz) / span, 0D, 1D);
            double distance = Math.hypot(px - (ax + t * dx), pz - (az + t * dz));
            double half = inlet.halfWidth()[station] + t * (inlet.halfWidth()[station + 1] - inlet.halfWidth()[station]);
            if (distance > half) {
                continue;
            }
            floor = Math.min(floor, (int) Math.round(inlet.floor()[station] + t * (inlet.floor()[station + 1] - inlet.floor()[station])));
            ceiling = Math.max(ceiling, ceiling(inlet, station, t));
        }
        double radius = inlet.chamberRadius();
        double fromCentre = Math.hypot(px - inlet.chamberX(), pz - inlet.chamberZ());
        if (fromCentre <= radius) {
            int last = xs.length - 1;
            double dome = Math.sqrt(1D - (fromCentre / radius) * (fromCentre / radius));
            int water = inlet.waterLevel();
            floor = Math.min(floor, water - (int) Math.max(1L, Math.round((water - inlet.floor()[last]) * dome)));
            ceiling = Math.max(ceiling, water + (int) Math.round((inlet.ceiling()[last] - water) * dome));
        }
        if (ceiling == NONE) {
            return false;
        }
        out[0] = floor;
        out[1] = ceiling;
        return true;
    }

    private static int ceiling(SeaInlet inlet, int station, double t) {
        if (inlet.open(station) || inlet.open(station + 1)) {
            return t <= 0.5D ? inlet.ceiling()[station] : inlet.ceiling()[station + 1];
        }
        return (int) Math.round(inlet.ceiling()[station] + t * (inlet.ceiling()[station + 1] - inlet.ceiling()[station]));
    }

    static boolean shape(SeaInlet inlet, int originX, int originZ, int size, int[] floors, int[] ceilings) {
        Arrays.fill(floors, NONE);
        Arrays.fill(ceilings, NONE);
        boolean any = false;
        int[] column = new int[2];
        for (int gz = 0; gz < size; gz++) {
            for (int gx = 0; gx < size; gx++) {
                if (column(inlet, originX + gx, originZ + gz, column)) {
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
                if (ceiling != SeaInlet.OPEN && ceiling > water && ceiling > roof) {
                    roof = ceiling;
                }
            }
        }
        return roof;
    }

    private static void guard(MantleWriter writer, int x, int fromY, int toY, int z, HydrologyCaveCell guard) {
        for (int y = fromY; y <= toY; y++) {
            if (writer.getDataIfPresent(x, y, z, HydrologyCaveCell.class) == null) {
                writer.setData(x, y, z, guard);
            }
        }
    }
}
