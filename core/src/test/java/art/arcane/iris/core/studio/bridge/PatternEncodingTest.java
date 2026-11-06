package art.arcane.iris.core.studio.bridge;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.volmit.block.BlockState;
import com.volmit.tooling.BlockPattern;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class PatternEncodingTest {
    @Test
    public void picksFollowTheHeaderRowByRowWithNothingAsNoBlock() {
        BlockState grass = BlockState.parse("minecraft:grass_block[snowy=false]");
        BlockState dirt = BlockState.parse("minecraft:dirt");
        BlockPattern pattern = BlockPattern.of(2, new BlockState[]{grass, null, dirt, grass});

        byte[] encoded = PatternEncoding.encode(pattern, 4, new Gson());

        ByteBuffer body = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);
        int headerLength = body.getInt();
        JsonObject header = JsonParser.parseString(new String(encoded, 4, headerLength, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("[\"minecraft:grass_block[snowy=false]\",\"minecraft:dirt\"]", header.get("blocks").toString());
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
}
