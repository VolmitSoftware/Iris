package art.arcane.iris.engine.hydrology;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RiverFootprint {
    private static final RiverFootprint EMPTY = new RiverFootprint(Map.of());

    private final Long2ObjectLinkedOpenHashMap<HydrologyColumnSample> indexed;
    private final Map<Long, HydrologyColumnSample> columns;

    public RiverFootprint(Map<Long, HydrologyColumnSample> columns) {
        if (columns == null) {
            throw new IllegalArgumentException("columns must not be null.");
        }
        indexed = new Long2ObjectLinkedOpenHashMap<>(columns.size());
        for (Map.Entry<Long, HydrologyColumnSample> entry : columns.entrySet()) {
            HydrologyColumnSample sample = entry.getValue();
            if (sample == null || entry.getKey() != pack(sample.x(), sample.z())) {
                throw new IllegalArgumentException("Footprint keys must match their column coordinates.");
            }
            indexed.put(entry.getKey().longValue(), sample);
        }
        long[] order = indexed.keySet().toLongArray();
        Arrays.sort(order);
        for (long key : order) {
            indexed.getAndMoveToLast(key);
        }
        this.columns = Collections.unmodifiableMap(indexed);
    }

    public static RiverFootprint empty() {
        return EMPTY;
    }

    public Optional<HydrologyColumnSample> sample(int x, int z) {
        return Optional.ofNullable(indexed.get(pack(x, z)));
    }

    public HydrologyRenderSample renderSample(int x, int z) {
        HydrologyColumnSample sample = indexed.get(pack(x, z));
        return sample == null ? new HydrologyRenderSample(x, z, List.of()) : sample.renderSample();
    }

    public Map<Long, HydrologyColumnSample> columns() {
        return columns;
    }

    public List<HydrologyColumnSample> columnsIn(int minimumX, int minimumZ, int maximumX, int maximumZ) {
        ArrayList<HydrologyColumnSample> selected = new ArrayList<>();
        long width = (long) maximumX - minimumX;
        long height = (long) maximumZ - minimumZ;
        if (width <= 0L || height <= 0L) {
            return List.of();
        }
        long area = width > Long.MAX_VALUE / height ? Long.MAX_VALUE : width * height;
        if (area < columns.size()) {
            for (long z = minimumZ; z < maximumZ; z++) {
                for (long x = minimumX; x < maximumX; x++) {
                    HydrologyColumnSample sample = indexed.get(pack((int) x, (int) z));
                    if (sample != null) {
                        selected.add(sample);
                    }
                }
            }
            return List.copyOf(selected);
        }
        for (HydrologyColumnSample sample : columns.values()) {
            if (sample.x() >= minimumX && sample.x() < maximumX
                    && sample.z() >= minimumZ && sample.z() < maximumZ) {
                selected.add(sample);
            }
        }
        return List.copyOf(selected);
    }

    public int size() {
        return columns.size();
    }

    public boolean isEmpty() {
        return columns.isEmpty();
    }

    public static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    public static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    public static int unpackZ(long packed) {
        return (int) packed;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RiverFootprint footprint && columns.equals(footprint.columns);
    }

    @Override
    public int hashCode() {
        return columns.hashCode();
    }

    @Override
    public String toString() {
        return "RiverFootprint[columns=" + columns.size() + "]";
    }
}
