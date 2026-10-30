package art.arcane.iris.pack.datapack.export;

import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.PackValidationResult;
import art.arcane.iris.pack.PackValidator;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.compile.PackCompiler;
import art.arcane.iris.pack.datapack.export.compile.noise.NoiseStyleCompiler;
import art.arcane.iris.pack.datapack.export.emit.UnusedResourcePruner;
import art.arcane.iris.pack.datapack.export.emit.WorldgenJsonWriter;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportIr;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.iris.pack.datapack.export.lower.GenerationBudget;
import art.arcane.iris.pack.datapack.export.lower.GenerationCostCounter;
import art.arcane.iris.pack.datapack.export.lower.VanillaBounds;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisLogging;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class WorldgenDatapackExporter implements WorldgenExportService {
    private static final String STAGING_PREFIX = ".iris-worldgen-export-";
    private static final Pattern UNSAFE_OUTPUT_CHARACTER = Pattern.compile("[^A-Za-z0-9._-]");

    private final Function<DataVersion, WorldgenJsonWriter> writers;
    private final PackCompiler compiler;

    public WorldgenDatapackExporter(Function<DataVersion, WorldgenJsonWriter> writers, PackCompiler compiler) {
        this.writers = Objects.requireNonNull(writers);
        this.compiler = Objects.requireNonNull(compiler);
    }

    public static WorldgenDatapackExporter create() {
        return new WorldgenDatapackExporter(WorldgenJsonWriter::forTarget, PackCompiler.create());
    }

    @Override
    public WorldgenExportResult export(WorldgenExportRequest request, WorldgenExportProgress progress) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(progress);
        ExportPlan plan;
        try {
            plan = plan(request);
        } catch (IllegalArgumentException exception) {
            return WorldgenExportResult.rejected(null, exception.getMessage(), List.of());
        }
        Path destination = plan.destination();
        if (Files.exists(destination) && !request.replace()) {
            return rejected(destination, "Export output already exists: " + destination + ". Pass replace=true to overwrite it.");
        }
        if (!DatapackExportDestinations.claim(destination)) {
            return rejected(destination, "An export to " + destination + " is already running.");
        }
        try {
            return exportClaimed(request, plan, progress);
        } finally {
            DatapackExportDestinations.release(destination);
        }
    }

    private ExportPlan plan(WorldgenExportRequest request) {
        DataVersion target = WorldgenExportTargets.resolve(request.version());
        WorldgenExportMode mode = WorldgenExportMode.parse(request.mode());
        DatapackExportFormat format = DatapackExportDestinations.parseFormat(request.format());
        String namespace = VanillaResourceIdentifier.requireNamespace(request.namespace(), "export namespace");
        String output = request.output().isEmpty() ? defaultOutput(request.dimension(), target) : request.output();
        return new ExportPlan(target, mode, format, namespace, DatapackExportDestinations.resolve(request.exportRoot(), output, format));
    }

    private WorldgenExportResult exportClaimed(WorldgenExportRequest request, ExportPlan plan, WorldgenExportProgress progress) {
        Path destination = plan.destination();
        File pack = request.pack().toFile();
        try {
            progress.stage("validate");
            PackValidationResult validation = PackValidator.validateForPackaging(pack);
            if (!validation.isLoadable()) {
                return rejected(destination, "Pack '" + validation.getPackName() + "' failed validation: "
                        + String.join("; ", validation.getBlockingErrors()));
            }
            progress.stage("load");
            IrisData data = IrisData.openRuntime(pack);
            try {
                IrisDimension dimension = data.getDimensionLoader().load(request.dimension(), false);
                if (dimension == null) {
                    return rejected(destination, "Dimension '" + request.dimension() + "' was not found in pack '" + pack.getName() + "'.");
                }
                PackCompileContext context = compileContext(data, dimension, plan, request.objects());
                ExportIr ir = compiler.compile(context, progress);
                return publish(request, plan, ir, context.biomeCodes().loadKeyByBiomeId(), progress);
            } finally {
                data.close();
            }
        } catch (IOException exception) {
            IrisLogging.reportError("Worldgen datapack publication to " + destination + " failed", exception);
            return WorldgenExportResult.failed(destination, "Datapack publication failed: " + exception.getMessage(), List.of());
        } catch (RuntimeException exception) {
            IrisLogging.reportError("Worldgen datapack export of dimension '" + request.dimension() + "' failed", exception);
            return WorldgenExportResult.failed(destination, "Datapack export failed: " + exception.getMessage(), List.of());
        }
    }

    private WorldgenExportResult publish(WorldgenExportRequest request, ExportPlan plan, ExportIr ir, Map<String, String> loadKeyByBiomeId,
                                         WorldgenExportProgress progress) throws IOException {
        progress.stage("write");
        GenerationCost routerCost = GenerationCostCounter.count(ir.router(), VanillaBounds.of(ir.namespace(), ir.noises()), ir.target());
        SortedMap<String, byte[]> files = writers.apply(plan.target()).write(ir);
        progress.resources(files.size(), files.size());
        progress.stage("prune");
        UnusedResourcePruner.Pruned pruned = UnusedResourcePruner.prune(files);
        progress.trimmed(pruned.trim());
        GenerationCost cost = routerCost.withDensityFunctionBytes(GenerationCost.densityFunctionBytes(pruned.files()));
        progress.stage("publish");
        AtomicDatapackPublisher.Publication publication = new AtomicDatapackPublisher().publish(
                pruned.files(),
                plan.destination(),
                plan.format(),
                request.replace(),
                STAGING_PREFIX);
        List<ExportDiagnostic> costDiagnostics = GenerationBudget.check(cost, ir.dimension().name());
        List<ExportDiagnostic> diagnostics = new ArrayList<>(ir.diagnostics().size() + pruned.diagnostics().size() + costDiagnostics.size());
        diagnostics.addAll(ir.diagnostics());
        diagnostics.addAll(costDiagnostics);
        diagnostics.addAll(pruned.diagnostics());
        return new WorldgenExportResult(
                WorldgenExportResult.Status.EXPORTED,
                plan.destination(),
                String.join("; ", publication.cleanupWarnings()),
                diagnostics,
                loadKeyByBiomeId,
                pruned.trim(),
                cost);
    }

    private static PackCompileContext compileContext(IrisData data, IrisDimension dimension, ExportPlan plan, boolean objects) {
        List<ExportDiagnostic> diagnostics = new ArrayList<>();
        return PackCompileContext.builder()
                .data(data)
                .dimension(dimension)
                .capabilities(ExportCapabilities.forTarget(plan.target()))
                .namespace(plan.namespace())
                .mode(plan.mode())
                .objects(objects)
                .diagnostics(diagnostics)
                .styles(NoiseStyleCompiler.create(diagnostics))
                .build();
    }

    private static String defaultOutput(String dimension, DataVersion target) {
        return UNSAFE_OUTPUT_CHARACTER.matcher(dimension.replace('/', '-')).replaceAll("-") + "-" + target.getVersion();
    }

    private static WorldgenExportResult rejected(Path destination, String message) {
        return WorldgenExportResult.rejected(destination, message, List.of());
    }

    private record ExportPlan(DataVersion target, WorldgenExportMode mode, DatapackExportFormat format, String namespace, Path destination) {
    }
}
