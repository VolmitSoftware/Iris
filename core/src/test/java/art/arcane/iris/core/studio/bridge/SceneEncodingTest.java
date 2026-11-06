package art.arcane.iris.core.studio.bridge;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockBox;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.GeneratedObject;
import com.volmit.object.ObjectBlock;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class SceneEncodingTest {
    @Test
    public void blocksCountFromTheAnchorIndexOnePaletteAndLeaveAirOut() {
        GeneratedObject tree = new GeneratedObject(new BlockBox(0, 0, 0, 3, 3, 3), new BlockPosition(1, 0, 1), List.of(
                new ObjectBlock(new BlockPosition(1, 0, 1), BlockState.parse("minecraft:oak_log[axis=y]")),
                new ObjectBlock(new BlockPosition(1, 1, 1), BlockState.parse("minecraft:oak_log[axis=y]")),
                new ObjectBlock(new BlockPosition(0, 2, 1), BlockState.parse("minecraft:air")),
                new ObjectBlock(new BlockPosition(1, 2, 1), BlockState.parse("minecraft:oak_leaves"))), List.of());

        byte[] encoded = SceneEncoding.encode(tree, new Gson());

        ByteBuffer body = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);
        int headerLength = body.getInt();
        JsonObject header = JsonParser.parseString(new String(encoded, 4, headerLength, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("[\"minecraft:oak_log[axis=y]\",\"minecraft:oak_leaves\"]", header.get("palette").toString());
        assertEquals(3, header.get("blockCount").getAsInt());
        assertEquals("[-1,0,-1]", header.get("min").toString());
        assertEquals("[2,3,2]", header.get("max").toString());
        int blocksAt = (4 + headerLength + 3) / 4 * 4;
        assertEquals(blocksAt + 3 * 16, encoded.length);
        body.position(blocksAt + 2 * 16);
        assertEquals(List.of(0, 2, 0, 1), List.of(body.getInt(), body.getInt(), body.getInt(), body.getInt()));
    }
}
