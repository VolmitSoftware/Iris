package art.arcane.iris.generation.validation;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GoldenHashEngineTest {
    private static final int MIN_Y = 0;
    private static final int MAX_Y = 8;

    private IrisSettings previousSettings;
    private File goldenDir;
    private Engine engine;
    private PlatformBlockState stone;
    private PlatformBlockState dirt;
    private PlatformBiome plains;
    private PlatformBiome forest;

    @Before
    public void setUp() throws Exception {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        goldenDir = Files.createTempDirectory("iris-goldenhash-test").toFile();
        engine = mock(Engine.class);
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getLoadKey()).thenReturn("testdim");
        when(engine.getDimension()).thenReturn(dimension);
        stone = mock(PlatformBlockState.class);
        when(stone.key()).thenReturn("minecraft:stone");
        dirt = mock(PlatformBlockState.class);
        when(dirt.key()).thenReturn("minecraft:dirt");
        plains = mock(PlatformBiome.class);
        when(plains.key()).thenReturn("minecraft:plains");
        forest = mock(PlatformBiome.class);
        when(forest.key()).thenReturn("minecraft:forest");
    }

    @After
    public void tearDown() {
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void capturePersistsGoldenFileWithStableFormat() throws Exception {
        RecordingFeedback feedback = new RecordingFeedback();
        GoldenHashEngine hashEngine = new GoldenHashEngine(engine, request(GoldenHashEngine.Mode.AUTO), goldenDir, source(dirt, 3, 2, 1), feedback, progress());

        assertTrue(hashEngine.run());
        File goldenFile = new File(goldenDir, "testdim-s1234-c0x0-r0.hashes");
        assertTrue(goldenFile.exists());

        List<String> lines = Files.readAllLines(goldenFile.toPath(), StandardCharsets.UTF_8);
        assertEquals("#iris-goldenhash v1", lines.get(0));
        assertEquals("#world=testworld", lines.get(1));
        assertEquals("#dim=testdim", lines.get(2));
        assertEquals("#seed=1234", lines.get(3));
        assertEquals("#mc=test-1.0", lines.get(4));
        assertEquals("#minY=0 maxY=8", lines.get(5));
        assertEquals("#center=0,0", lines.get(6));
        assertEquals("#radius=0", lines.get(7));

        String expectedBody = referenceLine(0, 0, 3, 2, 1);
        assertEquals(expectedBody, lines.get(8));
        assertEquals("#combined=" + referenceCombined(expectedBody), lines.get(9));
    }

    @Test
    public void nullBiomeUsesCanonicalPlainsFallback() throws Exception {
        RecordingFeedback feedback = new RecordingFeedback();
        GoldenHashEngine hashEngine = new GoldenHashEngine(engine, request(GoldenHashEngine.Mode.AUTO), goldenDir, source(dirt, 3, 2, 1, null), feedback, progress());

        assertTrue(hashEngine.run());
        List<String> lines = Files.readAllLines(hashEngine.getGoldenFile().toPath(), StandardCharsets.UTF_8);
        assertEquals(referenceLine(0, 0, 3, 2, 1), lines.get(8));
    }

    @Test
    public void nonNullBiomeKeyRemainsUnchanged() throws Exception {
        RecordingFeedback feedback = new RecordingFeedback();
        GoldenHashEngine hashEngine = new GoldenHashEngine(engine, request(GoldenHashEngine.Mode.AUTO), goldenDir, source(dirt, 3, 2, 1, forest), feedback, progress());

        assertTrue(hashEngine.run());
        List<String> lines = Files.readAllLines(hashEngine.getGoldenFile().toPath(), StandardCharsets.UTF_8);
        assertEquals(referenceLine(0, 0, 3, 2, 1, "minecraft:forest"), lines.get(8));
    }

    @Test
    public void verifyMatchesGoldenCapture() {
        RecordingFeedback captureFeedback = new RecordingFeedback();
        GoldenHashEngine capture = new GoldenHashEngine(engine, request(GoldenHashEngine.Mode.AUTO), goldenDir, source(dirt, 3, 2, 1), captureFeedback, progress());
        assertTrue(capture.run());

        RecordingFeedback verifyFeedback = new RecordingFeedback();
        GoldenHashEngine verify = new GoldenHashEngine(engine, request(GoldenHashEngine.Mode.AUTO), goldenDir, source(dirt, 3, 2, 1), verifyFeedback, progress());
        assertTrue(verify.run());
        assertTrue(verifyFeedback.ok.stream().anyMatch((String line) -> line.startsWith("GOLDEN MATCH: 1/1")));
        assertTrue(verifyFeedback.fail.isEmpty());
    }

    @Test
    public void verifyFlagsMismatchAndWritesDiagnostics() {
        RecordingFeedback captureFeedback = new RecordingFeedback();
        GoldenHashEngine capture = new GoldenHashEngine(engine, request(GoldenHashEngine.Mode.AUTO), goldenDir, source(dirt, 3, 2, 1), captureFeedback, progress());
        assertTrue(capture.run());

        RecordingFeedback verifyFeedback = new RecordingFeedback();
        GoldenHashEngine verify = new GoldenHashEngine(engine, request(GoldenHashEngine.Mode.VERIFY), goldenDir, source(dirt, 7, 4, 9), verifyFeedback, progress());
        assertFalse(verify.run());
        assertTrue(verifyFeedback.fail.stream().anyMatch((String line) -> line.startsWith("GOLDEN MISMATCH: 1/1")));

        File goldenFile = new File(goldenDir, "testdim-s1234-c0x0-r0.hashes");
        assertTrue(new File(goldenDir, goldenFile.getName() + ".new").exists());
        assertTrue(new File(goldenDir, goldenFile.getName() + ".diag-c0x0.txt").exists());
    }

    @Test
    public void verifyModeWithoutCaptureFails() {
        RecordingFeedback feedback = new RecordingFeedback();
        GoldenHashEngine verify = new GoldenHashEngine(engine, request(GoldenHashEngine.Mode.VERIFY), goldenDir, source(dirt, 3, 2, 1), feedback, progress());
        assertFalse(verify.run());
        assertTrue(feedback.fail.stream().anyMatch((String line) -> line.startsWith("No golden capture at ")));
    }

    @Test
    public void abortsWithoutWritingWhenChunkGenerationFails() {
        RecordingFeedback feedback = new RecordingFeedback();
        GoldenHashEngine.ChunkSource broken = (int chunkX, int chunkZ) -> {
            throw new IllegalStateException("boom");
        };
        GoldenHashEngine hashEngine = new GoldenHashEngine(engine, request(GoldenHashEngine.Mode.AUTO), goldenDir, broken, feedback, progress());
        assertFalse(hashEngine.run());
        assertFalse(new File(goldenDir, "testdim-s1234-c0x0-r0.hashes").exists());
        assertTrue(feedback.fail.stream().anyMatch((String line) -> line.startsWith("GoldenHash aborted: 1 chunk")));
    }

    private GoldenHashEngine.Request request(GoldenHashEngine.Mode mode) {
        return new GoldenHashEngine.Request("testworld", 1234L, "test-1.0", MIN_Y, MAX_Y, 0, 0, 0, 1, mode, false, false, GoldenHashEngine.FALLBACK_BIOME_KEY);
    }

    private GoldenHashEngine.Progress progress() {
        return new GoldenHashEngine.Progress() {
        };
    }

    private GoldenHashEngine.ChunkSource source(PlatformBlockState special, int sx, int sy, int sz) {
        return source(special, sx, sy, sz, plains);
    }

    private GoldenHashEngine.ChunkSource source(PlatformBlockState special, int sx, int sy, int sz, PlatformBiome biome) {
        return (int chunkX, int chunkZ) -> new GoldenHashEngine.ChunkSnapshot() {
            @Override
            public int minY() {
                return MIN_Y;
            }

            @Override
            public int maxY() {
                return MAX_Y;
            }

            @Override
            public PlatformBlockState block(int x, int y, int z) {
                return x == sx && y == sy && z == sz ? special : stone;
            }

            @Override
            public PlatformBiome biome(int x, int y, int z) {
                return biome;
            }
        };
    }

    private String referenceLine(int chunkX, int chunkZ, int sx, int sy, int sz) throws Exception {
        return referenceLine(chunkX, chunkZ, sx, sy, sz, "minecraft:plains");
    }

    private String referenceLine(int chunkX, int chunkZ, int sx, int sy, int sz, String biomeKey) throws Exception {
        MessageDigest blockDigest = MessageDigest.getInstance("SHA-256");
        MessageDigest biomeDigest = MessageDigest.getInstance("SHA-256");
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = MIN_Y; y < MAX_Y; y++) {
                    String key = x == sx && y == sy && z == sz ? "minecraft:dirt" : "minecraft:stone";
                    blockDigest.update((key + "\n").getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        for (int x = 0; x < 16; x += 4) {
            for (int z = 0; z < 16; z += 4) {
                for (int y = MIN_Y; y < MAX_Y; y += 4) {
                    biomeDigest.update((biomeKey + "\n").getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        return chunkX + " " + chunkZ + " "
                + HexFormat.of().formatHex(blockDigest.digest()) + " "
                + HexFormat.of().formatHex(biomeDigest.digest());
    }

    private String referenceCombined(String bodyLine) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update((bodyLine + "\n").getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static final class RecordingFeedback implements GoldenHashEngine.Feedback {
        private final List<String> ok = new ArrayList<>();
        private final List<String> warn = new ArrayList<>();
        private final List<String> fail = new ArrayList<>();

        @Override
        public void ok(String message) {
            ok.add(message);
        }

        @Override
        public void warn(String message) {
            warn.add(message);
        }

        @Override
        public void fail(String message) {
            fail.add(message);
        }
    }
}
