package art.arcane.iris.probe;

import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.collection.KMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class StubTileData extends TileData {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setStrictness(Strictness.LENIENT)
            .setObjectToNumberStrategy(com.google.gson.ToNumberPolicy.LONG_OR_DOUBLE)
            .create();

    private final String blockKey;
    private final KMap<String, Object> tileProperties;
    private final byte[] binary;

    private StubTileData(String blockKey, KMap<String, Object> tileProperties, byte[] binary) {
        super();
        this.blockKey = normalizeBlockKey(blockKey);
        this.tileProperties = tileProperties.copy();
        this.binary = Arrays.copyOf(binary, binary.length);
    }

    public static StubTileData read(DataInputStream input) throws IOException {
        if (!input.markSupported()) {
            throw new IOException("Probe tile-data input does not support mark/reset");
        }
        input.mark(Integer.MAX_VALUE);
        try {
            return readModern(input);
        } catch (IOException | RuntimeException error) {
            input.reset();
            return readLegacy(input);
        } finally {
            input.mark(0);
        }
    }

    static StubTileData fromProperties(PlatformBlockState state, KMap<String, Object> properties) {
        if (state == null) {
            throw new IllegalArgumentException("Probe tile data requires a block state");
        }
        String blockKey = normalizeBlockKey(state.placementBaseState().key());
        KMap<String, Object> copied = properties == null ? new KMap<>() : properties.copy();
        return new StubTileData(blockKey, copied, encodeModern(blockKey, copied));
    }

    private static StubTileData readModern(DataInputStream input) throws IOException {
        String blockKey = input.readUTF();
        if (!isResourceKey(blockKey)) {
            throw new IOException("Probe tile data has an invalid block key: " + blockKey);
        }
        KMap<String, Object> properties = properties(input.readUTF());
        if (properties == null) {
            throw new IOException("Probe tile data has invalid properties for " + blockKey);
        }
        return new StubTileData(blockKey, properties, encodeModern(blockKey, properties));
    }

    private static StubTileData readLegacy(DataInputStream input) throws IOException {
        int type = input.readUnsignedShort();
        return switch (type) {
            case 0 -> readLegacySign(input, type);
            case 1 -> readLegacySpawner(input, type);
            case 2 -> readLegacyBanner(input, type);
            case 3 -> readLegacyLootable(input, type);
            default -> throw new IOException("Unknown probe tile-data type: " + type);
        };
    }

    private static StubTileData readLegacySign(DataInputStream input, int type) throws IOException {
        List<String> lines = List.of(
                input.readUTF(), input.readUTF(), input.readUTF(), input.readUTF());
        int color = input.readUnsignedByte();
        KMap<String, Object> properties = new KMap<>();
        properties.put("lines", lines);
        properties.put("color", color);
        byte[] binary = encode(output -> {
            output.writeShort(type);
            for (String line : lines) {
                output.writeUTF(line);
            }
            output.writeByte(color);
        });
        return new StubTileData("minecraft:oak_sign", properties, binary);
    }

    private static StubTileData readLegacySpawner(DataInputStream input, int type) throws IOException {
        input.mark(Integer.MAX_VALUE);
        String entityKey = null;
        try {
            String candidate = input.readUTF();
            if (isResourceKey(candidate)) {
                entityKey = candidate;
            } else {
                input.reset();
            }
        } catch (IOException | RuntimeException error) {
            input.reset();
        }
        int legacyOrdinal = -1;
        if (entityKey == null) {
            legacyOrdinal = input.readShort();
        }
        KMap<String, Object> properties = new KMap<>();
        properties.put("entity", entityKey == null ? legacyOrdinal : entityKey);
        String resolvedEntityKey = entityKey;
        int resolvedLegacyOrdinal = legacyOrdinal;
        byte[] binary = encode(output -> {
            output.writeShort(type);
            if (resolvedEntityKey == null) {
                output.writeShort(resolvedLegacyOrdinal);
            } else {
                output.writeUTF(resolvedEntityKey);
            }
        });
        return new StubTileData("minecraft:spawner", properties, binary);
    }

    private static StubTileData readLegacyBanner(DataInputStream input, int type) throws IOException {
        int baseColor = input.readUnsignedByte();
        int patternCount = input.readUnsignedByte();
        input.mark(Integer.MAX_VALUE);
        List<BannerPattern> patterns = new ArrayList<>(patternCount);
        boolean keyed = true;
        try {
            for (int i = 0; i < patternCount; i++) {
                int color = input.readUnsignedByte();
                String pattern = input.readUTF();
                if (!isResourceKey(pattern)) {
                    throw new IOException("Invalid probe banner pattern: " + pattern);
                }
                patterns.add(new BannerPattern(color, pattern, -1));
            }
        } catch (IOException | RuntimeException error) {
            keyed = false;
            patterns.clear();
            input.reset();
            for (int i = 0; i < patternCount; i++) {
                patterns.add(new BannerPattern(input.readUnsignedByte(), null, input.readUnsignedByte()));
            }
        }
        KMap<String, Object> properties = new KMap<>();
        properties.put("baseColor", baseColor);
        properties.put("patterns", patterns);
        boolean resolvedKeyed = keyed;
        byte[] binary = encode(output -> {
            output.writeShort(type);
            output.writeByte(baseColor);
            output.writeByte(patternCount);
            for (BannerPattern pattern : patterns) {
                output.writeByte(pattern.color());
                if (resolvedKeyed) {
                    output.writeUTF(pattern.key());
                } else {
                    output.writeByte(pattern.legacyOrdinal());
                }
            }
        });
        return new StubTileData("minecraft:white_banner", properties, binary);
    }

    private static StubTileData readLegacyLootable(DataInputStream input, int type) throws IOException {
        String blockKey = input.readUTF();
        String lootTable = input.readUTF();
        long seed = input.readLong();
        if (!isResourceKey(blockKey)) {
            throw new IOException("Probe lootable tile has an invalid block key: " + blockKey);
        }
        KMap<String, Object> properties = new KMap<>();
        properties.put("LootTable", lootTable);
        properties.put("LootTableSeed", seed);
        byte[] binary = encode(output -> {
            output.writeShort(type);
            output.writeUTF(blockKey);
            output.writeUTF(lootTable);
            output.writeLong(seed);
        });
        return new StubTileData(blockKey, properties, binary);
    }

    private static byte[] encodeModern(String blockKey, KMap<String, Object> properties) {
        try {
            return encode(output -> {
                output.writeUTF(blockKey);
                output.writeUTF(GSON.toJson(properties));
            });
        } catch (IOException error) {
            throw new IllegalStateException("Failed to encode probe tile data for " + blockKey, error);
        }
    }

    private static byte[] encode(BinaryWriter writer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            writer.write(output);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static KMap<String, Object> properties(String json) {
        return GSON.fromJson(json, KMap.class);
    }

    private static boolean isResourceKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        return key.matches("[a-z0-9_.-]+:[a-z0-9/._-]+");
    }

    private static String normalizeBlockKey(String blockKey) {
        int properties = blockKey.indexOf('[');
        return properties < 0 ? blockKey : blockKey.substring(0, properties);
    }

    String blockKey() {
        return blockKey;
    }

    @Override
    public String getMaterialKey() {
        return blockKey;
    }

    @Override
    public KMap<String, Object> getProperties() {
        return tileProperties;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StubTileData other)) {
            return false;
        }
        return blockKey.equals(other.blockKey) && Arrays.equals(binary, other.binary);
    }

    @Override
    public int hashCode() {
        return 31 * blockKey.hashCode() + Arrays.hashCode(binary);
    }

    @Override
    public void toBinary(DataOutputStream output) throws IOException {
        output.write(binary);
    }

    @Override
    public TileData clone() {
        return new StubTileData(blockKey, tileProperties, binary);
    }

    @Override
    public String toString() {
        return blockKey + GSON.toJson(tileProperties);
    }

    @FunctionalInterface
    private interface BinaryWriter {
        void write(DataOutputStream output) throws IOException;
    }

    private record BannerPattern(int color, String key, int legacyOrdinal) {
    }
}
