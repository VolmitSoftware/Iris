package art.arcane.iris.pack.datapack.export.emit;

import art.arcane.iris.pack.datapack.export.ContentTrim;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.volmlib.util.nbt.io.BoundedNBTReader;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.ListTag;
import art.arcane.volmlib.util.nbt.tag.StringTag;
import art.arcane.volmlib.util.nbt.tag.Tag;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

public final class UnusedResourcePruner {
    private static final String DATA = "data/";
    private static final String VANILLA_NAMESPACE = "minecraft";
    private static final String JSON = ".json";
    private static final String MCMETA = ".mcmeta";
    private static final String NBT = ".nbt";
    private static final String PNG = ".png";
    private static final String TAG_PREFIX = "#";
    private static final String WORLDGEN = "worldgen/";
    private static final String WORLDGEN_TAGS = "tags/worldgen/";
    private static final String TAGS = "tags/";
    private static final String TEMPLATES = "structure";
    private static final String LOOT_TABLES = "loot_table";
    private static final String BLOCK_TAGS = "tags/block";
    private static final String STRUCTURE_SETS = "worldgen/structure_set";
    private static final String STRUCTURES = "worldgen/structure";
    private static final String BIOME_TAGS = "tags/worldgen/biome";
    private static final Set<String> WORLDGEN_REGISTRIES = Set.of(
            "biome",
            "carver",
            "configured_carver",
            "feature",
            "configured_feature",
            "placed_feature",
            "structure",
            "structure_set",
            "processor_list",
            "template_pool",
            "noise_settings",
            "noise",
            "density_function",
            "material_rule",
            "material_condition",
            "world_preset",
            "flat_level_generator_preset",
            "multi_noise_biome_source_parameter_list",
            "block_state_provider");
    private static final Set<String> ROOT_FOLDERS = Set.of("worldgen/world_preset", "tags/worldgen/world_preset");
    private static final Pattern REFERENCE = Pattern.compile("#?[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final BoundedNBTReader.Limits TEMPLATE_LIMITS = new BoundedNBTReader.Limits(1L << 30, 512, 1 << 26, Integer.MAX_VALUE, 65_535);
    private static final int GZIP_MAGIC_FIRST = 0x1f;
    private static final int GZIP_MAGIC_SECOND = 0x8b;
    private static final int INFLATE_BUFFER = 8192;

    private UnusedResourcePruner() {
    }

    public static Pruned prune(SortedMap<String, byte[]> files) {
        return new Reachability(Objects.requireNonNull(files)).prune();
    }

    private static Resource classify(String path) {
        if (!path.startsWith(DATA)) {
            return null;
        }
        int separator = path.indexOf('/', DATA.length());
        if (separator < 0) {
            return null;
        }
        String namespace = path.substring(DATA.length(), separator);
        String rest = path.substring(separator + 1);
        String folder = folder(rest);
        if (folder == null) {
            return null;
        }
        String extension = folder.equals(TEMPLATES) ? NBT : JSON;
        String id = namespace + ":" + rest.substring(folder.length() + 1, rest.length() - extension.length());
        if (!IDENTIFIER.matcher(id).matches()) {
            return null;
        }
        return new Resource(path, namespace, folder, id, folder.startsWith(TAGS));
    }

    private static String folder(String rest) {
        if (rest.startsWith(TEMPLATES + "/")) {
            return rest.endsWith(NBT) ? TEMPLATES : null;
        }
        if (!rest.endsWith(JSON)) {
            return null;
        }
        if (rest.startsWith(LOOT_TABLES + "/")) {
            return LOOT_TABLES;
        }
        if (rest.startsWith(BLOCK_TAGS + "/")) {
            return BLOCK_TAGS;
        }
        String prefix = rest.startsWith(WORLDGEN_TAGS) ? WORLDGEN_TAGS : rest.startsWith(WORLDGEN) ? WORLDGEN : null;
        if (prefix == null) {
            return null;
        }
        int separator = rest.indexOf('/', prefix.length());
        if (separator < 0 || !WORLDGEN_REGISTRIES.contains(rest.substring(prefix.length(), separator))) {
            return null;
        }
        return rest.substring(0, separator);
    }

    private static String normalize(String id) {
        return id.indexOf(':') < 0 ? VANILLA_NAMESPACE + ":" + id : id;
    }

    private static boolean string(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString();
    }

    private static JsonElement member(JsonElement element, String key) {
        return element.isJsonObject() ? element.getAsJsonObject().get(key) : null;
    }

    private static JsonElement parse(byte[] bytes) {
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
    }

    private static Tag<?> readNbt(byte[] bytes) throws IOException {
        boolean gzip = bytes.length > 1 && (bytes[0] & 0xff) == GZIP_MAGIC_FIRST && (bytes[1] & 0xff) == GZIP_MAGIC_SECOND;
        return BoundedNBTReader.read(new ByteArrayInputStream(gzip ? inflate(bytes) : bytes), TEMPLATE_LIMITS).getTag();
    }

    private static byte[] inflate(byte[] bytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(TEMPLATE_LIMITS.maxBytes(), bytes.length * 4L));
        byte[] buffer = new byte[INFLATE_BUFFER];
        try (InputStream input = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
                if (output.size() > TEMPLATE_LIMITS.maxBytes()) {
                    throw new IOException("Template inflates beyond " + TEMPLATE_LIMITS.maxBytes() + " bytes");
                }
            }
        }
        return output.toByteArray();
    }

    private static long bytes(Map<String, byte[]> files) {
        long total = 0L;
        for (byte[] content : files.values()) {
            total += content.length;
        }
        return total;
    }

    public record Pruned(SortedMap<String, byte[]> files, ContentTrim trim, List<ExportDiagnostic> diagnostics) {
        public Pruned {
            files = Collections.unmodifiableSortedMap(new TreeMap<>(files));
            Objects.requireNonNull(trim);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private record Resource(String path, String namespace, String folder, String id, boolean tag) {
    }

    private static final class Reachability {
        private final SortedMap<String, byte[]> files;
        private final Map<String, Resource> trimmed = new HashMap<>();
        private final Map<String, List<Resource>> byId = new HashMap<>();
        private final Map<String, List<Resource>> tagsById = new HashMap<>();
        private final Set<String> owned = new HashSet<>();
        private final Set<String> reached = new HashSet<>();
        private final ArrayDeque<String> pending = new ArrayDeque<>();
        private final SortedMap<String, String> unreadable = new TreeMap<>();

        private Reachability(SortedMap<String, byte[]> files) {
            this.files = files;
        }

        private Pruned prune() {
            index();
            for (String path : files.keySet()) {
                Resource resource = trimmed.get(path);
                if (resource == null || root(resource)) {
                    reach(path);
                }
            }
            while (!pending.isEmpty()) {
                scan(pending.poll());
            }
            SortedMap<String, byte[]> kept = new TreeMap<>();
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                if (reached.contains(file.getKey())) {
                    kept.put(file.getKey(), file.getValue());
                }
            }
            ContentTrim trim = new ContentTrim(files.size(), bytes(files), kept.size(), bytes(kept));
            return new Pruned(kept, trim, diagnostics(trim));
        }

        private void index() {
            for (String path : files.keySet()) {
                Resource resource = classify(path);
                if (resource == null) {
                    continue;
                }
                trimmed.put(path, resource);
                byId.computeIfAbsent(resource.id(), ignored -> new ArrayList<>(1)).add(resource);
                if (resource.tag()) {
                    tagsById.computeIfAbsent(resource.id(), ignored -> new ArrayList<>(1)).add(resource);
                } else {
                    owned.add(resource.namespace());
                }
            }
        }

        private boolean root(Resource resource) {
            if (external(resource) || ROOT_FOLDERS.contains(resource.folder())) {
                return true;
            }
            return resource.folder().equals(STRUCTURE_SETS) && active(resource);
        }

        private boolean external(Resource resource) {
            return resource.namespace().equals(VANILLA_NAMESPACE) || !owned.contains(resource.namespace());
        }

        private boolean active(Resource set) {
            JsonElement structures;
            try {
                structures = member(parse(files.get(set.path())), "structures");
            } catch (JsonParseException exception) {
                return true;
            }
            if (structures == null || !structures.isJsonArray()) {
                return true;
            }
            for (JsonElement entry : structures.getAsJsonArray()) {
                JsonElement structure = member(entry, "structure");
                if (!string(structure) || enabled(structure.getAsString())) {
                    return true;
                }
            }
            return false;
        }

        private boolean enabled(String structureId) {
            Resource structure = find(byId, normalize(structureId), STRUCTURES);
            if (structure == null) {
                return true;
            }
            JsonElement biomes;
            try {
                biomes = member(parse(files.get(structure.path())), "biomes");
            } catch (JsonParseException exception) {
                return true;
            }
            if (biomes == null) {
                return true;
            }
            if (biomes.isJsonArray()) {
                return !biomes.getAsJsonArray().isEmpty();
            }
            if (!string(biomes) || !biomes.getAsString().startsWith(TAG_PREFIX)) {
                return true;
            }
            return !emptyTag(biomes.getAsString().substring(TAG_PREFIX.length()), new HashSet<>());
        }

        private boolean emptyTag(String tagId, Set<String> visiting) {
            String id = normalize(tagId);
            if (!visiting.add(id)) {
                return true;
            }
            Resource tag = find(tagsById, id, BIOME_TAGS);
            if (tag == null || external(tag)) {
                return false;
            }
            JsonElement values;
            try {
                values = member(parse(files.get(tag.path())), "values");
            } catch (JsonParseException exception) {
                return false;
            }
            if (values == null || !values.isJsonArray()) {
                return false;
            }
            for (JsonElement value : values.getAsJsonArray()) {
                JsonElement entry = value.isJsonObject() ? member(value, "id") : value;
                if (!string(entry) || !entry.getAsString().startsWith(TAG_PREFIX)
                        || !emptyTag(entry.getAsString().substring(TAG_PREFIX.length()), visiting)) {
                    return false;
                }
            }
            return true;
        }

        private Resource find(Map<String, List<Resource>> index, String id, String folder) {
            for (Resource resource : index.getOrDefault(id, List.of())) {
                if (resource.folder().equals(folder)) {
                    return resource;
                }
            }
            return null;
        }

        private void reach(String path) {
            if (reached.add(path)) {
                pending.add(path);
            }
        }

        private void scan(String path) {
            if (path.endsWith(PNG)) {
                return;
            }
            byte[] bytes = files.get(path);
            if (path.endsWith(NBT)) {
                scanNbt(path, bytes);
            } else if (path.endsWith(JSON) || path.endsWith(MCMETA)) {
                scanJson(path, bytes);
            } else {
                scanText(new String(bytes, StandardCharsets.ISO_8859_1));
            }
        }

        private void scanJson(String path, byte[] bytes) {
            JsonElement json;
            try {
                json = parse(bytes);
            } catch (JsonParseException exception) {
                unreadable.put(path, "JSON (" + exception.getMessage() + ")");
                scanText(new String(bytes, StandardCharsets.UTF_8));
                return;
            }
            scanElement(json);
        }

        private void scanElement(JsonElement element) {
            if (element.isJsonObject()) {
                for (Map.Entry<String, JsonElement> member : element.getAsJsonObject().entrySet()) {
                    scanText(member.getKey());
                    scanElement(member.getValue());
                }
                return;
            }
            if (element.isJsonArray()) {
                for (JsonElement item : element.getAsJsonArray()) {
                    scanElement(item);
                }
                return;
            }
            if (string(element)) {
                scanText(element.getAsString());
            }
        }

        private void scanNbt(String path, byte[] bytes) {
            Tag<?> root;
            try {
                root = readNbt(bytes);
            } catch (IOException exception) {
                unreadable.put(path, "NBT (" + exception.getMessage() + ")");
                scanText(new String(bytes, StandardCharsets.ISO_8859_1));
                return;
            }
            scanTag(root);
        }

        private void scanTag(Tag<?> tag) {
            if (tag instanceof StringTag text) {
                scanText(text.getValue());
                return;
            }
            if (tag instanceof CompoundTag compound) {
                for (Tag<?> value : compound.values()) {
                    scanTag(value);
                }
                return;
            }
            if (tag instanceof ListTag<?> list) {
                for (Tag<?> item : list) {
                    scanTag(item);
                }
            }
        }

        private void scanText(String text) {
            if (text.indexOf(':') < 0) {
                return;
            }
            Matcher matcher = REFERENCE.matcher(text);
            while (matcher.find()) {
                String token = matcher.group();
                List<Resource> targets = token.startsWith(TAG_PREFIX) ? tagsById.get(token.substring(TAG_PREFIX.length())) : byId.get(token);
                if (targets == null) {
                    continue;
                }
                for (Resource target : targets) {
                    reach(target.path());
                }
            }
        }

        private List<ExportDiagnostic> diagnostics(ContentTrim trim) {
            List<ExportDiagnostic> diagnostics = new ArrayList<>(unreadable.size() + 1);
            for (Map.Entry<String, String> file : unreadable.entrySet()) {
                diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Severity.WARNING, ExportLimit.TRIMMED_CONTENT, file.getKey(),
                        "Could not be read as " + file.getValue() + "; every identifier in its raw bytes is kept"));
            }
            diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Severity.INFO, ExportLimit.TRIMMED_CONTENT, "", String.format(Locale.ROOT,
                    "Removed %d of %d files (%d of %d bytes) that the exported world never references",
                    trim.filesRemoved(), trim.filesBefore(), trim.bytesRemoved(), trim.bytesBefore())));
            return diagnostics;
        }
    }
}
