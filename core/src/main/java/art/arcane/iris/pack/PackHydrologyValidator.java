package art.arcane.iris.pack;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.decoration.HydrologyVegetation;
import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyPolicy;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    private PackHydrologyValidator() {
    }

    static Validation validate(File packFolder, File[] dimensionFiles) {
        List<String> errors = new ArrayList<>();
        if (packFolder == null || !packFolder.isDirectory() || dimensionFiles == null) {
            return new Validation(errors, List.of());
        }
        List<File> sortedDimensions = new ArrayList<>(List.of(dimensionFiles));
        sortedDimensions.sort(Comparator.comparing(File::getPath));
        File dimensionsFolder = new File(packFolder, PackValidator.DIMENSIONS_FOLDER);
        IrisData data = null;
        try {
            for (File dimensionFile : sortedDimensions) {
                data = validateDimension(packFolder, dimensionsFolder, dimensionFile, data, errors);
            }
        } finally {
            close(packFolder, data, errors);
        }
        return new Validation(errors, List.of());
    }

    private static IrisData validateDimension(File packFolder, File dimensionsFolder, File dimensionFile, IrisData data,
                                              List<String> errors) {
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
        Set<String> fluids = fluidNames(hydrology);
        validateFluidReferences(path, hydrology, fluids, errors);
        IrisData loaded = data == null ? IrisData.openDatapackCompiler(packFolder) : data;
        IrisDimension loadedDimension = loaded.getDimensionLoader().load(dimensionKey, false);
        if (loadedDimension == null) {
            return loaded;
        }
        validatePolicies(path, dimensionKey, loaded, loadedDimension, fluids, errors);
        if (errors.size() == reported && validateCompile(path, loadedDimension, errors)) {
            validateDecoratorBlocks(path, loaded, loadedDimension.getHydrology(), errors);
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

    private static void validatePolicies(String path, String dimensionKey, IrisData data, IrisDimension dimension, Set<String> fluids,
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

    private static void validatePolicy(String field, IrisHydrologyPolicy policy, IrisData data, Set<String> fluids, String configPath,
                                       List<String> errors) {
        if (policy == null) {
            return;
        }
        if (policy.getFluid() != null) {
            requireFluid(field + ".fluid", policy.getFluid(), fluids, configPath, errors);
        }
        if (policy.getDecorators() != null) {
            validateDecorators(field + ".decorators", policy.getDecorators(), data, errors);
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
}
