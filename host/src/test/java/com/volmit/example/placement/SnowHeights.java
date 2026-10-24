package com.volmit.example.placement;

import com.volmit.geometry.BlockPosition;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Per-placement accumulator: the highest written world block of each column. */
public final class SnowHeights {
    private final Map<Column, Integer> highest = new TreeMap<>();

    public void include(BlockPosition written) {
        highest.merge(new Column(written.x(), written.z()), written.y(), Math::max);
    }

    public List<BlockPosition> tops() {
        return highest.entrySet().stream()
                .map(entry -> new BlockPosition(entry.getKey().x(), entry.getValue(), entry.getKey().z()))
                .toList();
    }

    private record Column(int x, int z) implements Comparable<Column> {
        @Override
        public int compareTo(Column other) {
            int result = Integer.compare(x, other.x);
            return result == 0 ? Integer.compare(z, other.z) : result;
        }
    }
}
