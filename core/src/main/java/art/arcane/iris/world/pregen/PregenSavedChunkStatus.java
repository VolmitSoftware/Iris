package art.arcane.iris.world.pregen;

import art.arcane.iris.world.history.SavedTerrainChunk;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Objects;

public final class PregenSavedChunkStatus {
    private static final int MAXIMUM_REGIONS = 256;

    private final StatusReader reader;
    private final LinkedHashMap<Long, RegionStatus> regions = new LinkedHashMap<>(16, 0.75f, true);

    public PregenSavedChunkStatus(StatusReader reader) {
        this.reader = Objects.requireNonNull(reader);
    }

    public static PregenSavedChunkStatus fromWorld(Path dimensionRoot) {
        Path root = Objects.requireNonNull(dimensionRoot).toAbsolutePath().normalize();
        return new PregenSavedChunkStatus((x, z) -> readFull(root, x, z));
    }

    public synchronized boolean isFull(int chunkX, int chunkZ) {
        long key = ((long) (chunkX >> 5) << 32) | ((chunkZ >> 5) & 0xffffffffL);
        RegionStatus status = regions.get(key);
        if (status == null) {
            status = new RegionStatus(new BitSet(1024), new BitSet(1024));
            regions.put(key, status);
            if (regions.size() > MAXIMUM_REGIONS) {
                regions.remove(regions.keySet().iterator().next());
            }
        }
        int index = ((chunkZ & 31) << 5) | (chunkX & 31);
        if (!status.checked().get(index)) {
            try {
                status.full().set(index, reader.isFull(chunkX, chunkZ));
                status.checked().set(index);
            } catch (IOException failure) {
                throw new UncheckedIOException("Cannot verify saved pregeneration chunk " + chunkX + "," + chunkZ, failure);
            }
        }
        return status.full().get(index);
    }

    private static boolean readFull(Path root, int chunkX, int chunkZ) throws IOException {
        Path region = root.resolve("region/r." + (chunkX >> 5) + "." + (chunkZ >> 5) + ".mca");
        if (Files.notExists(region)) {
            return false;
        }
        try (RandomAccessFile input = new RandomAccessFile(region.toFile(), "r")) {
            input.seek((long) (((chunkZ & 31) << 5) | (chunkX & 31)) * Integer.BYTES);
            if (input.readInt() == 0) {
                return false;
            }
        }
        return SavedTerrainChunk.isComplete(SavedTerrainChunk.readStatus(root, chunkX, chunkZ));
    }

    @FunctionalInterface
    public interface StatusReader {
        boolean isFull(int chunkX, int chunkZ) throws IOException;
    }

    private record RegionStatus(BitSet checked, BitSet full) {
    }
}
