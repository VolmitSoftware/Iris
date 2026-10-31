package art.arcane.iris.structure.export;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.AtomicDatapackPublisher;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VanillaJigsawDatapackExporter {
    private static final String STAGING_PREFIX = ".iris-jigsaw-export-";
    private static final String PACK_METADATA = "pack.mcmeta";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    public VanillaJigsawExportValidation validate(VanillaJigsawExportRequest request) {
        DataVersion target = DataVersion.getRuntime();
        VanillaJigsawExportCompiler.Compilation compilation = new VanillaJigsawExportCompiler(target).compile(request.unit());
        List<String> resources = new ArrayList<>(files(request, target, compilation).keySet());
        resources.sort(String::compareTo);
        return new VanillaJigsawExportValidation(resources, compilation.diagnostics());
    }

    public VanillaJigsawExportResult export(VanillaJigsawExportRequest request) {
        if (Files.exists(request.output()) && !request.replaceExisting()) {
            VanillaJigsawExportDiagnostic diagnostic = new VanillaJigsawExportDiagnostic(
                    VanillaJigsawExportDiagnostic.Severity.ERROR,
                    VanillaJigsawExportDiagnostic.Code.OUTPUT_EXISTS,
                    request.output().toString(),
                    "Export output already exists and replaceExisting is false.");
            return new VanillaJigsawExportResult(
                    VanillaJigsawExportResult.Status.REJECTED,
                    request.output(),
                    List.of(),
                    List.of(diagnostic));
        }

        DataVersion target = DataVersion.getRuntime();
        VanillaJigsawExportCompiler.Compilation compilation = new VanillaJigsawExportCompiler(target).compile(request.unit());
        if (compilation.hasErrors()) {
            return new VanillaJigsawExportResult(
                    VanillaJigsawExportResult.Status.REJECTED,
                    request.output(),
                    List.of(),
                    compilation.diagnostics());
        }

        Map<String, byte[]> files = files(request, target, compilation);
        List<VanillaJigsawExportDiagnostic> diagnostics = new ArrayList<>(compilation.diagnostics());
        try {
            AtomicDatapackPublisher.Publication publication = new AtomicDatapackPublisher().publish(
                    files,
                    request.output(),
                    request.format(),
                    request.replaceExisting(),
                    STAGING_PREFIX);
            for (String warning : publication.cleanupWarnings()) {
                diagnostics.add(new VanillaJigsawExportDiagnostic(
                        VanillaJigsawExportDiagnostic.Severity.WARNING,
                        VanillaJigsawExportDiagnostic.Code.CLEANUP_FAILED,
                        request.output().toString(),
                        warning));
            }
        } catch (IOException exception) {
            diagnostics.add(new VanillaJigsawExportDiagnostic(
                    VanillaJigsawExportDiagnostic.Severity.ERROR,
                    VanillaJigsawExportDiagnostic.Code.PUBLICATION_FAILED,
                    request.output().toString(),
                    "Atomic datapack publication failed: " + exception.getMessage()));
            return new VanillaJigsawExportResult(
                    VanillaJigsawExportResult.Status.FAILED,
                    request.output(),
                    List.of(),
                    diagnostics);
        }

        List<String> resources = new ArrayList<>(files.keySet());
        resources.sort(String::compareTo);
        return new VanillaJigsawExportResult(
                VanillaJigsawExportResult.Status.EXPORTED,
                request.output(),
                resources,
                diagnostics);
    }

    private static Map<String, byte[]> files(
            VanillaJigsawExportRequest request,
            DataVersion target,
            VanillaJigsawExportCompiler.Compilation compilation
    ) {
        if (compilation.hasErrors()) {
            return Map.of();
        }
        Map<String, byte[]> files = new LinkedHashMap<>(compilation.resources().size() + 1);
        files.put(PACK_METADATA, (GSON.toJson(packMetadata(request.description(), target)) + "\n").getBytes(StandardCharsets.UTF_8));
        files.putAll(compilation.resources());
        return files;
    }

    private static JsonObject packMetadata(String description, DataVersion target) {
        JsonObject pack = new JsonObject();
        pack.addProperty("description", description);
        if (target == DataVersion.V1_21_1) {
            pack.addProperty("pack_format", target.getPackFormat());
            JsonObject root = new JsonObject();
            root.add("pack", pack);
            return root;
        }
        JsonArray minimum = new JsonArray();
        minimum.add(target.getPackFormat());
        minimum.add(target.getPackFormatMinor());
        pack.add("min_format", minimum);
        pack.addProperty("max_format", target.getPackFormat());
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return root;
    }
}
