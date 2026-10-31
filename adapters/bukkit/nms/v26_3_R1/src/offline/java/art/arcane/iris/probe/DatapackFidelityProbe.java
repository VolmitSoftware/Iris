package art.arcane.iris.probe;

import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import art.arcane.iris.pack.datapack.export.WorldgenDatapackExporter;
import art.arcane.iris.pack.datapack.export.WorldgenExportMode;
import art.arcane.iris.pack.datapack.export.WorldgenExportProgress;
import art.arcane.iris.pack.datapack.export.WorldgenExportRequest;
import art.arcane.iris.pack.datapack.export.WorldgenExportResult;
import art.arcane.iris.pack.datapack.export.WorldgenExportService;
import art.arcane.iris.pack.datapack.export.compile.surface.MaterialProgramCompiler;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.probe.fidelity.BiomeShareReport;
import art.arcane.iris.probe.fidelity.BiomeTypes;
import art.arcane.iris.probe.fidelity.ColumnSource;
import art.arcane.iris.probe.fidelity.ColumnSurvey;
import art.arcane.iris.probe.fidelity.FidelityComparison;
import art.arcane.iris.probe.fidelity.FidelityReport;
import art.arcane.iris.probe.fidelity.FidelitySampling;
import art.arcane.iris.probe.fidelity.FidelitySamples;
import art.arcane.iris.probe.fidelity.FidelityScanner;
import art.arcane.iris.probe.fidelity.FidelitySource;
import art.arcane.iris.probe.fidelity.FidelityStats;
import art.arcane.iris.probe.fidelity.FidelityThresholds;
import art.arcane.iris.probe.fidelity.IrisEngineColumnSource;
import art.arcane.iris.probe.fidelity.IrisEngineFidelitySource;
import art.arcane.iris.probe.fidelity.SurveyWindow;
import art.arcane.iris.probe.fidelity.VanillaNoiseColumnSource;
import art.arcane.iris.probe.fidelity.VanillaNoiseFidelitySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

public final class DatapackFidelityProbe {
    private static final String LOG_PREFIX = "[datapack-fidelity]";
    private static final String USAGE = "Usage: datapackFidelityProbe pack dimension seed radiusChunks stride output surveyBlocks surveyStride surveySeeds mode";
    private static final String TARGET_VERSION = "26.3";
    private static final String EXPORT_FORMAT = "directory";
    private static final String EXPORT_OUTPUT = "export";
    private static final int MAX_STRIDE = 16;
    private static final int CHUNK_WIDTH = 16;
    private static final int ARGUMENTS = 10;
    private static final String COLUMN_SCOPE = "columns";
    private static final String CHUNK_SCOPE = "chunks";
    private static final int EXIT_ERROR = 1;
    private static final int EXIT_FIDELITY_FAILED = 3;

    private DatapackFidelityProbe() {
    }

    public static void main(String[] arguments) {
        int status;
        try {
            status = execute(arguments, WorldgenDatapackExporter.create()).passed() ? 0 : EXIT_FIDELITY_FAILED;
        } catch (Throwable failure) {
            failure.printStackTrace(System.err);
            status = EXIT_ERROR;
        }
        System.out.flush();
        System.err.flush();
        System.exit(status);
    }

    static FidelityReport execute(String[] arguments, WorldgenExportService exporter) throws Exception {
        Objects.requireNonNull(exporter, "exporter");
        Configuration configuration = Configuration.parse(arguments);
        System.out.println(LOG_PREFIX + " pack=" + configuration.pack() + " dimension=" + configuration.dimension()
                + " seed=" + configuration.seed() + " radiusChunks=" + configuration.radiusChunks() + " stride=" + configuration.stride()
                + " surveyBlocks=" + configuration.survey().size() + " surveyStride=" + configuration.survey().stride()
                + " surveySeeds=" + configuration.surveySeeds() + " mode=" + configuration.mode().label());
        Outcome outcome = compare(configuration, exporter);
        writeReport(configuration.output(), outcome.json());
        System.out.print(outcome.table());
        System.out.println(LOG_PREFIX + " report=" + configuration.output());
        return outcome.report();
    }

    private static Outcome compare(Configuration configuration, WorldgenExportService exporter) throws Exception {
        Path scratch = Files.createTempDirectory("iris-datapack-fidelity-");
        try {
            return compareIn(scratch, configuration, exporter);
        } finally {
            ProbeWorkspaceCleanup.deleteRecursively(scratch);
        }
    }

    private static Outcome compareIn(Path scratch, Configuration configuration, WorldgenExportService exporter) throws Exception {
        HeadlessNativeRuntime runtime = new HeadlessNativeRuntime();
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(new RealPackProbeSupport.WorkspaceOptions(
                configuration.pack().toFile(), configuration.dimension(), LOG_PREFIX, runtime, scratch))) {
            WorldgenExportResult export = export(exporter, workspace.pack().toPath(), configuration.dimension(), scratch.resolve("exports"));
            Target target = new Target(export.output(), WorldgenExportRequest.DEFAULT_NAMESPACE,
                    VanillaResourceIdentifier.sanitizePath(configuration.dimension()), export.loadKeyByBiomeId());
            return switch (configuration.mode()) {
                case FULL -> Outcome.of(compareAll(workspace, target, configuration));
                case BIOMES -> Outcome.of(compareBiomes(workspace, target, configuration));
            };
        }
    }

    private static FidelityReport compareAll(RealPackProbeSupport.Workspace workspace, Target target, Configuration configuration) throws Exception {
        List<Long> seeds = configuration.surveySeeds();
        FidelityStats irisColumns = null;
        FidelityStats exportColumns = null;
        FidelitySamples chunks = null;
        for (long seed : seeds) {
            try (RealPackProbeSupport.EngineSession session = workspace.openEngine(seed, false, "fidelity-" + seed)) {
                Surveyed surveyed = survey(session.engine(), target, seed, configuration.survey());
                irisColumns = irisColumns == null ? surveyed.iris() : irisColumns.merge(surveyed.iris());
                exportColumns = exportColumns == null ? surveyed.export() : exportColumns.merge(surveyed.export());
                if (seed == configuration.seed()) {
                    chunks = scanChunks(session.engine(), target, configuration);
                }
            }
        }
        FidelitySampling columnSampling = new FidelitySampling(COLUMN_SCOPE, configuration.survey().size(), configuration.survey().stride(),
                seeds, irisColumns.columns());
        return FidelityComparison.compare(new FidelitySamples(irisColumns, exportColumns, columnSampling), chunks, FidelityThresholds.defaults());
    }

    private static BiomeShareReport compareBiomes(RealPackProbeSupport.Workspace workspace, Target target, Configuration configuration) throws Exception {
        List<FidelitySamples> seeds = new ArrayList<>(configuration.seedCount());
        Map<String, String> biomeTypes = Map.of();
        for (long seed : configuration.surveySeeds()) {
            try (RealPackProbeSupport.EngineSession session = workspace.openEngine(seed, false, "biomes-" + seed)) {
                Engine engine = session.engine();
                Surveyed surveyed = survey(engine, target, seed, configuration.survey());
                if (biomeTypes.isEmpty()) {
                    biomeTypes = BiomeTypes.of(engine.getDimension().getAllRegions(engine), key -> engine.getData().getBiomeLoader().load(key));
                }
                seeds.add(new FidelitySamples(surveyed.iris(), surveyed.export(), new FidelitySampling(COLUMN_SCOPE, configuration.survey().size(),
                        configuration.survey().stride(), List.of(seed), surveyed.iris().columns())));
            }
        }
        return FidelityComparison.compareBiomeShares(seeds, biomeTypes, FidelityThresholds.defaults());
    }

    private static Surveyed survey(Engine engine, Target target, long seed, SurveyWindow window) throws Exception {
        try (ColumnSource iris = new IrisEngineColumnSource(engine);
             ColumnSource vanilla = new VanillaNoiseColumnSource(target.sampler(seed), target.loadKeyByBiomeId())) {
            requireSameHeightRange(iris.minY(), iris.maxY(), vanilla.minY(), vanilla.maxY());
            return new Surveyed(surveyed(iris, window, seed), surveyed(vanilla, window, seed));
        }
    }

    private static FidelityStats surveyed(ColumnSource source, SurveyWindow window, long seed) {
        long started = System.nanoTime();
        FidelityStats stats = ColumnSurvey.scan(source, window, MultiBurst.burst.parallelism());
        System.out.println(LOG_PREFIX + " surveyed " + source.label() + " seed=" + seed + " columns=" + stats.columns()
                + " in " + (System.nanoTime() - started) / 1_000_000L + "ms");
        return stats;
    }

    private static FidelitySamples scanChunks(Engine engine, Target target, Configuration configuration) throws Exception {
        try (IrisEngineFidelitySource iris = new IrisEngineFidelitySource(engine);
             VanillaNoiseFidelitySource vanilla = new VanillaNoiseFidelitySource(target.sampler(configuration.seed()), target.loadKeyByBiomeId(),
                     target.namespace() + ":" + MaterialProgramCompiler.WALL_SHELL)) {
            requireSameHeightRange(iris.minY(), iris.maxY(), vanilla.minY(), vanilla.maxY());
            FidelityStats irisStats = scan(iris, configuration);
            FidelityStats exportStats = scan(vanilla, configuration);
            FidelitySampling sampling = new FidelitySampling(CHUNK_SCOPE, (2 * configuration.radiusChunks() + 1) * CHUNK_WIDTH,
                    configuration.stride(), List.of(configuration.seed()), irisStats.columns());
            return new FidelitySamples(irisStats, exportStats, sampling);
        }
    }

    private static WorldgenExportResult export(WorldgenExportService exporter, Path pack, String dimension, Path exportRoot) throws IOException {
        Files.createDirectories(exportRoot);
        WorldgenExportRequest request = WorldgenExportRequest.builder()
                .pack(pack)
                .dimension(dimension)
                .version(TARGET_VERSION)
                .format(EXPORT_FORMAT)
                .output(EXPORT_OUTPUT)
                .exportRoot(exportRoot)
                .build();
        WorldgenExportResult result = exporter.export(request, WorldgenExportProgress.NONE);
        if (!result.isSuccess()) {
            throw new IllegalStateException("Datapack export of dimension '" + dimension + "' was " + result.status() + ": " + result.message());
        }
        System.out.println(LOG_PREFIX + " exported target=" + TARGET_VERSION + " biomes=" + result.loadKeyByBiomeId().size()
                + " diagnostics=" + result.diagnostics().size() + " " + diagnosticCounts(result.diagnostics()));
        return result;
    }

    private static FidelityStats scan(FidelitySource source, Configuration configuration) {
        long started = System.nanoTime();
        FidelityStats stats = FidelityScanner.scan(source, 0, 0, configuration.radiusChunks(), configuration.stride(),
                MultiBurst.burst.parallelism());
        System.out.println(LOG_PREFIX + " sampled " + source.label() + " columns=" + stats.columns() + " terrainColumns=" + stats.heightColumns()
                + " in " + (System.nanoTime() - started) / 1_000_000L + "ms");
        return stats;
    }

    private static void requireSameHeightRange(int irisMinY, int irisMaxY, int exportMinY, int exportMaxY) {
        if (irisMinY != exportMinY || irisMaxY != exportMaxY) {
            throw new IllegalStateException("Iris height range [" + irisMinY + ", " + irisMaxY
                    + ") differs from the exported dimension range [" + exportMinY + ", " + exportMaxY + ")");
        }
    }

    private static SortedMap<String, Integer> diagnosticCounts(List<ExportDiagnostic> diagnostics) {
        SortedMap<String, Integer> counts = new TreeMap<>();
        for (ExportDiagnostic diagnostic : diagnostics) {
            counts.merge(diagnostic.limit().name(), 1, Integer::sum);
        }
        return counts;
    }

    private static void writeReport(Path output, String json) throws IOException {
        Path parent = output.getParent();
        Files.createDirectories(parent);
        Path staged = Files.createTempFile(parent, ".datapack-fidelity-", ".json");
        try {
            Files.writeString(staged, json + "\n", StandardCharsets.UTF_8);
            Files.move(staged, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private enum Mode {
        FULL,
        BIOMES;

        private static Mode parse(String value) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (Mode mode : values()) {
                if (mode.label().equals(normalized)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("mode must be full or biomes, got '" + value + "'");
        }

        private String label() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private record Outcome(FidelityReport report, String json, String table) {
        private static Outcome of(FidelityReport report) {
            return new Outcome(report, report.toJson(), report.toTable());
        }

        private static Outcome of(BiomeShareReport report) {
            return new Outcome(report.mean(), report.toJson(), report.toTable());
        }
    }

    private record Surveyed(FidelityStats iris, FidelityStats export) {
    }

    private record Target(Path output, String namespace, String dimension, Map<String, String> loadKeyByBiomeId) {
        private WorldgenExportSampler sampler(long seed) throws IOException {
            return WorldgenExportSampler.open(output, WorldgenExportMode.OVERWORLD, namespace + ":" + dimension, seed);
        }
    }

    private record Configuration(Path pack, String dimension, long seed, int radiusChunks, int stride, Path output, SurveyWindow survey,
                                 int seedCount, Mode mode) {
        private static Configuration parse(String[] arguments) {
            if (arguments.length != ARGUMENTS) {
                throw new IllegalArgumentException(USAGE);
            }
            Path pack = Path.of(arguments[0]).toAbsolutePath().normalize();
            if (!Files.isDirectory(pack)) {
                throw new IllegalArgumentException("Pack folder " + pack + " does not exist");
            }
            String dimension = arguments[1].trim();
            if (dimension.isEmpty()) {
                throw new IllegalArgumentException("Dimension key must not be blank");
            }
            long seed = parseLong(arguments[2], "seed");
            int radiusChunks = parseInt(arguments[3], "radiusChunks");
            if (radiusChunks < 0) {
                throw new IllegalArgumentException("radiusChunks must be zero or positive, got " + radiusChunks);
            }
            int stride = parseInt(arguments[4], "stride");
            if (stride < 1 || stride > MAX_STRIDE) {
                throw new IllegalArgumentException("stride must be within [1, " + MAX_STRIDE + "], got " + stride);
            }
            Path output = Path.of(arguments[5]).toAbsolutePath().normalize();
            if (Files.isDirectory(output)) {
                throw new IllegalArgumentException("Report output " + output + " is a directory");
            }
            SurveyWindow survey = new SurveyWindow(0, 0, parseInt(arguments[6], "surveyBlocks"), parseInt(arguments[7], "surveyStride"));
            int seedCount = parseInt(arguments[8], "surveySeeds");
            if (seedCount < 1) {
                throw new IllegalArgumentException("surveySeeds must be at least 1, got " + seedCount);
            }
            Mode mode = Mode.parse(arguments[9]);
            return new Configuration(pack, dimension, seed, radiusChunks, stride, output, survey, seedCount, mode);
        }

        private List<Long> surveySeeds() {
            List<Long> seeds = new ArrayList<>(seedCount);
            for (int index = 0; index < seedCount; index++) {
                seeds.add(seed + index);
            }
            return seeds;
        }

        private static long parseLong(String value, String name) {
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException failure) {
                throw new IllegalArgumentException(name + " must be an integer, got '" + value + "'", failure);
            }
        }

        private static int parseInt(String value, String name) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException failure) {
                throw new IllegalArgumentException(name + " must be an integer, got '" + value + "'", failure);
            }
        }
    }
}
