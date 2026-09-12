package art.arcane.iris.pack;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.image.CompiledIrisImageMap;
import art.arcane.iris.generation.image.IrisImageMapCompiler;
import art.arcane.iris.generation.image.IrisImageMapValidationException;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeGeneratorLink;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionStack;
import art.arcane.iris.generation.noise.IrisExpression;
import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.generation.image.IrisImage;
import art.arcane.iris.generation.image.IrisImageMap;
import art.arcane.iris.generation.image.IrisImageMapApplication;
import art.arcane.iris.generation.image.IrisImageMapBinding;
import art.arcane.iris.generation.image.IrisImageMapMask;
import art.arcane.iris.generation.image.IrisImageMapOutOfBounds;
import art.arcane.iris.generation.image.IrisImageMapType;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.iris.world.IrisWorldBoundary;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

final class PackImageMapValidator {
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private PackImageMapValidator() {
    }

    static Validation validate(File packFolder, File[] dimensionFiles, boolean validateLiveRegistries) {
        Set<String> blockingErrors = new LinkedHashSet<>();
        Set<String> warnings = new LinkedHashSet<>();
        try {
            if (!requiresValidation(packFolder)) {
                return new Validation(List.of(), List.of());
            }
        } catch (IOException error) {
            IrisLogging.reportError("Image-map feature detection failed for '" + packFolder.getName() + "'.", error);
            blockingErrors.add("Image-map validation could not inspect pack '" + packFolder.getName()
                    + "': " + failureMessage(error) + ".");
            return new Validation(List.copyOf(blockingErrors), List.of());
        }
        IrisData data = null;
        try {
            data = IrisData.openDatapackCompiler(packFolder);
            Map<String, MapResource> resources = compileResources(data, blockingErrors);
            PlatformRegistries registries = liveRegistries(validateLiveRegistries);
            validateDimensions(packFolder, dimensionFiles, data, resources, registries, blockingErrors, warnings);
        } catch (Throwable error) {
            IrisLogging.reportError("Image-map pack validation failed for '" + packFolder.getName() + "'.", error);
            blockingErrors.add("Image-map validation could not inspect pack '" + packFolder.getName()
                    + "': " + failureMessage(error) + ".");
        } finally {
            if (data != null) {
                try {
                    data.close();
                } catch (Throwable error) {
                    IrisLogging.reportError("Failed to close detached image-map validation data for '"
                            + packFolder.getName() + "'.", error);
                    blockingErrors.add("Image-map validation could not release detached pack data: "
                            + failureMessage(error) + ".");
                }
            }
        }
        return new Validation(List.copyOf(blockingErrors), List.copyOf(warnings));
    }

    private static boolean requiresValidation(File packFolder) throws IOException {
        File imageMapsFolder = new File(packFolder, "image-maps");
        if (imageMapsFolder.isDirectory()) {
            try (Stream<Path> paths = Files.walk(imageMapsFolder.toPath())) {
                if (paths.anyMatch(path -> Files.isRegularFile(path)
                        && path.getFileName().toString().endsWith(".json"))) {
                    return true;
                }
            }
        }
        try (Stream<Path> paths = Files.walk(packFolder.toPath())) {
            List<Path> jsonFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .toList();
            for (Path jsonFile : jsonFiles) {
                try {
                    if (containsImageMapKey(JsonParser.parseString(Files.readString(jsonFile)))) {
                        return true;
                    }
                } catch (JsonParseException error) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsImageMapKey(JsonElement element) {
        if (element == null || element.isJsonNull() || element.isJsonPrimitive()) {
            return false;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (containsImageMapKey(child)) {
                    return true;
                }
            }
            return false;
        }
        JsonObject object = element.getAsJsonObject();
        if (object.has("imageMap") || object.has("imageMaps")) {
            return true;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (containsImageMapKey(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, MapResource> compileResources(IrisData data, Set<String> blockingErrors) {
        String[] possibleKeys = data.getImageMapLoader().getPossibleKeys();
        Arrays.sort(possibleKeys);
        Set<String> imageKeys = new LinkedHashSet<>(Arrays.asList(data.getImageLoader().getPossibleKeys()));
        Map<String, MapResource> resources = new LinkedHashMap<>();
        for (String mapKey : possibleKeys) {
            IrisImageMap definition = data.getImageMapLoader().load(mapKey, false);
            if (definition == null) {
                blockingErrors.add("Image-map resource '" + mapKey + "' is invalid and could not be parsed.");
                resources.put(mapKey, new MapResource(null, null));
                continue;
            }
            String source = normalized(definition.getSource());
            if (source == null) {
                blockingErrors.add("Image-map resource '" + mapKey + "' source must not be blank.");
                resources.put(mapKey, new MapResource(definition, null));
                continue;
            }
            if (!imageKeys.contains(source)) {
                blockingErrors.add("Image-map resource '" + mapKey + "' references missing PNG source '"
                        + source + "'.");
                resources.put(mapKey, new MapResource(definition, null));
                continue;
            }
            File sourceFile = data.getImageLoader().findFile(source);
            if (sourceFile == null || !sourceFile.isFile()) {
                blockingErrors.add("Image-map resource '" + mapKey + "' references missing PNG source '"
                        + source + "'.");
                resources.put(mapKey, new MapResource(definition, null));
                continue;
            }
            try {
                if (!hasPngSignature(sourceFile)) {
                    blockingErrors.add("Image-map resource '" + mapKey + "' source '" + source
                            + "' is not a PNG file.");
                }
            } catch (IOException error) {
                blockingErrors.add("Image-map resource '" + mapKey + "' source '" + source
                        + "' could not be read: " + failureMessage(error) + ".");
            }
            IrisImage image = data.getImageLoader().load(source, false);
            if (image == null) {
                blockingErrors.add("Image-map resource '" + mapKey + "' source '" + source
                        + "' is corrupt or unsupported.");
                resources.put(mapKey, new MapResource(definition, null));
                continue;
            }
            CompiledIrisImageMap compiled = null;
            try {
                compiled = IrisImageMapCompiler.compile(definition, image).withoutDecodedValues();
            } catch (IrisImageMapValidationException validation) {
                for (String diagnostic : validation.getDiagnostics()) {
                    blockingErrors.add("Image-map resource '" + mapKey + "': " + diagnostic + ".");
                }
            } finally {
                data.getImageLoader().unload(source);
            }
            resources.put(mapKey, new MapResource(definition, compiled));
        }
        return resources;
    }

    private static void validateDimensions(
            File packFolder,
            File[] dimensionFiles,
            IrisData data,
            Map<String, MapResource> resources,
            PlatformRegistries registries,
            Set<String> blockingErrors,
            Set<String> warnings
    ) {
        File[] sortedFiles = Arrays.copyOf(dimensionFiles, dimensionFiles.length);
        Arrays.sort(sortedFiles, (File first, File second) -> first.getPath().compareTo(second.getPath()));
        Map<String, IrisDimension> dimensions = new LinkedHashMap<>();
        Set<String> bindingReadyDimensions = new LinkedHashSet<>();
        File dimensionsFolder = new File(packFolder, PackValidator.DIMENSIONS_FOLDER);
        for (File dimensionFile : sortedFiles) {
            String dimensionKey = PackValidationIo.deriveKey(dimensionsFolder, dimensionFile);
            JSONObject raw = PackValidationIo.readJson(dimensionFile);
            if (raw == null) {
                continue;
            }
            IrisDimension dimension = data.getDimensionLoader().load(dimensionKey, false);
            if (dimension == null) {
                continue;
            }
            dimensions.put(dimensionKey, dimension);
            if (raw.has("imageMaps")) {
                JSONArray rawBindings = raw.optJSONArray("imageMaps");
                if (rawBindings == null) {
                    blockingErrors.add("Dimension '" + dimensionKey + "' imageMaps must be an array.");
                } else if (dimension.getImageMaps() == null) {
                    blockingErrors.add("Dimension '" + dimensionKey
                            + "' imageMaps is invalid and could not be parsed.");
                } else {
                    bindingReadyDimensions.add(dimensionKey);
                }
            } else {
                bindingReadyDimensions.add(dimensionKey);
            }
            validateGeneratorStyleReferences(dimensionKey, dimension, data, resources, blockingErrors);
        }
        Map<String, List<Map.Entry<String, IrisDimension>>> upperParents = new TreeMap<>();
        Map<String, List<Map.Entry<String, IrisDimension>>> stackParents = new TreeMap<>();
        for (Map.Entry<String, IrisDimension> entry : dimensions.entrySet()) {
            String upperKey = normalized(entry.getValue().getUpperDimension());
            if (upperKey != null && !upperKey.equalsIgnoreCase("none")) {
                upperParents.computeIfAbsent(upperKey, ignored -> new ArrayList<>()).add(entry);
            }
            IrisDimensionStack stack = entry.getValue().getDimensionStack();
            if (stack == null || stack.getDimensions() == null) {
                continue;
            }
            Set<String> stackKeys = new LinkedHashSet<>();
            for (String stackKey : stack.getDimensions()) {
                String normalizedKey = normalized(stackKey);
                if (normalizedKey == null || normalizedKey.equals(entry.getKey())
                        || !stackKeys.add(normalizedKey)) {
                    continue;
                }
                stackParents.computeIfAbsent(normalizedKey, ignored -> new ArrayList<>()).add(entry);
            }
        }
        for (Map.Entry<String, IrisDimension> entry : dimensions.entrySet()) {
            if (!bindingReadyDimensions.contains(entry.getKey())) {
                continue;
            }
            List<Map.Entry<String, IrisDimension>> parents = upperParents.get(entry.getKey());
            List<Map.Entry<String, IrisDimension>> stackHosts = stackParents.get(entry.getKey());
            boolean hasUpperParents = parents != null && !parents.isEmpty();
            boolean hasStackHosts = stackHosts != null && !stackHosts.isEmpty();
            if (!hasUpperParents && !hasStackHosts) {
                validateBindings(packFolder, "Dimension '" + entry.getKey() + "'", entry.getValue(),
                        entry.getValue().getWorldBoundary(), data, resources, registries, blockingErrors, warnings);
                continue;
            }
            if (entry.getValue().getWorldBoundary() != null) {
                validateBindings(packFolder, "Dimension '" + entry.getKey() + "'", entry.getValue(),
                        entry.getValue().getWorldBoundary(), data, resources, registries, blockingErrors, warnings);
            }
            if (hasUpperParents) {
                parents.sort(Map.Entry.comparingByKey());
                for (Map.Entry<String, IrisDimension> parent : parents) {
                    validateBindings(packFolder,
                            "Dimension '" + parent.getKey() + "' upper dimension '" + entry.getKey() + "'",
                            entry.getValue(), parent.getValue().getWorldBoundary(), data, resources, registries,
                            blockingErrors, warnings);
                }
            }
            if (hasStackHosts) {
                stackHosts.sort(Map.Entry.comparingByKey());
                for (Map.Entry<String, IrisDimension> host : stackHosts) {
                    validateBindings(packFolder,
                            "Dimension '" + host.getKey() + "' dimension stack layer '" + entry.getKey() + "'",
                            entry.getValue(), host.getValue().getWorldBoundary(), data, resources, registries,
                            blockingErrors, warnings);
                }
            }
        }
    }

    private static void validateBindings(
            File packFolder,
            String dimensionContext,
            IrisDimension dimension,
            IrisWorldBoundary enforcedBoundary,
            IrisData data,
            Map<String, MapResource> resources,
            PlatformRegistries registries,
            Set<String> blockingErrors,
            Set<String> warnings
    ) {
        Map<String, IrisImageMapBinding> bindings = new LinkedHashMap<>();
        Map<IrisImageMapApplication, String> applications = new EnumMap<>(IrisImageMapApplication.class);
        List<IrisImageMapBinding> declared = dimension.getImageMaps();
        Set<String> regionKeys = allRegionKeys(dimension, resources);
        for (int index = 0; index < declared.size(); index++) {
            IrisImageMapBinding binding = declared.get(index);
            String indexContext = dimensionContext + " imageMaps[" + index + "]";
            if (binding == null) {
                blockingErrors.add(indexContext + " must be an object.");
                continue;
            }
            String key = normalized(binding.getKey());
            if (key == null) {
                blockingErrors.add(indexContext + ".key must not be blank.");
            } else if (bindings.putIfAbsent(key, binding) != null) {
                blockingErrors.add(dimensionContext + " declares duplicate image-map key '"
                        + key + "'.");
            }
            IrisImageMapApplication application = binding.getApplication();
            if (application == null) {
                blockingErrors.add(indexContext + ".application is required.");
            } else if (application != IrisImageMapApplication.MASK
                    && application != IrisImageMapApplication.CUSTOM) {
                String duplicate = applications.putIfAbsent(application, key == null ? indexContext : key);
                if (duplicate != null) {
                    blockingErrors.add(dimensionContext + " declares more than one "
                            + application + " image-map binding.");
                }
            }
            validateMapReference(dimensionContext, indexContext, binding, key, dimension, enforcedBoundary, data,
                    regionKeys, resources, registries, packFolder, blockingErrors, warnings);
        }
        for (int index = 0; index < declared.size(); index++) {
            IrisImageMapBinding binding = declared.get(index);
            if (binding != null) {
                validateMasks(dimensionContext, index, binding, bindings, blockingErrors);
            }
        }
    }

    private static void validateMapReference(
            String dimensionContext,
            String indexContext,
            IrisImageMapBinding binding,
            String bindingKey,
            IrisDimension dimension,
            IrisWorldBoundary enforcedBoundary,
            IrisData data,
            Set<String> regionKeys,
            Map<String, MapResource> resources,
            PlatformRegistries registries,
            File packFolder,
            Set<String> blockingErrors,
            Set<String> warnings
    ) {
        String mapKey = normalized(binding.getMap());
        if (mapKey == null) {
            blockingErrors.add(indexContext + ".map must not be blank.");
            return;
        }
        MapResource resource = resources.get(mapKey);
        if (resource == null) {
            blockingErrors.add(indexContext + " references missing image-map resource '" + mapKey + "'.");
            return;
        }
        IrisImageMap definition = resource.definition();
        IrisImageMapApplication application = binding.getApplication();
        if (definition == null || application == null) {
            return;
        }
        IrisImageMapType type = definition.getType();
        String context = dimensionContext + " image-map '"
                + (bindingKey == null ? mapKey : bindingKey) + "'";
        if (type != null && !compatible(application, type)) {
            blockingErrors.add(context + " type " + type + " is incompatible with " + application + ".");
        }
        if (application == IrisImageMapApplication.MASK
                && binding.getMasks() != null && !binding.getMasks().isEmpty()) {
            blockingErrors.add(context + " is a MASK binding and cannot reference additional masks.");
        }
        validateLegendTargets(packFolder, context, regionKeys, data, application, definition,
                registries, blockingErrors);
        if (application == IrisImageMapApplication.TERRAIN_HEIGHT) {
            validateTerrainHeightRange(context, dimension, definition, blockingErrors);
        }
        if (resource.compiled() != null && isGenerationApplication(application)) {
            validateBoundaryCoverage(context, enforcedBoundary, definition,
                    resource.compiled(), blockingErrors, warnings);
        }
    }

    private static void validateMasks(
            String dimensionContext,
            int bindingIndex,
            IrisImageMapBinding binding,
            Map<String, IrisImageMapBinding> bindings,
            Set<String> blockingErrors
    ) {
        if (binding.getMasks() == null) {
            return;
        }
        String bindingKey = normalized(binding.getKey());
        String context = bindingKey == null
                ? dimensionContext + " imageMaps[" + bindingIndex + "]"
                : dimensionContext + " image-map '" + bindingKey + "'";
        for (int maskIndex = 0; maskIndex < binding.getMasks().size(); maskIndex++) {
            IrisImageMapMask mask = binding.getMasks().get(maskIndex);
            String maskContext = context + " masks[" + maskIndex + "]";
            if (mask == null) {
                blockingErrors.add(maskContext + " must be an object.");
                continue;
            }
            String maskKey = normalized(mask.getMap());
            if (maskKey == null) {
                blockingErrors.add(maskContext + ".map must not be blank.");
            } else {
                IrisImageMapBinding referenced = bindings.get(maskKey);
                if (referenced == null) {
                    blockingErrors.add(maskContext + " references missing MASK binding '" + maskKey + "'.");
                } else if (referenced.getApplication() != IrisImageMapApplication.MASK) {
                    blockingErrors.add(maskContext + " references '" + maskKey + "', which is not a MASK binding.");
                }
            }
            if (mask.getOperation() == null) {
                blockingErrors.add(maskContext + ".operation is required.");
            }
            if (!unitRange(mask.getThreshold())) {
                blockingErrors.add(maskContext + ".threshold must be finite and within 0..1.");
            }
            if (!unitRange(mask.getFalloff())) {
                blockingErrors.add(maskContext + ".falloff must be finite and within 0..1.");
            }
        }
    }

    private static void validateLegendTargets(
            File packFolder,
            String context,
            Set<String> regionKeys,
            IrisData data,
            IrisImageMapApplication application,
            IrisImageMap definition,
            PlatformRegistries registries,
            Set<String> blockingErrors
    ) {
        if (definition.getType() != IrisImageMapType.COLOR_MAP) {
            return;
        }
        Set<String> targets = legendTargets(definition);
        if (application == IrisImageMapApplication.BIOME) {
            for (String target : targets) {
                String local = localIrisTarget(context, "biome", target, blockingErrors);
                if (local == null) {
                    continue;
                }
                File biomeFile = resourceFile(packFolder, "biomes", local);
                IrisBiome biome = data.getBiomeLoader().load(local, false);
                if (biomeFile == null || !biomeFile.isFile() || biome == null) {
                    blockingErrors.add(context + " references missing or invalid biome target '" + target + "'.");
                    continue;
                }
                validateBiomeRole(packFolder, context, regionKeys, local, blockingErrors);
            }
        } else if (application == IrisImageMapApplication.REGION) {
            for (String target : targets) {
                String local = localIrisTarget(context, "region", target, blockingErrors);
                if (local == null) {
                    continue;
                }
                File regionFile = resourceFile(packFolder, "regions", local);
                if (regionFile == null || !regionFile.isFile() || PackValidationIo.readJson(regionFile) == null) {
                    blockingErrors.add(context + " references missing or invalid region target '" + target + "'.");
                }
            }
        } else if (application == IrisImageMapApplication.SURFACE_BLOCK && registries != null) {
            for (String target : targets) {
                try {
                    PlatformBlockState block = registries.blockOrNull(target, false);
                    if (block == null) {
                        blockingErrors.add(context + " references unknown surface block target '" + target + "'.");
                    }
                } catch (Throwable error) {
                    IrisLogging.reportError("Could not validate image-map surface block target '" + target + "'.", error);
                    blockingErrors.add(context + " could not validate surface block target '" + target
                            + "': " + failureMessage(error) + ".");
                }
            }
            for (String issue : ContentKeyValidator.validateBlockStateProperties(registries, targets)) {
                blockingErrors.add(context + " surface block target is invalid: " + issue + ".");
            }
        }
    }

    private static void validateBiomeRole(
            File packFolder,
            String context,
            Set<String> regionKeys,
            String biomeKey,
            Set<String> blockingErrors
    ) {
        Set<String> roles = new LinkedHashSet<>();
        for (String regionKey : regionKeys) {
            File regionFile = resourceFile(packFolder, "regions", regionKey);
            JSONObject region = regionFile == null ? null : PackValidationIo.readJson(regionFile);
            if (region == null) {
                continue;
            }
            collectRole(region, "landBiomes", "LAND", biomeKey, roles);
            collectRole(region, "seaBiomes", "SEA", biomeKey, roles);
            collectRole(region, "shoreBiomes", "SHORE", biomeKey, roles);
        }
        if (roles.size() != 1) {
            blockingErrors.add(context + " biome target '" + biomeKey
                    + "' must occur in exactly one landBiomes, seaBiomes, or shoreBiomes role; found "
                    + roles + ".");
        }
    }

    private static void collectRole(
            JSONObject region,
            String field,
            String role,
            String biomeKey,
            Set<String> roles
    ) {
        JSONArray values = region.optJSONArray(field);
        if (values == null) {
            return;
        }
        for (int index = 0; index < values.length(); index++) {
            if (biomeKey.equals(values.optString(index, null))) {
                roles.add(role);
                return;
            }
        }
    }

    private static Set<String> allRegionKeys(
            IrisDimension dimension,
            Map<String, MapResource> resources
    ) {
        Set<String> regionKeys = new TreeSet<>();
        if (dimension.getRegions() != null) {
            for (String regionKey : dimension.getRegions()) {
                String normalized = normalized(regionKey);
                if (normalized != null) {
                    regionKeys.add(normalized);
                }
            }
        }
        for (IrisImageMapBinding binding : dimension.getImageMaps()) {
            if (binding == null || binding.getApplication() != IrisImageMapApplication.REGION) {
                continue;
            }
            MapResource resource = resources.get(normalized(binding.getMap()));
            if (resource == null || resource.definition() == null) {
                continue;
            }
            for (String target : legendTargets(resource.definition())) {
                String local = localIrisTargetOrNull(target);
                if (local != null) {
                    regionKeys.add(local);
                }
            }
        }
        return regionKeys;
    }

    private static void validateGeneratorStyleReferences(
            String dimensionKey,
            IrisDimension dimension,
            IrisData data,
            Map<String, MapResource> resources,
            Set<String> blockingErrors
    ) {
        Map<String, Object> roots = new TreeMap<>();
        roots.put("Dimension '" + dimensionKey + "'", dimension);
        for (IrisRegion region : dimension.getAllRegions(() -> data)) {
            if (region != null && normalized(region.getLoadKey()) != null) {
                roots.put("Region '" + region.getLoadKey() + "' reachable from dimension '" + dimensionKey + "'", region);
            }
        }
        Set<String> generatorKeys = new TreeSet<>();
        for (IrisBiome biome : dimension.getReachableBiomes(() -> data)) {
            if (biome == null || normalized(biome.getLoadKey()) == null) {
                continue;
            }
            roots.put("Biome '" + biome.getLoadKey() + "' reachable from dimension '" + dimensionKey + "'", biome);
            if (biome.getGenerators() == null) {
                continue;
            }
            for (IrisBiomeGeneratorLink link : biome.getGenerators()) {
                if (link == null) {
                    continue;
                }
                String generatorKey = normalized(link.getGenerator());
                generatorKeys.add(generatorKey == null ? "default" : generatorKey);
            }
        }
        Set<String> availableGeneratorKeys = new TreeSet<>(Arrays.asList(
                data.getGeneratorLoader().getPossibleKeys()));
        for (String generatorKey : generatorKeys) {
            if (!availableGeneratorKeys.contains(generatorKey)) {
                continue;
            }
            IrisGenerator generator = data.getGeneratorLoader().load(generatorKey, false);
            if (generator != null) {
                roots.put("Generator '" + generatorKey + "' reachable from dimension '" + dimensionKey + "'", generator);
            }
        }
        Set<String> visitedExpressions = new TreeSet<>();
        Set<String> availableExpressionKeys = new TreeSet<>(Arrays.asList(
                data.getExpressionLoader().getPossibleKeys()));
        for (Map.Entry<String, Object> entry : roots.entrySet()) {
            JSONObject root = new JSONObject(data.getGson().toJson(entry.getValue()));
            scanGeneratorStyles(entry.getKey(), root, data, resources, availableExpressionKeys,
                    visitedExpressions, blockingErrors);
        }
    }

    private static void scanGeneratorStyles(
            String path,
            Object value,
            IrisData data,
            Map<String, MapResource> resources,
            Set<String> availableExpressionKeys,
            Set<String> visitedExpressions,
            Set<String> blockingErrors
    ) {
        if (value instanceof JSONObject object) {
            if (isGeneratorStyleObject(object)) {
                validateGeneratorStyleReference(path, object, resources, blockingErrors);
                String expressionKey = normalized(object.optString("expression", null));
                if (expressionKey != null && availableExpressionKeys.contains(expressionKey)
                        && visitedExpressions.add(expressionKey)) {
                    IrisExpression expression = data.getExpressionLoader().load(expressionKey, false);
                    if (expression != null) {
                        JSONObject expressionRoot = new JSONObject(data.getGson().toJson(expression));
                        scanGeneratorStyles("Expression '" + expressionKey + "' reachable from " + path,
                                expressionRoot, data, resources, availableExpressionKeys,
                                visitedExpressions, blockingErrors);
                    }
                }
            }
            Set<String> keys = new TreeSet<>(object.keySet());
            for (String key : keys) {
                scanGeneratorStyles(path + "." + key, object.opt(key), data, resources,
                        availableExpressionKeys, visitedExpressions, blockingErrors);
            }
            return;
        }
        if (value instanceof JSONArray array) {
            for (int index = 0; index < array.length(); index++) {
                scanGeneratorStyles(path + "[" + index + "]", array.opt(index), data, resources,
                        availableExpressionKeys, visitedExpressions, blockingErrors);
            }
        }
    }

    private static boolean isGeneratorStyleObject(JSONObject object) {
        return object.has("cellularFrequency")
                && object.has("cellularZoom")
                && object.has("zoom")
                && object.has("cacheSize");
    }

    private static void validateGeneratorStyleReference(
            String context,
            JSONObject style,
            Map<String, MapResource> resources,
            Set<String> blockingErrors
    ) {
        if (!style.has("imageMap")) {
            return;
        }
        String mapKey = normalized(style.optString("imageMap", null));
        if (mapKey == null) {
            blockingErrors.add(context + " generator style imageMap must not be blank.");
            return;
        }
        MapResource resource = resources.get(mapKey);
        if (resource == null) {
            blockingErrors.add(context + " generator style references missing image-map resource '" + mapKey + "'.");
            return;
        }
        IrisImageMap definition = resource.definition();
        if (definition == null || definition.getType() == null) {
            return;
        }
        if (definition.getType() == IrisImageMapType.COLOR_MAP) {
            blockingErrors.add(context + " generator style image-map '" + mapKey
                    + "' must use a scalar map type, not COLOR_MAP.");
        }
        if (definition.getOutOfBounds() == IrisImageMapOutOfBounds.ERROR) {
            blockingErrors.add(context + " generator style image-map '" + mapKey
                    + "' cannot use outOfBounds=ERROR because its transformed sampling domain cannot be proven finite;"
                    + " use FALLBACK, CLAMP, REPEAT, or MIRROR.");
        }
    }

    private static void validateTerrainHeightRange(
            String context,
            IrisDimension dimension,
            IrisImageMap definition,
            Set<String> blockingErrors
    ) {
        if (definition.getType() != IrisImageMapType.GRAYSCALE_HEIGHT
                && definition.getType() != IrisImageMapType.RGB_HEIGHT) {
            return;
        }
        IrisRange dimensionHeight = dimension.getDimensionHeight();
        double minimumHeight = definition.getMinimumHeight();
        double maximumHeight = definition.getMaximumHeight();
        double verticalOffset = definition.getVerticalOffset();
        if (dimensionHeight == null
                || !Double.isFinite(minimumHeight)
                || !Double.isFinite(maximumHeight)
                || !Double.isFinite(verticalOffset)
                || maximumHeight < minimumHeight) {
            return;
        }
        double effectiveMinimum = minimumHeight + verticalOffset;
        double effectiveMaximum = maximumHeight + verticalOffset;
        if (definition.isClamp()) {
            effectiveMinimum = Math.max(minimumHeight, Math.min(maximumHeight, effectiveMinimum));
            effectiveMaximum = Math.max(minimumHeight, Math.min(maximumHeight, effectiveMaximum));
        }
        if (effectiveMinimum < dimensionHeight.getMin() || effectiveMaximum > dimensionHeight.getMax()) {
            blockingErrors.add(context + " produces world Y " + effectiveMinimum + ".." + effectiveMaximum
                    + " after verticalOffset and clamp; the owning dimension allows "
                    + dimensionHeight.getMin() + ".." + dimensionHeight.getMax() + ".");
        }
    }

    private static void validateBoundaryCoverage(
            String context,
            IrisWorldBoundary configuredBoundary,
            IrisImageMap definition,
            CompiledIrisImageMap compiled,
            Set<String> blockingErrors,
            Set<String> warnings
    ) {
        boolean requiresCoverage = definition.getOutOfBounds() == IrisImageMapOutOfBounds.ERROR;
        if (configuredBoundary == null) {
            if (requiresCoverage) {
                blockingErrors.add(context
                        + " uses outOfBounds=ERROR and requires a configured worldBoundary.");
            }
            return;
        }
        IrisWorldBoundary boundary;
        try {
            boundary = IrisWorldBoundary.snapshot(configuredBoundary);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        double insetX = boundaryInset(boundary.minimumX(), boundary.maximumX());
        double insetZ = boundaryInset(boundary.minimumZ(), boundary.maximumZ());
        double minimumX = boundary.minimumX() + insetX;
        double maximumX = boundary.maximumX() - insetX;
        double minimumZ = boundary.minimumZ() + insetZ;
        double maximumZ = boundary.maximumZ() - insetZ;
        boolean covers = compiled.containsWorldForSampling(minimumX, minimumZ)
                && compiled.containsWorldForSampling(minimumX, maximumZ)
                && compiled.containsWorldForSampling(maximumX, minimumZ)
                && compiled.containsWorldForSampling(maximumX, maximumZ);
        if (!covers) {
            String issue = context + " source footprint does not cover the configured worldBoundary.";
            if (requiresCoverage) {
                blockingErrors.add(issue);
            } else {
                warnings.add(issue);
            }
        }
    }

    private static Set<String> legendTargets(IrisImageMap definition) {
        Set<String> targets = new TreeSet<>();
        if (definition.getColors() != null) {
            for (String target : definition.getColors().values()) {
                String normalized = normalized(target);
                if (normalized != null) {
                    targets.add(normalized);
                }
            }
        }
        String fallback = normalized(definition.getFallbackTarget());
        if (fallback != null) {
            targets.add(fallback);
        }
        return targets;
    }

    private static String localIrisTarget(
            String context,
            String targetType,
            String target,
            Set<String> blockingErrors
    ) {
        int separator = target.indexOf(':');
        String local = target;
        if (separator >= 0) {
            if (!target.startsWith("iris:") || separator == target.length() - 1) {
                blockingErrors.add(context + " " + targetType
                        + " target must be a bare pack key or use the iris: namespace, got '" + target + "'.");
                return null;
            }
            local = target.substring(separator + 1);
        }
        if (local.isBlank() || local.startsWith("/") || local.contains("\\")) {
            blockingErrors.add(context + " " + targetType + " target '" + target + "' is not a valid pack key.");
            return null;
        }
        for (String segment : local.split("/")) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                blockingErrors.add(context + " " + targetType + " target '" + target + "' is not a valid pack key.");
                return null;
            }
        }
        return local;
    }

    private static String localIrisTargetOrNull(String target) {
        int separator = target.indexOf(':');
        if (separator < 0) {
            return target;
        }
        if (!target.startsWith("iris:") || separator == target.length() - 1) {
            return null;
        }
        return target.substring(separator + 1);
    }

    private static File resourceFile(File packFolder, String folder, String key) {
        String normalizedKey = normalized(key);
        if (normalizedKey == null || normalizedKey.startsWith("/") || normalizedKey.contains("\\")) {
            return null;
        }
        File root = new File(packFolder, folder);
        File target = new File(root, normalizedKey + ".json");
        try {
            if (!target.getCanonicalFile().toPath().startsWith(root.getCanonicalFile().toPath())) {
                return null;
            }
        } catch (IOException error) {
            return null;
        }
        return target;
    }

    private static PlatformRegistries liveRegistries(boolean validateLiveRegistries) {
        if (!validateLiveRegistries || !IrisPlatforms.isBound()) {
            return null;
        }
        try {
            PlatformRegistries registries = IrisPlatforms.get().registries();
            if (registries == null || registries.blockKeys() == null || registries.blockKeys().isEmpty()) {
                return null;
            }
            return registries;
        } catch (Throwable error) {
            IrisLogging.reportError("Could not read live block registries for image-map validation.", error);
            return null;
        }
    }

    private static boolean compatible(IrisImageMapApplication application, IrisImageMapType type) {
        return switch (application) {
            case TERRAIN_HEIGHT -> type == IrisImageMapType.GRAYSCALE_HEIGHT
                    || type == IrisImageMapType.RGB_HEIGHT;
            case BIOME, REGION, SURFACE_BLOCK -> type == IrisImageMapType.COLOR_MAP;
            case MASK -> type == IrisImageMapType.BINARY_MASK
                    || type == IrisImageMapType.GRAYSCALE_MASK
                    || type == IrisImageMapType.ALPHA_MASK;
            case CUSTOM -> true;
        };
    }

    private static boolean isGenerationApplication(IrisImageMapApplication application) {
        return application != IrisImageMapApplication.CUSTOM;
    }

    private static double boundaryInset(double minimum, double maximum) {
        return Math.max(Math.ulp(minimum), Math.ulp(maximum)) * 8D;
    }

    private static boolean hasPngSignature(File file) throws IOException {
        byte[] actual;
        try (InputStream input = Files.newInputStream(file.toPath())) {
            actual = input.readNBytes(PNG_SIGNATURE.length);
        }
        return Arrays.equals(PNG_SIGNATURE, actual);
    }

    private static boolean unitRange(double value) {
        return Double.isFinite(value) && value >= 0D && value <= 1D;
    }

    private static String normalized(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String failureMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    record Validation(List<String> errors, List<String> warnings) {
    }

    private record MapResource(IrisImageMap definition, CompiledIrisImageMap compiled) {
    }
}
