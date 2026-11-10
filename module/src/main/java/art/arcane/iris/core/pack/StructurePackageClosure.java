package art.arcane.iris.core.pack;

import art.arcane.iris.core.structure.authoring.StructureResourceBundle;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class StructurePackageClosure {
    private static final String STRUCTURES = "structures";
    private static final String POOLS = "jigsaw-pools";
    private static final String PIECES = "jigsaw-pieces";
    private static final String OBJECTS = "objects";
    private static final String LOOT = "loot";

    private final Path sourceRoot;
    private final Set<String> structures;
    private final Set<String> pools;
    private final Set<String> pieces;
    private final Set<String> objects;
    private final Set<String> loot;
    private final List<String> errors;

    private StructurePackageClosure(Path sourceRoot, MutableClosure closure) {
        this.sourceRoot = sourceRoot;
        this.structures = Collections.unmodifiableSet(new LinkedHashSet<>(closure.structures));
        this.pools = Collections.unmodifiableSet(new LinkedHashSet<>(closure.pools));
        this.pieces = Collections.unmodifiableSet(new LinkedHashSet<>(closure.pieces));
        this.objects = Collections.unmodifiableSet(new LinkedHashSet<>(closure.objects));
        this.loot = Collections.unmodifiableSet(new LinkedHashSet<>(closure.loot));
        this.errors = Collections.unmodifiableList(new ArrayList<>(closure.errors));
    }

    public static StructurePackageClosure collect(File sourceRoot, Collection<String> rootStructures) {
        return collect(sourceRoot, rootStructures, null);
    }

    public static StructurePackageClosure collect(
            File sourceRoot,
            Collection<String> rootStructures,
            Limits limits
    ) {
        Path normalizedRoot = sourceRoot.toPath().toAbsolutePath().normalize();
        MutableClosure closure = new MutableClosure(limits);
        if (!Files.isDirectory(normalizedRoot)) {
            closure.errors.add("Structure package source is not a directory: " + normalizedRoot);
            return new StructurePackageClosure(normalizedRoot, closure);
        }

        if (rootStructures != null) {
            for (String structureKey : rootStructures) {
                enqueueKey(closure.structureQueue, structureKey, "structure", closure.errors);
            }
        }

        collectStructures(normalizedRoot, closure);
        collectPools(normalizedRoot, closure);
        collectPieces(normalizedRoot, closure);
        validateBinaryResources(normalizedRoot, closure);
        validatePortableCollisions(closure.structures, STRUCTURES, closure.errors);
        validatePortableCollisions(closure.pools, POOLS, closure.errors);
        validatePortableCollisions(closure.pieces, PIECES, closure.errors);
        validatePortableCollisions(closure.objects, OBJECTS, closure.errors);
        validatePortableCollisions(closure.loot, LOOT, closure.errors);
        return new StructurePackageClosure(normalizedRoot, closure);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public Set<String> structures() {
        return structures;
    }

    public Set<String> pools() {
        return pools;
    }

    public Set<String> pieces() {
        return pieces;
    }

    public Set<String> objects() {
        return objects;
    }

    public Set<String> loot() {
        return loot;
    }

    public List<String> errors() {
        return errors;
    }

    public String writeTo(File targetRoot, boolean minify) throws IOException {
        if (!isValid()) {
            throw new IOException("Cannot package an invalid structure graph: " + String.join("; ", errors));
        }

        Path normalizedTarget = targetRoot.toPath().toAbsolutePath().normalize();
        prepareTargetRoot(normalizedTarget);
        StringBuilder hashes = new StringBuilder();
        copyJsonResources(normalizedTarget, STRUCTURES, structures, minify, hashes);
        copyJsonResources(normalizedTarget, POOLS, pools, minify, hashes);
        copyJsonResources(normalizedTarget, PIECES, pieces, minify, hashes);
        copyJsonResources(normalizedTarget, LOOT, loot, minify, hashes);
        copyBinaryResources(normalizedTarget, OBJECTS, objects, hashes);
        return IO.hash(hashes.toString());
    }

    private static void collectStructures(Path sourceRoot, MutableClosure closure) {
        while (!closure.structureQueue.isEmpty()) {
            String structureKey = closure.structureQueue.removeFirst();
            if (!closure.structures.add(structureKey)) {
                continue;
            }

            JSONObject structure = readJson(sourceRoot, STRUCTURES, structureKey, closure.errors, closure.limits);
            if (structure == null) {
                continue;
            }
            String startPool = requiredString(structure, "startPool", "Structure '" + structureKey + "'", closure.errors);
            if (startPool != null) {
                enqueueKey(closure.poolQueue, startPool,
                        "start pool for structure '" + structureKey + "'", closure.errors);
            }
            JSONArray loot = optionalArray(structure, "loot", "Structure '" + structureKey + "'", closure.errors);
            addArrayKeys(loot, closure.loot, "loot table", closure.errors);
        }
    }

    private static void collectPools(Path sourceRoot, MutableClosure closure) {
        while (!closure.poolQueue.isEmpty()) {
            String poolKey = closure.poolQueue.removeFirst();
            if (!closure.pools.add(poolKey)) {
                continue;
            }

            JSONObject pool = readJson(sourceRoot, POOLS, poolKey, closure.errors, closure.limits);
            if (pool == null) {
                continue;
            }
            JSONArray entries = requiredArray(pool, "pieces", "Jigsaw pool '" + poolKey + "'", closure.errors);
            if (entries != null) {
                for (int index = 0; index < entries.length(); index++) {
                    JSONObject entry = entries.optJSONObject(index);
                    if (entry == null) {
                        closure.errors.add("Jigsaw pool '" + poolKey + "' has a non-object piece entry at index " + index + ".");
                        continue;
                    }
                    if (isEmptyEntry(entry, poolKey, index, closure.errors)) {
                        continue;
                    }
                    String pieceKey = requiredString(entry, "piece",
                            "Piece entry " + index + " in jigsaw pool '" + poolKey + "'", closure.errors);
                    if (pieceKey != null) {
                        enqueueKey(closure.pieceQueue, pieceKey,
                                "piece in pool '" + poolKey + "'", closure.errors);
                    }
                }
            }
            String fallback = optionalString(pool, "fallback", "Jigsaw pool '" + poolKey + "'", closure.errors);
            enqueueOptionalKey(closure.poolQueue, fallback,
                    "fallback pool for '" + poolKey + "'", closure.errors);
        }
    }

    private static void collectPieces(Path sourceRoot, MutableClosure closure) {
        while (!closure.pieceQueue.isEmpty()) {
            String pieceKey = closure.pieceQueue.removeFirst();
            if (!closure.pieces.add(pieceKey)) {
                continue;
            }

            JSONObject piece = readJson(sourceRoot, PIECES, pieceKey, closure.errors, closure.limits);
            if (piece == null) {
                continue;
            }
            String objectKey = requiredString(piece, "object", "Jigsaw piece '" + pieceKey + "'", closure.errors);
            if (objectKey != null) {
                addRequiredKey(closure.objects, objectKey,
                        "object for piece '" + pieceKey + "'", closure.errors);
            }
            JSONArray connectors = optionalArray(piece, "connectors", "Jigsaw piece '" + pieceKey + "'", closure.errors);
            if (connectors == null) {
                continue;
            }
            for (int index = 0; index < connectors.length(); index++) {
                JSONObject connector = connectors.optJSONObject(index);
                if (connector == null) {
                    closure.errors.add("Jigsaw piece '" + pieceKey + "' has a non-object connector at index " + index + ".");
                    continue;
                }
                String poolKey = requiredString(connector, "pool",
                        "Connector " + index + " in jigsaw piece '" + pieceKey + "'", closure.errors);
                if (poolKey != null) {
                    enqueueKey(closure.poolQueue, poolKey,
                            "connector pool for piece '" + pieceKey + "'", closure.errors);
                }
            }
            collectPools(sourceRoot, closure);
        }
    }

    private static void validateBinaryResources(Path sourceRoot, MutableClosure closure) {
        for (String objectKey : closure.objects) {
            resolveExisting(sourceRoot, OBJECTS, objectKey, ".iob", closure.errors);
        }
        for (String lootKey : closure.loot) {
            resolveExisting(sourceRoot, LOOT, lootKey, ".json", closure.errors);
        }
    }

    private static JSONObject readJson(
            Path sourceRoot,
            String folder,
            String key,
            List<String> errors,
            Limits limits
    ) {
        Path file = resolveExisting(sourceRoot, folder, key, ".json", errors);
        if (file == null) {
            return null;
        }
        try {
            String content = limits == null
                    ? Files.readString(file, StandardCharsets.UTF_8)
                    : new String(readBounded(file, limits.maxJsonBytes()), StandardCharsets.UTF_8);
            return new JSONObject(content);
        } catch (IOException | RuntimeException e) {
            errors.add("Invalid " + folder + " resource '" + key + "': " + describe(e));
            return null;
        }
    }

    private static byte[] readBounded(Path file, int maximumBytes) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            byte[] content = input.readNBytes(maximumBytes + 1);
            if (content.length > maximumBytes) {
                throw new IOException("JSON resource exceeds " + maximumBytes + " bytes");
            }
            return content;
        }
    }

    private static Path resolveExisting(Path sourceRoot, String folder, String key, String extension,
                                        List<String> errors) {
        Path file = resolveResource(sourceRoot, folder, key, extension, errors);
        if (file != null && !Files.isRegularFile(file)) {
            errors.add("Missing " + folder + " resource '" + key + "'.");
            return null;
        }
        if (file != null && !isContainedSourceResource(sourceRoot, folder, key, extension, file, errors)) {
            return null;
        }
        return file;
    }

    private static Path resolveResource(Path root, String folder, String key, String extension, List<String> errors) {
        if (!validKey(key)) {
            errors.add("Invalid " + folder + " resource key '" + key + "'.");
            return null;
        }
        Path folderRoot = root.resolve(folder).normalize();
        Path file = folderRoot.resolve(key + extension).normalize();
        if (!file.startsWith(folderRoot)) {
            errors.add("Resource key escapes " + folder + ": '" + key + "'.");
            return null;
        }
        return file;
    }

    private void copyJsonResources(Path targetRoot, String folder, Set<String> keys, boolean minify,
                                   StringBuilder hashes) throws IOException {
        for (String key : keys) {
            Path source = resolveExistingOrThrow(sourceRoot, folder, key, ".json");
            Path target = prepareTargetResource(targetRoot, folder, key, ".json");
            String json = new JSONObject(Files.readString(source, StandardCharsets.UTF_8)).toString(minify ? 0 : 4);
            Files.writeString(target, json, StandardCharsets.UTF_8);
            hashes.append(IO.hash(json));
        }
    }

    private void copyBinaryResources(Path targetRoot, String folder, Set<String> keys,
                                     StringBuilder hashes) throws IOException {
        for (String key : keys) {
            Path source = resolveExistingOrThrow(sourceRoot, folder, key, ".iob");
            Path target = prepareTargetResource(targetRoot, folder, key, ".iob");
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            hashes.append(IO.hash(source.toFile()));
        }
    }

    private static Path resolveExistingOrThrow(Path root, String folder, String key, String extension) throws IOException {
        List<String> errors = new ArrayList<>();
        Path file = resolveExisting(root, folder, key, extension, errors);
        if (file == null) {
            throw new IOException(String.join("; ", errors));
        }
        return file;
    }

    private static Path resolveResourceOrThrow(Path root, String folder, String key, String extension) throws IOException {
        List<String> errors = new ArrayList<>();
        Path file = resolveResource(root, folder, key, extension, errors);
        if (file == null) {
            throw new IOException(String.join("; ", errors));
        }
        return file;
    }

    private static void prepareTargetRoot(Path targetRoot) throws IOException {
        if (Files.exists(targetRoot, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(targetRoot)) {
                throw new IOException("Structure package target is not a directory: " + targetRoot);
            }
            return;
        }
        Files.createDirectories(targetRoot);
    }

    private static Path prepareTargetResource(Path targetRoot, String folder, String key, String extension) throws IOException {
        Path target = resolveResourceOrThrow(targetRoot, folder, key, extension);
        Path parent = target.getParent();
        Path current = targetRoot;
        Path relativeParent = targetRoot.relativize(parent);
        for (Path segment : relativeParent) {
            Path next = current.resolve(segment);
            if (Files.exists(next, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(next) || !Files.isDirectory(next, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Structure package target contains a non-directory or symbolic link: " + next);
                }
            } else {
                Files.createDirectory(next);
            }
            current = next;
        }
        if (Files.isSymbolicLink(target)) {
            throw new IOException("Structure package target is a symbolic link: " + target);
        }
        return target;
    }

    private static boolean isContainedSourceResource(Path sourceRoot, String folder, String key, String extension,
                                                     Path file, List<String> errors) {
        try {
            Path realSourceRoot = sourceRoot.toRealPath();
            Path realFile = file.toRealPath();
            if (!realFile.startsWith(realSourceRoot)) {
                errors.add("Resource escapes the structure package through a symbolic link: "
                        + folder + "/" + key + extension + ".");
                return false;
            }
            if (!hasExactSourceCase(sourceRoot, file)) {
                errors.add("Resource path casing does not match its reference: "
                        + folder + "/" + key + extension + ".");
                return false;
            }
            return true;
        } catch (IOException exception) {
            errors.add("Cannot verify " + folder + " resource '" + key + "': " + describe(exception));
            return false;
        }
    }

    private static boolean hasExactSourceCase(Path sourceRoot, Path file) throws IOException {
        Path current = sourceRoot;
        for (Path segment : sourceRoot.relativize(file)) {
            Path exactChild = findExactChild(current, segment.toString());
            if (exactChild == null) {
                return false;
            }
            current = exactChild;
        }
        return true;
    }

    private static Path findExactChild(Path folder, String name) throws IOException {
        try (DirectoryStream<Path> children = Files.newDirectoryStream(folder)) {
            for (Path child : children) {
                if (child.getFileName().toString().equals(name)) {
                    return child;
                }
            }
        }
        return null;
    }

    private static void addArrayKeys(JSONArray values, Set<String> target, String kind, List<String> errors) {
        if (values == null) {
            return;
        }
        for (int index = 0; index < values.length(); index++) {
            Object raw = values.opt(index);
            if (!(raw instanceof String value)) {
                errors.add("Invalid " + kind + " reference at index " + index + ".");
                continue;
            }
            addRequiredKey(target, value, kind, errors);
        }
    }

    private static void addRequiredKey(Set<String> target, String key, String kind, List<String> errors) {
        String canonical = canonicalKey(key);
        if (canonical == null) {
            errors.add("Missing or invalid " + kind + ".");
            return;
        }
        target.add(canonical);
    }

    private static void enqueueKey(Deque<String> queue, String key, String kind, List<String> errors) {
        String canonical = canonicalKey(key);
        if (canonical == null) {
            errors.add("Missing or invalid " + kind + ".");
            return;
        }
        queue.addLast(canonical);
    }

    private static void enqueueOptionalKey(Deque<String> queue, String key, String kind, List<String> errors) {
        if (key == null || key.isBlank()) {
            return;
        }
        enqueueKey(queue, key, kind, errors);
    }

    private static String canonicalKey(String key) {
        if (key == null || !key.equals(key.trim())) {
            return null;
        }
        return validKey(key) ? key : null;
    }

    private static boolean validKey(String key) {
        if (key == null || key.isBlank() || !key.equals(key.trim())) {
            return false;
        }
        try {
            StructureResourceBundle.validateRelativePath(key);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String requiredString(JSONObject object, String field, String resource, List<String> errors) {
        Object value = object.opt(field);
        if (value instanceof String stringValue) {
            return stringValue;
        }
        errors.add(resource + " requires string field '" + field + "'.");
        return null;
    }

    private static String optionalString(JSONObject object, String field, String resource, List<String> errors) {
        if (!object.has(field)) {
            return null;
        }
        return requiredString(object, field, resource, errors);
    }

    private static JSONArray requiredArray(JSONObject object, String field, String resource, List<String> errors) {
        Object value = object.opt(field);
        if (value instanceof JSONArray arrayValue) {
            return arrayValue;
        }
        errors.add(resource + " requires array field '" + field + "'.");
        return null;
    }

    private static JSONArray optionalArray(JSONObject object, String field, String resource, List<String> errors) {
        if (!object.has(field)) {
            return null;
        }
        return requiredArray(object, field, resource, errors);
    }

    private static boolean isEmptyEntry(JSONObject entry, String poolKey, int index, List<String> errors) {
        if (!entry.has("empty")) {
            return false;
        }
        Object emptyValue = entry.opt("empty");
        if (!(emptyValue instanceof Boolean isEmpty)) {
            errors.add("Piece entry " + index + " in jigsaw pool '" + poolKey + "' requires boolean field 'empty'.");
            return false;
        }
        if (!isEmpty) {
            return false;
        }
        if (!entry.has("piece")) {
            return true;
        }
        Object pieceValue = entry.opt("piece");
        if (!(pieceValue instanceof String piece)) {
            errors.add("Empty piece entry " + index + " in jigsaw pool '" + poolKey
                    + "' requires string field 'piece' when defined.");
        } else if (!piece.isBlank()) {
            errors.add("Empty piece entry " + index + " in jigsaw pool '" + poolKey
                    + "' cannot define non-empty field 'piece'.");
        }
        return true;
    }

    private static void validatePortableCollisions(Set<String> keys, String folder, List<String> errors) {
        Map<String, String> portableKeys = new HashMap<>();
        for (String key : keys) {
            String portableKey = key.toLowerCase(Locale.ROOT);
            String previous = portableKeys.putIfAbsent(portableKey, key);
            if (previous != null && !previous.equals(key)) {
                errors.add("Case-insensitive " + folder + " resource collision between '"
                        + previous + "' and '" + key + "'.");
            }
        }
    }

    private static String describe(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    public record Limits(int maxResources, int maxJsonBytes) {
        public Limits {
            if (maxResources < 1 || maxResources > 100_000) {
                throw new IllegalArgumentException("Structure closure resource limit must be between 1 and 100000");
            }
            if (maxJsonBytes < 1 || maxJsonBytes > 64 * 1024 * 1024) {
                throw new IllegalArgumentException("Structure closure JSON limit must be between 1 and 67108864 bytes");
            }
        }
    }

    private static final class MutableClosure {
        private final Limits limits;
        private final Set<String> structures;
        private final Set<String> pools;
        private final Set<String> pieces;
        private final Set<String> objects;
        private final Set<String> loot;
        private final List<String> errors = new ArrayList<>();
        private final Deque<String> structureQueue = new ArrayDeque<>();
        private final Deque<String> poolQueue = new ArrayDeque<>();
        private final Deque<String> pieceQueue = new ArrayDeque<>();
        private int resources;
        private boolean resourceLimitReported;

        private MutableClosure(Limits limits) {
            this.limits = limits;
            structures = new BudgetedSet(this);
            pools = new BudgetedSet(this);
            pieces = new BudgetedSet(this);
            objects = new BudgetedSet(this);
            loot = new BudgetedSet(this);
        }

        private boolean reserveResource() {
            if (limits == null || resources < limits.maxResources()) {
                resources++;
                return true;
            }
            if (!resourceLimitReported) {
                errors.add("Structure closure exceeds " + limits.maxResources() + " resources.");
                resourceLimitReported = true;
            }
            return false;
        }
    }

    private static final class BudgetedSet extends LinkedHashSet<String> {
        private final MutableClosure closure;

        private BudgetedSet(MutableClosure closure) {
            this.closure = closure;
        }

        @Override
        public boolean add(String value) {
            if (contains(value) || !closure.reserveResource()) {
                return false;
            }
            return super.add(value);
        }
    }
}
