package art.arcane.iris.pack;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.decoration.HydrologyVegetation;
import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.iris.generation.hydrology.config.IrisFluidProfile;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyPolicy;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyTier;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyWetness;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.generation.terrain.ShoreBiomeLineages;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

final class PackHydrologyValidator {
    private static final String CONFIG = "hydrology";
    private static final String POLICY = "hydrologyPolicy";
    private static final String DEFAULT_FLUID = "water";
    private static final List<RangeField> RANGES = List.of(
            new RangeField("ponds.freeboard", Floor.NON_NEGATIVE, true),
            new RangeField("ponds.seeded.radius", Floor.POSITIVE, true),
            new RangeField("ponds.seeded.depth", Floor.POSITIVE, true),
            new RangeField("ponds.seeded.channelSpacing", Floor.POSITIVE, true),
            new RangeField("underground.seaInlets.length", Floor.POSITIVE, true),
            new RangeField("underground.seaInlets.halfWidth", Floor.POSITIVE, false),
            new RangeField("underground.seaInlets.depth", Floor.POSITIVE, true),
            new RangeField("underground.seaInlets.headroom", Floor.NON_NEGATIVE, true),
            new RangeField("underground.seaInlets.chamberRadius", Floor.POSITIVE, true),
            new RangeField("underground.cavePools.freeboard", Floor.NON_NEGATIVE, true)
    );
    private static final List<String> FLUID_REFERENCES = List.of("ponds.fluid", "underground.cavePools.fluid");
    private static final IrisHydrologyTier DEFAULT_TIER = new IrisHydrologyTier();
    private static final Set<String> WATER_BLOCKS = Set.of("minecraft:water");
    private static final String MANTLE_WATER = "the falling water at waterfalls and cascades, sea inlets and cave pools";

    private PackHydrologyValidator() {
    }

    static Validation validate(File packFolder, File[] dimensionFiles) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (packFolder == null || !packFolder.isDirectory() || dimensionFiles == null) {
            return new Validation(errors, warnings);
        }
        List<File> sortedDimensions = new ArrayList<>(List.of(dimensionFiles));
        sortedDimensions.sort(Comparator.comparing(File::getPath));
        File dimensionsFolder = new File(packFolder, PackValidator.DIMENSIONS_FOLDER);
        Findings findings = new Findings(errors, warnings);
        IrisData data = null;
        try {
            for (File dimensionFile : sortedDimensions) {
                data = validateDimension(packFolder, dimensionsFolder, dimensionFile, data, findings);
            }
        } finally {
            close(packFolder, data, errors);
        }
        return new Validation(errors, warnings);
    }

    private static IrisData validateDimension(File packFolder, File dimensionsFolder, File dimensionFile, IrisData data,
                                              Findings findings) {
        List<String> errors = findings.errors();
        JSONObject dimension = PackValidationIo.readJson(dimensionFile);
        if (dimension == null || !dimension.has(CONFIG)) {
            return data;
        }
        String dimensionKey = PackValidationIo.deriveKey(dimensionsFolder, dimensionFile);
        String path = "Dimension '" + dimensionKey + "'." + CONFIG;
        if (!(dimension.opt(CONFIG) instanceof JSONObject hydrology)) {
            errors.add(path + " must be an object.");
            return data;
        }
        if (!hydrology.optBoolean("enabled", false)) {
            return data;
        }
        int reported = errors.size();
        validateRanges(path, hydrology, errors);
        warnPartialTiers(path, hydrology, findings.warnings());
        Set<String> fluids = fluidNames(hydrology);
        Set<String> seams = seamNames(hydrology);
        validateFluidReferences(path, hydrology, fluids, errors);
        IrisData loaded = data == null ? IrisData.openDatapackCompiler(packFolder) : data;
        IrisDimension loadedDimension = loaded.getDimensionLoader().load(dimensionKey, false);
        if (loadedDimension == null) {
            return loaded;
        }
        warnMantleOff("Dimension '" + dimensionKey + "'", loadedDimension, findings.warnings());
        validatePolicies(path, dimensionKey, loaded, loadedDimension, new PolicyFluids(fluids, seams), errors);
        warnShoreBiomesWithoutWidth(dimensionKey, loaded, loadedDimension, findings.warnings());
        if (errors.size() == reported && validateCompile(path, loadedDimension, errors)) {
            validateDecoratorBlocks(path, loaded, loadedDimension.getHydrology(), errors);
            warnFluidsWithoutSeam(dimensionKey, loaded, loadedDimension, findings.warnings());
        }
        return loaded;
    }

    private static boolean validateCompile(String path, IrisDimension dimension, List<String> errors) {
        try {
            HydrologySettings.compile(dimension.getHydrology(), 0L, dimension.getFluidHeight(), 0,
                    dimension.getMaxHeight() - dimension.getMinHeight());
            return true;
        } catch (RuntimeException failure) {
            errors.add(path + " does not compile: " + reason(failure));
            return false;
        }
    }

    private static void validateDecoratorBlocks(String path, IrisData data, IrisHydrology hydrology, List<String> errors) {
        for (int index = 0; index < hydrology.getTiers().size(); index++) {
            validateDecorators(path + ".tiers[" + index + "].decorators", hydrology.getTiers().get(index).getDecorators(), data, errors);
        }
        validateDecorators(path + ".ponds.decorators", hydrology.getPonds().getDecorators(), data, errors);
    }

    private static void validateDecorators(String field, List<IrisDecorator> decorators, IrisData data, List<String> errors) {
        try {
            if (IrisPlatforms.isBound()) {
                HydrologyVegetation.Decorators.compile(decorators, data, field);
            } else {
                HydrologySettings.requireDecorators(decorators, field);
            }
        } catch (RuntimeException failure) {
            errors.add(reason(failure) + ".");
        }
    }

    private static String reason(RuntimeException failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private static void validateRanges(String path, JSONObject hydrology, List<String> errors) {
        JSONArray tiers = hydrology.optJSONArray("tiers");
        if (tiers != null) {
            for (int index = 0; index < tiers.length(); index++) {
                JSONObject tier = tiers.optJSONObject(index);
                if (tier != null && tier.has("halfWidth")) {
                    String field = path + ".tiers[" + index + "].halfWidth";
                    double[] range = range(field, tier.opt("halfWidth"), Floor.POSITIVE, false, errors);
                    if (range != null && range[1] > HydrologySettings.MAX_HALF_WIDTH) {
                        errors.add(field + " must not go above " + (int) HydrologySettings.MAX_HALF_WIDTH + ".");
                    }
                }
            }
        }
        for (RangeField range : RANGES) {
            Object value = nested(hydrology, range.path());
            if (value != null) {
                range(path + "." + range.path(), value, range.floor(), range.whole(), errors);
            }
        }
    }

    private static void warnPartialTiers(String path, JSONObject hydrology, List<String> warnings) {
        JSONArray tiers = hydrology.optJSONArray("tiers");
        if (tiers == null) {
            return;
        }
        for (int index = 0; index < tiers.length(); index++) {
            JSONObject tier = tiers.optJSONObject(index);
            if (tier == null) {
                continue;
            }
            String field = path + ".tiers[" + index + "] ('" + tier.optString("name", DEFAULT_TIER.getName()) + "')";
            if (!tier.has("halfWidth")) {
                warnings.add(field + " does not set halfWidth, so it takes the default " + DEFAULT_TIER.getHalfWidth() + ".");
            }
            if (!tier.has("bankBand")) {
                warnings.add(field + " does not set bankBand, so it takes the default " + DEFAULT_TIER.getBankBand()
                        + " and leaves the ground beside its channels as it is.");
            }
        }
    }

    private static void warnMantleOff(String path, IrisDimension dimension, List<String> warnings) {
        if (!dimension.isUseMantle()) {
            warnings.add(path + " sets useMantle to false while hydrology is enabled, so " + MANTLE_WATER + " are not generated.");
        }
        if (dimension.getDisabledComponents() != null && dimension.getDisabledComponents().contains(ReservedFlag.RIVER_HYDROLOGY)) {
            warnings.add(path + " disables the " + ReservedFlag.RIVER_HYDROLOGY + " component while hydrology is enabled, so " + MANTLE_WATER
                    + " are not generated.");
        }
    }

    private static double[] range(String field, Object value, Floor floor, boolean whole, List<String> errors) {
        if (!(value instanceof JSONArray pair) || pair.length() != 2 || !(pair.opt(0) instanceof Number first)
                || !(pair.opt(1) instanceof Number second)) {
            errors.add(field + " must be written as [min, max].");
            return null;
        }
        double minimum = first.doubleValue();
        double maximum = second.doubleValue();
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum)) {
            errors.add(field + " must hold finite numbers.");
            return null;
        }
        if (whole && (minimum != Math.rint(minimum) || maximum != Math.rint(maximum))) {
            errors.add(field + " must hold whole numbers.");
            return null;
        }
        if (minimum > maximum) {
            errors.add(field + " has its min above its max.");
            return null;
        }
        if (floor == Floor.POSITIVE ? minimum <= 0D : minimum < 0D) {
            errors.add(field + (floor == Floor.POSITIVE ? " must stay above 0." : " must not go below 0."));
            return null;
        }
        return new double[]{minimum, maximum};
    }

    private static Set<String> fluidNames(JSONObject hydrology) {
        JSONArray profiles = hydrology.optJSONArray("fluids");
        if (profiles == null) {
            return Set.of(DEFAULT_FLUID);
        }
        Set<String> names = new LinkedHashSet<>();
        for (int index = 0; index < profiles.length(); index++) {
            JSONObject profile = profiles.optJSONObject(index);
            if (profile != null) {
                names.add(profile.optString("name", DEFAULT_FLUID));
            }
        }
        return names;
    }

    private static Set<String> seamNames(JSONObject hydrology) {
        JSONArray profiles = hydrology.optJSONArray("fluids");
        if (profiles == null) {
            return Set.of();
        }
        Set<String> seams = new LinkedHashSet<>();
        for (int index = 0; index < profiles.length(); index++) {
            JSONObject profile = profiles.optJSONObject(index);
            String seam = profile == null ? null : profile.optString("seam", null);
            if (seam != null && !seam.isBlank()) {
                seams.add(seam);
            }
        }
        return seams;
    }

    private static void validateFluidReferences(String path, JSONObject hydrology, Set<String> fluids, List<String> errors) {
        JSONArray tiers = hydrology.optJSONArray("tiers");
        if (tiers == null) {
            requireFluid(path + ".tiers", DEFAULT_FLUID, fluids, path, errors);
        } else {
            for (int index = 0; index < tiers.length(); index++) {
                JSONObject tier = tiers.optJSONObject(index);
                if (tier != null) {
                    requireFluid(path + ".tiers[" + index + "].fluid", tier.optString("fluid", DEFAULT_FLUID), fluids, path, errors);
                }
            }
        }
        for (String reference : FLUID_REFERENCES) {
            Object value = nested(hydrology, reference);
            requireFluid(path + "." + reference, value instanceof String name ? name : DEFAULT_FLUID, fluids, path, errors);
        }
    }

    private static void validatePolicies(String path, String dimensionKey, IrisData data, IrisDimension dimension, PolicyFluids fluids,
                                         List<String> errors) {
        validatePolicy("Dimension '" + dimensionKey + "'." + POLICY, dimension.getHydrologyPolicy(), data, fluids, path, errors);
        List<IrisRegion> regions = new ArrayList<>(dimension.getAllRegions(() -> data));
        regions.sort(Comparator.comparing(IrisRegion::getLoadKey, Comparator.nullsFirst(Comparator.naturalOrder())));
        for (IrisRegion region : regions) {
            if (region != null) {
                validatePolicy("Region '" + region.getLoadKey() + "'." + POLICY, region.getHydrologyPolicy(), data, fluids, path, errors);
            }
        }
        List<IrisBiome> biomes = new ArrayList<>(dimension.getReachableBiomes(() -> data));
        biomes.sort(Comparator.comparing(IrisBiome::getLoadKey, Comparator.nullsFirst(Comparator.naturalOrder())));
        for (IrisBiome biome : biomes) {
            if (biome != null) {
                validatePolicy("Biome '" + biome.getLoadKey() + "'." + POLICY, biome.getHydrologyPolicy(), data, fluids, path, errors);
            }
        }
    }

    private static void validatePolicy(String field, IrisHydrologyPolicy policy, IrisData data, PolicyFluids fluids, String configPath,
                                       List<String> errors) {
        if (policy == null) {
            return;
        }
        String fluid = policy.getFluid();
        if (fluid != null) {
            requireFluid(field + ".fluid", fluid, fluids.defined(), configPath, errors);
            if (fluids.seams().contains(fluid)) {
                errors.add(field + ".fluid names fluid '" + fluid + "', which is a seam profile; seams only line the edges of other fluids.");
            }
        }
        if (policy.getDecorators() != null) {
            validateDecorators(field + ".decorators", policy.getDecorators(), data, errors);
        }
        requireMultiplier(field + ".rainfall", policy.getRainfall(), errors);
        requireMultiplier(field + ".pondDensity", policy.getPondDensity(), errors);
        IrisHydrologyWetness wetness = policy.getWetness();
        if (wetness != null && !(wetness.getMin() >= 0D && wetness.getMin() <= wetness.getMax() && wetness.getMax() <= 1D)) {
            errors.add(field + ".wetness must have 0 <= min <= max <= 1, got min " + wetness.getMin() + " and max " + wetness.getMax() + ".");
        }
    }

    private static void warnShoreBiomesWithoutWidth(String dimensionKey, IrisData data, IrisDimension dimension, List<String> warnings) {
        List<IrisRegion> regions = new ArrayList<>(dimension.getAllRegions(() -> data));
        regions.sort(Comparator.comparing(IrisRegion::getLoadKey, Comparator.nullsFirst(Comparator.naturalOrder())));
        Set<IrisHydrologyPolicy> widened = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<String, IrisBiome> lineageBiomes = new TreeMap<>();
        ShoreBiomeLineages.visit(dimension, regions, () -> data, (lineage, region) -> {
            Integer width = IrisHydrologyPolicy.inherited(lineage, region, dimension, IrisHydrologyPolicy::getShoreBiomeWidth);
            boolean wide = width != null && width > 0;
            for (IrisBiome biome : lineage) {
                if (biome.getLoadKey() != null) {
                    lineageBiomes.putIfAbsent(biome.getLoadKey(), biome);
                }
                if (wide && biome.getHydrologyPolicy() != null) {
                    widened.add(biome.getHydrologyPolicy());
                }
            }
            if (wide && region != null && region.getHydrologyPolicy() != null) {
                widened.add(region.getHydrologyPolicy());
            }
            if (wide && dimension.getHydrologyPolicy() != null) {
                widened.add(dimension.getHydrologyPolicy());
            }
        });
        warnShoreBiomesWithoutWidth("Dimension '" + dimensionKey + "'", dimension.getHydrologyPolicy(), widened, warnings);
        for (IrisRegion region : regions) {
            warnShoreBiomesWithoutWidth("Region '" + region.getLoadKey() + "'", region.getHydrologyPolicy(), widened, warnings);
        }
        for (IrisBiome biome : lineageBiomes.values()) {
            warnShoreBiomesWithoutWidth("Biome '" + biome.getLoadKey() + "'", biome.getHydrologyPolicy(), widened, warnings);
        }
    }

    private static void warnShoreBiomesWithoutWidth(String scope, IrisHydrologyPolicy policy, Set<IrisHydrologyPolicy> widened,
                                                    List<String> warnings) {
        if (policy != null && policy.getShoreBiomes() != null && !policy.getShoreBiomes().isEmpty() && !widened.contains(policy)) {
            warnings.add(scope + "." + POLICY + ".shoreBiomes lists shore biomes, but no shoreBiomeWidth above 0 applies to them,"
                    + " so none are placed.");
        }
    }

    private static void warnFluidsWithoutSeam(String dimensionKey, IrisData data, IrisDimension dimension, List<String> warnings) {
        String path = "Dimension '" + dimensionKey + "'." + CONFIG;
        Map<String, IrisFluidProfile> profiles = new HashMap<>();
        for (IrisFluidProfile profile : dimension.getHydrology().getFluids()) {
            profiles.put(profile.getName(), profile);
        }
        Map<String, Set<String>> rivers = riverFluidSources(dimensionKey, data, dimension);
        rivers.keySet().retainAll(profiles.keySet());
        for (Map.Entry<String, Set<String>> river : rivers.entrySet()) {
            IrisFluidProfile profile = profiles.get(river.getKey());
            Set<String> blocks = blockIds(profile.getPalette());
            if (hasSeam(profile) || blocks.equals(WATER_BLOCKS)) {
                continue;
            }
            List<String> met = new ArrayList<>();
            if (dimension.getFluidHeight() > 0 && !blocks.equals(blockIds(dimension.getFluidPalette()))) {
                met.add("the sea");
            }
            for (String other : rivers.keySet()) {
                IrisFluidProfile otherProfile = profiles.get(other);
                if (!hasSeam(otherProfile) && !blocks.equals(blockIds(otherProfile.getPalette()))) {
                    met.add("fluid '" + other + "'");
                }
            }
            if (!met.isEmpty()) {
                warnings.add(path + ".fluids profile '" + river.getKey() + "' sets no seam, so where its rivers meet " + String.join(" or ", met)
                        + " the fluids lie side by side. Set seam on the profile to line that edge. Its rivers come from "
                        + String.join(", ", river.getValue()) + ".");
            }
        }
    }

    private static Map<String, Set<String>> riverFluidSources(String dimensionKey, IrisData data, IrisDimension dimension) {
        Map<String, Set<String>> sources = new TreeMap<>();
        List<IrisRegion> regions = new ArrayList<>(dimension.getAllRegions(() -> data));
        ShoreBiomeLineages.visit(dimension, regions, () -> data, (lineage, region) -> {
            String fluid = IrisHydrologyPolicy.inherited(lineage, region, dimension, IrisHydrologyPolicy::getFluid);
            if (fluid == null) {
                addTierSources(dimensionKey, dimension, sources);
            } else {
                sources.computeIfAbsent(fluid, name -> new TreeSet<>()).add(policyFluidOwner(dimensionKey, lineage, region));
            }
        });
        if (sources.isEmpty()) {
            addTierSources(dimensionKey, dimension, sources);
        }
        return sources;
    }

    private static void addTierSources(String dimensionKey, IrisDimension dimension, Map<String, Set<String>> sources) {
        List<IrisHydrologyTier> tiers = dimension.getHydrology().getTiers();
        for (int index = 0; index < tiers.size(); index++) {
            sources.computeIfAbsent(tiers.get(index).getFluid(), name -> new TreeSet<>())
                    .add("Dimension '" + dimensionKey + "'." + CONFIG + ".tiers[" + index + "].fluid");
        }
    }

    private static String policyFluidOwner(String dimensionKey, List<IrisBiome> lineage, IrisRegion region) {
        for (IrisBiome biome : lineage) {
            if (biome.getHydrologyPolicy() != null && biome.getHydrologyPolicy().getFluid() != null) {
                return "Biome '" + biome.getLoadKey() + "'." + POLICY + ".fluid";
            }
        }
        if (region != null && region.getHydrologyPolicy() != null && region.getHydrologyPolicy().getFluid() != null) {
            return "Region '" + region.getLoadKey() + "'." + POLICY + ".fluid";
        }
        return "Dimension '" + dimensionKey + "'." + POLICY + ".fluid";
    }

    private static boolean hasSeam(IrisFluidProfile profile) {
        return profile.getSeam() != null && !profile.getSeam().isBlank();
    }

    private static Set<String> blockIds(IrisMaterialPalette palette) {
        Set<String> blocks = new TreeSet<>();
        for (IrisBlockData block : palette.getPalette()) {
            String id = block.getBlock().toLowerCase(Locale.ROOT);
            int state = id.indexOf('[');
            String name = state < 0 ? id : id.substring(0, state);
            blocks.add(name.contains(":") ? name : "minecraft:" + name);
        }
        return blocks;
    }

    private static void requireMultiplier(String field, Double value, List<String> errors) {
        if (value != null && !IrisHydrologyPolicy.validMultiplier(value)) {
            errors.add(field + " must be a finite value from 0 to " + (int) IrisHydrologyPolicy.MAX_MULTIPLIER + ", got " + value + ".");
        }
    }

    private static void close(File packFolder, IrisData data, List<String> errors) {
        if (data == null) {
            return;
        }
        try {
            data.close();
        } catch (RuntimeException failure) {
            IrisLogging.reportError("Hydrology pack validation could not release detached data for '" + packFolder.getName() + "'.", failure);
            errors.add("Hydrology validation could not release detached pack data: " + failure.getMessage() + ".");
        }
    }

    private static void requireFluid(String field, String fluid, Set<String> fluids, String configPath, List<String> errors) {
        if (!fluids.contains(fluid)) {
            errors.add(field + " names fluid '" + fluid + "', which " + configPath + ".fluids does not define " + fluids + ".");
        }
    }

    private static Object nested(JSONObject root, String path) {
        Object current = root;
        for (String part : path.split("\\.")) {
            if (!(current instanceof JSONObject object) || !object.has(part)) {
                return null;
            }
            current = object.opt(part);
        }
        return current;
    }

    private enum Floor {
        NON_NEGATIVE,
        POSITIVE
    }

    private record RangeField(String path, Floor floor, boolean whole) {
    }

    record Validation(List<String> errors, List<String> warnings) {
        Validation {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
        }
    }

    private record PolicyFluids(Set<String> defined, Set<String> seams) {
    }

    private record Findings(List<String> errors, List<String> warnings) {
    }
}
