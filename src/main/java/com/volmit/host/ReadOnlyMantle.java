package com.volmit.host;

import com.volmit.mantle.BlockBox;
import com.volmit.mantle.Mantle;
import com.volmit.mantle.MantleEdit;
import com.volmit.mantle.MantleKey;
import com.volmit.mantle.MantleLayer;
import com.volmit.mantle.MantleVisitor;

/** The mantle as chunk stages see it: reads pass through, every write throws. The mantle pipeline has
 * finished around the chunk by then, and its neighbors may already be generated.
 */
final class ReadOnlyMantle implements Mantle {
    private static final String READ_ONLY = "Chunk stages read the mantle but do not write it;"
            + " write it from a mantle stage (Stage.mantle), which runs before the chunks it reaches";

    private final Mantle mantle;

    ReadOnlyMantle(Mantle mantle) {
        this.mantle = mantle;
    }

    @Override
    public BlockBox bounds() {
        return mantle.bounds();
    }

    @Override
    public <T> MantleLayer<T> layer(MantleKey<T> key) {
        return new ReadOnlyLayer<>(mantle.layer(key));
    }

    @Override
    public MantleEdit edit() {
        throw new UnsupportedOperationException(READ_ONLY);
    }

    private record ReadOnlyLayer<T>(MantleLayer<T> layer) implements MantleLayer<T> {
        @Override
        public T get(int x, int y, int z) {
            return layer.get(x, y, z);
        }

        @Override
        public void set(int x, int y, int z, T value) {
            throw new UnsupportedOperationException(READ_ONLY);
        }

        @Override
        public void remove(int x, int y, int z) {
            throw new UnsupportedOperationException(READ_ONLY);
        }

        @Override
        public void visit(BlockBox area, MantleVisitor<T> visitor) {
            layer.visit(area, visitor);
        }

        @Override
        public void clear(BlockBox area) {
            throw new UnsupportedOperationException(READ_ONLY);
        }
    }
}
