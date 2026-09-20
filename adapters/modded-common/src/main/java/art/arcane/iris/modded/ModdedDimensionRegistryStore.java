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

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;

import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ModdedDimensionRegistryStore {
    private static final String FILE_NAME = "iris-dimensions.json";
    private static final Pattern ID_FIELD = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");

    private ModdedDimensionRegistryStore() {
    }

    public static List<PersistentDimension> load(NativeModdedServer server) {
        return load(storeFile(server));
    }

    static List<PersistentDimension> load(Path file) {
        return contents(file).dimensions();
    }

    static List<PersistentDimension> loadWorldRoot(Path worldRoot) {
        return load(storeFile(worldRoot));
    }

    /**
     * Boot-safe load: a corrupt registry must not abort server start. The broken file is renamed aside so the
     * next write starts clean, and every id we can still recognise in the raw text is reported as lost.
     */
    public static List<PersistentDimension> loadForStartup(NativeModdedServer server) {
        return loadForStartup(storeFile(server));
    }

    static List<PersistentDimension> loadForStartup(Path file) {
        try {
            return load(file);
        } catch (RuntimeException corrupt) {
            ModdedIrisLog.error("Iris persistent dimension registry at {} is corrupt; quarantining it and continuing boot",
                    file, corrupt);
            List<String> lostIds = salvageIds(file);
            if (lostIds.isEmpty()) {
                ModdedIrisLog.error("Iris could not recover any dimension ids from the corrupt registry; re-create the worlds with /iris world create");
            } else {
                ModdedIrisLog.error("Iris lost {} persistent dimension(s) from the corrupt registry: {}",
                        lostIds.size(), String.join(", ", lostIds));
            }
            quarantine(file);
            return List.of();
        }
    }

    private static List<String> salvageIds(Path file) {
        List<String> ids = new ArrayList<>();
        try {
            Matcher matcher = ID_FIELD.matcher(Files.readString(file, StandardCharsets.UTF_8));
            while (matcher.find()) {
                String id = matcher.group(1);
                if (!ids.contains(id)) {
                    ids.add(id);
                }
            }
        } catch (IOException | RuntimeException unreadable) {
            ModdedIrisLog.warn("Iris could not scan the corrupt persistent dimension registry at {} for lost ids", file, unreadable);
        }
        return ids;
    }

    private static void quarantine(Path file) {
        Path broken = file.resolveSibling(FILE_NAME + ".broken-" + System.currentTimeMillis());
        try {
            Files.move(file, broken, StandardCopyOption.REPLACE_EXISTING);
            ModdedIrisLog.error("Iris moved the corrupt persistent dimension registry to {}", broken);
        } catch (IOException failure) {
            ModdedIrisLog.error("Iris could not quarantine the corrupt persistent dimension registry at {}; delete it by hand",
                    file, failure);
        }
    }

    private static Contents contents(Path file) {
        if (!Files.isRegularFile(file)) {
            return new Contents(new ArrayList<>(), new ArrayList<>());
        }
        try {
            JSONObject root = new JSONObject(Files.readString(file, StandardCharsets.UTF_8));
            JSONArray entries = root.optJSONArray("dimensions");
            if (entries == null) {
                throw new IllegalArgumentException("registry root has no dimensions array");
            }
            Map<String, PersistentDimension> deduplicated = new LinkedHashMap<>();
            List<Object> unparsed = new ArrayList<>();
            for (int index = 0; index < entries.length(); index++) {
                Object raw = entries.opt(index);
                try {
                    JSONObject entry = entries.getJSONObject(index);
                    String id = required(entry, "id", index, file);
                    String pack = required(entry, "pack", index, file);
                    String dimension = required(entry, "dimension", index, file);
                    if (!entry.has("seed")) {
                        throw new IllegalArgumentException("missing seed");
                    }
                    PersistentDimension previous = deduplicated.putIfAbsent(
                            id, new PersistentDimension(id, pack, dimension, entry.getLong("seed")));
                    if (previous != null) {
                        ModdedIrisLog.warn("Iris persistent dimension registry entry {} in {} duplicates id '{}'; keeping the first",
                                index, file, id);
                    }
                } catch (RuntimeException invalidEntry) {
                    if (raw != null) {
                        unparsed.add(raw);
                    }
                    ModdedIrisLog.warn("Iris persistent dimension registry entry {} in {} is invalid ({}); kept verbatim: {}",
                            index, file, invalidEntry.getMessage(), raw);
                }
            }
            return new Contents(new ArrayList<>(deduplicated.values()), unparsed);
        } catch (RuntimeException | IOException e) {
            throw new IllegalStateException("Iris persistent dimension registry at " + file
                    + " could not be read; refusing to discard persistent worlds", e);
        }
    }

    public static PersistentDimension get(NativeModdedServer server, String id) {
        return index(load(server)).get(id);
    }

    public static synchronized void put(NativeModdedServer server, PersistentDimension dimension) {
        Path file = storeFile(server);
        Contents contents = contents(file);
        Map<String, PersistentDimension> current = index(contents.dimensions());
        current.put(dimension.id(), dimension);
        write(file, new ArrayList<>(current.values()), contents.unparsed());
    }

    public static synchronized void remove(NativeModdedServer server, String id) {
        Path file = storeFile(server);
        Contents contents = contents(file);
        Map<String, PersistentDimension> current = index(contents.dimensions());
        if (current.remove(id) != null) {
            write(file, new ArrayList<>(current.values()), contents.unparsed());
        }
    }

    private static Map<String, PersistentDimension> index(List<PersistentDimension> dimensions) {
        Map<String, PersistentDimension> map = new LinkedHashMap<>();
        for (PersistentDimension dimension : dimensions) {
            map.put(dimension.id(), dimension);
        }
        return map;
    }

    private static String required(JSONObject entry, String key, int index, Path file) {
        String value = entry.optString(key, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("entry " + index + " in " + file + " has no " + key);
        }
        return value;
    }

    static void write(Path file, List<PersistentDimension> dimensions) {
        write(file, dimensions, List.of());
    }

    private static void write(Path file, List<PersistentDimension> dimensions, List<Object> unparsed) {
        JSONArray entries = new JSONArray();
        for (PersistentDimension dimension : dimensions) {
            JSONObject entry = new JSONObject();
            entry.put("id", dimension.id());
            entry.put("pack", dimension.pack());
            entry.put("dimension", dimension.dimension());
            entry.put("seed", dimension.seed());
            entries.put(entry);
        }
        for (Object entry : unparsed) {
            entries.put(entry);
        }
        JSONObject root = new JSONObject();
        root.put("dimensions", entries);
        Path temp = file.resolveSibling(FILE_NAME + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(temp, root.toString(2), StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            moveReplacing(temp, file);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw new IllegalStateException("Iris failed to write persistent dimension registry at " + file, e);
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path storeFile(NativeModdedServer server) {
        return storeFile(server.root());
    }

    private static Path storeFile(Path worldRoot) {
        return worldRoot.resolve("iris").resolve(FILE_NAME);
    }

    public record PersistentDimension(String id, String pack, String dimension, long seed) {
    }

    private record Contents(List<PersistentDimension> dimensions, List<Object> unparsed) {
    }
}
