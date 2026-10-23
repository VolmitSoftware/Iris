package com.volmit.host;

import com.volmit.block.BlockState;
import com.volmit.generation.ChunkContext;
import com.volmit.mantle.BlockBox;
import com.volmit.mantle.Mantle;
import com.volmit.mantle.MantleEdit;
import com.volmit.mantle.MantleKey;
import com.volmit.mantle.MantleLayer;
import com.volmit.mantle.MantleVisitor;

/** The mantle as chunk stages see it: reads pass through everywhere the host's mantle reaches, writes to module layers
 * only inside the chunk's own 16 by 16 columns. The mantle pipeline has finished around the chunk by then, and its
 * neighbours may already be generated, so their mantle stays as the pipeline left it. The host's blocks are read-only.
 */
final class ChunkMantle implements Mantle {
    private static final String NEIGHBOUR_WRITE = "A chunk stage writes the mantle of its own chunk only;"
            + " write around it from a mantle stage (Stage.mantle), which runs before the chunks it reaches";
    private static final String HOST_BLOCKS = "The mantle's blocks are written by placements, not by stages";

    private final Mantle mantle;
    private final BlockBox ownChunk;

    ChunkMantle(Mantle mantle, int originX, int originZ) {
        this.mantle = mantle;
        BlockBox bounds = mantle.bounds();
        this.ownChunk = new BlockBox(originX, bounds.minY(), originZ, originX + ChunkContext.CHUNK_WIDTH, bounds.maxY(),
                originZ + ChunkContext.CHUNK_WIDTH);
    }

    @Override
    public BlockBox bounds() {
        return mantle.bounds();
    }

    @Override
    public <T> MantleLayer<T> layer(MantleKey<T> key) {
        return new OwnChunkLayer<>(mantle.layer(key));
    }

    @Override
    public MantleLayer<BlockState> blocks() {
        return new ReadOnlyLayer<>(mantle.blocks());
    }

    @Override
    public boolean isCarved(int x, int y, int z) {
        return mantle.isCarved(x, y, z);
    }

    @Override
    public MantleEdit edit() {
        throw new UnsupportedOperationException("Chunk stages write mantle layers directly, without an edit");
    }

    private void requireOwnChunk(int x, int y, int z) {
        if (!ownChunk.contains(x, y, z)) {
            throw new UnsupportedOperationException(NEIGHBOUR_WRITE + "; " + x + "," + y + "," + z + " is outside " + ownChunk);
        }
    }

    private void requireOwnChunk(BlockBox area) {
        if (!ownChunk.contains(area)) {
            throw new UnsupportedOperationException(NEIGHBOUR_WRITE + "; " + area + " reaches outside " + ownChunk);
        }
    }

    private final class OwnChunkLayer<T> implements MantleLayer<T> {
        private final MantleLayer<T> layer;

        OwnChunkLayer(MantleLayer<T> layer) {
            this.layer = layer;
        }

        @Override
        public T get(int x, int y, int z) {
            return layer.get(x, y, z);
        }

        @Override
        public void set(int x, int y, int z, T value) {
            requireOwnChunk(x, y, z);
            layer.set(x, y, z, value);
        }

        @Override
        public void remove(int x, int y, int z) {
            requireOwnChunk(x, y, z);
            layer.remove(x, y, z);
        }

        @Override
        public void visit(BlockBox area, MantleVisitor<T> visitor) {
            layer.visit(area, visitor);
        }

        @Override
        public void clear(BlockBox area) {
            requireOwnChunk(area);
            layer.clear(area);
        }
    }

    private record ReadOnlyLayer<T>(MantleLayer<T> layer) implements MantleLayer<T> {
        @Override
        public T get(int x, int y, int z) {
            return layer.get(x, y, z);
        }

        @Override
        public void set(int x, int y, int z, T value) {
            throw new UnsupportedOperationException(HOST_BLOCKS);
        }

        @Override
        public void remove(int x, int y, int z) {
            throw new UnsupportedOperationException(HOST_BLOCKS);
        }

        @Override
        public void visit(BlockBox area, MantleVisitor<T> visitor) {
            layer.visit(area, visitor);
        }

        @Override
        public void clear(BlockBox area) {
            throw new UnsupportedOperationException(HOST_BLOCKS);
        }
    }
}
