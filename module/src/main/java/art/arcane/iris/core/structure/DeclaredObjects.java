package art.arcane.iris.core.structure;

import com.volmit.block.BlockState;
import com.volmit.geometry.BlockBox;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.BlockEntityData;
import com.volmit.object.GeneratedObject;
import com.volmit.object.ObjectBlock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Objects as the structure tools write them: a declared size, with blocks placed from the object's minimum corner. Iris
 * keeps an object's blocks as offsets from its center, which sits half the size in from that corner.
 */
public final class DeclaredObjects {
    private static final BlockPosition ANCHOR = new BlockPosition(0, 0, 0);

    private DeclaredObjects() {
    }

    public static Builder builder(int width, int height, int depth) {
        return new Builder(width, height, depth);
    }

    /** An object of the given size without a block. */
    public static GeneratedObject empty(int width, int height, int depth) {
        return builder(width, height, depth).build();
    }

    /** The size {@code object} declares. */
    public static BlockPosition sizeOf(GeneratedObject object) {
        BlockBox bounds = object.bounds();
        return new BlockPosition(bounds.maxX() - bounds.minX(), bounds.maxY() - bounds.minY(), bounds.maxZ() - bounds.minZ());
    }

    /**
     * {@code object} cut down to the box its blocks fill, centered the way every object is; an object without blocks
     * stays as it is.
     */
    public static GeneratedObject shrinkwrapped(GeneratedObject object) {
        List<ObjectBlock> blocks = object.blocks();
        if (blocks.isEmpty()) {
            return object;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (ObjectBlock block : blocks) {
            BlockPosition position = block.position();
            minX = Math.min(minX, position.x());
            minY = Math.min(minY, position.y());
            minZ = Math.min(minZ, position.z());
            maxX = Math.max(maxX, position.x());
            maxY = Math.max(maxY, position.y());
            maxZ = Math.max(maxZ, position.z());
        }
        Builder wrapped = builder(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        for (ObjectBlock block : blocks) {
            BlockPosition position = block.position();
            wrapped.state(position.x() - minX, position.y() - minY, position.z() - minZ, block.state());
            int cornerX = position.x() - minX;
            int cornerY = position.y() - minY;
            int cornerZ = position.z() - minZ;
            block.blockEntity().ifPresent(entity -> wrapped.blockEntity(cornerX, cornerY, cornerZ, entity));
        }
        return wrapped.build();
    }

    /** {@code local}, an offset from the object's center, as a position counted from its minimum corner. */
    public static BlockPosition fromCorner(GeneratedObject object, BlockPosition local) {
        BlockBox bounds = object.bounds();
        return new BlockPosition(local.x() - bounds.minX(), local.y() - bounds.minY(), local.z() - bounds.minZ());
    }

    public static final class Builder {
        private final int width;
        private final int height;
        private final int depth;
        private final Map<BlockPosition, BlockState> states = new LinkedHashMap<>();
        private final Map<BlockPosition, BlockEntityData> entities = new LinkedHashMap<>();

        private Builder(int width, int height, int depth) {
            this.width = width;
            this.height = height;
            this.depth = depth;
        }

        /** Sets the block at a position counted from the minimum corner. */
        public Builder state(int x, int y, int z, BlockState state) {
            states.put(new BlockPosition(x, y, z), state);
            return this;
        }

        /** Gives the block at a position counted from the minimum corner a block entity. */
        public Builder blockEntity(int x, int y, int z, BlockEntityData entity) {
            entities.put(new BlockPosition(x, y, z), entity);
            return this;
        }

        public GeneratedObject build() {
            int centerX = width / 2;
            int centerY = height / 2;
            int centerZ = depth / 2;
            List<ObjectBlock> blocks = new ArrayList<>(states.size());
            states.forEach((position, state) -> blocks.add(new ObjectBlock(
                    new BlockPosition(position.x() - centerX, position.y() - centerY, position.z() - centerZ), state,
                    Optional.ofNullable(entities.get(position)))));
            BlockBox declared = new BlockBox(-centerX, -centerY, -centerZ, width - centerX, height - centerY, depth - centerZ);
            return new GeneratedObject(declared, ANCHOR, blocks, List.of());
        }
    }
}
