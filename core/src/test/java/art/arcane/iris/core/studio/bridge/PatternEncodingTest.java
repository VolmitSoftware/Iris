package art.arcane.iris.core.studio.bridge;

import art.arcane.iris.core.link.CustomBlockLook;
import art.arcane.iris.core.link.Identifier;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.volmit.block.BlockState;
import com.volmit.tooling.BlockPattern;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class PatternEncodingTest {
    @Test
    public void picksFollowTheHeaderRowByRowWithNothingAsNoBlock() {
        BlockState grass = BlockState.parse("minecraft:grass_block[snowy=false]");
        BlockState dirt = BlockState.parse("minecraft:dirt");
        BlockPattern pattern = BlockPattern.of(2, new BlockState[]{grass, null, dirt, grass});

        byte[] encoded = PatternEncoding.encode(pattern, 4, new Gson(), state -> Optional.empty());

        ByteBuffer body = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);
        int headerLength = body.getInt();
        JsonObject header = headerOf(encoded);
        assertEquals("[\"minecraft:grass_block[snowy=false]\",\"minecraft:dirt\"]", header.get("blocks").toString());
        assertEquals(header.get("blocks"), header.get("drawnAs"));
        assertEquals(2, header.get("columns").getAsInt());
        assertEquals(4, header.get("blocksPerColumn").getAsInt());
        assertEquals(-1, header.get("noBlock").getAsInt());
        int picksAt = (4 + headerLength + 3) / 4 * 4;
        assertEquals(picksAt + 4 * Short.BYTES, encoded.length);
        body.position(picksAt);
        assertEquals(0, body.getShort());
        assertEquals(-1, body.getShort());
        assertEquals(1, body.getShort());
        assertEquals(0, body.getShort());
    }

    @Test
    public void aPluginBlockIsNamedAsThePackNamesItAndDrawnAsTheStatePlayersSee() {
        BlockState serverOnly = BlockState.parse("craftengine:custom_7");
        BlockPattern pattern = BlockPattern.of(1, new BlockState[]{serverOnly});
        CustomBlockLook grass = new CustomBlockLook(new Identifier("etopia", "grass_block"), "minecraft:note_block[instrument=basedrum,note=7,powered=false]");

        JsonObject header = headerOf(PatternEncoding.encode(pattern, 1, new Gson(), state -> Optional.of(grass)));

        assertEquals("[\"etopia:grass_block\"]", header.get("blocks").toString());
        assertEquals("[\"minecraft:note_block[instrument=basedrum,note=7,powered=false]\"]", header.get("drawnAs").toString());
    }

    private static JsonObject headerOf(byte[] encoded) {
        int headerLength = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN).getInt();
        return JsonParser.parseString(new String(encoded, 4, headerLength, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
