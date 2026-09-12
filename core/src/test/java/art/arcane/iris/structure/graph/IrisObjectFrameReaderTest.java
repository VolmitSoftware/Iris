package art.arcane.iris.structure.graph;

import art.arcane.iris.structure.object.IrisObject;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IrisObjectFrameReaderTest {
    @Test
    public void acceptsModernTileFrame() throws Exception {
        IrisObject object = readObject(tile -> {
            tile.writeUTF("minecraft:chest");
            tile.writeUTF("{}");
        });

        assertEquals(1, object.getW());
        assertEquals(1, object.getH());
        assertEquals(1, object.getD());
    }

    @Test
    public void acceptsEveryLegacyTileFrame() throws Exception {
        readObject(tile -> {
            tile.writeShort(0);
            tile.writeUTF("one");
            tile.writeUTF("two");
            tile.writeUTF("three");
            tile.writeUTF("four");
            tile.writeByte(0);
        });
        readObject(tile -> {
            tile.writeShort(1);
            tile.writeUTF("minecraft:pig");
        });
        readObject(tile -> {
            tile.writeShort(1);
            tile.writeShort(0);
        });
        readObject(tile -> {
            tile.writeShort(2);
            tile.writeByte(0);
            tile.writeByte(1);
            tile.writeByte(0);
            tile.writeUTF("minecraft:base");
        });
        readObject(tile -> {
            tile.writeShort(2);
            tile.writeByte(0);
            tile.writeByte(1);
            tile.writeByte(0);
            tile.writeByte(0);
        });
        readObject(tile -> {
            tile.writeShort(3);
            tile.writeUTF("minecraft:chest");
            tile.writeUTF("minecraft:chests/simple_dungeon");
            tile.writeLong(42L);
        });
    }

    @Test
    public void acceptsLegacySignColorBoundary() throws Exception {
        readObject(tile -> writeLegacySign(tile, 15));
    }

    @Test
    public void rejectsBlankModernTileMaterialWithResourceContext() throws Exception {
        byte[] content = objectBytes(tile -> {
            tile.writeUTF("");
            tile.writeUTF("{}");
        });

        IOException exception = assertThrows(IOException.class, () -> IrisObjectFrameReader.readBounds(
                new ByteArrayInputStream(content), "objects/blank-material.iob"));

        assertEquals("Malformed Iris object resource objects/blank-material.iob: "
                + "tile 0 modern material is blank", exception.getMessage());
    }

    @Test
    public void rejectsLegacySignColorsOutsideRuntimeRange() throws Exception {
        IOException negative = readFailure(tile -> writeLegacySign(tile, -1), "objects/sign-negative.iob");
        IOException overflow = readFailure(tile -> writeLegacySign(tile, 16), "objects/sign-overflow.iob");

        assertTrue(negative.getMessage(), negative.getMessage().endsWith(
                "tile 0 legacy sign color index -1 is outside 0..15"));
        assertTrue(overflow.getMessage(), overflow.getMessage().endsWith(
                "tile 0 legacy sign color index 16 is outside 0..15"));
    }

    @Test
    public void rejectsTileCountsAboveValidationLimit() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(257);
            output.writeInt(257);
            output.writeInt(1);
            output.writeUTF("Iris V2 IOB;");
            output.writeShort(0);
            output.writeInt(0);
            output.writeInt(65_537);
        }

        IOException exception = assertThrows(IOException.class, () -> IrisObjectFrameReader.readBounds(
                new ByteArrayInputStream(bytes.toByteArray()), "objects/too-many-tiles.iob"));

        assertTrue(exception.getMessage(), exception.getMessage().endsWith(
                "tile count 65537 exceeds limit 65536"));
    }

    @Test
    public void boundsObjectInputBeforeUnboundedAllocation() throws Exception {
        byte[] content = new byte[33];

        IOException exception = assertThrows(IOException.class, () -> IrisObjectFrameReader.readLimitedContent(
                new ByteArrayInputStream(content), "objects/oversized.iob", 32));

        assertEquals("Malformed Iris object resource objects/oversized.iob: "
                + "file exceeds 32-byte limit", exception.getMessage());
    }

    @Test
    public void rejectsTruncatedTileFrame() throws Exception {
        byte[] content = objectBytes(tile -> tile.writeUTF("minecraft:chest"));

        assertThrows(IOException.class, () -> IrisObjectFrameReader.readBounds(
                new ByteArrayInputStream(content), "objects/truncated.iob"));
    }

    @Test
    public void rejectsTrailingBytes() throws Exception {
        byte[] content = objectBytes(tile -> {
            tile.writeUTF("minecraft:chest");
            tile.writeUTF("{}");
            tile.writeByte(1);
        });

        assertThrows(IOException.class, () -> IrisObjectFrameReader.readBounds(
                new ByteArrayInputStream(content), "objects/trailing.iob"));
    }

    private IrisObject readObject(TileWriter tileWriter) throws Exception {
        return IrisObjectFrameReader.readBounds(
                new ByteArrayInputStream(objectBytes(tileWriter)), "objects/test.iob");
    }

    private IOException readFailure(TileWriter tileWriter, String resourceName) throws Exception {
        byte[] content = objectBytes(tileWriter);
        return assertThrows(IOException.class, () -> IrisObjectFrameReader.readBounds(
                new ByteArrayInputStream(content), resourceName));
    }

    private void writeLegacySign(DataOutputStream tile, int colorIndex) throws IOException {
        tile.writeShort(0);
        tile.writeUTF("one");
        tile.writeUTF("two");
        tile.writeUTF("three");
        tile.writeUTF("four");
        tile.writeByte(colorIndex);
    }

    private byte[] objectBytes(TileWriter tileWriter) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(1);
            output.writeInt(1);
            output.writeInt(1);
            output.writeUTF("Iris V2 IOB;");
            output.writeShort(1);
            output.writeUTF("minecraft:chest");
            output.writeInt(1);
            output.writeShort(0);
            output.writeShort(0);
            output.writeShort(0);
            output.writeShort(0);
            output.writeInt(1);
            output.writeShort(0);
            output.writeShort(0);
            output.writeShort(0);
            tileWriter.write(output);
        }
        return bytes.toByteArray();
    }

    @FunctionalInterface
    private interface TileWriter {
        void write(DataOutputStream output) throws IOException;
    }
}
