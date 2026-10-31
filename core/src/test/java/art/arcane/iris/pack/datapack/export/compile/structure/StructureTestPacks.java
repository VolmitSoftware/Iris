package art.arcane.iris.pack.datapack.export.compile.structure;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.compile.DimensionStage;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeCodes;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeDefinitionCompiler;
import art.arcane.iris.pack.datapack.export.ir.BinaryResource;
import art.arcane.iris.pack.datapack.export.ir.ClimateLayout;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.CompatTestPlatform;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class StructureTestPacks {
    static final String NAMESPACE = "iris_export";
    static final String FOREST = "iris_export:forest_land";
    static final String MAPPED = "iris_export:mapped";
    static final String OCEAN = "iris_export:ocean";
    static final String DESERT = "iris_export:desert";
    static final Map<String, String> EXPORTED = Map.of(FOREST, "Forest Land", MAPPED, "mapped", OCEAN, "ocean", DESERT, "desert");
    private static final Field ZERO = new Field.Const(0.0);

    private StructureTestPacks() {
    }

    static File pack(File folder, String dimensionJson, String secondRegionStructures) throws IOException {
        File pack = ExportTestPacks.withDimension(folder, dimensionJson);
        CompatTestPlatform.write(pack, "regions/second.json",
                "{\"landBiomes\":[\"desert\"],\"seaBiomes\":[\"ocean\"],\"structures\":" + secondRegionStructures + "}");
        CompatTestPlatform.write(pack, "biomes/Forest Land.json", "{\"name\":\"Forest Land\",\"derivative\":\"minecraft:forest\"}");
        CompatTestPlatform.write(pack, "biomes/mapped.json", "{\"name\":\"Mapped\",\"derivative\":\"minecraft:plains\"}");
        CompatTestPlatform.write(pack, "biomes/desert.json", "{\"name\":\"Desert\",\"derivative\":\"minecraft:desert\"}");
        return pack;
    }

    static String dimension(List<File> imports, String extra) {
        List<String> sources = new ArrayList<>(imports.size());
        for (File archive : imports) {
            sources.add("\"" + archive.toURI().toASCIIString() + "\"");
        }
        return "{\"regions\":[\"region\",\"second\"],\"datapackImports\":[" + String.join(",", sources) + "]"
                + (extra.isEmpty() ? "" : "," + extra) + "}";
    }

    static File zip(File folder, String name, Map<String, String> entries) throws IOException {
        File archive = new File(folder, name);
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(archive))) {
            for (Map.Entry<String, String> entry : new TreeMap<>(entries).entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return archive;
    }

    static String metadata(int minimum, int maximum) {
        return "{\"pack\":{\"description\":\"test\",\"min_format\":" + minimum + ",\"max_format\":" + maximum + "}}";
    }

    static PackCompileContext context(IrisData data) {
        PackCompileContext context = ExportTestPacks.context(data, DataVersion.V26_3);
        new DimensionStage().apply(context);
        context.setBiomeCodes(new BiomeCodes(ZERO, ZERO, ZERO, new ClimateLayout(List.of()), EXPORTED));
        BiomeDefinitionCompiler.Definitions definitions = BiomeDefinitionCompiler.forTarget(DataVersion.V26_3).compile(context, EXPORTED);
        context.setBiomes(definitions.biomes());
        context.jsonResources().addAll(definitions.tags());
        return context;
    }

    static PackCompileContext compiled(IrisData data) {
        PackCompileContext context = context(data);
        new StructureStage().apply(context);
        return context;
    }

    static SortedMap<String, JsonElement> json(PackCompileContext context) {
        SortedMap<String, JsonElement> resources = new TreeMap<>();
        for (JsonResource resource : context.jsonResources()) {
            if (resources.put(resource.path(), resource.json()) != null) {
                throw new AssertionError("Duplicate json resource " + resource.path());
            }
        }
        return resources;
    }

    static SortedMap<String, String> binaries(PackCompileContext context) {
        SortedMap<String, String> resources = new TreeMap<>();
        for (BinaryResource resource : context.binaryResources()) {
            if (resources.put(resource.path(), new String(resource.bytes(), StandardCharsets.UTF_8)) != null) {
                throw new AssertionError("Duplicate binary resource " + resource.path());
            }
        }
        return resources;
    }

    static List<ExportDiagnostic> diagnostics(PackCompileContext context, ExportLimit limit) {
        List<ExportDiagnostic> matching = new ArrayList<>();
        for (ExportDiagnostic diagnostic : context.diagnostics()) {
            if (diagnostic.limit() == limit) {
                matching.add(diagnostic);
            }
        }
        return matching;
    }

    static JsonElement parse(String json) {
        return JsonParser.parseString(json);
    }
}
