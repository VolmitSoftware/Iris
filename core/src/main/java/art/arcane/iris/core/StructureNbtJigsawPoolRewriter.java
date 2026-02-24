package art.arcane.iris.core;

import art.arcane.volmlib.util.nbt.io.NBTDeserializer;
import art.arcane.volmlib.util.nbt.io.NBTSerializer;
import art.arcane.volmlib.util.nbt.io.NamedTag;
import art.arcane.volmlib.util.nbt.tag.ByteTag;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.IntTag;
import art.arcane.volmlib.util.nbt.tag.ListTag;
import art.arcane.volmlib.util.nbt.tag.NumberTag;
import art.arcane.volmlib.util.nbt.tag.ShortTag;
import art.arcane.volmlib.util.nbt.tag.Tag;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class StructureNbtJigsawPoolRewriter {
    private StructureNbtJigsawPoolRewriter() {
    }

    static byte[] rewrite(byte[] bytes, Map<String, String> remappedKeys) {
        if (bytes == null || bytes.length == 0 || remappedKeys == null || remappedKeys.isEmpty()) {
            return bytes;
        }

        try {
            NbtReadResult readResult = readNamedTagWithCompression(bytes);
            Tag<?> rootTag = readResult.namedTag().getTag();
            if (!(rootTag instanceof CompoundTag compoundTag)) {
                return bytes;
            }

            boolean rewritten = rewriteJigsawPoolReferences(compoundTag, remappedKeys);
            if (!rewritten) {
                return bytes;
            }

            return writeNamedTag(readResult.namedTag(), readResult.compressed());
        } catch (Throwable ignored) {
            return bytes;
        }
    }

    private static boolean rewriteJigsawPoolReferences(CompoundTag root, Map<String, String> remappedKeys) {
        ListTag<?> palette = root.getListTag("palette");
        ListTag<?> blocks = root.getListTag("blocks");
        if (palette == null || blocks == null || palette.size() <= 0 || blocks.size() <= 0) {
            return false;
        }

        Set<Integer> jigsawStates = new HashSet<>();
        for (int paletteIndex = 0; paletteIndex < palette.size(); paletteIndex++) {
            Object paletteRaw = palette.get(paletteIndex);
            if (!(paletteRaw instanceof CompoundTag paletteEntry)) {
                continue;
            }
            String blockName = paletteEntry.getString("Name");
            if ("minecraft:jigsaw".equalsIgnoreCase(blockName)) {
                jigsawStates.add(paletteIndex);
            }
        }

        if (jigsawStates.isEmpty()) {
            return false;
        }

        boolean rewritten = false;
        for (Object blockRaw : blocks.getValue()) {
            if (!(blockRaw instanceof CompoundTag blockTag)) {
                continue;
            }

            Integer stateIndex = tagToInt(blockTag.get("state"));
            if (stateIndex == null || !jigsawStates.contains(stateIndex)) {
                continue;
            }

            CompoundTag blockNbt = blockTag.getCompoundTag("nbt");
            if (blockNbt == null || blockNbt.size() <= 0) {
                continue;
            }

            String poolValue = blockNbt.getString("pool");
            if (poolValue == null || poolValue.isBlank()) {
                continue;
            }

            String normalizedPool = normalizeResourceKey(poolValue);
            if (normalizedPool == null || normalizedPool.isBlank()) {
                continue;
            }

            String remappedPool = remappedKeys.get(normalizedPool);
            if (remappedPool == null || remappedPool.isBlank()) {
                continue;
            }

            blockNbt.putString("pool", remappedPool);
            rewritten = true;
        }

        return rewritten;
    }

    private static Integer tagToInt(Tag<?> tag) {
        if (tag == null) {
            return null;
        }
        if (tag instanceof IntTag intTag) {
            return intTag.asInt();
        }
        if (tag instanceof ShortTag shortTag) {
            return (int) shortTag.asShort();
        }
        if (tag instanceof ByteTag byteTag) {
            return (int) byteTag.asByte();
        }
        if (tag instanceof NumberTag<?> numberTag) {
            Number value = numberTag.getValue();
            if (value != null) {
                return value.intValue();
            }
        }
        Object value = tag.getValue();
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private static String normalizeResourceKey(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.charAt(0) == '#') {
            normalized = normalized.substring(1);
        }

        String namespace = "minecraft";
        String path = normalized;
        int separator = normalized.indexOf(':');
        if (separator >= 0) {
            namespace = normalized.substring(0, separator).trim().toLowerCase();
            path = normalized.substring(separator + 1).trim();
        }

        if (path.startsWith("worldgen/template_pool/")) {
            path = path.substring("worldgen/template_pool/".length());
        }
        path = path.replace('\\', '/');
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            return "";
        }

        return namespace + ":" + path;
    }

    private static NbtReadResult readNamedTagWithCompression(byte[] bytes) throws IOException {
        IOException primary = null;
        try {
            NamedTag uncompressed = new NBTDeserializer(false).fromStream(new ByteArrayInputStream(bytes));
            return new NbtReadResult(uncompressed, false);
        } catch (IOException e) {
            primary = e;
        }

        try {
            NamedTag compressed = new NBTDeserializer(true).fromStream(new ByteArrayInputStream(bytes));
            return new NbtReadResult(compressed, true);
        } catch (IOException e) {
            if (primary != null) {
                e.addSuppressed(primary);
            }
            throw e;
        }
    }

    private static byte[] writeNamedTag(NamedTag namedTag, boolean compressed) throws IOException {
        return new NBTSerializer(compressed).toBytes(namedTag);
    }

    private record NbtReadResult(NamedTag namedTag, boolean compressed) {
    }
}
