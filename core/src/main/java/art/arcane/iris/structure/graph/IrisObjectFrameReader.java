package art.arcane.iris.structure.graph;

import art.arcane.iris.structure.object.IrisObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class IrisObjectFrameReader {
    private static final String HEADER = "Iris V2 IOB;";
    private static final int MAX_FILE_BYTES = 64 * 1024 * 1024;
    private static final int MAX_TILE_COUNT = 65_536;
    private static final int MAX_CANDIDATE_OFFSETS = 1_024;
    private static final long MAX_TILE_PARSE_STATES = 262_144L;
    private static final int MAX_LEGACY_SIGN_COLOR_INDEX = 15;

    private IrisObjectFrameReader() {
    }

    public static IrisObject readBounds(InputStream stream, String resourceName) throws IOException {
        String activeResourceName = resourceName == null || resourceName.isBlank()
                ? "<unknown>" : resourceName;
        try {
            byte[] content = readLimitedContent(Objects.requireNonNull(stream), activeResourceName);
            return readBounds(content, activeResourceName);
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Malformed Iris object resource ")) {
                throw e;
            }
            throw malformed(activeResourceName, e.getMessage() == null
                    ? e.getClass().getSimpleName() : e.getMessage());
        } catch (RuntimeException e) {
            throw malformed(activeResourceName, e.getMessage() == null
                    ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private static IrisObject readBounds(byte[] content, String resourceName) throws IOException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(content);
        DataInputStream input = new DataInputStream(bytes);
        int width = input.readInt();
        int height = input.readInt();
        int depth = input.readInt();
        long volume = requireDimensions(resourceName, width, height, depth);
        if (!HEADER.equals(input.readUTF())) {
            throw malformed(resourceName, "invalid header");
        }

        int paletteSize = input.readShort();
        if (paletteSize < 0) {
            throw malformed(resourceName, "palette exceeds the signed IOB limit");
        }
        for (int index = 0; index < paletteSize; index++) {
            if (input.readUTF().isBlank()) {
                throw malformed(resourceName, "palette entry " + index + " is blank");
            }
        }

        int blockCount = input.readInt();
        if (blockCount < 0 || blockCount > volume || blockCount > 0 && paletteSize == 0) {
            throw malformed(resourceName, "invalid block count " + blockCount);
        }
        for (int index = 0; index < blockCount; index++) {
            int x = input.readShort();
            int y = input.readShort();
            int z = input.readShort();
            int paletteIndex = input.readShort();
            requirePosition(resourceName, "block", index, x, y, z, width, height, depth);
            if (paletteIndex < 0 || paletteIndex >= paletteSize) {
                throw malformed(resourceName, "block " + index
                        + " references invalid palette index " + paletteIndex);
            }
        }

        int tileCount = input.readInt();
        if (tileCount < 0 || tileCount > volume) {
            throw malformed(resourceName, "invalid tile count " + tileCount);
        }
        if (tileCount > MAX_TILE_COUNT) {
            throw malformed(resourceName, "tile count " + tileCount
                    + " exceeds limit " + MAX_TILE_COUNT);
        }
        int tileStart = content.length - bytes.available();
        requireCompleteTileFraming(content, tileStart, tileCount, width, height, depth, resourceName);
        return new IrisObject(width, height, depth);
    }

    private static void requireCompleteTileFraming(
            byte[] content,
            int tileStart,
            int tileCount,
            int width,
            int height,
            int depth,
            String resourceName
    ) throws IOException {
        Set<Integer> offsets = Set.of(tileStart);
        long parseStates = 0L;
        for (int tileIndex = 0; tileIndex < tileCount; tileIndex++) {
            Set<Integer> nextOffsets = new LinkedHashSet<>();
            TileFailureTracker failures = new TileFailureTracker();
            for (int offset : offsets) {
                parseStates++;
                requireParseStateBudget(resourceName, tileIndex, parseStates);
                TilePrefix prefix = readTilePrefix(
                        content, offset, width, height, depth, tileIndex, failures);
                if (prefix == null) {
                    continue;
                }
                int modernEnd = readModernTileEnd(
                        content, prefix.payloadOffset(), tileIndex, failures);
                if (modernEnd >= 0) {
                    addCandidateOffset(resourceName, tileIndex, nextOffsets, modernEnd);
                }
                for (int legacyEnd : readLegacyTileEnds(
                        content, prefix.payloadOffset(), tileIndex, failures)) {
                    addCandidateOffset(resourceName, tileIndex, nextOffsets, legacyEnd);
                }
            }
            if (nextOffsets.isEmpty()) {
                throw malformed(resourceName, failures.detailOr(
                        "tile " + tileIndex + " is truncated or malformed"));
            }
            offsets = nextOffsets;
        }
        if (!offsets.contains(content.length)) {
            throw malformed(resourceName, "tile records leave trailing or incomplete data");
        }
    }

    private static TilePrefix readTilePrefix(
            byte[] content,
            int offset,
            int width,
            int height,
            int depth,
            int tileIndex,
            TileFailureTracker failures
    ) {
        try {
            ByteArrayInputStream bytes = slice(content, offset);
            DataInputStream input = new DataInputStream(bytes);
            int x = input.readShort();
            int y = input.readShort();
            int z = input.readShort();
            if (!withinSignedObjectAxis(x, width) || !withinSignedObjectAxis(y, height)
                    || !withinSignedObjectAxis(z, depth)) {
                failures.record("tile " + tileIndex + " position " + x + "," + y + "," + z
                        + " is outside object bounds", 20);
                return null;
            }
            return new TilePrefix(content.length - bytes.available());
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static int readModernTileEnd(
            byte[] content,
            int offset,
            int tileIndex,
            TileFailureTracker failures
    ) {
        try {
            ByteArrayInputStream bytes = slice(content, offset);
            DataInputStream input = new DataInputStream(bytes);
            String material = input.readUTF();
            if (material.isBlank()) {
                failures.record("tile " + tileIndex + " modern material is blank", 40);
                return -1;
            }
            JsonElement properties = JsonParser.parseString(input.readUTF());
            if (!properties.isJsonObject()) {
                failures.record("tile " + tileIndex
                        + " modern properties are not a JSON object", 30);
                return -1;
            }
            return content.length - bytes.available();
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    private static Set<Integer> readLegacyTileEnds(
            byte[] content,
            int offset,
            int tileIndex,
            TileFailureTracker failures
    ) {
        try {
            ByteArrayInputStream bytes = slice(content, offset);
            DataInputStream input = new DataInputStream(bytes);
            int id = input.readShort();
            return switch (id) {
                case 0 -> singletonEnd(content, readLegacySign(input, bytes, tileIndex, failures));
                case 1 -> readLegacySpawnerEnds(content, content.length - bytes.available());
                case 2 -> readLegacyBannerEnds(content, input, bytes);
                case 3 -> singletonEnd(content, readLegacyLootable(input, bytes));
                default -> Set.of();
            };
        } catch (IOException | RuntimeException e) {
            return Set.of();
        }
    }

    private static int readLegacySign(
            DataInputStream input,
            ByteArrayInputStream bytes,
            int tileIndex,
            TileFailureTracker failures
    ) throws IOException {
        input.readUTF();
        input.readUTF();
        input.readUTF();
        input.readUTF();
        int colorIndex = input.readByte();
        if (colorIndex < 0 || colorIndex > MAX_LEGACY_SIGN_COLOR_INDEX) {
            failures.record("tile " + tileIndex + " legacy sign color index " + colorIndex
                    + " is outside 0.." + MAX_LEGACY_SIGN_COLOR_INDEX, 50);
            throw new IOException("Invalid legacy sign color index");
        }
        return bytes.available();
    }

    private static Set<Integer> readLegacySpawnerEnds(byte[] content, int payloadOffset) {
        Set<Integer> ends = new LinkedHashSet<>();
        try {
            ByteArrayInputStream keyedBytes = slice(content, payloadOffset);
            new DataInputStream(keyedBytes).readUTF();
            ends.add(content.length - keyedBytes.available());
        } catch (IOException | RuntimeException ignored) {
        }
        if (payloadOffset <= content.length - Short.BYTES) {
            ends.add(payloadOffset + Short.BYTES);
        }
        return Set.copyOf(ends);
    }

    private static Set<Integer> readLegacyBannerEnds(
            byte[] content,
            DataInputStream input,
            ByteArrayInputStream bytes
    ) throws IOException {
        input.readUnsignedByte();
        int patternCount = input.readUnsignedByte();
        int patternsOffset = content.length - bytes.available();
        Set<Integer> ends = new LinkedHashSet<>();
        try {
            ByteArrayInputStream keyedBytes = slice(content, patternsOffset);
            DataInputStream keyedInput = new DataInputStream(keyedBytes);
            for (int index = 0; index < patternCount; index++) {
                keyedInput.readUnsignedByte();
                keyedInput.readUTF();
            }
            ends.add(content.length - keyedBytes.available());
        } catch (IOException | RuntimeException ignored) {
        }
        long legacyEnd = (long) patternsOffset + (long) patternCount * 2L;
        if (legacyEnd <= content.length) {
            ends.add((int) legacyEnd);
        }
        return Set.copyOf(ends);
    }

    private static int readLegacyLootable(DataInputStream input, ByteArrayInputStream bytes) throws IOException {
        input.readUTF();
        input.readUTF();
        input.readLong();
        return bytes.available();
    }

    private static Set<Integer> singletonEnd(byte[] content, int remaining) {
        return Set.of(content.length - remaining);
    }

    private static byte[] readLimitedContent(InputStream stream, String resourceName) throws IOException {
        return readLimitedContent(stream, resourceName, MAX_FILE_BYTES);
    }

    static byte[] readLimitedContent(
            InputStream stream,
            String resourceName,
            int maximumBytes
    ) throws IOException {
        byte[] content = stream.readNBytes(maximumBytes + 1);
        if (content.length > maximumBytes) {
            throw malformed(resourceName, "file exceeds " + maximumBytes + "-byte limit");
        }
        return content;
    }

    private static void addCandidateOffset(
            String resourceName,
            int tileIndex,
            Set<Integer> offsets,
            int offset
    ) throws IOException {
        offsets.add(offset);
        if (offsets.size() > MAX_CANDIDATE_OFFSETS) {
            throw malformed(resourceName, "tile " + tileIndex + " exceeds candidate-offset limit "
                    + MAX_CANDIDATE_OFFSETS);
        }
    }

    private static void requireParseStateBudget(
            String resourceName,
            int tileIndex,
            long parseStates
    ) throws IOException {
        if (parseStates > MAX_TILE_PARSE_STATES) {
            throw malformed(resourceName, "tile " + tileIndex + " exceeds parse-state limit "
                    + MAX_TILE_PARSE_STATES);
        }
    }

    private static ByteArrayInputStream slice(byte[] content, int offset) {
        if (offset < 0 || offset > content.length) {
            throw new IllegalArgumentException("Invalid IOB frame offset " + offset);
        }
        return new ByteArrayInputStream(content, offset, content.length - offset);
    }

    private static long requireDimensions(String resourceName, int width, int height, int depth) throws IOException {
        if (width < 1 || height < 1 || depth < 1) {
            throw malformed(resourceName, "invalid dimensions " + width + "x" + height + "x" + depth);
        }
        try {
            return Math.multiplyExact(Math.multiplyExact((long) width, height), depth);
        } catch (ArithmeticException e) {
            throw malformed(resourceName, "dimensions overflow the IOB volume limit");
        }
    }

    private static void requirePosition(
            String resourceName,
            String type,
            int index,
            int x,
            int y,
            int z,
            int width,
            int height,
            int depth
    ) throws IOException {
        if (!withinSignedObjectAxis(x, width) || !withinSignedObjectAxis(y, height)
                || !withinSignedObjectAxis(z, depth)) {
            throw malformed(resourceName, type + " " + index + " position "
                    + x + "," + y + "," + z + " is outside object bounds");
        }
    }

    private static boolean withinSignedObjectAxis(int coordinate, int size) {
        int center = size / 2;
        return coordinate >= -center && coordinate < size - center;
    }

    private static IOException malformed(String resourceName, String detail) {
        return new IOException("Malformed Iris object resource " + resourceName + ": " + detail);
    }

    private record TilePrefix(int payloadOffset) {
    }

    private static final class TileFailureTracker {
        private String detail;
        private int priority = Integer.MIN_VALUE;

        private void record(String candidateDetail, int candidatePriority) {
            if (candidatePriority <= priority) {
                return;
            }
            detail = candidateDetail;
            priority = candidatePriority;
        }

        private String detailOr(String fallback) {
            return detail == null ? fallback : detail;
        }
    }
}
