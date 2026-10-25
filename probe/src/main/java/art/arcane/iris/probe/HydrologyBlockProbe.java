package art.arcane.iris.probe;

import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import com.google.gson.GsonBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HydrologyBlockProbe {
    private static final int MAXIMUM_CHUNKS = 64;
    private static final int MAXIMUM_EXAMPLES = 16;
    private static final int[][] CARDINALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private HydrologyBlockProbe() {
    }

    record Configuration(File pack, String dimension, long seed, int minimumChunkX, int maximumChunkX, int minimumChunkZ,
                         int maximumChunkZ, Set<String> requiredCoverage, File output, boolean studio) {
        Configuration {
            if (pack == null || !pack.isAbsolute() || dimension == null || dimension.isBlank() || output == null || !output.isAbsolute()) {
                throw new IllegalArgumentException("Absolute pack/output paths and a dimension key are required.");
            }
            long width = (long) maximumChunkX - minimumChunkX + 1;
            long depth = (long) maximumChunkZ - minimumChunkZ + 1;
            if (width < 1 || depth < 1 || width * depth > MAXIMUM_CHUNKS || minimumChunkX < Integer.MIN_VALUE / 16
                    || minimumChunkZ < Integer.MIN_VALUE / 16 || maximumChunkX > Integer.MAX_VALUE / 16 - 1
                    || maximumChunkZ > Integer.MAX_VALUE / 16 - 1) {
                throw new IllegalArgumentException("Bounds must contain 1..64 chunks and fit block coordinates.");
            }
            requiredCoverage = Set.copyOf(requiredCoverage);
            if (!Set.of("channel", "pond", "bank", "cane").containsAll(requiredCoverage)) {
                throw new IllegalArgumentException("Required coverage accepts channel,pond,bank,cane or -.");
            }
        }

        static Configuration parse(String[] args) {
            if (args.length != 10) {
                throw new IllegalArgumentException("Expected <pack> <dimension> <seed> <minChunkX> <maxChunkX> <minChunkZ> <maxChunkZ> "
                        + "<requiredCoverage|-> <outputDir> <studio>.");
            }
            Set<String> required = new LinkedHashSet<>();
            if (!args[7].equals("-")) {
                for (String value : args[7].split(",", -1)) {
                    required.add(value.trim());
                }
            }
            return new Configuration(new File(args[0]), args[1], Long.parseLong(args[2]), Integer.parseInt(args[3]),
                    Integer.parseInt(args[4]), Integer.parseInt(args[5]), Integer.parseInt(args[6]), required, new File(args[8]),
                    RealPackProbeSupport.parseBoolean(args[9], "studio"));
        }
    }

    static final class Evidence {
        long plannedWetColumns;
        long channelColumns;
        long pondColumns;
        long bankColumns;
        long caneRoots;
        long validCaneRoots;
        long invalidCaneSubstrates;
        long dryCaneRoots;
        long censoredCaneRoots;
        HydrologyVoxels.WaterScan scan = new HydrologyVoxels.WaterScan();
        final List<String> examples = new ArrayList<>();

        void example(String value) {
            if (examples.size() < MAXIMUM_EXAMPLES) {
                examples.add(value);
            }
        }
    }

    record Result(String status, Configuration configuration, int seaLevel, Evidence evidence, List<String> failures,
                  List<String> artifacts) {
    }

    record PlannedColumn(WaterRole role, int terrain, int natural, int head) {
    }

    public static void main(String[] args) {
        int exitCode = 2;
        try {
            Result result = run(Configuration.parse(args));
            Evidence evidence = result.evidence();
            System.out.println("IRIS_HYDROLOGY_BLOCK_RESULT version=2 status=" + result.status() + " seed=" + result.configuration().seed()
                    + " plannedWetColumns=" + evidence.plannedWetColumns + " missingSurfaceColumns=" + evidence.scan.missingSurfaces
                    + " containmentLeaks=" + evidence.scan.containmentLeaks + " tickConversionCells=" + evidence.scan.tickConversionCells
                    + " cane=" + evidence.validCaneRoots + " failures=" + result.failures().size()
                    + " output=" + result.configuration().output().getAbsolutePath());
            exitCode = result.failures().isEmpty() ? 0 : 1;
        } catch (Throwable failure) {
            failure.printStackTrace(System.out);
        }
        System.exit(exitCode);
    }

    static Result run(Configuration configuration) throws Exception {
        StubPlatform.vanillaSolidity(true);
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(configuration.pack(), configuration.dimension(),
                "[hydrology-blocks]");
             RealPackProbeSupport.EngineSession session = workspace.openEngine(configuration.seed(), configuration.studio(), "hydrology-blocks")) {
            Engine engine = session.engine();
            IrisComplex complex = engine.getComplex();
            HydrologyRuntime runtime = complex.getHydrologyRuntime();
            if (runtime == null || !runtime.enabled()) {
                throw new IllegalStateException("The dimension must enable hydrology.");
            }
            HydrologyVoxels.Volume volume = new HydrologyVoxels.Volume();
            for (int z = configuration.minimumChunkZ(); z <= configuration.maximumChunkZ(); z++) {
                for (int x = configuration.minimumChunkX(); x <= configuration.maximumChunkX(); x++) {
                    volume.put(HydrologyVoxels.generate(engine, x, z));
                }
            }
            Map<Long, PlannedColumn> planned = planned(complex, runtime, configuration);
            int seaLevel = engine.getDimension().getFluidHeight();
            Evidence evidence = inspect(configuration, volume, planned, engine);
            List<String> failures = failures(evidence, configuration.requiredCoverage());
            List<Throwable> reports = RealPackProbeSupport.drainReported();
            RealPackProbeSupport.printReports("[hydrology-blocks]", "generation", reports);
            if (!reports.isEmpty()) {
                failures.add("The engine reported " + reports.size() + " generation errors.");
            }
            Files.createDirectories(configuration.output().toPath());
            List<String> artifacts = writeImages(configuration, volume, engine);
            Result result = new Result(failures.isEmpty() ? "PASS" : "FAIL", configuration, seaLevel, evidence, List.copyOf(failures), artifacts);
            Files.writeString(new File(configuration.output(), "hydrology-blocks.json").toPath(), json(result), StandardCharsets.UTF_8);
            return result;
        }
    }

    private static Map<Long, PlannedColumn> planned(IrisComplex complex, HydrologyRuntime runtime, Configuration configuration) {
        Map<Long, PlannedColumn> planned = new HashMap<>();
        for (int chunkZ = configuration.minimumChunkZ(); chunkZ <= configuration.maximumChunkZ(); chunkZ++) {
            for (int chunkX = configuration.minimumChunkX(); chunkX <= configuration.maximumChunkX(); chunkX++) {
                int[] heads = HydrologyVoxels.effectiveHeads(complex, chunkX, chunkZ);
                ChunkWaterRaster own = runtime.raster(chunkX, chunkZ);
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        int x = (chunkX << 4) + lx;
                        int z = (chunkZ << 4) + lz;
                        int head = heads[(lz << 4) | lx];
                        int natural = complex.naturalTrueHeight(x, z);
                        int terrain = own.empty() ? natural : own.terrainHeight(lx, lz);
                        planned.put(HydrologyVoxels.columnKey(x, z), new PlannedColumn(own.role(lx, lz), terrain, natural, head));
                    }
                }
            }
        }
        return planned;
    }

    static Evidence inspect(Configuration configuration, HydrologyVoxels.Volume volume, Map<Long, PlannedColumn> planned, Engine engine) {
        Evidence evidence = new Evidence();
        int minimumX = configuration.minimumChunkX() << 4;
        int minimumZ = configuration.minimumChunkZ() << 4;
        int maximumX = (configuration.maximumChunkX() << 4) + 15;
        int maximumZ = (configuration.maximumChunkZ() << 4) + 15;
        int height = engine.getTarget().getHeight();
        int seaLevel = engine.getDimension().getFluidHeight();
        HydrologyBlockMetrics.Columns columns = new HydrologyBlockMetrics.Columns() {
            @Override
            public boolean known(int x, int z) {
                return planned.containsKey(HydrologyVoxels.columnKey(x, z));
            }

            @Override
            public WaterRole role(int x, int z) {
                return planned.get(HydrologyVoxels.columnKey(x, z)).role();
            }

            @Override
            public int terrain(int x, int z) {
                return planned.get(HydrologyVoxels.columnKey(x, z)).terrain();
            }

            @Override
            public int natural(int x, int z) {
                return planned.get(HydrologyVoxels.columnKey(x, z)).natural();
            }
        };
        for (int z = minimumZ; z <= maximumZ; z++) {
            for (int x = minimumX; x <= maximumX; x++) {
                PlannedColumn column = planned.get(HydrologyVoxels.columnKey(x, z));
                inspectCane(volume, x, z, height, evidence);
                switch (column.role()) {
                    case CHANNEL, MOUTH, CASCADE, WATERFALL -> evidence.channelColumns++;
                    case POND -> evidence.pondColumns++;
                    case BANK -> evidence.bankColumns++;
                    default -> {
                    }
                }
                if (column.head() != HydrologyVoxels.NO_WATER) {
                    evidence.plannedWetColumns++;
                }
                if (HydrologyBlockMetrics.scanned(columns, x, z)) {
                    evidence.scan.column(volume, x, z, HydrologyBlockMetrics.scanFloor(columns, seaLevel, x, z), height - 1);
                }
            }
        }
        return evidence;
    }

    private static void inspectCane(HydrologyVoxels.Volume volume, int x, int z, int height, Evidence evidence) {
        for (int y = 1; y < height; y++) {
            if (volume.at(x, y, z) != HydrologyVoxels.CANE || volume.at(x, y - 1, z) == HydrologyVoxels.CANE) {
                continue;
            }
            evidence.caneRoots++;
            byte substrate = volume.at(x, y - 1, z);
            if (substrate != HydrologyVoxels.SOLID) {
                evidence.invalidCaneSubstrates++;
                evidence.example("unsupported cane " + x + "," + y + "," + z);
                continue;
            }
            boolean censored = false;
            boolean wet = false;
            for (int[] offset : CARDINALS) {
                byte adjacent = volume.at(x + offset[0], y - 1, z + offset[1]);
                censored |= adjacent == HydrologyVoxels.UNKNOWN;
                wet |= HydrologyVoxels.water(adjacent);
            }
            if (wet) {
                evidence.validCaneRoots++;
            } else if (censored) {
                evidence.censoredCaneRoots++;
            } else {
                evidence.dryCaneRoots++;
                evidence.example("dry cane root " + x + "," + y + "," + z);
            }
        }
    }

    static List<String> failures(Evidence evidence, Set<String> required) {
        List<String> failures = new ArrayList<>();
        if (evidence.invalidCaneSubstrates > 0 || evidence.dryCaneRoots > 0) {
            failures.add("Generated sugar cane has invalid substrate or no adjacent water.");
        }
        if (evidence.scan.missingSurfaces > 0) {
            failures.add("Generated blocks miss the planned water surface in " + evidence.scan.missingSurfaces + " columns.");
        }
        if (evidence.scan.containmentLeaks > 0) {
            failures.add("Generated water leaks into open ground in " + evidence.scan.containmentLeaks + " cells.");
        }
        if (evidence.scan.tickConversionCells > 0) {
            failures.add("Fluid ticks would move generated water in " + evidence.scan.tickConversionCells + " cells.");
        }
        for (String coverage : required) {
            boolean covered = switch (coverage) {
                case "channel" -> evidence.channelColumns > 0;
                case "pond" -> evidence.pondColumns > 0;
                case "bank" -> evidence.bankColumns > 0;
                case "cane" -> evidence.validCaneRoots > 0;
                default -> false;
            };
            if (!covered) {
                failures.add("Required generated coverage is absent: " + coverage);
            }
        }
        return failures;
    }

    static String json(Result result) {
        Configuration configuration = result.configuration();
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("pack", configuration.pack().getAbsolutePath());
        settings.put("dimension", configuration.dimension());
        settings.put("seed", configuration.seed());
        settings.put("minimumChunkX", configuration.minimumChunkX());
        settings.put("maximumChunkX", configuration.maximumChunkX());
        settings.put("minimumChunkZ", configuration.minimumChunkZ());
        settings.put("maximumChunkZ", configuration.maximumChunkZ());
        settings.put("requiredCoverage", configuration.requiredCoverage());
        settings.put("studio", configuration.studio());
        Evidence evidence = result.evidence();
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("plannedWetColumns", evidence.plannedWetColumns);
        counts.put("missingSurfaceColumns", evidence.scan.missingSurfaces);
        counts.put("channelColumns", evidence.channelColumns);
        counts.put("pondColumns", evidence.pondColumns);
        counts.put("bankColumns", evidence.bankColumns);
        counts.put("waterCells", evidence.scan.waterCells);
        counts.put("containmentLeaks", evidence.scan.containmentLeaks);
        counts.put("tickConversionCells", evidence.scan.tickConversionCells);
        counts.put("censoredCells", evidence.scan.censoredCells);
        counts.put("caneRoots", evidence.caneRoots);
        counts.put("validCaneRoots", evidence.validCaneRoots);
        counts.put("dryCaneRoots", evidence.dryCaneRoots);
        counts.put("invalidCaneSubstrates", evidence.invalidCaneSubstrates);
        counts.put("censoredCaneRoots", evidence.censoredCaneRoots);
        List<String> examples = new ArrayList<>(evidence.examples);
        examples.addAll(evidence.scan.examples);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("status", result.status());
        report.put("configuration", settings);
        report.put("seaLevel", result.seaLevel());
        report.put("evidence", counts);
        report.put("examples", examples);
        report.put("failures", result.failures());
        report.put("artifacts", result.artifacts());
        report.put("coordinateConvention", "Block X/Z are world coordinates; Y is relative to the world minimum height.");
        report.put("imageLegend", "open=light blue,water=blue,lava=orange,cane=lime,solid=gray");
        return new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n";
    }

    private static List<String> writeImages(Configuration configuration, HydrologyVoxels.Volume volume, Engine engine) throws Exception {
        int minimumX = configuration.minimumChunkX() << 4;
        int minimumZ = configuration.minimumChunkZ() << 4;
        int width = (configuration.maximumChunkX() - configuration.minimumChunkX() + 1) << 4;
        int depth = (configuration.maximumChunkZ() - configuration.minimumChunkZ() + 1) << 4;
        int height = engine.getTarget().getHeight();
        BufferedImage overhead = new BufferedImage(width, depth, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                for (int y = height - 1; y >= 0; y--) {
                    byte voxel = volume.at(minimumX + x, y, minimumZ + z);
                    if (voxel != HydrologyVoxels.OPEN) {
                        overhead.setRGB(x, z, color(voxel));
                        break;
                    }
                }
            }
        }
        List<String> artifacts = new ArrayList<>();
        File plan = new File(configuration.output(), "generated-overhead.png");
        ImageIO.write(overhead, "png", plan);
        artifacts.add(plan.getAbsolutePath());
        int selectedX = minimumX + width / 2;
        int selectedZ = minimumZ + depth / 2;
        for (boolean alongX : new boolean[]{true, false}) {
            int span = alongX ? width : depth;
            BufferedImage section = new BufferedImage(span, height, BufferedImage.TYPE_INT_RGB);
            for (int position = 0; position < span; position++) {
                int x = alongX ? minimumX + position : selectedX;
                int z = alongX ? selectedZ : minimumZ + position;
                for (int y = 0; y < height; y++) {
                    section.setRGB(position, height - y - 1, color(volume.at(x, y, z)));
                }
            }
            File file = new File(configuration.output(), alongX ? "generated-section-z-" + selectedZ + ".png"
                    : "generated-section-x-" + selectedX + ".png");
            ImageIO.write(section, "png", file);
            artifacts.add(file.getAbsolutePath());
        }
        return List.copyOf(artifacts);
    }

    private static int color(byte voxel) {
        if (HydrologyVoxels.water(voxel)) {
            return 0x247AC4;
        }
        if (HydrologyVoxels.fluid(voxel)) {
            return 0xEC6F20;
        }
        if (voxel == HydrologyVoxels.CANE) {
            return 0x9BDD42;
        }
        return voxel == HydrologyVoxels.SOLID ? 0x888C90 : 0xC9E3F0;
    }
}
