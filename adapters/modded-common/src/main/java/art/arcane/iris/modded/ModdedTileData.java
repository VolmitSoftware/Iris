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
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.data.UnresolvedKeyLog;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.TagValueInput;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ModdedTileData extends TileData {
    public static final String NBT_PROPERTY = "nbt";
    static final String LEGACY_BANNER_COLOR_PROPERTY = "iris:legacy_banner_color";
    private static final int MAX_TAG_DEPTH = 64;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setStrictness(Strictness.LENIENT).create();
    private static final UnresolvedKeyLog SNBT_FALLBACK = new UnresolvedKeyLog("Iris tile capture SNBT fallback", 30_000L);

    private final byte[] raw;
    private final KMap<String, Object> tileProperties;
    private final String expectedBlockKey;
    private final int legacyType;
    private int hash;

    ModdedTileData(byte[] raw, KMap<String, Object> tileProperties, String expectedBlockKey, int legacyType) {
        super();
        this.raw = raw;
        this.tileProperties = tileProperties == null ? new KMap<>() : tileProperties;
        this.expectedBlockKey = expectedBlockKey;
        this.legacyType = legacyType;
    }

    public static ModdedTileData capture(String blockKey, String snbt) throws IOException {
        KMap<String, Object> properties = captureProperties(blockKey, snbt);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(blockKey);
            out.writeUTF(GSON.toJson(properties));
        }
        return new ModdedTileData(bytes.toByteArray(), properties, normalizeBlockKey(blockKey), -1);
    }

    /**
     * Converts a captured tile to the generic map form the Bukkit side reads and writes, so an object captured on a
     * mod loader still pastes on Bukkit, and keeps the original SNBT under {@value #NBT_PROPERTY} alongside it.
     * <p>
     * Both forms are stored because the map form is lossy: {@link #fromTag(Tag, int, int)} collapses ByteArray,
     * IntArray and LongArray tags to a plain List, and pasting that back produces a ListTag, which Minecraft rejects
     * where it expects an array - a player head's {@code profile.id} (IntArray of 4) is the common case. Modded paste
     * reads the SNBT first ({@link #payload()}), so a modded capture pastes byte-identical on a mod loader; Bukkit
     * still reads the map form and accepts the array-shaped members degrading to lists, as it did before.
     * <p>
     * The SNBT is dropped when the captured tag itself has a root member named {@code nbt}, since that member owns the
     * map key; such a tile keeps the pre-existing lossy behaviour on both platforms. No vanilla block entity has one.
     */
    @SuppressWarnings("unchecked")
    private static KMap<String, Object> captureProperties(String blockKey, String snbt) {
        KMap<String, Object> properties = new KMap<>();
        if (snbt != null && !snbt.isBlank()) {
            try {
                Object converted = fromTag(NbtUtils.snbtToStructure(snbt), 0, MAX_TAG_DEPTH);
                if (converted instanceof KMap<?, ?> map) {
                    properties.putAll((KMap<String, Object>) map);
                }
            } catch (Throwable e) {
                if (SNBT_FALLBACK.firstOccurrence(blockKey == null ? "<null>" : blockKey)) {
                    IrisLogging.warn("Tile capture for '" + blockKey + "' kept SNBT form: " + e.getMessage());
                }
                String summary = SNBT_FALLBACK.pollSummary();
                if (summary != null) {
                    IrisLogging.warn(summary);
                }
            }
        }
        if (snbt != null && !properties.containsKey(NBT_PROPERTY)) {
            properties.put(NBT_PROPERTY, snbt);
        }
        return properties;
    }

    public static ModdedTileData fromProperties(PlatformBlockState state, KMap<String, Object> properties) {
        String blockKey = state.placementBaseState().key();
        int bracket = blockKey.indexOf('[');
        if (bracket >= 0) {
            blockKey = blockKey.substring(0, bracket);
        }
        KMap<String, Object> copied = properties == null ? new KMap<>() : properties.copy();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeUTF(blockKey);
                out.writeUTF(GSON.toJson(copied));
            }
            return new ModdedTileData(bytes.toByteArray(), copied, normalizeBlockKey(blockKey), -1);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode modded tile data for " + blockKey, e);
        }
    }

    public String snbt() {
        Object value = tileProperties.get(NBT_PROPERTY);
        return value == null ? null : value.toString();
    }

    public boolean apply(BlockEntity blockEntity, ServerLevel level) throws Exception {
        CompoundTag payload = payload();
        if (payload == null) {
            return false;
        }
        CompoundTag merged = blockEntity.saveWithoutMetadata(level.registryAccess());
        merged.merge(payload);
        blockEntity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), merged));
        blockEntity.setChanged();
        level.sendBlockUpdated(blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity.getBlockState(), Block.UPDATE_CLIENTS);
        return true;
    }

    /**
     * The platform-neutral block key. The superclass reads its own {@code material} field, which a modded record
     * never populates (it carries the key as {@link #expectedBlockKey} instead), so it must be answered here.
     * Null for a legacy record, which identifies its target by block-entity type rather than by key - see
     * {@link #isApplicable(BlockState, BlockEntity)}.
     */
    @Override
    public String getMaterialKey() {
        return expectedBlockKey;
    }

    public boolean isApplicable(BlockState state, BlockEntity blockEntity) {
        if (expectedBlockKey != null) {
            return expectedBlockKey.equals(normalizeBlockKey(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()));
        }
        return switch (legacyType) {
            case 0 -> blockEntity instanceof SignBlockEntity;
            case 1 -> blockEntity instanceof SpawnerBlockEntity;
            case 2 -> blockEntity instanceof BannerBlockEntity;
            case 3 -> blockEntity instanceof RandomizableContainerBlockEntity;
            default -> false;
        };
    }

    CompoundTag payload() throws Exception {
        String snbt = snbt();
        if (snbt != null && !snbt.isBlank()) {
            return NbtUtils.snbtToStructure(snbt);
        }
        Tag converted = toTag(tileProperties);
        return converted instanceof CompoundTag compound ? compound : null;
    }

    public BlockState adjustBlockState(BlockState state) {
        Object colorValue = tileProperties.get(LEGACY_BANNER_COLOR_PROPERTY);
        if (colorValue == null) {
            return state;
        }
        Identifier currentId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String currentPath = currentId.getPath();
        if (!currentPath.endsWith("_banner")) {
            return state;
        }
        String suffix = currentPath.endsWith("_wall_banner") ? "_wall_banner" : "_banner";
        Identifier targetId = Identifier.tryParse("minecraft:" + colorValue + suffix);
        if (targetId == null || !BuiltInRegistries.BLOCK.containsKey(targetId)) {
            return state;
        }
        BlockState adjusted = BuiltInRegistries.BLOCK.getValue(targetId).defaultBlockState();
        for (Property<?> property : state.getProperties()) {
            if (adjusted.hasProperty(property)) {
                adjusted = copyProperty(adjusted, state, property);
            }
        }
        return adjusted;
    }

    private static Tag toTag(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            CompoundTag compound = new CompoundTag();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (LEGACY_BANNER_COLOR_PROPERTY.equals(String.valueOf(entry.getKey()))) {
                    continue;
                }
                Tag child = toTag(entry.getValue());
                if (child != null) {
                    compound.put(String.valueOf(entry.getKey()), child);
                }
            }
            return compound;
        }
        if (value instanceof List<?> values) {
            ListTag list = new ListTag();
            for (Object entry : values) {
                Tag child = toTag(entry);
                if (child != null) {
                    list.add(child);
                }
            }
            return list;
        }
        if (value instanceof Boolean bool) {
            return ByteTag.valueOf(bool);
        }
        if (value instanceof Byte number) {
            return ByteTag.valueOf(number);
        }
        if (value instanceof Short number) {
            return ShortTag.valueOf(number);
        }
        if (value instanceof Integer number) {
            return IntTag.valueOf(number);
        }
        if (value instanceof Long number) {
            return LongTag.valueOf(number);
        }
        if (value instanceof Float number) {
            return FloatTag.valueOf(number);
        }
        if (value instanceof Number number) {
            return DoubleTag.valueOf(number.doubleValue());
        }
        return StringTag.valueOf(String.valueOf(value));
    }

    /**
     * Inverse of {@link #toTag(Object)}, matching the Bukkit NMS tile converter value-for-value so both platforms
     * produce the same generic map for the same block entity.
     */
    private static Object fromTag(Tag tag, int depth, int maxDepth) {
        if (tag == null || depth > maxDepth) {
            return null;
        }
        if (tag instanceof CompoundTag compound) {
            KMap<String, Object> map = new KMap<>();
            for (String key : compound.keySet()) {
                Tag child = compound.get(key);
                if (child == null) {
                    continue;
                }
                Object value = fromTag(child, depth + 1, maxDepth);
                if (value != null) {
                    map.put(key, value);
                }
            }
            return map;
        }
        if (tag instanceof CollectionTag collection) {
            List<Object> values = new ArrayList<>();
            for (Object entry : collection) {
                if (entry instanceof Tag child) {
                    Object value = fromTag(child, depth + 1, maxDepth);
                    if (value != null) {
                        values.add(value);
                    }
                } else if (entry != null) {
                    values.add(entry);
                }
            }
            return values;
        }
        if (tag instanceof NumericTag numeric) {
            return numeric.box();
        }
        return tag.asString().orElse(null);
    }

    private static <T extends Comparable<T>> BlockState copyProperty(BlockState target, BlockState source, Property<T> property) {
        return target.setValue(property, source.getValue(property));
    }

    private static Object deepCopy(Object value) {
        if (value instanceof Map<?, ?> map) {
            KMap<String, Object> copy = new KMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), deepCopy(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> values) {
            List<Object> copy = new ArrayList<>(values.size());
            for (Object entry : values) {
                copy.add(deepCopy(entry));
            }
            return copy;
        }
        return value;
    }

    static String normalizeBlockKey(String blockKey) {
        if (blockKey == null || blockKey.isBlank()) {
            return null;
        }
        String normalized = blockKey.trim().toLowerCase(java.util.Locale.ROOT);
        int bracket = normalized.indexOf('[');
        if (bracket >= 0) {
            normalized = normalized.substring(0, bracket);
        }
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized.replaceAll("\\s+", "_");
        }
        Identifier identifier = Identifier.tryParse(normalized);
        return identifier == null ? null : identifier.toString();
    }

    @Override
    public KMap<String, Object> getProperties() {
        return tileProperties;
    }

    @Override
    public void toBinary(DataOutputStream out) throws IOException {
        out.write(raw);
    }

    /**
     * Identity over this record's own state. The superclass generates equals/hashCode from its
     * {@code material} and {@code properties} fields, both of which stay null on a modded record, which would make
     * every modded tile equal with a constant hash. Mantle tile sections are palette backed
     * (16x16x16 = 4096 entries, so PaletteOrHunk picks a value-keyed DataContainer) and resolve palette ids through
     * equals, so a collapsed identity writes the first tile's NBT into every other tile in the section.
     * <p>
     * The serialized {@link #raw} form is the complete identity: it carries the block key and the property JSON for a
     * modern record and the consumed bytes for a legacy one. Two logically identical tiles still share one palette
     * entry, which is the intended dedup.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ModdedTileData other)) {
            return false;
        }
        return legacyType == other.legacyType
                && Objects.equals(expectedBlockKey, other.expectedBlockKey)
                && Arrays.equals(raw, other.raw);
    }

    @Override
    public int hashCode() {
        int cached = hash;
        if (cached != 0) {
            return cached;
        }
        int computed = 31 * (31 * Arrays.hashCode(raw) + Objects.hashCode(expectedBlockKey)) + legacyType;
        if (computed == 0) {
            computed = 1;
        }
        hash = computed;
        return computed;
    }

    @Override
    public String toString() {
        return (expectedBlockKey == null ? "legacy:" + legacyType : expectedBlockKey) + GSON.toJson(tileProperties);
    }

    @SuppressWarnings("unchecked")
    @Override
    public TileData clone() {
        return new ModdedTileData(raw == null ? null : raw.clone(),
                (KMap<String, Object>) deepCopy(tileProperties), expectedBlockKey, legacyType);
    }
}
