package art.arcane.iris.probe;

import art.arcane.iris.generation.hydrology.plan.WindowKey;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class HydrologyWindows {
    static final int CHUNKS = WindowKey.SIZE >> 4;

    private HydrologyWindows() {
    }

    record WindowRange(int minX, int maxX, int minZ, int maxZ) {
        WindowRange {
            if (maxX < minX || maxZ < minZ) {
                throw new IllegalArgumentException("Window range " + minX + ".." + maxX + "," + minZ + ".." + maxZ + " is empty.");
            }
        }

        static WindowRange parse(String text) {
            String[] axes = text.trim().split(",", -1);
            if (axes.length != 2) {
                throw new IllegalArgumentException("Windows must read x0..x1,z0..z1, got '" + text + "'.");
            }
            int[] x = bounds(axes[0], text);
            int[] z = bounds(axes[1], text);
            return new WindowRange(x[0], x[1], z[0], z[1]);
        }

        private static int[] bounds(String axis, String text) {
            String[] parts = axis.trim().split("\\.\\.", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Windows must read x0..x1,z0..z1, got '" + text + "'.");
            }
            return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
        }

        int count() {
            return (maxX - minX + 1) * (maxZ - minZ + 1);
        }

        boolean contains(WindowKey key) {
            return key.wx() >= minX && key.wx() <= maxX && key.wz() >= minZ && key.wz() <= maxZ;
        }

        boolean containsChunk(int chunkX, int chunkZ) {
            return contains(new WindowKey(Math.floorDiv(chunkX, CHUNKS), Math.floorDiv(chunkZ, CHUNKS)));
        }

        boolean containsBlock(int x, int z) {
            return contains(WindowKey.containing(x, z));
        }

        WindowRange grown(int rings) {
            return new WindowRange(minX - rings, maxX + rings, minZ - rings, maxZ + rings);
        }

        List<WindowKey> keys() {
            List<WindowKey> keys = new ArrayList<>(count());
            for (int wz = minZ; wz <= maxZ; wz++) {
                for (int wx = minX; wx <= maxX; wx++) {
                    keys.add(new WindowKey(wx, wz));
                }
            }
            return keys;
        }
    }

    record Target(File pack, String dimension, long seed, WindowRange windows, boolean studio) {
        Target {
            if (pack == null || !pack.isAbsolute() || dimension == null || dimension.isBlank() || windows == null) {
                throw new IllegalArgumentException("An absolute pack path, a dimension key and a window range are required.");
            }
        }

        static Target parse(String pack, String dimension, String seed, String windows, String studio) {
            File file = new File(pack);
            return new Target(file, dimension.equals("-") ? onlyDimension(file) : dimension, Long.parseLong(seed), WindowRange.parse(windows),
                    RealPackProbeSupport.parseBoolean(studio, "studio"));
        }

        private static String onlyDimension(File pack) {
            File[] files = new File(pack, "dimensions").listFiles((directory, name) -> name.endsWith(".json"));
            if (files == null || files.length != 1) {
                throw new IllegalArgumentException("The pack " + pack + " must hold exactly one dimension, or name one with probeDimension.");
            }
            String name = files[0].getName();
            return name.substring(0, name.length() - ".json".length());
        }
    }

    static List<WindowKey> neighbourhood(WindowKey key) {
        List<WindowKey> keys = new ArrayList<>(9);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                keys.add(new WindowKey(key.wx() + dx, key.wz() + dz));
            }
        }
        return keys;
    }
}
