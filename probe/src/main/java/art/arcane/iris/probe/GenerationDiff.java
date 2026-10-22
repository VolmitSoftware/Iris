/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.probe;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class GenerationDiff {
    private static final String LOG_PREFIX = "[gendiff]";
    private static final int DEFAULT_WORST_CHUNKS = 10;
    private static final Set<String> AIR = Set.of("air", "cave_air", "void_air");
    private static final Set<String> FLUID = Set.of("water", "lava", "bubble_column");
    private static final Set<String> ORE_BLOCKS = Set.of("ancient_debris", "raw_iron_block", "raw_copper_block",
            "raw_gold_block");
    private static final Set<String> DECOR_EXACT = Set.of("snow", "fire", "grass", "short_grass", "tall_grass",
            "farmland", "wheat", "turtle_egg", "lily_pad", "hay_block");
    private static final List<String> DECOR_FRAGMENTS = List.of("log", "wood", "leaves", "hyphae", "stem",
            "sapling", "propagule", "flower", "tulip", "orchid", "allium", "poppy", "dandelion", "daisy",
            "cornflower", "lily", "bluet", "rose", "peony", "lilac", "petals", "eyeblossom", "leaf_litter",
            "fern", "bush", "shrub", "vine", "mushroom", "kelp", "seagrass", "coral", "sea_pickle", "cactus",
            "sugar_cane", "bamboo", "carpet", "dripleaf", "azalea", "roots", "sprouts", "fungus", "wart_block",
            "shroomlight", "lichen", "spore_blossom", "berry", "pumpkin", "melon", "pointed_dripstone", "cobweb",
            "torch", "lantern", "planks", "stairs", "slab", "fence", "wall", "door", "glass", "brick", "button",
            "chest", "barrel", "bookshelf", "chain", "_bed", "banner", "sign", "campfire", "candle", "head",
            "skull", "sculk_sensor", "sculk_shrieker", "sculk_catalyst", "pillar", "sea_lantern", "glowstone");

    private GenerationDiff() {
    }

    enum Category {
        AIR,
        FLUID,
        TERRAIN,
        ORE,
        DECOR
    }

    record ChunkScore(int chunkX, int chunkZ, long blockMismatches, long nonAirPositions, long nonAirMismatches,
                      long surfaceDeltaSum, int surfaceMaxDelta, long biomeMismatches) {
        double nonAirIdentity() {
            return nonAirPositions == 0L ? 100D : 100D * (nonAirPositions - nonAirMismatches) / nonAirPositions;
        }
    }

    static final class Totals {
        long chunksBaseline;
        long chunksCandidate;
        long duplicateBaseline;
        long duplicateCandidate;
        long commonChunks;
        long onlyBaseline;
        long onlyCandidate;
        long positions;
        long identical;
        long nonAirPositions;
        long nonAirIdentical;
        long columns;
        long surfaceIdentical;
        long surfaceDeltaSum;
        int surfaceMaxDelta;
        long terrainSurfaceIdentical;
        long terrainSurfaceDeltaSum;
        int terrainSurfaceMaxDelta;
        long biomePositions;
        long biomeIdentical;
        final long[][] matrix = new long[Category.values().length][Category.values().length];
        final long[] baselineCategoryCounts = new long[Category.values().length];
        final long[] candidateCategoryCounts = new long[Category.values().length];
        final List<ChunkScore> scores = new ArrayList<>();
        final Map<String, Integer> canonicalIds = new HashMap<>();
    }

    public static void main(String[] arguments) {
        if (arguments.length < 2 || arguments.length > 3) {
            System.out.println(LOG_PREFIX + " FAIL: expected <baselineDump> <candidateDump> [worstChunks]");
            System.exit(2);
            return;
        }
        try {
            int worst = arguments.length == 3 ? Integer.parseInt(arguments[2]) : DEFAULT_WORST_CHUNKS;
            Totals totals = compare(Path.of(arguments[0]), Path.of(arguments[1]));
            print(totals, Math.max(0, worst));
            System.exit(totals.commonChunks > 0 ? 0 : 1);
        } catch (Throwable failure) {
            System.out.println(LOG_PREFIX + " FAIL: " + failure.getMessage());
            failure.printStackTrace(System.out);
            System.exit(1);
        }
    }

    static Totals compare(Path baselineFile, Path candidateFile) throws IOException {
        Totals totals = new Totals();
        Map<Long, ChunkDump.ChunkSnapshot> baseline = new HashMap<>();
        ChunkDump.Header baselineHeader;
        try (ChunkDump.Reader reader = new ChunkDump.Reader(baselineFile)) {
            baselineHeader = reader.header();
            ChunkDump.ChunkSnapshot snapshot;
            while ((snapshot = reader.next()) != null) {
                totals.chunksBaseline++;
                if (baseline.putIfAbsent(snapshot.coordinateKey(), snapshot) != null) {
                    totals.duplicateBaseline++;
                }
            }
        }
        Map<Long, Boolean> seenCandidate = new HashMap<>();
        try (ChunkDump.Reader reader = new ChunkDump.Reader(candidateFile)) {
            ChunkDump.Header candidateHeader = reader.header();
            printHeaders(baselineFile, baselineHeader, candidateFile, candidateHeader);
            ChunkDump.ChunkSnapshot snapshot;
            while ((snapshot = reader.next()) != null) {
                totals.chunksCandidate++;
                long key = snapshot.coordinateKey();
                if (seenCandidate.putIfAbsent(key, Boolean.TRUE) != null) {
                    totals.duplicateCandidate++;
                    continue;
                }
                ChunkDump.ChunkSnapshot expected = baseline.remove(key);
                if (expected == null) {
                    totals.onlyCandidate++;
                    continue;
                }
                totals.commonChunks++;
                compareChunk(totals, expected, baselineHeader.minY(), snapshot, candidateHeader.minY());
            }
        }
        totals.onlyBaseline = baseline.size();
        return totals;
    }

    static Category classify(String key) {
        int properties = key.indexOf('[');
        String material = properties >= 0 ? key.substring(0, properties) : key;
        int namespace = material.indexOf(':');
        String path = namespace >= 0 ? material.substring(namespace + 1) : material;
        if (AIR.contains(path)) {
            return Category.AIR;
        }
        if (FLUID.contains(path)) {
            return Category.FLUID;
        }
        if (path.endsWith("_ore") || ORE_BLOCKS.contains(path)) {
            return Category.ORE;
        }
        if (DECOR_EXACT.contains(path)) {
            return Category.DECOR;
        }
        for (String fragment : DECOR_FRAGMENTS) {
            if (path.contains(fragment)) {
                return Category.DECOR;
            }
        }
        return Category.TERRAIN;
    }

    private static void compareChunk(Totals totals, ChunkDump.ChunkSnapshot baseline, int baselineMinY,
                                     ChunkDump.ChunkSnapshot candidate, int candidateMinY) {
        String[] baselinePalette = baseline.blockPalette();
        String[] candidatePalette = candidate.blockPalette();
        int[] baselineBlockIds = canonicalIds(totals, baselinePalette);
        int[] candidateBlockIds = canonicalIds(totals, candidatePalette);
        int[] baselineBiomeIds = canonicalIds(totals, baseline.biomePalette());
        int[] candidateBiomeIds = canonicalIds(totals, candidate.biomePalette());
        byte[] baselineCategories = categories(baselinePalette);
        byte[] candidateCategories = categories(candidatePalette);
        int air = Category.AIR.ordinal();
        int fluid = Category.FLUID.ordinal();
        int terrain = Category.TERRAIN.ordinal();
        int ore = Category.ORE.ordinal();
        int baselineHeight = baseline.height();
        int candidateHeight = candidate.height();
        int lowY = Math.min(baselineMinY, candidateMinY);
        int highY = Math.max(baselineMinY + baselineHeight, candidateMinY + candidateHeight) - 1;
        int biomeLowY = Math.max(baselineMinY, candidateMinY);
        int biomeHighY = Math.min(baselineMinY + baselineHeight, candidateMinY + candidateHeight) - 1;
        char[] baselineBlocks = baseline.blocks();
        char[] candidateBlocks = candidate.blocks();
        char[] baselineBiomes = baseline.biomes();
        char[] candidateBiomes = candidate.biomes();
        long blockMismatches = 0L;
        long nonAirPositions = 0L;
        long nonAirMismatches = 0L;
        long surfaceDeltaSum = 0L;
        int surfaceMaxDelta = 0;
        long biomeMismatches = 0L;
        for (int column = 0; column < 256; column++) {
            int baselineBase = column * baselineHeight;
            int candidateBase = column * candidateHeight;
            int baselineSurface = lowY - 1;
            int candidateSurface = lowY - 1;
            int baselineTerrainSurface = lowY - 1;
            int candidateTerrainSurface = lowY - 1;
            for (int y = lowY; y <= highY; y++) {
                int baselineY = y - baselineMinY;
                int candidateY = y - candidateMinY;
                boolean baselineInside = baselineY >= 0 && baselineY < baselineHeight;
                boolean candidateInside = candidateY >= 0 && candidateY < candidateHeight;
                int baselineIndex = baselineInside ? baselineBlocks[baselineBase + baselineY] : -1;
                int candidateIndex = candidateInside ? candidateBlocks[candidateBase + candidateY] : -1;
                int baselineCategory = baselineIndex < 0 ? air : baselineCategories[baselineIndex];
                int candidateCategory = candidateIndex < 0 ? air : candidateCategories[candidateIndex];
                boolean equal;
                if (baselineIndex < 0 || candidateIndex < 0) {
                    equal = baselineCategory == air && candidateCategory == air;
                } else {
                    equal = baselineBlockIds[baselineIndex] == candidateBlockIds[candidateIndex];
                }
                totals.positions++;
                totals.baselineCategoryCounts[baselineCategory]++;
                totals.candidateCategoryCounts[candidateCategory]++;
                if (equal) {
                    totals.identical++;
                } else {
                    blockMismatches++;
                    totals.matrix[baselineCategory][candidateCategory]++;
                }
                if (baselineCategory != air || candidateCategory != air) {
                    nonAirPositions++;
                    if (!equal) {
                        nonAirMismatches++;
                    }
                }
                if (baselineCategory != air && baselineCategory != fluid) {
                    baselineSurface = y;
                }
                if (candidateCategory != air && candidateCategory != fluid) {
                    candidateSurface = y;
                }
                if (baselineCategory == terrain || baselineCategory == ore) {
                    baselineTerrainSurface = y;
                }
                if (candidateCategory == terrain || candidateCategory == ore) {
                    candidateTerrainSurface = y;
                }
            }
            int delta = Math.abs(baselineSurface - candidateSurface);
            int terrainDelta = Math.abs(baselineTerrainSurface - candidateTerrainSurface);
            totals.columns++;
            if (delta == 0) {
                totals.surfaceIdentical++;
            }
            if (terrainDelta == 0) {
                totals.terrainSurfaceIdentical++;
            }
            totals.surfaceDeltaSum += delta;
            totals.terrainSurfaceDeltaSum += terrainDelta;
            totals.surfaceMaxDelta = Math.max(totals.surfaceMaxDelta, delta);
            totals.terrainSurfaceMaxDelta = Math.max(totals.terrainSurfaceMaxDelta, terrainDelta);
            surfaceDeltaSum += delta;
            surfaceMaxDelta = Math.max(surfaceMaxDelta, delta);
            for (int y = biomeLowY; y <= biomeHighY; y++) {
                int baselineBiome = baselineBiomes[baselineBase + y - baselineMinY];
                int candidateBiome = candidateBiomes[candidateBase + y - candidateMinY];
                totals.biomePositions++;
                if (baselineBiomeIds[baselineBiome] == candidateBiomeIds[candidateBiome]) {
                    totals.biomeIdentical++;
                } else {
                    biomeMismatches++;
                }
            }
        }
        totals.nonAirPositions += nonAirPositions;
        totals.nonAirIdentical += nonAirPositions - nonAirMismatches;
        totals.scores.add(new ChunkScore(baseline.chunkX(), baseline.chunkZ(), blockMismatches, nonAirPositions,
                nonAirMismatches, surfaceDeltaSum, surfaceMaxDelta, biomeMismatches));
    }

    private static int[] canonicalIds(Totals totals, String[] palette) {
        int[] ids = new int[palette.length];
        for (int index = 0; index < palette.length; index++) {
            ids[index] = totals.canonicalIds.computeIfAbsent(canonicalKey(palette[index]),
                    key -> totals.canonicalIds.size());
        }
        return ids;
    }

    static String canonicalKey(String key) {
        int properties = key.indexOf('[');
        int namespace = key.indexOf(':');
        if (namespace >= 0 && (properties < 0 || namespace < properties)) {
            return key;
        }
        return "minecraft:" + key;
    }

    private static byte[] categories(String[] palette) {
        byte[] categories = new byte[palette.length];
        for (int index = 0; index < palette.length; index++) {
            categories[index] = (byte) classify(palette[index]).ordinal();
        }
        return categories;
    }

    private static void printHeaders(Path baselineFile, ChunkDump.Header baseline, Path candidateFile,
                                     ChunkDump.Header candidate) {
        System.out.println(LOG_PREFIX + " baseline: " + baselineFile + " dimension=" + baseline.dimensionKey()
                + " seed=" + baseline.seed() + " minY=" + baseline.minY() + " height=" + baseline.height());
        System.out.println(LOG_PREFIX + " candidate: " + candidateFile + " dimension=" + candidate.dimensionKey()
                + " seed=" + candidate.seed() + " minY=" + candidate.minY() + " height=" + candidate.height());
        if (!baseline.dimensionKey().equals(candidate.dimensionKey()) || baseline.seed() != candidate.seed()) {
            System.out.println(LOG_PREFIX + " WARNING: dumps differ in dimension or seed");
        }
        if (baseline.minY() != candidate.minY() || baseline.height() != candidate.height()) {
            System.out.println(LOG_PREFIX + " WARNING: dumps differ in vertical range; blocks compare over the union"
                    + " (outside a range counts as air) and biomes over the intersection");
        }
    }

    private static void print(Totals totals, int worst) {
        System.out.println(LOG_PREFIX + " chunks: baseline=" + totals.chunksBaseline + " candidate="
                + totals.chunksCandidate + " common=" + totals.commonChunks + " only_baseline=" + totals.onlyBaseline
                + " only_candidate=" + totals.onlyCandidate + " duplicate_baseline=" + totals.duplicateBaseline
                + " duplicate_candidate=" + totals.duplicateCandidate);
        System.out.printf(Locale.ROOT, "%s blocks identical: %.4f%% (%d/%d)%n", LOG_PREFIX,
                percent(totals.identical, totals.positions), totals.identical, totals.positions);
        System.out.printf(Locale.ROOT, "%s blocks identical where either side is non-air: %.4f%% (%d/%d)%n",
                LOG_PREFIX, percent(totals.nonAirIdentical, totals.nonAirPositions), totals.nonAirIdentical,
                totals.nonAirPositions);
        System.out.printf(Locale.ROOT, "%s surface (highest non-air non-fluid) identical columns: %.4f%% mean_abs_delta=%.4f max_abs_delta=%d%n",
                LOG_PREFIX, percent(totals.surfaceIdentical, totals.columns),
                mean(totals.surfaceDeltaSum, totals.columns), totals.surfaceMaxDelta);
        System.out.printf(Locale.ROOT, "%s terrain surface (highest terrain or ore) identical columns: %.4f%% mean_abs_delta=%.4f max_abs_delta=%d%n",
                LOG_PREFIX, percent(totals.terrainSurfaceIdentical, totals.columns),
                mean(totals.terrainSurfaceDeltaSum, totals.columns), totals.terrainSurfaceMaxDelta);
        System.out.printf(Locale.ROOT, "%s biomes identical: %.4f%% (%d/%d)%n", LOG_PREFIX,
                percent(totals.biomeIdentical, totals.biomePositions), totals.biomeIdentical, totals.biomePositions);

        Category[] categories = Category.values();
        System.out.println(LOG_PREFIX + " category volumes (baseline -> candidate):");
        for (Category category : categories) {
            long before = totals.baselineCategoryCounts[category.ordinal()];
            long after = totals.candidateCategoryCounts[category.ordinal()];
            System.out.printf(Locale.ROOT, "  %-8s %12d -> %12d (%+.3f%%)%n", category, before, after,
                    before == 0L ? 0D : 100D * (after - before) / before);
        }
        System.out.println(LOG_PREFIX + " mismatches by category (either side in category):");
        for (Category category : categories) {
            System.out.printf(Locale.ROOT, "  %-8s %12d%n", category, categoryMismatches(totals, category));
        }
        System.out.println(LOG_PREFIX + " mismatch matrix (rows baseline category, columns candidate category):");
        StringBuilder headerLine = new StringBuilder(String.format(Locale.ROOT, "  %-8s", ""));
        for (Category category : categories) {
            headerLine.append(String.format(Locale.ROOT, " %12s", category));
        }
        System.out.println(headerLine);
        for (Category row : categories) {
            StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "  %-8s", row));
            for (Category column : categories) {
                line.append(String.format(Locale.ROOT, " %12d", totals.matrix[row.ordinal()][column.ordinal()]));
            }
            System.out.println(line);
        }

        List<ChunkScore> ranked = new ArrayList<>(totals.scores);
        ranked.sort(Comparator.comparingLong(ChunkScore::nonAirMismatches)
                .thenComparingLong(ChunkScore::surfaceDeltaSum)
                .reversed());
        int shown = Math.min(worst, ranked.size());
        System.out.println(LOG_PREFIX + " worst " + shown + " chunks:");
        for (int index = 0; index < shown; index++) {
            ChunkScore score = ranked.get(index);
            System.out.printf(Locale.ROOT,
                    "  chunk=%d,%d nonair_mismatch=%d nonair_identity=%.3f%% block_mismatch=%d surface_mean_abs_delta=%.3f surface_max_abs_delta=%d biome_mismatch=%d%n",
                    score.chunkX(), score.chunkZ(), score.nonAirMismatches(), score.nonAirIdentity(),
                    score.blockMismatches(), score.surfaceDeltaSum() / 256D, score.surfaceMaxDelta(),
                    score.biomeMismatches());
        }

        System.out.printf(Locale.ROOT,
                "IRIS_GENDIFF_RESULT version=1 common_chunks=%d only_baseline=%d only_candidate=%d block_identity_pct=%.4f nonair_identity_pct=%.4f surface_identity_pct=%.4f surface_mean_abs_delta=%.4f surface_max_abs_delta=%d terrain_surface_identity_pct=%.4f terrain_surface_mean_abs_delta=%.4f biome_identity_pct=%.4f mismatch_air=%d mismatch_fluid=%d mismatch_terrain=%d mismatch_ore=%d mismatch_decor=%d%n",
                totals.commonChunks, totals.onlyBaseline, totals.onlyCandidate,
                percent(totals.identical, totals.positions),
                percent(totals.nonAirIdentical, totals.nonAirPositions),
                percent(totals.surfaceIdentical, totals.columns),
                mean(totals.surfaceDeltaSum, totals.columns), totals.surfaceMaxDelta,
                percent(totals.terrainSurfaceIdentical, totals.columns),
                mean(totals.terrainSurfaceDeltaSum, totals.columns),
                percent(totals.biomeIdentical, totals.biomePositions),
                categoryMismatches(totals, Category.AIR), categoryMismatches(totals, Category.FLUID),
                categoryMismatches(totals, Category.TERRAIN), categoryMismatches(totals, Category.ORE),
                categoryMismatches(totals, Category.DECOR));
    }

    private static long categoryMismatches(Totals totals, Category category) {
        long count = 0L;
        for (Category other : Category.values()) {
            count += totals.matrix[category.ordinal()][other.ordinal()];
            if (other != category) {
                count += totals.matrix[other.ordinal()][category.ordinal()];
            }
        }
        return count;
    }

    private static double percent(long part, long whole) {
        return whole == 0L ? 100D : 100D * part / whole;
    }

    private static double mean(long sum, long count) {
        return count == 0L ? 0D : sum / (double) count;
    }
}
