package art.arcane.iris.generation.hydrology.underground;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.mantle.MantleWriter;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

public final class GrottoRasterizer {
    public static final int SHELL = 2;
    static final byte NONE = 0;
    static final byte WET = 1;
    static final byte DRY = 2;
    static final byte GUARD = 3;
    private static final int CHUNK = 16;
    private static final int GRID = CHUNK + 2 * SHELL;

    public interface Exclusion {
        Exclusion NOWHERE = (x, y, z) -> false;

        boolean blocked(int x, int y, int z);
    }

    public void write(Grotto grotto, int chunkX, int chunkZ, MantleWriter writer, String fluid, Exclusion exclusion) {
        Cells cells = cells(grotto, chunkX, chunkZ, exclusion);
        if (cells == null) {
            return;
        }
        HydrologyCaveCell wet = new HydrologyCaveCell(HydrologyCaveAction.WET_SOURCE, fluid);
        HydrologyCaveCell dry = new HydrologyCaveCell(HydrologyCaveAction.DRY_AIR, fluid);
        HydrologyCaveCell guard = new HydrologyCaveCell(HydrologyCaveAction.SEAL_GUARD, fluid);
        for (int y = cells.minY(); y <= cells.maxY(); y++) {
            for (int lz = 0; lz < CHUNK; lz++) {
                for (int lx = 0; lx < CHUNK; lx++) {
                    int x = (chunkX << 4) + lx;
                    int z = (chunkZ << 4) + lz;
                    switch (cells.at(lx, y, lz)) {
                        case WET -> {
                            writer.setData(x, y, z, wet);
                            writer.clearData(x, y, z, NativeBlockState.class);
                        }
                        case DRY -> writer.setData(x, y, z, dry);
                        case GUARD -> {
                            if (writer.getDataIfPresent(x, y, z, HydrologyCaveCell.class) == null) {
                                writer.setData(x, y, z, guard);
                            }
                        }
                        default -> {
                        }
                    }
                }
            }
        }
    }

    static Cells cells(Grotto grotto, int chunkX, int chunkZ, Exclusion exclusion) {
        int originX = (chunkX << 4) - SHELL;
        int originZ = (chunkZ << 4) - SHELL;
        if (!grotto.touches(originX, originZ, originX + GRID - 1, originZ + GRID - 1)) {
            return null;
        }
        GrottoShape shape = new GrottoShape(grotto).scoped(originX, originZ, originX + GRID - 1, originZ + GRID - 1);
        if (shape.empty()) {
            return null;
        }
        int sea = grotto.seaLevel();
        int minY = Math.max(1, shape.minY() - SHELL);
        int maxY = Math.max(shape.maxY(), sea);
        int height = maxY - minY + 1;
        boolean[] carved = new boolean[GRID * GRID * height];
        boolean any = false;
        for (int gz = 0; gz < GRID; gz++) {
            for (int gx = 0; gx < GRID; gx++) {
                int x = originX + gx;
                int z = originZ + gz;
                if (!shape.near(x, z)) {
                    continue;
                }
                boolean own = gx >= SHELL && gx < SHELL + CHUNK && gz >= SHELL && gz < SHELL + CHUNK;
                int top = own ? maxY : sea;
                for (int y = Math.max(minY, shape.minY()); y <= top; y++) {
                    if (shape.inside(x, y, z) && !exclusion.blocked(x, y, z)) {
                        carved[index(gx, y - minY, gz)] = true;
                        any = true;
                    }
                }
            }
        }
        if (!any) {
            return null;
        }
        byte[] kinds = new byte[CHUNK * CHUNK * height];
        for (int gz = 0; gz < GRID; gz++) {
            for (int gx = 0; gx < GRID; gx++) {
                for (int y = minY; y <= sea; y++) {
                    if (carved[index(gx, y - minY, gz)]) {
                        guardAround(kinds, gx, y - minY, gz, sea - minY);
                    }
                }
            }
        }
        for (int lz = 0; lz < CHUNK; lz++) {
            for (int lx = 0; lx < CHUNK; lx++) {
                for (int y = minY; y <= maxY; y++) {
                    if (carved[index(lx + SHELL, y - minY, lz + SHELL)]) {
                        kinds[(y - minY) * CHUNK * CHUNK + lz * CHUNK + lx] = y <= sea ? WET : DRY;
                    }
                }
            }
        }
        return new Cells(minY, maxY, kinds);
    }

    private static void guardAround(byte[] kinds, int gx, int level, int gz, int seaLevel) {
        for (int dz = -SHELL; dz <= SHELL; dz++) {
            int lz = gz + dz - SHELL;
            if (lz < 0 || lz >= CHUNK) {
                continue;
            }
            for (int dx = -SHELL; dx <= SHELL; dx++) {
                int lx = gx + dx - SHELL;
                if (lx < 0 || lx >= CHUNK) {
                    continue;
                }
                for (int dy = -SHELL; dy <= SHELL; dy++) {
                    int at = level + dy;
                    if (at >= 0 && at <= seaLevel) {
                        kinds[at * CHUNK * CHUNK + lz * CHUNK + lx] = GUARD;
                    }
                }
            }
        }
    }

    private static int index(int gx, int level, int gz) {
        return level * GRID * GRID + gz * GRID + gx;
    }

    record Cells(int minY, int maxY, byte[] kinds) {
        byte at(int lx, int y, int lz) {
            if (y < minY || y > maxY) {
                return NONE;
            }
            return kinds[(y - minY) * CHUNK * CHUNK + lz * CHUNK + lx];
        }
    }
}
