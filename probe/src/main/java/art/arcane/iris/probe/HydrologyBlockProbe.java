package art.arcane.iris.probe;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.hydrology.HydrologyColumnLayer;
import art.arcane.iris.engine.hydrology.HydrologyColumnSample;
import art.arcane.iris.engine.hydrology.HydrologyFeatureType;
import art.arcane.iris.engine.hydrology.RiverFootprint;
import art.arcane.iris.engine.object.InferredType;
import art.arcane.iris.spi.PlatformBlockState;
import com.google.gson.GsonBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class HydrologyBlockProbe {
    private static final int MAXIMUM_CHUNKS = 64;
    private static final long MAXIMUM_VOXELS = 16_777_216L;
    private static final int MAXIMUM_EXAMPLES = 16;
    private static final int[][] CARDINALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private HydrologyBlockProbe() {
    }

    record Configuration(File pack, String dimension, long seed, int minimumChunkX, int maximumChunkX,
                         int minimumChunkZ, int maximumChunkZ, Set<String> requiredCoverage, File output,
                         boolean studio) {
        Configuration {
            if (pack == null || !pack.isAbsolute() || dimension == null || dimension.isBlank()
                    || output == null || !output.isAbsolute()) {
                throw new IllegalArgumentException("Absolute pack/output paths and a dimension key are required.");
            }
            long width = (long) maximumChunkX - minimumChunkX + 1;
            long depth = (long) maximumChunkZ - minimumChunkZ + 1;
            if (width < 1 || depth < 1 || width > MAXIMUM_CHUNKS || depth > MAXIMUM_CHUNKS
                    || width * depth > MAXIMUM_CHUNKS
                    || minimumChunkX < Integer.MIN_VALUE / 16 || minimumChunkZ < Integer.MIN_VALUE / 16
                    || maximumChunkX > Integer.MAX_VALUE / 16 - 1 || maximumChunkZ > Integer.MAX_VALUE / 16 - 1) {
                throw new IllegalArgumentException("Bounds must contain 1..64 chunks and fit block coordinates.");
            }
            requiredCoverage = Set.copyOf(requiredCoverage);
            if (!Set.of("cane", "mouth", "channel", "bank").containsAll(requiredCoverage)) {
                throw new IllegalArgumentException("Required coverage accepts cane,mouth,channel,bank or -.");
            }
        }

        static Configuration parse(String[] args) {
            if (args.length != 10) {
                throw new IllegalArgumentException("Expected <pack> <dimension> <seed> <minChunkX> <maxChunkX> "
                        + "<minChunkZ> <maxChunkZ> <requiredCoverage|-> <outputDir> <studio>.");
            }
            Set<String> required = new LinkedHashSet<>();
            if (!args[7].equals("-")) {
                for (String value : args[7].split(",", -1)) {
                    required.add(value.trim());
                }
            }
            return new Configuration(new File(args[0]), args[1], Long.parseLong(args[2]), Integer.parseInt(args[3]),
                    Integer.parseInt(args[4]), Integer.parseInt(args[5]), Integer.parseInt(args[6]), required,
                    new File(args[8]), RealPackProbeSupport.parseBoolean(args[9], "studio"));
        }
    }

    record PlannedColumn(int naturalY, boolean ocean, HydrologyColumnLayer surface, PlatformBlockState fluid) {
        boolean mouthAt(int seaLevel) {
            return surface != null && fluid != null && fluid.isWater()
                    && surface.feature().type() == HydrologyFeatureType.MOUTH
                    && surface.channel() && (surface.fluidOwned() || surface.oceanApron())
                    && surface.fluidHeadY() == seaLevel;
        }
    }

    static final class Evidence {
        long caneRoots;
        long validCaneRoots;
        long invalidCaneSubstrates;
        long dryCaneRoots;
        long censoredCaneRoots;
        long plannedWetVoxels;
        long missingWetVoxels;
        long bankColumns;
        long bankExcavation;
        int maximumBankCut;
        long oceanBedWrites;
        long drySillCuts;
        long invalidDrySillCuts;
        int connectedMouthComponents;
        int disconnectedMouthComponents;
        int censoredMouthComponents;
        final List<String> examples = new ArrayList<>();

        void example(String value) {
            if (examples.size() < MAXIMUM_EXAMPLES) {
                examples.add(value);
            }
        }
    }

    record Result(String status, Configuration configuration, int worldMinimumY, int seaLevel,
                  Evidence evidence, List<String> failures, List<String> artifacts) {
    }

    static final class Volume {
        private final Map<Long, RealPackProbeSupport.GeneratedChunk> chunks;
        private final int minimumX;
        private final int maximumX;
        private final int minimumZ;
        private final int maximumZ;
        private final int height;

        Volume(Map<Long, RealPackProbeSupport.GeneratedChunk> chunks) {
            if (chunks.isEmpty()) {
                throw new IllegalArgumentException("Generated chunks are required.");
            }
            this.chunks = Map.copyOf(chunks);
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;
            int selectedHeight = chunks.values().iterator().next().height();
            for (RealPackProbeSupport.GeneratedChunk chunk : chunks.values()) {
                if (chunk.height() != selectedHeight) {
                    throw new IllegalArgumentException("Generated chunks must have the same height.");
                }
                minX = Math.min(minX, chunk.chunkX() << 4);
                maxX = Math.max(maxX, (chunk.chunkX() << 4) + 15);
                minZ = Math.min(minZ, chunk.chunkZ() << 4);
                maxZ = Math.max(maxZ, (chunk.chunkZ() << 4) + 15);
            }
            minimumX = minX;
            maximumX = maxX;
            minimumZ = minZ;
            maximumZ = maxZ;
            height = selectedHeight;
        }

        boolean contains(int x, int z) {
            return chunks.containsKey(RiverFootprint.pack(Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
        }

        PlatformBlockState block(int x, int y, int z) {
            RealPackProbeSupport.GeneratedChunk chunk = chunks.get(
                    RiverFootprint.pack(Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
            return chunk == null || y < 0 || y >= height ? null : chunk.blockAt(x, y, z);
        }
    }

    public static void main(String[] args) {
        int exitCode = 2;
        try {
            Result result = run(Configuration.parse(args));
            Evidence evidence = result.evidence();
            System.out.println("IRIS_HYDROLOGY_BLOCK_RESULT version=1 status=" + result.status()
                    + " seed=" + result.configuration().seed() + " cane=" + evidence.validCaneRoots
                    + " wetVoxels=" + evidence.plannedWetVoxels + " missingWet=" + evidence.missingWetVoxels
                    + " mouths=" + evidence.connectedMouthComponents + " failures=" + result.failures().size()
                    + " output=" + result.configuration().output().getAbsolutePath());
            exitCode = result.failures().isEmpty() ? 0 : 1;
        } catch (Throwable failure) {
            failure.printStackTrace(System.out);
        }
        System.exit(exitCode);
    }

    static Result run(Configuration configuration) throws Exception {
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(
                configuration.pack(), configuration.dimension(), "[hydrology-blocks]");
             RealPackProbeSupport.EngineSession session = workspace.openEngine(
                     configuration.seed(), configuration.studio(), "hydrology-blocks")) {
            Engine engine = session.engine();
            IrisComplex complex = engine.getComplex();
            if (complex.getHydrologyRuntime() == null) {
                throw new IllegalStateException("The dimension must enable hydrology.");
            }
            long chunks = ((long) configuration.maximumChunkX() - configuration.minimumChunkX() + 1)
                    * ((long) configuration.maximumChunkZ() - configuration.minimumChunkZ() + 1);
            if (chunks * 256 * engine.getTarget().getHeight() > MAXIMUM_VOXELS) {
                throw new IllegalArgumentException("Generated bounds exceed the 16777216 voxel limit.");
            }
            Map<Long, RealPackProbeSupport.GeneratedChunk> generated = new HashMap<>();
            for (int z = configuration.minimumChunkZ(); z <= configuration.maximumChunkZ(); z++) {
                for (int x = configuration.minimumChunkX(); x <= configuration.maximumChunkX(); x++) {
                    generated.put(RiverFootprint.pack(x, z), RealPackProbeSupport.generateChunk(engine, x, z));
                }
            }
            Volume volume = new Volume(generated);
            Map<Long, PlannedColumn> planned = new HashMap<>();
            for (int z = volume.minimumZ; z <= volume.maximumZ; z++) {
                for (int x = volume.minimumX; x <= volume.maximumX; x++) {
                    HydrologyColumnSample sample = complex.sampleHydrologyColumn(x, z);
                    int natural = sample == null ? (int) Math.round(complex.getNaturalHeightStream().getDouble(x, z))
                            : sample.naturalHeight();
                    HydrologyColumnLayer layer = surfaceForInspection(sample);
                    PlatformBlockState fluid = layer == null ? null : complex.resolveHydrologyFluid(
                            layer.profileKey(), x, z);
                    planned.put(RiverFootprint.pack(x, z), new PlannedColumn(natural, sample == null
                            ? complex.getBridgeStream().get(x, z) == InferredType.SEA : sample.ocean(), layer, fluid));
                }
            }
            int seaLevel = complex.getHydrologyRuntime().settings().seaLevel();
            Evidence evidence = inspect(volume, planned, seaLevel);
            int maximumCut = complex.getHydrologyRuntime().settings().surface().banks().erosion()
                    .excavation().maximumDepth();
            List<String> failures = failures(evidence, maximumCut, configuration.requiredCoverage());
            List<Throwable> reports = RealPackProbeSupport.drainReported();
            RealPackProbeSupport.printReports("[hydrology-blocks]", "generation", reports);
            if (!reports.isEmpty()) {
                failures.add("The engine reported " + reports.size() + " generation errors.");
            }
            Files.createDirectories(configuration.output().toPath());
            List<String> artifacts = writeImages(configuration.output(), volume, planned, seaLevel);
            Result result = new Result(failures.isEmpty() ? "PASS" : "FAIL", configuration, engine.getMinHeight(),
                    seaLevel, evidence, List.copyOf(failures), artifacts);
            Files.writeString(new File(configuration.output(), "hydrology-blocks.json").toPath(),
                    json(result), StandardCharsets.UTF_8);
            return result;
        }
    }

    static String json(Result result) {
        Configuration configuration = result.configuration();
        Map<String, Object> settings = Map.ofEntries(
                Map.entry("pack", configuration.pack().getAbsolutePath()),
                Map.entry("dimension", configuration.dimension()),
                Map.entry("seed", configuration.seed()),
                Map.entry("minimumChunkX", configuration.minimumChunkX()),
                Map.entry("maximumChunkX", configuration.maximumChunkX()),
                Map.entry("minimumChunkZ", configuration.minimumChunkZ()),
                Map.entry("maximumChunkZ", configuration.maximumChunkZ()),
                Map.entry("requiredCoverage", configuration.requiredCoverage()),
                Map.entry("output", configuration.output().getAbsolutePath()),
                Map.entry("studio", configuration.studio()));
        Map<String, Object> report = Map.of("status", result.status(), "configuration", settings,
                "worldMinimumY", result.worldMinimumY(), "seaLevel", result.seaLevel(),
                "evidence", result.evidence(), "failures", result.failures(), "artifacts", result.artifacts(),
                "coordinateConvention", "Block X/Z are world coordinates; evidence Y and seaLevel are relative to worldMinimumY.",
                "imageLegend", "air=light blue,water=blue,lava=orange,cane=lime,vegetation=green,sand=tan,soil=brown,other=gray");
        return new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n";
    }

    static HydrologyColumnLayer surfaceForInspection(HydrologyColumnSample sample) {
        if (sample == null) {
            return null;
        }
        HydrologyColumnLayer primary = sample.primarySurfaceLayerOrNull();
        if (primary != null) {
            return primary;
        }
        for (HydrologyColumnLayer layer : sample.layers()) {
            if (layer.oceanApron() && layer.feature().type() == HydrologyFeatureType.MOUTH) {
                return layer;
            }
        }
        return null;
    }

    static Evidence inspect(Volume volume, Map<Long, PlannedColumn> planned, int seaLevel) {
        Evidence evidence = new Evidence();
        for (int z = volume.minimumZ; z <= volume.maximumZ; z++) {
            for (int x = volume.minimumX; x <= volume.maximumX; x++) {
                if (!volume.contains(x, z)) {
                    continue;
                }
                for (int y = 0; y < volume.height; y++) {
                    PlatformBlockState block = volume.block(x, y, z);
                    if (key(block).equals("minecraft:sugar_cane")
                            && !key(volume.block(x, y - 1, z)).equals("minecraft:sugar_cane")) {
                        inspectCane(volume, block, x, y, z, evidence);
                    }
                }
                PlannedColumn column = planned.get(RiverFootprint.pack(x, z));
                if (column == null || column.surface() == null) {
                    continue;
                }
                HydrologyColumnLayer surface = column.surface();
                int cut = Math.max(0, column.naturalY() - surface.bedY());
                if (surface.terrainOwned() && (column.ocean() || column.naturalY() < seaLevel)) {
                    evidence.oceanBedWrites++;
                }
                if (surface.terrainOwned() && column.naturalY() == seaLevel && cut > 0) {
                    evidence.drySillCuts++;
                    if (!surface.channel() || !surface.fluidOwned() || surface.fluidHeadY() != seaLevel) {
                        evidence.invalidDrySillCuts++;
                    }
                }
                if (surface.terrainOwned() && !surface.channel()) {
                    evidence.bankColumns++;
                    evidence.bankExcavation += cut;
                    evidence.maximumBankCut = Math.max(evidence.maximumBankCut, cut);
                }
                if (!surface.fluidOwned() || !surface.channel() || column.fluid() == null) {
                    continue;
                }
                for (int y = Math.max(0, surface.bedY() + 1); y <= surface.fluidHeadY() && y < volume.height; y++) {
                    evidence.plannedWetVoxels++;
                    PlatformBlockState actual = volume.block(x, y, z);
                    boolean matches = column.fluid().isWater() ? water(actual)
                            : key(column.fluid()).equals(key(actual));
                    if (!matches) {
                        evidence.missingWetVoxels++;
                        evidence.example("missing wet voxel " + x + "," + y + "," + z + "=" + key(actual));
                    }
                }
            }
        }
        inspectMouths(volume, planned, seaLevel, evidence);
        return evidence;
    }

    private static void inspectCane(Volume volume, PlatformBlockState cane, int x, int y, int z, Evidence evidence) {
        evidence.caneRoots++;
        PlatformBlockState substrate = volume.block(x, y - 1, z);
        if (substrate == null || !cane.canPlaceOnto(substrate)) {
            evidence.invalidCaneSubstrates++;
            evidence.example("unsupported cane " + x + "," + y + "," + z + " on " + key(substrate));
            return;
        }
        boolean censored = false;
        for (int[] offset : CARDINALS) {
            int nx = x + offset[0];
            int nz = z + offset[1];
            if (!volume.contains(nx, nz)) {
                censored = true;
                continue;
            }
            PlatformBlockState adjacent = volume.block(nx, y - 1, nz);
            if (water(adjacent) || key(adjacent).equals("minecraft:frosted_ice")) {
                evidence.validCaneRoots++;
                return;
            }
        }
        if (censored) {
            evidence.censoredCaneRoots++;
        } else {
            evidence.dryCaneRoots++;
            evidence.example("dry cane root " + x + "," + y + "," + z);
        }
    }

    private static void inspectMouths(Volume volume, Map<Long, PlannedColumn> planned, int seaLevel, Evidence evidence) {
        Set<Long> visited = new HashSet<>();
        TreeMap<Long, PlannedColumn> ordered = new TreeMap<>(planned);
        for (Map.Entry<Long, PlannedColumn> entry : ordered.entrySet()) {
            if (!entry.getValue().mouthAt(seaLevel) || !visited.add(entry.getKey())) {
                continue;
            }
            int startX = (int) (entry.getKey() >> 32);
            int startZ = (int) (long) entry.getKey();
            if (!water(volume.block(startX, seaLevel, startZ))) {
                evidence.disconnectedMouthComponents++;
                evidence.example("dry mouth " + startX + "," + seaLevel + "," + startZ);
                continue;
            }
            ArrayDeque<Long> queue = new ArrayDeque<>();
            queue.add(entry.getKey());
            long courseId = entry.getValue().surface().feature().courseId();
            boolean ocean = false;
            boolean channel = false;
            boolean censored = false;
            while (!queue.isEmpty()) {
                long packed = queue.removeFirst();
                int x = (int) (packed >> 32);
                int z = (int) packed;
                PlannedColumn column = planned.get(packed);
                ocean |= column != null && column.ocean() && column.naturalY() < seaLevel
                        && (column.surface() == null || !column.surface().terrainOwned());
                channel |= column != null && column.surface() != null && column.surface().channel()
                        && column.surface().fluidOwned() && !column.surface().oceanApron()
                        && column.surface().fluidHeadY() == seaLevel
                        && column.surface().feature().courseId() == courseId;
                for (int[] offset : CARDINALS) {
                    int nx = x + offset[0];
                    int nz = z + offset[1];
                    if (!volume.contains(nx, nz)) {
                        censored = true;
                        continue;
                    }
                    long next = RiverFootprint.pack(nx, nz);
                    if (water(volume.block(nx, seaLevel, nz)) && visited.add(next)) {
                        queue.addLast(next);
                    }
                }
            }
            if (ocean && channel) {
                evidence.connectedMouthComponents++;
            } else if (censored) {
                evidence.censoredMouthComponents++;
            } else {
                evidence.disconnectedMouthComponents++;
                evidence.example("mouth without connected course and ocean water "
                        + startX + "," + seaLevel + "," + startZ);
            }
        }
    }

    static List<String> failures(Evidence evidence, int maximumBankCut, Set<String> required) {
        List<String> failures = new ArrayList<>();
        if (evidence.invalidCaneSubstrates > 0 || evidence.dryCaneRoots > 0) {
            failures.add("Generated sugar cane has invalid substrate or no adjacent supporting water.");
        }
        if (evidence.missingWetVoxels > 0) {
            failures.add("Generated blocks do not retain every planned surface channel fluid voxel.");
        }
        if (evidence.disconnectedMouthComponents > 0) {
            failures.add("A fully sampled mouth water component does not connect to untouched natural ocean water.");
        }
        if (evidence.oceanBedWrites > 0 || evidence.invalidDrySillCuts > 0) {
            failures.add("Hydrology owns ocean-bed terrain or an invalid dry shoreline sill cut.");
        }
        if (evidence.maximumBankCut > maximumBankCut) {
            failures.add("Published dry-bank cut depth exceeds its configured budget.");
        }
        for (String coverage : required) {
            boolean covered = switch (coverage) {
                case "cane" -> evidence.validCaneRoots > 0;
                case "mouth" -> evidence.connectedMouthComponents > 0;
                case "channel" -> evidence.plannedWetVoxels > 0;
                case "bank" -> evidence.bankColumns > 0;
                default -> false;
            };
            if (!covered) {
                failures.add("Required generated coverage is absent: " + coverage);
            }
        }
        return failures;
    }

    private static List<String> writeImages(File output, Volume volume, Map<Long, PlannedColumn> planned, int seaLevel)
            throws Exception {
        int width = volume.maximumX - volume.minimumX + 1;
        int depth = volume.maximumZ - volume.minimumZ + 1;
        BufferedImage overhead = new BufferedImage(width, depth, BufferedImage.TYPE_INT_RGB);
        int selectedX = volume.minimumX + width / 2;
        int selectedZ = volume.minimumZ + depth / 2;
        for (int z = volume.minimumZ; z <= volume.maximumZ; z++) {
            for (int x = volume.minimumX; x <= volume.maximumX; x++) {
                for (int y = volume.height - 1; y >= 0; y--) {
                    PlatformBlockState block = volume.block(x, y, z);
                    if (block != null && !block.isAir()) {
                        overhead.setRGB(x - volume.minimumX, z - volume.minimumZ, color(block));
                        break;
                    }
                }
                PlannedColumn column = planned.get(RiverFootprint.pack(x, z));
                if (column != null && column.mouthAt(seaLevel)) {
                    selectedX = x;
                    selectedZ = z;
                }
            }
        }
        File plan = new File(output, "generated-overhead.png");
        ImageIO.write(overhead, "png", plan);
        List<String> artifacts = new ArrayList<>();
        artifacts.add(plan.getAbsolutePath());
        for (boolean alongX : new boolean[]{true, false}) {
            int span = alongX ? width : depth;
            BufferedImage section = new BufferedImage(span, volume.height, BufferedImage.TYPE_INT_RGB);
            for (int position = 0; position < span; position++) {
                int x = alongX ? volume.minimumX + position : selectedX;
                int z = alongX ? selectedZ : volume.minimumZ + position;
                for (int y = 0; y < volume.height; y++) {
                    section.setRGB(position, volume.height - y - 1, color(volume.block(x, y, z)));
                }
            }
            File file = new File(output, alongX ? "generated-section-z-" + selectedZ + ".png"
                    : "generated-section-x-" + selectedX + ".png");
            ImageIO.write(section, "png", file);
            artifacts.add(file.getAbsolutePath());
        }
        return List.copyOf(artifacts);
    }

    private static int color(PlatformBlockState block) {
        if (block == null || block.isAir()) {
            return 0xC9E3F0;
        }
        if (water(block)) {
            return 0x247AC4;
        }
        String key = key(block);
        if (key.equals("minecraft:lava")) {
            return 0xEC6F20;
        }
        if (key.equals("minecraft:sugar_cane")) {
            return 0x9BDD42;
        }
        if (key.contains("grass") || key.contains("moss") || key.contains("leaves")) {
            return 0x4E7845;
        }
        if (key.contains("sand")) {
            return 0xDCCFA5;
        }
        if (key.contains("dirt") || key.contains("mud")) {
            return 0x82634D;
        }
        return 0x888C90;
    }

    private static boolean water(PlatformBlockState block) {
        return block != null && (block.isWater() || block.isWaterLogged());
    }

    private static String key(PlatformBlockState block) {
        if (block == null) {
            return "minecraft:air";
        }
        String key = block.key();
        int bracket = key.indexOf('[');
        return bracket < 0 ? key : key.substring(0, bracket);
    }
}
