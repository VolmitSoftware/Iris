package art.arcane.iris.probe;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.actuator.IrisTerrainNormalActuator;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.terrain.Terrain3DColumn;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.hunk.Hunk;
import com.google.gson.GsonBuilder;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class Terrain3DProbe {
    private static final String PREFIX = "[terrain3d]";
    private static final int MAXIMUM_CHUNKS_PER_AXIS = 64;
    private static final int MINIMUM_GAP_HEIGHT = 2;

    private Terrain3DProbe() {
    }

    record Configuration(File pack, String dimension, long seed, int minimumChunkX, int maximumChunkX,
                         int minimumChunkZ, int maximumChunkZ, Set<String> requiredBiomes, File output,
                         boolean strictGeometry, boolean studio) {
        Configuration {
            if (pack == null || !pack.isAbsolute() || dimension == null || dimension.isBlank()
                    || dimension.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException("An absolute pack path and nonblank dimension key are required.");
            }
            validateAxis(minimumChunkX, maximumChunkX);
            validateAxis(minimumChunkZ, maximumChunkZ);
            if (output == null || !output.isAbsolute()) {
                throw new IllegalArgumentException("The output directory must be absolute.");
            }
            requiredBiomes = Set.copyOf(requiredBiomes);
        }

        static Configuration parse(String[] args) {
            if (args.length != 11) {
                throw new IllegalArgumentException("Expected: <pack> <dimension> <seed> <minChunkX> <maxChunkX> "
                        + "<minChunkZ> <maxChunkZ> <requiredBiomeKeys|- > <outputDir> <strictGeometry> <studio>");
            }
            LinkedHashSet<String> required = new LinkedHashSet<>();
            if (!args[7].equals("-")) {
                for (String value : args[7].split(",", -1)) {
                    String key = value.trim();
                    if (key.isBlank() || key.chars().anyMatch(Character::isWhitespace)) {
                        throw new IllegalArgumentException("Required biome keys must be comma-separated nonblank load keys.");
                    }
                    required.add(key);
                }
            }
            return new Configuration(new File(args[0]), args[1], Long.parseLong(args[2]),
                    Integer.parseInt(args[3]), Integer.parseInt(args[4]), Integer.parseInt(args[5]),
                    Integer.parseInt(args[6]), required, new File(args[8]),
                    RealPackProbeSupport.parseBoolean(args[9], "strictGeometry"),
                    RealPackProbeSupport.parseBoolean(args[10], "studio"));
        }

        int chunkCount() {
            return (maximumChunkX - minimumChunkX + 1) * (maximumChunkZ - minimumChunkZ + 1);
        }

        private static void validateAxis(int minimum, int maximum) {
            long width = (long) maximum - minimum + 1;
            if (width < 1 || width > MAXIMUM_CHUNKS_PER_AXIS
                    || minimum < Integer.MIN_VALUE / 16 || maximum > Integer.MAX_VALUE / 16 - 1) {
                throw new IllegalArgumentException("Chunk axes must be ordered, at most 64 chunks wide, and fit block coordinates.");
            }
        }
    }

    record ColumnEvidence(int additiveBlocks, int retainedAdditiveBlocks, int coveredGaps, int retainedCoveredGaps,
                          int carvedBlocks, int retainedCarvedBlocks, int geometryMismatches,
                          int finalGeometryDifferences, int generatedTop, int maximumGapHeight) {
    }

    record ColumnMorphology(int solidSpans, int coveredAirBlocks, long[] gapHeightBins,
                            long[] upperSolidThicknessBins) {
    }

    static final class BiomeEvidence {
        long sampledColumns;
        long shapedColumns;
        long additiveBlocks;
        long retainedAdditiveBlocks;
        long coveredGaps;
        long retainedCoveredGaps;
        long carvedBlocks;
        long retainedCarvedBlocks;
        long geometryMismatches;
        long finalGeometryDifferences;
        long heightMismatches;
        long columnsWithCoveredGaps;
        long columnsWithMultipleSolidSpans;
        long additionalSolidSpans;
        long coveredAirBlocks;
        final long[] gapHeightBins = new long[5];
        final long[] upperSolidThicknessBins = new long[5];
        int maximumGapHeight;
        final List<String> examples = new ArrayList<>();

        void add(ColumnEvidence evidence, ColumnMorphology morphology, boolean heightMismatch, int x, int z, int minHeight) {
            shapedColumns++;
            additiveBlocks += evidence.additiveBlocks();
            retainedAdditiveBlocks += evidence.retainedAdditiveBlocks();
            coveredGaps += evidence.coveredGaps();
            retainedCoveredGaps += evidence.retainedCoveredGaps();
            carvedBlocks += evidence.carvedBlocks();
            retainedCarvedBlocks += evidence.retainedCarvedBlocks();
            geometryMismatches += evidence.geometryMismatches();
            finalGeometryDifferences += evidence.finalGeometryDifferences();
            heightMismatches += heightMismatch ? 1 : 0;
            columnsWithCoveredGaps += morphology.coveredAirBlocks() > 0 ? 1 : 0;
            columnsWithMultipleSolidSpans += morphology.solidSpans() > 1 ? 1 : 0;
            additionalSolidSpans += Math.max(0, morphology.solidSpans() - 1);
            coveredAirBlocks += morphology.coveredAirBlocks();
            for (int index = 0; index < gapHeightBins.length; index++) {
                gapHeightBins[index] += morphology.gapHeightBins()[index];
                upperSolidThicknessBins[index] += morphology.upperSolidThicknessBins()[index];
            }
            maximumGapHeight = Math.max(maximumGapHeight, evidence.maximumGapHeight());
            if (evidence.retainedCoveredGaps() > 0 && examples.size() < 8) {
                examples.add(x + "," + (evidence.generatedTop() + minHeight) + "," + z);
            }
        }

        boolean coversOverhangs() {
            return retainedCoveredGaps > 0 && retainedAdditiveBlocks > 0;
        }
    }

    record Result(String status, Configuration configuration, int generatedChunks, int worldMinimumY,
                  Map<String, BiomeEvidence> biomes, TopologyEvidence topology,
                  List<String> failures, List<String> sections) {
    }

    record TopologyEvidence(long groundedComponents, long censoredComponents, long detachedComponents,
                            long detachedBlocks, long retainedDetachedBlocks, long[] detachedSizeBins,
                            long fullyRetainedSmallComponents, List<String> examples) {
    }

    public static void main(String[] args) {
        int exitCode = 2;
        try {
            Configuration configuration = Configuration.parse(args);
            Result result = run(configuration);
            System.out.println("IRIS_TERRAIN3D_RESULT version=1 status=" + result.status()
                    + " dimension=" + configuration.dimension() + " seed=" + configuration.seed()
                    + " chunks=" + result.generatedChunks() + " biomes=" + result.biomes().size()
                    + " failures=" + result.failures().size() + " studio=" + configuration.studio()
                    + " strict_geometry=" + configuration.strictGeometry()
                    + " output=" + configuration.output().getAbsolutePath());
            exitCode = result.failures().isEmpty() ? 0 : 1;
        } catch (Throwable failure) {
            System.out.println(PREFIX + " FAIL: " + failure.getMessage());
            failure.printStackTrace(System.out);
        }
        System.exit(exitCode);
    }

    static ColumnEvidence inspectColumn(int baseTop, boolean[] predicted, boolean[] terrain,
                                        boolean[] generated, boolean[] retainedTerrain) {
        int height = predicted.length;
        if (height < 1 || terrain.length != height || generated.length != height || retainedTerrain.length != height) {
            throw new IllegalArgumentException("All column buffers must have the same positive height.");
        }
        int additive = 0;
        int retainedAdditive = 0;
        int carved = 0;
        int retainedCarved = 0;
        int mismatches = 0;
        int finalDifferences = 0;
        int top = -1;
        for (int y = 0; y < height; y++) {
            if (terrain[y]) {
                top = y;
            }
            if (predicted[y] != terrain[y]) {
                mismatches++;
            }
            if (predicted[y] != generated[y]) {
                finalDifferences++;
            }
            if (y > baseTop && predicted[y] && terrain[y]) {
                additive++;
                retainedAdditive += retainedTerrain[y] ? 1 : 0;
            }
            if (y <= baseTop && !predicted[y] && !terrain[y]) {
                carved++;
                retainedCarved += !generated[y] ? 1 : 0;
            }
        }
        int gaps = 0;
        int retainedGaps = 0;
        int maximumGap = 0;
        for (int bottom = 1; bottom < height - 1; bottom++) {
            if (predicted[bottom] || !predicted[bottom - 1] || terrain[bottom]) {
                continue;
            }
            int roof = bottom + 1;
            while (roof < height && !predicted[roof]) {
                roof++;
            }
            int gapHeight = roof - bottom;
            if (roof >= height || gapHeight < MINIMUM_GAP_HEIGHT || !terrain[bottom - 1] || !terrain[roof]) {
                continue;
            }
            boolean rawAir = true;
            boolean finalAir = true;
            for (int y = bottom; y < roof; y++) {
                rawAir &= !terrain[y];
                finalAir &= !generated[y];
            }
            if (rawAir) {
                gaps++;
                maximumGap = Math.max(maximumGap, gapHeight);
                if (finalAir && retainedTerrain[bottom - 1] && retainedTerrain[roof]) {
                    retainedGaps++;
                }
            }
            bottom = roof;
        }
        return new ColumnEvidence(additive, retainedAdditive, gaps, retainedGaps, carved, retainedCarved,
                mismatches, finalDifferences, top, maximumGap);
    }

    static ColumnMorphology inspectMorphology(boolean[] terrain) {
        int spans = 0;
        int previousTop = -1;
        int coveredAir = 0;
        long[] gaps = new long[5];
        long[] thickness = new long[5];
        for (int bottom = 0; bottom < terrain.length; bottom++) {
            if (!terrain[bottom]) {
                continue;
            }
            int top = bottom;
            while (top + 1 < terrain.length && terrain[top + 1]) {
                top++;
            }
            if (previousTop >= 0) {
                int gap = bottom - previousTop - 1;
                coveredAir += gap;
                gaps[morphologyBin(gap)]++;
                thickness[morphologyBin(top - bottom + 1)]++;
            }
            spans++;
            previousTop = top;
            bottom = top;
        }
        return new ColumnMorphology(spans, coveredAir, gaps, thickness);
    }

    private static int morphologyBin(int size) {
        return size <= 1 ? 0 : size <= 3 ? 1 : size <= 7 ? 2 : size <= 15 ? 3 : 4;
    }

    static List<String> coverageFailures(Map<String, BiomeEvidence> biomes, Set<String> required, boolean strict) {
        ArrayList<String> failures = new ArrayList<>();
        long shapedColumns = 0;
        for (Map.Entry<String, BiomeEvidence> entry : biomes.entrySet()) {
            BiomeEvidence evidence = entry.getValue();
            shapedColumns += evidence.shapedColumns;
            if (evidence.geometryMismatches > 0 || evidence.heightMismatches > 0) {
                failures.add(entry.getKey() + ": terrain output differs from shape/query: blocks="
                        + evidence.geometryMismatches + ", heights=" + evidence.heightMismatches);
            }
            if (strict && evidence.finalGeometryDifferences > 0) {
                failures.add(entry.getKey() + ": final chunk geometry differs at "
                        + evidence.finalGeometryDifferences + " blocks in strict mode");
            }
        }
        if (shapedColumns == 0) {
            failures.add("No shaped terrain columns were generated in the requested rectangle.");
        }
        for (String key : required) {
            BiomeEvidence evidence = biomes.get(key);
            if (evidence == null || !evidence.coversOverhangs()) {
                failures.add(key + ": required retained additive terrain and covered air gaps were not both generated");
            }
        }
        return List.copyOf(failures);
    }

    private static Result run(Configuration configuration) throws Exception {
        Files.createDirectories(configuration.output().toPath());
        TreeMap<String, BiomeEvidence> evidence = new TreeMap<>();
        ArrayList<String> sections = new ArrayList<>();
        int generatedChunks = 0;
        int minHeight;
        TopologyEvidence topology;
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(
                configuration.pack(), configuration.dimension(), PREFIX);
             RealPackProbeSupport.EngineSession session = workspace.openEngine(
                     configuration.seed(), configuration.studio(), "terrain3d")) {
            Engine engine = session.engine();
            minHeight = engine.getMinHeight();
            TerrainTopology terrainTopology = new TerrainTopology(
                    (configuration.maximumChunkX() - configuration.minimumChunkX() + 1) * 16,
                    (configuration.maximumChunkZ() - configuration.minimumChunkZ() + 1) * 16);
            IrisTerrainNormalActuator actuator = new IrisTerrainNormalActuator(engine);
            for (int chunkZ = configuration.minimumChunkZ(); chunkZ <= configuration.maximumChunkZ(); chunkZ++) {
                SectionRenderer renderer = new SectionRenderer(configuration,
                        new SectionBounds(chunkZ, engine.getHeight(), minHeight));
                for (int chunkX = configuration.minimumChunkX(); chunkX <= configuration.maximumChunkX(); chunkX++) {
                    inspectChunk(engine, actuator, chunkX, chunkZ, evidence, renderer, terrainTopology);
                    generatedChunks++;
                    if (generatedChunks % 16 == 0 || generatedChunks == configuration.chunkCount()) {
                        System.out.println(PREFIX + " generated " + generatedChunks + "/" + configuration.chunkCount());
                    }
                }
                sections.addAll(renderer.write());
            }
            topology = terrainTopology.inspect();
            failReported("generation", RealPackProbeSupport.settleAndDrain());
        }
        List<String> failures = coverageFailures(evidence, configuration.requiredBiomes(), configuration.strictGeometry());
        Result result = new Result(failures.isEmpty() ? "PASS" : "FAIL", configuration, generatedChunks,
                minHeight, evidence, topology, failures, sections);
        Files.writeString(configuration.output().toPath().resolve("terrain3d-summary.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(summary(result)) + "\n", StandardCharsets.UTF_8);
        for (Map.Entry<String, BiomeEvidence> entry : evidence.entrySet()) {
            BiomeEvidence value = entry.getValue();
            System.out.println(PREFIX + " biome=" + entry.getKey() + " shaped=" + value.shapedColumns
                    + " additive=" + value.retainedAdditiveBlocks + " covered_gaps=" + value.retainedCoveredGaps
                    + " max_gap=" + value.maximumGapHeight + " geometry_errors=" + value.geometryMismatches);
        }
        for (String failure : failures) {
            System.out.println(PREFIX + " FAIL: " + failure);
        }
        return result;
    }

    private static Map<String, Object> summary(Result result) {
        Configuration configuration = result.configuration();
        Map<String, Object> inputs = Map.ofEntries(
                Map.entry("pack", configuration.pack().getAbsolutePath()),
                Map.entry("dimension", configuration.dimension()),
                Map.entry("seed", configuration.seed()),
                Map.entry("minimumChunkX", configuration.minimumChunkX()),
                Map.entry("maximumChunkX", configuration.maximumChunkX()),
                Map.entry("minimumChunkZ", configuration.minimumChunkZ()),
                Map.entry("maximumChunkZ", configuration.maximumChunkZ()),
                Map.entry("requiredBiomes", configuration.requiredBiomes()),
                Map.entry("strictGeometry", configuration.strictGeometry()),
                Map.entry("studio", configuration.studio()));
        return Map.of("status", result.status(), "configuration", inputs, "generatedChunks", result.generatedChunks(),
                "worldMinimumY", result.worldMinimumY(), "biomes", result.biomes(), "failures", result.failures(),
                "sections", result.sections(), "topology", result.topology(), "metricDefinitions", Map.of(
                        "morphologyBinInclusiveRanges", List.of("1", "2–3", "4–7", "8–15", "16+"),
                        "detachedSizeBinInclusiveRanges", List.of("1–8", "9–64", "65–512", "513+"),
                        "normalization", "Divide per-biome counts by sampledColumns, including unshaped columns.",
                        "topologyScope", "Actual normal-terrain solid output; excludes additional-layer ownership and placed features. "
                                + "Components touching rectangle edges, world ceiling, or excluded ownership are censored. "
                                + "Examples use local block X,Y,Z relative to the rectangle and world minimum Y."));
    }

    private static void inspectChunk(Engine engine, IrisTerrainNormalActuator actuator, int chunkX, int chunkZ,
                                     Map<String, BiomeEvidence> biomes, SectionRenderer renderer,
                                     TerrainTopology topology) throws Exception {
        failReported("before chunk " + chunkX + "," + chunkZ, RealPackProbeSupport.drainReported());
        RealPackProbeSupport.GeneratedChunk generated = RealPackProbeSupport.generateChunk(engine, chunkX, chunkZ);
        int height = generated.height();
        Hunk<PlatformBlockState> terrain = Hunk.newArrayHunk(16, height, 16);
        IrisComplex complex = engine.getComplex();
        int blockX = chunkX << 4;
        int blockZ = chunkZ << 4;
        actuator.actuate(blockX, blockZ, terrain, false, new ChunkContext(blockX, blockZ, complex));
        failReported("chunk " + chunkX + "," + chunkZ, RealPackProbeSupport.drainReported());
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = blockX + x;
                int worldZ = blockZ + z;
                IrisBiome biome = complex.getTrueBiomeStream().get(worldX, worldZ);
                String key = biome == null ? "<missing>" : biome.getLoadKey();
                BiomeEvidence aggregate = biomes.computeIfAbsent(key, ignored -> new BiomeEvidence());
                aggregate.sampledColumns++;
                Terrain3DColumn column = complex.terrainColumn(worldX, worldZ);
                boolean[] raw = new boolean[height];
                boolean[] retained = new boolean[height];
                BitSet excluded = new BitSet(height);
                boolean additionalTerrain = engine.getUpperContext() != null || engine.getDimensionStackContext() != null;
                int rootTop = (int) Math.round(complex.getRawHeightStream().getDouble(worldX, worldZ));
                for (int y = 0; y < height; y++) {
                    boolean owned = additionalTerrain && engine.isAdditionalTerrainOwned(worldX, y, worldZ);
                    excluded.set(y, owned);
                    PlatformBlockState rawBlock = terrain.get(x, y, z);
                    PlatformBlockState finalBlock = generated.blocks().get(x, y, z);
                    raw[y] = y <= rootTop && !owned && solid(rawBlock);
                    retained[y] = raw[y] && finalBlock != null && rawBlock.matches(finalBlock);
                }
                topology.column(worldX - (renderer.configuration.minimumChunkX() << 4),
                        worldZ - (renderer.configuration.minimumChunkZ() << 4),
                        new TopologyColumn(raw, retained, excluded, column != null));
                ColumnEvidence observation = column == null ? null
                        : inspectShapedColumn(column, terrain, generated.blocks(), x, z);
                if (observation != null) {
                    int queryTop = (int) Math.round(complex.getRawHeightStream().getDouble(worldX, worldZ));
                    aggregate.add(observation, inspectMorphology(raw),
                            queryTop != column.topY() || queryTop != observation.generatedTop(),
                            worldX, worldZ, engine.getMinHeight());
                }
                renderer.column(worldX, z, terrain, generated.blocks(), x, column,
                        (int) Math.round(complex.getBaseTerrainHeightStream().getDouble(worldX, worldZ)), observation);
            }
        }
    }

    private static ColumnEvidence inspectShapedColumn(Terrain3DColumn column, Hunk<PlatformBlockState> terrain,
                                                      Hunk<PlatformBlockState> generated, int x, int z) {
        int height = terrain.getHeight();
        boolean[] predicted = new boolean[height];
        boolean[] raw = new boolean[height];
        boolean[] complete = new boolean[height];
        boolean[] retained = new boolean[height];
        for (int y = 0; y < height; y++) {
            PlatformBlockState rawBlock = terrain.get(x, y, z);
            PlatformBlockState finalBlock = generated.get(x, y, z);
            predicted[y] = column.isSolid(y);
            raw[y] = solid(rawBlock);
            complete[y] = finalBlock != null && !finalBlock.isAir();
            retained[y] = raw[y] && finalBlock != null && rawBlock.matches(finalBlock);
        }
        return inspectColumn((int) Math.round(column.baseHeight()), predicted, raw, complete, retained);
    }

    private static boolean solid(PlatformBlockState block) {
        return block != null && !block.isAir() && !block.isFluid();
    }

    private static void failReported(String phase, List<Throwable> reports) throws Exception {
        if (reports.isEmpty()) {
            return;
        }
        RealPackProbeSupport.printReports(PREFIX, phase, reports);
        IllegalStateException failure = new IllegalStateException("Engine reported failures during " + phase);
        for (Throwable report : reports) {
            failure.addSuppressed(report);
        }
        throw failure;
    }

    record TopologyColumn(boolean[] solid, boolean[] retained, BitSet excluded, boolean shaped) {
        TopologyColumn {
            if (solid.length == 0 || solid.length != retained.length || excluded == null) {
                throw new IllegalArgumentException("Topology columns need matching nonempty buffers and an ownership mask.");
            }
        }
    }

    static final class TerrainTopology {
        private final int width;
        private final int depth;
        private final List<List<SolidSpan>> columns;
        private final List<BitSet> excluded;

        TerrainTopology(int width, int depth) {
            if (width < 1 || depth < 1) {
                throw new IllegalArgumentException("Topology dimensions must be positive.");
            }
            this.width = width;
            this.depth = depth;
            this.columns = new ArrayList<>(width * depth);
            this.excluded = new ArrayList<>(width * depth);
            for (int index = 0; index < width * depth; index++) {
                columns.add(null);
                excluded.add(null);
            }
        }

        void column(int x, int z, TopologyColumn column) {
            if (x < 0 || z < 0 || x >= width || z >= depth || columns.get(z * width + x) != null) {
                throw new IllegalArgumentException("Each topology column must be supplied exactly once within the rectangle.");
            }
            ArrayList<SolidSpan> spans = new ArrayList<>();
            for (int bottom = 0; bottom < column.solid().length; bottom++) {
                if (!column.solid()[bottom] || column.excluded().get(bottom)) {
                    continue;
                }
                int top = bottom;
                long retained = column.retained()[bottom] ? 1 : 0;
                while (top + 1 < column.solid().length && column.solid()[top + 1] && !column.excluded().get(top + 1)) {
                    top++;
                    retained += column.retained()[top] ? 1 : 0;
                }
                boolean censored = x == 0 || x == width - 1 || z == 0 || z == depth - 1
                        || top == column.solid().length - 1 || column.excluded().get(top + 1)
                        || bottom > 0 && column.excluded().get(bottom - 1);
                spans.add(new SolidSpan(new SpanBounds(x, z, bottom, top),
                        new SpanObservation(column.shaped(), censored, retained)));
                bottom = top;
            }
            columns.set(z * width + x, spans);
            excluded.set(z * width + x, (BitSet) column.excluded().clone());
        }

        TopologyEvidence inspect() {
            if (columns.contains(null)) {
                throw new IllegalStateException("All columns must be sampled before topology analysis.");
            }
            for (int z = 0; z < depth; z++) {
                for (int x = 0; x < width; x++) {
                    int index = z * width + x;
                    if (x > 0) {
                        connect(index, index - 1);
                    }
                    if (z > 0) {
                        connect(index, index - width);
                    }
                }
            }
            long grounded = 0;
            long censored = 0;
            long detached = 0;
            long blocks = 0;
            long retained = 0;
            long smallRetained = 0;
            long[] bins = new long[4];
            ArrayList<String> examples = new ArrayList<>();
            for (List<SolidSpan> column : columns) {
                for (SolidSpan span : column) {
                    if (span.parent != span || !span.shaped) {
                        continue;
                    }
                    if (span.grounded) {
                        grounded++;
                    } else if (span.censored) {
                        censored++;
                    } else {
                        detached++;
                        blocks += span.blocks;
                        retained += span.retained;
                        bins[span.blocks <= 8 ? 0 : span.blocks <= 64 ? 1 : span.blocks <= 512 ? 2 : 3]++;
                        smallRetained += span.blocks <= 64 && span.retained == span.blocks ? 1 : 0;
                        if (examples.size() < 16) {
                            examples.add(span.bounds.x() + "," + span.bounds.bottom() + "," + span.bounds.z()
                                    + " blocks=" + span.blocks + " retained=" + span.retained);
                        }
                    }
                }
            }
            return new TopologyEvidence(grounded, censored, detached, blocks, retained, bins,
                    smallRetained, List.copyOf(examples));
        }

        private void connect(int firstIndex, int secondIndex) {
            List<SolidSpan> first = columns.get(firstIndex);
            List<SolidSpan> second = columns.get(secondIndex);
            censor(first, excluded.get(secondIndex));
            censor(second, excluded.get(firstIndex));
            int left = 0;
            int right = 0;
            while (left < first.size() && right < second.size()) {
                SolidSpan a = first.get(left);
                SolidSpan b = second.get(right);
                if (a.bounds.bottom() <= b.bounds.top() && b.bounds.bottom() <= a.bounds.top()) {
                    a.union(b);
                }
                if (a.bounds.top() < b.bounds.top()) {
                    left++;
                } else {
                    right++;
                }
            }
        }

        private static void censor(List<SolidSpan> spans, BitSet ownership) {
            for (SolidSpan span : spans) {
                int owned = ownership.nextSetBit(span.bounds.bottom());
                if (owned >= 0 && owned <= span.bounds.top()) {
                    span.root().censored = true;
                }
            }
        }
    }

    private record SpanBounds(int x, int z, int bottom, int top) {
    }

    private record SpanObservation(boolean shaped, boolean censored, long retained) {
    }

    private static final class SolidSpan {
        private final SpanBounds bounds;
        private SolidSpan parent = this;
        private long blocks;
        private long retained;
        private boolean shaped;
        private boolean grounded;
        private boolean censored;

        private SolidSpan(SpanBounds bounds, SpanObservation observation) {
            this.bounds = bounds;
            this.blocks = bounds.top() - bounds.bottom() + 1L;
            this.retained = observation.retained();
            this.shaped = observation.shaped();
            this.grounded = bounds.bottom() == 0;
            this.censored = observation.censored();
        }

        private SolidSpan root() {
            SolidSpan root = this;
            while (root.parent != root) {
                root = root.parent;
            }
            SolidSpan current = this;
            while (current.parent != root) {
                SolidSpan next = current.parent;
                current.parent = root;
                current = next;
            }
            return root;
        }

        private void union(SolidSpan other) {
            SolidSpan first = root();
            SolidSpan second = other.root();
            if (first == second) {
                return;
            }
            if (first.blocks < second.blocks) {
                SolidSpan swap = first;
                first = second;
                second = swap;
            }
            second.parent = first;
            first.blocks += second.blocks;
            first.retained += second.retained;
            first.shaped |= second.shaped;
            first.grounded |= second.grounded;
            first.censored |= second.censored;
        }
    }

    private record SectionBounds(int chunkZ, int height, int minimumY) {
    }

    private static final class SectionRenderer {
        private static final int LEFT = 48;
        private static final int HEADER = 58;
        private final Configuration configuration;
        private final int chunkZ;
        private final int height;
        private final int minimumY;
        private final BufferedImage[] images = new BufferedImage[16];
        private final long[] scores = new long[16];
        private final int[] lowestShape = new int[16];
        private final int[] highestShape = new int[16];

        private SectionRenderer(Configuration configuration, SectionBounds bounds) {
            this.configuration = configuration;
            this.chunkZ = bounds.chunkZ();
            this.height = bounds.height();
            this.minimumY = bounds.minimumY();
            int width = Math.max(720, (configuration.maximumChunkX() - configuration.minimumChunkX() + 1) * 16 + LEFT + 16);
            for (int z = 0; z < 16; z++) {
                BufferedImage image = new BufferedImage(width, height * 2 + HEADER * 2 + 28, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                graphics.setColor(new Color(0xf1f4f7));
                graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                graphics.dispose();
                images[z] = image;
                lowestShape[z] = height;
            }
        }

        private void column(int worldX, int localZ, Hunk<PlatformBlockState> terrain, Hunk<PlatformBlockState> generated,
                            int localX, Terrain3DColumn shape, int baseHeight, ColumnEvidence observation) {
            BufferedImage image = images[localZ];
            int pixelX = LEFT + worldX - (configuration.minimumChunkX() << 4);
            for (int y = 0; y < height; y++) {
                image.setRGB(pixelX, HEADER + height - y - 1, color(terrain.get(localX, y, localZ), shape, baseHeight, y));
                image.setRGB(pixelX, HEADER * 2 + height * 2 - y - 1,
                        color(generated.get(localX, y, localZ), shape, baseHeight, y));
            }
            if (observation != null) {
                scores[localZ] += observation.retainedCoveredGaps() * 1_000L + observation.retainedAdditiveBlocks();
                lowestShape[localZ] = Math.min(lowestShape[localZ], shape.minY());
                highestShape[localZ] = Math.max(highestShape[localZ], shape.topY());
            }
        }

        private int color(PlatformBlockState block, Terrain3DColumn shape, int baseHeight, int y) {
            if (block == null || block.isAir()) {
                return shape != null && y <= shape.topY() && !shape.isSolid(y) ? 0xd8b8ce : 0xeaf1f7;
            }
            if (block.isFluid()) {
                return block.isWater() ? 0x328bbc : 0xe3762b;
            }
            if (shape != null && y > baseHeight && shape.isSolid(y)) {
                return 0xcf9b43;
            }
            String key = block.key();
            if (key.contains("grass") || key.contains("moss") || block.isTreeBlock()) {
                return 0x497b50;
            }
            if (key.contains("snow") || key.contains("ice")) {
                return 0xc5d8de;
            }
            return 0x58616c;
        }

        private List<String> write() throws Exception {
            int selected = 8;
            for (int z = 0; z < scores.length; z++) {
                if (scores[z] > scores[selected]) {
                    selected = z;
                }
            }
            return List.of(writeSection(selected, "section-z-"), writeSection(8, "fixed-section-z-"));
        }

        private String writeSection(int selected, String prefix) throws Exception {
            int worldZ = (chunkZ << 4) + selected;
            BufferedImage image = images[selected];
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(new Color(0x222a34));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            graphics.drawString(configuration.dimension() + " | seed " + configuration.seed() + " | Z " + worldZ
                    + " | X " + (configuration.minimumChunkX() << 4) + ".." + ((configuration.maximumChunkX() << 4) + 15), LEFT, 19);
            graphics.drawString("Terrain actuator output | gold: added terrain | mauve: shaped air | green: vegetation", LEFT, 39);
            graphics.drawString("Complete generated chunk output (all pack features enabled)", LEFT, height + HEADER + 34);
            graphics.drawString("Block geometry only; colors identify categories, not Minecraft rendering.", LEFT, image.getHeight() - 8);
            for (int y = 0; y < height; y += 64) {
                String label = Integer.toString(y + minimumY);
                graphics.drawString(label, 4, HEADER + height - y - 1);
                graphics.drawString(label, 4, HEADER * 2 + height * 2 - y - 1);
            }
            graphics.dispose();
            File file = new File(configuration.output(), prefix + worldZ + "-full.png");
            if (!ImageIO.write(image, "PNG", file)) {
                throw new IllegalStateException("No PNG writer is available.");
            }
            return writeFocused(image, selected, prefix);
        }

        private String writeFocused(BufferedImage source, int selected, String prefix) throws Exception {
            int worldZ = (chunkZ << 4) + selected;
            int minimum = lowestShape[selected] == height ? 0 : Math.max(0, lowestShape[selected] - 16);
            int maximum = lowestShape[selected] == height ? height - 1 : Math.min(height - 1, highestShape[selected] + 16);
            int columns = (configuration.maximumChunkX() - configuration.minimumChunkX() + 1) * 16;
            int scale = Math.min(6, Math.max(1, 640 / columns));
            int panelHeight = (maximum - minimum + 1) * scale;
            int panelWidth = columns * scale;
            int focusedHeader = 80;
            BufferedImage focused = new BufferedImage(Math.max(720, panelWidth * 2 + LEFT * 2 + 24),
                    panelHeight + focusedHeader + 28, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = focused.createGraphics();
            graphics.setColor(new Color(0xf1f4f7));
            graphics.fillRect(0, 0, focused.getWidth(), focused.getHeight());
            graphics.setColor(new Color(0x222a34));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            graphics.drawString(configuration.dimension() + " | seed " + configuration.seed() + " | Z " + worldZ
                    + " | X " + (configuration.minimumChunkX() << 4) + ".." + ((configuration.maximumChunkX() << 4) + 15), LEFT, 19);
            graphics.drawString("Gold: additions | mauve: shaped air | scale " + scale + " px/block", LEFT, 39);
            graphics.drawString("Terrain actuator output", LEFT, 64);
            graphics.drawString("Complete generation", LEFT * 2 + panelWidth, 64);
            for (int panel = 0; panel < 2; panel++) {
                int destinationLeft = LEFT + panel * (LEFT + panelWidth);
                int sourceTop = HEADER + panel * (HEADER + height) + height - maximum - 1;
                graphics.drawImage(source, destinationLeft, focusedHeader, destinationLeft + panelWidth, focusedHeader + panelHeight,
                        LEFT, sourceTop, LEFT + columns, sourceTop + maximum - minimum + 1, null);
                for (int y = minimum; y <= maximum; y += 16) {
                    graphics.drawString(Integer.toString(y + minimumY), destinationLeft - 44,
                            focusedHeader + (maximum - y) * scale + 10);
                }
            }
            graphics.drawString("Actual block cross-section; colors do not represent Minecraft rendering.", LEFT, focused.getHeight() - 8);
            graphics.dispose();
            File output = new File(configuration.output(), prefix + worldZ + ".png");
            if (!ImageIO.write(focused, "PNG", output)) {
                throw new IllegalStateException("No PNG writer is available.");
            }
            return output.getAbsolutePath();
        }
    }
}
