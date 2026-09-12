package art.arcane.iris.world.history;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class SavedBiomeChunk {
    public static final int MAXIMUM_HEIGHT = 65_536;
    public static final long MAXIMUM_ESTIMATED_BYTES = 64L * 1024L * 1024L;
    private static final int COLUMN_COUNT = 256;

    private final Header header;
    private final Column[] columns;
    private final long estimatedBytes;

    private SavedBiomeChunk(Header header, Column[] columns) {
        this.header = header;
        this.columns = columns.clone();
        long bytes = 128L + COLUMN_COUNT * 16L;
        for (Column column : this.columns) {
            Objects.requireNonNull(column, "Every saved biome column is required");
            int expectedY = header.minimumY();
            for (Span span : column.vertical()) {
                if (span.minimumY() != expectedY) {
                    throw new IllegalArgumentException("Saved biome spans must cover the column without gaps or overlaps");
                }
                expectedY = span.maximumYExclusive();
                bytes = checkedSize(bytes + cellBytes(span.cell()));
            }
            if (expectedY != header.maximumYExclusive()) {
                throw new IllegalArgumentException("Saved biome spans must cover the entire world height");
            }
            bytes = checkedSize(bytes + cellBytes(column.surface()) + cellBytes(column.caveBase()));
        }
        estimatedBytes = bytes;
    }

    public static Builder builder(Header header) {
        return new Builder(Objects.requireNonNull(header, "header"));
    }

    public Header header() {
        return header;
    }

    public int chunkX() {
        return header.chunkX();
    }

    public int chunkZ() {
        return header.chunkZ();
    }

    public long activationId() {
        return header.activationId();
    }

    public Column column(int localX, int localZ) {
        return columns[columnIndex(localX, localZ)];
    }

    public Cell surfaceAt(int localX, int localZ) {
        return column(localX, localZ).surface();
    }

    public Cell caveBaseAt(int localX, int localZ) {
        return column(localX, localZ).caveBase();
    }

    public Cell biomeAt(int localX, int worldY, int localZ) {
        if (worldY < header.minimumY() || worldY >= header.maximumYExclusive()) {
            throw new IndexOutOfBoundsException("Biome height is outside the saved world height");
        }
        List<Span> spans = column(localX, localZ).vertical();
        int low = 0;
        int high = spans.size() - 1;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (worldY >= spans.get(middle).maximumYExclusive()) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return spans.get(low).cell();
    }

    long estimatedBytes() {
        return estimatedBytes;
    }

    @Override
    public boolean equals(Object compared) {
        return this == compared || compared instanceof SavedBiomeChunk other
                && header.equals(other.header) && Arrays.equals(columns, other.columns);
    }

    @Override
    public int hashCode() {
        return 31 * header.hashCode() + Arrays.hashCode(columns);
    }

    private static long checkedSize(long bytes) {
        if (bytes > MAXIMUM_ESTIMATED_BYTES) {
            throw new IllegalArgumentException("Saved biome chunk exceeds its decoded footprint limit");
        }
        return bytes;
    }

    private static long cellBytes(Cell cell) {
        return cell.isResolved()
                ? 160L + cell.biomeKey().length() * 2L + cell.regionKey().length() * 2L
                : 160L;
    }

    private static int columnIndex(int localX, int localZ) {
        if (localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16) {
            throw new IndexOutOfBoundsException("Saved biome local coordinates must be between 0 and 15");
        }
        return localZ * 16 + localX;
    }

    public record Header(int chunkX, int chunkZ, long activationId, int minimumY, int height) {
        public Header {
            if (activationId <= 0L || height <= 0 || height > MAXIMUM_HEIGHT
                    || (long) minimumY + height > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid saved biome chunk header");
            }
        }

        public int maximumYExclusive() {
            return minimumY + height;
        }
    }

    public enum Resolution {
        RESOLVED,
        UNRESOLVED
    }

    public record Cell(long activationId, String biomeKey, String regionKey, Resolution resolution) {
        public Cell {
            if (activationId <= 0L) {
                throw new IllegalArgumentException("Saved biome activation must be positive");
            }
            Objects.requireNonNull(resolution, "resolution");
            if (resolution == Resolution.RESOLVED) {
                if (biomeKey == null || regionKey == null) {
                    throw new IllegalArgumentException("Resolved saved biomes require both resource keys");
                }
                biomeKey = ChunkGenerationSemantics.requireResourceKey(biomeKey);
                regionKey = ChunkGenerationSemantics.requireResourceKey(regionKey);
            } else if (biomeKey != null || regionKey != null) {
                throw new IllegalArgumentException("Unresolved saved biomes must not contain resource keys");
            }
        }

        public Cell(long activationId, String biomeKey, String regionKey) {
            this(activationId, biomeKey, regionKey, Resolution.RESOLVED);
        }

        public static Cell unresolved(long activationId) {
            return new Cell(activationId, null, null, Resolution.UNRESOLVED);
        }

        public boolean isResolved() {
            return resolution == Resolution.RESOLVED;
        }
    }

    public record Span(int minimumY, int maximumYExclusive, Cell cell) {
        public Span {
            if (minimumY >= maximumYExclusive) {
                throw new IllegalArgumentException("Saved biome spans must have positive height");
            }
            Objects.requireNonNull(cell, "cell");
        }
    }

    public record Column(Cell surface, Cell caveBase, List<Span> vertical) {
        public Column {
            Objects.requireNonNull(surface, "surface");
            Objects.requireNonNull(caveBase, "caveBase");
            Objects.requireNonNull(vertical, "vertical");
            if (vertical.isEmpty() || vertical.size() > MAXIMUM_HEIGHT) {
                throw new IllegalArgumentException("Invalid saved biome column span count");
            }
            ArrayList<Span> compact = new ArrayList<>(vertical.size());
            for (Span span : vertical) {
                Objects.requireNonNull(span, "span");
                if (!compact.isEmpty()) {
                    Span previous = compact.getLast();
                    if (previous.maximumYExclusive() != span.minimumY()) {
                        throw new IllegalArgumentException("Saved biome spans must be contiguous");
                    }
                    if (previous.cell().equals(span.cell())) {
                        compact.set(compact.size() - 1,
                                new Span(previous.minimumY(), span.maximumYExclusive(), span.cell()));
                        continue;
                    }
                }
                compact.add(span);
            }
            vertical = List.copyOf(compact);
        }
    }

    public static final class Builder {
        private final Header header;
        private final Column[] columns = new Column[COLUMN_COUNT];

        private Builder(Header header) {
            this.header = header;
        }

        public Builder column(int localX, int localZ, Column column) {
            columns[columnIndex(localX, localZ)] = Objects.requireNonNull(column, "column");
            return this;
        }

        public SavedBiomeChunk build() {
            return new SavedBiomeChunk(header, columns);
        }
    }
}
