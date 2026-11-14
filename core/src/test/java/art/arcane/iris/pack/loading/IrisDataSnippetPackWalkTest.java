package art.arcane.iris.pack.loading;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.noise.IrisExpression;
import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.mod.IrisMod;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.structure.placement.IrisStructure;
import art.arcane.iris.world.entity.IrisEntity;
import art.arcane.iris.world.entity.IrisMarker;
import art.arcane.iris.world.entity.IrisSpawner;
import art.arcane.iris.world.loot.IrisLootTable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Walks every snippet reference of a real pack, set through IRIS_TEST_PACK, and checks it resolves for the type of the
 * field it sits in. Reads the pack only; skipped when the variable is unset.
 */
public class IrisDataSnippetPackWalkTest {
    private static final String PACK_ENVIRONMENT_VARIABLE = "IRIS_TEST_PACK";
    private static final String ENGINE_PACKAGE_PREFIX = "art.arcane.iris";
    private static final List<Class<? extends IrisRegistrant>> PACK_ROOT_TYPES = List.of(
            IrisBiome.class, IrisRegion.class, IrisDimension.class, IrisGenerator.class, IrisEntity.class,
            IrisLootTable.class, IrisSpawner.class, IrisMarker.class, IrisExpression.class, IrisMod.class,
            IrisStructure.class, IrisJigsawPool.class, IrisJigsawPiece.class);

    private File pack;
    private final Map<String, Class<?>> snippetTypesByFolder = new HashMap<>();
    private final Map<Class<?>, SnippetFolders> foldersByType = new HashMap<>();
    private final List<String> unresolved = new ArrayList<>();
    private int resolved;
    private int outsideSnippetFields;
    private int snippetFilesWalked;
    private final List<String> untypedSnippetFiles = new ArrayList<>();

    @Test
    public void everySnippetReferenceInThePackResolves() throws Exception {
        String packPath = System.getenv(PACK_ENVIRONMENT_VARIABLE);
        assumeTrue(PACK_ENVIRONMENT_VARIABLE + " is not set", packPath != null && !packPath.isBlank());
        pack = new File(packPath);
        assumeTrue(pack + " is not a folder", pack.isDirectory());

        Set<Class<?>> visitedTypes = new HashSet<>();
        PACK_ROOT_TYPES.forEach(root -> collectSnippetTypes(root, visitedTypes));
        for (Class<? extends IrisRegistrant> root : PACK_ROOT_TYPES) {
            walkRootFolder(root);
        }
        walkSnippetFiles();

        System.out.println("snippet pack walk: resolved=" + resolved + " unresolved=" + unresolved.size()
                + " outsideSnippetFields=" + outsideSnippetFields + " snippetFilesWalked=" + snippetFilesWalked
                + " untypedSnippetFiles=" + untypedSnippetFiles.size());
        untypedSnippetFiles.forEach(file -> System.out.println("untyped snippet file: " + file));
        assertTrue("unresolved snippet references:\n" + String.join("\n", unresolved), unresolved.isEmpty());
        assertTrue("the pack holds no snippet references", resolved > 0);
    }

    private void walkRootFolder(Class<? extends IrisRegistrant> root) throws Exception {
        String folderName = root.getConstructor().newInstance().getFolderName();
        for (File file : jsonFilesUnder(new File(pack, folderName))) {
            walk(parse(file), root, relative(file));
        }
    }

    private void walkSnippetFiles() throws IOException {
        for (File file : jsonFilesUnder(new File(pack, "snippet"))) {
            Class<?> type = typeOfSnippetFile(file);
            if (type == null) {
                untypedSnippetFiles.add(relative(file));
                continue;
            }
            snippetFilesWalked++;
            JsonElement content = parse(file);
            if (content != null && content.isJsonArray()) {
                content.getAsJsonArray().forEach(entry -> walk(entry, type, relative(file)));
            } else {
                walk(content, type, relative(file));
            }
        }
    }

    private Class<?> typeOfSnippetFile(File file) {
        String[] segments = relative(file).split("/");
        Class<?> legacyType = snippetTypesByFolder.get(segments[1]);
        if (legacyType != null) {
            return legacyType;
        }
        return segments.length > 4 ? snippetTypesByFolder.get(segments[3]) : null;
    }

    private void walk(JsonElement value, Type type, String path) {
        if (value == null || value.isJsonNull()) {
            return;
        }
        Class<?> raw = rawClass(type);
        if (value.isJsonPrimitive()) {
            checkReference(value, raw, path);
            return;
        }
        if (value.isJsonArray()) {
            walkElements(value.getAsJsonArray(), elementTypeOf(type), path);
            return;
        }
        walkObject(value.getAsJsonObject(), type, raw, path);
    }

    private void walkElements(JsonArray elements, Type elementType, String path) {
        for (int index = 0; index < elements.size(); index++) {
            walk(elements.get(index), elementType, path + "[" + index + "]");
        }
    }

    private void walkObject(JsonObject object, Type type, Class<?> raw, String path) {
        if (raw == null) {
            return;
        }
        if (Map.class.isAssignableFrom(raw)) {
            Type valueType = typeArgument(type, 1);
            object.entrySet().forEach(entry -> walk(entry.getValue(), valueType, path + "." + entry.getKey()));
            return;
        }
        Map<String, Type> fields = fieldTypesOf(raw);
        object.entrySet().forEach(entry -> {
            Type fieldType = fields.get(entry.getKey());
            if (fieldType != null) {
                walk(entry.getValue(), fieldType, path + "." + entry.getKey());
            }
        });
    }

    private void checkReference(JsonElement value, Class<?> raw, String path) {
        if (!value.getAsJsonPrimitive().isString() || !value.getAsString().startsWith(SnippetFolders.PREFIX)) {
            return;
        }
        String reference = value.getAsString();
        SnippetFolders folders = raw == null ? null : foldersOf(raw);
        if (folders == null) {
            outsideSnippetFields++;
            return;
        }
        if (folders.find(pack, reference).found()) {
            resolved++;
            return;
        }
        unresolved.add(path + " -> " + reference + " (tried " + folders.candidateKeys(reference) + ")");
    }

    private SnippetFolders foldersOf(Class<?> type) {
        Snippet snippet = type.getDeclaredAnnotation(Snippet.class);
        if (snippet == null) {
            return null;
        }
        return foldersByType.computeIfAbsent(type, ignored -> SnippetFolders.discover(pack, snippet.value()));
    }

    private void collectSnippetTypes(Type type, Set<Class<?>> visited) {
        if (type instanceof ParameterizedType parameterized) {
            for (Type argument : parameterized.getActualTypeArguments()) {
                collectSnippetTypes(argument, visited);
            }
        }
        Class<?> raw = rawClass(type);
        if (raw == null) {
            return;
        }
        if (raw.isArray()) {
            collectSnippetTypes(raw.getComponentType(), visited);
            return;
        }
        if (!raw.getName().startsWith(ENGINE_PACKAGE_PREFIX) || raw.isEnum() || raw.isInterface() || !visited.add(raw)) {
            return;
        }
        Snippet snippet = raw.getDeclaredAnnotation(Snippet.class);
        if (snippet != null) {
            snippetTypesByFolder.putIfAbsent(snippet.value(), raw);
        }
        fieldTypesOf(raw).values().forEach(fieldType -> collectSnippetTypes(fieldType, visited));
    }

    private static Map<String, Type> fieldTypesOf(Class<?> type) {
        Map<String, Type> fields = new HashMap<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isTransient(field.getModifiers())) {
                    fields.putIfAbsent(field.getName(), field.getGenericType());
                }
            }
        }
        return fields;
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> raw) {
            return raw;
        }
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> raw) {
            return raw;
        }
        return null;
    }

    private static Type elementTypeOf(Type type) {
        if (type instanceof Class<?> raw && raw.isArray()) {
            return raw.getComponentType();
        }
        return typeArgument(type, 0);
    }

    private static Type typeArgument(Type type, int index) {
        if (type instanceof ParameterizedType parameterized && index < parameterized.getActualTypeArguments().length) {
            return parameterized.getActualTypeArguments()[index];
        }
        return Object.class;
    }

    private static JsonElement parse(File file) {
        try {
            return JsonParser.parseString(Files.readString(file.toPath()));
        } catch (Exception unreadable) {
            return null;
        }
    }

    private static List<File> jsonFilesUnder(File folder) {
        if (!folder.isDirectory()) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(folder.toPath())) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".json"))
                    .map(Path::toFile)
                    .sorted()
                    .toList();
        } catch (IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }

    private String relative(File file) {
        return pack.toPath().relativize(file.toPath()).toString().replace('\\', '/');
    }
}
