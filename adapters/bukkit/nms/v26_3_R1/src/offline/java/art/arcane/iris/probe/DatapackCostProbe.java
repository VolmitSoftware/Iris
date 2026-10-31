package art.arcane.iris.probe;

import art.arcane.iris.pack.datapack.export.WorldgenDatapackExporter;
import art.arcane.iris.pack.datapack.export.WorldgenExportProgress;
import art.arcane.iris.pack.datapack.export.WorldgenExportRequest;
import art.arcane.iris.pack.datapack.export.WorldgenExportResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class DatapackCostProbe {
    private static final String LOG_PREFIX = "[datapack-cost]";
    static final String VANILLA_PRESET = "minecraft:normal";
    private static final int WARMUP_ORIGIN = -4096;
    private static final int MINIMUM_CALLS = 8;
    private static final int TOP_OFFENDERS = 10;
    private static final long ANALYSIS_STACK_BYTES = 1L << 30;
    private static final double MEGABYTE = 1024.0 * 1024.0;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private DatapackCostProbe() {
    }

    public static void main(String[] arguments) {
        PrintStream diagnostics = System.err;
        try {
            execute(Configuration.parse(arguments));
        } catch (Throwable failure) {
            failure.printStackTrace(diagnostics);
            diagnostics.flush();
            System.exit(1);
        }
        System.out.flush();
        System.exit(0);
    }

    private static void execute(Configuration configuration) throws Exception {
        HeadlessNativeBootstrap.initialize();
        Files.createDirectories(configuration.work());
        Path work = Files.createTempDirectory(configuration.work(), "run-");
        List<HeadlessNativeRegistries> loaded = new ArrayList<>(2);
        try {
            log("exporting " + configuration.dimension() + " from " + configuration.pack());
            ExportRun export = export(configuration, work);
            log(String.format(Locale.ROOT, "exported in %.1f s: %d files, %d density functions", export.stats().seconds(),
                    export.stats().files(), export.stats().densityFunctions()));
            DensityJsonBreakdown.Report densityJson = onLargeStack("density-json",
                    () -> DensityJsonBreakdown.analyze(export.files(), configuration.dimension()).report());
            Path datapack = work.resolve("datapack");
            writeFiles(export.files(), datapack);
            Path vanillaPack = Files.createDirectories(work.resolve("vanilla"));
            ExportStats exportStats = export.stats();
            export.files().clear();

            long baseline = settledHeap();
            long loadStarted = System.nanoTime();
            HeadlessNativeRegistries vanilla = isolatedLoad(vanillaPack);
            double vanillaLoad = (System.nanoTime() - loadStarted) / NANOS_PER_SECOND;
            loaded.add(vanilla);
            long vanillaHeap = settledHeap();
            loadStarted = System.nanoTime();
            HeadlessNativeRegistries exported = isolatedLoad(datapack);
            double exportLoad = (System.nanoTime() - loadStarted) / NANOS_PER_SECOND;
            loaded.add(exported);
            long exportHeap = settledHeap();
            log(String.format(Locale.ROOT, "registries loaded: vanilla %.2f s, export %.2f s", vanillaLoad, exportLoad));

            WorldgenStageBench vanillaBench = new WorldgenStageBench(new WorldgenStageBench.Options("vanilla",
                    presetOverworld(vanilla.registries(), VANILLA_PRESET), vanilla.registries(), configuration.seed()));
            warm(vanillaBench, configuration);
            long vanillaSamplers = settledHeap();
            WorldgenStageBench exportBench = new WorldgenStageBench(new WorldgenStageBench.Options("export",
                    exportedOverworld(exported.registries()), exported.registries(), configuration.seed()));
            warm(exportBench, configuration);
            long exportSamplers = settledHeap();

            double loadStart = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
            Measurement vanillaMeasurement = new Measurement(vanillaBench);
            Measurement exportMeasurement = new Measurement(exportBench);
            measure(List.of(vanillaMeasurement, exportMeasurement), configuration);
            double loadEnd = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
            Map<String, GeneratorStats> generators = new LinkedHashMap<>();
            generators.put("vanilla", vanillaMeasurement.stats(vanillaLoad, vanillaHeap - baseline, vanillaSamplers - exportHeap));
            generators.put("export", exportMeasurement.stats(exportLoad, exportHeap - vanillaHeap, exportSamplers - vanillaSamplers));

            log("analyzing density function graphs");
            Map<String, List<DensityCostAnalysis.RootCost>> roots = new LinkedHashMap<>();
            roots.put("vanilla", onLargeStack("density-cost-vanilla", () -> analyzeRoots(vanillaBench.settings(), configuration.dimension())));
            roots.put("export", onLargeStack("density-cost-export", () -> analyzeRoots(exportBench.settings(), configuration.dimension())));

            Report report = new Report(configuration.pack().toString(), configuration.dimension(), configuration.seed(),
                    configuration.rounds() * configuration.side() * configuration.side(), configuration.warmup() * configuration.warmup(),
                    configuration.rounds(), new MachineLoad(Runtime.getRuntime().availableProcessors(), loadStart, loadEnd),
                    "single thread for chunk stages and calls, vanilla and export interleaved per round; registry load uses HeadlessNativeRegistries' 4-thread loader; export uses Iris's own pools",
                    "feature decoration not measured: Paper's applyBiomeDecoration needs a live ServerLevel and the headless feature world lives in test fixtures",
                    exportStats, generators, densityJson, roots, definitions());
            writeReport(report, configuration.output());
            System.out.println(table(report));
            log("report written to " + configuration.output());
        } finally {
            for (HeadlessNativeRegistries registries : loaded) {
                registries.close();
            }
            deleteTree(work);
        }
    }

    private static ExportRun export(Configuration configuration, Path work) throws Exception {
        Path exportRoot = Files.createDirectories(work.resolve("exports"));
        StageClock clock = new StageClock();
        WorldgenExportResult result;
        long started;
        long finished;
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(new RealPackProbeSupport.WorkspaceOptions(
                configuration.pack().toFile(), configuration.dimension(), LOG_PREFIX, new HeadlessNativeRuntime(), work))) {
            WorldgenExportRequest request = WorldgenExportRequest.builder()
                    .pack(workspace.pack().toPath())
                    .dimension(configuration.dimension())
                    .exportRoot(exportRoot)
                    .objects(true)
                    .build();
            started = System.nanoTime();
            clock.start(started);
            result = WorldgenDatapackExporter.create().export(request, clock);
            finished = System.nanoTime();
        }
        if (!result.isSuccess()) {
            throw new IllegalStateException("Export failed: " + result.message() + " " + result.diagnostics());
        }
        SortedMap<String, byte[]> files = zipEntries(result.output());
        ExportStats stats = new ExportStats((finished - started) / NANOS_PER_SECOND, clock.seconds(finished), Files.size(result.output()),
                files.size(), count(files, "/worldgen/density_function/"), bytes(files, "/worldgen/density_function/"),
                count(files, "/worldgen/noise/"), count(files, "/worldgen/biome/"), result.diagnostics().size());
        Files.delete(result.output());
        return new ExportRun(stats, files);
    }

    private static void warm(WorldgenStageBench bench, Configuration configuration) {
        long started = System.nanoTime();
        bench.chunks(WARMUP_ORIGIN, WARMUP_ORIGIN, configuration.warmup(), new WorldgenStageBench.ChunkSamples());
        WorldgenStageBench.CallBudget budget = new WorldgenStageBench.CallBudget(MINIMUM_CALLS * 4, MINIMUM_CALLS,
                TimeUnit.SECONDS.toNanos(configuration.callSeconds()) / 4);
        bench.uncachedBiome(budget, new WorldgenStageBench.CallSamples());
        bench.baseHeight(budget, Heightmap.Types.WORLD_SURFACE_WG, new WorldgenStageBench.CallSamples());
        bench.baseColumn(budget, new WorldgenStageBench.CallSamples());
        log(String.format(Locale.ROOT, "%s warmed in %.1f s", bench.label(), (System.nanoTime() - started) / NANOS_PER_SECOND));
    }

    private static void measure(List<Measurement> measurements, Configuration configuration) {
        int stride = configuration.side() + 4;
        WorldgenStageBench.CallBudget budget = new WorldgenStageBench.CallBudget(
                Math.max(1, configuration.calls() / configuration.rounds()), Math.max(1, MINIMUM_CALLS / configuration.rounds()),
                TimeUnit.SECONDS.toNanos(configuration.callSeconds()) / configuration.rounds());
        for (int round = 0; round < configuration.rounds(); round++) {
            for (int index = 0; index < measurements.size(); index++) {
                Measurement measurement = measurements.get((index + round) % measurements.size());
                long started = System.nanoTime();
                measurement.bench.chunks(round * stride, round * stride, configuration.side(), measurement.chunks);
                measurement.bench.uncachedBiome(budget, measurement.uncachedBiome);
                measurement.bench.baseHeight(budget, Heightmap.Types.WORLD_SURFACE_WG, measurement.surfaceHeight);
                measurement.bench.baseHeight(budget, Heightmap.Types.OCEAN_FLOOR_WG, measurement.floorHeight);
                measurement.bench.baseColumn(budget, measurement.column);
                log(String.format(Locale.ROOT, "%s round %d measured in %.1f s", measurement.bench.label(), round,
                        (System.nanoTime() - started) / NANOS_PER_SECOND));
            }
        }
    }

    private static List<DensityCostAnalysis.RootCost> analyzeRoots(NoiseGeneratorSettings settings, String dimension) {
        DensityCostAnalysis analysis = new DensityCostAnalysis(id -> DensityJsonBreakdown.category(dimension, id), TOP_OFFENDERS);
        Map<String, DensityFunction> roots = new LinkedHashMap<>();
        NoiseRouter router = settings.noiseRouter();
        roots.put("temperature", router.temperature());
        roots.put("vegetation", router.vegetation());
        roots.put("continents", router.continents());
        roots.put("erosion", router.erosion());
        roots.put("depth", router.depth());
        roots.put("ridges", router.ridges());
        roots.put("chunk_surface_level", router.chunkSurfaceLevel());
        roots.put("final_density", router.finalDensity());
        if (settings.aquifers().isPresent()) {
            Aquifer.Config aquifer = settings.aquifers().get();
            roots.put("aquifer.barrier", aquifer.barrierNoise());
            roots.put("aquifer.fluid_level_floodedness", aquifer.fluidLevelFloodednessNoise());
            roots.put("aquifer.fluid_level_spread", aquifer.fluidLevelSpreadNoise());
            roots.put("aquifer.lava", aquifer.lavaNoise());
            roots.put("aquifer.exclusion", aquifer.exclusion());
            roots.put("aquifer.surface_level", aquifer.surfaceLevel());
        }
        List<DensityCostAnalysis.RootCost> costs = new ArrayList<>(roots.size());
        for (Map.Entry<String, DensityFunction> root : roots.entrySet()) {
            costs.add(analysis.analyze(root.getKey(), root.getValue()));
        }
        return costs;
    }

    static LevelStem presetOverworld(RegistryAccess registries, String preset) {
        return registries.lookupOrThrow(Registries.WORLD_PRESET)
                .getOrThrow(ResourceKey.create(Registries.WORLD_PRESET, Identifier.parse(preset))).value().overworld()
                .orElseThrow(() -> new IllegalStateException("World preset " + preset + " has no overworld"));
    }

    static LevelStem exportedOverworld(RegistryAccess registries) {
        return registries.lookupOrThrow(Registries.LEVEL_STEM).getOrThrow(LevelStem.OVERWORLD).value();
    }

    private static HeadlessNativeRegistries isolatedLoad(Path directory) {
        try (PaperRegistryScope scope = PaperRegistryScope.open()) {
            return HeadlessNativeRegistries.load(directory);
        }
    }

    private static long settledHeap() throws InterruptedException {
        for (int pass = 0; pass < 4; pass++) {
            System.gc();
            Thread.sleep(200L);
        }
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    private static <T> T onLargeStack(String name, Callable<T> work) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(null, () -> {
            try {
                result.set(work.call());
            } catch (Throwable problem) {
                failure.set(problem);
            }
        }, name, ANALYSIS_STACK_BYTES);
        thread.start();
        thread.join();
        if (failure.get() instanceof Exception exception) {
            throw exception;
        }
        if (failure.get() instanceof Error error) {
            throw error;
        }
        return result.get();
    }

    private static SortedMap<String, byte[]> zipEntries(Path zip) throws IOException {
        SortedMap<String, byte[]> files = new TreeMap<>();
        try (ZipFile archive = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    files.put(entry.getName(), archive.getInputStream(entry).readAllBytes());
                }
            }
        }
        return files;
    }

    private static void writeFiles(SortedMap<String, byte[]> files, Path directory) throws IOException {
        Files.createDirectories(directory);
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            Path target = directory.resolve(file.getKey()).normalize();
            if (!target.startsWith(directory)) {
                throw new IllegalArgumentException("Export file escapes the datapack root: " + file.getKey());
            }
            Files.createDirectories(target.getParent());
            Files.write(target, file.getValue());
        }
    }

    private static int count(SortedMap<String, byte[]> files, String folder) {
        int count = 0;
        for (String path : files.keySet()) {
            if (path.contains(folder) && path.endsWith(".json")) {
                count++;
            }
        }
        return count;
    }

    private static long bytes(SortedMap<String, byte[]> files, String folder) {
        long bytes = 0;
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            if (file.getKey().contains(folder) && file.getKey().endsWith(".json")) {
                bytes += file.getValue().length;
            }
        }
        return bytes;
    }

    private static void writeReport(Report report, Path output) throws IOException {
        Files.createDirectories(output.getParent());
        Gson gson = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
        Files.writeString(output, gson.toJson(report), StandardCharsets.UTF_8);
    }

    private static Map<String, String> definitions() {
        Map<String, String> definitions = new LinkedHashMap<>();
        definitions.put("chunks", "per stage: wall ms and current-thread CPU ms per chunk; rounds of side x side chunks alternate vanilla and export");
        definitions.put("chunks.biomes", "ChunkGenerator.createBiomes on a fresh ProtoChunk, ms per chunk over the grid plus a one-chunk ring");
        definitions.put("chunks.noise", "NoiseChunk construction (aquifer init) plus NoiseBasedChunkGenerator.doFill, ms per chunk");
        definitions.put("chunks.surface", "MaterialSystem.buildSurface with the fill's NoiseChunk and a region biome manager, ms per chunk");
        definitions.put("chunks.carverBiome", "one uncached BiomeSource lookup at the chunk origin, as ChunkAccess.carverBiome memoizes per chunk, ms per chunk");
        definitions.put("uncachedBiome", "BiomeSource.createUncachedResolver(...).getNoiseBiome at random quart positions within 32k blocks, wall us per call and mean thread CPU us per call");
        definitions.put("baseHeight", "NoiseBasedChunkGenerator.getBaseHeight as structure placement calls it, us per call");
        definitions.put("baseColumn", "NoiseBasedChunkGenerator.getBaseColumn, us per call");
        definitions.put("retainedHeapBytes", "used heap after four System.gc passes, delta across the registry load");
        definitions.put("samplerHeapBytes", "used heap delta across RandomState creation and warmup (compiled samplers and buffer pools)");
        definitions.put("roots.dagNodes", "distinct non-constant density function and spline nodes reachable, references shared");
        definitions.put("roots.cachedNodes", "non-constant evaluations per point with caches enabled: every distinct cache body once, all branches");
        definitions.put("roots.expandedNodes", "non-constant evaluations per point with every reference and cache re-expanded, all branches");
        definitions.put("roots.uncachedPointWorst", "worst single uncached point evaluation with lazy splines, interval_select and range_choice; interpolated off-lattice costs 8 corners at expanded cost; find_top_surface counts its probe steps");
        definitions.put("roots.dagShare", "share of the distinct nodes by the category of the named file that holds them; offset copies are Grad2 and Taps duplicates");
        definitions.put("roots.exclusive", "named density functions ranked by multiplicity x own nodes in the expanded tree");
        definitions.put("roots.inclusive", "named density functions ranked by multiplicity x subtree nodes in the expanded tree");
        definitions.put("densityJson.structurallyDuplicateBytes", "bytes beyond one file per JSON shape, with numbers, density function references and noise ids abstracted");
        return definitions;
    }

    private static String table(Report report) {
        StringBuilder out = new StringBuilder(4096);
        GeneratorStats vanilla = report.generators().get("vanilla");
        GeneratorStats export = report.generators().get("export");
        out.append(String.format(Locale.ROOT, "%s %s seed %d: %d chunks in %d interleaved rounds after %d warmup chunks, one thread, load %.0f-%.0f on %d cores%n",
                LOG_PREFIX, report.dimension(), report.seed(), report.chunks(), report.rounds(), report.warmupChunks(),
                report.load().loadAverageStart(), report.load().loadAverageEnd(), report.load().processors()));
        out.append(String.format(Locale.ROOT, "%-34s %10s %10s %8s %10s %10s %8s%n", "stage (mean)", "vanilla", "export", "ratio",
                "cpu van", "cpu exp", "ratio"));
        out.append(String.format(Locale.ROOT, "%-34s %10s %10.1f%n", "export (s)", "-", report.export().seconds()));
        row(out, "registry load (s)", vanilla.registryLoadSeconds(), export.registryLoadSeconds());
        row(out, "retained heap after load (MB)", vanilla.retainedHeapBytes() / MEGABYTE, export.retainedHeapBytes() / MEGABYTE);
        row(out, "sampler heap after warmup (MB)", vanilla.samplerHeapBytes() / MEGABYTE, export.samplerHeapBytes() / MEGABYTE);
        stage(out, "biome fill (ms/chunk)", vanilla.chunks().biomes(), export.chunks().biomes());
        stage(out, "noise fill (ms/chunk)", vanilla.chunks().noise(), export.chunks().noise());
        stage(out, "surface (ms/chunk)", vanilla.chunks().surface(), export.chunks().surface());
        stage(out, "carver biome lookup (ms/chunk)", vanilla.chunks().carverBiome(), export.chunks().carverBiome());
        row(out, "chunk total (ms/chunk)", vanilla.chunks().totalMean(), export.chunks().totalMean(),
                vanilla.chunks().totalCpuMean(), export.chunks().totalCpuMean());
        call(out, "uncached getNoiseBiome (us/call)", vanilla.uncachedBiome(), export.uncachedBiome());
        call(out, "getBaseHeight WORLD_SURFACE (us)", vanilla.baseHeightSurface(), export.baseHeightSurface());
        call(out, "getBaseHeight OCEAN_FLOOR (us)", vanilla.baseHeightFloor(), export.baseHeightFloor());
        call(out, "getBaseColumn (us/call)", vanilla.baseColumn(), export.baseColumn());
        out.append(String.format(Locale.ROOT, "%-24s %9s %9s %9s %10s %10s %10s %10s%n", "router root", "dag(v)", "dag",
                "cached", "expand(v)", "expanded", "uncPt(v)", "uncachedPt"));
        List<DensityCostAnalysis.RootCost> vanillaRoots = report.roots().get("vanilla");
        List<DensityCostAnalysis.RootCost> exportRoots = report.roots().get("export");
        for (DensityCostAnalysis.RootCost root : exportRoots) {
            DensityCostAnalysis.RootCost reference = find(vanillaRoots, root.root());
            out.append(String.format(Locale.ROOT, "%-24s %9s %9s %9s %10s %10s %10s %10s%n", root.root(),
                    reference == null ? "-" : compact(reference.dagNodes()), compact(root.dagNodes()), compact(root.cachedNodes()),
                    reference == null ? "-" : compact(reference.expandedNodes()), compact(root.expandedNodes()),
                    reference == null ? "-" : compact(reference.uncachedPointWorst()), compact(root.uncachedPointWorst())));
        }
        out.append("top expanded offenders (exclusive share of root, multiplicity)\n");
        for (DensityCostAnalysis.RootCost root : exportRoots) {
            if (root.exclusive().isEmpty() || root.expandedNodes() < 1.0e4) {
                continue;
            }
            StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "  %-22s", root.root()));
            for (int index = 0; index < Math.min(2, root.exclusive().size()); index++) {
                DensityCostAnalysis.Offender offender = root.exclusive().get(index);
                line.append(String.format(Locale.ROOT, " %4.1f%% %s x%s;", 100.0 * offender.contribution() / root.expandedNodes(),
                        shortName(offender.name()), compact(offender.multiplicity())));
            }
            out.append(line).append('\n');
        }
        for (DensityCostAnalysis.RootCost root : exportRoots) {
            if (root.dagNodes() < 1.0e4) {
                continue;
            }
            List<Map.Entry<String, Double>> shares = new ArrayList<>(root.dagShare().entrySet());
            shares.sort(Map.Entry.<String, Double>comparingByValue().reversed());
            StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "  dag %-18s", root.root()));
            for (int index = 0; index < Math.min(3, shares.size()); index++) {
                line.append(String.format(Locale.ROOT, " %4.1f%% %s;", 100.0 * shares.get(index).getValue(), shares.get(index).getKey()));
            }
            out.append(line).append('\n');
        }
        DensityJsonBreakdown.Report json = report.densityJson();
        out.append(String.format(Locale.ROOT, "density function JSON %.0f MB in %d files, %d cellular instances in %d shapes%n",
                json.bytes() / MEGABYTE, json.files(), json.cellularInstances(), json.cellularShapes().size()));
        for (int index = 0; index < Math.min(9, json.categories().size()); index++) {
            DensityJsonBreakdown.CategoryBytes category = json.categories().get(index);
            out.append(String.format(Locale.ROOT, "  %-22s %8.1f MB %7d files %7d shapes %8.1f MB dup%n", category.category(),
                    category.bytes() / MEGABYTE, category.files(), category.distinctShapes(), category.structurallyDuplicateBytes() / MEGABYTE));
        }
        for (int index = 0; index < Math.min(4, json.libraryOwners().size()); index++) {
            DensityJsonBreakdown.Bytes owner = json.libraryOwners().get(index);
            out.append(String.format(Locale.ROOT, "  %-48s %8.1f MB%n", owner.name(), owner.bytes() / MEGABYTE));
        }
        return out.toString();
    }

    private static void row(StringBuilder out, String label, double vanilla, double export) {
        out.append(String.format(Locale.ROOT, "%-34s %10.3f %10.3f %8s%n", label, vanilla, export, ratio(vanilla, export)));
    }

    private static void row(StringBuilder out, String label, double vanilla, double export, double vanillaCpu, double exportCpu) {
        out.append(String.format(Locale.ROOT, "%-34s %10.3f %10.3f %8s %10.3f %10.3f %8s%n", label, vanilla, export, ratio(vanilla, export),
                vanillaCpu, exportCpu, ratio(vanillaCpu, exportCpu)));
    }

    private static void stage(StringBuilder out, String label, WorldgenStageBench.StageTiming vanilla, WorldgenStageBench.StageTiming export) {
        row(out, label, vanilla.wall().mean(), export.wall().mean(), vanilla.cpu().mean(), export.cpu().mean());
    }

    private static void call(StringBuilder out, String label, WorldgenStageBench.CallTiming vanilla, WorldgenStageBench.CallTiming export) {
        row(out, label, vanilla.wall().mean(), export.wall().mean(), vanilla.cpuMean(), export.cpuMean());
    }

    private static String ratio(double vanilla, double export) {
        return vanilla > 0.0 ? String.format(Locale.ROOT, "%.1fx", export / vanilla) : "-";
    }

    private static DensityCostAnalysis.RootCost find(List<DensityCostAnalysis.RootCost> roots, String name) {
        for (DensityCostAnalysis.RootCost root : roots) {
            if (root.root().equals(name)) {
                return root;
            }
        }
        return null;
    }

    private static String shortName(String name) {
        int separator = name.indexOf(':');
        String path = separator < 0 ? name : name.substring(separator + 1);
        return path.length() <= 34 ? path : "..." + path.substring(path.length() - 31);
    }

    private static String compact(double value) {
        if (value < 1.0e4) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        return String.format(Locale.ROOT, "%.2e", value);
    }

    private static void log(String message) {
        System.out.println(LOG_PREFIX + " " + message);
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        HeadlessNativeRegistries.delete(root);
    }

    record Configuration(Path pack, String dimension, long seed, int side, int rounds, int warmup, int calls, long callSeconds,
                         Path output, Path work) {
        Configuration {
            if (side < 1 || rounds < 1 || warmup < 1 || calls < MINIMUM_CALLS || callSeconds < 1) {
                throw new IllegalArgumentException("side, rounds, warmup and callSeconds must be positive and calls at least " + MINIMUM_CALLS);
            }
        }

        static Configuration parse(String[] arguments) {
            if (arguments.length != 10) {
                throw new IllegalArgumentException("Usage: pack dimension seed side rounds warmupSide calls callSeconds output work");
            }
            return new Configuration(new File(arguments[0]).toPath().toAbsolutePath(), arguments[1], Long.parseLong(arguments[2]),
                    Integer.parseInt(arguments[3]), Integer.parseInt(arguments[4]), Integer.parseInt(arguments[5]),
                    Integer.parseInt(arguments[6]), Long.parseLong(arguments[7]), new File(arguments[8]).toPath().toAbsolutePath(),
                    new File(arguments[9]).toPath().toAbsolutePath());
        }
    }

    record ExportStats(double seconds, Map<String, Double> stageSeconds, long zipBytes, int files, int densityFunctions,
                       long densityFunctionBytes, int noises, int biomes, int diagnostics) {
    }

    record GeneratorStats(double registryLoadSeconds, long retainedHeapBytes, long samplerHeapBytes, WorldgenStageBench.ChunkTimings chunks,
                          WorldgenStageBench.CallTiming uncachedBiome, WorldgenStageBench.CallTiming baseHeightSurface,
                          WorldgenStageBench.CallTiming baseHeightFloor, WorldgenStageBench.CallTiming baseColumn) {
    }

    record MachineLoad(int processors, double loadAverageStart, double loadAverageEnd) {
    }

    record Report(String pack, String dimension, long seed, int chunks, int warmupChunks, int rounds, MachineLoad load,
                  String threading, String decoration,
                  ExportStats export, Map<String, GeneratorStats> generators, DensityJsonBreakdown.Report densityJson,
                  Map<String, List<DensityCostAnalysis.RootCost>> roots, Map<String, String> definitions) {
    }

    private record ExportRun(ExportStats stats, SortedMap<String, byte[]> files) {
    }

    private static final class Measurement {
        private final WorldgenStageBench bench;
        private final WorldgenStageBench.ChunkSamples chunks = new WorldgenStageBench.ChunkSamples();
        private final WorldgenStageBench.CallSamples uncachedBiome = new WorldgenStageBench.CallSamples();
        private final WorldgenStageBench.CallSamples surfaceHeight = new WorldgenStageBench.CallSamples();
        private final WorldgenStageBench.CallSamples floorHeight = new WorldgenStageBench.CallSamples();
        private final WorldgenStageBench.CallSamples column = new WorldgenStageBench.CallSamples();

        private Measurement(WorldgenStageBench bench) {
            this.bench = bench;
        }

        private GeneratorStats stats(double loadSeconds, long retainedHeap, long samplerHeap) {
            return new GeneratorStats(loadSeconds, retainedHeap, samplerHeap, chunks.timings(), uncachedBiome.timing(),
                    surfaceHeight.timing(), floorHeight.timing(), column.timing());
        }
    }

    private static final class StageClock implements WorldgenExportProgress {
        private final Map<String, Long> starts = new LinkedHashMap<>();
        private long origin;

        private void start(long nanos) {
            origin = nanos;
        }

        @Override
        public void stage(String stage) {
            starts.put(stage, System.nanoTime());
        }

        @Override
        public void resources(int done, int total) {
        }

        private Map<String, Double> seconds(long finished) {
            Map<String, Double> seconds = new LinkedHashMap<>();
            String previous = "setup";
            long previousStart = origin;
            for (Map.Entry<String, Long> entry : starts.entrySet()) {
                seconds.put(previous, (entry.getValue() - previousStart) / NANOS_PER_SECOND);
                previous = entry.getKey();
                previousStart = entry.getValue();
            }
            seconds.put(previous, (finished - previousStart) / NANOS_PER_SECOND);
            return seconds;
        }
    }
}
