package art.arcane.iris.generation.image;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.block.B;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class IrisImageMapRuntime {
    private final int minimumWorldHeight;
    private final Map<String, RuntimeBinding> bindings;
    private final Map<IrisImageMapApplication, RuntimeBinding> applications;
    private final Set<IrisRegion> mappedRegions;
    private final Set<IrisBiome> mappedBiomes;

    private IrisImageMapRuntime(
            int minimumWorldHeight,
            Map<String, RuntimeBinding> bindings,
            Map<IrisImageMapApplication, RuntimeBinding> applications,
            Set<IrisRegion> mappedRegions,
            Set<IrisBiome> mappedBiomes
    ) {
        this.minimumWorldHeight = minimumWorldHeight;
        this.bindings = Map.copyOf(bindings);
        this.applications = Map.copyOf(applications);
        this.mappedRegions = Set.copyOf(mappedRegions);
        this.mappedBiomes = Set.copyOf(mappedBiomes);
    }

    public static IrisImageMapRuntime compile(Engine engine) {
        Objects.requireNonNull(engine, "Image-map runtime requires an engine");
        return compile(engine.getData(), engine.getDimension(), engine.getMinHeight());
    }

    public static IrisImageMapRuntime compile(IrisData data, IrisDimension dimension, int minimumWorldHeight) {
        Objects.requireNonNull(data, "Image-map runtime requires Iris data");
        Objects.requireNonNull(dimension, "Image-map runtime requires a dimension");
        Map<String, RuntimeBinding> bindings = new LinkedHashMap<>();
        Map<IrisImageMapApplication, RuntimeBinding> applications = new EnumMap<>(IrisImageMapApplication.class);
        Set<IrisRegion> mappedRegions = new LinkedHashSet<>();
        Set<IrisBiome> mappedBiomes = new LinkedHashSet<>();

        for (IrisImageMapBinding binding : dimension.getImageMaps()) {
            RuntimeBinding runtimeBinding = compileBinding(binding, data, dimension, mappedRegions, mappedBiomes);
            RuntimeBinding duplicate = bindings.putIfAbsent(runtimeBinding.key, runtimeBinding);
            if (duplicate != null) {
                throw validation("Duplicate dimension image-map key '" + runtimeBinding.key + "'");
            }
            IrisImageMapApplication application = runtimeBinding.application;
            if (application != IrisImageMapApplication.MASK && application != IrisImageMapApplication.CUSTOM) {
                RuntimeBinding applicationDuplicate = applications.putIfAbsent(application, runtimeBinding);
                if (applicationDuplicate != null) {
                    throw validation("Dimension declares more than one " + application + " image-map binding");
                }
            }
        }

        for (RuntimeBinding binding : bindings.values()) {
            binding.masks = resolveMasks(binding, bindings);
        }

        return new IrisImageMapRuntime(
                minimumWorldHeight, bindings, applications, mappedRegions, mappedBiomes
        );
    }

    public boolean has(IrisImageMapApplication application) {
        return applications.containsKey(application);
    }

    public Set<IrisRegion> getMappedRegions() {
        return mappedRegions;
    }

    public Set<IrisBiome> getMappedBiomes() {
        return mappedBiomes;
    }

    public double sampleTerrainHeight(double worldX, double worldZ, double proceduralLocalHeight) {
        RuntimeBinding binding = applications.get(IrisImageMapApplication.TERRAIN_HEIGHT);
        if (binding == null) {
            return proceduralLocalHeight;
        }
        double mappedLocalHeight = binding.compiled.sampleHeight(worldX, worldZ) - minimumWorldHeight;
        double weight = binding.maskWeight(worldX, worldZ);
        return blendTerrainHeight(mappedLocalHeight, proceduralLocalHeight, weight);
    }

    public IrisRegion sampleRegion(double worldX, double worldZ) {
        RuntimeBinding binding = applications.get(IrisImageMapApplication.REGION);
        if (binding == null || !selectCategorical(binding.maskWeight(worldX, worldZ))) {
            return null;
        }
        String target = binding.compiled.sampleTarget(worldX, worldZ);
        return target == null ? null : binding.regions.get(target);
    }

    public IrisBiome sampleBiome(double worldX, double worldZ) {
        RuntimeBinding binding = applications.get(IrisImageMapApplication.BIOME);
        if (binding == null || !selectCategorical(binding.maskWeight(worldX, worldZ))) {
            return null;
        }
        String target = binding.compiled.sampleTarget(worldX, worldZ);
        return target == null ? null : binding.biomes.get(target);
    }

    public PlatformBlockState sampleSurfaceBlock(double worldX, double worldZ) {
        RuntimeBinding binding = applications.get(IrisImageMapApplication.SURFACE_BLOCK);
        if (binding == null || !selectCategorical(binding.maskWeight(worldX, worldZ))) {
            return null;
        }
        String target = binding.compiled.sampleTarget(worldX, worldZ);
        return target == null ? null : binding.blocks.get(target);
    }

    public CompiledIrisImageMap getCompiled(String key) {
        RuntimeBinding binding = bindings.get(key);
        return binding == null ? null : binding.compiled;
    }

    public static double blendTerrainHeight(double mappedLocalHeight, double proceduralLocalHeight, double weight) {
        return proceduralLocalHeight + ((mappedLocalHeight - proceduralLocalHeight) * weight);
    }

    public static boolean selectCategorical(double weight) {
        return weight >= 0.5D;
    }

    private static RuntimeBinding compileBinding(
            IrisImageMapBinding binding,
            IrisData data,
            IrisDimension dimension,
            Set<IrisRegion> mappedRegions,
            Set<IrisBiome> mappedBiomes
    ) {
        if (binding == null) {
            throw validation("Dimension imageMaps contains a null binding");
        }
        String key = requireText(binding.getKey(), "Dimension image-map key");
        String mapKey = requireText(binding.getMap(), "Dimension image-map resource for '" + key + "'");
        IrisImageMapApplication application = binding.getApplication();
        if (application == null) {
            throw validation("Dimension image-map '" + key + "' requires an application");
        }
        IrisImageMap definition = data.getImageMapLoader().load(mapKey);
        if (definition == null) {
            throw validation("Dimension image-map '" + key + "' references missing image-map resource '" + mapKey + "'");
        }
        String source = requireText(definition.getSource(), "Image-map resource '" + mapKey + "' source");
        IrisImage image = data.getImageLoader().load(source);
        if (image == null) {
            throw validation("Image-map resource '" + mapKey + "' references missing or invalid PNG '" + source + "'");
        }
        CompiledIrisImageMap compiled;
        try {
            validateApplication(key, application, definition.getType(), binding.getMasks());
            compiled = CompiledIrisImageMap.compile(definition, image);
        } finally {
            data.getImageLoader().unload(source);
        }
        Map<String, IrisRegion> regions = new LinkedHashMap<>();
        Map<String, IrisBiome> biomes = new LinkedHashMap<>();
        Map<String, PlatformBlockState> blocks = new LinkedHashMap<>();
        Set<String> targets = legendTargets(definition);

        if (application == IrisImageMapApplication.REGION) {
            for (String target : targets) {
                IrisRegion region = data.getRegionLoader().load(localResourceKey(target));
                if (region == null) {
                    throw validation("Image-map '" + key + "' references missing region target '" + target + "'");
                }
                regions.put(target, region);
                mappedRegions.add(region);
            }
        } else if (application == IrisImageMapApplication.BIOME) {
            for (String target : targets) {
                String localTarget = localResourceKey(target);
                IrisBiome biome = data.getBiomeLoader().load(localTarget);
                if (biome == null) {
                    throw validation("Image-map '" + key + "' references missing biome target '" + target + "'");
                }
                IrisBiome typedBiome = biome.withInferredType(resolveBiomeType(localTarget, dimension, data));
                biomes.put(target, typedBiome);
                mappedBiomes.add(typedBiome);
            }
        } else if (application == IrisImageMapApplication.SURFACE_BLOCK) {
            for (String target : targets) {
                PlatformBlockState block = B.getStateOrNull(target, false);
                if (block == null) {
                    throw validation("Image-map '" + key + "' references unknown surface block target '" + target + "'");
                }
                blocks.put(target, block);
            }
        }

        return new RuntimeBinding(
                key,
                application,
                compiled,
                Map.copyOf(regions),
                Map.copyOf(biomes),
                Map.copyOf(blocks),
                binding.getMasks() == null ? List.of() : List.copyOf(binding.getMasks())
        );
    }

    private static void validateApplication(
            String key,
            IrisImageMapApplication application,
            IrisImageMapType type,
            List<IrisImageMapMask> masks
    ) {
        if (type == null) {
            throw validation("Image-map '" + key + "' requires a type");
        }
        boolean valid = switch (application) {
            case TERRAIN_HEIGHT -> type == IrisImageMapType.GRAYSCALE_HEIGHT
                    || type == IrisImageMapType.RGB_HEIGHT;
            case BIOME, REGION, SURFACE_BLOCK -> type == IrisImageMapType.COLOR_MAP;
            case MASK -> isMaskType(type);
            case CUSTOM -> true;
        };
        if (!valid) {
            throw validation("Image-map '" + key + "' type " + type + " is incompatible with " + application);
        }
        if (application == IrisImageMapApplication.MASK && masks != null && !masks.isEmpty()) {
            throw validation("MASK image-map '" + key + "' cannot reference additional masks");
        }
    }

    private static IrisImageMapMaskSampler resolveMasks(
            RuntimeBinding binding,
            Map<String, RuntimeBinding> bindings
    ) {
        if (binding.maskDefinitions.isEmpty()) {
            return IrisImageMapMaskSampler.empty();
        }
        List<IrisImageMapMaskSampler.Layer> masks = new ArrayList<>(binding.maskDefinitions.size());
        for (IrisImageMapMask mask : binding.maskDefinitions) {
            if (mask == null) {
                throw validation("Image-map '" + binding.key + "' contains a null mask reference");
            }
            String maskKey = requireText(mask.getMap(), "Mask reference on image-map '" + binding.key + "'");
            RuntimeBinding maskBinding = bindings.get(maskKey);
            if (maskBinding == null) {
                throw validation("Image-map '" + binding.key + "' references missing MASK binding '" + maskKey + "'");
            }
            if (maskBinding.application != IrisImageMapApplication.MASK) {
                throw validation("Image-map '" + binding.key + "' references '" + maskKey + "', which is not a MASK binding");
            }
            if (mask.getOperation() == null) {
                throw validation("Mask '" + maskKey + "' on image-map '" + binding.key + "' requires an operation");
            }
            if (!range(mask.getThreshold()) || !range(mask.getFalloff())) {
                throw validation("Mask '" + maskKey + "' threshold and falloff must be finite values within 0..1");
            }
            masks.add(IrisImageMapMaskSampler.layer(maskBinding.compiled, mask));
        }
        return new IrisImageMapMaskSampler(masks);
    }

    private static Set<String> legendTargets(IrisImageMap definition) {
        Set<String> targets = new LinkedHashSet<>();
        if (definition.getColors() != null) {
            for (String target : definition.getColors().values()) {
                if (target != null && !target.isBlank()) {
                    targets.add(target);
                }
            }
        }
        String fallback = definition.getFallbackTarget();
        if (fallback != null && !fallback.isBlank()) {
            targets.add(fallback);
        }
        return targets;
    }

    private static InferredType resolveBiomeType(String biomeKey, IrisDimension dimension, IrisData data) {
        Set<InferredType> types = new LinkedHashSet<>();
        for (IrisRegion region : dimension.getAllRegions(() -> data)) {
            if (region == null) {
                continue;
            }
            if (region.getLandBiomes().contains(biomeKey)) {
                types.add(InferredType.LAND);
            }
            if (region.getSeaBiomes().contains(biomeKey)) {
                types.add(InferredType.SEA);
            }
            if (region.getShoreBiomes().contains(biomeKey)) {
                types.add(InferredType.SHORE);
            }
        }
        if (types.size() != 1) {
            throw validation("Image-mapped biome '" + biomeKey
                    + "' must occur in exactly one landBiomes, seaBiomes, or shoreBiomes role across the dimension; found "
                    + types);
        }
        return types.iterator().next();
    }

    private static String localResourceKey(String target) {
        String normalized = requireText(target, "Image-map Iris resource target");
        int separator = normalized.indexOf(':');
        if (separator < 0) {
            return normalized;
        }
        if (!normalized.startsWith("iris:") || separator == normalized.length() - 1) {
            throw validation("Iris biome and region targets must be bare pack keys or use the iris: namespace, got '"
                    + target + "'");
        }
        return normalized.substring(separator + 1);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw validation(name + " must not be blank");
        }
        return value.trim();
    }

    private static boolean range(double value) {
        return Double.isFinite(value) && value >= 0D && value <= 1D;
    }

    private static boolean isMaskType(IrisImageMapType type) {
        return type == IrisImageMapType.BINARY_MASK
                || type == IrisImageMapType.GRAYSCALE_MASK
                || type == IrisImageMapType.ALPHA_MASK;
    }

    private static IrisImageMapValidationException validation(String message) {
        return new IrisImageMapValidationException(message);
    }

    private static final class RuntimeBinding {
        private final String key;
        private final IrisImageMapApplication application;
        private final CompiledIrisImageMap compiled;
        private final Map<String, IrisRegion> regions;
        private final Map<String, IrisBiome> biomes;
        private final Map<String, PlatformBlockState> blocks;
        private final List<IrisImageMapMask> maskDefinitions;
        private IrisImageMapMaskSampler masks = IrisImageMapMaskSampler.empty();

        private RuntimeBinding(
                String key,
                IrisImageMapApplication application,
                CompiledIrisImageMap compiled,
                Map<String, IrisRegion> regions,
                Map<String, IrisBiome> biomes,
                Map<String, PlatformBlockState> blocks,
                List<IrisImageMapMask> maskDefinitions
        ) {
            this.key = key;
            this.application = application;
            this.compiled = compiled;
            this.regions = regions;
            this.biomes = biomes;
            this.blocks = blocks;
            this.maskDefinitions = maskDefinitions;
        }

        private double maskWeight(double worldX, double worldZ) {
            return masks.sample(worldX, worldZ);
        }
    }
}
