package art.arcane.iris.core.service;

import com.volmit.block.BlockState;
import com.volmit.geometry.BlockBox;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.BlockEntityData;
import com.volmit.object.GeneratedObject;
import com.volmit.object.ObjectBlock;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.ObjectCodec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/** An object codec that writes an object as readable lines, in the order the object keeps its blocks. */
final class TextObjectCodec implements ObjectCodec {
    private static final String NO_BLOCK_ENTITY = "-";

    @Override
    public String extension() {
        return ".txt";
    }

    @Override
    public byte[] encode(GeneratedObject object) {
        BlockBox bounds = object.bounds();
        StringBuilder text = new StringBuilder();
        text.append(bounds.minX()).append(' ').append(bounds.minY()).append(' ').append(bounds.minZ()).append(' ')
                .append(bounds.maxX()).append(' ').append(bounds.maxY()).append(' ').append(bounds.maxZ()).append('\n');
        for (ObjectBlock block : object.blocks()) {
            text.append(block.position().x()).append(' ')
                    .append(block.position().y()).append(' ')
                    .append(block.position().z()).append(' ')
                    .append(block.state().key()).append(' ')
                    .append(block.blockEntity().map(TextObjectCodec::describe).orElse(NO_BLOCK_ENTITY))
                    .append('\n');
        }
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public GeneratedObject decode(byte[] bytes) throws IOException {
        String[] lines = new String(bytes, StandardCharsets.UTF_8).split("\n");
        String[] bound = lines[0].split(" ");
        BlockBox bounds = new BlockBox(
                Integer.parseInt(bound[0]), Integer.parseInt(bound[1]), Integer.parseInt(bound[2]),
                Integer.parseInt(bound[3]), Integer.parseInt(bound[4]), Integer.parseInt(bound[5]));
        List<ObjectBlock> blocks = new ArrayList<>();
        for (int index = 1; index < lines.length; index++) {
            String[] parts = lines[index].split(" ");
            Optional<BlockEntityData> entity = parts[4].equals(NO_BLOCK_ENTITY)
                    ? Optional.empty()
                    : Optional.of(parseEntity(parts[4]));
            blocks.add(new ObjectBlock(
                    new BlockPosition(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])),
                    BlockState.parse(parts[3]),
                    entity));
        }
        return new GeneratedObject(bounds, new BlockPosition(0, 0, 0), blocks, List.of());
    }

    private static String describe(BlockEntityData entity) {
        return entity.format() + "@" + entity.version() + "@" + HexFormat.of().formatHex(entity.payload());
    }

    private static BlockEntityData parseEntity(String description) {
        String[] parts = description.split("@");
        return new BlockEntityData(ResourceId.parse(parts[0]), Integer.parseInt(parts[1]), HexFormat.of().parseHex(parts[2]));
    }
}
