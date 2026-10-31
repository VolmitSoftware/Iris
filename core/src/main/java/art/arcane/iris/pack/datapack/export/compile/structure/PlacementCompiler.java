package art.arcane.iris.pack.datapack.export.compile.structure;

import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import art.arcane.iris.pack.datapack.export.compile.ExportedBiomes;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.ir.BinaryResource;
import art.arcane.iris.pack.datapack.export.ir.DimensionDef;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import art.arcane.iris.structure.export.VanillaJigsawExportCompiler;
import art.arcane.iris.structure.export.VanillaJigsawExportDiagnostic;
import art.arcane.iris.structure.export.VanillaJigsawExportSettings;
import art.arcane.iris.structure.export.VanillaJigsawExportSource;
import art.arcane.iris.structure.export.VanillaJigsawExportUnit;
import art.arcane.iris.structure.nativegen.IrisNativeStructure;
import art.arcane.iris.structure.nativegen.NativeStructureSuppression;
import art.arcane.iris.structure.placement.IrisStructure;
import art.arcane.iris.structure.placement.IrisStructureAnchorMode;
import art.arcane.iris.structure.placement.IrisStructurePlacement;
import art.arcane.iris.structure.placement.StructureDistribution;
import art.arcane.volmlib.util.structure.StructureTerrainMode;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

final class PlacementCompiler {
    private static final String ROOT = "placement/";
    private static final String JSON_SUFFIX = ".json";
    private static final int MAX_SPACING = 4096;
    private static final int MAX_RING_DISTANCE = 1023;
    private static final int MAX_RING_SPREAD = 1023;
    private static final int MAX_RING_COUNT = 4095;
    private static final int LISTED_ISSUES = 3;

    private final PackCompileContext context;
    private final StructureDefinitions definitions;
    private final ExportedBiomes biomes;
    private final VanillaJigsawExportCompiler jigsaw;

    PlacementCompiler(PackCompileContext context, StructureDefinitions definitions, ExportedBiomes biomes) {
        this.context = context;
        this.definitions = definitions;
        this.biomes = biomes;
        jigsaw = new VanillaJigsawExportCompiler(context.capabilities().target());
    }

    static Set<String> suppressedSources(PackCompileContext context) {
        SortedSet<String> sources = new TreeSet<>();
        if (context.dimension().getStructures() == null) {
            return sources;
        }
        for (IrisStructurePlacement placement : context.dimension().getStructures()) {
            if (placement == null || placement.getNativeSuppression() != NativeStructureSuppression.REPLACE_SOURCE) {
                continue;
            }
            if (placement.hasNativeStructures()) {
                for (IrisNativeStructure source : placement.getNativeStructures()) {
                    if (source != null && source.getStructure() != null && !source.getStructure().isBlank()) {
                        sources.add(TagFile.member(source.getStructure()));
                    }
                }
            }
            if (placement.hasIrisStructures()) {
                for (String key : placement.getStructures()) {
                    IrisStructure structure = key == null ? null : VanillaJigsawExportSource.forData(context.data(), key).loadStructure();
                    if (structure != null && structure.getVanillaSource() != null && !structure.getVanillaSource().isBlank()) {
                        sources.add(TagFile.member(structure.getVanillaSource()));
                    }
                }
            }
        }
        return sources;
    }

    void compile() {
        for (PlacementScopes.Scope scope : PlacementScopes.collect(context, biomes)) {
            if (scope.placements().isEmpty()) {
                continue;
            }
            if (scope.biomeIds().isEmpty()) {
                diagnose(ExportDiagnostic.Severity.WARNING, ExportLimit.STRUCTURE_PLACEMENT, scope.label(),
                        "No exported biome lies in this scope, so its structure placements are not exported");
                continue;
            }
            String tag = ROOT + scope.name();
            boolean tagged = false;
            for (int index = 0; index < scope.placements().size(); index++) {
                IrisStructurePlacement placement = scope.placements().get(index);
                if (placement != null && compile(new Build(scope, index, placement, tag)) && !tagged) {
                    PlacementTags.write(context, tag, scope.biomeIds());
                    tagged = true;
                }
            }
        }
    }

    private boolean compile(Build build) {
        IrisStructurePlacement placement = build.placement;
        if (placement.isAnchoredUnderground() && build.band == null) {
            diagnose(ExportDiagnostic.Severity.WARNING, ExportLimit.STRUCTURE_PLACEMENT, build.resource,
                    "The placement's height band lies outside the dimension, so it is not exported");
            return false;
        }
        JsonObject spread = spread(build);
        if (spread == null) {
            reportDropped(build);
            return false;
        }
        if (placement.hasNativeStructures()) {
            for (IrisNativeStructure source : placement.getNativeStructures()) {
                if (source != null) {
                    nativeEntry(build, source);
                }
            }
        }
        if (placement.hasIrisStructures()) {
            StructureTerrainMode terrain = placement.resolvedTerrain().resolvedMode();
            if (terrain != StructureTerrainMode.SOURCE && terrain != StructureTerrainMode.PRESERVE) {
                build.dropped.add("terrain mode " + terrain.name());
            }
            for (String key : placement.getStructures()) {
                if (key != null && !key.isBlank()) {
                    jigsawEntry(build, key.trim());
                }
            }
        }
        if (placement.getStilt() != null) {
            build.dropped.add("stilt foundations");
        }
        if (!build.scope.shared().isEmpty()) {
            build.dropped.add("region scoping (" + String.join(", ", build.scope.shared())
                    + " also belong to other regions, where the structure generates too)");
        }
        reportDropped(build);
        if (build.entries.isEmpty()) {
            return false;
        }
        JsonArray structures = new JsonArray(build.entries.size());
        for (Map.Entry<String, Integer> entry : build.entries.entrySet()) {
            JsonObject structure = new JsonObject();
            structure.addProperty("structure", entry.getKey());
            structure.addProperty("weight", entry.getValue());
            structures.add(structure);
        }
        JsonObject set = new JsonObject();
        set.add("structures", structures);
        set.add("placement", spread);
        context.jsonResources().add(new JsonResource(VanillaJigsawExportCompiler.structureSetPath(context.namespace(), build.name), set));
        return true;
    }

    private void nativeEntry(Build build, IrisNativeStructure source) {
        String key = source.getStructure() == null ? "" : TagFile.member(source.getStructure());
        if (!VanillaResourceIdentifier.validIdentifier(key)) {
            diagnose(ExportDiagnostic.Severity.WARNING, ExportLimit.STRUCTURE_PLACEMENT, build.resource,
                    "Native structure '" + source.getStructure() + "' is not a valid structure id; the entry is not exported");
            return;
        }
        Optional<JsonObject> definition = definitions.read(key);
        if (definition.isEmpty()) {
            diagnose(ExportDiagnostic.Severity.WARNING, ExportLimit.VANILLA_STRUCTURE_DATA, build.resource, "Native structure '" + key
                    + "' has no definition in the bundled datapacks" + (definitions.vanillaAvailable() ? "" : " and vanilla structure data for Minecraft "
                    + context.capabilities().target().getVersion() + " is not available on this server") + "; the entry is not exported");
            return;
        }
        JsonObject structure = definition.get().deepCopy();
        boolean jigsawType = StructureJson.jigsaw(structure);
        StructureJson.scope(structure, context.namespace() + ":" + build.tag);
        if (source.getJigsaw() != null && source.getJigsaw().hasOverrides()) {
            if (jigsawType) {
                JigsawOverrides.apply(structure, source.getJigsaw());
            } else {
                build.dropped.add("jigsaw overrides on the non-jigsaw structure " + key);
            }
        }
        if (build.band != null) {
            if (jigsawType) {
                StructureJson.band(structure, build.band.minimum(), build.band.maximum());
            } else {
                build.dropped.add("height band on the non-jigsaw structure " + key);
            }
        }
        StructureTerrainMode terrain = build.placement.resolvedTerrain().resolvedMode();
        if (terrain == StructureTerrainMode.PRESERVE) {
            structure.addProperty("terrain_adaptation", "none");
        } else if (terrain != StructureTerrainMode.SOURCE) {
            build.dropped.add("terrain mode " + terrain.name());
        }
        String id = context.namespace() + ":" + build.name + "/" + VanillaResourceIdentifier.sanitizePath(key.replace(':', '/'));
        if (build.entries.containsKey(id)) {
            build.entries.merge(id, Math.max(1, source.getWeight()), Integer::sum);
            return;
        }
        context.jsonResources().add(new JsonResource(StructureDefinitions.path(id), structure));
        build.entries.put(id, Math.max(1, source.getWeight()));
    }

    private void jigsawEntry(Build build, String key) {
        String resourcePath = build.name + "/" + VanillaResourceIdentifier.sanitizePath(key);
        String id = context.namespace() + ":" + resourcePath;
        if (build.entries.containsKey(id)) {
            build.entries.merge(id, 1, Integer::sum);
            return;
        }
        VanillaJigsawExportCompiler.Compilation compilation = jigsaw.compile(new VanillaJigsawExportUnit(
                VanillaJigsawExportSource.forData(context.data(), key), context.namespace(), resourcePath, settings(build)));
        if (!reportJigsaw(build, key, compilation)) {
            return;
        }
        String structurePath = VanillaJigsawExportCompiler.structurePath(context.namespace(), resourcePath);
        String skippedSet = VanillaJigsawExportCompiler.structureSetPath(context.namespace(), resourcePath);
        String skippedTag = VanillaJigsawExportCompiler.biomeTagPath(context.namespace(), resourcePath);
        for (Map.Entry<String, byte[]> resource : new TreeMap<>(compilation.resources()).entrySet()) {
            String path = resource.getKey();
            if (path.equals(skippedSet) || path.equals(skippedTag)) {
                continue;
            }
            if (!path.endsWith(JSON_SUFFIX)) {
                context.binaryResources().add(new BinaryResource(path, resource.getValue()));
                continue;
            }
            JsonObject json = JsonParser.parseString(new String(resource.getValue(), StandardCharsets.UTF_8)).getAsJsonObject();
            if (path.equals(structurePath)) {
                StructureJson.scope(json, context.namespace() + ":" + build.tag);
                if (build.band != null) {
                    StructureJson.band(json, build.band.minimum(), build.band.maximum());
                }
            }
            context.jsonResources().add(new JsonResource(path, json));
        }
        build.entries.put(id, 1);
    }

    private VanillaJigsawExportSettings settings(Build build) {
        VanillaJigsawExportSettings.Builder settings = VanillaJigsawExportSettings.builder()
                .biomes(List.copyOf(build.scope.biomeIds()))
                .terrainAdaptation(VanillaJigsawExportSettings.TerrainAdaptation.NONE);
        if (build.band == null) {
            return settings.build();
        }
        return settings.startHeight(build.band.minimum() + (build.band.maximum() - build.band.minimum()) / 2)
                .projectHeightmap(VanillaJigsawExportSettings.ProjectHeightmap.NONE)
                .generationStep(VanillaJigsawExportSettings.GenerationStep.UNDERGROUND_STRUCTURES)
                .build();
    }

    private boolean reportJigsaw(Build build, String key, VanillaJigsawExportCompiler.Compilation compilation) {
        List<String> errors = new ArrayList<>();
        for (VanillaJigsawExportDiagnostic diagnostic : compilation.diagnostics()) {
            if (diagnostic.isBlocking()) {
                errors.add(diagnostic.message());
            } else {
                diagnose(ExportDiagnostic.Severity.INFO, ExportLimit.JIGSAW_STRUCTURE, "structures/" + key, diagnostic.message());
            }
        }
        if (!compilation.hasErrors()) {
            return true;
        }
        String listed = String.join(" ", errors.stream().limit(LISTED_ISSUES).toList()) + (errors.size() > LISTED_ISSUES ? " ..." : "");
        diagnose(ExportDiagnostic.Severity.WARNING, ExportLimit.JIGSAW_STRUCTURE, "structures/" + key,
                "Iris structure '" + key + "' placed by " + build.resource + " cannot be exported as a vanilla jigsaw: " + listed);
        return false;
    }

    private JsonObject spread(Build build) {
        IrisStructurePlacement placement = build.placement;
        StructureDistribution distribution = placement.getDistribution() == null ? StructureDistribution.RANDOM_SPREAD : placement.getDistribution();
        JsonObject spread = new JsonObject();
        spread.addProperty("salt", placement.getSalt() & Integer.MAX_VALUE);
        switch (distribution) {
            case RANDOM_SPREAD -> {
                if (placement.getSpacing() < 1 || placement.getSpacing() > MAX_SPACING
                        || placement.getSeparation() < 0 || placement.getSeparation() >= placement.getSpacing()) {
                    build.dropped.add("random spread spacing " + placement.getSpacing() + " with separation " + placement.getSeparation()
                            + " (vanilla needs 0 <= separation < spacing <= " + MAX_SPACING + "), so the placement is not exported");
                    return null;
                }
                spread.addProperty("type", "minecraft:random_spread");
                spread.addProperty("spacing", placement.getSpacing());
                spread.addProperty("separation", placement.getSeparation());
            }
            case DENSITY -> {
                spread.addProperty("type", "minecraft:random_spread");
                spread.addProperty("spacing", 1);
                spread.addProperty("separation", 0);
                spread.addProperty("frequency", (float) Math.max(0.0, Math.min(1.0, placement.getDensity())));
            }
            case CONCENTRIC_RINGS -> {
                spread.addProperty("type", "minecraft:concentric_rings");
                spread.addProperty("distance", ring(build, "ring distance", placement.getRingDistance(), 0, MAX_RING_DISTANCE));
                spread.addProperty("spread", ring(build, "ring spread", placement.getRingSpread(), 0, MAX_RING_SPREAD));
                spread.addProperty("count", ring(build, "ring count", placement.getRingCount(), 1, MAX_RING_COUNT));
                spread.addProperty("preferred_biomes", "#" + context.namespace() + ":" + build.tag);
            }
        }
        return spread;
    }

    private static int ring(Build build, String field, int value, int minimum, int maximum) {
        int clamped = Math.max(minimum, Math.min(maximum, value));
        if (clamped != value) {
            build.dropped.add(field + " " + value + " (clamped to " + clamped + ")");
        }
        return clamped;
    }

    private void reportDropped(Build build) {
        if (!build.dropped.isEmpty()) {
            diagnose(ExportDiagnostic.Severity.INFO, ExportLimit.STRUCTURE_PLACEMENT, build.resource,
                    "Placement settings with no vanilla equivalent are not exported: " + String.join("; ", build.dropped));
        }
    }

    private void diagnose(ExportDiagnostic.Severity severity, ExportLimit limit, String resource, String message) {
        context.diagnostics().add(new ExportDiagnostic(severity, limit, resource, message));
    }

    private record Band(int minimum, int maximum) {
    }

    private final class Build {
        private final PlacementScopes.Scope scope;
        private final IrisStructurePlacement placement;
        private final String name;
        private final String tag;
        private final String resource;
        private final Band band;
        private final Map<String, Integer> entries = new LinkedHashMap<>();
        private final Set<String> dropped = new LinkedHashSet<>();

        private Build(PlacementScopes.Scope scope, int index, IrisStructurePlacement placement, String tag) {
            this.scope = scope;
            this.placement = placement;
            this.tag = tag;
            name = ROOT + scope.name() + "/" + index;
            resource = scope.label() + "/structures[" + index + "]";
            band = band(placement);
        }

        private Band band(IrisStructurePlacement source) {
            DimensionDef dimension = context.dimensionDef();
            int bottom = dimension.minY();
            int top = dimension.minY() + dimension.height() - 1;
            int minimum = Math.max(source.getMinHeight(), bottom);
            int maximum = Math.min(source.getMaxHeight(), top);
            IrisStructureAnchorMode anchor = source.resolvedAnchor();
            if (!source.isAnchoredUnderground()) {
                if (minimum > bottom || maximum < top) {
                    dropped.add("surface height gate " + source.getMinHeight() + ".." + source.getMaxHeight());
                }
                return null;
            }
            if (anchor.isCave()) {
                dropped.add("cave anchor " + anchor.name() + " (exported as a height band)");
            }
            return minimum > maximum ? null : new Band(minimum, maximum);
        }
    }
}
