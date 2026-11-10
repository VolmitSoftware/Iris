package art.arcane.iris.core.service;

import art.arcane.volmlib.util.collection.KMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import com.google.gson.ToNumberPolicy;
import com.volmit.object.BlockEntityData;
import com.volmit.resource.ResourceId;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;

/**
 * The block entities of the studio's jigsaw markers in the host's tile format, {@code iris:tile} version 1: the block's
 * key and its NBT as JSON, which is the form the host keeps tile data in everywhere else.
 */
final class JigsawStudioTileCodec {
    static final ResourceId TILE_FORMAT = ResourceId.parse("iris:tile");
    static final int TILE_FORMAT_VERSION = 1;

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setStrictness(Strictness.LENIENT)
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .create();

    private JigsawStudioTileCodec() {
    }

    static BlockEntityData encode(String blockKey, Map<String, Object> nbt) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(blockKey);
            out.writeUTF(GSON.toJson(nbt));
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot store the NBT of " + blockKey, failure);
        }
        return new BlockEntityData(TILE_FORMAT, TILE_FORMAT_VERSION, bytes.toByteArray());
    }

    /** The NBT {@code data} holds; empty when it is not in the tile format. */
    static Optional<KMap<String, Object>> nbt(BlockEntityData data) {
        if (!TILE_FORMAT.equals(data.format()) || data.version() != TILE_FORMAT_VERSION) {
            return Optional.empty();
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data.payload()))) {
            in.readUTF();
            @SuppressWarnings("unchecked")
            KMap<String, Object> nbt = GSON.fromJson(in.readUTF(), KMap.class);
            return Optional.ofNullable(nbt);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot read a block entity of format " + TILE_FORMAT, failure);
        }
    }
}
