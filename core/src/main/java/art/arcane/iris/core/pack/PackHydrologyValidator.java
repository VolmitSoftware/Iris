package art.arcane.iris.core.pack;

import art.arcane.iris.engine.hydrology.HydrologyFeatureQuery;
import art.arcane.iris.engine.object.IrisCoastalRiverGrottoConfig;
import art.arcane.iris.engine.object.IrisDeepFluidConfig;
import art.arcane.iris.engine.object.IrisInlandRiverGrottoConfig;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class PackHydrologyValidator {
    private static final int MAX_PROFILES = 64;
    private static final int MAX_DEEP_FLUIDS = 64;
    private static final int MAX_POLICY_REFERENCES = 128;
    private static final long MAX_LATTICE_NODES = 65_536L;
    private static final long MAX_ROUTE_SAMPLES = 262_144L;
    private static final int MAX_REACHABLE_BIOMES = 65_536;
    private static final int CORRIDOR_SAMPLE_SPACING = 4;
    private static final List<Integer> SAMPLE_SPACINGS = List.of(8, 16, 32, 64);
    private static final IrisCoastalRiverGrottoConfig DEFAULT_COASTAL_GROTTO =
            new IrisCoastalRiverGrottoConfig();
    private static final IrisInlandRiverGrottoConfig DEFAULT_INLAND_GROTTO =
            new IrisInlandRiverGrottoConfig();
    private static final IrisDeepFluidConfig DEFAULT_DEEP_FLUID = new IrisDeepFluidConfig();
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Pattern RESOURCE_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_./-]{0,127}");
    private static final Set<String> PLACEMENT_MODES = Set.of(
            "DISABLED",
            "TRANSIT_ONLY",
            "NATURAL",
            "PREFERRED_HEADWATER",
            "REQUIRED_HEADWATER"
    );
    private static final Set<String> ROUTING_MODES = Set.of("BLOCK", "AVOID", "ALLOW", "PREFER");
    private static final Set<String> BLEND_STYLES = Set.of("SMOOTH", "LINEAR", "CONCAVE", "TERRACED", "CLIFF");
    private static final Set<String> BED_PROFILES = Set.of("BOWL", "FLAT", "V", "U");
    private static final Set<String> INLAND_OUTLETS = Set.of("SINKHOLE_GROTTO");
    private static final List<String> POLICY_BIOME_FIELDS = List.of(
            "surfaceBiomes",
            "mouthBiomes",
            "shoreBiomes",
            "bankBiomes",
            "floodedCaveBiomes"
    );
    private static final List<String> REGION_BIOME_FIELDS = List.of(
            "landBiomes",
            "seaBiomes",
            "shoreBiomes",
            "caveBiomes"
    );

    private PackHydrologyValidator() {
    }

    static Validation validate(File packFolder, File[] dimensionFiles) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (packFolder == null || !packFolder.isDirectory() || dimensionFiles == null) {
            return new Validation(errors, warnings);
        }

        Map<String, JSONObject> regions = loadResources(new File(packFolder, "regions"));
        Map<String, JSONObject> biomes = loadResources(new File(packFolder, "biomes"));
        Map<String, JSONObject> imageMaps = loadResources(new File(packFolder, "image-maps"));
        validateResourcePolicies("Region", regions, biomes, errors);
        validateResourcePolicies("Biome", biomes, biomes, errors);

        List<File> sortedDimensions = new ArrayList<>(List.of(dimensionFiles));
        sortedDimensions.sort(Comparator.comparing(File::getPath));
        File dimensionsFolder = new File(packFolder, PackValidator.DIMENSIONS_FOLDER);
        for (File dimensionFile : sortedDimensions) {
            JSONObject dimension = PackValidationIo.readJson(dimensionFile);
            if (dimension == null) {
                continue;
            }
            String dimensionKey = PackValidationIo.deriveKey(dimensionsFolder, dimensionFile);
            String dimensionPath = "Dimension '" + dimensionKey + "'";
            JSONObject dimensionPolicy = validatePolicy(
                    dimensionPath,
                    dimension,
                    "riverPolicy",
                    biomes,
                    errors
            );
            HydrologyValidation hydrology = validateHydrology(dimensionPath, dimension, biomes, errors, warnings);
            if (!hydrology.active()) {
                continue;
            }
            validateMantleCapabilities(dimensionPath + " hydrology", dimension, errors);
            if (hydrology.riversEnabled()) {
                validateReachablePolicies(
                        dimensionKey,
                        dimension,
                        dimensionPolicy,
                        hydrology.profileIds(),
                        hydrology.surfacePoolIds(),
                        regions,
                        biomes,
                        imageMaps,
                        errors
                );
            }
        }
        return new Validation(errors, warnings);
    }

    private static HydrologyValidation validateHydrology(
            String dimensionPath,
            JSONObject dimension,
            Map<String, JSONObject> biomes,
            List<String> errors,
            List<String> warnings
    ) {
        if (!dimension.has("hydrology")) {
            return HydrologyValidation.inactive();
        }
        JSONObject hydrology = requireObject(dimension, "hydrology", dimensionPath + ".hydrology", errors);
        if (hydrology == null) {
            return HydrologyValidation.inactive();
        }

        boolean riversEnabled = false;
        Set<String> profileIds = Set.of("default");
        if (hydrology.has("rivers")) {
            JSONObject rivers = requireObject(
                    hydrology,
                    "rivers",
                    dimensionPath + ".hydrology.rivers",
                    errors
            );
            if (rivers != null) {
                riversEnabled = booleanValue(rivers, "enabled", false);
                profileIds = validateRivers(
                        dimensionPath + ".hydrology.rivers",
                        rivers,
                        dimension,
                        riversEnabled,
                        errors,
                        warnings
                );
            }
        }

        boolean deepFluidsActive = validateDeepFluids(
                dimensionPath + ".hydrology",
                hydrology,
                dimension,
                profileIds,
                errors
        );
        Set<String> surfacePoolIds = validateSurfacePools(dimensionPath + ".hydrology", hydrology, profileIds, biomes, errors);
        return new HydrologyValidation(riversEnabled, deepFluidsActive || !surfacePoolIds.isEmpty(), profileIds, surfacePoolIds);
    }

    private static Set<String> validateRivers(
            String path,
            JSONObject rivers,
            JSONObject dimension,
            boolean enabled,
            List<String> errors,
            List<String> warnings
    ) {
        PackJsonFieldChecks.validateOptionalBoolean(path, rivers, "enabled", errors);
        JSONObject routing = nestedObject(rivers, "routing", path, errors);
        JSONObject geometry = nestedObject(rivers, "geometry", path, errors);
        JSONObject surface = nestedObject(rivers, "surface", path, errors);
        JSONObject underground = nestedObject(rivers, "underground", path, errors);
        JSONObject grottos = nestedObject(rivers, "grottos", path, errors);

        RoutingValues routingValues = routing == null
                ? RoutingValues.defaults()
                : validateRouting(path + ".routing", routing, errors);
        if (geometry != null) {
            validateGeometry(path + ".geometry", geometry, errors);
        }
        int minimumSurfaceCourseLength = routing == null ? 384 : integerValue(routing, "minimumSurfaceCourseLength", 384);
        SourceBudget surfaceBudget = surface == null
                ? SourceBudget.defaults()
                : validateSurface(path + ".surface", surface, dimension, minimumSurfaceCourseLength, errors, warnings);
        SourceBudget undergroundBudget = underground == null
                ? SourceBudget.defaults()
                : validateUnderground(path + ".underground", underground, dimension, errors, warnings);
        if (underground != null
                && integerValue(underground, "mouthLevelingDistance", 64) > routingValues.maximumRouteLength()) {
            errors.add(path + ".underground.mouthLevelingDistance must not exceed routing.maximumRouteLength.");
        }
        if (grottos != null) {
            validateGrottos(path + ".grottos", grottos, routingValues, errors);
        }

        validateRouteComplexity(path, routingValues, surfaceBudget, undergroundBudget, errors);
        Set<String> profileIds = validateProfiles(path, rivers, errors);
        if (enabled && profileIds.isEmpty()) {
            errors.add(path + ".profiles must contain at least one valid river profile when rivers are enabled.");
        }
        return profileIds;
    }

    private static void validateGeometry(String path, JSONObject geometry, List<String> errors) {
        JSONObject meanders = nestedObject(geometry, "meanders", path, errors);
        JSONObject surface = nestedObject(geometry, "surface", path, errors);
        JSONObject underground = nestedObject(geometry, "underground", path, errors);
        JSONObject grottos = nestedObject(geometry, "grottos", path, errors);
        JSONObject drops = nestedObject(geometry, "drops", path, errors);
        if (meanders != null) {
            validateMeanders(path + ".meanders", meanders, errors);
        }
        if (surface != null) {
            // The three nullable roughness fields fall back to the surface channel; a JSON null counts as absent.
            validateChannelShape(path + ".surface", surface, errors);
        }
        if (underground != null) {
            validateChannelShape(path + ".underground", underground, errors);
        }
        if (grottos != null) {
            validateChannelShape(path + ".grottos", grottos, errors);
        }
        if (drops != null) {
            validateDropShape(path + ".drops", drops, errors);
        }
    }

    private static void validateMeanders(String path, JSONObject meanders, List<String> errors) {
        PackJsonFieldChecks.validateOptionalIntegerRange(path, meanders, "primaryWavelength", 8, 512, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, meanders, "detailWavelength", 4, 128, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, meanders, "primaryStrength", 0D, 2D, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, meanders, "detailStrength", 0D, 2D, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, meanders, "maximumOffsetRatio", 0D, 1D, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, meanders, "smoothingPasses", 0, 4, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, meanders, "maximumTurnDegrees", 10D, 150D, errors);
        if (integerValue(meanders, "detailWavelength", 12)
                > integerValue(meanders, "primaryWavelength", 64)) {
            errors.add(path + ".detailWavelength must not exceed primaryWavelength.");
        }
    }

    private static void validateChannelShape(String path, JSONObject shape, List<String> errors) {
        PackJsonFieldChecks.validateOptionalDoubleRange(path, shape, "bedRoundness", 1D, 6D, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, shape, "bedRoughness", 0D, 1D, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, shape, "wallRoughness", 0D, 1D, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, shape, "roughnessWavelength", 3, 128, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, shape, "radialBase", 0.4D, 1.2D, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, shape, "radialMinimum", 0.2D, 1D, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, shape, "radialMaximum", 1D, 2D, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, shape, "primaryLobeStrength", 0D, 0.5D, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, shape, "detailLobeStrength", 0D, 0.5D, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, shape, "ceilingRoughness", 0D, 1D, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, shape, "aspectMinimum", 0.2D, 1D, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, shape, "aspectRange", 0D, 0.8D, errors);
    }

    private static void validateDropShape(String path, JSONObject drops, List<String> errors) {
        PackJsonFieldChecks.validateOptionalIntegerRange(path, drops, "cascadeRunPerBlock", 1, 16, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, drops, "cascadeExponent", 0.25D, 6D, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, drops, "maximumCascadeStep", 1, 4, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, drops, "flowWidthRatio", 0.25D, 1D, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, drops, "maximumFlowDepth", 1, 16, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, drops, "basinWidthRatio", 1D, 4D, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, drops, "maximumBasinDepth", 1, 32, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, drops, "undergroundCascadeRunPerBlock", 0, 16, errors);
    }

    private static RoutingValues validateRouting(String path, JSONObject routing, List<String> errors) {
        PackJsonFieldChecks.validateOptionalIntegerRange(path, routing, "tileSize", 256, 8192, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, routing, "sampleSpacing", 8, 64, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, routing, "maximumRouteLength", 256, 32768, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(
                path,
                routing,
                "minimumSurfaceCourseLength",
                0,
                32768,
                errors
        );
        PackJsonFieldChecks.validateOptionalIntegerRange(
                path,
                routing,
                "minimumUndergroundCourseLength",
                0,
                32768,
                errors
        );
        PackJsonFieldChecks.validateOptionalIntegerRange(path, routing, "maximumOutletsPerTile", 1, 256, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, routing, "maximumCoastalOutletsPerTile", 0, 64, errors);
        PackJsonFieldChecks.validateOptionalBoolean(path, routing, "oceanOutlets", errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, routing, "valleyPreference", 0D, 8D, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, routing, "uphillPenalty", 0D, 128D, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, routing, "slopePenalty", 0D, 16D, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, routing, "confluenceAttraction", 0D, 1D, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, routing, "lengthPreference", 0D, 8D, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, routing, "tributaries", 0, 4, errors);

        int tileSize = integerValue(routing, "tileSize", 2048);
        int sampleSpacing = integerValue(routing, "sampleSpacing", 64);
        int maximumRouteLength = integerValue(routing, "maximumRouteLength", 16384);
        int minimumSurfaceCourseLength = integerValue(routing, "minimumSurfaceCourseLength", 384);
        int minimumUndergroundCourseLength = integerValue(routing, "minimumUndergroundCourseLength", 192);
        if (!SAMPLE_SPACINGS.contains(sampleSpacing)) {
            errors.add(path + ".sampleSpacing must be one of " + SAMPLE_SPACINGS + ".");
        }
        if (sampleSpacing > 0 && tileSize % sampleSpacing != 0) {
            errors.add(path + ".tileSize must be divisible by sampleSpacing.");
        }
        if (minimumSurfaceCourseLength > maximumRouteLength) {
            errors.add(path + ".minimumSurfaceCourseLength must not exceed maximumRouteLength.");
        }
        if (minimumUndergroundCourseLength > maximumRouteLength) {
            errors.add(path + ".minimumUndergroundCourseLength must not exceed maximumRouteLength.");
        }

        Set<String> inlandOutlets = validateEnumArray(
                path,
                routing,
                "inlandOutlets",
                INLAND_OUTLETS,
                errors
        );
        boolean oceanOutlets = booleanValue(routing, "oceanOutlets", true);
        if (!oceanOutlets && inlandOutlets.isEmpty()) {
            errors.add(path + " must enable oceanOutlets or select at least one inlandOutlets entry.");
        }
        return new RoutingValues(tileSize, sampleSpacing, maximumRouteLength, inlandOutlets);
    }

    private static SourceBudget validateSurface(
            String path,
            JSONObject surface,
            JSONObject dimension,
            int minimumSurfaceCourseLength,
            List<String> errors,
            List<String> warnings
    ) {
        PackJsonFieldChecks.validateOptionalBoolean(path, surface, "enabled", errors);
        JSONObject sources = nestedObject(surface, "sources", path, errors);
        JSONObject channel = nestedObject(surface, "channel", path, errors);
        JSONObject banks = nestedObject(surface, "banks", path, errors);
        JSONObject flow = nestedObject(surface, "flow", path, errors);
        JSONObject mouths = nestedObject(surface, "mouths", path, errors);
        JSONObject bed = nestedObject(surface, "bed", path, errors);
        JSONObject erosion = nestedObject(surface, "erosion", path, errors);
        JSONObject ponds = nestedObject(surface, "ponds", path, errors);

        SourceBudget budget = sources == null
                ? SourceBudget.defaults()
                : validateSources(path + ".sources", sources, true, errors);
        if (channel != null) {
            validateSurfaceChannel(path + ".channel", channel, errors);
        }
        if (banks != null) {
            validateSurfaceBanks(path + ".banks", banks, errors);
        }
        if (flow != null) {
            validateSurfaceFlow(path + ".flow", flow, errors);
        }
        if (mouths != null) {
            validateMouths(path + ".mouths", mouths, minimumSurfaceCourseLength, errors);
        }
        if (bed != null) {
            validateSurfaceBed(path + ".bed", bed, errors);
        }
        if (erosion != null) {
            validateSurfaceErosion(path + ".erosion", erosion, errors);
        }
        if (ponds != null) {
            validateSurfacePonds(path + ".ponds", ponds, errors);
        }

        boolean surfaceEnabled = booleanValue(surface, "enabled", true);
        if (surfaceEnabled && budget.maximumExpectedSources() == 0) {
            warnings.add(path
                    + " has no natural source budget; only qualifying REQUIRED_HEADWATER cells can admit sources.");
        }
        if (surfaceEnabled && sources != null) {
            double minimumElevation = doubleValue(sources, "minimumElevation", 88D);
            NumericRange dimensionHeight = dimensionHeight(dimension);
            if (Double.isFinite(minimumElevation) && minimumElevation >= dimensionHeight.maximum()) {
                errors.add(path + ".sources.minimumElevation must be below dimensionHeight.max.");
            }
        }
        return surfaceEnabled ? budget : SourceBudget.disabled();
    }

    private static void validateSurfaceChannel(String path, JSONObject channel, List<String> errors) {
        validateRange(path, channel, "width", 1D, 128D, 4D, 8D, errors);
        validateRange(path, channel, "depth", 1D, 64D, 2D, 4D, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, channel, "sink", 0, 3, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, channel, "maximumIncision", 1, 32, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, channel, "springWidthRatio", 1D, 4D, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, channel, "springLength", 4, 96, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, channel, "roughness", 0D, 1D, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, channel, "roughnessWavelength", 4, 64, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, channel, "smoothingRadius", 0, 64, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, channel, "outlineMinimumRatio", 0.2D, 1D, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, channel, "outlineMaximumRatio", 1D, 3D, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, channel, "springExtraDepth", 0D, 8D, errors);
    }

    private static void validateSurfaceErosion(String path, JSONObject erosion, List<String> errors) {
        PackJsonFieldChecks.validateOptionalBoolean(path, erosion, "enabled", errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, erosion, "smoothingRadius", 0, 64, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, erosion, "thalwegFraction", 0D, 0.95D, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, erosion, "blendCurve", 0.25D, 4D, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, erosion, "bedNoise", 0D, 2D, errors);
        PackJsonFieldChecks.validateOptionalEnum(path, erosion, "style", BLEND_STYLES, errors);
        // terraceSteps and cliffFraction only matter to their own style; the other styles ignore them, so no cross-field rule.
        PackJsonFieldChecks.validateOptionalIntegerRange(path, erosion, "terraceSteps", 2, 16, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, erosion, "cliffFraction", 0D, 1D, errors);
        PackJsonFieldChecks.validateOptionalEnum(path, erosion, "bedProfile", BED_PROFILES, errors);
    }

    private static void validateSurfacePonds(String path, JSONObject ponds, List<String> errors) {
        for (String end : new String[] {"source", "terminal"}) {
            JSONObject pond = nestedObject(ponds, end, path, errors);
            if (pond == null) {
                continue;
            }
            String pondPath = path + "." + end;
            PackJsonFieldChecks.validateOptionalBoolean(pondPath, pond, "enabled", errors);
            PackJsonFieldChecks.validateOptionalIntegerRange(pondPath, pond, "minimumRadius", 1, 32, errors);
            PackJsonFieldChecks.validateOptionalIntegerRange(pondPath, pond, "maximumRadius", 1, 32, errors);
            PackJsonFieldChecks.validateOptionalIntegerRange(pondPath, pond, "depth", 1, 8, errors);
            if (pond.has("minimumRadius") && pond.has("maximumRadius")
                    && pond.optInt("minimumRadius") > pond.optInt("maximumRadius")) {
                errors.add(pondPath + ".minimumRadius must not exceed maximumRadius");
            }
        }
    }

    private static void validateSurfaceBanks(String path, JSONObject banks, List<String> errors) {
        PackJsonFieldChecks.validateOptionalDoubleRange(path, banks, "shoreWidth", 0D, 16D, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, banks, "blendSlope", 0.5D, 12D, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, banks, "minimumBlendWidth", 1, 64, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, banks, "maximumBlendWidth", 1, 64, errors);
        PackJsonFieldChecks.validateOptionalBoolean(path, banks, "exposeCutStrata", errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, banks, "shoreRise", 0D, 4D, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, banks, "blendBaseWidth", 0D, 32D, errors);
        validateMaterial(path, banks, "shoreMaterial", errors);
        validateMaterial(path, banks, "bankMaterial", errors);
        if (integerValue(banks, "minimumBlendWidth", 4) > integerValue(banks, "maximumBlendWidth", 32)) {
            errors.add(path + ".minimumBlendWidth must not exceed maximumBlendWidth.");
        }
    }

    private static void validateSurfaceFlow(String path, JSONObject flow, List<String> errors) {
        PackJsonFieldChecks.validateOptionalIntegerRange(path, flow, "cascadeRun", 1, 8, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, flow, "waterfallMinimumDrop", 2, 32, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, flow, "waterfallThalwegFraction", 0D, 0.95D, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, flow, "plungeBasinMinimumDrop", 1, 8, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, flow, "plungeBasinLengthRatio", 0D, 8D, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, flow, "plungeBasinDepth", 0, 8, errors);
    }

    private static void validateSurfaceBed(String path, JSONObject bed, List<String> errors) {
        PackJsonFieldChecks.validateOptionalBoolean(path, bed, "allowGravityBlocks", errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, bed, "padding", 0, 8, errors);
        if (bed.has("paddingPalette")) {
            validateSolidPalette(path + ".paddingPalette", bed.opt("paddingPalette"), errors);
        }
        validateMaterial(path, bed, "material", errors);
    }

    private static void validateMaterial(String path, JSONObject owner, String field, List<String> errors) {
        JSONObject material = nestedObject(owner, field, path, errors);
        if (material == null) {
            return;
        }
        String materialPath = path + "." + field;
        PackJsonFieldChecks.validateOptionalBoolean(materialPath, material, "enabled", errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(materialPath, material, "depth", 1, 8, errors);
        if (material.has("palette")) {
            validateSolidPalette(materialPath + ".palette", material.opt("palette"), errors);
        }
    }

    private static void validateSolidPalette(String path, Object rawPalette, List<String> errors) {
        if (!(rawPalette instanceof JSONObject palette)) {
            errors.add(path + " must be an object.");
            return;
        }
        Object rawEntries = palette.opt("palette");
        if (!(rawEntries instanceof JSONArray entries) || entries.length() == 0) {
            errors.add(path + ".palette must contain at least one solid block.");
            return;
        }
        for (int index = 0; index < entries.length(); index++) {
            String entryPath = path + ".palette[" + index + "]";
            JSONObject entry = entries.optJSONObject(index);
            if (entry == null) {
                errors.add(entryPath + " must be an object.");
                continue;
            }
            Object rawBlock = entry.opt("block");
            if (!(rawBlock instanceof String block) || block.isBlank()) {
                errors.add(entryPath + ".block must name a solid block.");
            } else if (!definitelyNotFluid(block)) {
                errors.add(entryPath + ".block must not be a fluid.");
            }
            PackJsonFieldChecks.validateOptionalIntegerRange(entryPath, entry, "weight", 1, 64, errors);
        }
    }

    private static void validateMouths(String path, JSONObject mouths, int minimumSurfaceCourseLength, List<String> errors) {
        PackJsonFieldChecks.validateOptionalDoubleRange(path, mouths, "flareRatio", 1D, 4D, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, mouths, "maximumOceanApron", 0, 32, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, mouths, "inletLength", 0, 256, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, mouths, "inletDepth", 0, 16, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, mouths, "maximumIncision", 0, 128, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, mouths, "inletCourseFraction", 0.05D, 1D, errors);
        PackJsonFieldChecks.validateOptionalDoubleRange(path, mouths, "inletRampSlope", 0.1D, 4D, errors);
        // The drowned reach is the end of a river, never the whole of one.
        if (mouths.has("inletLength") && integerValue(mouths, "inletLength", 64) > minimumSurfaceCourseLength) {
            errors.add(path + ".inletLength must not exceed routing.minimumSurfaceCourseLength.");
        }
    }

    private static SourceBudget validateUnderground(
            String path,
            JSONObject underground,
            JSONObject dimension,
            List<String> errors,
            List<String> warnings
    ) {
        PackJsonFieldChecks.validateOptionalBoolean(path, underground, "enabled", errors);
        JSONObject sources = nestedObject(underground, "sources", path, errors);
        SourceBudget budget = sources == null
                ? SourceBudget.defaults()
                : validateSources(path + ".sources", sources, false, errors);
        NumericRange fluidLevel = validateRange(
                path,
                underground,
                "fluidLevel",
                -2048D,
                2048D,
                -48D,
                50D,
                errors
        );
        validateRange(path, underground, "channelWidth", 1D, 64D, 4D, 10D, errors);
        NumericRange depth = validateRange(path, underground, "depth", 1D, 32D, 1D, 3D, errors);
        NumericRange headroom = validateRange(path, underground, "headroom", 1D, 128D, 6D, 14D, errors);
        PackJsonFieldChecks.validateOptionalBoolean(path, underground, "connectToExistingCaves", errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(
                path,
                underground,
                "mouthLevelingDistance",
                16,
                512,
                errors
        );
        PackJsonFieldChecks.validateOptionalIntegerRange(path, underground, "minimumRockCover", 1, 64, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, underground, "minimumFloorCover", 1, 32, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, underground, "wideningSources", 1, 64, errors);
        validateMaterial(path, underground, "bedMaterial", errors);

        NumericRange height = dimensionHeight(dimension);
        if (fluidLevel.minimum() - depth.maximum() <= height.minimum()) {
            errors.add(path + " fluidLevel.min and depth.max must remain above dimensionHeight.min.");
        }
        if (fluidLevel.maximum() + headroom.maximum() >= height.maximum()) {
            errors.add(path + " fluidLevel.max and headroom.max must remain below dimensionHeight.max.");
        }
        boolean undergroundEnabled = booleanValue(underground, "enabled", true);
        if (undergroundEnabled && budget.maximumExpectedSources() == 0) {
            warnings.add(path
                    + " has no natural source budget; only qualifying REQUIRED_HEADWATER cells can admit sources.");
        }
        return undergroundEnabled ? budget : SourceBudget.disabled();
    }

    private static SourceBudget validateSources(
            String path,
            JSONObject sources,
            boolean surface,
            List<String> errors
    ) {
        PackJsonFieldChecks.validateOptionalDoubleRange(path, sources, "density", 0D, 64D, errors);
        if (surface) {
            PackJsonFieldChecks.validateOptionalIntegerRange(
                    path,
                    sources,
                    "minimumElevation",
                    -2048,
                    2048,
                    errors
            );
        }
        PackJsonFieldChecks.validateOptionalIntegerRange(path, sources, "minimumPerTile", 0, 64, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, sources, "minimumSpacing", 0, 8192, errors);
        return new SourceBudget(
                doubleValue(sources, "density", surface ? 0.5D : 0.25D),
                integerValue(sources, "minimumPerTile", 0)
        );
    }

    private static void validateGrottos(
            String path,
            JSONObject grottos,
            RoutingValues routing,
            List<String> errors
    ) {
        JSONObject coastal = nestedObject(grottos, "coastal", path, errors);
        JSONObject inland = nestedObject(grottos, "inland", path, errors);
        if (coastal != null) {
            validateGrotto(path + ".coastal", coastal, true, errors);
        }
        if (inland != null) {
            validateGrotto(path + ".inland", inland, false, errors);
            if (routing.inlandOutlets().contains("SINKHOLE_GROTTO")
                    && !booleanValue(inland, "enabled", true)) {
                errors.add(path + ".inland must be enabled when routing.inlandOutlets selects SINKHOLE_GROTTO.");
            }
            if (booleanValue(inland, "connectSurfaceRivers", false)
                    && !booleanValue(inland, "enabled", true)) {
                errors.add(path + ".inland.connectSurfaceRivers requires the inland grotto to be enabled.");
            }
            if (booleanValue(inland, "connectSurfaceRivers", false)
                    && !routing.inlandOutlets().contains("SINKHOLE_GROTTO")) {
                errors.add(path + ".inland.connectSurfaceRivers requires routing.inlandOutlets to select SINKHOLE_GROTTO.");
            }
        }
    }

    private static void validateGrotto(
            String path,
            JSONObject grotto,
            boolean coastal,
            List<String> errors
    ) {
        PackJsonFieldChecks.validateOptionalBoolean(path, grotto, "enabled", errors);
        if (!coastal) {
            PackJsonFieldChecks.validateOptionalBoolean(path, grotto, "connectSurfaceRivers", errors);
        }
        PackJsonFieldChecks.validateOptionalIntegerRange(path, grotto, "horizontalRadius", 1, 128, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, grotto, "verticalRadius", 1, 64, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, grotto, "headroom", 1, 63, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, grotto, "maximumVolume", 1, 1_048_576, errors);
        if (coastal && grotto.has("poolLevel")) {
            Object rawPoolLevel = grotto.opt("poolLevel");
            if (!(rawPoolLevel instanceof String poolLevel) || !"SEA_LEVEL".equals(poolLevel)) {
                errors.add(path + ".poolLevel must be SEA_LEVEL.");
            }
        }
        if (coastal) {
            // cliffMinimumHeight is nullable; a JSON null falls back to max(4, verticalRadius) at runtime.
            PackJsonFieldChecks.validateOptionalIntegerRange(path, grotto, "cliffMinimumHeight", 0, 128, errors);
            PackJsonFieldChecks.validateOptionalDoubleRange(path, grotto, "cliffSlopeFactor", 0D, 4D, errors);
            JSONObject seaCaves = nestedObject(grotto, "seaCaves", path, errors);
            if (seaCaves != null) {
                String seaCavePath = path + ".seaCaves";
                PackJsonFieldChecks.validateOptionalBoolean(seaCavePath, seaCaves, "enabled", errors);
                PackJsonFieldChecks.validateOptionalIntegerRange(seaCavePath, seaCaves, "maximumPerTile", 0, 64, errors);
                PackJsonFieldChecks.validateOptionalIntegerRange(seaCavePath, seaCaves, "minimumSpacing", 16, 8192, errors);
                PackJsonFieldChecks.validateOptionalIntegerRange(seaCavePath, seaCaves, "minimumCoastHeight", 1, 128, errors);
                PackJsonFieldChecks.validateOptionalIntegerRange(seaCavePath, seaCaves, "depth", 0, 128, errors);
                PackJsonFieldChecks.validateOptionalDoubleRange(seaCavePath, seaCaves, "sweepJitterDegrees", 0D, 90D, errors);
                int horizontalRadius = integerValue(grotto, "horizontalRadius", DEFAULT_COASTAL_GROTTO.getHorizontalRadius());
                int minimumSpacing = integerValue(seaCaves, "minimumSpacing", DEFAULT_COASTAL_GROTTO.getSeaCaves().getMinimumSpacing());
                if (minimumSpacing < horizontalRadius * 2) {
                    errors.add(seaCavePath + ".minimumSpacing must be at least twice " + path + ".horizontalRadius.");
                }
            }
        }

        int verticalRadius = integerValue(
                grotto,
                "verticalRadius",
                coastal ? DEFAULT_COASTAL_GROTTO.getVerticalRadius() : DEFAULT_INLAND_GROTTO.getVerticalRadius()
        );
        int headroom = integerValue(
                grotto,
                "headroom",
                coastal ? DEFAULT_COASTAL_GROTTO.getHeadroom() : DEFAULT_INLAND_GROTTO.getHeadroom()
        );
        if (headroom >= verticalRadius * 2 + 1) {
            errors.add(path + ".headroom must fit inside the grotto height.");
        }
    }

    private static Set<String> validateProfiles(String path, JSONObject rivers, List<String> errors) {
        if (!rivers.has("profiles")) {
            return Set.of("default");
        }
        Object rawProfiles = rivers.opt("profiles");
        if (!(rawProfiles instanceof JSONArray profiles)) {
            errors.add(path + ".profiles must be an array.");
            return Set.of();
        }
        if (profiles.length() > MAX_PROFILES) {
            errors.add(path + ".profiles must contain at most " + MAX_PROFILES + " entries.");
        }

        Set<String> profileIds = new LinkedHashSet<>();
        for (int index = 0; index < profiles.length(); index++) {
            String profilePath = path + ".profiles[" + index + "]";
            JSONObject profile = profiles.optJSONObject(index);
            if (profile == null) {
                errors.add(profilePath + " must be an object.");
                continue;
            }
            String id = requiredId(profilePath, profile, errors);
            if (id != null && !profileIds.add(id)) {
                errors.add(profilePath + ".id must be unique inside hydrology.rivers.profiles.");
            }
            validateFluidPalette(profilePath, profile, true, errors);
        }
        return Set.copyOf(profileIds);
    }

    private static Set<String> validateSurfacePools(
            String path,
            JSONObject hydrology,
            Set<String> riverProfileIds,
            Map<String, JSONObject> biomes,
            List<String> errors
    ) {
        if (!hydrology.has("surfacePools")) {
            return Set.of();
        }
        Object rawPools = hydrology.opt("surfacePools");
        if (!(rawPools instanceof JSONArray pools)) {
            errors.add(path + ".surfacePools must be an array.");
            return Set.of();
        }
        if (pools.length() > MAX_DEEP_FLUIDS) {
            errors.add(path + ".surfacePools must contain at most " + MAX_DEEP_FLUIDS + " entries.");
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (int index = 0; index < pools.length(); index++) {
            String poolPath = path + ".surfacePools[" + index + "]";
            JSONObject pool = pools.optJSONObject(index);
            if (pool == null) {
                errors.add(poolPath + " must be an object.");
                continue;
            }
            String id = requiredId(poolPath, pool, errors);
            if (id != null) {
                if (HydrologyFeatureQuery.isReservedKeyword(id)) {
                    errors.add(poolPath + ".id '" + id + "' is a reserved hydrology feature selector.");
                } else if (riverProfileIds.contains(id)) {
                    errors.add(poolPath + ".id '" + id + "' collides with a river profile.");
                } else if (!ids.add(id)) {
                    errors.add(poolPath + ".id must be unique inside hydrology.surfacePools.");
                }
            }
            validateFluidPalette(poolPath, pool, true, errors);
            PackJsonFieldChecks.validateOptionalDoubleRange(poolPath, pool, "density", 0D, 64D, errors);
            PackJsonFieldChecks.validateOptionalIntegerRange(poolPath, pool, "spacing", 32, 8192, errors);
            PackJsonFieldChecks.validateOptionalIntegerRange(poolPath, pool, "minimumRadius", 2, 16, errors);
            PackJsonFieldChecks.validateOptionalIntegerRange(poolPath, pool, "maximumRadius", 2, 16, errors);
            PackJsonFieldChecks.validateOptionalIntegerRange(poolPath, pool, "depth", 1, 8, errors);
            if (integerValue(pool, "minimumRadius", 4) > integerValue(pool, "maximumRadius", 7)) {
                errors.add(poolPath + ".minimumRadius must not exceed maximumRadius.");
            }
            Object rawBiome = pool.opt("biome");
            if (rawBiome instanceof String biome && !biome.isBlank() && !biomes.containsKey(biome)) {
                errors.add(poolPath + ".biome references unknown biome '" + biome + "'.");
            }
        }
        return Set.copyOf(ids);
    }

    private static void validatePolicyPoolsInDimension(
            String path,
            JSONObject policy,
            String dimensionKey,
            Set<String> pools,
            List<String> errors
    ) {
        if (policy == null) {
            return;
        }
        JSONArray references = policy.optJSONArray("surfacePools");
        if (references == null) {
            return;
        }
        for (int index = 0; index < references.length(); index++) {
            String pool = references.optString(index, null);
            if (pool != null && ID_PATTERN.matcher(pool).matches() && !pools.contains(pool)) {
                addDistinct(errors, path + ".surfacePools[" + index + "] references unknown surface pool '"
                        + pool + "' in Dimension '" + dimensionKey + "'.");
            }
        }
    }

    private static void validatePolicyPools(String path, JSONObject policy, List<String> errors) {
        if (!policy.has("surfacePools") || policy.opt("surfacePools") == JSONObject.NULL) {
            return;
        }
        Object rawPools = policy.opt("surfacePools");
        if (!(rawPools instanceof JSONArray pools)) {
            errors.add(path + ".surfacePools must be an array or null.");
            return;
        }
        if (pools.length() > MAX_POLICY_REFERENCES) {
            errors.add(path + ".surfacePools must contain at most " + MAX_POLICY_REFERENCES + " entries.");
        }
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < pools.length(); index++) {
            Object raw = pools.opt(index);
            String referencePath = path + ".surfacePools[" + index + "]";
            if (!(raw instanceof String id) || !ID_PATTERN.matcher(id).matches()) {
                errors.add(referencePath + " must name a surface pool id.");
                continue;
            }
            if (!seen.add(id)) {
                errors.add(referencePath + " duplicates surface pool '" + id + "'.");
            }
        }
    }

    private static boolean validateDeepFluids(
            String path,
            JSONObject hydrology,
            JSONObject dimension,
            Set<String> riverProfileIds,
            List<String> errors
    ) {
        if (!hydrology.has("deepFluids")) {
            return false;
        }
        Object rawDeepFluids = hydrology.opt("deepFluids");
        if (!(rawDeepFluids instanceof JSONArray deepFluids)) {
            errors.add(path + ".deepFluids must be an array.");
            return false;
        }
        if (deepFluids.length() > MAX_DEEP_FLUIDS) {
            errors.add(path + ".deepFluids must contain at most " + MAX_DEEP_FLUIDS + " entries.");
        }

        boolean active = false;
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < deepFluids.length(); index++) {
            String fluidPath = path + ".deepFluids[" + index + "]";
            JSONObject deepFluid = deepFluids.optJSONObject(index);
            if (deepFluid == null) {
                errors.add(fluidPath + " must be an object.");
                continue;
            }
            String id = requiredId(fluidPath, deepFluid, errors);
            if (id != null && !ids.add(id)) {
                errors.add(fluidPath + ".id must be unique inside hydrology.deepFluids.");
            }
            if (HydrologyFeatureQuery.isReservedKeyword(id)) {
                errors.add(fluidPath + ".id must not use a reserved hydrology feature keyword.");
            }
            if (id != null && riverProfileIds.contains(id)) {
                errors.add(fluidPath + ".id must not duplicate a hydrology.rivers profile id.");
            }
            validateFluidPalette(fluidPath, deepFluid, true, errors);
            NumericRange configuredHeight = validateRange(
                    fluidPath,
                    deepFluid,
                    "height",
                    -2048D,
                    2048D,
                    -192D,
                    32D,
                    errors
            );
            PackJsonFieldChecks.validateOptionalDoubleRange(fluidPath, deepFluid, "density", 0D, 64D, errors);
            PackJsonFieldChecks.validateOptionalIntegerRange(fluidPath, deepFluid, "spacing", 16, 8192, errors);
            PackJsonFieldChecks.validateOptionalIntegerRange(fluidPath, deepFluid, "horizontalRadius", 2, 128, errors);
            PackJsonFieldChecks.validateOptionalIntegerRange(fluidPath, deepFluid, "verticalRadius", 2, 64, errors);
            PackJsonFieldChecks.validateOptionalIntegerRange(fluidPath, deepFluid, "channelWidth", 1, 32, errors);
            PackJsonFieldChecks.validateOptionalIntegerRange(fluidPath, deepFluid, "depth", 1, 32, errors);
            PackJsonFieldChecks.validateOptionalIntegerRange(fluidPath, deepFluid, "headroom", 1, 63, errors);
            PackJsonFieldChecks.validateOptionalBoolean(fluidPath, deepFluid, "containedPools", errors);
            PackJsonFieldChecks.validateOptionalBoolean(fluidPath, deepFluid, "shortChannels", errors);

            int spacing = integerValue(deepFluid, "spacing", DEFAULT_DEEP_FLUID.getSpacing());
            int horizontalRadius = integerValue(
                    deepFluid,
                    "horizontalRadius",
                    DEFAULT_DEEP_FLUID.getHorizontalRadius()
            );
            int verticalRadius = integerValue(
                    deepFluid,
                    "verticalRadius",
                    DEFAULT_DEEP_FLUID.getVerticalRadius()
            );
            int channelWidth = integerValue(deepFluid, "channelWidth", DEFAULT_DEEP_FLUID.getChannelWidth());
            int depth = integerValue(deepFluid, "depth", DEFAULT_DEEP_FLUID.getDepth());
            int headroom = integerValue(deepFluid, "headroom", DEFAULT_DEEP_FLUID.getHeadroom());
            boolean containedPools = booleanValue(
                    deepFluid,
                    "containedPools",
                    DEFAULT_DEEP_FLUID.isContainedPools()
            );
            boolean shortChannels = booleanValue(
                    deepFluid,
                    "shortChannels",
                    DEFAULT_DEEP_FLUID.isShortChannels()
            );
            int footprintDiameter = (horizontalRadius + channelWidth) * 2;
            if (spacing < footprintDiameter) {
                errors.add(fluidPath + ".spacing must be at least " + footprintDiameter
                        + " to separate complete deep-fluid footprints.");
            }
            if (depth + headroom >= verticalRadius * 2 + 1) {
                errors.add(fluidPath + ".depth and headroom must fit inside verticalRadius.");
            }
            NumericRange dimensionRange = dimensionHeight(dimension);
            int lowerEnvelope = containedPools ? Math.max(depth, verticalRadius) : depth;
            if (configuredHeight.minimum() - lowerEnvelope <= dimensionRange.minimum()) {
                errors.add(fluidPath
                        + " height.min and lower footprint envelope must remain above dimensionHeight.min.");
            }
            if (configuredHeight.maximum() + headroom >= dimensionRange.maximum()) {
                errors.add(fluidPath + " height.max and headroom must remain below dimensionHeight.max.");
            }
            if (doubleValue(deepFluid, "density", DEFAULT_DEEP_FLUID.getDensity()) > 0D
                    && (containedPools || shortChannels)) {
                active = true;
            }
        }
        return active;
    }

    private static void validateRouteComplexity(
            String path,
            RoutingValues routing,
            SourceBudget surface,
            SourceBudget underground,
            List<String> errors
    ) {
        if (routing.sampleSpacing() <= 0) {
            return;
        }
        long latticeAxis = routing.tileSize() / (long) routing.sampleSpacing() + 1L;
        long latticeNodes = latticeAxis * latticeAxis;
        if (latticeNodes > MAX_LATTICE_NODES) {
            errors.add(path + ".routing produces " + latticeNodes
                    + " coarse lattice nodes, above the safe limit of " + MAX_LATTICE_NODES + ".");
        }

        long sources = surface.maximumExpectedSources() + underground.maximumExpectedSources();
        long samplesPerRoute = divideCeiling(routing.maximumRouteLength(), CORRIDOR_SAMPLE_SPACING) + 1L;
        long routeSamples = saturatedMultiply(sources, samplesPerRoute);
        if (routeSamples > MAX_ROUTE_SAMPLES) {
            errors.add(path + " may refine " + routeSamples
                    + " route samples per tile, above the safe limit of " + MAX_ROUTE_SAMPLES + ".");
        }
    }

    private static void validateMantleCapabilities(
            String path,
            JSONObject dimension,
            List<String> errors
    ) {
        if (!booleanValue(dimension, "useMantle", true)) {
            errors.add(path + " requires useMantle to be true.");
        }
        if (!booleanValue(dimension, "carvingEnabled", true)) {
            errors.add(path + " requires carvingEnabled to be true.");
        }
        if (disabled(dimension, "CARVED")) {
            errors.add(path + " requires CARVED to remain enabled.");
        }
        if (disabled(dimension, "RIVER_HYDROLOGY")) {
            errors.add(path + " requires RIVER_HYDROLOGY to remain enabled.");
        }
    }

    private static void validateResourcePolicies(
            String resourceType,
            Map<String, JSONObject> resources,
            Map<String, JSONObject> biomes,
            List<String> errors
    ) {
        for (Map.Entry<String, JSONObject> entry : resources.entrySet()) {
            validatePolicy(
                    resourceType + " '" + entry.getKey() + "'",
                    entry.getValue(),
                    "riverPolicy",
                    biomes,
                    errors
            );
        }
    }

    private static JSONObject validatePolicy(
            String ownerPath,
            JSONObject owner,
            String field,
            Map<String, JSONObject> biomes,
            List<String> errors
    ) {
        if (!owner.has(field) || owner.opt(field) == JSONObject.NULL) {
            return null;
        }
        Object rawPolicy = owner.opt(field);
        String path = ownerPath + "." + field;
        if (!(rawPolicy instanceof JSONObject policy)) {
            errors.add(path + " must be an object or null.");
            return null;
        }

        validateNullableEnum(path, policy, "placement", PLACEMENT_MODES, errors);
        validateNullableEnum(path, policy, "routing", ROUTING_MODES, errors);
        validateNullableBoolean(path, policy, "outletAdmission", errors);
        validateNullableDouble(path, policy, "surfaceSourceDensity", 0D, 64D, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, policy, "surfaceSourceSpacing", 0, 8192, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, policy, "surfaceTributaries", 0, 4, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, policy, "surfaceInlandOutlets", 0, 256, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, policy, "surfaceCoastalOutlets", 0, 64, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, policy, "surfaceMinimumCourseLength", 16, 4096, errors);
        PackJsonFieldChecks.validateOptionalIntegerRange(path, policy, "surfaceMaximumIncision", 1, 32, errors);
        validatePolicyProfiles(path, policy, errors);
        validatePolicyPools(path, policy, errors);
        for (String biomeField : POLICY_BIOME_FIELDS) {
            validatePolicyBiomes(path, policy, biomeField, biomes, errors);
        }
        validateNullableDouble(path, policy, "widthMultiplier", 0.0001D, 16D, errors);
        validateNullableDouble(path, policy, "depthMultiplier", 0.0001D, 16D, errors);
        validateNullableDouble(path, policy, "incisionMultiplier", 0D, 16D, errors);
        validateNullableDouble(path, policy, "routingMultiplier", 0D, 64D, errors);
        validateNullableDouble(path, policy, "bankMultiplier", 0D, 4D, errors);
        validateNullableDouble(path, policy, "shoreBiomeWidth", 0D, 32D, errors);
        validateNullableDouble(path, policy, "shoreWidth", 0D, 16D, errors);
        validateNullableBoolean(path, policy, "erosion", errors);
        validateNullableBoolean(path, policy, "confined", errors);
        return policy;
    }

    private static void validateReachablePolicies(
            String dimensionKey,
            JSONObject dimension,
            JSONObject dimensionPolicy,
            Set<String> profiles,
            Set<String> pools,
            Map<String, JSONObject> regions,
            Map<String, JSONObject> biomes,
            Map<String, JSONObject> imageMaps,
            List<String> errors
    ) {
        String dimensionPath = "Dimension '" + dimensionKey + "'";
        validatePolicyProfilesInDimension(
                dimensionPath + ".riverPolicy",
                dimensionPolicy,
                dimensionKey,
                profiles,
                errors
        );
        validatePolicyPoolsInDimension(
                dimensionPath + ".riverPolicy",
                dimensionPolicy,
                dimensionKey,
                pools,
                errors
        );

        Deque<String> pending = new ArrayDeque<>();
        addPolicyBiomeReferences(pending, dimensionPolicy);
        JSONArray regionKeys = dimension.optJSONArray("regions");
        if (regionKeys != null) {
            for (int index = 0; index < regionKeys.length(); index++) {
                String regionKey = regionKeys.optString(index, null);
                JSONObject region = regionKey == null ? null : regions.get(regionKey);
                if (region == null) {
                    continue;
                }
                JSONObject policy = region.optJSONObject("riverPolicy");
                validatePolicyProfilesInDimension(
                        "Region '" + regionKey + "'.riverPolicy",
                        policy,
                        dimensionKey,
                        profiles,
                        errors
                );
                validatePolicyPoolsInDimension(
                        "Region '" + regionKey + "'.riverPolicy",
                        policy,
                        dimensionKey,
                        pools,
                        errors
                );
                addPolicyBiomeReferences(pending, policy);
                for (String biomeField : REGION_BIOME_FIELDS) {
                    addStringArray(pending, region.optJSONArray(biomeField));
                }
            }
        }
        addDimensionBiomeImageMapTargets(pending, dimension, imageMaps);
        addDimensionCarvingBiomes(pending, dimension);

        Set<String> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            String biomeKey = pending.removeFirst();
            if (!visited.add(biomeKey)) {
                continue;
            }
            if (visited.size() > MAX_REACHABLE_BIOMES) {
                errors.add(dimensionPath + " hydrology reachable biome closure exceeds "
                        + MAX_REACHABLE_BIOMES + " entries.");
                return;
            }
            JSONObject biome = biomes.get(biomeKey);
            if (biome == null) {
                continue;
            }
            JSONObject policy = biome.optJSONObject("riverPolicy");
            validatePolicyProfilesInDimension(
                    "Biome '" + biomeKey + "'.riverPolicy",
                    policy,
                    dimensionKey,
                    profiles,
                    errors
            );
            validatePolicyPoolsInDimension(
                    "Biome '" + biomeKey + "'.riverPolicy",
                    policy,
                    dimensionKey,
                    pools,
                    errors
            );
            addPolicyBiomeReferences(pending, policy);
            addStringArray(pending, biome.optJSONArray("children"));
            addBiomeReference(pending, biome.optString("carvingBiome", null));
            addFloatingChildren(pending, biome.optJSONArray("floatingChildBiomes"));
        }
    }

    private static void validatePolicyProfilesInDimension(
            String path,
            JSONObject policy,
            String dimensionKey,
            Set<String> profiles,
            List<String> errors
    ) {
        if (policy == null) {
            return;
        }
        JSONArray references = policy.optJSONArray("profiles");
        if (references == null) {
            return;
        }
        for (int index = 0; index < references.length(); index++) {
            String profile = references.optString(index, null);
            if (profile != null && ID_PATTERN.matcher(profile).matches() && !profiles.contains(profile)) {
                addDistinct(errors, path + ".profiles[" + index + "] references unknown river profile '"
                        + profile + "' in Dimension '" + dimensionKey + "'.");
            }
        }
    }

    private static void validatePolicyProfiles(String path, JSONObject policy, List<String> errors) {
        if (!policy.has("profiles") || policy.opt("profiles") == JSONObject.NULL) {
            return;
        }
        Object rawProfiles = policy.opt("profiles");
        if (!(rawProfiles instanceof JSONArray profiles)) {
            errors.add(path + ".profiles must be an array or null.");
            return;
        }
        if (profiles.length() > MAX_POLICY_REFERENCES) {
            errors.add(path + ".profiles must contain at most " + MAX_POLICY_REFERENCES + " entries.");
        }
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < profiles.length(); index++) {
            Object rawProfile = profiles.opt(index);
            String referencePath = path + ".profiles[" + index + "]";
            if (!(rawProfile instanceof String profile) || !ID_PATTERN.matcher(profile).matches()) {
                errors.add(referencePath
                        + " must use 1 to 64 lowercase letters, digits, underscores, or hyphens.");
                continue;
            }
            if (!seen.add(profile)) {
                errors.add(referencePath + " duplicates river profile '" + profile + "'.");
            }
        }
    }

    private static void validatePolicyBiomes(
            String path,
            JSONObject policy,
            String field,
            Map<String, JSONObject> biomes,
            List<String> errors
    ) {
        if (!policy.has(field) || policy.opt(field) == JSONObject.NULL) {
            return;
        }
        Object rawBiomes = policy.opt(field);
        if (!(rawBiomes instanceof JSONArray references)) {
            errors.add(path + "." + field + " must be an array or null.");
            return;
        }
        if (references.length() > MAX_POLICY_REFERENCES) {
            errors.add(path + "." + field + " must contain at most "
                    + MAX_POLICY_REFERENCES + " entries.");
        }
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < references.length(); index++) {
            Object rawReference = references.opt(index);
            String referencePath = path + "." + field + "[" + index + "]";
            if (!(rawReference instanceof String biomeKey) || !validResourceKey(biomeKey)) {
                errors.add(referencePath + " must name a biome resource.");
                continue;
            }
            if (!seen.add(biomeKey)) {
                errors.add(referencePath + " duplicates biome '" + biomeKey + "'.");
            }
            if (!biomes.containsKey(biomeKey)) {
                errors.add(referencePath + " references missing biome '" + biomeKey + "'.");
            }
        }
    }

    private static NumericRange validateRange(
            String path,
            JSONObject owner,
            String field,
            double minimumAllowed,
            double maximumAllowed,
            double defaultMinimum,
            double defaultMaximum,
            List<String> errors
    ) {
        if (!owner.has(field)) {
            return NumericRange.of(defaultMinimum, defaultMaximum);
        }
        Object rawRange = owner.opt(field);
        String rangePath = path + "." + field;
        if (!(rawRange instanceof JSONObject range)) {
            errors.add(rangePath + " must be an object.");
            return NumericRange.of(defaultMinimum, defaultMaximum);
        }
        PackJsonFieldChecks.validateOptionalDoubleRange(
                rangePath,
                range,
                "min",
                minimumAllowed,
                maximumAllowed,
                errors
        );
        PackJsonFieldChecks.validateOptionalDoubleRange(
                rangePath,
                range,
                "max",
                minimumAllowed,
                maximumAllowed,
                errors
        );
        validateRangeStyle(rangePath, range, errors);
        double minimum = doubleValue(range, "min", defaultMinimum);
        double maximum = doubleValue(range, "max", defaultMaximum);
        if (Double.isFinite(minimum) && Double.isFinite(maximum) && minimum > maximum) {
            errors.add(rangePath + ".min must not exceed max.");
        }
        return NumericRange.of(minimum, maximum);
    }

    private static void validateRangeStyle(String path, JSONObject range, List<String> errors) {
        if (!range.has("style") || range.opt("style") == JSONObject.NULL) {
            return;
        }
        Object style = range.opt("style");
        if (style instanceof JSONObject) {
            return;
        }
        if (style instanceof String reference && !reference.isBlank()) {
            return;
        }
        errors.add(path + ".style must be an object or snippet reference.");
    }

    private static void validateFluidPalette(
            String path,
            JSONObject owner,
            boolean required,
            List<String> errors
    ) {
        if (!owner.has("fluidPalette")) {
            if (required) {
                errors.add(path + ".fluidPalette must be configured.");
            }
            return;
        }
        Object rawPalette = owner.opt("fluidPalette");
        if (!(rawPalette instanceof JSONObject palette)) {
            errors.add(path + ".fluidPalette must be an object.");
            return;
        }
        Object rawEntries = palette.opt("palette");
        if (!(rawEntries instanceof JSONArray entries) || entries.length() == 0) {
            errors.add(path + ".fluidPalette.palette must contain at least one fluid block.");
            return;
        }
        if (entries.length() > 64) {
            errors.add(path + ".fluidPalette.palette must contain at most 64 entries.");
        }
        for (int index = 0; index < entries.length(); index++) {
            String entryPath = path + ".fluidPalette.palette[" + index + "]";
            JSONObject entry = entries.optJSONObject(index);
            if (entry == null) {
                errors.add(entryPath + " must be an object.");
                continue;
            }
            Object rawBlock = entry.opt("block");
            if (!(rawBlock instanceof String block) || block.isBlank()) {
                errors.add(entryPath + ".block must name a fluid block.");
            } else if (definitelyNotFluid(block)) {
                errors.add(entryPath + ".block may contain only fluid blocks.");
            }
            PackJsonFieldChecks.validateOptionalIntegerRange(entryPath, entry, "weight", 1, 64, errors);
        }
    }

    private static Set<String> validateEnumArray(
            String path,
            JSONObject owner,
            String field,
            Set<String> allowed,
            List<String> errors
    ) {
        if (!owner.has(field)) {
            return Set.of();
        }
        Object rawEntries = owner.opt(field);
        if (!(rawEntries instanceof JSONArray entries)) {
            errors.add(path + "." + field + " must be an array.");
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (int index = 0; index < entries.length(); index++) {
            Object rawEntry = entries.opt(index);
            String entryPath = path + "." + field + "[" + index + "]";
            if (!(rawEntry instanceof String value) || !allowed.contains(value)) {
                errors.add(entryPath + " must be one of " + allowed + ".");
                continue;
            }
            if (!values.add(value)) {
                errors.add(entryPath + " duplicates outlet '" + value + "'.");
            }
        }
        return Set.copyOf(values);
    }

    private static String requiredId(String path, JSONObject object, List<String> errors) {
        Object rawId = object.opt("id");
        if (!(rawId instanceof String id) || !ID_PATTERN.matcher(id).matches()) {
            errors.add(path + ".id must use 1 to 64 lowercase letters, digits, underscores, or hyphens.");
            return null;
        }
        return id;
    }

    private static JSONObject nestedObject(
            JSONObject owner,
            String field,
            String path,
            List<String> errors
    ) {
        if (!owner.has(field)) {
            return new JSONObject();
        }
        return requireObject(owner, field, path + "." + field, errors);
    }

    private static JSONObject requireObject(
            JSONObject owner,
            String field,
            String path,
            List<String> errors
    ) {
        Object rawObject = owner.opt(field);
        if (!(rawObject instanceof JSONObject object)) {
            errors.add(path + " must be an object.");
            return null;
        }
        return object;
    }

    private static void validateNullableEnum(
            String path,
            JSONObject object,
            String field,
            Set<String> values,
            List<String> errors
    ) {
        if (!object.has(field) || object.opt(field) == JSONObject.NULL) {
            return;
        }
        Object rawValue = object.opt(field);
        if (!(rawValue instanceof String value) || !values.contains(value)) {
            errors.add(path + "." + field + " must be one of " + values + " or null.");
        }
    }

    private static void validateNullableBoolean(
            String path,
            JSONObject object,
            String field,
            List<String> errors
    ) {
        if (!object.has(field) || object.opt(field) == JSONObject.NULL) {
            return;
        }
        if (!(object.opt(field) instanceof Boolean)) {
            errors.add(path + "." + field + " must be a boolean or null.");
        }
    }

    private static void validateNullableDouble(
            String path,
            JSONObject object,
            String field,
            double minimum,
            double maximum,
            List<String> errors
    ) {
        if (!object.has(field) || object.opt(field) == JSONObject.NULL) {
            return;
        }
        PackJsonFieldChecks.validateOptionalDoubleRange(path, object, field, minimum, maximum, errors);
    }

    private static Map<String, JSONObject> loadResources(File folder) {
        Map<String, JSONObject> resources = new LinkedHashMap<>();
        if (!folder.isDirectory()) {
            return resources;
        }
        List<File> files = PackValidationIo.listJsonRecursive(folder);
        files.sort(Comparator.comparing(File::getPath));
        for (File file : files) {
            String key = PackValidationIo.deriveKey(folder, file);
            JSONObject resource = PackValidationIo.readJson(file);
            if (key != null && resource != null) {
                resources.put(key, resource);
            }
        }
        return resources;
    }

    private static void addPolicyBiomeReferences(Deque<String> pending, JSONObject policy) {
        if (policy == null) {
            return;
        }
        for (String field : POLICY_BIOME_FIELDS) {
            addStringArray(pending, policy.optJSONArray(field));
        }
    }

    private static void addStringArray(Deque<String> pending, JSONArray values) {
        if (values == null) {
            return;
        }
        for (int index = 0; index < values.length(); index++) {
            addBiomeReference(pending, values.optString(index, null));
        }
    }

    private static void addBiomeReference(Deque<String> pending, String biomeKey) {
        if (biomeKey != null && !biomeKey.isBlank() && !"NONE".equalsIgnoreCase(biomeKey)) {
            pending.addLast(biomeKey);
        }
    }

    private static void addDimensionCarvingBiomes(Deque<String> pending, JSONObject dimension) {
        JSONArray carving = dimension.optJSONArray("carving");
        if (carving == null) {
            return;
        }
        for (int index = 0; index < carving.length(); index++) {
            JSONObject entry = carving.optJSONObject(index);
            if (entry != null && booleanValue(entry, "enabled", true)) {
                addBiomeReference(pending, entry.optString("biome", null));
            }
        }
    }

    private static void addDimensionBiomeImageMapTargets(
            Deque<String> pending,
            JSONObject dimension,
            Map<String, JSONObject> imageMaps
    ) {
        JSONArray bindings = dimension.optJSONArray("imageMaps");
        if (bindings == null) {
            return;
        }
        for (int index = 0; index < bindings.length(); index++) {
            JSONObject binding = bindings.optJSONObject(index);
            if (binding == null || !"BIOME".equals(binding.optString("application", null))) {
                continue;
            }
            String mapKey = binding.optString("map", null);
            JSONObject imageMap = mapKey == null ? null : imageMaps.get(mapKey);
            if (imageMap == null) {
                continue;
            }
            JSONObject colors = imageMap.optJSONObject("colors");
            if (colors != null) {
                for (String color : colors.keySet()) {
                    Object rawTarget = colors.opt(color);
                    if (rawTarget instanceof String target) {
                        addImageMapBiomeReference(pending, target);
                    }
                }
            }
            Object rawFallback = imageMap.opt("fallbackTarget");
            if (rawFallback instanceof String fallbackTarget) {
                addImageMapBiomeReference(pending, fallbackTarget);
            }
        }
    }

    private static void addImageMapBiomeReference(Deque<String> pending, String target) {
        addBiomeReference(pending, target.startsWith("iris:") ? target.substring("iris:".length()) : target);
    }

    private static void addFloatingChildren(Deque<String> pending, JSONArray floatingChildren) {
        if (floatingChildren == null) {
            return;
        }
        for (int index = 0; index < floatingChildren.length(); index++) {
            JSONObject floatingChild = floatingChildren.optJSONObject(index);
            if (floatingChild != null) {
                addBiomeReference(pending, floatingChild.optString("biome", null));
            }
        }
    }

    private static NumericRange dimensionHeight(JSONObject dimension) {
        JSONObject height = dimension.optJSONObject("dimensionHeight");
        if (height == null) {
            return NumericRange.of(-64D, 320D);
        }
        return NumericRange.of(
                doubleValue(height, "min", -64D),
                doubleValue(height, "max", 320D)
        );
    }

    private static boolean disabled(JSONObject dimension, String flag) {
        JSONArray disabled = dimension.optJSONArray("disabledComponents");
        if (disabled == null) {
            return false;
        }
        for (int index = 0; index < disabled.length(); index++) {
            if (flag.equals(disabled.optString(index, null))) {
                return true;
            }
        }
        return false;
    }

    private static boolean validResourceKey(String resourceKey) {
        return RESOURCE_PATTERN.matcher(resourceKey).matches()
                && !resourceKey.contains("..")
                && !resourceKey.startsWith("/")
                && !resourceKey.endsWith("/");
    }

    private static boolean definitelyNotFluid(String block) {
        String normalized = block.trim().toLowerCase(Locale.ROOT);
        int stateStart = normalized.indexOf('[');
        if (stateStart >= 0) {
            normalized = normalized.substring(0, stateStart);
        }
        int namespaceSeparator = normalized.indexOf(':');
        String namespace = namespaceSeparator < 0 ? "minecraft" : normalized.substring(0, namespaceSeparator);
        String key = namespaceSeparator < 0 ? normalized : normalized.substring(namespaceSeparator + 1);
        return "minecraft".equals(namespace)
                && !"water".equals(key)
                && !"lava".equals(key)
                && !"bubble_column".equals(key);
    }

    private static boolean booleanValue(JSONObject object, String field, boolean defaultValue) {
        Object rawValue = object.opt(field);
        return rawValue instanceof Boolean value ? value : defaultValue;
    }

    private static int integerValue(JSONObject object, String field, int defaultValue) {
        Object rawValue = object.opt(field);
        if (!(rawValue instanceof Number number)
                || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != StrictMath.rint(number.doubleValue())
                || number.longValue() < Integer.MIN_VALUE
                || number.longValue() > Integer.MAX_VALUE) {
            return defaultValue;
        }
        return number.intValue();
    }

    private static double doubleValue(JSONObject object, String field, double defaultValue) {
        Object rawValue = object.opt(field);
        return rawValue instanceof Number number && Double.isFinite(number.doubleValue())
                ? number.doubleValue()
                : defaultValue;
    }

    private static long divideCeiling(long dividend, long divisor) {
        return dividend / divisor + (dividend % divisor == 0L ? 0L : 1L);
    }

    private static long saturatedMultiply(long left, long right) {
        if (left == 0L || right == 0L) {
            return 0L;
        }
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        return left * right;
    }

    private static void addDistinct(List<String> destination, String value) {
        if (!destination.contains(value)) {
            destination.add(value);
        }
    }

    record Validation(List<String> errors, List<String> warnings) {
        Validation {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
        }
    }

    private record HydrologyValidation(
            boolean riversEnabled,
            boolean deepFluidsActive,
            Set<String> profileIds,
            Set<String> surfacePoolIds
    ) {
        private static HydrologyValidation inactive() {
            return new HydrologyValidation(false, false, Set.of(), Set.of());
        }

        private boolean active() {
            return riversEnabled || deepFluidsActive;
        }
    }

    private record RoutingValues(
            int tileSize,
            int sampleSpacing,
            int maximumRouteLength,
            Set<String> inlandOutlets
    ) {
        private static RoutingValues defaults() {
            return new RoutingValues(2048, 64, 16384, Set.of());
        }
    }

    private record SourceBudget(double density, int minimumPerTile) {
        private static SourceBudget defaults() {
            return new SourceBudget(0.5D, 0);
        }

        private static SourceBudget disabled() {
            return new SourceBudget(0D, 0);
        }

        private long maximumExpectedSources() {
            if (!Double.isFinite(density) || density <= 0D) {
                return Math.max(0, minimumPerTile);
            }
            return Math.max(Math.max(0, minimumPerTile), (long) StrictMath.ceil(density));
        }
    }

    private record NumericRange(double minimum, double maximum) {
        private static NumericRange of(double minimum, double maximum) {
            return new NumericRange(minimum, maximum);
        }
    }
}
