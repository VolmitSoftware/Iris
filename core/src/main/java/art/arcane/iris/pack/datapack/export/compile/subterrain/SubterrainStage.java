package art.arcane.iris.pack.datapack.export.compile.subterrain;

import art.arcane.iris.generation.subterrain.IrisSubterrainFeature;
import art.arcane.iris.generation.subterrain.SubterrainRasterizer;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import art.arcane.iris.pack.datapack.export.compile.CompileStage;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.compile.structure.PlacementTags;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public final class SubterrainStage implements CompileStage {
    private static final String NAME = "subterrain";
    private static final long BAKE_SEED = 0x5ab7e4a1c0de5eedL;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void apply(PackCompileContext context) {
        IrisDimension dimension = context.dimension();
        if (!dimension.allowsSubterrainFeatures() || dimension.getSubterrainFeatures() == null) {
            return;
        }
        List<IrisSubterrainFeature> features = new ArrayList<>(dimension.getSubterrainFeatures().size());
        for (IrisSubterrainFeature feature : dimension.getSubterrainFeatures()) {
            if (feature != null && feature.isEnabled()) {
                features.add(feature);
            }
        }
        if (features.isEmpty()) {
            return;
        }
        SubterrainRasterizer.validateMaterials(features);
        new Run(context).compile(features);
    }

    private static final class Run {
        private static final String TAG_FOLDER = "has_structure/";

        private final PackCompileContext context;
        private final String dimensionKey;
        private final SubterrainBaker baker;
        private final RoomStructures structures;
        private final PlacementTags tags;
        private final Set<String> paths = new HashSet<>();

        private Run(PackCompileContext context) {
            this.context = context;
            dimensionKey = context.dimension().getLoadKey();
            baker = new SubterrainBaker(context, BAKE_SEED);
            structures = new RoomStructures(context.namespace(), context.capabilities().target());
            tags = PlacementTags.collect(context);
        }

        private void compile(List<IrisSubterrainFeature> features) {
            for (IrisSubterrainFeature feature : features) {
                String resource = "dimensions/" + dimensionKey + "/subterrainFeatures/" + feature.getId();
                String path = unique(RoomStructures.FOLDER + VanillaResourceIdentifier.sanitizePath(feature.getId()));
                Optional<String> biomes = biomes(feature, path, resource);
                if (biomes.isEmpty()) {
                    continue;
                }
                Optional<BakedFeature> baked = baker.bake(feature, resource);
                if (baked.isEmpty()) {
                    continue;
                }
                RoomStructures.Compiled compiled = structures.compile(feature, baked.get(), path, biomes.get());
                context.jsonResources().addAll(compiled.json());
                context.binaryResources().addAll(compiled.templates());
                report(feature, baked.get(), resource);
            }
        }

        private Optional<String> biomes(IrisSubterrainFeature feature, String path, String resource) {
            SortedSet<String> regions = new TreeSet<>(feature.getAllowedRegions());
            List<String> scopes = new ArrayList<>(Math.max(1, regions.size()));
            SortedSet<String> shared = new TreeSet<>();
            if (regions.isEmpty()) {
                tags.dimensionTag().ifPresent(scopes::add);
            }
            for (String region : regions) {
                tags.regionTag(region).ifPresent(scopes::add);
                shared.addAll(tags.sharedBiomes(region));
            }
            if (scopes.isEmpty()) {
                diagnose(ExportDiagnostic.Severity.WARNING, ExportLimit.STRUCTURE_PLACEMENT, resource,
                        "No exported biome lies in the room's allowed regions, so the room is not exported");
                return Optional.empty();
            }
            if (!shared.isEmpty()) {
                diagnose(ExportDiagnostic.Severity.INFO, ExportLimit.STRUCTURE_PLACEMENT, resource,
                        "Region scoping follows biomes: " + String.join(", ", shared)
                                + " also belong to other regions, where the room generates too");
            }
            if (scopes.size() == 1) {
                return Optional.of("#" + scopes.getFirst());
            }
            JsonArray values = new JsonArray(scopes.size());
            for (String scope : scopes) {
                values.add("#" + scope);
            }
            JsonObject tag = new JsonObject();
            tag.addProperty("replace", false);
            tag.add("values", values);
            context.jsonResources().add(new JsonResource("data/" + context.namespace() + "/tags/worldgen/biome/" + TAG_FOLDER + path + ".json", tag));
            return Optional.of("#" + context.namespace() + ":" + TAG_FOLDER + path);
        }

        private void report(IrisSubterrainFeature feature, BakedFeature baked, String resource) {
            diagnose(ExportDiagnostic.Severity.INFO, ExportLimit.SUBTERRAIN_VARIANT, resource,
                    "The room exports as " + baked.rooms().size() + " fixed shapes placed with a random quarter turn, not a new shape per placement");
            diagnose(ExportDiagnostic.Severity.INFO, ExportLimit.SUBTERRAIN_TERRAIN, resource,
                    "The room is placed as a structure after terrain and caves: its walls are painted only where rock remains, it does not stop below"
                            + " oceans, rivers or cave pools, rooms do not yield to each other by priority, and later decoration can overlap it");
            if (feature.getBiome() != null && !feature.getBiome().isBlank()) {
                diagnose(ExportDiagnostic.Severity.INFO, ExportLimit.SUBTERRAIN_BIOME, resource,
                        "The room biome '" + feature.getBiome().trim() + "' is not exported: the room keeps the surrounding biome, its mob spawns,"
                                + " ambience, decorators and objects, while the room biome's floor, ceiling and wall blocks are baked into the room");
            }
        }

        private String unique(String path) {
            String candidate = path;
            for (int suffix = 1; !paths.add(candidate); suffix++) {
                candidate = path + "_" + suffix;
            }
            return candidate;
        }

        private void diagnose(ExportDiagnostic.Severity severity, ExportLimit limit, String resource, String message) {
            context.diagnostics().add(new ExportDiagnostic(severity, limit, resource, message));
        }
    }
}
