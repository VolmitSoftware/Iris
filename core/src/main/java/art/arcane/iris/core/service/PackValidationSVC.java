package art.arcane.iris.core.service;

import art.arcane.iris.Iris;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Snippet;
import art.arcane.iris.util.common.data.registry.KeyedRegistry;
import art.arcane.iris.util.common.data.registry.RegistryUtil;
import art.arcane.iris.util.common.plugin.IrisService;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import org.bukkit.NamespacedKey;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PackValidationSVC implements IrisService {
    private final Map<Class<?>, Map<String, Field>> fieldCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, Boolean> keyedTypeCache = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        IrisSettings.IrisSettingsGeneral general = IrisSettings.get().getGeneral();
        if (!general.isValidatePacksOnStartup()) {
            return;
        }

        File packsFolder = Iris.instance.getDataFolder("packs");
        File[] packFolders = packsFolder.listFiles(File::isDirectory);
        if (packFolders == null || packFolders.length == 0) {
            Iris.info("Startup pack validation skipped: no pack folders found.");
            return;
        }

        int maxLoggedIssues = Math.max(1, general.getMaxPackValidationErrorsPerPack());
        int totalPacks = 0;
        int totalFiles = 0;
        int totalErrors = 0;
        int packsWithErrors = 0;
        long started = System.currentTimeMillis();

        Iris.info("Startup pack validation started for %d pack(s).", packFolders.length);

        for (File packFolder : packFolders) {
            totalPacks++;
            PackValidationReport report = validatePack(packFolder, maxLoggedIssues);
            totalFiles += report.filesChecked;
            totalErrors += report.errorCount;

            if (report.errorCount > 0) {
                packsWithErrors++;
                Iris.error("Pack \"%s\" has %d validation issue(s) across %d file(s).", report.packName, report.errorCount, report.filesChecked);
                for (String issue : report.sampleIssues) {
                    Iris.error(" - %s", issue);
                }

                int hiddenIssues = report.errorCount - report.sampleIssues.size();
                if (hiddenIssues > 0) {
                    Iris.error(" - ... %d additional issue(s) not shown", hiddenIssues);
                }
            }
        }

        long elapsed = System.currentTimeMillis() - started;
        if (totalErrors == 0) {
            Iris.info("Startup pack validation finished: %d pack(s), %d file(s), no issues (%dms).", totalPacks, totalFiles, elapsed);
            return;
        }

        Iris.error("Startup pack validation finished: %d issue(s) in %d pack(s), %d file(s) checked (%dms).", totalErrors, packsWithErrors, totalFiles, elapsed);
        if (general.isStopStartupOnPackValidationFailure()) {
            throw new IllegalStateException("Pack validation failed with " + totalErrors + " issue(s).");
        }
    }

    @Override
    public void onDisable() {

    }

    private PackValidationReport validatePack(File packFolder, int maxLoggedIssues) {
        PackValidationReport report = new PackValidationReport(packFolder.getName(), maxLoggedIssues);
        IrisData data = IrisData.get(packFolder);
        Collection<ResourceLoader<? extends IrisRegistrant>> loaders = data.getLoaders().values();

        for (ResourceLoader<? extends IrisRegistrant> loader : loaders) {
            Class<?> rootType = loader.getObjectClass();
            if (rootType == null) {
                continue;
            }

            List<File> folders = loader.getFolders();
            for (File folder : folders) {
                validateFolder(data, packFolder, folder, rootType, report);
            }
        }

        validateSnippetFolders(data, packFolder, report);
        return report;
    }

    private void validateSnippetFolders(IrisData data, File packFolder, PackValidationReport report) {
        File snippetRoot = new File(packFolder, "snippet");
        if (!snippetRoot.isDirectory()) {
            return;
        }

        Map<String, Class<?>> snippetTypes = new HashMap<>();
        for (Class<?> snippetType : data.resolveSnippets()) {
            Snippet snippet = snippetType.getDeclaredAnnotation(Snippet.class);
            if (snippet == null) {
                continue;
            }
            snippetTypes.put(snippet.value(), snippetType);
        }

        for (Map.Entry<String, Class<?>> entry : snippetTypes.entrySet()) {
            File typeFolder = new File(snippetRoot, entry.getKey());
            if (!typeFolder.isDirectory()) {
                continue;
            }

            validateFolder(data, packFolder, typeFolder, entry.getValue(), report);
        }
    }

    private void validateFolder(IrisData data, File packFolder, File folder, Class<?> rootType, PackValidationReport report) {
        List<File> jsonFiles = new ArrayList<>();
        collectJsonFiles(folder, jsonFiles);

        for (File jsonFile : jsonFiles) {
            report.filesChecked++;
            validateJsonFile(data, packFolder, jsonFile, rootType, report);
        }
    }

    private void collectJsonFiles(File folder, List<File> output) {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                collectJsonFiles(file, output);
                continue;
            }

            if (file.isFile() && file.getName().endsWith(".json")) {
                output.add(file);
            }
        }
    }

    private void validateJsonFile(IrisData data, File packFolder, File file, Class<?> rootType, PackValidationReport report) {
        String content;
        try {
            content = IO.readAll(file);
        } catch (Throwable e) {
            report.addIssue(packFolder, file, "$", "Unable to read file: " + simpleMessage(e));
            return;
        }

        JSONObject object;
        try {
            object = new JSONObject(content);
        } catch (Throwable e) {
            report.addIssue(packFolder, file, "$", "Invalid JSON syntax: " + simpleMessage(e));
            return;
        }

        try {
            Object decoded = data.getGson().fromJson(content, rootType);
            if (decoded == null) {
                report.addIssue(packFolder, file, "$", "Decoded value is null for root type " + rootType.getSimpleName());
            }
        } catch (Throwable e) {
            report.addIssue(packFolder, file, "$", "Deserializer rejected file for " + rootType.getSimpleName() + ": " + simpleMessage(e));
        }

        validateObject(packFolder, file, "$", object, rootType, report);
    }

    private void validateObject(File packFolder, File file, String path, JSONObject object, Class<?> type, PackValidationReport report) {
        if (type == null) {
            return;
        }

        Map<String, Field> fields = getSerializableFields(type);
        for (String key : object.keySet()) {
            Field field = fields.get(key);
            String keyPath = path + "." + key;

            if (field == null) {
                report.addIssue(packFolder, file, keyPath, "Unknown or misplaced key for type " + type.getSimpleName());
                continue;
            }

            Object value = object.get(key);
            validateValue(packFolder, file, keyPath, value, field.getType(), field.getGenericType(), field, report);
        }
    }

    private void validateValue(File packFolder, File file, String path, Object value, Class<?> expectedType, Type genericType, Field sourceField, PackValidationReport report) {
        Class<?> normalizedType = normalizeType(expectedType);
        if (value == JSONObject.NULL) {
            if (normalizedType.isPrimitive()) {
                report.addIssue(packFolder, file, path, "Null value is not allowed for primitive type " + normalizedType.getSimpleName());
            }
            return;
        }

        if (Collection.class.isAssignableFrom(normalizedType)) {
            validateCollection(packFolder, file, path, value, genericType, sourceField, report);
            return;
        }

        if (normalizedType.isArray()) {
            validateArray(packFolder, file, path, value, normalizedType, report);
            return;
        }

        if (Map.class.isAssignableFrom(normalizedType)) {
            validateMap(packFolder, file, path, value, genericType, report);
            return;
        }

        if (normalizedType.isEnum()) {
            validateEnum(packFolder, file, path, value, normalizedType, report);
            return;
        }

        if (isKeyedType(normalizedType)) {
            validateKeyed(packFolder, file, path, value, normalizedType, report);
            return;
        }

        if (normalizedType == String.class) {
            if (!(value instanceof String)) {
                report.addIssue(packFolder, file, path, "Expected string value");
            }
            return;
        }

        if (normalizedType == Boolean.class) {
            if (!(value instanceof Boolean)) {
                report.addIssue(packFolder, file, path, "Expected boolean value");
            }
            return;
        }

        if (Number.class.isAssignableFrom(normalizedType)) {
            if (!(value instanceof Number)) {
                report.addIssue(packFolder, file, path, "Expected numeric value");
            }
            return;
        }

        if (normalizedType == Character.class) {
            if (!(value instanceof String text) || text.length() != 1) {
                report.addIssue(packFolder, file, path, "Expected single-character string value");
            }
            return;
        }

        Snippet snippet = normalizedType.getDeclaredAnnotation(Snippet.class);
        if (snippet != null) {
            if (value instanceof String reference) {
                if (!reference.startsWith("snippet/")) {
                    report.addIssue(packFolder, file, path, "Snippet reference must start with snippet/");
                }
                return;
            }

            if (value instanceof JSONObject jsonObject) {
                validateObject(packFolder, file, path, jsonObject, normalizedType, report);
                return;
            }

            report.addIssue(packFolder, file, path, "Snippet value must be an object or snippet reference string");
            return;
        }

        if (value instanceof JSONObject jsonObject) {
            if (shouldValidateNestedObject(normalizedType)) {
                validateObject(packFolder, file, path, jsonObject, normalizedType, report);
            }
            return;
        }

        if (value instanceof JSONArray) {
            report.addIssue(packFolder, file, path, "Unexpected array value for type " + normalizedType.getSimpleName());
            return;
        }

        if (shouldValidateNestedObject(normalizedType)) {
            report.addIssue(packFolder, file, path, "Expected object value for type " + normalizedType.getSimpleName());
        }
    }

    private void validateCollection(File packFolder, File file, String path, Object value, Type genericType, Field sourceField, PackValidationReport report) {
        if (!(value instanceof JSONArray array)) {
            report.addIssue(packFolder, file, path, "Expected array value");
            return;
        }

        Class<?> elementType = resolveCollectionElementType(genericType, sourceField);
        if (elementType == null || elementType == Object.class) {
            return;
        }

        for (int i = 0; i < array.length(); i++) {
            Object element = array.get(i);
            validateValue(packFolder, file, path + "[" + i + "]", element, elementType, null, null, report);
        }
    }

    private void validateArray(File packFolder, File file, String path, Object value, Class<?> expectedType, PackValidationReport report) {
        if (!(value instanceof JSONArray array)) {
            report.addIssue(packFolder, file, path, "Expected array value");
            return;
        }

        Class<?> componentType = normalizeType(expectedType.getComponentType());
        for (int i = 0; i < array.length(); i++) {
            Object element = array.get(i);
            validateValue(packFolder, file, path + "[" + i + "]", element, componentType, null, null, report);
        }
    }

    private void validateMap(File packFolder, File file, String path, Object value, Type genericType, PackValidationReport report) {
        if (!(value instanceof JSONObject object)) {
            report.addIssue(packFolder, file, path, "Expected object value");
            return;
        }

        Class<?> valueType = resolveMapValueType(genericType);
        if (valueType == null || valueType == Object.class) {
            return;
        }

        for (String key : object.keySet()) {
            Object child = object.get(key);
            validateValue(packFolder, file, path + "." + key, child, valueType, null, null, report);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void validateEnum(File packFolder, File file, String path, Object value, Class<?> expectedType, PackValidationReport report) {
        if (!(value instanceof String text)) {
            report.addIssue(packFolder, file, path, "Expected enum string for " + expectedType.getSimpleName());
            return;
        }

        try {
            Enum.valueOf((Class<? extends Enum>) expectedType, text);
        } catch (Throwable e) {
            report.addIssue(packFolder, file, path, "Unknown enum value \"" + text + "\" for " + expectedType.getSimpleName());
        }
    }

    @SuppressWarnings("unchecked")
    private void validateKeyed(File packFolder, File file, String path, Object value, Class<?> expectedType, PackValidationReport report) {
        if (!(value instanceof String text)) {
            report.addIssue(packFolder, file, path, "Expected namespaced key string for " + expectedType.getSimpleName());
            return;
        }

        NamespacedKey key = NamespacedKey.fromString(text);
        if (key == null) {
            report.addIssue(packFolder, file, path, "Invalid namespaced key format \"" + text + "\"");
            return;
        }

        KeyedRegistry<Object> registry;
        try {
            registry = RegistryUtil.lookup((Class<Object>) expectedType);
        } catch (Throwable e) {
            report.addIssue(packFolder, file, path, "Unable to resolve keyed registry for " + expectedType.getSimpleName() + ": " + simpleMessage(e));
            return;
        }

        if (registry.isEmpty()) {
            return;
        }

        if (registry.get(key) == null) {
            report.addIssue(packFolder, file, path, "Unknown registry key \"" + text + "\" for " + expectedType.getSimpleName());
        }
    }

    private Map<String, Field> getSerializableFields(Class<?> type) {
        return fieldCache.computeIfAbsent(type, this::buildSerializableFields);
    }

    private Map<String, Field> buildSerializableFields(Class<?> type) {
        Map<String, Field> fields = new LinkedHashMap<>();
        Class<?> cursor = type;

        while (cursor != null && cursor != Object.class) {
            Field[] declared = cursor.getDeclaredFields();
            for (Field field : declared) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
                    continue;
                }

                fields.putIfAbsent(field.getName(), field);
            }
            cursor = cursor.getSuperclass();
        }

        return fields;
    }

    private Class<?> resolveCollectionElementType(Type genericType, Field sourceField) {
        if (sourceField != null) {
            ArrayType arrayType = sourceField.getDeclaredAnnotation(ArrayType.class);
            if (arrayType != null && arrayType.type() != Object.class) {
                return arrayType.type();
            }
        }

        if (genericType instanceof ParameterizedType parameterizedType) {
            Type[] arguments = parameterizedType.getActualTypeArguments();
            if (arguments.length > 0) {
                Class<?> resolved = resolveType(arguments[0]);
                if (resolved != null) {
                    return resolved;
                }
            }
        }

        return Object.class;
    }

    private Class<?> resolveMapValueType(Type genericType) {
        if (genericType instanceof ParameterizedType parameterizedType) {
            Type[] arguments = parameterizedType.getActualTypeArguments();
            if (arguments.length > 1) {
                Class<?> resolved = resolveType(arguments[1]);
                if (resolved != null) {
                    return resolved;
                }
            }
        }

        return Object.class;
    }

    private Class<?> resolveType(Type type) {
        if (type instanceof Class<?> clazz) {
            return normalizeType(clazz);
        }

        if (type instanceof ParameterizedType parameterizedType && parameterizedType.getRawType() instanceof Class<?> clazz) {
            return normalizeType(clazz);
        }

        if (type instanceof WildcardType wildcardType) {
            Type[] upperBounds = wildcardType.getUpperBounds();
            if (upperBounds.length > 0) {
                return resolveType(upperBounds[0]);
            }
        }

        if (type instanceof GenericArrayType genericArrayType) {
            Class<?> component = resolveType(genericArrayType.getGenericComponentType());
            if (component != null) {
                return normalizeType(Array.newInstance(component, 0).getClass());
            }
        }

        return null;
    }

    private Class<?> normalizeType(Class<?> type) {
        if (type == null) {
            return Object.class;
        }

        if (!type.isPrimitive()) {
            return type;
        }

        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == boolean.class) return Boolean.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private boolean shouldValidateNestedObject(Class<?> type) {
        String name = type.getName();
        return name.startsWith("art.arcane.iris.");
    }

    private boolean isKeyedType(Class<?> type) {
        return keyedTypeCache.computeIfAbsent(type, this::resolveKeyedType);
    }

    @SuppressWarnings("unchecked")
    private boolean resolveKeyedType(Class<?> type) {
        try {
            KeyedRegistry<Object> registry = RegistryUtil.lookup((Class<Object>) type);
            return !registry.isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String simpleMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return throwable.getClass().getSimpleName();
        }

        return throwable.getClass().getSimpleName() + ": " + message;
    }

    private String relativePath(File packFolder, File file) {
        try {
            Path packPath = packFolder.toPath();
            Path filePath = file.toPath();
            return packPath.relativize(filePath).toString().replace(File.separatorChar, '/');
        } catch (Throwable ignored) {
            return file.getPath();
        }
    }

    private final class PackValidationReport {
        private final String packName;
        private final int maxIssues;
        private final List<String> sampleIssues;
        private int filesChecked;
        private int errorCount;

        private PackValidationReport(String packName, int maxIssues) {
            this.packName = packName;
            this.maxIssues = maxIssues;
            this.sampleIssues = new ArrayList<>();
        }

        private void addIssue(File packFolder, File file, String path, String message) {
            errorCount++;
            if (sampleIssues.size() >= maxIssues) {
                return;
            }

            String relative = relativePath(packFolder, file);
            sampleIssues.add(relative + " " + path + " -> " + message);
        }
    }
}
