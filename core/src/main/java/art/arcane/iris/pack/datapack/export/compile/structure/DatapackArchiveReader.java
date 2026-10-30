package art.arcane.iris.pack.datapack.export.compile.structure;

import art.arcane.iris.pack.datapack.DataVersion;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class DatapackArchiveReader {
    private static final String METADATA = "pack.mcmeta";
    private static final String DATA = "data/";
    private static final int MAX_ENTRIES = 100_000;
    private static final long MAX_ENTRY_BYTES = 256L * 1024L * 1024L;
    private static final long MAX_TOTAL_BYTES = 1024L * 1024L * 1024L;

    private DatapackArchiveReader() {
    }

    static Pack read(Path archive, DataVersion target) throws IOException {
        SortedMap<String, byte[]> entries = entries(archive);
        String root = root(entries, archive);
        JsonObject metadata = metadata(entries.get(root + METADATA), archive);
        JsonElement pack = metadata.get("pack");
        if (pack == null || !pack.isJsonObject()) {
            throw new IOException("Datapack archive " + archive.getFileName() + " has no pack section in pack.mcmeta");
        }
        SortedMap<String, byte[]> data = new TreeMap<>();
        copy(entries, root, data);
        List<String> overlays = new ArrayList<>();
        for (Overlay overlay : overlays(metadata)) {
            if (overlay.formats().contains(target)) {
                copy(entries, root + overlay.directory() + "/", data);
                overlays.add(overlay.directory());
            }
        }
        return new Pack(data, PackFormatRange.of(pack.getAsJsonObject(), "supported_formats"), List.copyOf(overlays));
    }

    private static SortedMap<String, byte[]> entries(Path archive) throws IOException {
        SortedMap<String, byte[]> entries = new TreeMap<>();
        long total = 0L;
        int count = 0;
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            Enumeration<? extends ZipEntry> enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                if (++count > MAX_ENTRIES) {
                    throw new IOException("Datapack archive " + archive.getFileName() + " has more than " + MAX_ENTRIES + " entries");
                }
                String name = entry.getName().replace('\\', '/');
                if (entry.isDirectory() || !safeName(name)) {
                    continue;
                }
                try (InputStream input = zip.getInputStream(entry)) {
                    byte[] bytes = bounded(input, archive, name);
                    total += bytes.length;
                    if (total > MAX_TOTAL_BYTES) {
                        throw new IOException("Datapack archive " + archive.getFileName() + " expands beyond " + MAX_TOTAL_BYTES + " bytes");
                    }
                    entries.put(name, bytes);
                }
            }
        }
        return entries;
    }

    private static byte[] bounded(InputStream input, Path archive, String name) throws IOException {
        byte[] bytes = input.readNBytes((int) Math.min(Integer.MAX_VALUE - 8L, MAX_ENTRY_BYTES + 1L));
        if (bytes.length > MAX_ENTRY_BYTES) {
            throw new IOException("Datapack archive " + archive.getFileName() + " entry '" + name + "' exceeds " + MAX_ENTRY_BYTES + " bytes");
        }
        return bytes;
    }

    private static boolean safeName(String name) {
        if (name.isEmpty() || name.startsWith("/")) {
            return false;
        }
        for (String segment : name.split("/")) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    private static String root(SortedMap<String, byte[]> entries, Path archive) throws IOException {
        if (entries.containsKey(METADATA)) {
            return "";
        }
        String wrapper = null;
        for (String name : entries.keySet()) {
            int slash = name.indexOf('/');
            String top = slash < 0 ? "" : name.substring(0, slash + 1);
            if (top.isEmpty() || (wrapper != null && !wrapper.equals(top))) {
                wrapper = null;
                break;
            }
            wrapper = top;
        }
        if (wrapper == null || !entries.containsKey(wrapper + METADATA)) {
            throw new IOException("Datapack archive " + archive.getFileName() + " has no pack.mcmeta at its root");
        }
        return wrapper;
    }

    private static JsonObject metadata(byte[] bytes, Path archive) throws IOException {
        try {
            JsonElement parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                throw new IOException("Datapack archive " + archive.getFileName() + " has a pack.mcmeta that is not a JSON object");
            }
            return parsed.getAsJsonObject();
        } catch (JsonParseException exception) {
            throw new IOException("Datapack archive " + archive.getFileName() + " has an unreadable pack.mcmeta", exception);
        }
    }

    private static void copy(SortedMap<String, byte[]> entries, String prefix, SortedMap<String, byte[]> data) {
        String from = prefix + DATA;
        for (Map.Entry<String, byte[]> entry : entries.tailMap(from).entrySet()) {
            if (!entry.getKey().startsWith(from)) {
                break;
            }
            data.put(DATA + entry.getKey().substring(from.length()), entry.getValue());
        }
    }

    private static List<Overlay> overlays(JsonObject metadata) {
        JsonElement section = metadata.get("overlays");
        if (section == null || !section.isJsonObject() || !section.getAsJsonObject().has("entries")
                || !section.getAsJsonObject().get("entries").isJsonArray()) {
            return List.of();
        }
        List<Overlay> overlays = new ArrayList<>();
        for (JsonElement element : section.getAsJsonObject().getAsJsonArray("entries")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            JsonElement directory = entry.get("directory");
            Optional<PackFormatRange> formats = PackFormatRange.of(entry, "formats");
            if (directory != null && directory.isJsonPrimitive() && safeName(directory.getAsString()) && formats.isPresent()) {
                overlays.add(new Overlay(directory.getAsString(), formats.get()));
            }
        }
        return overlays;
    }

    record Pack(SortedMap<String, byte[]> data, Optional<PackFormatRange> formats, List<String> overlays) {
    }

    private record Overlay(String directory, PackFormatRange formats) {
    }
}
