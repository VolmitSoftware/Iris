/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.modded;

import art.arcane.iris.generation.block.TileData;
import art.arcane.volmlib.util.collection.KMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPattern;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public final class ModdedTileReader implements TileData.TileReader {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setStrictness(Strictness.LENIENT).setObjectToNumberStrategy(com.google.gson.ToNumberPolicy.LONG_OR_DOUBLE).create();
    private static final int DYE_COLOR_COUNT = 16;
    private static final Identifier DEFAULT_SPAWNER_ENTITY = Identifier.parse("minecraft:pig");
    private static final Identifier DEFAULT_BANNER_PATTERN = Identifier.parse("minecraft:base");
    private static final List<Identifier> LEGACY_BUKKIT_ENTITY_TYPES = createLegacyBukkitEntityTypes();
    private static final List<Identifier> PAPER_26_2_BANNER_PATTERNS = List.of(
            Identifier.parse("minecraft:small_stripes"),
            Identifier.parse("minecraft:stripe_right"),
            Identifier.parse("minecraft:diagonal_left"),
            Identifier.parse("minecraft:stripe_middle"),
            Identifier.parse("minecraft:square_bottom_right"),
            Identifier.parse("minecraft:half_horizontal"),
            Identifier.parse("minecraft:skull"),
            Identifier.parse("minecraft:flow"),
            Identifier.parse("minecraft:rhombus"),
            Identifier.parse("minecraft:border"),
            Identifier.parse("minecraft:gradient_up"),
            Identifier.parse("minecraft:guster"),
            Identifier.parse("minecraft:square_top_left"),
            Identifier.parse("minecraft:triangle_bottom"),
            Identifier.parse("minecraft:triangles_bottom"),
            Identifier.parse("minecraft:half_horizontal_bottom"),
            Identifier.parse("minecraft:gradient"),
            Identifier.parse("minecraft:triangle_top"),
            Identifier.parse("minecraft:piglin"),
            Identifier.parse("minecraft:stripe_center"),
            Identifier.parse("minecraft:circle"),
            Identifier.parse("minecraft:stripe_left"),
            Identifier.parse("minecraft:stripe_bottom"),
            Identifier.parse("minecraft:square_top_right"),
            Identifier.parse("minecraft:curly_border"),
            Identifier.parse("minecraft:creeper"),
            Identifier.parse("minecraft:square_bottom_left"),
            Identifier.parse("minecraft:triangles_top"),
            Identifier.parse("minecraft:half_vertical"),
            Identifier.parse("minecraft:mojang"),
            Identifier.parse("minecraft:diagonal_right"),
            Identifier.parse("minecraft:cross"),
            Identifier.parse("minecraft:straight_cross"),
            Identifier.parse("minecraft:bricks"),
            Identifier.parse("minecraft:diagonal_up_left"),
            Identifier.parse("minecraft:base"),
            Identifier.parse("minecraft:flower"),
            Identifier.parse("minecraft:stripe_downleft"),
            Identifier.parse("minecraft:diagonal_up_right"),
            Identifier.parse("minecraft:stripe_downright"),
            Identifier.parse("minecraft:stripe_top"),
            Identifier.parse("minecraft:globe"),
            Identifier.parse("minecraft:half_vertical_right"));

    private final Supplier<MinecraftServer> server;

    public ModdedTileReader(Supplier<MinecraftServer> server) {
        this.server = server;
    }

    private static final class ReplayInputStream extends InputStream {
        private final InputStream source;
        private byte[] buffer = new byte[256];
        private int size = 0;
        private int position = 0;
        private int marked = 0;

        private ReplayInputStream(InputStream source) {
            this.source = source;
        }

        @Override
        public int read() throws IOException {
            if (position < size) {
                int value = buffer[position] & 0xFF;
                position++;
                return value;
            }
            int value = source.read();
            if (value < 0) {
                return value;
            }
            if (size == buffer.length) {
                buffer = Arrays.copyOf(buffer, buffer.length * 2);
            }
            buffer[size] = (byte) value;
            size++;
            position++;
            return value;
        }

        @Override
        public boolean markSupported() {
            return true;
        }

        @Override
        public synchronized void mark(int readLimit) {
            marked = position;
        }

        @Override
        public synchronized void reset() {
            position = marked;
        }

        private void rewind() {
            position = 0;
        }

        private int position() {
            return position;
        }

        private byte[] consumed() {
            return Arrays.copyOf(buffer, position);
        }
    }

    @Override
    public TileData read(DataInputStream in) throws IOException {
        if (!in.markSupported()) {
            throw new IOException("Mark not supported");
        }
        in.mark(Integer.MAX_VALUE);
        ReplayInputStream replay = new ReplayInputStream(in);
        DataInputStream din = new DataInputStream(replay);
        try {
            return parse(din, replay);
        } finally {
            in.reset();
            in.skipNBytes(replay.position());
            in.mark(0);
        }
    }

    private TileData parse(DataInputStream din, ReplayInputStream replay) throws IOException {
        try {
            String materialKey = din.readUTF();
            boolean materialMatched = matchMaterial(materialKey);
            String json = din.readUTF();
            KMap<String, Object> properties = kmapFromJson(json);
            if (!materialMatched) {
                throw new NullPointerException("material is marked non-null but is null");
            }
            if (properties == null) {
                throw new NullPointerException("properties is marked non-null but is null");
            }
            return new ModdedTileData(replay.consumed(), properties,
                    ModdedTileData.normalizeBlockKey(materialKey), -1);
        } catch (Throwable e) {
            replay.rewind();
            return parseLegacy(din, replay);
        }
    }

    @SuppressWarnings("unchecked")
    private static KMap<String, Object> kmapFromJson(String json) {
        return GSON.fromJson(json, KMap.class);
    }

    private TileData parseLegacy(DataInputStream din, ReplayInputStream replay) throws IOException {
        int id = din.readShort();
        String expectedBlockKey = null;
        KMap<String, Object> properties = switch (id) {
            case 0 -> readSign(din);
            case 1 -> readSpawner(din, replay);
            case 2 -> readBanner(din, replay);
            case 3 -> {
                expectedBlockKey = ModdedTileData.normalizeBlockKey(din.readUTF());
                yield readLootable(din);
            }
            default -> throw new IOException("Unknown tile type: " + id);
        };
        return new ModdedTileData(replay.consumed(), properties, expectedBlockKey, id);
    }

    private static KMap<String, Object> readSign(DataInputStream din) throws IOException {
        List<String> messages = List.of(din.readUTF(), din.readUTF(), din.readUTF(), din.readUTF());
        byte dye = din.readByte();
        if (dye < 0 || dye >= DYE_COLOR_COUNT) {
            throw new ArrayIndexOutOfBoundsException("Index " + dye + " out of bounds for length " + DYE_COLOR_COUNT);
        }
        KMap<String, Object> text = new KMap<>();
        text.put("messages", messages);
        text.put("color", DyeColor.byId(dye).getName());
        text.put("has_glowing_text", false);
        KMap<String, Object> properties = new KMap<>();
        properties.put("front_text", text);
        properties.put("back_text", text.copy());
        return properties;
    }

    private static KMap<String, Object> readSpawner(DataInputStream din, ReplayInputStream replay) throws IOException {
        Identifier entityId = null;
        replay.mark(Integer.MAX_VALUE);

        try {
            String keyString = din.readUTF();
            Identifier key = Identifier.tryParse(keyString);
            if (key != null && BuiltInRegistries.ENTITY_TYPE.containsKey(key)) {
                entityId = key;
            } else {
                replay.reset();
            }
        } catch (Throwable ignored) {
            replay.reset();
        }

        if (entityId == null) {
            entityId = legacySpawnerEntityId(din.readShort());
        }
        KMap<String, Object> entity = new KMap<>();
        entity.put("id", entityId.toString());
        KMap<String, Object> spawnData = new KMap<>();
        spawnData.put("entity", entity);
        KMap<String, Object> properties = new KMap<>();
        properties.put("SpawnData", spawnData);
        return properties;
    }

    static Identifier legacySpawnerEntityId(int legacyOrdinal) {
        if (legacyOrdinal < 0 || legacyOrdinal >= LEGACY_BUKKIT_ENTITY_TYPES.size()) {
            return DEFAULT_SPAWNER_ENTITY;
        }
        return LEGACY_BUKKIT_ENTITY_TYPES.get(legacyOrdinal);
    }

    private static List<Identifier> createLegacyBukkitEntityTypes() {
        List<Identifier> entityTypes = new ArrayList<>();
        for (Identifier key : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            if (Identifier.DEFAULT_NAMESPACE.equals(key.getNamespace())) {
                entityTypes.add(key);
            }
        }
        entityTypes.sort(Identifier::compareTo);
        return List.copyOf(entityTypes);
    }

    private KMap<String, Object> readBanner(DataInputStream din, ReplayInputStream replay) throws IOException {
        int baseColor = din.readUnsignedByte();
        int listSize = din.readUnsignedByte();
        replay.mark(Integer.MAX_VALUE);

        List<Object> layers = new ArrayList<>(listSize);
        try {
            for (int i = 0; i < listSize; i++) {
                int color = din.readUnsignedByte();
                Identifier patternKey = Identifier.tryParse(din.readUTF());
                if (patternKey == null || !bannerPatternExists(patternKey)) {
                    throw new IOException("Unknown banner pattern key");
                }
                layers.add(bannerLayer(patternKey, color));
            }
        } catch (Throwable ignored) {
            replay.reset();
            layers.clear();
        }

        if (layers.isEmpty() && listSize > 0) {
            for (int i = 0; i < listSize; i++) {
                int color = din.readUnsignedByte();
                int pattern = din.readUnsignedByte();
                layers.add(bannerLayer(legacyBannerPatternKey(pattern), color));
            }
        }
        KMap<String, Object> properties = new KMap<>();
        properties.put("patterns", layers);
        properties.put(ModdedTileData.LEGACY_BANNER_COLOR_PROPERTY, DyeColor.byId(baseColor).getName());
        return properties;
    }

    private static KMap<String, Object> bannerLayer(Identifier pattern, int color) {
        KMap<String, Object> layer = new KMap<>();
        layer.put("pattern", pattern.toString());
        layer.put("color", DyeColor.byId(color).getName());
        return layer;
    }

    static Identifier legacyBannerPatternKey(int legacyOrdinal) {
        if (legacyOrdinal < 0 || legacyOrdinal >= PAPER_26_2_BANNER_PATTERNS.size()) {
            return DEFAULT_BANNER_PATTERN;
        }
        return PAPER_26_2_BANNER_PATTERNS.get(legacyOrdinal);
    }

    private boolean bannerPatternExists(Identifier key) {
        MinecraftServer instance = server.get();
        if (instance == null) {
            return true;
        }
        Registry<BannerPattern> registry = instance.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
        return registry.containsKey(key);
    }

    private static KMap<String, Object> readLootable(DataInputStream din) throws IOException {
        String lootTable = din.readUTF();
        long seed = din.readLong();
        KMap<String, Object> properties = new KMap<>();
        if (!lootTable.isBlank()) {
            properties.put("LootTable", lootTable);
            properties.put("LootTableSeed", seed);
        }
        return properties;
    }

    private static boolean matchMaterial(String name) {
        String filtered = name.trim().toLowerCase(Locale.ROOT);
        int bracket = filtered.indexOf('[');
        if (bracket >= 0) {
            filtered = filtered.substring(0, bracket);
        }
        if (!filtered.contains(":")) {
            filtered = "minecraft:" + filtered.replaceAll("\\s+", "_");
        }
        Identifier identifier = Identifier.tryParse(filtered);
        if (identifier == null) {
            return false;
        }
        return BuiltInRegistries.BLOCK.containsKey(identifier);
    }
}
