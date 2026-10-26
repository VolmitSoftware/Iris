package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.generation.mantle.MantleHydrologyComponent;
import art.arcane.iris.generation.mantle.MantleWriter;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.FakeHydrology;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.testsupport.WaterScene;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntBinaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EffectiveWaterTest {
    private static final int DRY = EffectiveWater.DRY;
    private static final int SEA = 127;
    private static final long NONE = EffectiveWater.column(0, DRY);
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private IrisSettings previousSettings;

    @Before
    public void bindPlatform() {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        IrisServices.register(PreservationRegistry.class, mock(PreservationRegistry.class));
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.platformName()).thenReturn("bukkit");
        IrisPlatforms.bind(platform);
    }

    @After
    public void unbindPlatform() {
        IrisPlatforms.unbind();
        IrisServices.remove(PreservationRegistry.class);
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void lowerCornerColumnsRemainWetBetweenHigherNeighbours() {
        long corner = EffectiveWater.settle(EffectiveWater.column(77, 79), wet(81), wet(81), wet(79), NONE);
        assertEquals(EffectiveWater.column(77, 79), corner);

        long mixed = EffectiveWater.settle(EffectiveWater.column(77, 79), wet(81), wet(81), wet(87), NONE);
        assertEquals(EffectiveWater.column(77, 79), mixed);
    }

    @Test
    public void wetColumnsKeepTheirCanonicalBedAcrossDrops() {
        assertEquals(EffectiveWater.column(94, 99), EffectiveWater.settle(EffectiveWater.column(94, 99), wet(79), wet(99), NONE, NONE));
        assertEquals(EffectiveWater.column(94, 99), EffectiveWater.settle(EffectiveWater.column(94, 99), NONE, wet(97), NONE, NONE));
        assertEquals(EffectiveWater.column(94, 99), EffectiveWater.settle(EffectiveWater.column(94, 99), wet(98), NONE, NONE, NONE));
        assertEquals(EffectiveWater.column(98, 99), EffectiveWater.settle(EffectiveWater.column(98, 99), wet(70), NONE, NONE, NONE));
    }

    @Test
    public void seamDriedColumnsRiseAboveTheirHighestWetNeighbour() {
        long dried = EffectiveWater.column(148, DRY);
        assertEquals(EffectiveWater.column(150, DRY), EffectiveWater.settle(dried, wet(149), wet(148), NONE, NONE));
        assertEquals(EffectiveWater.column(160, DRY), EffectiveWater.settle(EffectiveWater.column(160, DRY), wet(149), NONE, NONE, NONE));
        assertEquals(dried, EffectiveWater.settle(dried, NONE, NONE, NONE, NONE));
    }

    @Test(timeout = 180_000L)
    public void aChunkFetchesItsNeighbourRastersOnceAndItsColumnsNeverLookUpTheRuntime() throws Exception {
        try (HydrologyFixture fixture = fixture()) {
            HydrologyRuntime counted = spy(fixture.hydrology.runtime);
            IrisComplex complex = fixture.complex(counted);
            int wet = 0;
            for (int pass = 0; pass < 2; pass++) {
                for (int index = 0; index < 256; index++) {
                    int x = index & 15;
                    int z = index >> 4;
                    complex.resolveHydrologyTerrainHeight(x, z);
                    complex.resolveWaterHead(x, z);
                    complex.hydrologyOwnsTerrain(x, z);
                    wet += complex.resolveWaterRole(x, z).wet() ? 1 : 0;
                }
            }
            assertTrue("wet columns " + wet, wet > 50);
            verify(counted, times(9)).raster(anyInt(), anyInt());
            verify(counted, never()).rasterAt(anyInt(), anyInt());
        }
    }

    @Test(timeout = 180_000L)
    public void columnsReadWhileANeighbourRasterIsProvisionalMatchTheChunkPath() throws Exception {
        try (HydrologyFixture fixture = fixture()) {
            HydrologyRuntime counted = spy(fixture.hydrology.runtime);
            IrisComplex complex = fixture.complex(counted);
            for (int[] chunk : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                fixture.hydrology.runtime.raster(chunk[0], chunk[1]);
            }
            fixture.hydrology.hold();
            fixture.mainThread.set(true);

            List<String> perColumn = waterColumns(complex);
            assertTrue(fixture.hydrology.runtime.rasterSnapshot(-1, -1).provisional());
            verify(counted, atLeastOnce()).rasterAt(anyInt(), anyInt());

            fixture.hydrology.release();
            for (int slot = 0; slot < 9; slot++) {
                fixture.hydrology.awaitSnapshot(slot % 3 - 1, slot / 3 - 1);
            }
            clearInvocations(counted);
            List<String> perChunk = waterColumns(complex);
            verify(counted, never()).rasterAt(anyInt(), anyInt());

            assertEquals(perChunk, perColumn);
            assertTrue(perChunk.toString(), perChunk.stream().filter(column -> column.endsWith("POND")).count() > 50);
        }
    }

    @Test
    public void chunksSharingACacheSlotNeverAnswerForEachOther() {
        EffectiveWater.Chunks chunks = new EffectiveWater.Chunks();
        EffectiveWater.Chunk first = new EffectiveWater.Chunk(new long[256], new WaterRole[256], new boolean[256], null);
        EffectiveWater.Chunk second = new EffectiveWater.Chunk(new long[256], new WaterRole[256], new boolean[256], null);

        chunks.put(0, 0, first);
        assertSame(first, chunks.get(0, 0));
        assertNull(chunks.get(4, 0));
        assertNull(chunks.get(0, -4));
        assertNull(chunks.get(-4, 4));

        chunks.put(4, 0, second);
        assertNull(chunks.get(0, 0));
        assertSame(second, chunks.get(4, 0));

        chunks.put(1, 0, first);
        assertSame(first, chunks.get(1, 0));
        assertSame(second, chunks.get(4, 0));
        assertNull(chunks.get(5, 0));
    }

    private static List<String> waterColumns(IrisComplex complex) {
        List<String> columns = new ArrayList<>(256);
        for (int index = 0; index < 256; index++) {
            int x = index & 15;
            int z = index >> 4;
            columns.add(x + "," + z + ":" + complex.resolveHydrologyTerrainHeight(x, z) + "," + complex.resolveWaterHead(x, z) + ","
                    + complex.hydrologyOwnsTerrain(x, z) + "," + complex.resolveWaterRole(x, z));
        }
        return columns;
    }

    @Test(timeout = 180_000L)
    public void diagonalStaircaseStepsPreserveEveryWetColumnWithoutSourceConversion() throws Exception {
        Scene scene = staircase();
        try (HydrologyFixture fixture = fixture(); SceneRun run = scene.install(fixture)) {
            Drops drops = new Drops(run.complex(), 0, 7, 0, 7);
            int wet = 0;
            for (int x = 24; x <= 104; x++) {
                for (int z = 24; z <= 104; z++) {
                    int head = rasterHead(run.hydrology(), x, z);
                    if (head == DRY) {
                        continue;
                    }
                    wet++;
                    assertEquals("water head at " + x + "," + z, head, drops.head(x, z));
                    assertTrue("submerged bed at " + x + "," + z, drops.ground(x, z) < head);
                    for (int[] side : SIDES) {
                        int neighbour = rasterHead(run.hydrology(), x + side[0], z + side[1]);
                        assertFalse("one-block drop at " + x + "," + z, neighbour != DRY && Math.abs(neighbour - head) == 1);
                    }
                }
            }
            assertTrue("wet columns " + wet, wet > 200);
            drops.assertSealed(24, 104, 24, 104);
        }
    }

    @Test(timeout = 180_000L)
    public void cascadeLipsHoldTheUpperWaterInOneBlockAboveTheFall() throws Exception {
        Scene scene = cascade();
        try (HydrologyFixture fixture = fixture(); SceneRun run = scene.install(fixture)) {
            Drops drops = new Drops(run.complex(), 0, 7, 0, 5);
            int lips = 0;
            int falls = 0;
            for (int x = 16; x <= 112; x++) {
                for (int z = 30; z <= 50; z++) {
                    int head = drops.head(x, z);
                    if (head == DRY) {
                        continue;
                    }
                    boolean lip = false;
                    for (int[] side : SIDES) {
                        int neighbour = drops.head(x + side[0], z + side[1]);
                        lip |= neighbour != DRY && neighbour <= head - 2;
                    }
                    if (lip) {
                        lips++;
                        assertEquals("lip ground at " + x + "," + z, head - 1, drops.ground(x, z));
                        assertEquals("canonical lip at " + x + "," + z, rasterGround(run.hydrology(), x, z), drops.ground(x, z));
                    }
                    if (drops.falling(x, head + 1, z)) {
                        falls++;
                        int highest = head;
                        for (int[] side : SIDES) {
                            highest = Math.max(highest, drops.head(x + side[0], z + side[1]));
                        }
                        for (int y = head + 1; y <= highest; y++) {
                            assertTrue("fall column at " + x + "," + y + "," + z, drops.falling(x, y, z));
                        }
                        assertFalse(drops.falling(x, highest + 1, z));
                    }
                }
            }
            assertTrue("lips " + lips, lips > 10);
            assertTrue("falls " + falls, falls > 10);
            drops.assertSealed(16, 112, 30, 50);
        }
    }

    @Test(timeout = 180_000L)
    public void noFallAnywhereMeetsTheSourceConversionCondition() throws Exception {
        for (Scene scene : List.of(staircase(), cascade(), outlet(), mouth())) {
            try (HydrologyFixture fixture = fixture(); SceneRun run = scene.install(fixture)) {
                Drops drops = new Drops(run.complex(), 0, 7, 0, 7);
                int wet = 0;
                for (int x = 8; x <= 120; x++) {
                    for (int z = 8; z <= 120; z++) {
                        int head = rasterHead(run.hydrology(), x, z);
                        if (head != DRY) {
                            wet++;
                            assertEquals(scene.name + " water stays connected at " + x + "," + z, head, drops.head(x, z));
                            assertTrue(scene.name + " bed stays submerged at " + x + "," + z, drops.ground(x, z) < head);
                        }
                    }
                }
                assertTrue(scene.name + " wet columns " + wet, wet > 100);
                assertEquals(scene.name + " conversions", 0, drops.conversions(8, 120, 8, 120));
                assertEquals(scene.name + " unsupported falls", 0, drops.unsupported(8, 120, 8, 120));
                drops.assertSealed(8, 120, 8, 120);
            }
        }
    }

    @Test(timeout = 180_000L)
    public void fallsAreTheSameInEitherChunkOrderThroughTheProductionReadPath() throws Exception {
        Scene scene = cascade();
        try (HydrologyFixture fixture = fixture()) {
            List<int[]> chunks = new ArrayList<>();
            for (int chunkX = 0; chunkX <= 7; chunkX++) {
                for (int chunkZ = 1; chunkZ <= 3; chunkZ++) {
                    chunks.add(new int[]{chunkX, chunkZ});
                }
            }
            Map<String, Set<String>> forward;
            try (SceneRun run = scene.install(fixture)) {
                forward = writes(run.complex(), chunks);
            }
            List<int[]> reversed = new ArrayList<>(chunks);
            Collections.reverse(reversed);
            Map<String, Set<String>> backward;
            try (SceneRun run = scene.install(fixture)) {
                backward = writes(run.complex(), reversed);
            }
            assertEquals(forward, backward);
            int total = 0;
            for (Set<String> cells : forward.values()) {
                total += cells.size();
            }
            assertTrue("fall cells " + total, total > 50);
        }
    }

    private static Scene staircase() {
        IntBinaryOperator natural = (x, z) -> 200 - Math.floorDiv(x + z, 24);
        return new Scene("staircase", natural, List.of(WaterScene.line(71L, WaterScene.stream(), 8, 8, 120, 120,
                (x, z) -> 198 - Math.floorDiv(x + z, 24))), List.of());
    }

    private static Scene cascade() {
        IntBinaryOperator natural = (x, z) -> 200 - 3 * Math.floorDiv(x, 32);
        return new Scene("cascade", natural, List.of(WaterScene.line(72L, WaterScene.tier("deep", 3f, 1.5f), 8, 40, 120, 40,
                (x, z) -> 198 - 3 * Math.floorDiv(x, 32))), List.of());
    }

    private static Scene outlet() {
        IntBinaryOperator natural = (x, z) -> 193;
        return new Scene("outlet", natural, List.of(WaterScene.line(73L, WaterScene.stream(), 60, 48, 120, 48, (x, z) -> 190)),
                List.of(WaterScene.pond(74L, 191, 48, 48, 10)));
    }

    private static Scene mouth() {
        IntBinaryOperator natural = (x, z) -> x < 64 ? SEA + 12 : SEA - 8;
        Reach reach = WaterScene.line(75L, WaterScene.stream(), 8, 40, 120, 40,
                (x, z) -> x < 64 ? SEA + 11 : SEA);
        ReachStyle[] styles = reach.style().clone();
        for (int point = 0; point < styles.length; point++) {
            if (reach.xs()[point] >= 64) {
                styles[point] = ReachStyle.MOUTH;
            }
        }
        Reach coastal = new Reach(reach.id(), reach.tier(), reach.downstreamId(), reach.xs(), reach.zs(), reach.grade(),
                reach.halfWidth(), reach.depth(), styles, reach.catchment());
        return new Scene("mouth", natural, List.of(coastal), List.of());
    }

    private HydrologyFixture fixture() throws Exception {
        return new HydrologyFixture(HydrologyFixture.pack(temporaryFolder.newFolder().toPath(), null, null, true));
    }

    private static long wet(int head) {
        return EffectiveWater.column(0, head);
    }

    private static int rasterHead(FakeHydrology hydrology, int x, int z) {
        return hydrology.runtime.raster(x >> 4, z >> 4).waterHead(x & 15, z & 15);
    }

    private static int rasterGround(FakeHydrology hydrology, int x, int z) {
        return hydrology.runtime.raster(x >> 4, z >> 4).terrainHeight(x & 15, z & 15);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Set<String>> writes(IrisComplex complex, List<int[]> chunks) {
        Map<String, Set<String>> writes = new HashMap<>();
        for (int[] chunk : chunks) {
            Set<String> cells = new TreeSet<>();
            MantleWriter writer = mock(MantleWriter.class);
            Mantle<Matter> mantle = mock(Mantle.class);
            when(writer.getMantle()).thenReturn(mantle);
            when(mantle.getWorldHeight()).thenReturn(384);
            doAnswer(call -> {
                cells.add(call.getArgument(0) + "," + call.getArgument(1) + "," + call.getArgument(2) + ","
                        + (call.getArgument(3) instanceof NativeBlockState ? "fluid" : "update"));
                return null;
            }).when(writer).setData(anyInt(), anyInt(), anyInt(), any());
            ChunkContext context = mock(ChunkContext.class);
            when(context.getComplex()).thenReturn(complex);
            new MantleHydrologyComponent(mock(EngineMantle.class)).generateLayer(writer, chunk[0], chunk[1], context);
            writes.put(chunk[0] + "," + chunk[1], cells);
        }
        return writes;
    }

    private record Scene(String name, IntBinaryOperator natural, List<Reach> reaches, List<Pond> ponds) {
        private SceneRun install(HydrologyFixture fixture) {
            FakeHydrology hydrology = new FakeHydrology(() -> false, natural, SEA, reaches, ponds);
            IrisComplex complex = fixture.complex(hydrology.runtime);
            complex.setNaturalHeightStream(ProceduralStream.ofDouble((x, z) -> natural.applyAsInt((int) Math.floor(x), (int) Math.floor(z))));
            return new SceneRun(hydrology, complex);
        }
    }

    private record SceneRun(FakeHydrology hydrology, IrisComplex complex) implements AutoCloseable {
        @Override
        public void close() {
            hydrology.close();
        }
    }

    private static final class Drops {
        private final IrisComplex complex;
        private final Map<Long, int[]> columns = new HashMap<>();
        private final Set<String> falls = new HashSet<>();

        @SuppressWarnings("unchecked")
        private Drops(IrisComplex complex, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
            this.complex = complex;
            List<int[]> chunks = new ArrayList<>();
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    chunks.add(new int[]{chunkX, chunkZ});
                }
            }
            for (Set<String> cells : writes(complex, chunks).values()) {
                for (String cell : cells) {
                    if (cell.endsWith(",fluid")) {
                        falls.add(cell.substring(0, cell.length() - ",fluid".length()));
                    }
                }
            }
        }

        private int ground(int x, int z) {
            return column(x, z)[0];
        }

        private int head(int x, int z) {
            return column(x, z)[1];
        }

        private int[] column(int x, int z) {
            return columns.computeIfAbsent(((long) x << 32) | (z & 0xFFFFFFFFL), key -> {
                int ground = (int) Math.round(complex.resolveHydrologyTerrainHeight(x, z));
                int head = (int) Math.round(complex.resolveWaterHead(x, z));
                return new int[]{ground, head > ground ? head : DRY};
            });
        }

        private boolean still(int x, int y, int z) {
            int head = head(x, z);
            return head != DRY && y > ground(x, z) && y <= head;
        }

        private boolean falling(int x, int y, int z) {
            return falls.contains(x + "," + y + "," + z);
        }

        private boolean water(int x, int y, int z) {
            return still(x, y, z) || falling(x, y, z);
        }

        private void assertSealed(int minX, int maxX, int minZ, int maxZ) {
            int leaks = 0;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int head = head(x, z);
                    for (int y = ground(x, z) + 1; y <= head; y++) {
                        for (int[] side : SIDES) {
                            int nx = x + side[0];
                            int nz = z + side[1];
                            if (y > ground(nx, nz) && !water(nx, y, nz)) {
                                leaks++;
                            }
                        }
                    }
                }
            }
            assertEquals("still water beside air", 0, leaks);
            assertEquals("falls without water below", 0, unsupported(minX, maxX, minZ, maxZ));
            assertEquals("falls that turn into sources", 0, conversions(minX, maxX, minZ, maxZ));
        }

        private int unsupported(int minX, int maxX, int minZ, int maxZ) {
            int found = 0;
            for (String cell : falls) {
                int[] at = parse(cell);
                if (inside(at, minX, maxX, minZ, maxZ) && !water(at[0], at[1] - 1, at[2])) {
                    found++;
                }
            }
            return found;
        }

        private int conversions(int minX, int maxX, int minZ, int maxZ) {
            int found = 0;
            for (String cell : falls) {
                int[] at = parse(cell);
                if (!inside(at, minX, maxX, minZ, maxZ)) {
                    continue;
                }
                int sources = 0;
                for (int[] side : SIDES) {
                    sources += still(at[0] + side[0], at[1], at[2] + side[1]) ? 1 : 0;
                }
                boolean base = still(at[0], at[1] - 1, at[2]) || at[1] - 1 <= ground(at[0], at[2]);
                if (sources >= 2 && base) {
                    found++;
                }
            }
            return found;
        }

        private static boolean inside(int[] at, int minX, int maxX, int minZ, int maxZ) {
            return at[0] >= minX && at[0] <= maxX && at[2] >= minZ && at[2] <= maxZ;
        }

        private static int[] parse(String cell) {
            String[] parts = cell.split(",");
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
        }
    }
}
