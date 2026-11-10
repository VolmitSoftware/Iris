package art.arcane.iris.core.runtime.jigsaw;

import art.arcane.iris.core.structure.DeclaredObjects;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.BlockEntityData;
import com.volmit.object.GeneratedObject;
import com.volmit.object.ObjectBlock;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An object as the studio edits it: a declared size, and blocks and block entities at positions counted from the
 * object's minimum corner.
 */
record JigsawStudioObject(
        int width,
        int height,
        int depth,
        Map<BlockPosition, BlockState> blocks,
        Map<BlockPosition, BlockEntityData> blockEntities
) {
    JigsawStudioObject {
        blocks = Collections.unmodifiableMap(new LinkedHashMap<>(blocks));
        blockEntities = Collections.unmodifiableMap(new LinkedHashMap<>(blockEntities));
    }

    /** An object of the given size without a block. */
    static JigsawStudioObject empty(int width, int height, int depth) {
        return new JigsawStudioObject(width, height, depth, Map.of(), Map.of());
    }

    static JigsawStudioObject of(GeneratedObject object) {
        BlockPosition size = DeclaredObjects.sizeOf(object);
        Map<BlockPosition, BlockState> blocks = new LinkedHashMap<>();
        Map<BlockPosition, BlockEntityData> blockEntities = new LinkedHashMap<>();
        for (ObjectBlock block : object.blocks()) {
            BlockPosition corner = DeclaredObjects.fromCorner(object, block.position());
            blocks.put(corner, block.state());
            block.blockEntity().ifPresent(entity -> blockEntities.put(corner, entity));
        }
        return new JigsawStudioObject(size.x(), size.y(), size.z(), blocks, blockEntities);
    }

    /** This object's contents in an object of another size, with blocks and block entities the caller edited. */
    JigsawStudioObject with(int width, int height, int depth,
                            Map<BlockPosition, BlockState> blocks, Map<BlockPosition, BlockEntityData> blockEntities) {
        return new JigsawStudioObject(width, height, depth, blocks, blockEntities);
    }

    GeneratedObject toGenerated() {
        DeclaredObjects.Builder builder = DeclaredObjects.builder(width, height, depth);
        blocks.forEach((position, state) -> builder.state(position.x(), position.y(), position.z(), state));
        blockEntities.forEach((position, entity) -> builder.blockEntity(position.x(), position.y(), position.z(), entity));
        return builder.build();
    }
}
