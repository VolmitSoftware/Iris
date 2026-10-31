package art.arcane.iris.probe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

final class DensityJsonBreakdown {
    private static final String DENSITY_FUNCTIONS = "/worldgen/density_function/";
    private static final String NOISES = "/worldgen/noise/";
    private static final String JSON = ".json";
    private static final String CELLULAR = "cellular";
    private static final String NOISE_STYLE = "noise style";
    private static final String UNREFERENCED = "unreferenced";
    private static final String OFFSET_MARKER = "/offset_";
    private static final String OFFSET_SUFFIX = " offset copies";
    private static final int INSTANCE_SEGMENTS = 3;
    private static final Set<String> ROUTER_ROOTS = Set.of("final_density", "temperature", "vegetation", "continents", "erosion",
            "depth", "ridges", "chunk_surface_level", "barrier", "fluid_level_floodedness", "fluid_level_spread", "lava", "exclusion",
            "surface_level", "spawn_elevation");
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final int TOP = 10;

    private final String dimension;
    private final String dimensionPrefix;
    private final Map<String, Integer> categoryIndex = new HashMap<>();
    private final List<String> categoryNames = new ArrayList<>();
    private final Map<String, Entry> entries = new TreeMap<>();
    private final Map<String, List<String>> parents = new HashMap<>();
    private final Map<String, Long> owners = new HashMap<>();

    private DensityJsonBreakdown(String dimension) {
        this.dimension = dimension;
        dimensionPrefix = dimension + "/";
    }

    static DensityJsonBreakdown analyze(SortedMap<String, byte[]> files, String dimension) {
        DensityJsonBreakdown breakdown = new DensityJsonBreakdown(dimension);
        breakdown.load(files);
        return breakdown;
    }

    static String category(String dimension, String id) {
        String base = baseCategory(dimension, id);
        return id.contains(OFFSET_MARKER) ? base + OFFSET_SUFFIX : base;
    }

    private static String baseCategory(String dimension, String id) {
        int separator = id.indexOf(':');
        String path = separator < 0 ? id : id.substring(separator + 1);
        String dimensionPrefix = dimension + "/";
        if (path.startsWith(dimensionPrefix)) {
            path = path.substring(dimensionPrefix.length());
        }
        if (ROUTER_ROOTS.contains(path)) {
            return "router";
        }
        if (path.startsWith("cell/")) {
            return CELLULAR;
        }
        if (path.startsWith("style/")) {
            return NOISE_STYLE;
        }
        if (path.startsWith("biome/selector/")) {
            return "biome selector";
        }
        if (path.startsWith("biome/carving/")) {
            return "biome carving";
        }
        if (path.startsWith("biome/")) {
            return "biome decision";
        }
        if (path.equals("height") || path.startsWith("height/") || path.startsWith("terrain/height/")) {
            return "terrain height";
        }
        if (path.startsWith("terrain/generator/")) {
            return "terrain generator";
        }
        if (path.startsWith("terrain/volume/")) {
            return "terrain volumetric";
        }
        if (path.startsWith("terrain/")) {
            return "terrain bands";
        }
        if (path.startsWith("cave")) {
            return "caves";
        }
        if (path.startsWith("surface/")) {
            return "surface";
        }
        int slash = path.indexOf('/');
        return slash < 0 ? path : path.substring(0, slash);
    }

    Report report() {
        Map<String, CategoryAccumulator> categories = new TreeMap<>();
        Map<String, CategoryAccumulator> groups = new TreeMap<>();
        Map<String, Long> libraryOwners = new TreeMap<>();
        long totalBytes = 0;
        long totalNodes = 0;
        for (Map.Entry<String, Entry> item : entries.entrySet()) {
            String id = item.getKey();
            Entry entry = item.getValue();
            totalBytes += entry.bytes;
            totalNodes += entry.nodes;
            String category = category(dimension, id);
            categories.computeIfAbsent(category, ignored -> new CategoryAccumulator()).add(entry);
            groups.computeIfAbsent(group(id), ignored -> new CategoryAccumulator()).add(entry);
            if (isLibrary(category)) {
                libraryOwners.merge(category + " via " + ownerLabel(owners(id)), entry.bytes, Long::sum);
            }
        }
        List<CategoryBytes> categoryRows = rows(categories, Integer.MAX_VALUE);
        List<CategoryBytes> groupRows = rows(groups, 40);
        List<Bytes> ownerRows = new ArrayList<>();
        for (Map.Entry<String, Long> owner : libraryOwners.entrySet()) {
            ownerRows.add(new Bytes(owner.getKey(), owner.getValue()));
        }
        ownerRows.sort(Comparator.comparingLong(Bytes::bytes).reversed());
        List<Bytes> largest = new ArrayList<>();
        for (Map.Entry<String, Entry> item : entries.entrySet()) {
            largest.add(new Bytes(item.getKey(), item.getValue().bytes));
        }
        largest.sort(Comparator.comparingLong(Bytes::bytes).reversed());
        return new Report(entries.size(), totalBytes, totalNodes, categoryRows, groupRows, ownerRows,
                List.copyOf(largest.subList(0, Math.min(TOP, largest.size()))), cellularInstances());
    }

    private void load(SortedMap<String, byte[]> files) {
        Map<String, byte[]> bodies = new HashMap<>();
        Set<String> noises = new HashSet<>();
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            String id = resourceId(file.getKey(), DENSITY_FUNCTIONS);
            if (id != null) {
                bodies.put(id, file.getValue());
                continue;
            }
            String noise = resourceId(file.getKey(), NOISES);
            if (noise != null) {
                noises.add(noise);
            }
        }
        for (Map.Entry<String, byte[]> body : bodies.entrySet()) {
            JsonElement json = JsonParser.parseString(new String(body.getValue(), StandardCharsets.UTF_8));
            Entry entry = new Entry(body.getValue().length);
            entry.shape = shape(json, bodies.keySet(), noises, entry);
            entries.put(body.getKey(), entry);
            for (String reference : entry.references) {
                parents.computeIfAbsent(reference, ignored -> new ArrayList<>()).add(body.getKey());
            }
        }
    }

    private long shape(JsonElement element, Set<String> functions, Set<String> noises, Entry entry) {
        if (element.isJsonObject()) {
            entry.nodes++;
            JsonObject object = element.getAsJsonObject();
            long hash = mix(FNV_OFFSET, '{');
            for (String key : new TreeMap<>(object.asMap()).keySet()) {
                hash = mix(mix(hash, key.hashCode()), shape(object.get(key), functions, noises, entry));
            }
            return mix(hash, '}');
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            long hash = mix(FNV_OFFSET, '[');
            for (JsonElement item : array) {
                hash = mix(hash, shape(item, functions, noises, entry));
            }
            return mix(hash, ']');
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                return mix(FNV_OFFSET, 'N');
            }
            if (primitive.isString()) {
                String value = primitive.getAsString();
                if (functions.contains(value)) {
                    entry.references.add(value);
                    return mix(FNV_OFFSET, 'R');
                }
                if (noises.contains(value)) {
                    return mix(FNV_OFFSET, 'Z');
                }
                return mix(FNV_OFFSET, value.hashCode());
            }
            return mix(FNV_OFFSET, primitive.getAsBoolean() ? 'T' : 'F');
        }
        return mix(FNV_OFFSET, '0');
    }

    private List<CellularShape> cellularInstances() {
        Map<String, List<String>> instances = new TreeMap<>();
        for (String id : entries.keySet()) {
            if (!isLibrary(category(dimension, id)) || !id.contains(":cell/")) {
                continue;
            }
            instances.computeIfAbsent(instanceKey(id), ignored -> new ArrayList<>()).add(id);
        }
        Map<Long, CellularAccumulator> shapes = new HashMap<>();
        for (Map.Entry<String, List<String>> instance : instances.entrySet()) {
            List<String> members = instance.getValue();
            members.sort(String::compareTo);
            long hash = FNV_OFFSET;
            long bytes = 0;
            long owner = 0L;
            StringBuilder layout = new StringBuilder();
            for (String member : members) {
                Entry entry = entries.get(member);
                String suffix = member.substring(instance.getKey().length());
                hash = mix(mix(hash, suffix.hashCode()), entry.shape);
                bytes += entry.bytes;
                owner |= owners(member);
                if (!layout.isEmpty()) {
                    layout.append(',');
                }
                layout.append(suffix);
            }
            CellularAccumulator accumulator = shapes.computeIfAbsent(hash, ignored -> new CellularAccumulator(instance.getKey(), layout.toString()));
            accumulator.instances++;
            accumulator.bytes += bytes;
            accumulator.owners |= owner;
        }
        List<CellularShape> rows = new ArrayList<>(shapes.size());
        for (CellularAccumulator accumulator : shapes.values()) {
            rows.add(new CellularShape(accumulator.example, accumulator.layout, accumulator.instances, accumulator.bytes, ownerLabel(accumulator.owners)));
        }
        rows.sort(Comparator.comparingLong(CellularShape::bytes).reversed());
        return List.copyOf(rows);
    }

    private long owners(String id) {
        Long known = owners.get(id);
        if (known != null) {
            return known;
        }
        owners.put(id, 0L);
        long mask = 0L;
        List<String> referrers = parents.get(id);
        if (referrers == null) {
            mask = bit(UNREFERENCED);
        } else {
            for (String parent : referrers) {
                String category = category(dimension, parent);
                mask |= isLibrary(category) ? owners(parent) : bit(category);
            }
        }
        owners.put(id, mask);
        return mask;
    }

    private long bit(String category) {
        int index = categoryIndex.computeIfAbsent(category, name -> {
            categoryNames.add(name);
            return categoryNames.size() - 1;
        });
        if (index >= Long.SIZE) {
            throw new IllegalStateException("More than " + Long.SIZE + " density function owner categories");
        }
        return 1L << index;
    }

    private String ownerLabel(long mask) {
        if (mask == 0L) {
            return UNREFERENCED;
        }
        List<String> labels = new ArrayList<>(Long.bitCount(mask));
        for (int index = 0; index < categoryNames.size(); index++) {
            if ((mask & (1L << index)) != 0L) {
                labels.add(categoryNames.get(index));
            }
        }
        labels.sort(String::compareTo);
        return String.join("+", labels);
    }

    private String group(String id) {
        int separator = id.indexOf(':');
        String path = separator < 0 ? id : id.substring(separator + 1);
        if (path.startsWith(dimensionPrefix)) {
            path = path.substring(dimensionPrefix.length());
        }
        String[] segments = path.split("/");
        if (segments.length == 1) {
            return segments[0];
        }
        if (segments[0].equals("cell") || segments[0].equals("style")) {
            return segments[0] + "/*/" + segments[segments.length - 1];
        }
        return segments[0] + "/" + segments[1];
    }

    private static boolean isLibrary(String category) {
        return category.startsWith(CELLULAR) || category.startsWith(NOISE_STYLE);
    }

    private static String instanceKey(String id) {
        int end = id.indexOf(':') + 1;
        for (int segment = 0; segment < INSTANCE_SEGMENTS && end > 0; segment++) {
            int slash = id.indexOf('/', end);
            if (slash < 0) {
                return id;
            }
            end = slash + 1;
        }
        return id.substring(0, end - 1);
    }

    private static List<CategoryBytes> rows(Map<String, CategoryAccumulator> accumulators, int limit) {
        List<CategoryBytes> rows = new ArrayList<>(accumulators.size());
        for (Map.Entry<String, CategoryAccumulator> item : accumulators.entrySet()) {
            CategoryAccumulator accumulator = item.getValue();
            long representative = 0;
            for (long bytes : accumulator.representativeBytes.values()) {
                representative += bytes;
            }
            rows.add(new CategoryBytes(item.getKey(), accumulator.files, accumulator.bytes, accumulator.nodes,
                    accumulator.representativeBytes.size(), accumulator.bytes - representative));
        }
        rows.sort(Comparator.comparingLong(CategoryBytes::bytes).reversed());
        return List.copyOf(rows.subList(0, Math.min(limit, rows.size())));
    }

    private static String resourceId(String path, String folder) {
        int marker = path.indexOf(folder);
        if (!path.startsWith("data/") || marker < 0 || !path.endsWith(JSON)) {
            return null;
        }
        return path.substring("data/".length(), marker) + ":" + path.substring(marker + folder.length(), path.length() - JSON.length());
    }

    private static long mix(long hash, long value) {
        long mixed = hash ^ value;
        mixed *= FNV_PRIME;
        return mixed ^ (mixed >>> 29);
    }

    record Report(int files, long bytes, long nodes, List<CategoryBytes> categories, List<CategoryBytes> groups,
                  List<Bytes> libraryOwners, List<Bytes> largest, List<CellularShape> cellularShapes) {
        int cellularInstances() {
            int count = 0;
            for (CellularShape shape : cellularShapes) {
                count += shape.instances();
            }
            return count;
        }
    }

    record CategoryBytes(String category, int files, long bytes, long nodes, int distinctShapes, long structurallyDuplicateBytes) {
    }

    record Bytes(String name, long bytes) {
    }

    record CellularShape(String example, String members, int instances, long bytes, String owners) {
    }

    private static final class Entry {
        private final long bytes;
        private final List<String> references = new ArrayList<>();
        private long nodes;
        private long shape;

        private Entry(long bytes) {
            this.bytes = bytes;
        }
    }

    private static final class CategoryAccumulator {
        private final Map<Long, Long> representativeBytes = new HashMap<>();
        private int files;
        private long bytes;
        private long nodes;

        private void add(Entry entry) {
            files++;
            bytes += entry.bytes;
            nodes += entry.nodes;
            representativeBytes.putIfAbsent(entry.shape, entry.bytes);
        }
    }

    private static final class CellularAccumulator {
        private final String example;
        private final String layout;
        private int instances;
        private long bytes;
        private long owners;

        private CellularAccumulator(String example, String layout) {
            this.example = example;
            this.layout = layout;
        }
    }
}
