package art.arcane.iris.core.structure.export;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public final class VanillaJigsawDatapackExporter {
    public VanillaJigsawExportValidation validate(VanillaJigsawExportRequest request) {
        VanillaJigsawExportCompiler.Compilation compilation = new VanillaJigsawExportCompiler().compile(request);
        List<String> resources = new ArrayList<>(compilation.resources().keySet());
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

        VanillaJigsawExportCompiler.Compilation compilation = new VanillaJigsawExportCompiler().compile(request);
        if (compilation.hasErrors()) {
            return new VanillaJigsawExportResult(
                    VanillaJigsawExportResult.Status.REJECTED,
                    request.output(),
                    List.of(),
                    compilation.diagnostics());
        }

        List<VanillaJigsawExportDiagnostic> diagnostics = new ArrayList<>(compilation.diagnostics());
        try {
            AtomicDatapackPublisher.Publication publication = new AtomicDatapackPublisher().publish(
                    compilation.resources(),
                    request.output(),
                    request.format(),
                    request.replaceExisting());
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

        List<String> resources = new ArrayList<>(compilation.resources().keySet());
        resources.sort(String::compareTo);
        return new VanillaJigsawExportResult(
                VanillaJigsawExportResult.Status.EXPORTED,
                request.output(),
                resources,
                diagnostics);
    }
}
