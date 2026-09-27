package art.arcane.iris.probe;

import art.arcane.iris.testsupport.IrisRuntimeState;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

public final class TransitionFixtureProbeTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @AfterClass
    public static void resetRuntime() {
        IrisRuntimeState.reset();
    }

    @Test(timeout = 120_000)
    public void lowlandToMountainPreservesHistoricalTrackingAndActivatesTheNewPack() throws Exception {
        run(new Scenario("lowland", "mountain", 72, -1, 104, -1));
    }

    @Test(timeout = 120_000)
    public void oceanToMountainRetainsOldWaterAndProducesDryDistantTerrain() throws Exception {
        run(new Scenario("ocean", "mountain", 24, 40, 104, -1));
    }

    @Test(timeout = 120_000)
    public void mountainToOceanProducesTheReplacementWaterTable() throws Exception {
        run(new Scenario("mountain", "ocean", 104, -1, 24, 40));
    }

    @Test(timeout = 120_000)
    public void waterLevelChangeKeepsTheAuthoredOceanFloor() throws Exception {
        run(new Scenario("ocean", "ocean-highwater", 24, 40, 24, 48));
    }

    private void run(Scenario scenario) throws Exception {
        Path output = temporary.newFolder().toPath().resolve("output");
        Path originalPack = fixture(scenario.original());
        Path replacementPack = fixture(scenario.replacement());
        Map<Path, byte[]> originalSource = snapshot(originalPack);
        Map<Path, byte[]> replacementSource = snapshot(replacementPack);
        TransitionProbe.main(new String[]{originalPack.toString(), replacementPack.toString(),
                "transition", "1337", "32", "2", output.toString()});
        assertSnapshotEquals(originalSource, snapshot(originalPack));
        assertSnapshotEquals(replacementSource, snapshot(replacementPack));
        List<String> chunks = Files.readAllLines(output.resolve("chunks.csv"));
        assertEquals(9, chunks.size());
        long originalActivation = Long.parseLong(chunks.get(1).split(",")[3]);
        int original = 0;
        int transition = 0;
        int distant = 0;
        for (String row : chunks.subList(1, chunks.size())) {
            String[] cells = row.split(",");
            long activation = Long.parseLong(cells[3]);
            boolean band = Boolean.parseBoolean(cells[4]);
            assertTrue(Long.parseLong(cells[6]) > 0);
            switch (cells[0]) {
                case "original" -> {
                    original++;
                    assertEquals(originalActivation, activation);
                    assertTrue(!band);
                }
                case "transition" -> {
                    transition++;
                    assertTrue(activation > originalActivation);
                    assertTrue(band);
                }
                case "distant" -> {
                    distant++;
                    assertTrue(activation > originalActivation);
                    assertTrue(!band);
                }
                default -> throw new AssertionError("Unexpected phase " + cells[0]);
            }
        }
        assertEquals(4, original);
        assertEquals(2, transition);
        assertEquals(2, distant);
        List<String> transect = Files.readAllLines(output.resolve("transect.csv"));
        assertEquals(97, transect.size());
        assertFlatSeam(scenario, transect);
        for (String row : transect.subList(1, transect.size())) {
            String[] cells = row.split(",");
            if (cells[0].equals("original")) {
                assertEquals(row, scenario.originalGround(), Integer.parseInt(cells[3]));
                assertEquals(row, scenario.originalFluid(), Integer.parseInt(cells[4]));
            } else if (cells[0].equals("distant")) {
                assertEquals(row, scenario.replacementGround(), Integer.parseInt(cells[3]));
                assertEquals(row, scenario.replacementFluid(), Integer.parseInt(cells[4]));
            }
        }
    }

    @Test(timeout = 120_000)
    public void slopedTransitionsKeepChunkOutputAndTrackingIndependentOfGenerationOrder() throws Exception {
        compareOrders("slope-lowland", "slope-mountain", false);
    }

    @Test(timeout = 120_000)
    public void volumetricTransitionsKeepChunkOutputIndependentOfGenerationOrder() throws Exception {
        compareOrders("volumetric-lowland", "volumetric-mountain", true);
    }

    @Test(timeout = 120_000)
    public void nearSeamDisplacementRetainsNewTerrainRippleCurvature() throws Exception {
        Path original = fixture("lowland");
        Path replacement = fixture("ripple-mountain");
        Path output = temporary.newFolder().toPath().resolve("ripple");
        TransitionProbe.main(new String[]{original.toString(), replacement.toString(), "transition", "1337",
                "64", "4", output.toString()});
        Map<Integer, int[]> blended = columns(Files.readAllLines(output.resolve("transect.csv")));
        Map<Integer, Integer> nativeHeights = new HashMap<>();
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(
                replacement.toFile(), "transition", "[transition-ripple-control]");
             RealPackProbeSupport.EngineSession session = workspace.openEngine(1337, true, "native-control")) {
            Map<String, List<String>> displacedChunks = chunkContents(output);
            for (int chunkX : new int[]{2, 3, 6, 7, 8, 9}) {
                RealPackProbeSupport.GeneratedChunk chunk = RealPackProbeSupport.generateChunk(session.engine(), chunkX, 0);
                if (chunkX >= 6) {
                    GenerationOrderProbe.ChunkHash nativeHash = GenerationOrderProbe.hashChunk(
                            new GenerationOrderProbe.ChunkCoordinate(chunkX, 0), chunk.blocks(), chunk.biomes(), chunk.height());
                    assertEquals("Transition must not alter distant voxels", displacedChunks.get(chunkX + ",0").get(0), nativeHash.blocks());
                    assertEquals("Transition must not alter distant physical biomes", displacedChunks.get(chunkX + ",0").get(1), nativeHash.biomes());
                    continue;
                }
                for (int x = 0; x < 16; x++) {
                    int ground = -1;
                    for (int y = 0; y < chunk.height(); y++) {
                        NativeBlockState block = chunk.blocks().getRaw(x, y, 8);
                        if (block != null && !block.isAir() && !block.isWater()) {
                            ground = y;
                        }
                    }
                    nativeHeights.put(chunkX * 16 + x, ground);
                }
            }
        }
        int strongRipples = 0;
        for (int x = 40; x <= 60; x++) {
            int nativeCurvature = nativeHeights.get(x - 3) - 2 * nativeHeights.get(x) + nativeHeights.get(x + 3);
            if (Math.abs(nativeCurvature) >= 6) {
                strongRipples++;
                int blendedCurvature = blended.get(x - 3)[0] - 2 * blended.get(x)[0] + blended.get(x + 3)[0];
                assertTrue("New terrain relief was attenuated near the seam at x=" + x
                                + " native=" + nativeCurvature + " blended=" + blendedCurvature,
                        Math.abs(nativeCurvature - blendedCurvature) <= 3);
            }
        }
        assertTrue("Control fixture must contain enough measurable ripples", strongRipples >= 5);
    }

    @Test(timeout = 120_000)
    public void transitionSolidsUseTheNewPaletteWithoutHistoricalMaterialDither() throws Exception {
        Path original = fixture("granite-lowland");
        Path replacement = fixture("calcite-mountain");
        Path output = temporary.newFolder().toPath().resolve("materials");
        TransitionProbe.main(new String[]{original.toString(), replacement.toString(), "transition", "1337",
                "32", "2", output.toString()});
        int oldSolid = 0;
        int newSolid = 0;
        List<String> rows = Files.readAllLines(output.resolve("materials.csv"));
        for (String row : rows.subList(1, rows.size())) {
            String[] cells = row.split(",");
            if (cells[3].equals("minecraft:air") || cells[3].equals("minecraft:cave_air")) {
                continue;
            }
            if (cells[0].equals("original")) {
                assertEquals(row, "minecraft:granite", cells[3]);
                oldSolid += Integer.parseInt(cells[4]);
            } else {
                assertEquals(row, "minecraft:calcite", cells[3]);
                newSolid += Integer.parseInt(cells[4]);
            }
        }
        assertTrue(oldSolid > 0);
        assertTrue(newSolid > 0);
    }

    @Test(timeout = 120_000)
    public void oldCaveOpeningContinuesAtTheSeamAndStopsWithinFourBlocks() throws Exception {
        Path original = fixture("cave-lowland");
        Path replacement = fixture("lowland");
        Path output = temporary.newFolder().toPath().resolve("cave");
        TransitionProbe.main(new String[]{original.toString(), replacement.toString(), "transition", "1337",
                "32", "2", output.toString()});
        Map<Integer, String[]> sections = sections(Files.readAllLines(output.resolve("sections.csv")));
        int opening = 0;
        for (int y = 32; y <= 47; y++) {
            if (sections.get(31)[y].equals("minecraft:air") || sections.get(31)[y].equals("minecraft:cave_air")) {
                opening++;
                assertTrue("Historical cave mouth must continue at the first seam column y=" + y,
                        sections.get(32)[y].equals("minecraft:air") || sections.get(32)[y].equals("minecraft:cave_air"));
            }
            assertEquals("Cave patch exceeded four blocks at y=" + y, "minecraft:stone", sections.get(35)[y]);
            assertEquals("Cave must not be extruded across the transition at y=" + y, "minecraft:stone", sections.get(48)[y]);
        }
        assertTrue("Fixture must expose an actual cave mouth at the historical edge", opening >= 12);
        assertEquals("Cave mouth requires a floor", "minecraft:stone", sections.get(31)[31]);
        assertEquals("Cave mouth requires a roof", "minecraft:stone", sections.get(31)[48]);
    }

    private static Map<Integer, String[]> sections(List<String> rows) {
        Map<Integer, String[]> result = new HashMap<>();
        for (String row : rows.subList(1, rows.size())) {
            String[] cells = row.split(",", 6);
            int x = Integer.parseInt(cells[1]);
            String[] column = result.computeIfAbsent(x, ignored -> new String[128]);
            String blockKey = cells[5].contains(":") ? cells[5] : "minecraft:" + cells[5];
            Arrays.fill(column, Integer.parseInt(cells[3]), Integer.parseInt(cells[4]), blockKey);
        }
        return result;
    }

    private void compareOrders(String original, String replacement, boolean volumetric) throws Exception {
        Path originalPack = fixture(original);
        Path replacementPack = fixture(replacement);
        Path forward = temporary.newFolder().toPath().resolve("forward");
        Path reverse = temporary.newFolder().toPath().resolve("reverse");
        for (Path output : List.of(forward, reverse)) {
            TransitionProbe.main(new String[]{originalPack.toString(), replacementPack.toString(), "transition", "1337",
                    "64", "4", output.toString(), Boolean.toString(output.equals(reverse))});
        }
        assertEquals(chunkContents(forward), chunkContents(reverse));
        Map<Integer, int[]> transect = columns(Files.readAllLines(forward.resolve("transect.csv")));
        assertEquals("Transect must cover both joins without missing columns", 160, transect.size());
        if (volumetric) {
            assertTrue("Volumetric fixture must contain more than one solid span",
                    transect.values().stream().anyMatch(column -> column[2] > 1));
        } else {
            assertTrue("Old-side fixture must have a non-flat slope", transect.get(31)[0] != transect.get(0)[0]);
            assertTrue("New-side fixture must have a non-flat slope", transect.get(159)[0] != transect.get(96)[0]);
            assertTrue("Height must meet historical ground at the seam", Math.abs(transect.get(32)[0] - transect.get(31)[0]) <= 2);
            assertTrue("Height must meet native ground at the end of the band", Math.abs(transect.get(96)[0] - transect.get(95)[0]) <= 2);
            for (int x = 32; x < 96; x++) {
                assertTrue("Slope step at x=" + x, Math.abs(transect.get(x)[0] - transect.get(x - 1)[0]) <= 3);
            }
        }
    }

    private static Map<String, List<String>> chunkContents(Path output) throws Exception {
        Map<String, List<String>> result = new HashMap<>();
        List<String> rows = Files.readAllLines(output.resolve("chunks.csv"));
        for (String row : rows.subList(1, rows.size())) {
            String[] cells = row.split(",");
            result.put(cells[1] + "," + cells[2], Arrays.asList(cells).subList(7, 11));
        }
        return result;
    }

    private static void assertFlatSeam(Scenario scenario, List<String> rows) {
        Map<Integer, int[]> columns = columns(rows);
        assertEquals("Transect must cover both joins without missing columns", 96, columns.size());
        int difference = scenario.replacementGround() - scenario.originalGround();
        int maximumStep = (int) Math.ceil(Math.abs(difference) * 1.875 / 32) + 1;
        assertTrue("First new column must meet historical ground: old=" + columns.get(31)[0]
                + " new=" + columns.get(32)[0], Math.abs(columns.get(32)[0] - columns.get(31)[0]) <= 1);
        assertTrue("Band must reach native new ground: band=" + columns.get(63)[0]
                + " native=" + columns.get(64)[0], Math.abs(columns.get(64)[0] - columns.get(63)[0]) <= 1);
        for (int x = 32; x < 64; x++) {
            int[] column = columns.get(x);
            int step = column[0] - columns.get(x - 1)[0];
            assertTrue("Excessive terrain step at x=" + x + " step=" + step + " maximum=" + maximumStep, Math.abs(step) <= maximumStep);
            assertTrue("Terrain overshoot at x=" + x, column[0] >= Math.min(scenario.originalGround(), scenario.replacementGround())
                    && column[0] <= Math.max(scenario.originalGround(), scenario.replacementGround()));
            assertEquals("Floating water or a dry gap below water at x=" + x, 0, column[3]);
            if (difference == 0) {
                assertEquals("Fluid-level transition must not create a stone dam at x=" + x, scenario.originalGround(), column[0]);
                assertTrue("Fluid-level transition must remain wet at x=" + x, column[1] > column[0]);
                assertTrue("Water level must stay within the old/new range at x=" + x,
                        column[1] >= Math.min(scenario.originalFluid(), scenario.replacementFluid())
                                && column[1] <= Math.max(scenario.originalFluid(), scenario.replacementFluid()));
                assertTrue("Water level must rise monotonically at x=" + x, column[1] >= columns.get(x - 1)[1]);
                assertTrue("Water step at x=" + x, column[1] - columns.get(x - 1)[1] <= 1);
            } else {
                assertTrue("Transition must not create an isolated wall or pit at x=" + x + " step=" + step,
                        difference > 0 ? step >= 0 : step <= 0);
            }
        }
    }

    private static Map<Integer, int[]> columns(List<String> rows) {
        Map<Integer, int[]> result = new TreeMap<>();
        for (String row : rows.subList(1, rows.size())) {
            String[] cells = row.split(",");
            int[] values = {Integer.parseInt(cells[3]), Integer.parseInt(cells[4]),
                    Integer.parseInt(cells[5]), Integer.parseInt(cells[6])};
            assertEquals("Duplicate transect column", null, result.put(Integer.parseInt(cells[1]), values));
        }
        return result;
    }

    private static Map<Path, byte[]> snapshot(Path root) throws Exception {
        Map<Path, byte[]> entries = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                entries.put(root.relativize(path), Files.isDirectory(path) ? null : Files.readAllBytes(path));
            }
        }
        return entries;
    }

    private static void assertSnapshotEquals(Map<Path, byte[]> expected, Map<Path, byte[]> actual) {
        assertEquals(expected.keySet(), actual.keySet());
        for (Map.Entry<Path, byte[]> entry : expected.entrySet()) {
            assertArrayEquals(entry.getKey().toString(), entry.getValue(), actual.get(entry.getKey()));
        }
    }

    private Path fixture(String name) throws Exception {
        URL resource = Objects.requireNonNull(TransitionFixtureProbeTest.class.getClassLoader()
                .getResource("transition-packs/" + name), "Transition fixture " + name);
        Path source = Path.of(resource.toURI());
        Path pack = temporary.newFolder(name).toPath();
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path file : paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".json")
                    && !source.relativize(path).toString().startsWith(".")).toList()) {
                Path target = pack.resolve(source.relativize(file));
                Files.createDirectories(target.getParent());
                Files.copy(file, target);
            }
        }
        Files.write(pack.resolve(".source-content"), new byte[]{0, 17, -128, 127});
        return pack;
    }

    private record Scenario(String original, String replacement, int originalGround, int originalFluid,
                            int replacementGround, int replacementFluid) {
    }
}
