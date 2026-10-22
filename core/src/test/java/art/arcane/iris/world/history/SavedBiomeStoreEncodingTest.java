package art.arcane.iris.world.history;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import static org.junit.Assert.assertArrayEquals;

public class SavedBiomeStoreEncodingTest {
    @Test
    public void encodedRecordsMatchTheReferenceEncoding() throws Exception {
        Method encode = SavedBiomeStore.class.getDeclaredMethod("encode", SavedBiomeChunk.class);
        encode.setAccessible(true);
        Random random = new Random(20261022L);
        for (int sample = 0; sample < 40; sample++) {
            SavedBiomeChunk chunk = randomChunk(random, sample);
            assertArrayEquals("sample " + sample, referenceEncode(chunk), (byte[]) encode.invoke(null, chunk));
        }
    }

    private static SavedBiomeChunk randomChunk(Random random, int sample) {
        long activation = 1L + random.nextInt(9);
        int minimumY = -64;
        int height = 384;
        List<SavedBiomeChunk.Cell> cells = new ArrayList<>();
        int distinctCells = 1 + random.nextInt(sample % 4 == 0 ? 3 : 24);
        for (int index = 0; index < distinctCells; index++) {
            cells.add(random.nextInt(9) == 0
                    ? SavedBiomeChunk.Cell.unresolved(activation)
                    : new SavedBiomeChunk.Cell(activation, "biome_" + random.nextInt(12), "region_" + random.nextInt(3)));
        }
        List<SavedBiomeChunk.Column> shared = new ArrayList<>();
        int distinctColumns = 1 + random.nextInt(sample % 3 == 0 ? 4 : 40);
        for (int index = 0; index < distinctColumns; index++) {
            shared.add(randomColumn(random, cells, minimumY, height));
        }
        SavedBiomeChunk.Builder builder = SavedBiomeChunk.builder(
                new SavedBiomeChunk.Header(3, -7, activation, minimumY, height));
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                SavedBiomeChunk.Column column = shared.get(random.nextInt(shared.size()));
                if (random.nextInt(4) == 0) {
                    column = new SavedBiomeChunk.Column(column.surface(), column.caveBase(), new ArrayList<>(column.vertical()));
                }
                builder.column(x, z, column);
            }
        }
        return builder.build();
    }

    private static SavedBiomeChunk.Column randomColumn(Random random, List<SavedBiomeChunk.Cell> cells,
                                                       int minimumY, int height) {
        List<SavedBiomeChunk.Span> spans = new ArrayList<>();
        int start = minimumY;
        int end = minimumY + height;
        while (start < end) {
            int top = Math.min(end, start + 1 + random.nextInt(96));
            spans.add(new SavedBiomeChunk.Span(start, top, cells.get(random.nextInt(cells.size()))));
            start = top;
        }
        return new SavedBiomeChunk.Column(cells.get(random.nextInt(cells.size())),
                cells.get(random.nextInt(cells.size())), spans);
    }

    private static byte[] referenceEncode(SavedBiomeChunk chunk) throws IOException {
        LinkedHashMap<SavedBiomeChunk.Cell, Integer> palette = new LinkedHashMap<>();
        LinkedHashMap<SavedBiomeChunk.Column, Integer> columns = new LinkedHashMap<>();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                SavedBiomeChunk.Column column = chunk.column(x, z);
                columns.computeIfAbsent(column, ignored -> columns.size());
                palette.computeIfAbsent(column.surface(), ignored -> palette.size());
                palette.computeIfAbsent(column.caveBase(), ignored -> palette.size());
                for (SavedBiomeChunk.Span span : column.vertical()) {
                    palette.computeIfAbsent(span.cell(), ignored -> palette.size());
                }
            }
        }
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(raw)) {
            output.writeLong(chunk.activationId());
            output.writeInt(chunk.header().minimumY());
            output.writeInt(chunk.header().height());
            output.writeInt(palette.size());
            for (SavedBiomeChunk.Cell cell : palette.keySet()) {
                output.writeLong(cell.activationId());
                output.writeByte(cell.isResolved() ? 0 : 1);
                if (cell.isResolved()) {
                    output.writeUTF(cell.biomeKey());
                    output.writeUTF(cell.regionKey());
                }
            }
            output.writeInt(columns.size());
            for (SavedBiomeChunk.Column column : columns.keySet()) {
                writeUnsigned(output, palette.get(column.surface()));
                writeUnsigned(output, palette.get(column.caveBase()));
                writeUnsigned(output, column.vertical().size());
                for (SavedBiomeChunk.Span span : column.vertical()) {
                    writeUnsigned(output, span.maximumYExclusive() - chunk.header().minimumY());
                    writeUnsigned(output, palette.get(span.cell()));
                }
            }
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    output.writeByte(columns.get(chunk.column(x, z)));
                }
            }
        }
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(encoded);
        output.writeInt(chunk.chunkX());
        output.writeInt(chunk.chunkZ());
        output.writeInt(raw.size());
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try (DeflaterOutputStream compressed = new DeflaterOutputStream(output, deflater)) {
            raw.writeTo(compressed);
        } finally {
            deflater.end();
        }
        return encoded.toByteArray();
    }

    private static void writeUnsigned(DataOutputStream output, int value) throws IOException {
        int remaining = value;
        while ((remaining & ~0x7f) != 0) {
            output.writeByte((remaining & 0x7f) | 0x80);
            remaining >>>= 7;
        }
        output.writeByte(remaining);
    }
}
